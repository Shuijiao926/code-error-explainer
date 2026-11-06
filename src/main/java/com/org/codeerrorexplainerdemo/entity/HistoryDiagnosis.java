package com.org.codeerrorexplainerdemo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 项目 AI 最终诊断（一条一行），对应表 history_diagnosis。
 * 诊断以子表逐条保存，便于同一记录多次追加、按时间回看。
 */
@Data
@TableName("history_diagnosis")
public class HistoryDiagnosis {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联 error_history.id */
    private Long historyId;

    /** 诊断内容 */
    private String content;

    /** 诊断时间 yyyy-MM-dd HH:mm:ss */
    private String ts;

    /** 创建时间（由数据库默认值生成） */
    private LocalDateTime createdAt;
}
