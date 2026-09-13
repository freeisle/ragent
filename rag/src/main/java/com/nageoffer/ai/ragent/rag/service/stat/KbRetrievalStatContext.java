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

/**
 * 知识库检索命中埋点所需的上下文标识
 *
 * @param conversationId     会话 ID
 * @param taskId             一次回答的流式任务 ID（每提问唯一），埋点时刻助手消息尚未落库，以此作为每回答唯一键
 * @param userId             用户 ID
 * @param questionMessageId  提问消息 ID（loadMemory 落库后回填），排障用，可为空
 */
public record KbRetrievalStatContext(String conversationId, String taskId, String userId, String questionMessageId) {
}
