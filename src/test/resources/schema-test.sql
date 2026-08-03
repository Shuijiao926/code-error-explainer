-- H2 内存库测试表结构（与 sql/init.sql 的 error_history 对应）
CREATE TABLE IF NOT EXISTS error_history (
  id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
  error_type  VARCHAR(100) NOT NULL,
  stack_trace CLOB         NOT NULL,
  explanation CLOB         NOT NULL,
  summary     VARCHAR(500) NOT NULL,
  source_ip   VARCHAR(50),
  created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);
