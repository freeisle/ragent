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

package com.nageoffer.ai.ragent.core.chunk.model;

import cn.hutool.core.util.IdUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ChunkAssembler#minChunkIdAt}：雪花 ID 布局与时间→下界换算的正确性
 * <p>
 * 该下界是向量检索「最近 N 天」过滤的语义根基（PG / Milvus 都按它做字符串范围比较），
 * 布局细节一旦被换掉（例如误用三参 isInclude=false 重载掺入 dc/worker 位），过滤会漏掉别的节点写的块
 */
class ChunkAssemblerTest {

    /** Hutool Snowflake 默认起点（2010-11-04），与 SnowflakeIdInitializer 使用的全局发号器一致 */
    private static final long TWEPOCH = 1288834974657L;

    @Test
    @DisplayName("下界是「全局最小 ID」：低位全零，与发号节点无关")
    void minChunkIdIsGlobalMinimum() {
        long t = 1735689600000L; // 2025-01-01 00:00:00 UTC

        assertEquals(Long.toString((t - TWEPOCH) << 22), ChunkAssembler.minChunkIdAt(t),
                "两参重载返回低位全 0 的全局下界；掺入本实例 dc/worker 位会漏掉别的节点写的块");
    }

    @Test
    @DisplayName("下界不高于任一节点在该时刻铸造的最小 ID")
    void minChunkIdNeverExceedsAnyNodeMinimum() {
        long t = 1735689600000L;

        // 任一 dc/worker 组合铸造的最小 ID：下界必须 <= 它，否则该节点的块会被误挡在窗外
        long anyNodeMinimum = ((t - TWEPOCH) << 22) | (31L << 17) | (31L << 12);
        assertTrue(Long.parseLong(ChunkAssembler.minChunkIdAt(t)) <= anyNodeMinimum);
    }

    @Test
    @DisplayName("时间早于 twepoch 时钳到 0：负数串字典序最小会意外放行全部")
    void clampsToZeroBeforeTwepoch() {
        assertEquals("0", ChunkAssembler.minChunkIdAt(0L));
        assertEquals("0", ChunkAssembler.minChunkIdAt(TWEPOCH - 1));
    }

    @Test
    @DisplayName("新铸造的块 ID 恒不低于它自己时间窗的下界")
    void freshlyMintedIdNeverFallsBelowItsWindowBound() {
        long before = System.currentTimeMillis();
        String freshId = IdUtil.getSnowflakeNextIdStr();

        assertTrue(Long.parseLong(freshId) >= Long.parseLong(ChunkAssembler.minChunkIdAt(before)),
                "方向性保证：过滤只排除旧块，绝不能误挡新块");
    }

    @Test
    @DisplayName("当前 ID 定宽 19 位——字符串范围比较等价于数值比较的前提")
    void currentIdsAreFixedWidthNineteenDigits() {
        // 定宽前提约在 2080 年前恒成立（19 位容量 10^18 个 id）；若此断言失效，PG/Milvus 的字符串序过滤须改数值比较
        assertEquals(19, ChunkAssembler.minChunkIdAt(System.currentTimeMillis()).length());
    }

    @Test
    @DisplayName("下界随时刻单调不减")
    void minChunkIdIsMonotonicInTime() {
        long t = 1735689600000L;

        assertTrue(Long.parseLong(ChunkAssembler.minChunkIdAt(t + 1)) >= Long.parseLong(ChunkAssembler.minChunkIdAt(t)));
    }
}
