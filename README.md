# 🔧 代码异常智能解释系统（Code Error Explainer）V1.0

> **把程序报错日志翻译成人话的 AI 工具。**
> 粘贴 Java 异常堆栈 → AI 用大白话分层解释（一句话概述 / 出问题位置 / 根因 / 修复步骤 / 预防建议）→ 自动存入历史 → 10 秒得到结论。

## ✨ 功能特性

| 模块 | 说明 |
|------|------|
| **堆栈翻译**（核心） | 粘贴堆栈自动识别异常类型（NPE / SQLException / Timeout…），AI 返回结构化五要素解释卡片，支持一键复制 |
| **历史记录** | 每次查询自动入库；分页倒序、关键字搜索、详情回看、单条删除 / 全部清空 |
| **AI 多通道** | 抽象 `AIService`，支持 DeepSeek / OpenAI 等兼容协议；**未配置 Key 时自动降级 Mock**，离线也能完整演示 |
| **深色模式** | 一键切换并持久化（程序员标配） |

## 🛠 技术栈

- **后端**：Spring Boot 4.0.x · Java 17 · MyBatis-Plus 3.5.16 · MySQL 8 · Lombok
- **前端**：React 18 · TypeScript · Ant Design 5 · Vite · axios
- **AI**：REST 直连 OpenAI 兼容 `/chat/completions`（DeepSeek 默认）

## 📁 项目结构

```
code-error-explainer/
├── sql/init.sql            # 建库建表脚本
├── src/                    # Spring Boot 后端（根目录）
│   └── main/java/com/org/codeerrorexplainerdemo/
│       ├── controller/     # REST 接口
│       ├── service/ai/     # AIService + OpenAI 兼容实现 + Mock
│       ├── service/        # 业务层
│       ├── mapper/entity/  # MyBatis-Plus
│       └── common/config/  # 统一响应 / CORS / 分页 / AI 装配
└── frontend/               # React 前端
```

## 🚀 快速开始

### 1. 准备数据库（MySQL 8）

```bash
mysql -u root -p < sql/init.sql   # 自动建库 code_error_explainer + 建表
```

### 2. 启动后端（端口 8080）

```bash
mvn spring-boot:run
# 可选环境变量：
#   DB_PASSWORD=你的密码    DB 连接默认 root/root@localhost:3306
#   AI_API_KEY=sk-xxx       AI Key（不配则自动走 Mock 演示）
#   AI_PROVIDER=deepseek    deepseek | openai | mock
```

### 3. 启动前端（端口 5173）

```bash
cd frontend && npm install && npm run dev
# 浏览器打开 http://localhost:5173
```

> 开发期前端已通过 Vite 代理将 `/api` 转发到 `localhost:8080`，无需处理跨域。

## 🔌 主要 API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/explain` | 提交堆栈，返回 AI 解释并入库 |
| GET | `/api/history?page=&size=&keyword=` | 历史记录分页 + 搜索 |
| GET | `/api/history/{id}` | 历史详情 |
| DELETE | `/api/history/{id}` | 单条删除 |
| DELETE | `/api/history` | 全部清空 |
| GET | `/api/health` | 健康检查 |

## ✅ 测试

```bash
mvn test              # 8 个用例：异常类型提取 + H2 内存库全链路集成测试
cd frontend && npm run build   # TS 类型检查 + Vite 打包
```

## 📌 版本规划

- **V1.0**（当前）：堆栈翻译 + 历史记录
- **V1.1**：异常知识卡片（error_template）、粘贴即自动检测
- **V2.x**：Spring Boot / MyBatis 日志解析、本地模型（Ollama）
