package com.org.codeerrorexplainerdemo.service.ai;

import java.util.List;

/**
 * AI 接入层抽象，支持切换 OpenAI / DeepSeek / 本地模型 / Mock。
 */
public interface AIService {

    /**
     * 前置分诊：把堆栈转成双段式报告（人话版 + 项目AI诊断包）。
     *
     * @param stackTrace 原始异常堆栈
     * @param techStack  已识别的技术栈
     * @param language   堆栈语言（java/python/node/go/unknown）
     * @return 分诊报告 + 本次调用日志 id
     */
    AiCallOutcome explain(String stackTrace, List<String> techStack, String language);
}
