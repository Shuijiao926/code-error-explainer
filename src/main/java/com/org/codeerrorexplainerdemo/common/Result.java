package com.org.codeerrorexplainerdemo.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一响应封装。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {

    public static final int CODE_OK = 200;
    public static final int CODE_FAIL = 500;

    private int code;
    private String message;
    private T data;

    public static <T> Result<T> ok(T data) {
        return new Result<>(CODE_OK, "success", data);
    }

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> fail(String message) {
        return new Result<>(CODE_FAIL, message, null);
    }
}
