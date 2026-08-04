package com.org.codeerrorexplainerdemo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.org.codeerrorexplainerdemo.dto.PageResult;
import com.org.codeerrorexplainerdemo.entity.AiCallLog;
import com.org.codeerrorexplainerdemo.mapper.AiCallLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * AI 调用日志：分页/筛选查询。
 */
@Service
@RequiredArgsConstructor
public class AiLogService {

    private final AiCallLogMapper aiCallLogMapper;

    public PageResult<AiCallLog> page(long page, long size, String status, String provider, Long historyId) {
        Page<AiCallLog> p = new Page<>(Math.max(page, 1), Math.max(size, 1));
        LambdaQueryWrapper<AiCallLog> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            wrapper.eq(AiCallLog::getStatus, status);
        }
        if (provider != null && !provider.isBlank()) {
            wrapper.eq(AiCallLog::getProvider, provider);
        }
        if (historyId != null) {
            wrapper.eq(AiCallLog::getHistoryId, historyId);
        }
        wrapper.orderByDesc(AiCallLog::getCreatedAt)
                .orderByDesc(AiCallLog::getId);
        IPage<AiCallLog> result = aiCallLogMapper.selectPage(p, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }
}
