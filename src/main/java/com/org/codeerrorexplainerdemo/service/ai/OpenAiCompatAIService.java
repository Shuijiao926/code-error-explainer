package com.org.codeerrorexplainerdemo.service.ai;

import com.org.codeerrorexplainerdemo.config.AiProperties;
import com.org.codeerrorexplainerdemo.dto.Handoff;
import com.org.codeerrorexplainerdemo.dto.PersonReport;
import com.org.codeerrorexplainerdemo.dto.PreTriageReport;
import com.org.codeerrorexplainerdemo.entity.AiCallLog;
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
 * 按「分诊专家」角色输出双段式前置分诊报告。
 */
@Slf4j
public class OpenAiCompatAIService implements AIService {

    private String systemPrompt(String language) {
        String role = switch (language == null ? "" : language) {
            case "python" -> "资深的 Python 开发与调试专家";
            case "node" -> "资深的 Node.js/JavaScript 开发与调试专家";
            case "go" -> "资深的 Go 开发与调试专家";
            default -> "资深的 Java 后端开发与调试专家";
        };
        return """
                你是一位%s。用户会粘贴一段异常堆栈。
                你的职责是前置分诊：帮用户（包括非 IT 人员）看懂这个报错，并产出一份能让
                「项目里的 AI」据此做最终诊断的结构化线索。

                你没有项目代码上下文，因此必须遵守：
                - 只给出方向和可疑点，绝不断言"确定根因"
                - 明确指出哪些信息缺失、需要结合仓库上下文确认

                请严格按以下 JSON 格式返回（不要包含 markdown 代码块标记）：
                {
                  "personReport": {
                    "summary": "用一句大白话说明这是什么问题，非 IT 也能看懂",
                    "layer": "出在哪一层（业务代码/框架/基础设施），用普通人能懂的语言解释",
                    "misuse": "疑似的技术使用不妥，用大白话描述，并说明这是方向而非定论",
                    "nextStep": "你现在可以怎么做（引导用户把诊断包复制发给项目 AI）"
                  },
                  "handoff": {
                    "techStack": ["已识别到的技术栈"],
                    "suspectedPoints": ["给项目 AI 的技术向疑似点"],
                    "checkList": ["需要项目 AI 结合仓库上下文查证的事项"]
                  }
                }

                要求：
                - personReport 面向非 IT：优先用生活化类比把技术概念讲成日常事情
                  （如空指针 = "想用还没准备好的对象，像借东西东西还没到位"），
                  避免术语，实在要用就先解释
                - handoff 面向项目 AI：精确、可操作
                - 如果堆栈信息不完整，在 checkList 里明确指出还缺什么信息
                """.formatted(role);
    }

    private final AiProperties props;
    private final ObjectMapper objectMapper;
    private final AiCallRecorder recorder;
    private final RestClient restClient;

    public OpenAiCompatAIService(AiProperties props, ObjectMapper objectMapper, AiCallRecorder recorder) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.recorder = recorder;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(props.getTimeout());
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(props.getBaseUrl())
                .build();
    }

    @Override
    public AiCallOutcome explain(String stackTrace, List<String> techStack, String language) {
        String techStackHint = techStack == null || techStack.isEmpty() ? "未识别" : String.join(", ", techStack);
        String lang = language == null || language.isBlank() ? "未知" : language;
        String userContent = "语言：" + lang
                + "\n已识别的技术栈：" + techStackHint
                + "\n\n原始异常堆栈：\n" + stackTrace;
        Map<String, Object> body = Map.of(
                "model", props.getModel(),
                "temperature", 0.3,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt(language)),
                        Map.of("role", "user", "content", userContent)
                )
        );
        long start = System.nanoTime();
        AiCallLog callLog = new AiCallLog();
        callLog.setProvider(props.getProvider());
        callLog.setModel(props.getModel());
        callLog.setInputExcerpt(AiCallRecorder.excerpt(stackTrace));
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
            int promptTokens = root.at("/usage/prompt_tokens").asInt(-1);
            int completionTokens = root.at("/usage/completion_tokens").asInt(-1);
            callLog.setStatus("success");
            callLog.setPromptTokens(promptTokens >= 0 ? promptTokens : null);
            callLog.setCompletionTokens(completionTokens >= 0 ? completionTokens : null);
            callLog.setDurationMs(AiCallRecorder.elapsedMs(start));
            Long logId = recorder.record(callLog);
            return new AiCallOutcome(finalizeReport(parseReport(content), stackTrace, techStack), logId);
        } catch (Exception e) {
            log.warn("AI 调用失败，返回兜底分诊：{}", e.getMessage());
            callLog.setStatus("fallback");
            callLog.setErrorMessage(truncate(e.getMessage(), 1000));
            callLog.setDurationMs(AiCallRecorder.elapsedMs(start));
            Long logId = recorder.record(callLog);
            return new AiCallOutcome(fallback(stackTrace, techStack), logId);
        }
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    /** 解析 AI 返回的 JSON（剥离可能存在的 markdown 围栏），并统一覆盖技术栈与原始堆栈 */
    private PreTriageReport parseReport(String content) throws Exception {
        String text = content.trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```[a-zA-Z]*\\s*", "").replaceFirst("```\\s*$", "").trim();
        }
        return objectMapper.readValue(text, PreTriageReport.class);
    }

    /** 保证 handoff 中的技术栈与原始堆栈始终与最终值一致 */
    private PreTriageReport finalizeReport(PreTriageReport report, String stackTrace, List<String> techStack) {
        if (report.getHandoff() == null) {
            report.setHandoff(Handoff.builder().build());
        }
        report.getHandoff().setTechStack(techStack);
        report.getHandoff().setRawError(stackTrace);
        return report;
    }

    /** AI 不可用时的兜底分诊：用户不白屏，仍可入库 */
    private PreTriageReport fallback(String stackTrace, List<String> techStack) {
        String firstLine = stackTrace == null ? "未知异常" : stackTrace.lines().findFirst().orElse("未知异常");
        return PreTriageReport.builder()
                .personReport(PersonReport.builder()
                        .summary(firstLine)
                        .layer("无法定位（AI 服务暂不可用）")
                        .misuse("AI 服务调用失败，疑似配置问题（方向，待确认）")
                        .nextStep("检查 AI_API_KEY 配置后重试，或将报错直接发给项目 AI")
                        .build())
                .handoff(Handoff.builder()
                        .techStack(techStack)
                        .suspectedPoints(List.of("AI 服务调用失败，请检查 ai.api-key / base-url"))
                        .checkList(List.of("确认 API Key 是否有效", "确认网络可访问 " + props.getBaseUrl()))
                        .rawError(stackTrace)
                        .build())
                .build();
    }
}
