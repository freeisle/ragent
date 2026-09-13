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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AnswerStyle}：请求值解析与风格指令文本
 */
class AnswerStyleTest {

    @Test
    @DisplayName("大小写不敏感、容忍首尾空白")
    void parsesCaseInsensitivelyAndTrims() {
        assertEquals(AnswerStyle.FORMAL, AnswerStyle.of("formal"));
        assertEquals(AnswerStyle.FORMAL, AnswerStyle.of("FORMAL"));
        assertEquals(AnswerStyle.FORMAL, AnswerStyle.of(" Formal "));
        assertEquals(AnswerStyle.CASUAL, AnswerStyle.of("casual"));
        assertEquals(AnswerStyle.CONCISE, AnswerStyle.of("concise"));
    }

    @Test
    @DisplayName("空串、未知值与 none 都归一为 null（调用方按无风格处理）")
    void unknownOrBlankResolvesToNull() {
        assertNull(AnswerStyle.of(null));
        assertNull(AnswerStyle.of(""));
        assertNull(AnswerStyle.of("  "));
        assertNull(AnswerStyle.of("unknown"));
        assertNull(AnswerStyle.of("none"), "NONE 是内部哨兵，不可经请求值到达");
    }

    @Test
    @DisplayName("三个风格的指令文本非空，且都带优先级条款（覆盖基础提示词里既有的语气规则）")
    void everyStyleCarriesPrecedenceClause() {
        for (AnswerStyle style : new AnswerStyle[]{AnswerStyle.FORMAL, AnswerStyle.CASUAL, AnswerStyle.CONCISE}) {
            String instruction = style.systemInstruction();
            assertFalse(instruction == null || instruction.isBlank(), style + " 的指令文本不得为空");
            assertTrue(instruction.contains("若与本提示词中其他语气或风格要求冲突，以本条为准"),
                    style + " 的指令必须声明优先级，否则压不住基础提示词里的语气规则");
        }
    }

    @Test
    @DisplayName("NONE 哨兵无指令文本")
    void noneHasNoInstruction() {
        assertNull(AnswerStyle.NONE.systemInstruction());
    }

    @Test
    @DisplayName("applyInstruction 恒追加在末尾；无风格原样返回；基础为空时兜底")
    void applyInstructionAppendsAtEndOrPassesThrough() {
        String base = "# 基础提示词";
        String applied = AnswerStyle.applyInstruction(base, AnswerStyle.FORMAL);
        assertTrue(applied.startsWith(base));
        assertTrue(applied.endsWith(AnswerStyle.FORMAL.systemInstruction()));

        assertEquals(base, AnswerStyle.applyInstruction(base, null));
        assertEquals(base, AnswerStyle.applyInstruction(base, AnswerStyle.NONE));
        assertEquals(AnswerStyle.CONCISE.systemInstruction(), AnswerStyle.applyInstruction("", AnswerStyle.CONCISE));
        assertEquals(AnswerStyle.CONCISE.systemInstruction(), AnswerStyle.applyInstruction(null, AnswerStyle.CONCISE));
    }
}
