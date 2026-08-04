package com.org.codeerrorexplainerdemo.service.ai;

import com.org.codeerrorexplainerdemo.dto.PreTriageReport;

/**
 * AI 调用结果：分诊报告 + 本次调用日志 id（供 history 入库后回填关联）。
 */
public record AiCallOutcome(PreTriageReport report, Long logId) {
}
