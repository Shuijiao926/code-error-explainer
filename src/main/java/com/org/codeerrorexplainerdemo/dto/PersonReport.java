package com.org.codeerrorexplainerdemo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 前置分诊报告·人话版（面向非 IT，通俗易懂）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PersonReport {

    /** 一句话定性（大白话） */
    private String summary;

    /** 出在哪一层（业务/框架/基础设施）+ 人话解释 */
    private String layer;

    /** 疑似技术使用不妥（人话，方向非断言） */
    private String misuse;

    /** 你现在可以怎么做（引导：复制诊断包发给项目 AI） */
    private String nextStep;
}
