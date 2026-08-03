package com.org.codeerrorexplainerdemo.service;

import com.org.codeerrorexplainerdemo.common.BusinessException;
import com.org.codeerrorexplainerdemo.dto.ExplainResponse;
import com.org.codeerrorexplainerdemo.dto.Explanation;
import com.org.codeerrorexplainerdemo.entity.ErrorHistory;
import com.org.codeerrorexplainerdemo.mapper.ErrorHistoryMapper;
import com.org.codeerrorexplainerdemo.service.ai.AIService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 核心业务：堆栈翻译 + 自动入库。
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
        Explanation explanation = aiService.explain(stackTrace);

        String explanationJson;
        try {
            explanationJson = objectMapper.writeValueAsString(explanation);
        } catch (Exception e) {
            throw new BusinessException("解释内容序列化失败：" + e.getMessage());
        }

        ErrorHistory history = new ErrorHistory();
        history.setErrorType(errorType);
        history.setStackTrace(stackTrace);
        history.setExplanation(explanationJson);
        String overview = explanation.getOverview();
        history.setSummary(truncate(overview == null || overview.isBlank() ? errorType : overview, SUMMARY_MAX));
        historyMapper.insert(history);

        log.info("explain done: id={}, type={}", history.getId(), errorType);
        return ExplainResponse.builder()
                .id(history.getId())
                .errorType(errorType)
                .summary(history.getSummary())
                .explanation(explanation)
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
