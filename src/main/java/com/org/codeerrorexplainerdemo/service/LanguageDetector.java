package com.org.codeerrorexplainerdemo.service;

import java.util.regex.Pattern;

/**
 * 堆栈语言识别（启发式）。按特征优先判断：Python/Go/Java/Node，回退 unknown。
 */
public final class LanguageDetector {

    private static final Pattern PY_TRACEBACK = Pattern.compile("Traceback \\(most recent call last\\)");
    private static final Pattern PY_FILE_LINE = Pattern.compile("File \"[^\"]+\", line \\d+");
    private static final Pattern PY_RAISE = Pattern.compile("(?m)^\\s*raise ");
    private static final Pattern GO_PANIC = Pattern.compile("panic:");
    private static final Pattern GO_GOROUTINE = Pattern.compile("goroutine \\d+ \\[");
    private static final Pattern GO_FILE = Pattern.compile("\\.go:\\d+");
    private static final Pattern JAVA_QUALIFIED = Pattern.compile("java\\.lang\\.[\\w.]+(?:Exception|Error)");
    private static final Pattern JAVA_CAUSED_BY = Pattern.compile("Caused by:");
    private static final Pattern JAVA_FILE = Pattern.compile("\\.java:\\d+");
    private static final Pattern NODE_ERROR_NAME =
            Pattern.compile("\\b(TypeError|ReferenceError|RangeError|SyntaxError|URIError):");
    private static final Pattern NODE_FRAME =
            Pattern.compile("at .+\\(.+\\.(?:js|ts|mjs|cjs|mts|cts):\\d+:\\d+\\)");
    private static final Pattern JAVA_AT = Pattern.compile("(?m)^\\s*at\\s+[\\w.$]+\\([^)]*\\)");

    private LanguageDetector() {
    }

    public static String detect(String text) {
        if (text == null || text.isBlank()) {
            return "unknown";
        }
        if (PY_TRACEBACK.matcher(text).find()
                || PY_FILE_LINE.matcher(text).find()
                || PY_RAISE.matcher(text).find()) {
            return "python";
        }
        if (GO_PANIC.matcher(text).find()
                || GO_GOROUTINE.matcher(text).find()
                || GO_FILE.matcher(text).find()) {
            return "go";
        }
        if (JAVA_QUALIFIED.matcher(text).find()
                || JAVA_CAUSED_BY.matcher(text).find()
                || JAVA_FILE.matcher(text).find()) {
            return "java";
        }
        if (NODE_ERROR_NAME.matcher(text).find() || NODE_FRAME.matcher(text).find()) {
            return "node";
        }
        if (JAVA_AT.matcher(text).find()) {
            return "java";
        }
        return "unknown";
    }
}
