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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link MilvusVectorRetrieverService#buildFilter}：库范围与时间下界的表达式拼装
 * <p>
 * 抽成静态纯函数的目的就是不依赖 Milvus 服务器验证组合逻辑；特别钉死 null-safety——
 * 库范围为空而时间下界非空时若退化为 null，检索会扫全共享库
 */
class MilvusVectorRetrieverServiceTest {

    private static final String MIN_ID = "1735689600000000000";

    @Test
    @DisplayName("单库无时间下界：表达式与旧版一致")
    void singleCollectionWithoutBound() {
        assertEquals("collection_name == \"kb-finance\"", MilvusVectorRetrieverService.buildFilter(List.of("kb-finance"), null));
    }

    @Test
    @DisplayName("单库 + 时间下界：两个谓词 AND 连接")
    void singleCollectionWithBound() {
        assertEquals("collection_name == \"kb-finance\" && id >= \"" + MIN_ID + "\"",
                MilvusVectorRetrieverService.buildFilter(List.of("kb-finance"), MIN_ID));
    }

    @Test
    @DisplayName("多库 + 时间下界：IN 列表与 id 下界 AND 连接")
    void multipleCollectionsWithBound() {
        assertEquals("collection_name in [\"kb-finance\", \"kb-hr\"] && id >= \"" + MIN_ID + "\"",
                MilvusVectorRetrieverService.buildFilter(List.of("kb-finance", "kb-hr"), MIN_ID));
    }

    @Test
    @DisplayName("库范围为空 + 时间下界：只剩 id 过滤，绝不退化为全共享库扫描")
    void emptyCollectionsWithBoundKeepsIdFilter() {
        assertEquals("id >= \"" + MIN_ID + "\"", MilvusVectorRetrieverService.buildFilter(List.of(), MIN_ID));
        assertEquals("id >= \"" + MIN_ID + "\"", MilvusVectorRetrieverService.buildFilter(null, MIN_ID));
    }

    @Test
    @DisplayName("时间下界为空：表达式与旧版一致")
    void blankBoundLeavesFilterUnchanged() {
        assertEquals("collection_name == \"kb-finance\"", MilvusVectorRetrieverService.buildFilter(List.of("kb-finance"), null));
        assertEquals("collection_name == \"kb-finance\"", MilvusVectorRetrieverService.buildFilter(List.of("kb-finance"), ""));
        assertEquals("collection_name == \"kb-finance\"", MilvusVectorRetrieverService.buildFilter(List.of("kb-finance"), "  "));
    }

    @Test
    @DisplayName("两者皆空：不过滤")
    void bothAbsentMeansNoFilter() {
        assertNull(MilvusVectorRetrieverService.buildFilter(null, null));
        assertNull(MilvusVectorRetrieverService.buildFilter(List.of(), ""));
    }

    @Test
    @DisplayName("下界与库名中的引号被转义，表达式不被注入截断")
    void quotesAreEscaped() {
        assertEquals("id >= \"a\\\"b\"", MilvusVectorRetrieverService.buildFilter(null, "a\"b"));
        assertEquals("collection_name == \"kb\\\"x\" && id >= \"" + MIN_ID + "\"",
                MilvusVectorRetrieverService.buildFilter(List.of("kb\"x"), MIN_ID));
    }
}
