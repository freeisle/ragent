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

package com.nageoffer.ai.ragent.mcp.executor;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class CurrentTimeMcpExecutor {

    private static final String TOOL_ID = "current_time_query";

    private static final String DEFAULT_TIMEZONE = "Asia/Shanghai";

    private static final Map<String, String> TIMEZONE_ALIASES = new LinkedHashMap<>();

    static {
        TIMEZONE_ALIASES.put("北京", "Asia/Shanghai");
        TIMEZONE_ALIASES.put("上海", "Asia/Shanghai");
        TIMEZONE_ALIASES.put("中国", "Asia/Shanghai");
        TIMEZONE_ALIASES.put("东京", "Asia/Tokyo");
        TIMEZONE_ALIASES.put("日本", "Asia/Tokyo");
        TIMEZONE_ALIASES.put("首尔", "Asia/Seoul");
        TIMEZONE_ALIASES.put("韩国", "Asia/Seoul");
        TIMEZONE_ALIASES.put("新加坡", "Asia/Singapore");
        TIMEZONE_ALIASES.put("伦敦", "Europe/London");
        TIMEZONE_ALIASES.put("巴黎", "Europe/Paris");
        TIMEZONE_ALIASES.put("纽约", "America/New_York");
        TIMEZONE_ALIASES.put("洛杉矶", "America/Los_Angeles");
        TIMEZONE_ALIASES.put("悉尼", "Australia/Sydney");
        TIMEZONE_ALIASES.put("迪拜", "Asia/Dubai");
    }

    @Bean
    public McpServerFeatures.SyncToolSpecification currentTimeToolSpecification() {
        return new McpServerFeatures.SyncToolSpecification(buildTool(),
                (exchange, request) -> handleCall(request));
    }

    private Tool buildTool() {
        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put("timezone", Map.of(
                "type", "string",
                "description", "时区标识，支持 IANA 时区名（如 Asia/Shanghai、America/New_York）或常见城市名（如 上海、东京），默认 Asia/Shanghai",
                "default", DEFAULT_TIMEZONE
        ));

        JsonSchema inputSchema = new JsonSchema(
                "object", properties, List.of(), null, null, null);

        return Tool.builder()
                .name(TOOL_ID)
                .description("查询指定时区的当前时间，返回 ISO-8601 格式的时间字符串，包含日期、时间、星期和时区偏移信息，默认查询北京时间（Asia/Shanghai）")
                .inputSchema(inputSchema)
                .build();
    }

    private CallToolResult handleCall(CallToolRequest request) {
        long startMs = System.currentTimeMillis();
        try {
            Map<String, Object> args = request.arguments() != null ? request.arguments() : Map.of();
            String timezone = stringArg(args, "timezone");

            if (timezone == null || timezone.isBlank()) {
                timezone = DEFAULT_TIMEZONE;
            }
            timezone = resolveTimezone(timezone.trim());

            ZoneId zoneId;
            try {
                zoneId = ZoneId.of(timezone);
            } catch (Exception e) {
                return errorResult("无法识别的时区: " + timezone + "，请使用 IANA 时区名（如 Asia/Shanghai）或常见城市名（如 上海、东京）");
            }

            String result = buildResult(zoneId);

            log.info("MCP 工具调用完成, toolId={}, timezone={}, elapsed={}ms",
                    TOOL_ID, zoneId.getId(), System.currentTimeMillis() - startMs);
            return successResult(result);
        } catch (Exception e) {
            log.error("MCP 工具调用失败, toolId={}, elapsed={}ms",
                    TOOL_ID, System.currentTimeMillis() - startMs, e);
            return errorResult("查询失败: " + e.getMessage());
        }
    }

    private String resolveTimezone(String timezone) {
        return TIMEZONE_ALIASES.getOrDefault(timezone, timezone);
    }

    private String buildResult(ZoneId zoneId) {
        ZonedDateTime now = ZonedDateTime.now(zoneId);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("【%s 当前时间】%n%n", zoneId.getId()));
        sb.append(String.format("ISO-8601: %s%n", now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)));
        sb.append(String.format("日期: %s%n", now.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"))));
        sb.append(String.format("时间: %s%n", now.format(DateTimeFormatter.ofPattern("HH:mm:ss"))));
        sb.append(String.format("星期: %s%n", toChineseWeekday(now.getDayOfWeek().getValue())));
        sb.append(String.format("时区偏移: UTC%s%n", now.getOffset().getId().replace("Z", "+00:00")));

        return sb.toString().trim();
    }

    private static String toChineseWeekday(int dayOfWeek) {
        String[] names = {"星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"};
        return names[(dayOfWeek - 1 + 7) % 7];
    }

    private static String stringArg(Map<String, Object> args, String key) {
        Object val = args.get(key);
        return val != null ? val.toString() : null;
    }

    private static CallToolResult successResult(String text) {
        return CallToolResult.builder()
                .content(List.of(new TextContent(text)))
                .isError(false)
                .build();
    }

    private static CallToolResult errorResult(String message) {
        return CallToolResult.builder()
                .content(List.of(new TextContent(message)))
                .isError(true)
                .build();
    }
}