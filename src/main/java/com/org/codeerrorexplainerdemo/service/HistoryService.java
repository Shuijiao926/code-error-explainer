package com.org.codeerrorexplainerdemo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.org.codeerrorexplainerdemo.common.BusinessException;
import com.org.codeerrorexplainerdemo.dto.DiagnosisEntry;
import com.org.codeerrorexplainerdemo.dto.KbStats;
import com.org.codeerrorexplainerdemo.dto.NameCount;
import com.org.codeerrorexplainerdemo.dto.PageResult;
import com.org.codeerrorexplainerdemo.dto.TrendPoint;
import com.org.codeerrorexplainerdemo.entity.ErrorHistory;
import com.org.codeerrorexplainerdemo.entity.HistoryDiagnosis;
import com.org.codeerrorexplainerdemo.mapper.ErrorHistoryMapper;
import com.org.codeerrorexplainerdemo.mapper.HistoryDiagnosisMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 历史记录：分页/搜索/详情/删除/清空 + 项目 AI 诊断追加回填。
 * V1.5：诊断改为 history_diagnosis 子表一行一条（替代 final_diagnosis 单列 JSON 追加），
 * 对外字段 finalDiagnosis 由子表重建，保持前端契约不变。
 */
@Service
@RequiredArgsConstructor
public class HistoryService {

    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_STACK_CHARS = 1500;

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

    /**
     * 踩坑档案知识库分页检索（V1.6，新增 /api/kb，不破坏 /api/history）。
     * keyword 同时匹配 error_type / summary / tech_stack / 回填诊断内容（history_diagnosis.content）。
     */
    public PageResult<ErrorHistory> kbPage(long page, long size, String keyword, String language,
                                           String techStack, Boolean archived, String startTime,
                                           String endTime, String tag) {
        Page<ErrorHistory> p = new Page<>(Math.max(page, 1), Math.max(size, 1));
        LambdaQueryWrapper<ErrorHistory> wrapper = buildKbWrapper(keyword, language, techStack,
                archived, startTime, endTime, tag);
        // 已沉淀档案优先，再按时间倒序
        wrapper.orderByDesc(ErrorHistory::getIsArchived)
                .orderByDesc(ErrorHistory::getCreatedAt)
                .orderByDesc(ErrorHistory::getId);
        wrapper.select(ErrorHistory::getId, ErrorHistory::getErrorType, ErrorHistory::getLanguage,
                ErrorHistory::getTechStack, ErrorHistory::getSummary, ErrorHistory::getTags,
                ErrorHistory::getIsArchived, ErrorHistory::getCreatedAt);
        IPage<ErrorHistory> result = historyMapper.selectPage(p, wrapper);
        fillDiagnosedFlag(result.getRecords());
        return PageResult.of(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    /**
     * 知识库筛选条件构造（keyword/language/techStack/archived/startTime/endTime/tag），
     * 供 /api/kb 检索、/api/kb/stats 统计、/api/kb/export 导出共用，保证口径一致。
     */
    private LambdaQueryWrapper<ErrorHistory> buildKbWrapper(String keyword, String language,
                                                           String techStack, Boolean archived,
                                                           String startTime, String endTime, String tag) {
        LambdaQueryWrapper<ErrorHistory> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(ErrorHistory::getErrorType, kw)
                    .or().like(ErrorHistory::getSummary, kw)
                    .or().like(ErrorHistory::getTechStack, kw)
                    // 命中任一条回填诊断内容（{0} 为参数绑定，防注入）
                    .or().apply("EXISTS (SELECT 1 FROM history_diagnosis hd WHERE hd.history_id = error_history.id AND hd.content LIKE {0})",
                            "%" + kw + "%"));
        }
        if (language != null && !language.isBlank()) {
            wrapper.eq(ErrorHistory::getLanguage, language.trim());
        }
        if (techStack != null && !techStack.isBlank()) {
            wrapper.like(ErrorHistory::getTechStack, techStack.trim());
        }
        if (archived != null) {
            wrapper.eq(ErrorHistory::getIsArchived, archived);
        }
        if (tag != null && !tag.isBlank()) {
            wrapper.like(ErrorHistory::getTags, tag.trim());
        }
        LocalDateTime start = parseTime(startTime, false);
        LocalDateTime end = parseTime(endTime, true);
        if (start != null) {
            wrapper.ge(ErrorHistory::getCreatedAt, start);
        }
        if (end != null) {
            wrapper.le(ErrorHistory::getCreatedAt, end);
        }
        return wrapper;
    }

    /**
     * 沉淀/取消沉淀为踩坑档案，并可选更新标签。
     * tags 为 null 表示不更新；传空串表示清空标签。
     */
    public ErrorHistory archive(Long id, Boolean archived, String tags) {
        if (archived == null) {
            throw new BusinessException("archived 不能为空");
        }
        ErrorHistory history = historyMapper.selectById(id);
        if (history == null) {
            throw new BusinessException("记录不存在");
        }
        if (tags != null && tags.length() > 500) {
            throw new BusinessException("tags 过长（最多 500 字符）");
        }
        history.setIsArchived(archived);
        if (tags != null) {
            history.setTags(tags);
        }
        historyMapper.updateById(history);
        return history;
    }

    /**
     * 踩坑知识库统计（V1.6，GET /api/kb/stats）。
     * 数据量在当前演示规模下采用内存聚合，保证与库内数据严格一致；
     * 命中条件复用 buildKbWrapper，口径与 /api/kb 检索一致。空库返回零值，不报错。
     */
    public KbStats stats(Boolean archived, String keyword, String language, String techStack,
                         String startTime, String endTime, String tag, int topN, int days) {
        LambdaQueryWrapper<ErrorHistory> wrapper = buildKbWrapper(keyword, language, techStack,
                archived, startTime, endTime, tag);
        wrapper.select(ErrorHistory::getId, ErrorHistory::getErrorType, ErrorHistory::getLanguage,
                ErrorHistory::getTechStack, ErrorHistory::getCreatedAt);
        List<ErrorHistory> records = historyMapper.selectList(wrapper);

        // 1) 总数
        long total = records.size();

        // 2) 异常类型 TopN（topN<=0 表示全量）
        Map<String, Long> typeCount = new LinkedHashMap<>();
        for (ErrorHistory h : records) {
            if (h.getErrorType() != null && !h.getErrorType().isBlank()) {
                typeCount.merge(h.getErrorType(), 1L, Long::sum);
            }
        }
        List<NameCount> errorTypeTop = toNameCounts(typeCount, topN);

        // 3) 语言分布（空语言不参与分布）
        Map<String, Long> langCount = new LinkedHashMap<>();
        for (ErrorHistory h : records) {
            if (h.getLanguage() != null && !h.getLanguage().isBlank()) {
                langCount.merge(h.getLanguage(), 1L, Long::sum);
            }
        }
        List<NameCount> languageDist = toNameCounts(langCount, 0);

        // 4) 技术栈分布：逗号分隔字段拆开后逐项计数
        Map<String, Long> stackCount = new LinkedHashMap<>();
        for (ErrorHistory h : records) {
            if (h.getTechStack() == null || h.getTechStack().isBlank()) {
                continue;
            }
            for (String item : h.getTechStack().split(",")) {
                String s = item.trim();
                if (!s.isEmpty()) {
                    stackCount.merge(s, 1L, Long::sum);
                }
            }
        }
        List<NameCount> techStackDist = toNameCounts(stackCount, 0);

        // 5) 近 N 天新增趋势（默认近 30 天含当天；startTime+endTime 同时提供时按该范围逐日聚合）
        List<TrendPoint> trend = buildTrend(records, startTime, endTime, days);

        return KbStats.builder()
                .total(total)
                .errorTypeTop(errorTypeTop)
                .languageDist(languageDist)
                .techStackDist(techStackDist)
                .trend(trend)
                .build();
    }

    /**
     * 导出 Markdown（GET /api/kb/export?format=markdown）。
     * 每条档案：标题（异常类型 + 概要）+ 元信息（language/tech_stack/tags/created_at）
     * + 堆栈（超长截断）+ 回填诊断列表。
     */
    public String exportMarkdown(String keyword, String language, String techStack,
                                 Boolean archived, String startTime, String endTime, String tag) {
        List<ErrorHistory> records = kbListAll(keyword, language, techStack, archived, startTime, endTime, tag);
        Map<Long, List<HistoryDiagnosis>> diagnoses = loadDiagnosesByHistory(records);

        StringBuilder sb = new StringBuilder();
        sb.append("# 踩坑知识库导出\n\n");
        int index = 1;
        for (ErrorHistory h : records) {
            sb.append("## ").append(index++).append(". ")
                    .append(flat(h.getErrorType())).append(" — ").append(flat(h.getSummary())).append("\n\n");
            sb.append("- 语言：").append(flat(h.getLanguage())).append("\n");
            sb.append("- 技术栈：").append(flat(h.getTechStack())).append("\n");
            sb.append("- 标签：").append(flat(h.getTags())).append("\n");
            sb.append("- 时间：").append(formatDateTime(h.getCreatedAt())).append("\n\n");
            sb.append("### 堆栈\n```\n").append(truncateStack(h.getStackTrace())).append("\n```\n\n");
            List<HistoryDiagnosis> list = diagnoses.getOrDefault(h.getId(), List.of());
            if (!list.isEmpty()) {
                sb.append("### 回填诊断\n");
                for (HistoryDiagnosis d : list) {
                    sb.append("- [").append(flat(d.getTs())).append("] ").append(flat(d.getContent())).append("\n");
                }
                sb.append("\n");
            }
            sb.append("---\n\n");
        }
        return sb.toString();
    }

    /**
     * 导出 CSV（GET /api/kb/export?format=csv），UTF-8 带 BOM，Excel 可直接打开。
     * 列：error_type, language, tech_stack, summary, tags, created_at, diagnosis。
     */
    public byte[] exportCsv(String keyword, String language, String techStack,
                            Boolean archived, String startTime, String endTime, String tag) {
        List<ErrorHistory> records = kbListAll(keyword, language, techStack, archived, startTime, endTime, tag);
        Map<Long, List<HistoryDiagnosis>> diagnoses = loadDiagnosesByHistory(records);

        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF'); // UTF-8 BOM，Excel 兼容
        sb.append(csvField("error_type")).append(',').append(csvField("language")).append(',')
                .append(csvField("tech_stack")).append(',').append(csvField("summary")).append(',')
                .append(csvField("tags")).append(',').append(csvField("created_at")).append(',')
                .append(csvField("diagnosis")).append("\r\n");
        for (ErrorHistory h : records) {
            String diagnosis = diagnoses.getOrDefault(h.getId(), List.of()).stream()
                    .map(HistoryDiagnosis::getContent)
                    .filter(d -> d != null && !d.isBlank())
                    .collect(Collectors.joining(" \n "));
            sb.append(csvField(h.getErrorType())).append(',')
                    .append(csvField(h.getLanguage())).append(',')
                    .append(csvField(h.getTechStack())).append(',')
                    .append(csvField(h.getSummary())).append(',')
                    .append(csvField(h.getTags())).append(',')
                    .append(csvField(formatDateTime(h.getCreatedAt()))).append(',')
                    .append(csvField(diagnosis)).append("\r\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** 导出用：命中筛选条件的全部记录（不含分页，含堆栈与诊断，时间倒序） */
    private List<ErrorHistory> kbListAll(String keyword, String language, String techStack,
                                         Boolean archived, String startTime, String endTime, String tag) {
        LambdaQueryWrapper<ErrorHistory> wrapper = buildKbWrapper(keyword, language, techStack,
                archived, startTime, endTime, tag);
        wrapper.orderByDesc(ErrorHistory::getCreatedAt).orderByDesc(ErrorHistory::getId);
        return historyMapper.selectList(wrapper);
    }

    /** Map(name->count) 转分布列表，按数量降序；limit<=0 表示不限制条数 */
    private List<NameCount> toNameCounts(Map<String, Long> counts, int limit) {
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(limit > 0 ? limit : Long.MAX_VALUE)
                .map(e -> NameCount.builder().name(e.getKey()).count(e.getValue()).build())
                .toList();
    }

    /** 按日聚合新增趋势：默认近 days 天（含当天）零值天补齐；startTime+endTime 同时提供时按该范围 */
    private List<TrendPoint> buildTrend(List<ErrorHistory> records, String startTime, String endTime, int days) {
        LocalDate today = LocalDate.now();
        LocalDate startDate;
        LocalDate endDate;
        if (startTime != null && !startTime.isBlank() && endTime != null && !endTime.isBlank()) {
            startDate = dateOf(parseTime(startTime, false));
            endDate = dateOf(parseTime(endTime, true));
        } else {
            startDate = today.minusDays(Math.max(days, 1) - 1L);
            endDate = today;
        }
        if (startDate.isAfter(endDate)) {
            throw new BusinessException("时间范围无效：开始时间晚于结束时间");
        }
        Map<LocalDate, Long> byDay = new LinkedHashMap<>();
        for (ErrorHistory h : records) {
            if (h.getCreatedAt() != null) {
                LocalDate d = h.getCreatedAt().toLocalDate();
                if (!d.isBefore(startDate) && !d.isAfter(endDate)) {
                    byDay.merge(d, 1L, Long::sum);
                }
            }
        }
        List<TrendPoint> trend = new ArrayList<>();
        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            trend.add(TrendPoint.builder()
                    .date(d.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                    .count(byDay.getOrDefault(d, 0L))
                    .build());
        }
        return trend;
    }

    private LocalDate dateOf(LocalDateTime time) {
        return time == null ? LocalDate.now() : time.toLocalDate();
    }

    /** 批量加载记录的全部诊断，按 historyId 分组（时间 + 插入序） */
    private Map<Long, List<HistoryDiagnosis>> loadDiagnosesByHistory(List<ErrorHistory> records) {
        if (records.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = records.stream().map(ErrorHistory::getId).toList();
        return diagnosisMapper.selectList(
                new LambdaQueryWrapper<HistoryDiagnosis>()
                        .in(HistoryDiagnosis::getHistoryId, ids)
                        .orderByAsc(HistoryDiagnosis::getTs)
                        .orderByAsc(HistoryDiagnosis::getId)
        ).stream().collect(Collectors.groupingBy(HistoryDiagnosis::getHistoryId));
    }

    /** 单行文本化：null→""，内部换行折叠为空格，避免破坏 markdown/CSV 结构 */
    private String flat(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[\\r\\n]+", " ").trim();
    }

    /** 堆栈截断：超过 MAX_STACK_CHARS 截断并追加标记 */
    private String truncateStack(String stack) {
        if (stack == null) {
            return "";
        }
        if (stack.length() <= MAX_STACK_CHARS) {
            return stack;
        }
        return stack.substring(0, MAX_STACK_CHARS) + "\n...（堆栈过长已截断）";
    }

    private String formatDateTime(LocalDateTime time) {
        return time == null ? "" : time.format(TS_FORMAT);
    }

    /** CSV 字段转义：含逗号/引号/换行时加双引号包裹，内部引号翻倍 */
    private String csvField(String value) {
        if (value == null) {
            return "";
        }
        boolean needQuote = value.contains(",") || value.contains("\"")
                || value.contains("\n") || value.contains("\r");
        String escaped = value.replace("\"", "\"\"");
        return needQuote ? "\"" + escaped + "\"" : escaped;
    }

    /** 时间参数解析：支持 yyyy-MM-dd（endOfDay 取当日 23:59:59）或 yyyy-MM-dd HH:mm:ss */
    private LocalDateTime parseTime(String value, boolean endOfDay) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim();
        try {
            if (v.length() == 10) {
                LocalDate date = LocalDate.parse(v, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                return endOfDay ? date.atTime(LocalTime.of(23, 59, 59)) : date.atStartOfDay();
            }
            return LocalDateTime.parse(v, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception e) {
            throw new BusinessException("时间参数格式应为 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss：" + v);
        }
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
