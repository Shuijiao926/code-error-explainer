-- V1.5 迁移脚本：长文本存储短期修复（幂等，可重复执行）
-- 1) error_history 长字段升级 MEDIUMTEXT
-- 2) final_diagnosis 追加式 JSON 拆 history_diagnosis 子表并回填
-- 用法：mysql -u root -p < sql/migrate-v15.sql

USE code_error_explainer;

-- 1. 长字段升级 MEDIUMTEXT（MODIFY 幂等，可重复执行）
ALTER TABLE error_history
  MODIFY stack_trace     MEDIUMTEXT NOT NULL COMMENT '原始堆栈（用户输入的）',
  MODIFY explanation     MEDIUMTEXT NOT NULL COMMENT 'AI 返回的完整解释 (JSON)',
  MODIFY pre_report      MEDIUMTEXT NULL     COMMENT '前置分诊报告 JSON（V1.1）',
  MODIFY final_diagnosis MEDIUMTEXT NULL     COMMENT '项目AI回填最终诊断（V1.1，V1.5 起停写，遗留列）';

-- 2. 诊断子表（一条诊断一行，替代单列追加）
CREATE TABLE IF NOT EXISTS history_diagnosis (
  id          BIGINT       PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  history_id  BIGINT       NOT NULL COMMENT '关联 error_history.id',
  content     MEDIUMTEXT   NOT NULL COMMENT '诊断内容',
  ts          VARCHAR(20)  NOT NULL COMMENT '诊断时间 yyyy-MM-dd HH:mm:ss',
  created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  INDEX idx_history_id (history_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目AI最终诊断（一对多，替代 final_diagnosis 追加）';

-- 3. 回填历史 final_diagnosis JSON → 子表（幂等：仅对尚无子行的 history_id）
INSERT INTO history_diagnosis (history_id, content, ts)
SELECT eh.id, jt.content, jt.ts
FROM error_history eh,
     JSON_TABLE(
       COALESCE(NULLIF(eh.final_diagnosis, ''), '[]'),
       '$[*]' COLUMNS (
         content MEDIUMTEXT PATH '$.content',
         ts VARCHAR(20) PATH '$.ts'
       )
     ) jt
WHERE eh.final_diagnosis IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM history_diagnosis hd WHERE hd.history_id = eh.id);
