package com.org.codeerrorexplainerdemo.service.ai;

import com.org.codeerrorexplainerdemo.dto.Explanation;

/**
 * AI 接入层抽象，支持切换 OpenAI / DeepSeek / 本地模型 / Mock。
 */
public interface AIService {

    /**
     * 把堆栈翻译成人话。
     *
     * @param stackTrace 原始异常堆栈
     * @return 结构化解释
     */
    Explanation explain(String stackTrace);
}
