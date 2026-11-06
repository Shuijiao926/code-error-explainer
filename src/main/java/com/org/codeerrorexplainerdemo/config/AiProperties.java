package com.org.codeerrorexplainerdemo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * AI 提供商配置（application.yaml 的 ai.* 前缀）。
 */
@Data
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    /** openai | deepseek | claude | ollama | local（内置规则库） */
    private String provider = "deepseek";

    /** API Key，为空时使用内置规则库分诊 */
    private String apiKey = "";

    private String model = "deepseek-chat";

    private String baseUrl = "https://api.deepseek.com/v1";

    /** 请求超时 */
    private Duration timeout = Duration.ofSeconds(30);

    /** ai_call_log 保留天数，超期由定时任务清理 */
    private Integer callLogRetentionDays = 30;
}
