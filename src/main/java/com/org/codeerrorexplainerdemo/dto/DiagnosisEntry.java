package com.org.codeerrorexplainerdemo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 一条项目 AI 最终诊断记录（可多条追加）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiagnosisEntry {

    /** 回填时间，yyyy-MM-dd HH:mm:ss */
    private String ts;

    /** 诊断内容 */
    private String content;
}
