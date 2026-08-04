-- V1.4 迁移脚本：ai_call_log 表（AI 调用日志，幂等可重复执行）
-- 用法：mysql -u root -p < sql/migrate-v14.sql

USE code_error_explainer;

CREATE TABLE IF NOT EXISTS ai_call_log (
  id                BIGINT        PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  history_id        BIGINT        NULL COMMENT '关联 error_history.id',
  provider          VARCHAR(50)   NULL COMMENT 'mock/deepseek/openai/ollama',
  model             VARCHAR(100)  NULL COMMENT '模型名',
  status            VARCHAR(20)   NULL COMMENT 'success/fallback',
  duration_ms       BIGINT        NULL COMMENT '调用耗时(毫秒)',
  prompt_tokens     INT           NULL COMMENT '输入 token',
  completion_tokens INT           NULL COMMENT '输出 token',
  input_excerpt     VARCHAR(1000) NULL COMMENT '输入堆栈摘要(前200字符)',
  error_message     VARCHAR(1000) NULL COMMENT '失败/降级原因',
  created_at        DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  INDEX idx_history_id (history_id),
  INDEX idx_created_at (created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI调用日志';
