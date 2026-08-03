package com.org.codeerrorexplainerdemo;

import com.org.codeerrorexplainerdemo.service.ErrorExplainService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ErrorTypeExtractionTest {

    @Test
    void extractsNullPointerExceptionFromFullStack() {
        String stack = """
                java.lang.NullPointerException: Cannot invoke "com.app.UserRepository.findById(Object)" because "this.userRepository" is null
                \tat com.app.UserService.login(UserService.java:47)
                \tat com.app.AuthController.login(AuthController.java:12)
                """;
        assertEquals("NullPointerException", ErrorExplainService.extractErrorType(stack));
    }

    @Test
    void extractsSimpleClassNameFromQualifiedName() {
        assertEquals("NullPointerException",
                ErrorExplainService.extractErrorType("java.lang.NullPointerException"));
    }

    @Test
    void extractsOutOfMemoryError() {
        assertEquals("OutOfMemoryError",
                ErrorExplainService.extractErrorType("java.lang.OutOfMemoryError: Java heap space"));
    }

    @Test
    void returnsUnknownForPlainText() {
        assertEquals("Unknown", ErrorExplainService.extractErrorType("hello world, no exception here"));
    }

    @Test
    void returnsUnknownForNull() {
        assertEquals("Unknown", ErrorExplainService.extractErrorType(null));
    }
}
