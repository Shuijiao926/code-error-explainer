package com.org.codeerrorexplainerdemo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.org.codeerrorexplainerdemo.config.AiProperties;
import com.org.codeerrorexplainerdemo.entity.AiCallLog;
import com.org.codeerrorexplainerdemo.mapper.AiCallLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * ai_call_log 保留期清理：每日凌晨删除超过保留天数的日志（默认 30 天，可配 ai.call-log-retention-days）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiLogRetentionTask {

    private final AiCallLogMapper aiCallLogMapper;
    private final AiProperties aiProperties;

    @Scheduled(cron = "0 0 3 * * *")
    public void purgeExpired() {
        int days = aiProperties.getCallLogRetentionDays();
        if (days <= 0) {
            return;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
        int deleted = aiCallLogMapper.delete(
                new LambdaQueryWrapper<AiCallLog>().lt(AiCallLog::getCreatedAt, cutoff));
        if (deleted > 0) {
            log.info("ai_call_log retention: purged {} rows older than {} days", deleted, days);
        }
    }
}
