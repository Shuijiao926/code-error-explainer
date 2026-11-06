package com.org.codeerrorexplainerdemo.service.ai;

import com.org.codeerrorexplainerdemo.dto.Handoff;
import com.org.codeerrorexplainerdemo.dto.PersonReport;
import com.org.codeerrorexplainerdemo.dto.PreTriageReport;
import com.org.codeerrorexplainerdemo.entity.AiCallLog;
import com.org.codeerrorexplainerdemo.service.ErrorExplainService;

import java.util.List;

/**
 * 本地规则库提供者：未配置外部 AI 服务时启用，按语言 + 异常类型返回内置的双段式分诊报告
 * （人话版类比优先）。用于保证离线或未配置 Key 时核心流程仍然可用。
 */
public class LocalRuleTriageProvider implements TriageReportProvider {

    private final AiCallRecorder recorder;

    public LocalRuleTriageProvider(AiCallRecorder recorder) {
        this.recorder = recorder;
    }

    @Override
    public AiCallOutcome explain(String stackTrace, List<String> techStack, String language) {
        long start = System.nanoTime();
        String errorType = ErrorExplainService.extractErrorType(stackTrace, language);
        PreTriageReport report = switch (language == null ? "" : language) {
            case "python" -> reportForPython(errorType, stackTrace, techStack);
            case "node" -> reportForNode(errorType, stackTrace, techStack);
            case "go" -> reportForGo(errorType, stackTrace, techStack);
            default -> reportForJava(errorType, stackTrace, techStack);
        };

        AiCallLog log = new AiCallLog();
        log.setProvider("local");
        log.setModel("local");
        log.setStatus("success");
        log.setDurationMs(AiCallRecorder.elapsedMs(start));
        log.setInputExcerpt(AiCallRecorder.excerpt(stackTrace));
        Long logId = recorder.record(log);
        return new AiCallOutcome(report, logId);
    }

    // ---- Java：按异常类型选择内置分诊模板 ----

    private PreTriageReport reportForJava(String type, String stack, List<String> techStack) {
        String t = type == null ? "" : type;
        if (t.contains("NullPointerException")) return npe(stack, techStack);
        if (t.contains("SQLException")) return sql(stack, techStack);
        if (t.contains("Timeout") || t.contains("timeout")) return timeout(stack, techStack);
        if (t.contains("OutOfMemory")) return oom(stack, techStack);
        if (t.contains("StackOverflow")) return stackOverflow(stack, techStack);
        return genericJava(type, stack, techStack);
    }

    private PreTriageReport npe(String stack, List<String> techStack) {
        return PreTriageReport.builder()
                .personReport(PersonReport.builder()
                        .summary("程序想用「还没准备好的对象」，就像你要借东西、可东西还没到位——这叫空指针（NullPointerException）。")
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

    private PreTriageReport sql(String stack, List<String> techStack) {
        return PreTriageReport.builder()
                .personReport(PersonReport.builder()
                        .summary("你的程序在跟数据库「约事情」时没约成，卡住了（SQLException）。")
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

    private PreTriageReport timeout(String stack, List<String> techStack) {
        return PreTriageReport.builder()
                .personReport(PersonReport.builder()
                        .summary("你的程序等一个「慢动作」等太久了，超时放弃（TimeoutException）。")
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

    private PreTriageReport oom(String stack, List<String> techStack) {
        return PreTriageReport.builder()
                .personReport(PersonReport.builder()
                        .summary("程序的内存「仓库」装满了，再也放不进东西（OutOfMemoryError）。")
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

    private PreTriageReport stackOverflow(String stack, List<String> techStack) {
        return PreTriageReport.builder()
                .personReport(PersonReport.builder()
                        .summary("程序自己一层层「套娃」套太深，把栈挤爆了（StackOverflowError）。")
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

    private PreTriageReport genericJava(String type, String stack, List<String> techStack) {
        return PreTriageReport.builder()
                .personReport(PersonReport.builder()
                        .summary("检测到异常类型：" + type + "。（当前由内置规则库分诊，配置 AI 服务后可获得更具体的分析。）")
                        .layer("具体层面需看堆栈里第一个 \"at ...\" 行。")
                        .misuse("具体可疑点需结合报错上下文判断（这是方向，不是定论）。")
                        .nextStep("把「诊断包」发给项目 AI；或配置 AI 服务后重新提交，获得更具体的前置分诊。")
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

    // ---- Python ----

    private PreTriageReport reportForPython(String type, String stack, List<String> techStack) {
        String t = type == null ? "" : type;
        if (t.contains("KeyError")) {
            return personAndHandoff("程序去字典/映射里取一个「没有登记」的钥匙，结果扑空了（KeyError）。",
                    "出在取数据的那一行，通常是 dict[xxx] 或类似操作。",
                    "可能是 key 不存在、拼写不一致、或数据源里根本没这个字段。这是方向，不是定论。",
                    List.of("用 .get(key, 默认值) 代替直接下标", "先判断 key 是否存在再取值", "核对数据源字段与代码用名是否一致"),
                    stack, techStack);
        }
        if (t.contains("ValueError")) {
            return personAndHandoff("程序想把一个值「转换/解读」成别的样子，但内容对不上号（ValueError）。",
                    "出在类型转换或解析的地方，如 int()/float()/日期解析。",
                    "很可能是输入值格式不对（如空字符串转数字、非法日期）。这是方向，不是定论。",
                    List.of("看异常消息里是哪个值出问题", "转换前先校验格式", "对不可靠输入做 try/except 兜底"),
                    stack, techStack);
        }
        if (t.contains("IndexError")) {
            return personAndHandoff("程序去列表里取「第 N 个」元素，但列表没那么长（IndexError）。",
                    "出在按下标访问序列的地方，如 list[i] / tuple[i]。",
                    "很可能是越界了——下标超出列表长度，或空列表直接取 [0]。这是方向，不是定论。",
                    List.of("检查下标范围是否超出 len()", "取元素前判断列表是否为空", "用切片或加边界保护"),
                    stack, techStack);
        }
        return personAndHandoff("检测到 Python 异常类型：" + type + "。（内置规则库分诊，配置 AI 服务后更具体。）",
                "看 Traceback 里离你代码最近的 File 那一层。",
                "具体可疑点需结合报错上下文判断（方向，非定论）。",
                List.of("从 Traceback 底部往上看，找属于自己代码的 File 行", "结合该行传入的数据排查"),
                stack, techStack);
    }

    // ---- Node.js ----

    private PreTriageReport reportForNode(String type, String stack, List<String> techStack) {
        String t = type == null ? "" : type;
        if (t.contains("TypeError")) {
            return personAndHandoff("程序想对一个「不存在的东西」做操作（TypeError），比如读取 undefined 的属性。",
                    "出在调用链里第一个属于你业务代码的 at 行。",
                    "很可能是变量没定义、函数没返回值、或对象属性拼写不对。这是方向，不是定论。",
                    List.of("检查报错的变量/属性是否已定义", "用可选链 ?. 或判空保护", "核对对象结构与赋值处"),
                    stack, techStack);
        }
        if (t.contains("ReferenceError")) {
            return personAndHandoff("程序用了一个「没声明过」的变量（ReferenceError）。",
                    "出在引用该变量/函数的行。",
                    "很可能是变量名拼错、忘了声明、或作用域取错了。这是方向，不是定论。",
                    List.of("确认变量已声明且拼写一致", "检查是否在正确的作用域内引用", "注意块级作用域 let/const"),
                    stack, techStack);
        }
        return personAndHandoff("检测到 Node.js 异常类型：" + type + "。（内置规则库分诊，配置 AI 服务后更具体。）",
                "看堆栈里属于自己代码的第一个 at 行。",
                "具体可疑点需结合报错上下文判断（方向，非定论）。",
                List.of("从堆栈底部找第一个属于自己代码的 at 行", "结合该处传入的数据排查"),
                stack, techStack);
    }

    // ---- Go ----

    private PreTriageReport reportForGo(String type, String stack, List<String> techStack) {
        String t = type == null ? "" : type;
        if (t.contains("nil pointer") || t.contains("invalid memory address")) {
            return personAndHandoff("程序对一个「空指针」做了操作（nil pointer dereference）。",
                    "出在 panic 上方第一个属于你业务代码的 goroutine 栈行。",
                    "很可能是某个返回值为 nil 的变量没判空就直接用了。这是方向，不是定论。",
                    List.of("定位 panic 上方第一个业务函数", "对可能为 nil 的返回值做判空", "检查错误是否被忽略（err == nil）"),
                    stack, techStack);
        }
        if (t.contains("index out of range")) {
            return personAndHandoff("程序切了「不够长」的切片/数组（index out of range）。",
                    "出在对 slice/map 按下标访问的地方。",
                    "很可能是切片为空或越界。这是方向，不是定论。",
                    List.of("检查下标是否越界", "访问前判断 len()", "用 range 或加边界保护"),
                    stack, techStack);
        }
        return personAndHandoff("检测到 Go panic：" + type + "。（内置规则库分诊，配置 AI 服务后更具体。）",
                "看 panic 上方 goroutine 栈里属于自己业务代码的行。",
                "具体可疑点需结合报错上下文判断（方向，非定论）。",
                List.of("从 panic 往上找第一个业务函数", "结合传入数据与代码排查"),
                stack, techStack);
    }

    private PreTriageReport personAndHandoff(String summary, String layer, String misuse,
                                             List<String> checkList, String stack, List<String> techStack) {
        return PreTriageReport.builder()
                .personReport(PersonReport.builder()
                        .summary(summary)
                        .layer(layer)
                        .misuse(misuse)
                        .nextStep("把「诊断包」发给项目 AI，让它结合仓库上下文核实并给出修复方案。")
                        .build())
                .handoff(Handoff.builder()
                        .techStack(techStack)
                        .suspectedPoints(List.of(misuse))
                        .checkList(checkList)
                        .rawError(stack)
                        .build())
                .build();
    }
}
