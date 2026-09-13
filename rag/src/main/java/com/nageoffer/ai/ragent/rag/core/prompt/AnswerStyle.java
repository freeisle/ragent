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

package com.nageoffer.ai.ragent.rag.core.prompt;

import cn.hutool.core.util.StrUtil;

/**
 * 回答风格：随请求注入的答案语气 / 篇幅指令，追加在系统提示词末尾并声明优先级
 * <p>
 * 未指定（null 归一为 NONE）时行为与现状完全一致；指令文本是稳定产品文案，放代码而不是 DB 人设，
 * 是为了让「覆盖基础提示词里的语气规则」这条优先级条款不被控制台改动削弱
 */
public enum AnswerStyle {

    /** 内部哨兵：per-style 缓存的默认键；{@link #of(String)} 永不返回它 */
    NONE(null, null),

    FORMAL("formal", "【回答风格】本次回答必须使用正式、专业的书面语：句式完整规范，措辞严谨得体，"
            + "不使用口语化词汇、网络用语或语气词；先给结论再展开。"
            + "若与本提示词中其他语气或风格要求冲突，以本条为准。"),

    CASUAL("casual", "【回答风格】本次回答必须使用口语化、亲切的聊天风格：用自然随和的日常表达，"
            + "句式简短灵活，可适当使用语气词，像朋友交谈一样；先给结论再展开。"
            + "若与本提示词中其他语气或风格要求冲突，以本条为准。"),

    CONCISE("concise", "【回答风格】本次回答必须简洁：先给出核心结论，再以要点补充必要细节；"
            + "删去冗余铺垫、客套话和不影响理解的修饰，在不损失关键信息的前提下尽量缩短篇幅。"
            + "若与本提示词中其他语气或风格要求冲突，以本条为准。");

    private final String value;
    private final String systemInstruction;

    AnswerStyle(String value, String systemInstruction) {
        this.value = value;
        this.systemInstruction = systemInstruction;
    }

    public String getValue() {
        return value;
    }

    public String systemInstruction() {
        return systemInstruction;
    }

    /**
     * 宽容解析：大小写不敏感、容忍首尾空白；空串 / 未知值返回 null（调用方按无风格处理）
     */
    public static AnswerStyle of(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        String normalized = value.trim();
        for (AnswerStyle style : values()) {
            if (style.value != null && style.value.equalsIgnoreCase(normalized)) {
                return style;
            }
        }
        return null;
    }

    /**
     * 把风格指令追加到系统提示词末尾；无风格 / 无指令时原样返回
     * <p>
     * 风格指令声明优先级，必须在全部语气规则之后，故恒追加在末尾；
     * 基础提示词为空时直接以指令充任（理论上不出现，兜底防裸空串）
     */
    public static String applyInstruction(String systemPrompt, AnswerStyle style) {
        String instruction = style == null ? null : style.systemInstruction();
        if (StrUtil.isBlank(instruction)) {
            return systemPrompt;
        }
        return StrUtil.isBlank(systemPrompt) ? instruction : systemPrompt + "\n\n" + instruction;
    }
}
