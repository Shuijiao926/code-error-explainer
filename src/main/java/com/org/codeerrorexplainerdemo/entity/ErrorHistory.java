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

    /** 堆栈语言：java/python/node/go */
    private String language;

    /** 识别到的技术栈，逗号分隔 */
    private String techStack;

    /** 原始堆栈（用户输入的） */
    private String stackTrace;

    /** AI 返回的完整解释 (JSON)，历史遗留字段，与 pre_report 同内容 */
    private String explanation;

    /** 前置分诊报告 JSON */
    private String preReport;

    /** 项目 AI 回填的最终诊断（历史遗留列，当前由 history_diagnosis 子表承载） */
    private String finalDiagnosis;

    /** 一句话概要，列表展示用 */
    private String summary;

    /** 请求来源 IP */
    private String sourceIp;

    /** 是否沉淀为踩坑档案（0/1） */
    private Boolean isArchived;

    /** 踩坑标签，逗号分隔（可空） */
    private String tags;

    /** 创建时间（由数据库默认值生成） */
    private LocalDateTime createdAt;
}
