-- 代码异常智能解释系统 V1.0 建库建表脚本
-- 用法：mysql -u root -p < sql/init.sql

CREATE DATABASE IF NOT EXISTS code_error_explainer DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE code_error_explainer;

CREATE TABLE IF NOT EXISTS error_history (
  id               BIGINT       PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  error_type       VARCHAR(100) NOT NULL COMMENT '异常类型，如 NullPointerException',
  language         VARCHAR(20)  NULL COMMENT '堆栈语言：java/python/node/go',
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

CREATE TABLE IF NOT EXISTS error_template (
  id          BIGINT        PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  error_type  VARCHAR(100)  NOT NULL UNIQUE COMMENT '异常类型',
  category    VARCHAR(50)   NULL COMMENT '分类：空指针/并发/SQL/内存/超时',
  cause_top3  TEXT          NULL COMMENT '常见原因 Top3 (JSON数组)',
  fix_template TEXT         NULL COMMENT '修复代码模板',
  hierarchy   VARCHAR(500)  NULL COMMENT '异常继承关系',
  created_at  DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at  DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='内置异常知识卡片';
