package com.org.codeerrorexplainerdemo.controller;

import com.org.codeerrorexplainerdemo.common.Result;
import com.org.codeerrorexplainerdemo.dto.DiagnosisEntry;
import com.org.codeerrorexplainerdemo.dto.DiagnosisRequest;
import com.org.codeerrorexplainerdemo.dto.ExplainRequest;
import com.org.codeerrorexplainerdemo.dto.ExplainResponse;
import com.org.codeerrorexplainerdemo.dto.PageResult;
import com.org.codeerrorexplainerdemo.entity.ErrorHistory;
import com.org.codeerrorexplainerdemo.service.ErrorExplainService;
import com.org.codeerrorexplainerdemo.service.HistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 核心接口：堆栈翻译 + 历史记录。
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

    @GetMapping("/health")
    public Result<String> health() {
        return Result.ok("ok");
    }
}
