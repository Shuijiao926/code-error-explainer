package com.org.codeerrorexplainerdemo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 趋势点：某天新增数量（按 created_at 聚合）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrendPoint {

    /** 日期 yyyy-MM-dd */
    private String date;

    /** 当天新增条数 */
    private long count;
}
