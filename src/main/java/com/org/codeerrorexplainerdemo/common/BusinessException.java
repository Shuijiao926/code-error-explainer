package com.org.codeerrorexplainerdemo.common;

/**
 * 业务异常：用于可控的业务失败提示，由全局异常处理器转成 Result.fail。
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
