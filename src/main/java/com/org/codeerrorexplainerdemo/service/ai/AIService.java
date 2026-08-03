package com.org.codeerrorexplainerdemo.service.ai;

import com.org.codeerrorexplainerdemo.dto.PreTriageReport;

import java.util.List;

/**
 * AI 接入层抽象，支持切换 OpenAI / DeepSeek / 本地模型 / Mock。
 */
public interface AIService {

    /**
     * 前置分诊：把堆栈转成双段式报告（人话版 + 项目AI诊断包）。
     *
     * @param stackTrace 原始异常堆栈
     * @param techStack  已识别的技术栈（用户修正后）
     * @return 前置分诊报告
     */
    PreTriageReport explain(String stackTrace, List<String> techStack);
}
