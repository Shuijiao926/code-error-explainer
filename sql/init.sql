-- 代码异常智能解释系统 V1.0 建库建表脚本
-- 用法：mysql -u root -p < sql/init.sql

CREATE DATABASE IF NOT EXISTS code_error_explainer DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE code_error_explainer;

CREATE TABLE IF NOT EXISTS error_history (
  id               BIGINT       PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  error_type       VARCHAR(100) NOT NULL COMMENT '异常类型，如 NullPointerException',
  tech_stack       VARCHAR(255) NULL COMMENT '识别到的技术栈，逗号分隔',
  stack_trace      TEXT         NOT NULL COMMENT '原始堆栈（用户输入的）',
  explanation      TEXT         NOT NULL COMMENT 'AI 返回的完整解释 (JSON)',
  pre_report       TEXT         NULL COMMENT '前置分诊报告 JSON（V1.1）',
  final_diagnosis  TEXT         NULL COMMENT '项目AI回填的最终诊断（V1.1）',
  summary          VARCHAR(500) NOT NULL COMMENT '一句话概要，列表展示用',
  source_ip        VARCHAR(50)  NULL COMMENT '请求来源 IP',
  created_at       DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  INDEX idx_error_type (error_type),
  INDEX idx_created_at (created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='错误解释历史记录';
