package com.org.codeerrorexplainerdemo.controller;

import com.org.codeerrorexplainerdemo.common.BusinessException;
import com.org.codeerrorexplainerdemo.common.Result;
import com.org.codeerrorexplainerdemo.dto.ArchiveRequest;
import com.org.codeerrorexplainerdemo.dto.DiagnosisEntry;
import com.org.codeerrorexplainerdemo.dto.DiagnosisRequest;
import com.org.codeerrorexplainerdemo.dto.ExplainRequest;
import com.org.codeerrorexplainerdemo.dto.ExplainResponse;
import com.org.codeerrorexplainerdemo.dto.KbStats;
import com.org.codeerrorexplainerdemo.dto.PageResult;
import com.org.codeerrorexplainerdemo.entity.ErrorHistory;
import com.org.codeerrorexplainerdemo.service.ErrorExplainService;
import com.org.codeerrorexplainerdemo.service.HistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/**
 * 对外接口：前置分诊、历史记录、踩坑知识库与调用日志。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ErrorExplainController {

    private final ErrorExplainService explainService;
    private final HistoryService historyService;

    @PostMapping("/explain")
    public Result<ExplainResponse> explain(@RequestBody ExplainRequest request) {
        return Result.ok(explainService.explain(request.getStackTrace()));
    }

    @GetMapping("/history")
    public Result<PageResult<ErrorHistory>> history(@RequestParam(defaultValue = "1") long page,
                                                    @RequestParam(defaultValue = "20") long size,
                                                    @RequestParam(required = false) String keyword,
                                                    @RequestParam(defaultValue = "all") String status) {
        return Result.ok(historyService.page(page, size, keyword, status));
    }

    @GetMapping("/history/{id}")
    public Result<ErrorHistory> historyDetail(@PathVariable Long id) {
        return Result.ok(historyService.detail(id));
    }

    @DeleteMapping("/history/{id}")
    public Result<Void> deleteHistory(@PathVariable Long id) {
        historyService.delete(id);
        return Result.ok();
    }

    @DeleteMapping("/history")
    public Result<Void> clearHistory() {
        historyService.clearAll();
        return Result.ok();
    }

    /** 回填/追加项目 AI 的最终诊断（多条保留） */
    @PutMapping("/history/{id}/diagnosis")
    public Result<List<DiagnosisEntry>> backfillDiagnosis(@PathVariable Long id,
                                                          @RequestBody DiagnosisRequest request) {
        return Result.ok(historyService.saveDiagnosis(id, request.getDiagnosis()));
    }

    /** 沉淀/取消沉淀为踩坑档案，可同时更新标签 */
    @PutMapping("/history/{id}/archive")
    public Result<ErrorHistory> archive(@PathVariable Long id, @RequestBody ArchiveRequest request) {
        return Result.ok(historyService.archive(id, request.getArchived(), request.getTags()));
    }

    /** 踩坑知识库检索（独立于 /api/history，筛选条件更丰富） */
    @GetMapping("/kb")
    public Result<PageResult<ErrorHistory>> kb(@RequestParam(defaultValue = "1") long page,
                                               @RequestParam(defaultValue = "20") long size,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) String language,
                                               @RequestParam(required = false) String techStack,
                                               @RequestParam(required = false) Boolean archived,
                                               @RequestParam(required = false) String startTime,
                                               @RequestParam(required = false) String endTime,
                                               @RequestParam(required = false) String tag) {
        return Result.ok(historyService.kbPage(page, size, keyword, language, techStack,
                archived, startTime, endTime, tag));
    }

    /**
     * 踩坑知识库统计。
     * 异常类型 TopN（topN 默认 10）+ 语言/技术栈分布 + 近 N 天新增趋势（days 默认 30）。
     * 筛选参数与 /api/kb 一致，archived 为 true 时只统计已沉淀档案。
     */
    @GetMapping("/kb/stats")
    public Result<KbStats> kbStats(@RequestParam(required = false) Boolean archived,
                                   @RequestParam(required = false) String keyword,
                                   @RequestParam(required = false) String language,
                                   @RequestParam(required = false) String techStack,
                                   @RequestParam(required = false) String startTime,
                                   @RequestParam(required = false) String endTime,
                                   @RequestParam(required = false) String tag,
                                   @RequestParam(defaultValue = "10") int topN,
                                   @RequestParam(defaultValue = "30") int days) {
        return Result.ok(historyService.stats(archived, keyword, language, techStack,
                startTime, endTime, tag, topN, days));
    }

    /**
     * 踩坑知识库导出。format=markdown|csv，复用检索筛选参数，以附件形式下载。
     * csv 为 UTF-8 带 BOM，Excel 可直接打开。
     */
    @GetMapping("/kb/export")
    public ResponseEntity<byte[]> kbExport(@RequestParam(defaultValue = "markdown") String format,
                                           @RequestParam(required = false) Boolean archived,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) String language,
                                           @RequestParam(required = false) String techStack,
                                           @RequestParam(required = false) String startTime,
                                           @RequestParam(required = false) String endTime,
                                           @RequestParam(required = false) String tag) {
        String f = (format == null || format.isBlank()) ? "markdown" : format.trim().toLowerCase();
        byte[] content;
        MediaType mediaType;
        String filename;
        if ("csv".equals(f)) {
            content = historyService.exportCsv(keyword, language, techStack, archived, startTime, endTime, tag);
            mediaType = MediaType.parseMediaType("text/csv;charset=UTF-8");
            filename = "kb-export-" + LocalDate.now() + ".csv";
        } else if ("markdown".equals(f)) {
            content = historyService.exportMarkdown(keyword, language, techStack, archived, startTime, endTime, tag)
                    .getBytes(StandardCharsets.UTF_8);
            mediaType = MediaType.parseMediaType("text/markdown;charset=UTF-8");
            filename = "kb-export-" + LocalDate.now() + ".md";
        } else {
            throw new BusinessException("format 仅支持 markdown 或 csv");
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(mediaType)
                .body(content);
    }

    @GetMapping("/health")
    public Result<String> health() {
        return Result.ok("ok");
    }
}
