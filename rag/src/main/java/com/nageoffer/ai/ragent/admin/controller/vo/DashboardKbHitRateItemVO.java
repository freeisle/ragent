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

package com.nageoffer.ai.ragent.admin.controller.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识库命中率条目
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardKbHitRateItemVO {

    private String kbId;

    /**
     * 知识库名；已删除知识库显示兜底名「已删除知识库」
     */
    private String kbName;

    /**
     * 被检索命中次数（按提问去重）
     */
    private Long hitCount;

    /**
     * 被引用次数
     */
    private Long citationCount;

    /**
     * 命中率（%）= 被引用次数 / 被检索命中次数 × 100，一位小数
     */
    private Double hitRate;
}
