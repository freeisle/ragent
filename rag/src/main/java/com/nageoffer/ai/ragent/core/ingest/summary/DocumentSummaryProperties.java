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

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文档摘要配置（LLM 文本增强，非 VLM）
 */
@Data
@Component
@ConfigurationProperties(prefix = "rag.document-summary")
public class DocumentSummaryProperties {

    /**
     * 总开关；关闭后入库流程完全跳过摘要步骤
     */
    private boolean enabled = true;

    /**
     * 全文渲染文本低于此长度（字符）不生成摘要：短文自身即可被完整召回，省一次 LLM 调用
     */
    private int minContentChars = 500;

    /**
     * 送入 LLM 的文档文本截断上限（字符），与 Tier.FAST 档位 5s 超时匹配的经验值
     */
    private int maxInputChars = 6000;

    /**
     * 摘要目标长度（字符）：注入提示词 {maxLength} 占位符，同时作为输出硬截断上限
     */
    private int maxLength = 200;

    /**
     * 可选模型覆盖；空 = 走 fast 档位候选
     */
    private String modelId = "";

    /**
     * 摘要引导提示词，含 {maxLength} 占位符，发送前替换为 {@link #maxLength}
     */
    private String systemPrompt = "你是知识库文档摘要助手。请阅读下面的文档全文（可能包含标题、段落、表格、列表与图片描述），"
            + "用中文写一段约 {maxLength} 字的摘要，概括文档的主题、核心内容与关键结论，便于检索时回答"
            + "\"这篇文档主要讲了什么\"这类全局性问题。要求：\n"
            + "1. 开头点明文档主题与适用场景；\n"
            + "2. 覆盖文档的主要结构、关键术语与重要结论，不要遗漏图片描述中承载的信息；\n"
            + "3. 只输出摘要正文本身，不要输出任何解释、前缀或 markdown 标记。";
}
