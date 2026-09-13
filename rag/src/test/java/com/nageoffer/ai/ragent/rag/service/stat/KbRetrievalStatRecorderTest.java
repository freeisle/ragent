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

import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;
import com.nageoffer.ai.ragent.framework.convention.SourceRef;
import com.nageoffer.ai.ragent.knowledge.dao.entity.KnowledgeBaseDO;
import com.nageoffer.ai.ragent.knowledge.dao.mapper.KnowledgeBaseMapper;
import com.nageoffer.ai.ragent.rag.dao.entity.KbRetrievalStatDO;
import com.nageoffer.ai.ragent.rag.dao.mapper.KbRetrievalStatMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link KbRetrievalStatRecorder}：按提问去重的知识库命中埋点
 */
@ExtendWith(MockitoExtension.class)
class KbRetrievalStatRecorderTest {

    private static final KbRetrievalStatContext CONTEXT =
            new KbRetrievalStatContext("conv-1", "task-1", "user-1", "message-1");

    @Mock
    private KbRetrievalStatMapper statMapper;
    @Mock
    private KnowledgeBaseMapper knowledgeBaseMapper;

    private KbRetrievalStatRecorder recorder;

    @BeforeEach
    void setUp() {
        recorder = new KbRetrievalStatRecorder(statMapper, knowledgeBaseMapper);
    }

    private static RetrievedChunk chunk(String collection, String docId) {
        return RetrievedChunk.builder().collectionName(collection).docId(docId).build();
    }

    private static SourceRef source(String docId) {
        return SourceRef.builder().docId(docId).docName("文档").build();
    }

    @Test
    @DisplayName("跨子问题/通道同库命中只记一行")
    void dedupesAcrossIntents() {
        when(knowledgeBaseMapper.selectList(any())).thenReturn(List.of(kb("kb-1", "coll-1")));

        recorder.record(Map.of(
                "intent-1", List.of(chunk("coll-1", "d1"), chunk("coll-1", "d2")),
                "intent-2", List.of(chunk("coll-1", "d1"))
        ), List.of(), CONTEXT);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<KbRetrievalStatDO>> captor = ArgumentCaptor.forClass(List.class);
        verify(statMapper).insert(captor.capture());
        assertEquals(1, captor.getValue().size());
        KbRetrievalStatDO row = captor.getValue().get(0);
        assertEquals("kb-1", row.getKbId());
        assertEquals(0, row.getCited());
        assertEquals("task-1", row.getTaskId());
        assertEquals("message-1", row.getQuestionMessageId());
    }

    @Test
    @DisplayName("cited 判定：sources 引用该库任一文档记 1，其余记 0")
    void determinesCitedByDocId() {
        when(knowledgeBaseMapper.selectList(any())).thenReturn(List.of(kb("kb-1", "coll-1"), kb("kb-2", "coll-2")));

        recorder.record(Map.of("intent-1", List.of(
                chunk("coll-1", "d1"),     // 被引用 → cited=1
                chunk("coll-2", "d9"),     // 未被引用 → cited=0
                chunk("coll-1", null)      // docId 为空 → 不计引用
        )), List.of(source("d1")), CONTEXT);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<KbRetrievalStatDO>> captor = ArgumentCaptor.forClass(List.class);
        verify(statMapper).insert(captor.capture());
        List<KbRetrievalStatDO> rows = captor.getValue();
        assertEquals(2, rows.size());
        assertEquals(1, rows.get(0).getCited());
        assertEquals(0, rows.get(1).getCited());
    }

    @Test
    @DisplayName("联网结果（collectionName 为空）跳过，不产生行")
    void skipsWebChunks() {
        recorder.record(Map.of("intent-1", List.of(
                chunk(null, null),
                RetrievedChunk.builder().build()
        )), List.of(), CONTEXT);

        verifyNoInteractions(knowledgeBaseMapper, statMapper);
    }

    @Test
    @DisplayName("collection 查不到有效知识库时跳过该行，其余库照常落库")
    void skipsUnresolvableCollection() {
        when(knowledgeBaseMapper.selectList(any())).thenReturn(List.of(kb("kb-1", "coll-1")));

        recorder.record(Map.of("intent-1", List.of(
                chunk("coll-1", "d1"),
                chunk("coll-gone", "d2")
        )), List.of(), CONTEXT);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<KbRetrievalStatDO>> captor = ArgumentCaptor.forClass(List.class);
        verify(statMapper).insert(captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals("kb-1", captor.getValue().get(0).getKbId());
    }

    @Test
    @DisplayName("埋点 DB 异常不外溢，问答主链路不受影响")
    void degradesOnInsertFailure() {
        when(knowledgeBaseMapper.selectList(any())).thenReturn(List.of(kb("kb-1", "coll-1")));
        when(statMapper.insert(any(List.class))).thenThrow(new IllegalStateException("db down"));

        assertDoesNotThrow(() -> recorder.record(Map.of("intent-1", List.of(chunk("coll-1", "d1"))),
                List.of(), CONTEXT));
    }

    @Test
    @DisplayName("空入参直接返回，不碰任何 mapper")
    void noOpsOnEmptyInput() {
        recorder.record(Map.of(), List.of(), CONTEXT);
        recorder.record(null, List.of(), CONTEXT);
        verifyNoInteractions(knowledgeBaseMapper, statMapper);
    }

    private static KnowledgeBaseDO kb(String id, String collectionName) {
        return KnowledgeBaseDO.builder().id(id).collectionName(collectionName).build();
    }
}
