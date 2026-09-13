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

package com.nageoffer.ai.ragent.rag.service.stat;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;
import com.nageoffer.ai.ragent.framework.convention.SourceRef;
import com.nageoffer.ai.ragent.knowledge.dao.entity.KnowledgeBaseDO;
import com.nageoffer.ai.ragent.knowledge.dao.mapper.KnowledgeBaseMapper;
import com.nageoffer.ai.ragent.rag.dao.entity.KbRetrievalStatDO;
import com.nageoffer.ai.ragent.rag.dao.mapper.KbRetrievalStatMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 知识库检索命中埋点：检索完成后按知识库批量写 {@code t_kb_retrieval_stat}
 * <p>
 * 口径：一次提问中该库出现在检索结果里记 1 行（跨子问题/通道按 collectionName 分组去重）；
 * cited = 该次回答 sources 是否引用该库任一文档（按 docId 判定，sources 已按 docId 去重）。
 * 埋点失败只记 warn，绝不影响问答主链路
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KbRetrievalStatRecorder {

    private final KbRetrievalStatMapper statMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;

    /**
     * 记录一次回答的知识库检索命中
     *
     * @param intentChunks 各意图检索结果（值列表里的 chunk 携带 collectionName 与 docId）
     * @param sources      本次回答的来源引用（SourceRef 按 docId 去重）
     * @param context      会话/任务/用户标识
     */
    public void record(Map<String, List<RetrievedChunk>> intentChunks,
                       List<SourceRef> sources,
                       KbRetrievalStatContext context) {
        if (intentChunks == null || intentChunks.isEmpty()) {
            return;
        }
        try {
            List<KbRetrievalStatDO> rows = buildRows(intentChunks, sources, context);
            if (!rows.isEmpty()) {
                statMapper.insert(rows);
            }
        } catch (Exception ex) {
            log.warn("知识库检索命中埋点失败（不影响问答主链路），conversationId={}，taskId={}",
                    context.conversationId(), context.taskId(), ex);
        }
    }

    private List<KbRetrievalStatDO> buildRows(Map<String, List<RetrievedChunk>> intentChunks,
                                              List<SourceRef> sources,
                                              KbRetrievalStatContext context) {
        // 被引用 docId 集合（sources 已按 docId 去重）
        Set<String> citedDocIds = sources == null ? Set.of() : sources.stream()
                .map(SourceRef::getDocId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());

        // 按 collectionName 分组（跨子问题/通道去重；无库来源的联网结果 collectionName 为空，跳过）
        Map<String, List<RetrievedChunk>> byCollection = intentChunks.values().stream()
                .filter(chunks -> chunks != null && !chunks.isEmpty())
                .flatMap(List::stream)
                .filter(chunk -> chunk != null && StringUtils.hasText(chunk.getCollectionName()))
                .collect(Collectors.groupingBy(RetrievedChunk::getCollectionName,
                        LinkedHashMap::new, Collectors.toList()));
        if (byCollection.isEmpty()) {
            return List.of();
        }

        // collectionName -> kbId（仅活跃库；@TableLogic 自动过滤已删除知识库）
        Map<String, String> kbIdByCollection = knowledgeBaseMapper.selectList(
                        new QueryWrapper<KnowledgeBaseDO>()
                                .select("id", "collection_name")
                                .in("collection_name", byCollection.keySet()))
                .stream()
                .collect(Collectors.toMap(KnowledgeBaseDO::getCollectionName, KnowledgeBaseDO::getId,
                        (first, second) -> first));

        List<KbRetrievalStatDO> rows = new ArrayList<>();
        byCollection.forEach((collection, chunks) -> {
            String kbId = kbIdByCollection.get(collection);
            if (kbId == null) {
                log.warn("检索命中 collection={} 未找到有效知识库，跳过埋点", collection);
                return;
            }
            boolean cited = chunks.stream()
                    .anyMatch(chunk -> chunk.getDocId() != null && citedDocIds.contains(chunk.getDocId()));
            rows.add(KbRetrievalStatDO.builder()
                    .kbId(kbId)
                    .conversationId(context.conversationId())
                    .userId(context.userId())
                    .taskId(context.taskId())
                    .questionMessageId(context.questionMessageId())
                    .cited(cited ? 1 : 0)
                    .build());
        });
        return rows;
    }
}
