package com.org.codeerrorexplainerdemo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * POST /api/explain 响应体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExplainResponse {

    private Long id;

    /** 异常类型，如 NullPointerException */
    private String errorType;

    /** 一句话概要 */
    private String summary;

    /** AI 结构化解释 */
    private Explanation explanation;

    private LocalDateTime createdAt;
}
