package com.org.codeerrorexplainerdemo.service.ai;

import java.util.List;

/**
 * 为同一份异常堆栈生成前置分诊报告，来源可以是外部模型或本地规则库。
 */
public interface TriageReportProvider {

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
