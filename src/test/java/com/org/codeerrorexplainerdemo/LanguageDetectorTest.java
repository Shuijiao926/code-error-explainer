package com.org.codeerrorexplainerdemo;

import com.org.codeerrorexplainerdemo.service.LanguageDetector;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LanguageDetectorTest {

    @Test
    void detectsJava() {
        assertThat(LanguageDetector.detect(
                "java.lang.NullPointerException: x\n\tat com.app.UserService.login(UserService.java:47)"))
                .isEqualTo("java");
    }

    @Test
    void detectsPython() {
        assertThat(LanguageDetector.detect(
                "Traceback (most recent call last):\n  File \"app.py\", line 5, in <module>\n    raise ValueError(\"bad\")"))
                .isEqualTo("python");
    }

    @Test
    void detectsNode() {
        assertThat(LanguageDetector.detect(
                "TypeError: Cannot read properties of undefined (reading 'name')\n"
                        + "    at Object.<anonymous> (/app/main.js:8:5)"))
                .isEqualTo("node");
    }

    @Test
    void detectsGo() {
        assertThat(LanguageDetector.detect(
                "panic: runtime error: invalid memory address or nil pointer dereference\n"
                        + "[signal SIGSEGV: segmentation violation]\n"
                        + "goroutine 1 [running]:\nmain.main()\n\t/workspace/main.go:15"))
                .isEqualTo("go");
    }

    @Test
    void unknownForPlainText() {
        assertThat(LanguageDetector.detect("hello world, nothing here")).isEqualTo("unknown");
        assertThat(LanguageDetector.detect(null)).isEqualTo("unknown");
    }
}
