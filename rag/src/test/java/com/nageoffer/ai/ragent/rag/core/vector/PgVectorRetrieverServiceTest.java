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

import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;
import com.nageoffer.ai.ragent.infra.embedding.EmbeddingService;
import com.nageoffer.ai.ragent.rag.core.retrieval.RetrieveRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PgVectorRetrieverServiceTest {

    @Test
    @DisplayName("多Collection使用单条IN查询并只携带一个总LIMIT")
    @SuppressWarnings("unchecked")
    void queryMultipleCollectionsWithOneSharedLimit() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        when(embeddingService.embed("报销流程")).thenReturn(List.of(3.0F, 4.0F));
        when(jdbcTemplate.query(
                anyString(),
                any(RowMapper.class),
                any(Object[].class)
        )).thenReturn(List.<RetrievedChunk>of());

        PgVectorRetrieverService service = new PgVectorRetrieverService(jdbcTemplate, embeddingService);
        service.retrieve(RetrieveRequest.builder()
                .query("报销流程")
                .collectionNames(List.of("kb-finance", "kb-policy"))
                .topK(7)
                .build());

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, times(1)).query(
                sqlCaptor.capture(),
                any(RowMapper.class),
                argsCaptor.capture()
        );

        assertTrue(sqlCaptor.getValue().contains("collection_name IN (?, ?)"));
        Object[] args = argsCaptor.getValue();
        assertEquals("kb-finance", args[1]);
        assertEquals("kb-policy", args[2]);
        assertEquals(7, args[4], "SQL 只能有一个跨 Collection 共享的 LIMIT");
        verify(embeddingService, times(1)).embed("报销流程");
    }

    @Test
    @DisplayName("带 chunk ID 下界时拼接 AND id >= ?，且谓词参数排在 ORDER BY 向量参数之前")
    @SuppressWarnings("unchecked")
    void minChunkIdAppendsIdPredicateBeforeOrderByVector() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        when(embeddingService.embed("报销流程")).thenReturn(List.of(3.0F, 4.0F));
        when(jdbcTemplate.query(
                anyString(),
                any(RowMapper.class),
                any(Object[].class)
        )).thenReturn(List.<RetrievedChunk>of());

        PgVectorRetrieverService service = new PgVectorRetrieverService(jdbcTemplate, embeddingService);
        service.retrieve(RetrieveRequest.builder()
                .query("报销流程")
                .collectionNames(List.of("kb-finance", "kb-policy"))
                .topK(7)
                .minChunkId("1735689600000000000")
                .build());

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, times(1)).query(
                sqlCaptor.capture(),
                any(RowMapper.class),
                argsCaptor.capture()
        );

        assertTrue(sqlCaptor.getValue().contains("AND id >= ?"), "时间下界谓词必须出现在 WHERE 里");
        Object[] args = argsCaptor.getValue();
        assertEquals("kb-finance", args[1]);
        assertEquals("kb-policy", args[2]);
        assertEquals("1735689600000000000", args[3], "下界参数紧跟 IN 列表，排在 ORDER BY 向量之前");
        assertEquals(args[0], args[4], "ORDER BY 的向量参数被谓词右移一位");
        assertEquals(7, args[5], "LIMIT 恒在末尾");
    }
}
