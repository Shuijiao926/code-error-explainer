package com.org.codeerrorexplainerdemo;

import com.org.codeerrorexplainerdemo.dto.ExplainResponse;
import com.org.codeerrorexplainerdemo.dto.PageResult;
import com.org.codeerrorexplainerdemo.entity.ErrorHistory;
import com.org.codeerrorexplainerdemo.service.ErrorExplainService;
import com.org.codeerrorexplainerdemo.service.HistoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 核心链路集成测试（H2 内存库 + Mock AI）：
 * 翻译 → 入库 → 分页搜索 → 详情 → 删除。
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
                + "\tat com.app.AuthController.login(AuthController.java:12)";

        // 1. 翻译并入库
        ExplainResponse resp = explainService.explain(stack);
        assertThat(resp.getId()).isNotNull();
        assertThat(resp.getErrorType()).isEqualTo("NullPointerException");
        assertThat(resp.getExplanation().getOverview()).isNotBlank();
        assertThat(resp.getSummary()).isNotBlank();

        // 2. 分页搜索命中
        PageResult<ErrorHistory> page = historyService.page(1, 20, "NullPointer");
        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getRecords()).hasSize(1);
        assertThat(page.getRecords().get(0).getErrorType()).isEqualTo("NullPointerException");

        // 3. 详情回看
        ErrorHistory detail = historyService.detail(resp.getId());
        assertThat(detail.getSummary()).isNotBlank();
        assertThat(detail.getExplanation()).isNotBlank();

        // 4. 单条删除后为空
        historyService.delete(resp.getId());
        assertThat(historyService.page(1, 20, null).getTotal()).isZero();
    }

    @Test
    void keywordNotMatch_returnsEmpty() {
        explainService.explain("java.lang.OutOfMemoryError: Java heap space");
        PageResult<ErrorHistory> page = historyService.page(1, 20, "SQLException");
        assertThat(page.getTotal()).isZero();
    }
}
