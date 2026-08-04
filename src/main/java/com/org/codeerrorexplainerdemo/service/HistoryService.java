package com.org.codeerrorexplainerdemo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.org.codeerrorexplainerdemo.common.BusinessException;
import com.org.codeerrorexplainerdemo.dto.DiagnosisEntry;
import com.org.codeerrorexplainerdemo.dto.PageResult;
import com.org.codeerrorexplainerdemo.entity.ErrorHistory;
import com.org.codeerrorexplainerdemo.entity.HistoryDiagnosis;
import com.org.codeerrorexplainerdemo.mapper.ErrorHistoryMapper;
import com.org.codeerrorexplainerdemo.mapper.HistoryDiagnosisMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 历史记录：分页/搜索/详情/删除/清空 + 项目 AI 诊断追加回填。
 * V1.5：诊断改为 history_diagnosis 子表一行一条（替代 final_diagnosis 单列 JSON 追加），
 * 对外字段 finalDiagnosis 由子表重建，保持前端契约不变。
 */
@Service
@RequiredArgsConstructor
public class HistoryService {

    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ErrorHistoryMapper historyMapper;
    private final HistoryDiagnosisMapper diagnosisMapper;
    private final ObjectMapper objectMapper;

    public PageResult<ErrorHistory> page(long page, long size, String keyword) {
        return page(page, size, keyword, "all");
    }

    public PageResult<ErrorHistory> page(long page, long size, String keyword, String status) {
        Page<ErrorHistory> p = new Page<>(Math.max(page, 1), Math.max(size, 1));
        LambdaQueryWrapper<ErrorHistory> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(ErrorHistory::getErrorType, keyword)
                    .or().like(ErrorHistory::getSummary, keyword)
                    .or().like(ErrorHistory::getTechStack, keyword));
        }
        // 状态筛选：pending=无诊断，diagnosed=有诊断（基于子表存在性）
        if ("pending".equalsIgnoreCase(status)) {
            wrapper.apply("NOT EXISTS (SELECT 1 FROM history_diagnosis hd WHERE hd.history_id = error_history.id)");
        } else if ("diagnosed".equalsIgnoreCase(status)) {
            wrapper.apply("EXISTS (SELECT 1 FROM history_diagnosis hd WHERE hd.history_id = error_history.id)");
        }
        wrapper.orderByDesc(ErrorHistory::getCreatedAt)
                .orderByDesc(ErrorHistory::getId);
        // 列表只取轻量字段（finalDiagnosis 由子表批量重建，不读遗留列）
        wrapper.select(ErrorHistory::getId, ErrorHistory::getErrorType, ErrorHistory::getTechStack,
                ErrorHistory::getSummary, ErrorHistory::getCreatedAt);
        IPage<ErrorHistory> result = historyMapper.selectPage(p, wrapper);
        fillDiagnosedFlag(result.getRecords());
        return PageResult.of(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    /** 列表记录按 historyId 批量查子表，有诊断置 "[]"（前端 truthy 判"已诊断"），无则 null */
    private void fillDiagnosedFlag(List<ErrorHistory> records) {
        if (records.isEmpty()) {
            return;
        }
        List<Long> ids = records.stream().map(ErrorHistory::getId).toList();
        Set<Long> diagnosed = new HashSet<>(
                diagnosisMapper.selectList(
                        new LambdaQueryWrapper<HistoryDiagnosis>().in(HistoryDiagnosis::getHistoryId, ids)
                ).stream().map(HistoryDiagnosis::getHistoryId).toList());
        for (ErrorHistory h : records) {
            h.setFinalDiagnosis(diagnosed.contains(h.getId()) ? "[]" : null);
        }
    }

    public ErrorHistory detail(Long id) {
        ErrorHistory history = historyMapper.selectById(id);
        if (history == null) {
            throw new BusinessException("记录不存在");
        }
        // finalDiagnosis 由子表重建，保持前端详情契约不变
        history.setFinalDiagnosis(toJson(listDiagnoses(id)));
        return history;
    }

    public void delete(Long id) {
        if (historyMapper.selectById(id) == null) {
            throw new BusinessException("记录不存在");
        }
        diagnosisMapper.delete(new LambdaQueryWrapper<HistoryDiagnosis>()
                .eq(HistoryDiagnosis::getHistoryId, id));
        historyMapper.deleteById(id);
    }

    public void clearAll() {
        diagnosisMapper.delete(null);
        historyMapper.delete(null);
    }

    /**
     * 追加一条项目 AI 的最终诊断（子表一行一条，可多条保留）。
     *
     * @return 追加后的完整诊断记录列表
     */
    public List<DiagnosisEntry> saveDiagnosis(Long id, String diagnosis) {
        if (diagnosis == null || diagnosis.isBlank()) {
            throw new BusinessException("诊断内容不能为空");
        }
        if (historyMapper.selectById(id) == null) {
            throw new BusinessException("记录不存在");
        }
        HistoryDiagnosis row = new HistoryDiagnosis();
        row.setHistoryId(id);
        row.setContent(diagnosis);
        row.setTs(LocalDateTime.now().format(TS_FORMAT));
        diagnosisMapper.insert(row);
        return listDiagnoses(id);
    }

    /** 该 history 的全部诊断（按时间+插入序） */
    private List<DiagnosisEntry> listDiagnoses(Long historyId) {
        return diagnosisMapper.selectList(
                new LambdaQueryWrapper<HistoryDiagnosis>()
                        .eq(HistoryDiagnosis::getHistoryId, historyId)
                        .orderByAsc(HistoryDiagnosis::getTs)
                        .orderByAsc(HistoryDiagnosis::getId)
        ).stream().map(d -> DiagnosisEntry.builder().ts(d.getTs()).content(d.getContent()).build()).toList();
    }

    private String toJson(List<DiagnosisEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(entries);
        } catch (Exception e) {
            throw new BusinessException("诊断记录序列化失败：" + e.getMessage());
        }
    }
}
