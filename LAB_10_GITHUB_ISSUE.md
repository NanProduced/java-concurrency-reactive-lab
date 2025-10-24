# Lab-10: Project Reactor 核心库 - Reactive Streams规范与响应式编程深入

> **创建时间**：2025-10-24
> **难度等级**：⭐⭐⭐⭐（较高）
> **预计工期**：8周（4-5周集中开发 + 1-2周优化完善）
> **质量目标**：≥90分
> **前置知识**：完成Lab-01~09，特别是Lab-09的WebFlux学习

---

## 📌 项目概述

### 战略定位
Lab-10是整个14Lab教学体系的**理论支柱**：
- **Lab-09**：应用层 → "怎么用WebFlux"（框架抽象隐藏细节）
- **Lab-10**：底层库 → "为什么这样设计"（核心规范+算法原理）
- **知识闭环**：应用到原理，从"会用"到"真正理解"

### 学习路径
```
Lab-01~05: 并发基础
  ↓
Lab-06~07: 网络编程(BIO/NIO/Netty)
  ↓
Lab-08~09: 异步HTTP栈(Servlet Async → WebFlux)
  ↓
Lab-10: Project Reactor深入 ← 【你的目标】
  ↓
Lab-11+: 高级主题(虚拟线程/分布式响应式/微服务)
```

### 核心优势
与Lab-09的互补关系：
- Lab-09已有：基础Mono/Flux、常用操作符、背压概念、生产集成
- Lab-10深化：规范理论、操作符原理、背压算法、性能优化
- **复用率60% + 深度提升100%** = 学习效率最大化

---

## 🎯 5个核心学习目标

### 目标1️⃣：理解Reactive Streams规范
- 掌握Publisher/Subscriber/Subscription/Processor四大接口的语义
- 理解背压协议的核心（request(n)的传播链路）
- **能力验证**：实现符合规范的自定义Publisher，通过官方TCK测试

### 目标2️⃣：掌握Reactor核心操作符
- 创建操作符：just/create/generate/defer/using
- 转换操作符：map/flatMap/concatMap/switchMap
- 组合操作符：merge/zip/concat/combineLatest
- 错误处理：onErrorReturn/onErrorResume/retry/retryWhen
- **能力验证**：为不同场景选择最优操作符，避免反模式（如flatMap无界并发）

### 目标3️⃣：掌握背压与流量控制
- 理解4种背压策略（BUFFER/DROP/LATEST/ERROR）的适用场景
- 理解limitRate的预取策略（75%补充阈值）
- 分析背压失效的常见场景（flatMap无界、publishOn队列溢出）
- **能力验证**：完成背压策略决策树，解决5+个背压失效案例

### 目标4️⃣：掌握调度器与线程模型
- 理解4种Schedulers的设计（parallel/boundedElastic/single/immediate）
- 掌握publishOn/subscribeOn的区别和组合使用
- 理解线程切换机制（Assembly-time vs Subscription-time）
- **能力验证**：完成调度器选择决策树，优化3+个性能问题

### 目标5️⃣：掌握上下文传播与高级特性
- 理解Context的不可变特性和传播规则（从下往上）
- 掌握异常恢复策略（重试/降级/跳过）
- 理解热流冷流差异（ConnectableFlux/cache/replay）
- 理解operator fusion优化机制
- **能力验证**：完成错误处理决策树，正确处理Context传播

---

## 🚀 5个Phase递进计划

### Phase 1️⃣：Reactive Streams规范与基础操作符（2周）

**目标**：深度理解规范、实现自定义Publisher、掌握基础操作符

**关键输出物**：

1. **规范解读文档** `docs/phase-1-reactive-streams-spec.md`
   - Reactive Streams规范深度分析（4大接口、9条规则）
   - Publisher/Subscriber/Subscription/Processor接口语义详解
   - 与JDK Flow API的对比

2. **自定义Publisher实现** `src/test/java/nan/tech/lab10/spec/`
   ```
   ├── RangePublisher.java         # 发射1..N的Publisher
   ├── FilterPublisher.java        # 过滤操作符的Publisher
   ├── RangePublisherTest.java     # TCK测试（PublisherVerification）
   └── README.md                   # 如何通过TCK测试
   ```

3. **创建操作符演示** `src/main/java/nan/tech/lab10/creation/`
   - Mono.just vs Mono.defer（冷流vs延迟创建）
   - Flux.create（基于事件源）
   - Flux.generate（基于状态机）
   - Flux.using（资源管理）
   - 冷流vs热流概念演示

4. **基础操作符演示** `src/main/java/nan/tech/lab10/operators/`
   - map（1对1转换）
   - flatMap（1对N异步链）
   - filter/take/skip/distinct
   - 每个操作符3+个使用场景

5. **StepVerifier测试框架** `src/test/java/nan/tech/lab10/testing/`
   - 基础用法（expectNext/expectComplete）
   - 高级用法（expectError/verifyThenAssertThat）
   - 虚拟时间测试（withVirtualTime）
   - 背压测试（thenRequest）

**代码示例**：
```java
// 自定义Publisher（符合Reactive Streams规范）
public class RangePublisher implements Publisher<Integer> {
    @Override
    public void subscribe(Subscriber<? super Integer> s) {
        s.onSubscribe(new RangeSubscription(s, start, count));
    }
}

// TCK测试
public class RangePublisherTest extends PublisherVerification<Integer> {
    @Override
    public Publisher<Integer> createPublisher(long elements) {
        return new RangePublisher(1, (int) elements);
    }
}
```

**质量检查清单**：
- [ ] 所有自定义Publisher都符合规范
- [ ] RangePublisher + FilterPublisher通过TCK测试
- [ ] 操作符演示包含3+个使用场景
- [ ] StepVerifier测试覆盖基础和高级用法
- [ ] 注释密度≥70%，Javadoc 100%

---

### Phase 2️⃣：背压机制与流量控制（2周）

**目标**：理解背压协议、掌握4种策略、分析失效场景

**关键输出物**：

1. **背压协议深度剖析** `docs/phase-2-backpressure-explained.md`
   - request(n)的传播链路可视化
   - Subscription.request(Long.MAX_VALUE)的含义
   - 背压与线程模型的关系
   - 背压与异常处理的交互

2. **4种背压策略演示** `src/main/java/nan/tech/lab10/backpressure/`
   ```
   ├── BackpressureBufferDemo.java      # BUFFER策略
   ├── BackpressureDropDemo.java        # DROP策略
   ├── BackpressureLatestDemo.java      # LATEST策略
   ├── BackpressureErrorDemo.java       # ERROR策略
   └── BackpressureStrategyComparison   # 性能对比
   ```
   - 每种策略包含3个不同场景的演示
   - 性能指标对比（吞吐量/延迟/内存）
   - 何时选择哪种策略

3. **limitRate源码分析** `docs/phase-2-limitrate-deep-dive.md`
   - limitRate(n)的预取策略（75%补充阈值）
   - limitRate(n, m)的两参数用法
   - 与onBackpressureBuffer的区别
   - 性能优化：减少request(n)调用次数

4. **背压失效场景演示** `src/test/java/nan/tech/lab10/backpressure/pitfalls/`
   ```
   ├── FlatMapUnboundedConcurrencyTest.java     # flatMap无界并发
   ├── PublishOnQueueOverflowTest.java          # publishOn队列溢出
   ├── SubscribeOnBackpressureTest.java         # subscribeOn背压失效
   ├── BufferMemoryLeakTest.java                # buffer内存泄漏
   └── FromIterableBackpressureTest.java        # fromIterable背压优化
   ```
   - 每个失效场景包含复现代码 + 问题分析 + 解决方案

5. **背压策略决策树** `docs/phase-2-backpressure-decision-tree.md`
   ```
   数据特性
   ├─ 临时突发 → BUFFER（短期缓冲）
   ├─ 持续高压 → DROP（丢弃新数据）或 LATEST（保留最新）
   ├─ 实时数据（传感器/监控）→ DROP
   └─ 状态更新（UI/缓存）→ LATEST

   资源约束
   ├─ 内存充足 → BUFFER
   ├─ 内存有限 → DROP/LATEST
   └─ 延迟敏感 → DROP（避免缓冲延迟）
   ```

**代码示例**：
```java
// 背压失效：flatMap无界并发
Flux.range(1, 1000)
    .flatMap(i -> slowService(i)) // ❌ 默认256并发，背压失效
    .subscribe();

// 修复方案：限制并发度
Flux.range(1, 1000)
    .flatMap(i -> slowService(i), 10) // ✅ 并发度=10
    .subscribe();

// limitRate优化
Flux.range(1, 1000)
    .limitRate(100, 75) // ✅ 预取100，75%时补充
    .subscribe();
```

**质量检查清单**：
- [ ] 4种背压策略都有完整演示
- [ ] 5+个背压失效场景有复现代码和解决方案
- [ ] 性能对比数据完整（吞吐量/延迟/内存）
- [ ] 背压策略决策树清晰明确
- [ ] limitRate源码分析正确

---

### Phase 3️⃣：调度器与线程模型（1.5周）

**目标**：理解Schedulers原理、掌握publishOn/subscribeOn

**关键输出物**：

1. **Schedulers详解** `docs/phase-3-schedulers-guide.md`
   ```
   parallel        CPU密集型（核心数线程，工作窃取）
   boundedElastic  IO密集型（弹性，默认10x核心数，TTL=60s）
   single          单线程（顺序执行，共享单线程）
   immediate       当前线程（无切换，调试用）
   ```
   - 每种Scheduler的设计原理
   - 源码分析（线程创建/任务队列）
   - 适用场景和反模式

2. **publishOn vs subscribeOn对比演示** `src/main/java/nan/tech/lab10/schedulers/`
   ```
   ├── PublishOnVsSubscribeOnDemo.java      # 直观对比
   ├── PublishOnMultipleCallsDemo.java      # publishOn多次调用
   ├── SubscribeOnMultipleCallsDemo.java    # subscribeOn多次调用
   ├── ThreadSwitchingVisualization.java    # 线程切换可视化
   └── SchedulerSelectionDecisionTree.java  # 决策树实现
   ```

3. **线程切换可视化** `docs/phase-3-thread-switching-visualization.md`
   ```
   [main] INFO - subscribe()
   [parallel-1] INFO - map()
   [parallel-1] INFO - filter()
   [boundedElastic-1] INFO - flatMap()
   [boundedElastic-1] INFO - onNext()
   ```
   - 日志追踪线程切换
   - 性能开销分析

4. **调度器选择决策树** `docs/phase-3-scheduler-decision-tree.md`
   ```
   操作类型
   ├─ CPU密集（计算/编解码）→ parallel
   ├─ IO密集（非阻塞IO）→ parallel（Reactor本身就是非阻塞）
   ├─ 阻塞IO（JDBC/文件）→ boundedElastic
   └─ 顺序执行（状态机）→ single

   性能要求
   ├─ 高吞吐量 → parallel（避免上下文切换）
   ├─ 低延迟 → immediate（避免队列等待）
   └─ 资源隔离 → 自定义Scheduler（独立线程池）
   ```

5. **boundedElastic调优指南** `docs/phase-3-boundedelastic-tuning.md`
   - 线程数计算（默认10xCPU核心）
   - 队列大小配置（默认100000）
   - TTL配置（默认60s，根据任务频率调整）
   - 监控指标（活跃度/队列长度/拒绝次数）

**代码示例**：
```java
// publishOn vs subscribeOn
Flux.range(1, 10)
    .subscribeOn(Schedulers.boundedElastic()) // 上游线程
    .publishOn(Schedulers.parallel())          // 下游线程
    .subscribe();

// 调度器性能对比
Mono.fromCallable(() -> expensiveIO())
    .subscribeOn(Schedulers.parallel())        // ❌ 阻塞并发线程
    .subscribeOn(Schedulers.boundedElastic()); // ✅ 用IO线程池
```

**质量检查清单**：
- [ ] 4种Scheduler的源码分析正确
- [ ] publishOn vs subscribeOn演示清晰
- [ ] 线程切换可视化的日志追踪正确
- [ ] 调度器选择决策树完整
- [ ] boundedElastic调优指南有具体数据

---

### Phase 4️⃣：上下文传播与高级特性（1.5周）

**目标**：理解Context、掌握异常恢复、理解热流冷流

**关键输出物**：

1. **Context深度解析** `docs/phase-4-context-explained.md`
   - Context的不可变性（每次操作返回新实例）
   - 传播规则（从下往上，与数据流相反）
   - 与ThreadLocal的对比（响应式vs命令式）
   - 使用场景（tracing ID/用户上下文/租户ID）
   - Context丢失的5种场景

2. **Context实战演示** `src/main/java/nan/tech/lab10/context/`
   ```
   ├── ContextBasicsDemo.java              # Context基础用法
   ├── ContextWriteDemo.java               # contextWrite传播
   ├── DeferContextualDemo.java            # deferContextual获取
   ├── ContextLossScenarios.java           # Context丢失的5种场景
   └── MDCIntegrationDemo.java             # 与SLF4j MDC集成
   ```

3. **异常恢复策略** `src/main/java/nan/tech/lab10/errhandling/`
   ```
   ├── OnErrorReturnDemo.java              # onErrorReturn（默认值）
   ├── OnErrorResumeDemo.java              # onErrorResume（备用流）
   ├── OnErrorContinueDemo.java            # onErrorContinue（跳过）
   ├── RetryDemo.java                      # retry（简单重试）
   ├── RetryWhenDemo.java                  # retryWhen（条件重试）
   ├── ExponentialBackoffDemo.java         # 指数退避+jitter
   └── ErrorHandlingDecisionTree.java      # 决策树实现
   ```
   - 每种策略3+个使用场景
   - 何时选择哪种策略

4. **热流vs冷流** `src/main/java/nan/tech/lab10/hotvscold/`
   ```
   ├── ColdFluxDemo.java                   # 冷流：每个订阅独立
   ├── HotFluxDemo.java                    # 热流：订阅共享
   ├── PublishDemo.java                    # publish()冷流转热流
   ├── ShareDemo.java                      # share()共享+引用计数
   ├── CacheDemo.java                      # cache()缓存所有元素
   └── ReplayDemo.java                     # replay(n)重放最近n个
   ```

5. **高级组合操作符** `src/main/java/nan/tech/lab10/combinators/`
   ```
   ├── MergeDemo.java                      # merge：并行合并
   ├── ConcatDemo.java                     # concat：顺序合并
   ├── ZipDemo.java                        # zip：配对组合
   └── CombineLatestDemo.java              # combineLatest：最新组合
   ```

6. **Operator Fusion优化** `docs/phase-4-operator-fusion.md`
   - 概念：编译期操作符合并
   - Macro fusion（map+map → map）
   - Micro fusion（消除中间队列）
   - 条件：同一线程、可融合操作符
   - 性能提升（减少对象分配）

7. **错误处理决策树** `docs/phase-4-error-handling-decision-tree.md`
   ```
   错误类型
   ├─ 瞬时错误（网络抖动）→ retry（3次）
   ├─ 间歇性错误（服务降级）→ retryWhen（指数退避）
   ├─ 致命错误（配置错误）→ onErrorReturn（默认值）
   └─ 部分失败（批处理）→ onErrorContinue（跳过）

   业务影响
   ├─ 可降级 → onErrorResume（备用方案）
   ├─ 不可降级 → retry + 熔断
   └─ 需人工介入 → onErrorMap（业务异常）
   ```

**代码示例**：
```java
// Context传播（从下往上）
Flux.range(1, 10)
    .flatMap(i ->
        Mono.deferContextual(ctx ->
            Mono.just(i + " - " + ctx.get("user"))
        )
    )
    .contextWrite(Context.of("user", "Alice"))
    .subscribe();

// 指数退避重试
Flux.range(1, 10)
    .flatMap(this::mayFailService)
    .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
        .maxBackoff(Duration.ofSeconds(10))
        .jitter(0.5));

// 热流：多订阅者共享
ConnectableFlux<Integer> hot = Flux.range(1, 10)
    .publish();
hot.subscribe(i -> log.info("Sub1: {}", i));
hot.subscribe(i -> log.info("Sub2: {}", i));
hot.connect();
```

**质量检查清单**：
- [ ] Context原理和传播规则理解正确
- [ ] 5种Context丢失场景都有复现代码
- [ ] 5种异常恢复策略都有演示
- [ ] 热流vs冷流的演示清晰
- [ ] operator fusion原理解释正确
- [ ] 错误处理决策树完整

---

### Phase 5️⃣：性能对标与最佳实践（1周）

**目标**：对标分析、性能优化、知识沉淀

**关键输出物**：

1. **三维对标分析** `docs/phase-5-comparison-with-alternatives.md`
   ```
   | 维度 | Java Stream | Project Reactor | RxJava 3 | Akka Streams |
   |------|---|---|---|---|
   | 并发模型 | ForkJoinPool | Schedulers | Schedulers | Dispatcher |
   | 背压支持 | 无 | 原生 | 原生 | Materialization |
   | 异步支持 | 有限 | 完整 | 完整 | 完整 |
   | API设计 | 命令式 | 声明式 | 声明式 | Graph DSL |
   | 生态 | JDK | Spring | 独立 | Akka |
   ```
   - 性能数据对比（吞吐量/延迟/资源使用）
   - API设计对比
   - 适用场景分析

2. **JMH基准测试** `src/test/java/nan/tech/lab10/benchmark/`
   ```
   ├── OperatorPerformanceTest.java        # 操作符性能
   ├── BackpressureStrategyTest.java       # 背压策略对比
   ├── SchedulerOverheadTest.java          # 线程切换开销
   ├── OperatorFusionTest.java             # 融合优化效果
   └── LimitRateParametersTest.java        # limitRate参数调优
   ```
   - 10+个基准测试场景
   - 完整的JMH配置（参考项目最佳实践）
   - 性能数据可视化

3. **火焰图分析** `docs/phase-5-flamegraph-analysis.md`
   - 热点函数识别（Subscription.request/onNext）
   - 线程切换开销可视化
   - 内存分配热点（Queues/Arrays）
   - 优化前后对比（数据驱动）

4. **常见坑库** `docs/phase-5-pitfalls-compendium.md`
   - 30+个高频错误
   - 每个坑：现象 + 根因 + 复现代码 + 解决方案
   - 按主题分类（Context/flatMap/背压/调度器）

5. **最佳实践集合** `docs/phase-5-best-practices.md`
   - 20+条最佳实践
   - 背压策略选择指南
   - 调度器选择指南
   - 错误处理最佳实践
   - 性能优化技巧
   - 测试最佳实践

6. **决策树汇总** `docs/phase-5-decision-trees-summary.md`
   - 背压策略决策树（Phase 2）
   - 调度器选择决策树（Phase 3）
   - 错误处理决策树（Phase 4）

**代码示例**：
```java
// JMH基准测试
@Benchmark
public void mapVsFlatMapPerformance() {
    Flux.range(1, 1000)
        .map(i -> i * 2)
        .blockLast();
}

@Benchmark
public void backpressureStrategyComparison() {
    Flux.range(1, Integer.MAX_VALUE)
        .onBackpressureDrop()
        .take(10000)
        .blockLast();
}
```

**质量检查清单**：
- [ ] 三维对标分析完整（Stream/RxJava/Akka）
- [ ] 10+个JMH基准测试场景
- [ ] 火焰图分析有具体数据
- [ ] 30+个常见坑都有复现代码
- [ ] 20+条最佳实践清晰可行
- [ ] 3个决策树完整汇总

---

## 📋 代码质量检查清单

### P0 (必须)
- [ ] 所有自定义Publisher符合Reactive Streams规范，通过TCK测试
- [ ] 线程安全：无竞态条件、正确使用volatile/Atomic、无死锁
- [ ] 异常处理：正确处理onError、避免异常吞没、资源正确释放
- [ ] 操作符使用：避免常见反模式（flatMap无界并发、block()在响应式链）
- [ ] 测试覆盖：业务逻辑覆盖率≥85%

### P1 (重要)
- [ ] 文档完整：README + Javadoc + 注释 ≥90分
- [ ] 性能数据：完整的对标数据和可视化
- [ ] 决策指南：3个决策树（背压/调度器/错误处理）清晰明确
- [ ] 常见坑：30+个高频错误都有复现代码和解决方案

### P2 (可选)
- [ ] 火焰图：性能热点可视化（优化前后对比）
- [ ] CI/CD：GitHub Actions自动测试和报告

---

## 📊 质量评分标准（100分制，目标≥90分）

| 维度 | 满分 | 目标 | 检查项 |
|------|------|------|--------|
| **代码质量** | 40 | 35+ | 规范符合性10 + 线程安全10 + 异常处理8 + 操作符使用8 + 代码规范4 |
| **测试覆盖** | 20 | 17+ | 单元测试8 + 背压测试5 + 并发测试4 + TCK测试3 |
| **文档完整** | 25 | 22+ | README 8 + Javadoc 7 + 注释 5 + 架构文档 5 |
| **教学价值** | 15 | 13+ | 规范解读5 + 对标分析4 + 常见坑3 + 最佳实践3 |
| **总分** | **100** | **≥90** | 目标 94 分 ⭐⭐⭐⭐⭐ |

---

## 🗂️ 项目结构

```
lab-10-reactor-core/
├── src/main/java/nan/tech/lab10/
│   ├── creation/                    # Phase 1: 创建操作符
│   │   ├── MonoCreationDemo.java
│   │   ├── FluxCreationDemo.java
│   │   └── ColdVsHotStreamDemo.java
│   │
│   ├── operators/                   # Phase 1: 基础操作符
│   │   ├── TransformOperatorsDemo.java
│   │   ├── FilterOperatorsDemo.java
│   │   └── CombineOperatorsDemo.java
│   │
│   ├── backpressure/                # Phase 2: 背压策略
│   │   ├── BackpressureBufferDemo.java
│   │   ├── BackpressureDropDemo.java
│   │   ├── BackpressureLatestDemo.java
│   │   └── LimitRateDemo.java
│   │
│   ├── schedulers/                  # Phase 3: 调度器
│   │   ├── PublishOnVsSubscribeOnDemo.java
│   │   ├── ThreadSwitchingVisualization.java
│   │   └── BoundedElasticTuning.java
│   │
│   ├── context/                     # Phase 4: Context传播
│   │   ├── ContextBasicsDemo.java
│   │   └── ContextWriteDemo.java
│   │
│   ├── errhandling/                 # Phase 4: 异常恢复
│   │   ├── OnErrorReturnDemo.java
│   │   ├── RetryDemo.java
│   │   └── ExponentialBackoffDemo.java
│   │
│   ├── hotvscold/                   # Phase 4: 热流冷流
│   │   ├── ColdFluxDemo.java
│   │   ├── PublishDemo.java
│   │   ├── ShareDemo.java
│   │   └── ReplayDemo.java
│   │
│   ├── combinators/                 # Phase 4: 组合操作符
│   │   ├── MergeDemo.java
│   │   ├── ConcatDemo.java
│   │   ├── ZipDemo.java
│   │   └── CombineLatestDemo.java
│   │
│   └── README.md
│
├── src/test/java/nan/tech/lab10/
│   ├── spec/                        # Phase 1: 规范实现
│   │   ├── RangePublisher.java
│   │   ├── FilterPublisher.java
│   │   ├── RangePublisherTest.java  (TCK)
│   │   └── README.md (如何通过TCK)
│   │
│   ├── testing/                     # Phase 1: 测试框架
│   │   ├── StepVerifierBasicsTest.java
│   │   ├── StepVerifierAdvancedTest.java
│   │   └── VirtualTimeTest.java
│   │
│   ├── backpressure/pitfalls/       # Phase 2: 背压失效
│   │   ├── FlatMapUnboundedConcurrencyTest.java
│   │   ├── PublishOnQueueOverflowTest.java
│   │   └── ...
│   │
│   ├── benchmark/                   # Phase 5: 性能测试
│   │   ├── OperatorPerformanceTest.java (JMH)
│   │   ├── BackpressureStrategyTest.java
│   │   ├── SchedulerOverheadTest.java
│   │   └── README.md (火焰图生成指南)
│   │
│   └── ...
│
├── docs/
│   ├── phase-1-reactive-streams-spec.md
│   ├── phase-2-backpressure-explained.md
│   ├── phase-2-backpressure-decision-tree.md
│   ├── phase-3-schedulers-guide.md
│   ├── phase-3-scheduler-decision-tree.md
│   ├── phase-4-context-explained.md
│   ├── phase-4-error-handling-decision-tree.md
│   ├── phase-5-comparison-with-alternatives.md
│   ├── phase-5-pitfalls-compendium.md (30+个坑库)
│   ├── phase-5-best-practices.md (20+条最佳实践)
│   └── phase-5-decision-trees-summary.md
│
├── scripts/
│   ├── run-benchmarks.sh             # JMH基准测试脚本
│   ├── generate-flamegraph.sh        # 火焰图生成脚本
│   └── run-tests.sh                  # 运行所有测试
│
├── pom.xml
└── README.md
```

---

## 📚 技术栈和依赖

### 核心依赖
- `io.projectreactor:reactor-core:2023.x.x`
- `io.projectreactor:reactor-test:2023.x.x`
- `org.reactivestreams:reactive-streams:1.0.4`
- `org.reactivestreams:reactive-streams-tck:1.0.4`

### 测试依赖
- `org.junit.jupiter:junit-jupiter:5.10.x`
- `org.awaitility:awaitility:4.14.x` (异步等待)
- `org.openjdk.jmh:jmh-core:1.37` (JMH基准)
- `org.openjdk.jmh:jmh-generator-annprocess:1.37`

### 工具
- async-profiler (火焰图生成)
- JFR (Java Flight Recorder)

---

## 🎯 验收标准

### 代码交付
- [ ] 所有Phase的代码都能独立运行
- [ ] 编译通过：`mvn clean compile` 无警告/错误
- [ ] 测试通过：`mvn clean test` 所有测试通过
- [ ] 覆盖率达标：业务逻辑覆盖率≥85%

### 文档交付
- [ ] README完整，包含快速开始、学习路径、常见问题
- [ ] 所有Phase都有详细的markdown文档
- [ ] 3个决策树清晰明确（决策→建议）
- [ ] 30+个常见坑都有复现代码 + 解决方案

### 教学交付
- [ ] 64个教学点都有演示或解释
- [ ] 代码注释密度≥70%（特别是复杂逻辑）
- [ ] Javadoc覆盖100%（所有公开API）
- [ ] 教学价值分≥13/15

---

## 📅 时间规划

### Week 1-2: Phase 1
- Day 1-2: 规范学习 + RangePublisher实现
- Day 3-4: 操作符演示 + StepVerifier测试
- Day 5-10: 完善代码、文档、测试

### Week 3-4: Phase 2
- Day 1-4: 背压策略演示 + 失效场景分析
- Day 5-10: 决策树建立、文档完善

### Week 5-6: Phase 3
- Day 1-4: Schedulers详解 + publishOn/subscribeOn
- Day 5-10: 决策树建立、调优指南

### Week 7: Phase 4
- Day 1-2: Context详解 + 异常恢复
- Day 3-5: 热流冷流 + operator fusion
- Day 6-7: 文档完善

### Week 8: Phase 5
- Day 1-3: JMH基准测试 + 火焰图分析
- Day 4-5: 对标分析 + 常见坑库汇总
- Day 6-7: 最佳实践汇总 + 最终审查

---

## 🔗 相关资源

### 官方文档
- [Reactive Streams官网](https://www.reactive-streams.org/)
- [Project Reactor官方文档](https://projectreactor.io/docs)
- [Spring Framework WebFlux](https://docs.spring.io/spring-framework/reference/web/webflux.html)

### 学习资源
- [Reactor官方指南](https://projectreactor.io/learn)
- [Reactive Streams规范](https://github.com/reactive-streams/reactive-streams-jvm)
- [TCK测试套件](https://github.com/reactive-streams/reactive-streams-jvm/tree/main/tck)

---

## 📝 备注

### 与Lab-09的协同
- Lab-09已有基础Mono/Flux/背压概念，Lab-10深化原理
- Lab-09学应用开发，Lab-10学核心库设计
- 复用率60% + 深度提升100%

### 知识沉淀
完成Lab-10后，将沉淀：
- 5+ 个Reactor库的常见坑（加入PITFALLS_KNOWLEDGE）
- 3+ 个可复用的模板（Reactor规范实现、测试框架）
- 2+ 个自动化脚本（基准测试、火焰图生成）
- 最佳实践5+ 条（加入BEST_PRACTICES_COMPENDIUM）

---

**创建时间**：2025-10-24
**预期完成**：2025-12-19（8周）
**质量目标**：94/100 ⭐⭐⭐⭐⭐
**创建者**：Claude Code + Compounding Engineering System
