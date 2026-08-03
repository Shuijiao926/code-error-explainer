package com.org.codeerrorexplainerdemo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 错误解释历史记录，对应表 error_history。
 */
@Data
@TableName("error_history")
public class ErrorHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 异常类型，如 NullPointerException */
    private String errorType;

    /** 原始堆栈（用户输入的） */
    private String stackTrace;

    /** AI 返回的完整解释 (JSON) */
    private String explanation;

    /** 一句话概要，列表展示用 */
    private String summary;

    /** 请求来源 IP */
    private String sourceIp;

    /** 创建时间（由数据库默认值生成） */
    private LocalDateTime createdAt;
}
