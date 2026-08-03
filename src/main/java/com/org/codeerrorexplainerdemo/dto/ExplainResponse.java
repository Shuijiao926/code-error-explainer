package com.org.codeerrorexplainerdemo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * POST /api/explain 响应体（V1.1 双段式）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExplainResponse {

    private Long id;

    /** 异常类型，如 NullPointerException */
    private String errorType;

    /** 技术栈（识别或用户修正后的最终值） */
    private List<String> techStack;

    /** 一句话概要（= personReport.summary） */
    private String summary;

    /** 前置分诊报告（人话版 + 项目AI诊断包） */
    private PreTriageReport report;

    private LocalDateTime createdAt;
}
