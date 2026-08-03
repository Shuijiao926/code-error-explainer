package com.org.codeerrorexplainerdemo;

import com.org.codeerrorexplainerdemo.service.ErrorExplainService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void extractsPythonValueError() {
        String s = "Traceback (most recent call last):\n"
                + "  File \"app.py\", line 3, in <module>\n"
                + "    x = int('abc')\n"
                + "ValueError: invalid literal for int() with base 10: 'abc'";
        assertEquals("ValueError", ErrorExplainService.extractErrorType(s));
    }

    @Test
    void extractsNodeTypeError() {
        String s = "TypeError: Cannot read properties of undefined (reading 'name')\n"
                + "    at Object.<anonymous> (/app/main.js:8:5)";
        assertEquals("TypeError", ErrorExplainService.extractErrorType(s));
    }

    @Test
    void extractsGoPanic() {
        String s = "panic: runtime error: index out of range [3] with capacity 2\n\n"
                + "goroutine 1 [running]:\nmain.main()\n\t/workspace/main.go:20";
        assertTrue(ErrorExplainService.extractErrorType(s).contains("index out of range"));
    }
}
