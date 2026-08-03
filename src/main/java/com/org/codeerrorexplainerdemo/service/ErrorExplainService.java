package com.org.codeerrorexplainerdemo.service;

import com.org.codeerrorexplainerdemo.common.BusinessException;
import com.org.codeerrorexplainerdemo.dto.ExplainResponse;
import com.org.codeerrorexplainerdemo.dto.Handoff;
import com.org.codeerrorexplainerdemo.dto.PreTriageReport;
import com.org.codeerrorexplainerdemo.entity.ErrorHistory;
import com.org.codeerrorexplainerdemo.mapper.ErrorHistoryMapper;
import com.org.codeerrorexplainerdemo.service.ai.AIService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private final AIService aiService;
    private final ErrorHistoryMapper historyMapper;
    private final ObjectMapper objectMapper;

    public ExplainResponse explain(String stackTrace) {
        if (stackTrace == null || stackTrace.isBlank()) {
            throw new BusinessException("堆栈信息不能为空");
        }

        String errorType = extractErrorType(stackTrace);
        List<String> techStack = TechStackDetector.detect(stackTrace);

        PreTriageReport report = aiService.explain(stackTrace, techStack);
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
        history.setTechStack(String.join(", ", techStack));
        history.setStackTrace(stackTrace);
        history.setPreReport(preReportJson);
        // explanation 为 V1.0 遗留列（NOT NULL），与 pre_report 保持同内容以兼容旧表
        history.setExplanation(preReportJson);
        history.setSummary(truncate(summary == null || summary.isBlank() ? errorType : summary, SUMMARY_MAX));
        historyMapper.insert(history);

        log.info("pre-triage done: id={}, type={}, stack={}", history.getId(), errorType, techStack);
        return ExplainResponse.builder()
                .id(history.getId())
                .errorType(errorType)
                .techStack(techStack)
                .summary(history.getSummary())
                .report(report)
                .createdAt(history.getCreatedAt())
                .build();
    }

    /**
     * 从堆栈中提取异常类型（简单类名），无匹配返回 Unknown。
     */
    public static String extractErrorType(String stackTrace) {
        if (stackTrace == null) {
            return "Unknown";
        }
        Matcher matcher = ERROR_PATTERN.matcher(stackTrace);
        if (matcher.find()) {
            String full = matcher.group(1);
            return full.substring(full.lastIndexOf('.') + 1);
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
