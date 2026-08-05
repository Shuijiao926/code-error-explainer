package com.org.codeerrorexplainerdemo;

import com.org.codeerrorexplainerdemo.dto.KbStats;
import com.org.codeerrorexplainerdemo.dto.NameCount;
import com.org.codeerrorexplainerdemo.dto.TrendPoint;
import com.org.codeerrorexplainerdemo.mapper.AiCallLogMapper;
import com.org.codeerrorexplainerdemo.mapper.HistoryDiagnosisMapper;
import com.org.codeerrorexplainerdemo.service.ErrorExplainService;
import com.org.codeerrorexplainerdemo.service.HistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V1.6 踩坑统计与导出：/api/kb/stats 统计 + /api/kb/export 导出（markdown/csv）集成测试。
 * 覆盖：统计分布/趋势、archived 过滤、空库零值；导出 markdown 结构、CSV BOM 与转义、筛选一致性。
 */
@SpringBootTest
@ActiveProfiles("test")
class KbStatsExportIntegrationTest {

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
    void stats_emptyDb_returnsZeroWithoutError() {
        KbStats stats = historyService.stats(null, null, null, null, null, null, null, 10, 30);
        assertThat(stats.getTotal()).isZero();
        assertThat(stats.getErrorTypeTop()).isEmpty();
        assertThat(stats.getLanguageDist()).isEmpty();
        assertThat(stats.getTechStackDist()).isEmpty();
        // 趋势仍返回 30 个零值点（近 30 天）
        assertThat(stats.getTrend()).hasSize(30);
        assertThat(stats.getTrend()).allMatch(t -> t.getCount() == 0);
    }

    @Test
    void stats_countsDistributionsAndTrend() {
        createJavaNpe();
        createPythonValueError();

        KbStats stats = historyService.stats(null, null, null, null, null, null, null, 10, 30);
        assertThat(stats.getTotal()).isEqualTo(2);

        // 异常类型 TopN
        assertThat(stats.getErrorTypeTop()).hasSize(2);
        assertThat(stats.getErrorTypeTop())
                .extracting(NameCount::getName)
                .containsExactlyInAnyOrder("NullPointerException", "ValueError");

        // 语言分布
        assertThat(stats.getLanguageDist())
                .extracting(NameCount::getName)
                .containsExactlyInAnyOrder("java", "python");

        // 技术栈分布：字段拆开后逐项计数
        assertThat(stats.getTechStackDist()).isNotEmpty();
        assertThat(stats.getTechStackDist()).allMatch(t -> t.getCount() >= 1);

        // 趋势：默认近 30 天，今天有 2 条新增，其余为 0
        List<TrendPoint> trend = stats.getTrend();
        assertThat(trend).hasSize(30);
        assertThat(trend.get(trend.size() - 1).getCount()).isEqualTo(2);
        assertThat(trend).allMatch(t -> t.getDate() != null && !t.getDate().isBlank());
    }

    @Test
    void stats_topN_limitsErrorTypeList() {
        createJavaNpe();
        createPythonValueError();
        createJavaNpe();

        KbStats stats = historyService.stats(null, null, null, null, null, null, null, 1, 30);
        assertThat(stats.getErrorTypeTop()).hasSize(1);
        assertThat(stats.getErrorTypeTop().get(0).getName()).isEqualTo("NullPointerException");
        assertThat(stats.getErrorTypeTop().get(0).getCount()).isEqualTo(2);
    }

    @Test
    void stats_archivedFilter_onlyCountsArchived() {
        Long npeId = createJavaNpe();
        createPythonValueError();
        historyService.archive(npeId, true, "java");

        KbStats archived = historyService.stats(true, null, null, null, null, null, null, 10, 30);
        assertThat(archived.getTotal()).isEqualTo(1);
        assertThat(archived.getErrorTypeTop().get(0).getName()).isEqualTo("NullPointerException");

        KbStats notArchived = historyService.stats(false, null, null, null, null, null, null, 10, 30);
        assertThat(notArchived.getTotal()).isEqualTo(1);
    }

    @Test
    void stats_languageFilter_appliesToAllAggregates() {
        createJavaNpe();
        createPythonValueError();

        KbStats stats = historyService.stats(null, null, "python", null, null, null, null, 10, 30);
        assertThat(stats.getTotal()).isEqualTo(1);
        assertThat(stats.getLanguageDist()).extracting(NameCount::getName).containsExactly("python");
        assertThat(stats.getErrorTypeTop()).extracting(NameCount::getName).containsExactly("ValueError");
    }

    @Test
    void exportMarkdown_containsTitleMetaStackAndDiagnosis() {
        Long id = createJavaNpe();
        historyService.saveDiagnosis(id, "已定位：注入未生效，检查 @Autowired 装配");

        String md = historyService.exportMarkdown(null, null, null, null, null, null, null);
        assertThat(md).contains("# 踩坑知识库导出");
        assertThat(md).contains("NullPointerException");
        assertThat(md).contains("- 语言：java");
        assertThat(md).contains("### 堆栈");
        assertThat(md).contains("java.lang.NullPointerException");
        assertThat(md).contains("### 回填诊断");
        assertThat(md).contains("已定位：注入未生效");
    }

    @Test
    void exportMarkdown_respectsFilters() {
        createJavaNpe();
        createPythonValueError();

        String md = historyService.exportMarkdown(null, "python", null, null, null, null, null);
        assertThat(md).contains("ValueError");
        assertThat(md).doesNotContain("NullPointerException");
    }

    @Test
    void exportCsv_hasBomAndHeaderAndRows() {
        Long id = createJavaNpe();
        historyService.saveDiagnosis(id, "诊断A");
        historyService.saveDiagnosis(id, "诊断B");

        byte[] bytes = historyService.exportCsv(null, null, null, null, null, null, null);
        String csv = new String(bytes, StandardCharsets.UTF_8);

        // UTF-8 BOM
        assertThat(bytes[0] & 0xFF).isEqualTo(0xEF);
        assertThat(bytes[1] & 0xFF).isEqualTo(0xBB);
        assertThat(bytes[2] & 0xFF).isEqualTo(0xBF);

        // 表头
        assertThat(csv).contains("error_type,language,tech_stack,summary,tags,created_at,diagnosis");
        assertThat(csv).contains("NullPointerException");
        // 多条诊断以换行拼接（Excel 可换行单元格）
        assertThat(csv).contains("诊断A");
        assertThat(csv).contains("诊断B");
    }

    @Test
    void exportCsv_quotesFieldsWithCommaAndNewline() {
        Long id = createJavaNpe();
        // 含逗号的标签与含换行的诊断，验证转义
        historyService.archive(id, true, "java,空指针,并发");
        historyService.saveDiagnosis(id, "第一行诊断\n第二行：加逗号,继续");

        byte[] bytes = historyService.exportCsv(null, null, null, null, null, null, null);
        String csv = new String(bytes, StandardCharsets.UTF_8);
        assertThat(csv).contains("\"java,空指针,并发\"");
        assertThat(csv).contains("\"第一行诊断\n第二行：加逗号,继续\"");
    }
}
