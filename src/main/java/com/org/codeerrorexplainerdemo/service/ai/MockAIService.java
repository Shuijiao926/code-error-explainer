package com.org.codeerrorexplainerdemo.service.ai;

import com.org.codeerrorexplainerdemo.dto.Handoff;
import com.org.codeerrorexplainerdemo.dto.PersonReport;
import com.org.codeerrorexplainerdemo.dto.PreTriageReport;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mock 提供者：未配置 API Key 时启用，按异常类型返回模板化双段式分诊报告
 * （人话版采用「类比优先」风格）。仅用于演示，非真实分析。
 */
public class MockAIService implements AIService {

    private static final Pattern ERROR_PATTERN = Pattern.compile("([\\w.]+(?:Exception|Error))");

    @Override
    public PreTriageReport explain(String stackTrace, List<String> techStack) {
        String type = extractType(stackTrace);
        return buildForType(type, stackTrace, techStack);
    }

    private PreTriageReport buildForType(String type, String stackTrace, List<String> techStack) {
        String t = type == null ? "" : type;
        if (t.contains("NullPointerException")) return npe(type, stackTrace, techStack);
        if (t.contains("SQLException")) return sql(type, stackTrace, techStack);
        if (t.contains("Timeout") || t.contains("timeout")) return timeout(type, stackTrace, techStack);
        if (t.contains("OutOfMemory")) return oom(type, stackTrace, techStack);
        if (t.contains("StackOverflow")) return stackOverflow(type, stackTrace, techStack);
        return generic(type, stackTrace, techStack);
    }

    private PreTriageReport npe(String type, String stack, List<String> techStack) {
        return PreTriageReport.builder()
                .personReport(PersonReport.builder()
                        .summary("程序想用「还没准备好的对象」，就像你要借东西、可东西还没到位——这叫空指针（" + type + "）。")
                        .layer("出在你业务代码里：堆栈第一个 \"at ...\" 行就是实际出错的地方，比如登录这一步。")
                        .misuse("这个对象本该由框架或程序提前「送到位」，但看起来没送到——就像盒子该装的零件没装进去。这是方向，不是定论。")
                        .nextStep("把「诊断包」发给项目 AI，让它去仓库查一查：零件是不是没装进盒子（对象有没有被注入/初始化）。")
                        .build())
                .handoff(Handoff.builder()
                        .techStack(techStack)
                        .suspectedPoints(List.of("依赖注入可能未生效（@Service/@Autowired 或包扫描范围）",
                                "查询结果可能为 null 但未判空（findById/返回值链式调用）"))
                        .checkList(List.of("定位第一个属于业务代码的 \"at\" 行",
                                "检查该行调用对象是否被正确注入/初始化",
                                "检查该字段是否有单测覆盖"))
                        .rawError(stack)
                        .build())
                .build();
    }

    private PreTriageReport sql(String type, String stack, List<String> techStack) {
        return PreTriageReport.builder()
                .personReport(PersonReport.builder()
                        .summary("你的程序在跟数据库「约事情」时没约成，卡住了（" + type + "）。")
                        .layer("出在数据访问这一层：程序去找数据库要数据的地方。")
                        .misuse("可能是「打电话占线没人接」（连接池满）、「说的方言数据库听不懂」（SQL 写法不对）、或「撞了重复的名字」（唯一约束冲突）。这是方向，不是定论。")
                        .nextStep("把「诊断包」发给项目 AI，让它结合你的 Mapper/SQL 和库表结构确认是哪种。")
                        .build())
                .handoff(Handoff.builder()
                        .techStack(techStack)
                        .suspectedPoints(List.of("连接池耗尽（最大连接数过小）", "SQL 语法或表结构不匹配", "唯一约束冲突"))
                        .checkList(List.of("核对错误消息中的 SQLSTATE/具体 SQL",
                                "检查连接池配置与当前并发量",
                                "核对 Mapper 与表结构是否一致"))
                        .rawError(stack)
                        .build())
                .build();
    }

    private PreTriageReport timeout(String type, String stack, List<String> techStack) {
        return PreTriageReport.builder()
                .personReport(PersonReport.builder()
                        .summary("你的程序等一个「慢动作」等太久了，超时放弃（" + type + "）。")
                        .layer("出在等外部资源的地方：数据库、HTTP 接口、或线程池排队。")
                        .misuse("很可能是被等的「人」太慢、你给的时间太短、或排队的人太多。这是方向，不是定论。")
                        .nextStep("把「诊断包」发给项目 AI，让它结合调用配置和链路排查是哪一步慢。")
                        .build())
                .handoff(Handoff.builder()
                        .techStack(techStack)
                        .suspectedPoints(List.of("下游服务/慢 SQL 响应超时", "超时阈值配置过短", "线程池耗尽排队"))
                        .checkList(List.of("定位超时发生在哪一跳（DB/HTTP/线程池）",
                                "检查下游是否有性能瓶颈",
                                "检查连接池/线程池参数"))
                        .rawError(stack)
                        .build())
                .build();
    }

    private PreTriageReport oom(String type, String stack, List<String> techStack) {
        return PreTriageReport.builder()
                .personReport(PersonReport.builder()
                        .summary("程序的内存「仓库」装满了，再也放不进东西（" + type + "）。")
                        .layer("出在 JVM 内存管理这一层，要看是哪个模块一直在往里堆东西。")
                        .misuse("常见是「仓库只进不出」（缓存/集合无限增长）、一次搬太多、或内存泄漏。这是方向，不是定论。")
                        .nextStep("把「诊断包」发给项目 AI；方便的话把 jmap 堆转储也一起给它。")
                        .build())
                .handoff(Handoff.builder()
                        .techStack(techStack)
                        .suspectedPoints(List.of("大集合/缓存无限增长未清理", "一次性加载过大数据集", "内存泄漏"))
                        .checkList(List.of("用 jmap/MAT 分析堆转储定位占用大户",
                                "检查是否有集合/缓存无限增长",
                                "评估 -Xmx 是否过小"))
                        .rawError(stack)
                        .build())
                .build();
    }

    private PreTriageReport stackOverflow(String type, String stack, List<String> techStack) {
        return PreTriageReport.builder()
                .personReport(PersonReport.builder()
                        .summary("程序自己一层层「套娃」套太深，把栈挤爆了（" + type + "）。")
                        .layer("出在方法调用链上：堆栈里同一个方法反复出现，就是套娃入口。")
                        .misuse("很可能是套娃没有停止条件（递归缺终止），或对象之间互相叫没完没了。这是方向，不是定论。")
                        .nextStep("把「诊断包」发给项目 AI，让它结合你的递归/循环引用代码确认。")
                        .build())
                .handoff(Handoff.builder()
                        .techStack(techStack)
                        .suspectedPoints(List.of("递归缺少终止条件", "对象间循环引用（A 调 B、B 又调 A）"))
                        .checkList(List.of("在堆栈中找重复出现的类名/方法名",
                                "检查递归终止条件",
                                "检查 toString/equals/初始化是否互相调用"))
                        .rawError(stack)
                        .build())
                .build();
    }

    private PreTriageReport generic(String type, String stack, List<String> techStack) {
        return PreTriageReport.builder()
                .personReport(PersonReport.builder()
                        .summary("检测到异常类型：" + type + "。（当前为演示模式，配置真实 AI Key 后会获得专业分诊。）")
                        .layer("具体层面需看堆栈里第一个 \"at ...\" 行。")
                        .misuse("具体可疑点需结合报错上下文判断（这是方向，不是定论）。")
                        .nextStep("把「诊断包」发给项目 AI；或配置 AI_API_KEY 后重新提交获得专业分诊。")
                        .build())
                .handoff(Handoff.builder()
                        .techStack(techStack)
                        .suspectedPoints(List.of("具体根因需结合堆栈上下文分析"))
                        .checkList(List.of("查看异常消息与最内层堆栈定位出错代码",
                                "结合报错处代码与传入数据排查"))
                        .rawError(stack)
                        .build())
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
