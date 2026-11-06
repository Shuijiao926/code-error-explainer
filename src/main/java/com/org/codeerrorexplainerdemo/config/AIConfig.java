package com.org.codeerrorexplainerdemo.config;

import com.org.codeerrorexplainerdemo.service.ai.AiCallRecorder;
import com.org.codeerrorexplainerdemo.service.ai.LocalRuleTriageProvider;
import com.org.codeerrorexplainerdemo.service.ai.OpenAiCompatibleTriageProvider;
import com.org.codeerrorexplainerdemo.service.ai.TriageReportProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/**
 * AI 提供者装配：未配置 API Key，或 provider 指定为 mock/local 时使用本地规则库，否则走 OpenAI 兼容通道。
 */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AIConfig {

    @Bean
    public TriageReportProvider triageReportProvider(AiProperties props, ObjectMapper objectMapper, AiCallRecorder recorder) {
        boolean local = "mock".equalsIgnoreCase(props.getProvider())
                || "local".equalsIgnoreCase(props.getProvider())
                || props.getApiKey() == null
                || props.getApiKey().isBlank();
        return local ? new LocalRuleTriageProvider(recorder)
                : new OpenAiCompatibleTriageProvider(props, objectMapper, recorder);
    }
}
