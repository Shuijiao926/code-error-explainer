package com.org.codeerrorexplainerdemo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.org.codeerrorexplainerdemo.common.BusinessException;
import com.org.codeerrorexplainerdemo.dto.DiagnosisEntry;
import com.org.codeerrorexplainerdemo.dto.PageResult;
import com.org.codeerrorexplainerdemo.entity.ErrorHistory;
import com.org.codeerrorexplainerdemo.mapper.ErrorHistoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 历史记录：分页/搜索/详情/删除/清空 + 项目 AI 诊断追加回填。
 */
@Service
@RequiredArgsConstructor
public class HistoryService {

    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ErrorHistoryMapper historyMapper;
    private final ObjectMapper objectMapper;

    public PageResult<ErrorHistory> page(long page, long size, String keyword) {
        Page<ErrorHistory> p = new Page<>(Math.max(page, 1), Math.max(size, 1));
        LambdaQueryWrapper<ErrorHistory> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(ErrorHistory::getErrorType, keyword)
                    .or().like(ErrorHistory::getSummary, keyword)
                    .or().like(ErrorHistory::getTechStack, keyword));
        }
        wrapper.orderByDesc(ErrorHistory::getCreatedAt)
                .orderByDesc(ErrorHistory::getId);
        // 列表只取轻量字段（含技术栈与诊断状态，用于标签展示）
        wrapper.select(ErrorHistory::getId, ErrorHistory::getErrorType, ErrorHistory::getTechStack,
                ErrorHistory::getSummary, ErrorHistory::getFinalDiagnosis, ErrorHistory::getCreatedAt);
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

    /**
     * 追加一条项目 AI 的最终诊断（多条保留，带时间戳）。
     *
     * @return 追加后的完整诊断记录列表
     */
    public List<DiagnosisEntry> saveDiagnosis(Long id, String diagnosis) {
        if (diagnosis == null || diagnosis.isBlank()) {
            throw new BusinessException("诊断内容不能为空");
        }
        ErrorHistory history = historyMapper.selectById(id);
        if (history == null) {
            throw new BusinessException("记录不存在");
        }
        List<DiagnosisEntry> entries = parseEntries(history.getFinalDiagnosis());
        entries.add(DiagnosisEntry.builder()
                .ts(LocalDateTime.now().format(TS_FORMAT))
                .content(diagnosis)
                .build());
        history.setFinalDiagnosis(writeEntries(entries));
        historyMapper.updateById(history);
        return entries;
    }

    private List<DiagnosisEntry> parseEntries(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<DiagnosisEntry>>() {
            });
        } catch (Exception e) {
            throw new BusinessException("诊断记录解析失败：" + e.getMessage());
        }
    }

    private String writeEntries(List<DiagnosisEntry> entries) {
        try {
            return objectMapper.writeValueAsString(entries);
        } catch (Exception e) {
            throw new BusinessException("诊断记录序列化失败：" + e.getMessage());
        }
    }
}
