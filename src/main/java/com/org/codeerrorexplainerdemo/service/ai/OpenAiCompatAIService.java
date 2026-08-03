package com.org.codeerrorexplainerdemo.service.ai;

import com.org.codeerrorexplainerdemo.config.AiProperties;
import com.org.codeerrorexplainerdemo.dto.Explanation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容聊天接口实现（DeepSeek / OpenAI / Ollama 等均可走此通道）。
 * 通过 REST 直连 /chat/completions，不依赖重型 AI SDK。
 */
@Slf4j
public class OpenAiCompatAIService implements AIService {

    private static final String SYSTEM_PROMPT = """
            你是一个资深的 Java 后端开发专家和调试高手。用户会粘贴一段 Java 异常堆栈信息，
            请你用通俗易懂的中文解释这个错误。

            请严格按以下 JSON 格式返回（不要包含 markdown 代码块标记）：
            {
              "overview": "用一句话说清楚这是什么错误",
              "location": "异常发生在哪个文件、哪个方法、第几行",
              "rootCause": "根因分析，技术层面解释为什么会发生",
              "fixSteps": ["步骤1", "步骤2", "步骤3"],
              "prevention": "如何从代码层面预防同类问题"
            }

            要求：
            - 语言通俗，避免过于学术化
            - 修复步骤具体可操作
            - 如果堆栈信息不完整，明确指出还需要什么信息
            """;

    private final AiProperties props;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public OpenAiCompatAIService(AiProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(props.getTimeout());
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(props.getBaseUrl())
                .build();
    }

    @Override
    public Explanation explain(String stackTrace) {
        Map<String, Object> body = Map.of(
                "model", props.getModel(),
                "temperature", 0.3,
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", stackTrace)
                )
        );
        try {
            String raw = restClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + props.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            if (raw == null || raw.isBlank()) {
                throw new IllegalStateException("AI 响应为空");
            }
            JsonNode root = objectMapper.readTree(raw);
            String content = root.at("/choices/0/message/content").asText("");
            if (content.isBlank()) {
                throw new IllegalStateException("AI 未返回解释内容");
            }
            return parseExplanation(content);
        } catch (Exception e) {
            log.warn("AI 调用失败，返回兜底解释：{}", e.getMessage());
            return fallback(stackTrace);
        }
    }

    /** 解析 AI 返回的 JSON（剥离可能存在的 markdown 围栏） */
    private Explanation parseExplanation(String content) throws Exception {
        String text = content.trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```[a-zA-Z]*\\s*", "").replaceFirst("```\\s*$", "").trim();
        }
        return objectMapper.readValue(text, Explanation.class);
    }

    /** AI 不可用时的兜底解释：用户不白屏，仍可入库 */
    private Explanation fallback(String stackTrace) {
        String firstLine = stackTrace == null ? "未知异常" : stackTrace.lines().findFirst().orElse("未知异常");
        return Explanation.builder()
                .overview(firstLine)
                .location("无法定位（AI 服务暂不可用）")
                .rootCause("AI 服务调用失败，请检查 ai.api-key 配置是否有效")
                .fixSteps(List.of("检查 AI_API_KEY 环境变量或 application.yaml 中 ai.api-key", "确认网络可访问 " + props.getBaseUrl(), "稍后重试"))
                .prevention("配置好 AI 服务后即可获得真实分析")
                .build();
    }
}
