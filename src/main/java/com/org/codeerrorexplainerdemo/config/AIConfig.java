package com.org.codeerrorexplainerdemo.config;

import com.org.codeerrorexplainerdemo.service.ai.AIService;
import com.org.codeerrorexplainerdemo.service.ai.AiCallRecorder;
import com.org.codeerrorexplainerdemo.service.ai.MockAIService;
import com.org.codeerrorexplainerdemo.service.ai.OpenAiCompatAIService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/**
 * AI 提供者装配：未配置 API Key 或 provider=mock 时使用 Mock，否则走 OpenAI 兼容通道。
 */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AIConfig {

    @Bean
    public AIService aiService(AiProperties props, ObjectMapper objectMapper, AiCallRecorder recorder) {
        boolean mock = "mock".equalsIgnoreCase(props.getProvider())
                || props.getApiKey() == null
                || props.getApiKey().isBlank();
        return mock ? new MockAIService(recorder) : new OpenAiCompatAIService(props, objectMapper, recorder);
    }
}
