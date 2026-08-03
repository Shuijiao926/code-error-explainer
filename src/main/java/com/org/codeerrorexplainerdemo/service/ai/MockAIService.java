package com.org.codeerrorexplainerdemo.service.ai;

import com.org.codeerrorexplainerdemo.dto.Explanation;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mock 提供者：未配置 API Key 时启用，按异常类型返回模板化解释，
 * 保证无 Key 也能端到端演示主链路。仅用于演示，非真实分析。
 */
public class MockAIService implements AIService {

    private static final Pattern ERROR_PATTERN = Pattern.compile("([\\w.]+(?:Exception|Error))");

    @Override
    public Explanation explain(String stackTrace) {
        String type = extractType(stackTrace);
        return explainForType(type, stackTrace);
    }

    private Explanation explainForType(String type, String stackTrace) {
        String t = type == null ? "" : type;
        if (t.contains("NullPointerException")) return npe(type);
        if (t.contains("SQLException")) return sql(type);
        if (t.contains("Timeout") || t.contains("timeout")) return timeout(type);
        if (t.contains("OutOfMemory")) return oom(type);
        if (t.contains("StackOverflow")) return stackOverflow(type);
        if (t.contains("ClassCast")) return classCast(type);
        return generic(type);
    }

    private Explanation npe(String type) {
        return Explanation.builder()
                .overview("这是一个空指针异常（" + type + "）：代码调用了某个为 null 的对象的方法或属性。")
                .location("堆栈中第一个 \"at ...\" 行是真正出问题的位置，通常是最内层业务代码。")
                .rootCause("某个对象在调用前为 null，常见于依赖未注入、查询结果为空未判空、字段未初始化。")
                .fixSteps(List.of(
                        "定位堆栈中第一个属于自己业务的 \"at xxx\" 行",
                        "检查该行调用对象是否为 null，例如 userRepository 是否被注入",
                        "对可能为空的返回值（如 findById）做判空或 orElse 兜底",
                        "必要时用 Optional 或 Objects.requireNonNull 明确前置条件"))
                .prevention("用构造函数注入代替字段注入；对可能为 null 的返回值统一判空；在关键路径加断言。")
                .build();
    }

    private Explanation sql(String type) {
        return Explanation.builder()
                .overview("这是一个 SQL 相关异常（" + type + "），通常是连接、语法或约束层面的问题。")
                .location("看堆栈中 MyBatis/JDBC 相关的调用层，定位到具体 DAO/Mapper 方法。")
                .rootCause("常见于连接池耗尽、SQL 语法错误、唯一键冲突、事务超时、死锁。")
                .fixSteps(List.of(
                        "查看错误消息中的 SQLSTATE 编号定位具体原因",
                        "检查 Mapper XML / 注解 SQL 的语法与表结构是否一致",
                        "确认数据库连接池配置（最大连接数）是否过小",
                        "若是死锁，检查事务中加锁顺序是否一致"))
                .prevention("为慢 SQL 建索引；事务尽量短小；连接池参数按并发量配置并监控。")
                .build();
    }

    private Explanation timeout(String type) {
        return Explanation.builder()
                .overview("这是一个超时异常（" + type + "）：某个外部调用或任务在限定时间内没有完成。")
                .location("堆栈中的 HTTP 客户端 / RPC 调用 / 线程池等待处，即超时点。")
                .rootCause("被调用的服务或方法响应过慢，超过配置的超时阈值，常见于慢 SQL、下游接口变慢、线程池排队。")
                .fixSteps(List.of(
                        "确认超时发生在哪个调用链（数据库 / HTTP / RPC / 线程池）",
                        "检查下游服务或慢 SQL 是否出现性能瓶颈",
                        "评估是否需要调大超时阈值或加熔断降级",
                        "排查线程池是否被耗尽导致任务排队"))
                .prevention("为外部调用统一设置超时与重试；对慢接口做性能压测；关键路径加熔断。")
                .build();
    }

    private Explanation oom(String type) {
        return Explanation.builder()
                .overview("这是一个内存溢出错误（" + type + "）：JVM 堆内存耗尽，无法再分配对象。")
                .location("结合堆栈与 GC 日志定位是哪个模块在疯狂创建对象。")
                .rootCause("常见于大集合未释放、内存泄漏（如全局缓存无限增长）、一次性加载过多数据、堆内存配置过小。")
                .fixSteps(List.of(
                        "用 jmap / MAT 分析堆转储，定位内存占用大户",
                        "检查是否有集合/缓存无限增长未清理",
                        "对大结果集改用分批或流式处理",
                        "按实际负载调整 -Xmx 堆内存大小"))
                .prevention("对缓存设置上限与过期；大数据量分页处理；上线前做压测观察内存曲线。")
                .build();
    }

    private Explanation stackOverflow(String type) {
        return Explanation.builder()
                .overview("这是一个栈溢出错误（" + type + "）：方法调用层级过深，通常是死循环递归。")
                .location("堆栈中重复出现同一个方法多次，即为递归入口。")
                .rootCause("递归没有正确的终止条件，或对象间循环调用（A 调 B、B 又调 A）。")
                .fixSteps(List.of(
                        "在堆栈中找重复出现的类名/方法名",
                        "检查递归函数是否有明确的终止条件",
                        "排查对象初始化或 toString/equals 等是否互相调用",
                        "深层递归改用迭代 + 显式栈实现"))
                .prevention("递归前先想好终止条件；对循环引用做防护（如用标识避免重复访问）。")
                .build();
    }

    private Explanation classCast(String type) {
        return Explanation.builder()
                .overview("这是一个类型转换异常（" + type + "）：把一个对象强制转换成它实际不是的类型。")
                .location("堆栈中类型转换的 \"at\" 行即出错处。")
                .rootCause("对象实际类型与强转目标不匹配，常见于反序列化类型不对、泛型擦除、错误的多态分支。")
                .fixSteps(List.of(
                        "确认被强转对象的实际类型（调试或打印 getClass()）",
                        "检查反序列化 / JSON 解析处的目标类型声明",
                        "强转前用 instanceof 或 Class.isInstance 做类型检查",
                        "修正泛型声明，避免裸类型"))
                .prevention("统一入口做类型校验；避免裸泛型；对不确定类型先判断再转换。")
                .build();
    }

    private Explanation generic(String type) {
        return Explanation.builder()
                .overview("检测到异常类型：" + type + "（当前为演示模式的通用解释，配置真实 AI Key 后可获得专业分析）。")
                .location("堆栈中第一个 \"at ...\" 行即出错点。")
                .rootCause("具体根因需结合堆栈上下文分析。")
                .fixSteps(List.of(
                        "查看异常消息与最内层堆栈定位出错代码",
                        "结合报错处代码与传入数据排查",
                        "配置 AI_API_KEY 后重新提交以获得精确解释"))
                .prevention("保持代码可观测性（日志/监控），异常发生时能快速定位。")
                .build();
    }

    private String extractType(String stackTrace) {
        if (stackTrace == null) {
            return "Unknown";
        }
        Matcher matcher = ERROR_PATTERN.matcher(stackTrace);
        if (matcher.find()) {
            String full = matcher.group(1);
            return full.substring(full.lastIndexOf('.') + 1);
        }
        return "Unknown";
    }
}
