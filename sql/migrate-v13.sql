-- V1.3 迁移脚本：error_template 表 + error_history.language 列（幂等，可重复执行）
USE code_error_explainer;

DROP PROCEDURE IF EXISTS migrate_v13;

DELIMITER $$
CREATE PROCEDURE migrate_v13()
BEGIN
  -- 1. error_template 表
  CREATE TABLE IF NOT EXISTS error_template (
    id          BIGINT       PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    error_type  VARCHAR(100) NOT NULL UNIQUE COMMENT '异常类型',
    category    VARCHAR(50)  NULL COMMENT '分类：空指针/并发/SQL/内存/超时',
    cause_top3  TEXT         NULL COMMENT '常见原因 Top3 (JSON数组)',
    fix_template TEXT        NULL COMMENT '修复代码模板',
    hierarchy   VARCHAR(500) NULL COMMENT '异常继承关系',
    created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='内置异常知识卡片';

  -- 2. error_history.language 列
  IF NOT EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'error_history' AND COLUMN_NAME = 'language'
  ) THEN
    ALTER TABLE error_history
      ADD COLUMN language VARCHAR(20) NULL COMMENT '堆栈语言：java/python/node/go' AFTER error_type;
  END IF;
END$$
DELIMITER ;

CALL migrate_v13();
DROP PROCEDURE IF EXISTS migrate_v13;

-- 种子数据请另行执行（幂等，可重复）：
--   mysql -u root -p < sql/seed-templates.sql
