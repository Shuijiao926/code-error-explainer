# 🔧 代码异常智能解释系统（Code Error Explainer）V1.1

> **报错「前置分诊」工具**：把 Java 异常堆栈变成两样东西——
> ① **非 IT 也看得懂的人话说明**（类比优先），② **交给项目 AI 做最终诊断的结构化诊断包**。
> 工具本身不做最终诊断——最终根因由项目里的 AI（如 Claude Code）结合仓库上下文给出。

```
报错/堆栈 → 前置分诊报告（人话版 + 项目AI诊断包）→(复制诊断包)→ 项目AI
          → 结合仓库上下文的最终诊断 →(回填)→ 历史闭环踩坑档案
```

## ✨ 功能特性

| 模块 | 说明 |
|------|------|
| **前置分诊**（核心） | 技术栈**自动识别**（Spring/MyBatis/Jackson/Tomcat…）+ 可修正；输出**双段式**报告 |
| **人话版** | 一句话定性 / 出在哪一层 / 疑似技术使用不妥（方向非断言）/ 你现在可以怎么做——用生活化类比讲给非 IT |
| **项目AI诊断包** | 原始报错 + 预判 + 待查证清单，一键复制成 Markdown 提示词，直接喂给项目 AI |
| **历史闭环** | 自动存「报错+技术栈+预判」；**追加回填**项目 AI 的最终诊断（多条带时间戳）；已诊断/待回填状态标记；按异常类型/技术栈/关键字搜索 |
| **无 Key 可演示** | 未配置 AI Key 时自动走 Mock 分诊，全流程可跑 |

## 🛠 技术栈

- **后端**：Spring Boot 4.0.x · Java 17 · MyBatis-Plus 3.5.16 · MySQL 8 · Lombok
- **前端**：React 18 · TypeScript · Ant Design 5 · Vite · axios
- **AI**：REST 直连 OpenAI 兼容 `/chat/completions`（DeepSeek 默认），提示词为「分诊专家」角色

## 📁 项目结构

```
code-error-explainer/
├── sql/init.sql / migrate-v11.sql   # 建表 + V1.1 幂等迁移（+tech_stack/pre_report/final_diagnosis）
├── src/                    # Spring Boot 后端（根目录）
│   └── main/java/com/org/codeerrorexplainerdemo/
│       ├── controller/     # REST 接口（explain / detect / history / diagnosis 回填）
│       ├── service/ai/     # AIService + OpenAI 兼容实现 + Mock（双段式分诊）
│       ├── service/        # ErrorExplainService / HistoryService / TechStackDetector
│       ├── mapper/entity/  # MyBatis-Plus
│       └── common/config/  # 统一响应 / CORS / 分页 / AI 装配
└── frontend/               # React 前端（双段式卡片 + 历史闭环）
```

## 🚀 快速开始

### 1. 准备数据库（MySQL 8）

```bash
mysql -u root -p < sql/init.sql        # 新库：建库建表
# 已有 V1.0 老库：执行幂等迁移（可重复执行）
mysql -u root -p < sql/migrate-v11.sql
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
| POST | `/api/explain` | 提交堆栈（可带 `techStack` 修正），返回双段式分诊报告并入库 |
| POST | `/api/detect` | 技术栈自动识别（前端预填选择器用，不调用 AI） |
| GET | `/api/history?page=&size=&keyword=` | 历史记录分页 + 搜索（异常/概要/技术栈） |
| GET | `/api/history/{id}` | 历史详情 |
| PUT | `/api/history/{id}/diagnosis` | 追加回填项目 AI 最终诊断（多条带时间戳） |
| DELETE | `/api/history/{id}` | 单条删除 |
| DELETE | `/api/history` | 全部清空 |
| GET | `/api/health` | 健康检查 |

## ✅ 测试

```bash
mvn test              # 15 个用例：异常类型提取/技术栈识别/双段式分诊/回填追加 全链路
cd frontend && npm run build   # TS 类型检查 + Vite 打包
```

## 📌 版本规划

- **V1.0**（已完成）：堆栈翻译五要素 + 历史记录
- **V1.1**（当前）：重定位为前置分诊——双段式报告 + 技术栈识别 + 项目AI诊断包 + 历史闭环回填
- **V1.2**：内置「异常×技术栈」知识卡片（离线可用），多语言报错
