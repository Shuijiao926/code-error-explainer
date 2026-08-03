# Error Handling

> How errors are handled in this project.

---

## Overview

<!--
Document your project's error handling conventions here.

Questions to answer:
- What error types do you define?
- How are errors propagated?
- How are errors logged?
- How are errors returned to clients?
-->

(To be filled by the team)

---

## Error Types

<!-- Custom error classes/types -->

(To be filled by the team)

---

## Error Handling Patterns

<!-- Try-catch patterns, error propagation -->

(To be filled by the team)

---

## API Error Responses

<!-- Standard error response format -->

(To be filled by the team)

---

## Common Mistakes

<!-- Error handling mistakes your team has made -->

(To be filled by the team)

---

## V1.0 实现经验（2026-08-03 沉淀）

- **统一响应** `common/Result<T>{code, message, data}`：成功 code=200；失败 code=500。
- **业务异常** `common/BusinessException`：可控失败直接 throw，全局 `@RestControllerAdvice`
  `GlobalExceptionHandler` 转成 `Result.fail(中文提示)`；未捕获异常记 ERROR 日志并返回统一格式。
- **外部依赖（AI）必须兜底**：`AIService.explain()` 对任何异常返回 fallback `Explanation`
  （overview=堆栈首行，其余为友好提示），仍入库——用户不白屏，主链路不阻塞。
- 前端 `ensureOk()` 校验 `code===200`，否则抛出后端 message。
- 注意：`GlobalExceptionHandler.handleException` 会把内部消息暴露给前端（demo 阶段可接受，
  上线前应收敛为通用文案）。
