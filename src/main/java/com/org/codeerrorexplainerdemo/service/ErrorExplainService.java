package com.org.codeerrorexplainerdemo.service;

import com.org.codeerrorexplainerdemo.common.BusinessException;
import com.org.codeerrorexplainerdemo.dto.ExplainResponse;
import com.org.codeerrorexplainerdemo.dto.Handoff;
import com.org.codeerrorexplainerdemo.dto.PreTriageReport;
import com.org.codeerrorexplainerdemo.entity.ErrorHistory;
import com.org.codeerrorexplainerdemo.mapper.ErrorHistoryMapper;
import com.org.codeerrorexplainerdemo.service.ai.AiCallOutcome;
import com.org.codeerrorexplainerdemo.service.ai.AiCallRecorder;
import com.org.codeerrorexplainerdemo.service.ai.TriageReportProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 核心业务：前置分诊（识别技术栈 → AI 分诊 → 入库）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErrorExplainService {

    private static final Pattern ERROR_PATTERN = Pattern.compile("([\\w.]+(?:Exception|Error))");
    private static final int SUMMARY_MAX = 300;

    private final TriageReportProvider triageReportProvider;
    private final ErrorHistoryMapper historyMapper;
    private final ObjectMapper objectMapper;
    private final AiCallRecorder aiCallRecorder;

    public ExplainResponse explain(String stackTrace) {
        if (stackTrace == null || stackTrace.isBlank()) {
            throw new BusinessException("堆栈信息不能为空");
        }

        String language = LanguageDetector.detect(stackTrace);
        String errorType = extractErrorType(stackTrace, language);
        List<String> techStack = TechStackDetector.detect(stackTrace);

        AiCallOutcome outcome = triageReportProvider.explain(stackTrace, techStack, language);
        PreTriageReport report = outcome.report();
        // 保证 handoff 与最终技术栈/原始堆栈一致
        if (report.getHandoff() == null) {
            report.setHandoff(Handoff.builder().build());
        }
        report.getHandoff().setTechStack(techStack);
        report.getHandoff().setRawError(stackTrace);

        String preReportJson;
        try {
            preReportJson = objectMapper.writeValueAsString(report);
        } catch (Exception e) {
            throw new BusinessException("分诊报告序列化失败：" + e.getMessage());
        }

        String summary = null;
        if (report.getPersonReport() != null) {
            summary = report.getPersonReport().getSummary();
        }

        ErrorHistory history = new ErrorHistory();
        history.setErrorType(errorType);
        history.setLanguage(language);
        history.setTechStack(String.join(", ", techStack));
        history.setStackTrace(stackTrace);
        history.setPreReport(preReportJson);
        // explanation 为历史遗留列（NOT NULL），与 pre_report 写入同一份内容，兼容旧表结构
        history.setExplanation(preReportJson);
        history.setSummary(truncate(summary == null || summary.isBlank() ? errorType : summary, SUMMARY_MAX));
        try {
            historyMapper.insert(history);
            // 回填 AI 调用日志与 history 的关联
            aiCallRecorder.linkHistory(outcome.logId(), history.getId());
        } catch (DataAccessException e) {
            // 入库失败（多为内容过长）：清理孤儿调用日志，友好提示而非裸 500
            log.warn("history 入库失败，清理孤儿调用日志：{}", e.getMessage());
            aiCallRecorder.delete(outcome.logId());
            throw new BusinessException("保存失败：堆栈或报告内容过长，请精简后重试");
        }

        log.info("pre-triage done: id={}, lang={}, type={}, stack={}", history.getId(), language, errorType, techStack);
        return ExplainResponse.builder()
                .id(history.getId())
                .errorType(errorType)
                .language(language)
                .techStack(techStack)
                .summary(history.getSummary())
                .report(report)
                .createdAt(history.getCreatedAt())
                .build();
    }

    /**
     * 从堆栈中提取异常类型（自动识别语言后按语言规则提取），无匹配返回 Unknown。
     */
    public static String extractErrorType(String stackTrace) {
        return extractErrorType(stackTrace, LanguageDetector.detect(stackTrace));
    }

    public static String extractErrorType(String stackTrace, String language) {
        if (stackTrace == null) {
            return "Unknown";
        }
        if ("python".equals(language)) {
            return extractPythonError(stackTrace);
        }
        if ("node".equals(language)) {
            return extractNodeError(stackTrace);
        }
        if ("go".equals(language)) {
            return extractGoError(stackTrace);
        }
        return extractJavaError(stackTrace);
    }

    private static String extractJavaError(String stackTrace) {
        Matcher matcher = ERROR_PATTERN.matcher(stackTrace);
        if (matcher.find()) {
            String full = matcher.group(1);
            return full.substring(full.lastIndexOf('.') + 1);
        }
        return "Unknown";
    }

    /** Python：取最后一个 `XXXError/XXXException: msg` 行的异常名 */
    private static String extractPythonError(String stackTrace) {
        Matcher matcher = Pattern.compile("(?m)^(\\w+(?:Error|Exception)):").matcher(stackTrace);
        String found = "Unknown";
        while (matcher.find()) {
            found = matcher.group(1);
        }
        return found;
    }

    /** Node：取首个错误名（TypeError/ReferenceError 等） */
    private static String extractNodeError(String stackTrace) {
        Matcher matcher = Pattern.compile("\\b(TypeError|ReferenceError|RangeError|SyntaxError|URIError|Error):")
                .matcher(stackTrace);
        return matcher.find() ? matcher.group(1) : "Unknown";
    }

    /** Go：取 panic 信息第一段（截断） */
    private static String extractGoError(String stackTrace) {
        Matcher matcher = Pattern.compile("panic: (.+)").matcher(stackTrace);
        if (matcher.find()) {
            String msg = matcher.group(1).trim();
            return msg.length() > 60 ? msg.substring(0, 60) : msg;
        }
        return "Unknown";
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
