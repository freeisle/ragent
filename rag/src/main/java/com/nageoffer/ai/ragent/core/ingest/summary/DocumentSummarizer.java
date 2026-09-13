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
import com.nageoffer.ai.ragent.core.chunk.model.ChunkAssembler;
import com.nageoffer.ai.ragent.core.chunk.model.ChunkDraft;
import com.nageoffer.ai.ragent.core.chunk.model.ChunkMetadata;
import com.nageoffer.ai.ragent.core.parser.BlockTextRenderer;
import com.nageoffer.ai.ragent.core.parser.model.Block;
import com.nageoffer.ai.ragent.core.parser.model.Provenance;
import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import com.nageoffer.ai.ragent.infra.chat.LLMService;
import com.nageoffer.ai.ragent.infra.enums.Tier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 文档摘要增强：分块前用 LLM 给整篇文档生成约 {@code maxLength} 字摘要，摘要作为独立 chunk（index 0）与普通分块一起入库
 * <p>
 * 提升"这篇文档讲了什么"类全局性问题的召回率；输入文本由 {@link BlockTextRenderer} 渲染全文（图片块为 VLM
 * 图生文描述，LLM 摘要天然吃到图文全部信息）。摘要只是增强：开关关闭、文本过短、LLM 超时/限流/空返回
 * 一律返回 {@link Optional#empty()}，异常绝不外溢，不影响文档入库
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentSummarizer {

    /**
     * 摘要块元数据标记键：落在向量库 metadata JSONB（关系库 t_knowledge_chunk 无 metadata 列）
     */
    public static final String CHUNK_TYPE_KEY = "chunk_type";

    /**
     * 摘要块元数据标记值
     */
    public static final String CHUNK_TYPE_SUMMARY = "document_summary";

    /**
     * 摘要块展示文本的人读标记：前端分块列表据此辨识摘要块，无需前端改动
     */
    private static final String CONTENT_PREFIX = "## 文档摘要\n\n";

    /**
     * 提示词中目标字数的占位符：发送前替换为配置值，避免改配置后提示词漂移
     */
    private static final String MAX_LENGTH_PLACEHOLDER = "{maxLength}";

    private final LLMService llmService;
    private final DocumentSummaryProperties properties;

    /**
     * 生成摘要块；跳过与失败一律返回 empty，由调用方当作"无摘要"处理
     *
     * @param blocks     解析产出的有序 Block 列表
     * @param sourceFile 原始文件名，写入摘要块来源信息
     */
    public Optional<Chunk> summarize(List<Block> blocks, String sourceFile) {
        if (!properties.isEnabled()) {
            return Optional.empty();
        }
        if (blocks == null || blocks.isEmpty()) {
            return Optional.empty();
        }
        String text = BlockTextRenderer.render(blocks);
        if (text.length() < properties.getMinContentChars()) {
            log.debug("摄取-文档摘要跳过（文本过短） sourceFile={} 文本长度={}", sourceFile, text.length());
            return Optional.empty();
        }
        String input = text.length() > properties.getMaxInputChars()
                ? text.substring(0, properties.getMaxInputChars()) : text;

        try {
            long start = System.currentTimeMillis();
            String raw = llmService.chat(ChatRequest.builder()
                    .messages(List.of(
                            ChatMessage.system(systemPrompt()),
                            ChatMessage.user(input)))
                    .build(), Tier.FAST, properties.getModelId());
            long summaryMillis = System.currentTimeMillis() - start;

            String body = sanitize(raw, properties.getMaxLength());
            if (!StringUtils.hasText(body)) {
                log.warn("摄取-文档摘要返回为空，跳过 sourceFile={}", sourceFile);
                return Optional.empty();
            }
            log.info("摄取-文档摘要完成 sourceFile={} summaryMillis={} 字数={}", sourceFile, summaryMillis, body.length());
            return Optional.of(buildSummaryChunk(body, sourceFile));
        } catch (Exception e) {
            log.warn("摄取-文档摘要失败，跳过 sourceFile={} err={}", sourceFile, e.toString());
            return Optional.empty();
        }
    }

    /**
     * 摘要块：index 0 占位，展示文本带人读标记，向量文本只放摘要正文（无章节前缀污染）
     */
    private Chunk buildSummaryChunk(String body, String sourceFile) {
        ChunkMetadata metadata = ChunkMetadata.builder()
                .provenance(Provenance.ofFile(sourceFile))
                .extras(Map.of(CHUNK_TYPE_KEY, CHUNK_TYPE_SUMMARY))
                .build();
        return ChunkAssembler.assemble(0, ChunkDraft.of(CONTENT_PREFIX + body, body, metadata));
    }

    /**
     * 发送前的系统提示词：目标字数占位符替换为配置值
     */
    private String systemPrompt() {
        return properties.getSystemPrompt().replace(MAX_LENGTH_PLACEHOLDER, String.valueOf(properties.getMaxLength()));
    }

    /**
     * 摘要清洗：去代码围栏与首尾空白，超过 maxLength 硬截断（截断后再去一次首尾空白）
     */
    private String sanitize(String raw, int maxLength) {
        if (raw == null) {
            return "";
        }
        String cleaned = raw.replace("```", "").strip();
        return cleaned.length() > maxLength ? cleaned.substring(0, maxLength).strip() : cleaned;
    }
}
