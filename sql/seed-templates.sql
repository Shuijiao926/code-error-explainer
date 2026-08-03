-- 内置异常知识卡片种子数据（幂等：重复执行按 error_type 更新）
USE code_error_explainer;

INSERT INTO error_template (error_type, category, cause_top3, fix_template, hierarchy) VALUES
('NullPointerException', '空指针',
 '["依赖注入未生效（缺 @Autowired/@Service 或包扫描范围）","查询/方法返回 null 未判空","字段或局部变量未初始化"]',
 '1. 定位堆栈中第一个属于业务代码的 at 行\n2. 检查该行调用对象是否为 null\n3. 对可能为 null 的返回值判空或用 Optional 兜底\n4. 优先用构造器注入替代字段注入',
 'Object → Throwable → Exception → RuntimeException → NullPointerException'),

('ClassCastException', '类型转换',
 '["强转目标与实际类型不符","反序列化/JSON 目标类型声明错误","泛型擦除导致误转"]',
 '1. 打印对象实际类型（obj.getClass()）\n2. 检查反序列化目标类型声明\n3. 强转前用 instanceof / Class.isInstance 判断',
 'Object → Throwable → Exception → RuntimeException → ClassCastException'),

('ConcurrentModificationException', '并发',
 '["遍历集合时修改集合（增删元素）","多线程并发修改同一个集合","迭代器与集合状态不一致"]',
 '1. 避免在 foreach 中直接增删元素\n2. 用 CopyOnWriteArrayList / ConcurrentHashMap 等并发容器\n3. 或先收集再统一修改',
 'Object → Throwable → Exception → RuntimeException → ConcurrentModificationException'),

('SQLException', 'SQL',
 '["连接池耗尽（最大连接数过小）","SQL 语法或表结构不匹配","唯一约束/外键约束冲突、死锁"]',
 '1. 看错误消息中的 SQLSTATE 编号定位原因\n2. 核对 Mapper/XML SQL 与表结构\n3. 检查连接池配置与并发量\n4. 死锁时统一加锁顺序',
 'Object → Throwable → Exception → SQLException'),

('OutOfMemoryError', '内存',
 '["缓存/集合无限增长未清理","一次性加载过大数据集","内存泄漏（对象无法回收）"]',
 '1. 用 jmap/MAT 分析堆转储定位占用大户\n2. 检查集合/缓存是否无限增长\n3. 大数据量分批/流式处理\n4. 按负载调整 -Xmx',
 'Object → Throwable → Error → VirtualMachineError → OutOfMemoryError'),

('StackOverflowError', '栈溢出',
 '["递归缺少终止条件","对象间循环调用（A 调 B、B 又调 A）","深层递归层级过深"]',
 '1. 在堆栈中找重复出现的类名/方法名\n2. 检查递归终止条件\n3. 循环引用加防重标识\n4. 深递归改迭代+显式栈',
 'Object → Throwable → Error → VirtualMachineError → StackOverflowError'),

('TimeoutException', '超时',
 '["下游服务/慢 SQL 响应超时","超时阈值配置过短","线程池/连接池耗尽排队"]',
 '1. 定位超时发生跳（DB/HTTP/线程池）\n2. 检查下游性能瓶颈\n3. 评估调大超时阈值或加熔断\n4. 检查池参数与并发',
 'Object → Throwable → Exception → TimeoutException'),

('IllegalArgumentException', '参数校验',
 '["入参非法（空值/越界/格式错误）","业务前置条件未满足","方法契约被违反"]',
 '1. 看异常消息说明哪个参数违规\n2. 在方法入口用 Objects.requireNonNull / 校验\n3. 明确入参契约并文档化',
 'Object → Throwable → Exception → RuntimeException → IllegalArgumentException')
ON DUPLICATE KEY UPDATE
  category = VALUES(category),
  cause_top3 = VALUES(cause_top3),
  fix_template = VALUES(fix_template),
  hierarchy = VALUES(hierarchy);
