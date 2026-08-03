# 代码异常智能解释系统 V1.0 — 产品需求文档（PRD）

> 一句话定位：**把程序报错日志翻译成人话的 AI 工具**

---

## 一、产品概述

### 1.1 背景

后端开发日常：线上报错 → 点开日志平台 → 一坨堆栈糊脸上 → 逐行排查 → 查 Google → 查文档 → 30 分钟过去了。

其实 80% 的错误都是常见问题（NPE、超时、SQL 异常、连接池耗尽...），但堆栈信息对新手不友好，对老手也费眼睛。

### 1.2 产品价值

> 粘贴堆栈 → AI 用大白话解释 → 附带修复建议 → 10 秒搞定

| 用户 | 收益 |
|------|------|
| 初级开发 | 看懂报错，不再一脸懵 |
| 资深开发 | 省下逐行排查的时间，直接看结论 |
| 你（项目作者） | 自己每天就用，顺手拿下软著 |

### 1.3 产品名称

**代码异常智能解释系统 V1.0** — 代码项目名沿用 `code-error-explainer`。

---

## 二、目标用户 & 使用场景

| 场景 | 描述 |
|------|------|
| **场景一：查线上报错** | 从 ELK / Grafana / 阿里云日志复制堆栈，粘贴进来，秒出解释 |
| **场景二：本地调试** | IDEA 控制台报错，看不懂，贴进来 |
| **场景三：Code Review** | 同事代码里写了奇怪的异常处理，贴进来看看是不是坑 |
| **场景四：学习积累** | 遇到新类型错误，翻翻历史记录学习 |

---

## 三、功能模块（V1.0）

### 模块一：堆栈翻译（核心，P0）

```
输入：Java 异常堆栈（完整或片段）
处理：
  1. 自动识别异常类型（NullPointerException / SQLException / TimeoutException...）
  2. AI 用白话文分层解释：
     - 🌍 一句话概述
     - 📍 哪里出问题了（文件名+行号）
     - 🎯 根因是什么
     - 🔧 怎么修（分步建议）
     - 💡 以后怎么避免
输出：结构化解释卡片
```

**关键交互**：
- 输入框支持粘贴，自动检测内容
- 解释结果分卡片展示，不是一坨文字
- 一键复制解释文本
- 支持深色模式（程序员标配）

### 模块二：历史记录（P0）

| 功能 | 说明 |
|------|------|
| 自动保存 | 每次查询自动存入历史 |
| 列表展示 | 按时间倒序，卡片式，显示异常类型+一句话概要 |
| 搜索 | 按关键字/异常类型搜索历史 |
| 回看 | 点击某条记录恢复完整结果 |
| 删除 | 单条删除 / 全部清空 |

### 模块三：异常类型速查（P1）

```
内置常见 Java 异常知识卡片：
  NullPointerException  → 成因 / 典型场景 / 修复模板
  ClassCastException    → ...
  ConcurrentModificationException → ...
  SQLException（连接超时、死锁、唯一约束...）
  OutOfMemoryError      → ...
  StackOverflowError    → ...
  ...
```

每条卡片包含：异常继承关系图、常见原因 Top 3、修复代码模板。

这个模块 AI 离线也能用，是固化知识。

### 模块四：粘贴即检测（P1）

直接在输入框粘贴任何文本：
- 如果是 Java 堆栈 → 自动走「翻译」流程
- 如果是普通报错文本 → 尝试解释
- 如果不是报错 → 提示「未识别到异常堆栈」

---

## 四、界面设计（草图）

```
┌──────────────────────────────────────────────────┐
│  🔧 人话说八哥                     [历史] [关于]  │
├────────────┬─────────────────────────────────────┤
│            │                                     │
│  📋 历史记录 │  ┌─────────────────────────────┐   │
│            │  │ 📥 粘贴 Java 异常堆栈到这里...   │   │
│  12:30     │  │                             │   │
│  NPE       │  │                             │   │
│  登录模块.. │  │                             │   │
│            │  └─────────────────────────────┘   │
│  11:20     │          [🔍 翻译成人话]            │
│  SQL异常.. │                                     │
│            │  ┌─────────────────────────────┐   │
│  10:05     │  │ 🌍 一句话概述                 │   │
│  超时异常.. │  │ UserService.java:47 行，       │   │
│            │  │ userRepository 为 null         │   │
│            │  │                             │   │
│            │  │ 📍 出问题的地方                │   │
│            │  │ UserService.login() 方法，     │   │
│            │  │ 第 47 行调用了 userRepo...    │   │
│            │  │                             │   │
│            │  │ 🎯 根因                       │   │
│            │  │ @Autowired 未生效，Spring 容器  │   │
│            │  │ 未注入该依赖...               │   │
│            │  │                             │   │
│            │  │ 🔧 修复建议                    │   │
│            │  │ 1. 检查 UserService 是否有     │   │
│            │  │    @Service 注解              │   │
│            │  │ 2. 检查包扫描路径...          │   │
│            │  │                             │   │
│            │  │ [📋 复制解释] [⭐ 收藏]        │   │
│            │  └─────────────────────────────┘   │
└────────────┴─────────────────────────────────────┘
```

---

## 五、技术选型

### 5.1 整体架构

```
┌──────────┐    HTTP/REST     ┌──────────────┐    JDBC    ┌────────┐
│  React   │ ←──────────────→ │ Spring Boot  │ ←────────→ │ MySQL  │
│  前端    │                  │    后端      │           │        │
└──────────┘                  └──────┬───────┘           └────────┘
                                     │
                                     │ HTTP
                                     ▼
                              ┌──────────────┐
                              │  AI API      │
                              │ (OpenAI /    │
                              │  Claude /    │
                              │  本地模型)    │
                              └──────────────┘
```

### 5.2 技术栈明细

| 层            | 技术                           | 版本             | 选型理由                |
| ------------ | ---------------------------- | -------------- | ------------------- |
| **前端框架**     | React                        | 18+            | 生态成熟，Ant Design 配套好 |
| **前端语言**     | TypeScript                   | 5.x            | 类型安全，减少低级 bug       |
| **UI 组件库**   | Ant Design                   | 5.x            | 组件丰富，表格/表单/布局开箱即用   |
| **代码高亮**     | highlight.js                 | 11.x           | 堆栈展示区代码着色           |
| **HTTP 客户端** | axios                        | 1.x            | 稳定，拦截器方便            |
| **构建工具**     | Vite                         | 5.x            | 比 Webpack 快         |
| **后端框架**     | Spring Boot                  | 3.x            | Java 生态标配           |
| **JDK**      | Java 17+                     | LTS            | 长期支持，虚拟线程可选         |
| **ORM**      | MyBatis-Plus                 | 3.5.x          | 轻量，单表 CRUD 不用写 SQL  |
| **数据库**      | MySQL                        | 8.0            | 稳，你指定的              |
| **连接池**      | HikariCP                     | Spring Boot 默认 | 性能最好的 Java 连接池      |
| **AI SDK**   | openai-java / anthropic-java / Spring AI Alibaba | 最新             | 调 AI API            |
| **JSON**     | Jackson                      | Spring Boot 默认 | 无敌                  |
| **API 文档**   | Knife4j (Swagger)            | 4.x            | 调试方便                |

### 5.3 为什么不选更复杂的方案

| 否定项 | 理由 |
|--------|------|
| ❌ 微服务 | 杀鸡用牛刀，一个单体搞定 |
| ❌ Redis | V1 不需要缓存，MySQL 够用 |
| ❌ MQ | 没有异步场景 |
| ❌ Docker | 先跑起来再说，部署再说 |
| ❌ Next.js / SSR | 不需要 SEO，纯客户端渲染就行 |

---

## 六、数据库设计

### 6.1 表结构

#### error_history（错误解释历史）

```sql
CREATE TABLE error_history (
  id            BIGINT        PRIMARY KEY AUTO_INCREMENT  COMMENT '主键',
  error_type    VARCHAR(100)  NOT NULL                    COMMENT '异常类型，如 NullPointerException',
  stack_trace   TEXT          NOT NULL                    COMMENT '原始堆栈（用户输入的）',
  explanation   TEXT          NOT NULL                    COMMENT 'AI 返回的完整解释 (JSON)',
  summary       VARCHAR(500)  NOT NULL                    COMMENT '一句话概要，列表展示用',
  source_ip     VARCHAR(50)                               COMMENT '请求来源 IP',
  created_at    DATETIME      DEFAULT CURRENT_TIMESTAMP   COMMENT '创建时间',
  INDEX idx_error_type (error_type),
  INDEX idx_created_at (created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='错误解释历史记录';
```

#### error_template（异常知识卡片，P1 模块）

```sql
CREATE TABLE error_template (
  id            BIGINT        PRIMARY KEY AUTO_INCREMENT  COMMENT '主键',
  error_type    VARCHAR(100)  NOT NULL UNIQUE             COMMENT '异常类型',
  category      VARCHAR(50)                               COMMENT '分类：空指针/并发/SQL/内存/OOM',
  cause_top3    TEXT                                      COMMENT '常见原因 Top3 (JSON数组)',
  fix_template  TEXT                                      COMMENT '修复代码模板',
  hierarchy     VARCHAR(500)                              COMMENT '异常继承关系',
  created_at    DATETIME      DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='内置异常知识卡片';
```

### 6.2 就两张表

V1.0 不做用户系统，本地单机用，历史记录不过滤用户。极简。

---

## 七、API 接口设计

### 7.1 核心接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/explain` | 提交堆栈，获取 AI 解释 |
| GET | `/api/history` | 获取历史记录列表（分页） |
| GET | `/api/history/{id}` | 获取某条历史详情 |
| DELETE | `/api/history/{id}` | 删除某条历史 |
| GET | `/api/templates` | 获取异常知识卡片列表（P1） |
| GET | `/api/health` | 健康检查 |

### 7.2 核心接口详情

#### POST /api/explain

```
Request:
{
  "stackTrace": "java.lang.NullPointerException\n\tat com.app..."
}

Response:
{
  "code": 200,
  "data": {
    "id": 1,
    "errorType": "NullPointerException",
    "summary": "UserService.java:47 行，userRepository 为 null",
    "explanation": {
      "overview": "这是一个经典的空指针异常...",
      "location": "UserService.java 第 47 行，login 方法中调用了 userRepository.findById()",
      "rootCause": "userRepository 未被 Spring 注入，可能是缺少 @Autowired 或 @Service 注解",
      "fixSteps": [
        "检查 UserService 类是否标注 @Service",
        "检查 userRepository 字段是否有 @Autowired 或通过构造函数注入",
        "检查包扫描路径是否包含 UserService 所在包"
      ],
      "prevention": "建议使用构造函数注入代替字段注入，并在单元测试中覆盖该路径"
    },
    "createdAt": "2026-08-02 15:30:00"
  }
}
```

#### GET /api/history?page=1&size=20&keyword=NPE

```
Response:
{
  "code": 200,
  "data": {
    "records": [
      {
        "id": 1,
        "errorType": "NullPointerException",
        "summary": "UserService.java:47 行...",
        "createdAt": "2026-08-02 15:30:00"
      }
    ],
    "total": 1,
    "page": 1,
    "size": 20
  }
}
```

---

## 八、AI Prompt 设计

### 系统提示词

```
你是一个资深的 Java 后端开发专家和调试高手。用户会粘贴一段 Java 异常堆栈信息，
请你用通俗易懂的中文解释这个错误。

请严格按以下 JSON 格式返回（不要包含 markdown 代码块标记）：

{
  "overview": "用一句话说清楚这是什么错误",
  "location": "异常发生在哪个文件、哪个方法、第几行",
  "rootCause": "根因分析，技术层面解释为什么会发生",
  "fixSteps": ["步骤1", "步骤2", "步骤3"],
  "prevention": "如何从代码层面预防同类问题"
}

要求：
- 语言通俗，避免过于学术化
- 修复步骤具体可操作
- 如果堆栈信息不完整，明确指出还需要什么信息
```

---

## 九、开发计划

| 阶段 | 内容 | 预计时间 |
|------|------|----------|
| **Day 1** | 后端项目初始化、数据库建表、AI 调用封装 | 半天 |
| **Day 2** | 前端项目初始化、布局框架、输入组件 | 半天 |
| **Day 3** | 前后端联调、核心流程跑通 | 半天 |
| **Day 4** | 历史记录模块、搜索、分页 | 半天 |
| **Day 5** | UI 美化、深色模式、细节打磨 | 半天 |
| **Day 6** | 截图、写说明书 | 1 天 |
| **合计** | | **3.5 天** |

---

## 十、版本规划

| 版本 | 内容 |
|------|------|
| V1.0 🎯 | 堆栈翻译 + 历史记录（现在就做这个） |
| V1.1 | 异常知识卡片、粘贴自动检测 |
| V2.0 | 支持 Spring Boot / MyBatis 日志、Maven/Gradle 构建错误 |
| V2.1 | 接入本地模型（Ollama），离线也能用 |

---

## 十一、附录：截图规划（说明书素材）

至少准备以下 10 张截图：

| 编号 | 截图内容 |
|:--:|------|
| 1 | 主界面初始状态（空输入框 + 空结果区） |
| 2 | 粘贴 NPE 堆栈到输入框 |
| 3 | 点击「翻译」后的完整解释结果 |
| 4 | 复制解释文本的操作 |
| 5 | 历史记录列表（多条记录） |
| 6 | 点击历史记录回看详情 |
| 7 | 搜索历史记录 |
| 8 | SQL 异常的解释结果（展示不同异常类型） |
| 9 | 超时异常的解释结果 |
| 10 | 深色模式切换效果 |

> 每种异常类型一张结果截图，三种异常就是三张，加上交互截图，说明书轻轻松松 25+ 页。
