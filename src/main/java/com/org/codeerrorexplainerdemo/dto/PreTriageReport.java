package com.org.codeerrorexplainerdemo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 前置分诊报告（双段式）：人话版 + 项目AI诊断包。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreTriageReport {

    private PersonReport personReport;

    private Handoff handoff;
}
