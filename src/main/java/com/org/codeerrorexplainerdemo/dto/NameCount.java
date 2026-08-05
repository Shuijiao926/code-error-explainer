package com.org.codeerrorexplainerdemo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统计分布项：名称 + 数量，供前端图表消费（异常类型/语言/技术栈分布通用）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NameCount {

    /** 分布名称：异常类型 / 语言 / 技术栈 */
    private String name;

    /** 数量 */
    private long count;
}
