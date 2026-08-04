package com.org.codeerrorexplainerdemo.controller;

import com.org.codeerrorexplainerdemo.common.Result;
import com.org.codeerrorexplainerdemo.dto.PageResult;
import com.org.codeerrorexplainerdemo.entity.AiCallLog;
import com.org.codeerrorexplainerdemo.service.AiLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 调用日志查询接口（可观测性）。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AiLogController {

    private final AiLogService aiLogService;

    @GetMapping("/ai-logs")
    public Result<PageResult<AiCallLog>> aiLogs(@RequestParam(defaultValue = "1") long page,
                                                @RequestParam(defaultValue = "20") long size,
                                                @RequestParam(required = false) String status,
                                                @RequestParam(required = false) String provider,
                                                @RequestParam(required = false) Long historyId) {
        return Result.ok(aiLogService.page(page, size, status, provider, historyId));
    }
}
