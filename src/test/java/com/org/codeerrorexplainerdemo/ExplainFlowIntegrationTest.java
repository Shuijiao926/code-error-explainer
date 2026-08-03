package com.org.codeerrorexplainerdemo;

import com.org.codeerrorexplainerdemo.dto.DiagnosisEntry;
import com.org.codeerrorexplainerdemo.dto.ExplainResponse;
import com.org.codeerrorexplainerdemo.dto.PageResult;
import com.org.codeerrorexplainerdemo.entity.ErrorHistory;
import com.org.codeerrorexplainerdemo.service.ErrorExplainService;
import com.org.codeerrorexplainerdemo.service.HistoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 核心链路集成测试（H2 内存库 + Mock AI）：
 * 分诊 → 入库 → 分页搜索 → 详情 → 删除，以及技术栈识别/用户覆盖。
 */
@SpringBootTest
@ActiveProfiles("test")
class ExplainFlowIntegrationTest {

    @Autowired
    private ErrorExplainService explainService;

    @Autowired
    private HistoryService historyService;

    @Test
    void fullFlow_saveQueryDetailDelete() {
        String stack = "java.lang.NullPointerException: Cannot invoke \"com.app.UserRepository.findById(Object)\" because \"this.userRepository\" is null\n"
                + "\tat com.app.UserService.login(UserService.java:47)\n"
                + "\tat org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor.postProcessProperties(...)";

        // 1. 分诊并入库（自动识别技术栈）
        ExplainResponse resp = explainService.explain(stack, null);
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
    void techStackOverride_winsOverAutoDetect() {
        String stack = "java.net.SocketTimeoutException: Read timed out\n"
                + "\tat java.net.SocketInputStream.socketRead0(Native Method)";

        ExplainResponse resp = explainService.explain(stack, List.of("Spring Boot", "MyBatis"));
        assertThat(resp.getTechStack()).containsExactly("Spring Boot", "MyBatis");
        // 用户覆盖后，handoff 技术栈与入库字段保持一致
        assertThat(resp.getReport().getHandoff().getTechStack()).contains("Spring Boot");
    }

    @Test
    void keywordNotMatch_returnsEmpty() {
        explainService.explain("java.lang.OutOfMemoryError: Java heap space", null);
        PageResult<ErrorHistory> page = historyService.page(1, 20, "SQLException");
        assertThat(page.getTotal()).isZero();
    }

    @Test
    void backfillDiagnosis_appendsEntriesWithTimestamp() {
        ExplainResponse resp = explainService.explain("java.lang.OutOfMemoryError: Java heap space", null);

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
}
