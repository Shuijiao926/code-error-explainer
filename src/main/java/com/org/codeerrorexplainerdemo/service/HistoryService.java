package com.org.codeerrorexplainerdemo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.org.codeerrorexplainerdemo.common.BusinessException;
import com.org.codeerrorexplainerdemo.dto.PageResult;
import com.org.codeerrorexplainerdemo.entity.ErrorHistory;
import com.org.codeerrorexplainerdemo.mapper.ErrorHistoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 历史记录：分页/搜索/详情/删除/清空。
 */
@Service
@RequiredArgsConstructor
public class HistoryService {

    private final ErrorHistoryMapper historyMapper;

    public PageResult<ErrorHistory> page(long page, long size, String keyword) {
        Page<ErrorHistory> p = new Page<>(Math.max(page, 1), Math.max(size, 1));
        LambdaQueryWrapper<ErrorHistory> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(ErrorHistory::getErrorType, keyword)
                    .or().like(ErrorHistory::getSummary, keyword));
        }
        wrapper.orderByDesc(ErrorHistory::getCreatedAt)
                .orderByDesc(ErrorHistory::getId);
        // 列表只取轻量字段，避免大字段（堆栈/解释）拖慢列表
        wrapper.select(ErrorHistory::getId, ErrorHistory::getErrorType,
                ErrorHistory::getSummary, ErrorHistory::getCreatedAt);
        IPage<ErrorHistory> result = historyMapper.selectPage(p, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    public ErrorHistory detail(Long id) {
        ErrorHistory history = historyMapper.selectById(id);
        if (history == null) {
            throw new BusinessException("记录不存在");
        }
        return history;
    }

    public void delete(Long id) {
        if (historyMapper.selectById(id) == null) {
            throw new BusinessException("记录不存在");
        }
        historyMapper.deleteById(id);
    }

    public void clearAll() {
        historyMapper.delete(null);
    }
}
