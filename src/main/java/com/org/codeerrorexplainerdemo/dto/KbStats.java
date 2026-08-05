package com.org.codeerrorexplainerdemo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * GET /api/kb/stats 统计响应。
 * 结构按前端图表消费设计：异常类型分布 + 语言分布 + 技术栈分布 + 新增趋势。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KbStats {

    /** 命中筛选条件的记录总数 */
    private long total;

    /** 异常类型 TopN（默认 10） */
    private List<NameCount> errorTypeTop;

    /** 按 language 分布 */
    private List<NameCount> languageDist;

    /** 按 tech_stack 分布（逗号分隔字段拆开后逐项计数） */
    private List<NameCount> techStackDist;

    /** 近 N 天新增趋势（默认近 30 天，含当天，零值天补齐） */
    private List<TrendPoint> trend;
}
