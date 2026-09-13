/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nageoffer.ai.ragent.rag.core.vector;

import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.rag.config.RAGDefaultProperties;
import com.nageoffer.ai.ragent.rag.core.retrieval.RetrieveRequest;
import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;
import com.nageoffer.ai.ragent.infra.embedding.EmbeddingService;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.BaseVector;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rag.vector.type", havingValue = "milvus", matchIfMissing = true)
public class MilvusVectorRetrieverService implements VectorRetrieverService {

    private final EmbeddingService embeddingService;
    private final MilvusClientV2 milvusClient;
    private final RAGDefaultProperties ragDefaultProperties;

    @Override
    public List<RetrievedChunk> retrieve(RetrieveRequest retrieveParam) {
        float[] norm = embedAndNormalize(retrieveParam.getQuery());
        return retrieveByVector(norm, retrieveParam);
    }

    @Override
    public List<RetrievedChunk> retrieveByVector(float[] vector, RetrieveRequest retrieveParam) {
        // 单个或多个逻辑库都在共享物理 Collection 中一次过滤，topK 是整个过滤范围的总预算
        String filter = buildFilter(retrieveParam.getEffectiveCollectionNames(), retrieveParam.getMinChunkId());
        return searchShared(vector, filter, retrieveParam.getTopK());
    }

    @Override
    public float[] embedAndNormalize(String query) {
        return normalize(toArray(embeddingService.embed(query)));
    }

    @Override
    public boolean supportsGlobalRetrieval() {
        return true;
    }

    private static String buildCollectionFilter(List<String> collectionNames) {
        if (collectionNames == null || collectionNames.isEmpty()) {
            return null;
        }
        if (collectionNames.size() == 1) {
            return "collection_name == \"" + escapeFilterValue(collectionNames.get(0)) + "\"";
        }
        String inList = collectionNames.stream()
                .map(MilvusVectorRetrieverService::escapeFilterValue)
                .map(value -> "\"" + value + "\"")
                .collect(Collectors.joining(", "));
        return "collection_name in [" + inList + "]";
    }

    /**
     * 标量过滤表达式：库范围 + 时间下界，两者各自可为空，都为空时返回 null（不过滤）
     * <p>
     * id 是 VarChar 主键、取值是定宽 19 位的雪花十进制串（见 {@code RetrieveRequest#minChunkId}），
     * 与 collection_name 一样可做字典序范围比较；表达式是纯字符串拼装，
     * 抽成静态包级可见以便不起 Milvus 就能单测
     * <p>
     * 库范围为空而时间下界非空时只按 id 过滤（不能退化为全共享库扫描）；
     * 反之亦然，两者同为空时不过滤
     */
    static String buildFilter(List<String> collectionNames, String minChunkId) {
        String collectionFilter = buildCollectionFilter(collectionNames);
        String idFilter = StrUtil.isBlank(minChunkId) ? null : "id >= \"" + escapeFilterValue(minChunkId) + "\"";
        if (collectionFilter == null) {
            return idFilter;
        }
        return idFilter == null ? collectionFilter : collectionFilter + " && " + idFilter;
    }

    private static String escapeFilterValue(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * 在共享 collection 内执行一次向量检索
     *
     * @param filter 可选的标量过滤表达式（为空则不过滤，检索全共享库）
     */
    private List<RetrievedChunk> searchShared(float[] vector, String filter, int topK) {
        List<BaseVector> vectors = List.of(new FloatVec(vector));

        Map<String, Object> params = new HashMap<>();
        params.put("metric_type", ragDefaultProperties.getMetricType());
        params.put("ef", 128);

        var builder = SearchReq.builder()
                .collectionName(ragDefaultProperties.getCollectionName())
                .annsField("embedding")
                .data(vectors)
                .topK(topK)
                .searchParams(params)
                .outputFields(List.of("id", "content", "collection_name", "metadata"));
        if (StrUtil.isNotBlank(filter)) {
            builder.filter(filter);
        }

        SearchResp resp = milvusClient.search(builder.build());
        List<List<SearchResp.SearchResult>> results = resp.getSearchResults();

        if (results == null || results.isEmpty()) {
            return List.of();
        }

        return results.get(0).stream()
                .map(r -> RetrievedChunk.builder()
                        .id(Objects.toString(r.getEntity().get("id"), ""))
                        .text(Objects.toString(r.getEntity().get("content"), ""))
                        .collectionName(Objects.toString(r.getEntity().get("collection_name"), null))
                        .score(r.getScore())
                        .build())
                .collect(Collectors.toList());
    }

    private static float[] toArray(List<Float> list) {
        float[] arr = new float[list.size()];
        for (int i = 0; i < list.size(); i++) arr[i] = list.get(i);
        return arr;
    }

    private static float[] normalize(float[] v) {
        double sum = 0.0;
        for (float x : v) sum += x * x;
        double len = Math.sqrt(sum);
        float[] nv = new float[v.length];
        for (int i = 0; i < v.length; i++) nv[i] = (float) (v[i] / len);
        return nv;
    }
}
