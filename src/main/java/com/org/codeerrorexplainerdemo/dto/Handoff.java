package com.org.codeerrorexplainerdemo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 前置分诊报告·项目AI诊断包（面向项目 AI，精确可操作）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Handoff {

    /** 技术栈（由 TechStackDetector 识别或用户修正） */
    private List<String> techStack;

    /** 疑似不妥点（技术向） */
    private List<String> suspectedPoints;

    /** 待查证项（需要项目 AI 结合仓库上下文确认） */
    private List<String> checkList;

    /** 原始异常堆栈 */
    private String rawError;
}
