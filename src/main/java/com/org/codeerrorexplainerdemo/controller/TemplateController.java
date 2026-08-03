package com.org.codeerrorexplainerdemo.controller;

import com.org.codeerrorexplainerdemo.common.Result;
import com.org.codeerrorexplainerdemo.entity.ErrorTemplate;
import com.org.codeerrorexplainerdemo.service.TemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 异常知识卡片接口（离线速查）。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;

    @GetMapping("/templates")
    public Result<List<ErrorTemplate>> templates(@RequestParam(required = false) String keyword) {
        return Result.ok(templateService.list(keyword));
    }
}
