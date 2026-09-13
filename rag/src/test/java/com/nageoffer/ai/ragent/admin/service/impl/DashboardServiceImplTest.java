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

package com.nageoffer.ai.ragent.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.nageoffer.ai.ragent.admin.controller.vo.DashboardKbHitRateItemVO;
import com.nageoffer.ai.ragent.admin.controller.vo.DashboardKbHitRateVO;
import com.nageoffer.ai.ragent.knowledge.dao.entity.KnowledgeBaseDO;
import com.nageoffer.ai.ragent.knowledge.dao.mapper.KnowledgeBaseMapper;
import com.nageoffer.ai.ragent.rag.dao.mapper.ConversationMapper;
import com.nageoffer.ai.ragent.rag.dao.mapper.ConversationMessageMapper;
import com.nageoffer.ai.ragent.rag.dao.mapper.KbRetrievalStatMapper;
import com.nageoffer.ai.ragent.rag.dao.mapper.RagTraceRunMapper;
import com.nageoffer.ai.ragent.user.dao.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

/**
 * {@link DashboardServiceImpl}：知识库命中率统计
 */
@ExtendWith(MockitoExtension.class)
class DashboardServiceImplTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private ConversationMapper conversationMapper;
    @Mock
    private ConversationMessageMapper messageMapper;
    @Mock
    private RagTraceRunMapper traceRunMapper;
    @Mock
    private KbRetrievalStatMapper statMapper;
    @Mock
    private KnowledgeBaseMapper knowledgeBaseMapper;

    private DashboardServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DashboardServiceImpl(userMapper, conversationMapper, messageMapper,
                traceRunMapper, statMapper, knowledgeBaseMapper);
    }

    @Test
    @DisplayName("聚合 SQL：按 kb_id 分组、统计命中与引用次数、时间窗过滤（默认 7d）")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void aggregatesByKbWithinWindow() {
        when(statMapper.selectMaps(any())).thenReturn(List.of());

        service.loadKbHitRate(null);

        ArgumentCaptor<Wrapper> captor = ArgumentCaptor.forClass(Wrapper.class);
        org.mockito.Mockito.verify(statMapper).selectMaps(captor.capture());
        String selectSql = captor.getValue().getSqlSelect();
        assertTrue(selectSql.contains("count(*) as hits"), "命中次数聚合缺失：" + selectSql);
        assertTrue(selectSql.contains("coalesce(sum(cited), 0) as citations"), "引用次数聚合缺失：" + selectSql);
        String segment = captor.getValue().getSqlSegment();
        assertTrue(segment.contains("create_time"), "窗口过滤缺失：" + segment);
        assertTrue(segment.contains("GROUP BY kb_id"), "分组缺失：" + segment);
    }

    @Test
    @DisplayName("VO 组装：按命中降序、已删除库兜底名、命中率一位小数")
    void assemblesItemsWithFallbackName() {
        when(statMapper.selectMaps(any())).thenReturn(List.of(
                row("kb-1", 10, 3),
                row("kb-2", 5, 5)
        ));
        when(knowledgeBaseMapper.selectBatchIds(anyCollection()))
                .thenReturn(List.of(kb("kb-1", "产品知识库")));

        DashboardKbHitRateVO result = service.loadKbHitRate(null);

        assertEquals("7d", result.getWindow());
        assertEquals(2, result.getItems().size());
        DashboardKbHitRateItemVO first = result.getItems().get(0);
        assertEquals("kb-1", first.getKbId());
        assertEquals("产品知识库", first.getKbName());
        assertEquals(10L, first.getHitCount());
        assertEquals(3L, first.getCitationCount());
        assertEquals(30.0, first.getHitRate());
        DashboardKbHitRateItemVO second = result.getItems().get(1);
        assertEquals("已删除知识库", second.getKbName(), "查不到的知识库应显示兜底名");
        assertEquals(100.0, second.getHitRate());
    }

    @Test
    @DisplayName("无命中行时返回空列表")
    void returnsEmptyItemsWhenNoRows() {
        when(statMapper.selectMaps(any())).thenReturn(List.of());

        DashboardKbHitRateVO result = service.loadKbHitRate("24h");

        assertEquals("24h", result.getWindow());
        assertTrue(result.getItems().isEmpty());
    }

    private static Map<String, Object> row(String kbId, long hits, long citations) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("kb_id", kbId);
        row.put("hits", hits);
        row.put("citations", citations);
        return row;
    }

    private static KnowledgeBaseDO kb(String id, String name) {
        return KnowledgeBaseDO.builder().id(id).name(name).build();
    }
}
