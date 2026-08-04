package com.org.codeerrorexplainerdemo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 调用日志，对应表 ai_call_log。
 */
@Data
@TableName("ai_call_log")
public class AiCallLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联 error_history.id */
    private Long historyId;

    /** mock / deepseek / openai / ollama */
    private String provider;

    /** 模型名 */
    private String model;

    /** success / fallback */
    private String status;

    /** 调用耗时(毫秒) */
    private Long durationMs;

    /** 输入 token */
    private Integer promptTokens;

    /** 输出 token */
    private Integer completionTokens;

    /** 输入堆栈摘要(前200字符) */
    private String inputExcerpt;

    /** 失败/降级原因 */
    private String errorMessage;

    /** 创建时间（由数据库默认值生成） */
    private LocalDateTime createdAt;
}
