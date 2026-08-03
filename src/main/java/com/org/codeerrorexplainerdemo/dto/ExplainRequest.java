package com.org.codeerrorexplainerdemo.dto;

import lombok.Data;

import java.util.List;

/**
 * POST /api/explain 请求体。
 */
@Data
public class ExplainRequest {

    /** 原始异常堆栈（完整或片段） */
    private String stackTrace;

    /** 用户修正的技术栈（可选；为空则自动识别） */
    private List<String> techStack;
}
