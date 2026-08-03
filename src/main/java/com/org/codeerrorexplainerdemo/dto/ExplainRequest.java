package com.org.codeerrorexplainerdemo.dto;

import lombok.Data;

/**
 * POST /api/explain 请求体。
 */
@Data
public class ExplainRequest {

    /** 原始异常堆栈（完整或片段） */
    private String stackTrace;
}
