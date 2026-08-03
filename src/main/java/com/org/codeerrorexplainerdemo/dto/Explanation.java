package com.org.codeerrorexplainerdemo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * AI 结构化解释五要素。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Explanation {

    /** 一句话概述 */
    private String overview;

    /** 出问题的地方（文件+方法+行号） */
    private String location;

    /** 根因分析 */
    private String rootCause;

    /** 修复步骤 */
    private List<String> fixSteps;

    /** 以后怎么避免 */
    private String prevention;
}
