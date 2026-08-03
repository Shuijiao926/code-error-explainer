package com.org.codeerrorexplainerdemo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 内置异常知识卡片，对应表 error_template（离线可用，不依赖 AI）。
 */
@Data
@TableName("error_template")
public class ErrorTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 异常类型，唯一 */
    private String errorType;

    /** 分类：空指针/并发/SQL/内存/超时 */
    private String category;

    /** 常见原因 Top3 (JSON 数组) */
    private String causeTop3;

    /** 修复代码模板 */
    private String fixTemplate;

    /** 异常继承关系 */
    private String hierarchy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
