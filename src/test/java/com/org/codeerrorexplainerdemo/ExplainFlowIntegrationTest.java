package com.org.codeerrorexplainerdemo;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.org.codeerrorexplainerdemo.config.AiProperties;
import com.org.codeerrorexplainerdemo.dto.DiagnosisEntry;
import com.org.codeerrorexplainerdemo.dto.ExplainResponse;
import com.org.codeerrorexplainerdemo.dto.PageResult;
import com.org.codeerrorexplainerdemo.entity.AiCallLog;
import com.org.codeerrorexplainerdemo.entity.ErrorHistory;
import com.org.codeerrorexplainerdemo.entity.HistoryDiagnosis;
import com.org.codeerrorexplainerdemo.mapper.AiCallLogMapper;
import com.org.codeerrorexplainerdemo.mapper.ErrorHistoryMapper;
import com.org.codeerrorexplainerdemo.mapper.HistoryDiagnosisMapper;
import com.org.codeerrorexplainerdemo.service.ErrorExplainService;
import com.org.codeerrorexplainerdemo.service.HistoryService;
import com.org.codeerrorexplainerdemo.service.ai.AiCallRecorder;
import com.org.codeerrorexplainerdemo.service.ai.OpenAiCompatibleTriageProvider;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 核心链路集成测试：分诊、日志关联、入库及历史管理。
 */
@SpringBootTest
@ActiveProfiles("test")
class ExplainFlowIntegrationTest {

    @Autowired
    private ErrorExplainService explainService;

    @Autowired
    private HistoryService historyService;

    @Autowired
    private AiCallLogMapper aiCallLogMapper;

    @Autowired
    private HistoryDiagnosisMapper diagnosisMapper;

    @Autowired
    private ErrorHistoryMapper historyMapper;

    @Autowired
    private AiCallRecorder callRecorder;

    @Autowired
    private ObjectMapper objectMapper;

    /** H2 内存库在类内共享，每个用例前清空保证计数断言独立 */
    @BeforeEach
    void cleanDb() {
        historyService.clearAll();
        aiCallLogMapper.delete(null);
        diagnosisMapper.delete(null);
    }

    @Test
    void fullFlow_saveQueryDetailDelete() {
        String stack = "java.lang.NullPointerException: Cannot invoke \"com.app.UserRepository.findById(Object)\" because \"this.userRepository\" is null\n"
                + "\tat com.app.UserService.login(UserService.java:47)\n"
                + "\tat org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor.postProcessProperties(...)";

        // 1. 分诊并入库（自动识别技术栈）
        ExplainResponse resp = explainService.explain(stack);
        assertThat(resp.getId()).isNotNull();
        assertThat(resp.getErrorType()).isEqualTo("NullPointerException");
        assertThat(resp.getReport().getPersonReport().getSummary()).isNotBlank();
        assertThat(resp.getReport().getPersonReport().getNextStep()).isNotBlank();
        assertThat(resp.getReport().getHandoff().getRawError()).isEqualTo(stack);
        assertThat(resp.getReport().getHandoff().getTechStack()).contains("Spring");
        assertThat(resp.getTechStack()).contains("Spring");

        // 2. 分页搜索命中
        PageResult<ErrorHistory> page = historyService.page(1, 20, "NullPointer");
        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getRecords()).hasSize(1);
        assertThat(page.getRecords().get(0).getErrorType()).isEqualTo("NullPointerException");

        // 3. 详情回看（含预判报告与技术栈）
        ErrorHistory detail = historyService.detail(resp.getId());
        assertThat(detail.getSummary()).isNotBlank();
        assertThat(detail.getPreReport()).isNotBlank();
        assertThat(detail.getTechStack()).contains("Spring");

        // 4. 单条删除后为空
        historyService.delete(resp.getId());
        assertThat(historyService.page(1, 20, null).getTotal()).isZero();
    }

    @Test
    void aiCallLog_recordedForLocalExplain() {
        ExplainResponse resp = explainService.explain("java.lang.NullPointerException: null");

        List<AiCallLog> logs = aiCallLogMapper.selectList(null);
        assertThat(logs).hasSize(1);
        AiCallLog log = logs.get(0);
        assertThat(log.getProvider()).isEqualTo("local");
        assertThat(log.getStatus()).isEqualTo("success");
        assertThat(log.getHistoryId()).isEqualTo(resp.getId());
        assertThat(log.getDurationMs()).isNotNull();
        assertThat(log.getInputExcerpt()).contains("NullPointerException");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "fallback|plain text",
            "fallback|{}",
            "fallback|{\"personReport\":{\"summary\":\"  \"}}",
            "success|{\"personReport\":{\"summary\":\"Missing user\"}}"
    })
    void modelReply_savesOneLinkedLog(String expectedStatus, String modelContent) throws Exception {
        byte[] reply = objectMapper.writeValueAsBytes(Map.of(
                "choices", List.of(Map.of("message", Map.of("content", modelContent))),
                "usage", Map.of("prompt_tokens", 12, "completion_tokens", 5)
        ));
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            try (exchange) {
                exchange.getRequestBody().readAllBytes();
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, reply.length);
                exchange.getResponseBody().write(reply);
            }
        });
        server.start();
        try {
            AiProperties properties = new AiProperties();
            properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
            properties.setApiKey("test-key");
            ErrorExplainService service = new ErrorExplainService(
                    new OpenAiCompatibleTriageProvider(properties, objectMapper, callRecorder),
                    historyMapper, objectMapper, callRecorder);
            String stack = "java.lang.NullPointerException: missing user";
            ExplainResponse response = service.explain(stack);

            boolean fallback = "fallback".equals(expectedStatus);
            assertThat(response.getReport().getPersonReport().getSummary())
                    .isEqualTo(fallback ? stack : "Missing user");
            assertThat(response.getReport().getHandoff().getRawError()).isEqualTo(stack);
            assertThat(aiCallLogMapper.selectList(null)).singleElement().satisfies(entry -> {
                assertThat(entry.getStatus()).isEqualTo(expectedStatus);
                assertThat(entry.getHistoryId()).isEqualTo(response.getId());
                assertThat(entry.getPromptTokens()).isEqualTo(12);
                if (fallback) {
                    assertThat(entry.getErrorMessage()).isNotBlank();
                } else {
                    assertThat(entry.getErrorMessage()).isNull();
                }
            });
        } finally {
            server.stop(0);
        }
    }

    @Test
    void keywordNotMatch_returnsEmpty() {
        explainService.explain("java.lang.OutOfMemoryError: Java heap space");
        PageResult<ErrorHistory> page = historyService.page(1, 20, "SQLException");
        assertThat(page.getTotal()).isZero();
    }

    @Test
    void backfillDiagnosis_appendsEntriesWithTimestamp() {
        ExplainResponse resp = explainService.explain("java.lang.OutOfMemoryError: Java heap space");

        List<DiagnosisEntry> first = historyService.saveDiagnosis(resp.getId(), "疑似全局缓存无限增长，待查证");
        assertThat(first).hasSize(1);
        assertThat(first.get(0).getTs()).isNotBlank();

        List<DiagnosisEntry> second = historyService.saveDiagnosis(resp.getId(), "已定位：静态 List 缓存未清理，已修复");
        assertThat(second).hasSize(2);
        assertThat(second.get(1).getContent()).contains("已修复");

        // 状态标记依据：final_diagnosis 非空
        ErrorHistory detail = historyService.detail(resp.getId());
        assertThat(detail.getFinalDiagnosis()).contains("已修复");

        // 搜索可命中技术栈字段（该 OOM 未识别到技术栈 → 通用/未知）
        PageResult<ErrorHistory> byStack = historyService.page(1, 20, "通用/未知");
        assertThat(byStack.getTotal()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void saveDiagnosis_writesChildRows_notAppendColumn() {
        ExplainResponse resp = explainService.explain("java.lang.OutOfMemoryError: Java heap space");

        historyService.saveDiagnosis(resp.getId(), "诊断一");
        historyService.saveDiagnosis(resp.getId(), "诊断二");

        // 子表一行一条，而非单列 JSON 追加
        List<HistoryDiagnosis> rows = diagnosisMapper.selectList(
                new LambdaQueryWrapper<HistoryDiagnosis>().eq(HistoryDiagnosis::getHistoryId, resp.getId()));
        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(HistoryDiagnosis::getContent).containsExactly("诊断一", "诊断二");

        // detail 由子表重建 finalDiagnosis，前端契约不变
        ErrorHistory detail = historyService.detail(resp.getId());
        assertThat(detail.getFinalDiagnosis()).contains("诊断一").contains("诊断二");

        // 删除 history 级联清理子表
        historyService.delete(resp.getId());
        assertThat(diagnosisMapper.selectList(new LambdaQueryWrapper<HistoryDiagnosis>()
                .eq(HistoryDiagnosis::getHistoryId, resp.getId()))).isEmpty();
    }

    @Test
    void pythonStack_detectsLanguageAndGeneratesTriage() {
        ExplainResponse resp = explainService.explain(
                "Traceback (most recent call last):\n"
                        + "  File \"app.py\", line 3, in <module>\n"
                        + "    x = int('abc')\n"
                        + "ValueError: invalid literal for int() with base 10: 'abc'");
        assertThat(resp.getLanguage()).isEqualTo("python");
        assertThat(resp.getErrorType()).isEqualTo("ValueError");
        assertThat(resp.getReport().getPersonReport().getSummary()).isNotBlank();
        assertThat(resp.getReport().getHandoff().getRawError()).contains("Traceback");
    }

    @Test
    void statusFilter_pendingAndDiagnosed() {
        explainService.explain("java.lang.OutOfMemoryError: Java heap space");
        Long diagnosedId = explainService.explain("java.lang.NullPointerException").getId();
        historyService.saveDiagnosis(diagnosedId, "已定位并修复");

        PageResult<ErrorHistory> pending = historyService.page(1, 20, null, "pending");
        assertThat(pending.getTotal()).isGreaterThanOrEqualTo(1);
        assertThat(pending.getRecords())
                .allMatch(r -> r.getFinalDiagnosis() == null);

        PageResult<ErrorHistory> diagnosed = historyService.page(1, 20, null, "diagnosed");
        assertThat(diagnosed.getTotal()).isGreaterThanOrEqualTo(1);
        assertThat(diagnosed.getRecords())
                .allMatch(r -> r.getFinalDiagnosis() != null);
    }
}
