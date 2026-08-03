package com.org.codeerrorexplainerdemo.dto;

import lombok.Data;

/**
 * PUT /api/history/{id}/diagnosis 请求体。
 */
@Data
public class DiagnosisRequest {

    /** 项目 AI 给出的最终诊断文本 */
    private String diagnosis;
}
