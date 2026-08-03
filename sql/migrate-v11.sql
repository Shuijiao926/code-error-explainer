-- V1.1 迁移脚本：error_history 增加三列（tech_stack / pre_report / final_diagnosis）
-- 幂等：可重复执行，列已存在时自动跳过
-- 用法：mysql -u root -p < sql/migrate-v11.sql

USE code_error_explainer;

DROP PROCEDURE IF EXISTS migrate_v11;

DELIMITER $$
CREATE PROCEDURE migrate_v11()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'error_history' AND COLUMN_NAME = 'tech_stack'
  ) THEN
    ALTER TABLE error_history
      ADD COLUMN tech_stack VARCHAR(255) NULL COMMENT '识别到的技术栈，逗号分隔' AFTER error_type;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'error_history' AND COLUMN_NAME = 'pre_report'
  ) THEN
    ALTER TABLE error_history
      ADD COLUMN pre_report TEXT NULL COMMENT '前置分诊报告 JSON（V1.1）' AFTER explanation;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'error_history' AND COLUMN_NAME = 'final_diagnosis'
  ) THEN
    ALTER TABLE error_history
      ADD COLUMN final_diagnosis TEXT NULL COMMENT '项目AI回填的最终诊断（V1.1）' AFTER pre_report;
  END IF;
END$$
DELIMITER ;

CALL migrate_v11();
DROP PROCEDURE IF EXISTS migrate_v11;
