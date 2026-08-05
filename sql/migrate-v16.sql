-- V1.6 迁移脚本：踩坑档案元数据（幂等，可重复执行）
-- 1) error_history 增加 is_archived（是否沉淀为踩坑档案）
-- 2) error_history 增加 tags（逗号分隔标签，可空）
-- 说明：MySQL 不支持 ADD COLUMN IF NOT EXISTS，这里用 information_schema 判断列存在性再执行，保证可重跑。
-- 用法：mysql -u root -p < sql/migrate-v16.sql

USE code_error_explainer;

-- 1. is_archived 列（TINYINT(1) 默认 0，幂等）
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'error_history'
    AND COLUMN_NAME = 'is_archived'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE error_history ADD COLUMN is_archived TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''是否沉淀为踩坑档案''',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. tags 列（VARCHAR(500) 可空，幂等）
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'error_history'
    AND COLUMN_NAME = 'tags'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE error_history ADD COLUMN tags VARCHAR(500) NULL COMMENT ''逗号分隔踩坑标签''',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
