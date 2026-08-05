package com.org.codeerrorexplainerdemo;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.org.codeerrorexplainerdemo.common.BusinessException;
import com.org.codeerrorexplainerdemo.dto.PageResult;
import com.org.codeerrorexplainerdemo.entity.ErrorHistory;
import com.org.codeerrorexplainerdemo.mapper.AiCallLogMapper;
import com.org.codeerrorexplainerdemo.mapper.ErrorHistoryMapper;
import com.org.codeerrorexplainerdemo.mapper.HistoryDiagnosisMapper;
import com.org.codeerrorexplainerdemo.service.ErrorExplainService;
import com.org.codeerrorexplainerdemo.service.HistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V1.6 踩坑档案：归档标记 + /api/kb 知识库检索集成测试（H2 内存库 + Mock AI）。
 * 覆盖：归档/取消归档与标签更新、关键字（含中文诊断内容）、多条件组合、分页、时间范围。
 */
@SpringBootTest
@ActiveProfiles("test")
class KbArchiveIntegrationTest {

    private static final String JAVA_NPE_STACK =
            "java.lang.NullPointerException: Cannot invoke \"com.app.UserRepository.findById(Object)\" because \"this.userRepository\" is null\n"
                    + "\tat com.app.UserService.login(UserService.java:47)\n"
                    + "\tat org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor.postProcessProperties(...)";

    private static final String PYTHON_VALUE_ERROR_STACK =
            "Traceback (most recent call last):\n"
                    + "  File \"app.py\", line 3, in <module>\n"
                    + "    x = int('abc')\n"
                    + "ValueError: invalid literal for int() with base 10: 'abc'";

    @Autowired
    private ErrorExplainService explainService;

    @Autowired
    private HistoryService historyService;

    @Autowired
    private ErrorHistoryMapper historyMapper;

    @Autowired
    private AiCallLogMapper aiCallLogMapper;

    @Autowired
    private HistoryDiagnosisMapper diagnosisMapper;

    @BeforeEach
    void cleanDb() {
        historyService.clearAll();
        aiCallLogMapper.delete(null);
        diagnosisMapper.delete(null);
    }

    private Long createJavaNpe() {
        return explainService.explain(JAVA_NPE_STACK).getId();
    }

    private Long createPythonValueError() {
        return explainService.explain(PYTHON_VALUE_ERROR_STACK).getId();
    }

    @Test
    void archive_setsFlagAndUpdatesTags() {
        Long id = createJavaNpe();

        ErrorHistory archived = historyService.archive(id, true, "java,空指针");
        assertThat(archived.getIsArchived()).isTrue();
        assertThat(archived.getTags()).isEqualTo("java,空指针");

        // 取消沉淀 + 传空串清空标签
        ErrorHistory unarchived = historyService.archive(id, false, "");
        assertThat(unarchived.getIsArchived()).isFalse();
        assertThat(unarchived.getTags()).isEqualTo("");

        // tags 为 null 表示不更新，保持原值
        historyService.archive(id, true, "缓存");
        ErrorHistory kept = historyService.archive(id, true, null);
        assertThat(kept.getTags()).isEqualTo("缓存");
    }

    @Test
    void archive_requiresArchivedAndExistingId() {
        assertThatThrownBy(() -> historyService.archive(999999L, true, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("记录不存在");

        Long id = createJavaNpe();
        assertThatThrownBy(() -> historyService.archive(id, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("archived 不能为空");
    }

    @Test
    void kb_keyword_matchesDiagnosisContentAndBaseFields() {
        Long id = createJavaNpe();
        historyService.saveDiagnosis(id, "已定位：静态 List 缓存无限增长，已修复");

        // 关键字命中回填诊断内容（中文）
        PageResult<ErrorHistory> byDiagnosis = historyService.kbPage(1, 20, "缓存无限增长",
                null, null, null, null, null, null);
        assertThat(byDiagnosis.getTotal()).isEqualTo(1);
        assertThat(byDiagnosis.getRecords().get(0).getId()).isEqualTo(id);

        // 关键字命中 error_type
        PageResult<ErrorHistory> byType = historyService.kbPage(1, 20, "NullPointerException",
                null, null, null, null, null, null);
        assertThat(byType.getTotal()).isEqualTo(1);

        // 关键字命中 tech_stack
        PageResult<ErrorHistory> byStack = historyService.kbPage(1, 20, "Spring",
                null, null, null, null, null, null);
        assertThat(byStack.getTotal()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void kb_filtersByLanguageAndTechStack() {
        createJavaNpe();
        createPythonValueError();

        PageResult<ErrorHistory> py = historyService.kbPage(1, 20, null, "python",
                null, null, null, null, null);
        assertThat(py.getTotal()).isEqualTo(1);
        assertThat(py.getRecords().get(0).getLanguage()).isEqualTo("python");

        PageResult<ErrorHistory> java = historyService.kbPage(1, 20, null, "java",
                null, null, null, null, null);
        assertThat(java.getTotal()).isEqualTo(1);

        PageResult<ErrorHistory> spring = historyService.kbPage(1, 20, null, null, "Spring",
                null, null, null, null);
        assertThat(spring.getTotal()).isEqualTo(1);
        assertThat(spring.getRecords().get(0).getTechStack()).contains("Spring");
    }

    @Test
    void kb_filtersByArchivedAndTag() {
        Long npeId = createJavaNpe();
        createPythonValueError();
        historyService.archive(npeId, true, "java,空指针");

        PageResult<ErrorHistory> archived = historyService.kbPage(1, 20, null, null, null,
                true, null, null, null);
        assertThat(archived.getTotal()).isEqualTo(1);
        assertThat(archived.getRecords().get(0).getId()).isEqualTo(npeId);
        assertThat(archived.getRecords().get(0).getIsArchived()).isTrue();

        PageResult<ErrorHistory> notArchived = historyService.kbPage(1, 20, null, null, null,
                false, null, null, null);
        assertThat(notArchived.getTotal()).isEqualTo(1);

        PageResult<ErrorHistory> byTag = historyService.kbPage(1, 20, null, null, null,
                null, null, null, "空指针");
        assertThat(byTag.getTotal()).isEqualTo(1);
        assertThat(byTag.getRecords().get(0).getId()).isEqualTo(npeId);
    }

    @Test
    void kb_multiConditionCombination() {
        Long npeId = createJavaNpe();
        createPythonValueError();
        historyService.archive(npeId, true, "java,空指针");
        historyService.saveDiagnosis(npeId, "诊断：注入未生效");

        // keyword（命中诊断内容）+ language + archived + tag 全命中
        PageResult<ErrorHistory> result = historyService.kbPage(1, 20, "注入未生效", "java",
                null, true, null, null, "空指针");
        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords().get(0).getId()).isEqualTo(npeId);

        // 关键字命中诊断内容但 language 不匹配 → 0
        PageResult<ErrorHistory> none = historyService.kbPage(1, 20, "注入未生效", "python",
                null, true, null, null, null);
        assertThat(none.getTotal()).isZero();
    }

    @Test
    void kb_timeRangeFilter() {
        Long id = createJavaNpe();
        historyMapper.update(null, new LambdaUpdateWrapper<ErrorHistory>()
                .eq(ErrorHistory::getId, id)
                .set(ErrorHistory::getCreatedAt, LocalDateTime.of(2026, 8, 1, 10, 30, 0)));

        PageResult<ErrorHistory> inRange = historyService.kbPage(1, 20, null, null, null, null,
                "2026-08-01 00:00:00", "2026-08-01 23:59:59", null);
        assertThat(inRange.getTotal()).isEqualTo(1);

        // 日期形式（当日 00:00:00 ~ 23:59:59）
        PageResult<ErrorHistory> dateOnly = historyService.kbPage(1, 20, null, null, null, null,
                "2026-08-01", "2026-08-01", null);
        assertThat(dateOnly.getTotal()).isEqualTo(1);

        PageResult<ErrorHistory> after = historyService.kbPage(1, 20, null, null, null, null,
                "2026-08-02", null, null);
        assertThat(after.getTotal()).isZero();
    }

    @Test
    void kb_pagination() {
        for (int i = 0; i < 5; i++) {
            createJavaNpe();
        }
        PageResult<ErrorHistory> page2 = historyService.kbPage(2, 2, null, null, null,
                null, null, null, null);
        assertThat(page2.getTotal()).isEqualTo(5);
        assertThat(page2.getRecords()).hasSize(2);
        assertThat(page2.getPage()).isEqualTo(2);
        assertThat(page2.getSize()).isEqualTo(2);
    }

    @Test
    void kb_listFields_includeArchiveMetaAndDiagnosedFlag() {
        Long id = createJavaNpe();
        historyService.archive(id, true, "java,缓存,并发");
        historyService.saveDiagnosis(id, "诊断：缓存未设上限导致 OOM，已改为 LRU");

        PageResult<ErrorHistory> result = historyService.kbPage(1, 20, "缓存未设上限",
                null, null, null, null, null, null);
        assertThat(result.getTotal()).isEqualTo(1);
        ErrorHistory row = result.getRecords().get(0);
        assertThat(row.getId()).isEqualTo(id);
        assertThat(row.getErrorType()).isEqualTo("NullPointerException");
        assertThat(row.getLanguage()).isEqualTo("java");
        assertThat(row.getTechStack()).isNotBlank();
        assertThat(row.getSummary()).isNotBlank();
        assertThat(row.getTags()).isEqualTo("java,缓存,并发");
        assertThat(row.getIsArchived()).isTrue();
        assertThat(row.getCreatedAt()).isNotNull();
        // 已诊断标记：finalDiagnosis 非 null
        assertThat(row.getFinalDiagnosis()).isNotNull();
    }
}
