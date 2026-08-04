package com.org.codeerrorexplainerdemo.service.ai;

import com.org.codeerrorexplainerdemo.entity.AiCallLog;
import com.org.codeerrorexplainerdemo.mapper.AiCallLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * AI 调用日志记录器：落库一次调用，并在 error_history 入库后回填关联 id。
 */
@Component
@RequiredArgsConstructor
public class AiCallRecorder {

    private static final int EXCERPT_MAX = 200;

    private final AiCallLogMapper aiCallLogMapper;

    /** 记录一次 AI 调用，返回日志 id（供 history 入库后回填） */
    public Long record(AiCallLog log) {
        aiCallLogMapper.insert(log);
        return log.getId();
    }

    /** history 入库后回填 history_id（仅更新该列，不覆盖其他字段） */
    public void linkHistory(Long logId, Long historyId) {
        if (logId == null) {
            return;
        }
        AiCallLog update = new AiCallLog();
        update.setId(logId);
        update.setHistoryId(historyId);
        aiCallLogMapper.updateById(update);
    }

    /** 输入堆栈摘要：去空白 + 截断前 200 字符 */
    public static String excerpt(String stackTrace) {
        if (stackTrace == null) {
            return null;
        }
        String compact = stackTrace.replaceAll("\\s+", " ").trim();
        return compact.length() <= EXCERPT_MAX ? compact : compact.substring(0, EXCERPT_MAX);
    }

    /** 耗时毫秒 */
    public static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
