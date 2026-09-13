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

package com.nageoffer.ai.ragent.rag.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 知识库检索命中统计：每行 = 一次提问对单个知识库的一次检索命中（按提问去重）
 * <p>
 * 追加型埋点表：应用侧只写不删，不设逻辑删除列（惯例参照 t_agent_context_compaction / t_knowledge_document_chunk_log）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("t_kb_retrieval_stat")
public class KbRetrievalStatDO {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /**
     * 被命中知识库 ID（t_knowledge_base.id）
     */
    private String kbId;

    private String conversationId;

    private String userId;

    /**
     * 一次回答的流式任务 ID（雪花，每提问唯一），同提问跨子问题/通道的命中已合并去重
     */
    private String taskId;

    /**
     * 提问消息 ID（t_message.id），排障用，可为空
     */
    private String questionMessageId;

    /**
     * 该次回答 sources 是否引用该库任一文档：1-是，0-否
     */
    private Integer cited;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}
