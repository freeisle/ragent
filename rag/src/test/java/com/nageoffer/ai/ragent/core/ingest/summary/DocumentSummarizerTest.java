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

package com.nageoffer.ai.ragent.core.ingest.summary;

import com.nageoffer.ai.ragent.core.chunk.model.Chunk;
import com.nageoffer.ai.ragent.core.parser.model.Block;
import com.nageoffer.ai.ragent.core.parser.model.ImageBlock;
import com.nageoffer.ai.ragent.core.parser.model.ParagraphBlock;
import com.nageoffer.ai.ragent.core.parser.model.Provenance;
import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import com.nageoffer.ai.ragent.infra.chat.LLMService;
import com.nageoffer.ai.ragent.infra.enums.Tier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link DocumentSummarizer}：摘要生成、跳过与失败降级
 */
class DocumentSummarizerTest {

    private static final String SOURCE_FILE = "设计文档.md";

    private final LLMService llmService = mock(LLMService.class);
    private final DocumentSummaryProperties properties = new DocumentSummaryProperties();
    private final DocumentSummarizer summarizer = new DocumentSummarizer(llmService, properties);

    private static String longParagraphText() {
        return "本文介绍系统总体架构。".repeat(46); // 506 字 > min-content-chars(500)
    }

    private static List<Block> longParagraphBlocks() {
        return List.of(new ParagraphBlock(Provenance.ofFile(SOURCE_FILE), longParagraphText()));
    }

    @Test
    @DisplayName("文本够长时生成摘要块：index 0、展示带人读标记、向量文本为纯正文、元数据带 chunk_type 标记")
    void generatesSummaryChunk_whenTextLongEnough() {
        String body = "本文介绍系统的总体架构与各模块职责。";
        when(llmService.chat(any(), any(), any())).thenReturn(body);

        Optional<Chunk> result = summarizer.summarize(longParagraphBlocks(), SOURCE_FILE);

        assertTrue(result.isPresent());
        Chunk chunk = result.get();
        assertEquals(0, chunk.index());
        assertEquals("## 文档摘要\n\n" + body, chunk.content());
        assertEquals(body, chunk.embeddingText(), "向量文本只放摘要正文，不带展示前缀");
        assertEquals(DocumentSummarizer.CHUNK_TYPE_SUMMARY, chunk.metadata().extras().get(DocumentSummarizer.CHUNK_TYPE_KEY));
        assertEquals(SOURCE_FILE, chunk.metadata().provenance().sourceFile());

        ArgumentCaptor<ChatRequest> requestCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        ArgumentCaptor<Tier> tierCaptor = ArgumentCaptor.forClass(Tier.class);
        ArgumentCaptor<String> modelCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmService).chat(requestCaptor.capture(), tierCaptor.capture(), modelCaptor.capture());
        assertEquals(Tier.FAST, tierCaptor.getValue());
        assertEquals("", modelCaptor.getValue());
        ChatRequest request = requestCaptor.getValue();
        assertEquals(ChatMessage.Role.SYSTEM, request.getMessages().get(0).getRole());
        assertTrue(request.getMessages().get(0).getContent().contains(String.valueOf(properties.getMaxLength())),
                "提示词占位符应替换为目标字数");
        assertFalse(request.getMessages().get(0).getContent().contains("{maxLength}"));
        assertEquals(ChatMessage.Role.USER, request.getMessages().get(1).getRole());
        assertEquals(longParagraphText(), request.getMessages().get(1).getContent());
    }

    @Test
    @DisplayName("开关关闭时跳过，不调 LLM")
    void skips_whenDisabled() {
        properties.setEnabled(false);

        assertTrue(summarizer.summarize(longParagraphBlocks(), SOURCE_FILE).isEmpty());
        verifyNoInteractions(llmService);
    }

    @Test
    @DisplayName("文本过短时跳过，不调 LLM")
    void skips_whenTextTooShort() {
        List<Block> blocks = List.of(new ParagraphBlock(Provenance.ofFile(SOURCE_FILE), "很短"));

        assertTrue(summarizer.summarize(blocks, SOURCE_FILE).isEmpty());
        verifyNoInteractions(llmService);
    }

    @Test
    @DisplayName("LLM 返回空白/纯代码围栏时跳过")
    void skipsOnBlankResponse() {
        when(llmService.chat(any(), any(), any())).thenReturn("``` ```");

        assertTrue(summarizer.summarize(longParagraphBlocks(), SOURCE_FILE).isEmpty());
    }

    @Test
    @DisplayName("LLM 异常时降级跳过，异常不外溢")
    void skipsOnLlmException() {
        when(llmService.chat(any(), any(), any())).thenThrow(new IllegalStateException("模型超时"));

        assertTrue(summarizer.summarize(longParagraphBlocks(), SOURCE_FILE).isEmpty());
    }

    @Test
    @DisplayName("输出超过 maxLength 时硬截断")
    void truncatesOutputBeyondMaxLength() {
        when(llmService.chat(any(), any(), any())).thenReturn("字".repeat(properties.getMaxLength() + 100));

        Optional<Chunk> result = summarizer.summarize(longParagraphBlocks(), SOURCE_FILE);

        assertTrue(result.isPresent());
        assertEquals(properties.getMaxLength(), result.get().embeddingText().length());
    }

    @Test
    @DisplayName("图片块描述进入 LLM 输入：VLM 图生文与 LLM 摘要协同")
    void imageDescriptionIncludedInPrompt() {
        String description = "架构图描述：".repeat(84); // 504 字 > min-content-chars(500)，隔离渲染来源
        List<Block> blocks = List.of(new ImageBlock(Provenance.ofFile(SOURCE_FILE), null, "架构图", "alt", description));
        when(llmService.chat(any(), any(), any())).thenReturn("摘要正文");

        summarizer.summarize(blocks, SOURCE_FILE);

        ArgumentCaptor<ChatRequest> requestCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(llmService).chat(requestCaptor.capture(), any(), any());
        assertTrue(requestCaptor.getValue().getMessages().get(1).getContent().contains(description),
                "图片描述应进入摘要输入文本");
    }
}
