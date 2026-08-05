-- H2 内存库测试表结构（与 sql/init.sql 对应）
CREATE TABLE IF NOT EXISTS error_history (
  id               BIGINT       AUTO_INCREMENT PRIMARY KEY,
  error_type       VARCHAR(100) NOT NULL,
  language         VARCHAR(20),
  tech_stack       VARCHAR(255),
  stack_trace      CLOB         NOT NULL,
  explanation      CLOB         NOT NULL,
  pre_report       CLOB,
  final_diagnosis  CLOB,
  summary          VARCHAR(500) NOT NULL,
  source_ip        VARCHAR(50),
  is_archived      TINYINT      DEFAULT 0 COMMENT '是否沉淀为踩坑档案',
  tags             VARCHAR(500) COMMENT '逗号分隔踩坑标签',
  created_at       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS error_template (
  id           BIGINT       AUTO_INCREMENT PRIMARY KEY,
  error_type   VARCHAR(100) NOT NULL UNIQUE,
  category     VARCHAR(50),
  cause_top3   CLOB,
  fix_template CLOB,
  hierarchy    VARCHAR(500),
  created_at   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  updated_at   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

-- V1.4: AI 调用日志
CREATE TABLE IF NOT EXISTS ai_call_log (
  id                BIGINT       AUTO_INCREMENT PRIMARY KEY,
  history_id        BIGINT,
  provider          VARCHAR(50),
  model             VARCHAR(100),
  status            VARCHAR(20),
  duration_ms       BIGINT,
  prompt_tokens     INT,
  completion_tokens INT,
  input_excerpt     VARCHAR(1000),
  error_message     VARCHAR(1000),
  created_at        TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

-- V1.5: 项目AI最终诊断（一对多子表，替代 final_diagnosis 单列追加）
CREATE TABLE IF NOT EXISTS history_diagnosis (
  id          BIGINT      AUTO_INCREMENT PRIMARY KEY,
  history_id  BIGINT      NOT NULL,
  content     CLOB        NOT NULL,
  ts          VARCHAR(20) NOT NULL,
  created_at  TIMESTAMP   DEFAULT CURRENT_TIMESTAMP
);

-- 知识卡片测试种子（先清空保证幂等）
DELETE FROM error_template;
INSERT INTO error_template (error_type, category, cause_top3, fix_template, hierarchy) VALUES
('NullPointerException', '空指针', '["注入未生效","查询返回null未判空","字段未初始化"]', '1. 判空\n2. 检查注入', 'Exception → RuntimeException → NullPointerException'),
('SQLException', 'SQL', '["连接池耗尽","SQL语法错","约束冲突"]', '1. 看SQLSTATE', 'Exception → SQLException');

