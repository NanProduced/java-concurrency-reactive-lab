# Lab-10: Project Reactor 核心库

## 📌 项目概述

**Lab-10** 是java-concurrency-reactive-lab教学体系的**理论支柱**和**深化层**。

### 战略定位

```
Lab-09: 应用层  → "怎么用WebFlux"（框架隐藏细节）
          ↓
Lab-10: 底层库  → "为什么这样设计"（核心规范+算法原理）
          ↓
       知识闭环：从"会用"到"真正理解"
```

### 核心数据

| 维度 | 数值 |
|------|------|
| 工期 | 8周（4-5周开发 + 1-2周优化） |
| 难度 | ⭐⭐⭐⭐（较高，需理解规范+源码） |
| 教学点 | 64个演示/教学点 |
| Phase数 | 5个递进式Phase |
| 代码行数 | ~3000+ |
| 文档量 | ~50+ 页 |
| 质量目标 | ≥90分 |

## 🎯 5个核心学习目标

### 目标1️⃣：理解Reactive Streams规范
- 掌握Publisher/Subscriber/Subscription/Processor四大接口
- 理解背压协议的核心（request(n)的传播链路）
- **能力验证**：实现符合规范的自定义Publisher，通过官方TCK测试

### 目标2️⃣：掌握Reactor核心操作符
- 创建操作符：just/create/generate/defer/using
- 转换操作符：map/flatMap/concatMap/switchMap
- 组合操作符：merge/zip/concat/combineLatest
- 错误处理：onErrorReturn/onErrorResume/retry/retryWhen
- **能力验证**：为不同场景选择最优操作符

### 目标3️⃣：掌握背压与流量控制
- 理解4种背压策略（BUFFER/DROP/LATEST/ERROR）
- 理解limitRate的预取策略（75%补充阈值）
- **能力验证**：完成背压策略决策树，解决5+个背压失效案例

### 目标4️⃣：掌握调度器与线程模型
- 理解4种Schedulers的设计（parallel/boundedElastic/single/immediate）
- 掌握publishOn/subscribeOn的区别和组合使用
- **能力验证**：完成调度器选择决策树，优化3+个性能问题

### 目标5️⃣：掌握Context传播与高级特性
- 理解Context的不可变特性和传播规则
- 掌握异常恢复策略（重试/降级/跳过）
- 理解热流冷流差异（ConnectableFlux/cache/replay）
- **能力验证**：完成错误处理决策树，处理Context传播

## 🚀 5个Phase递进计划

### ✅ Phase 1 (2周)：Reactive Streams规范与基础操作符

**状态**：60% 完成 🔄

**完成进度**：
- ✅ RangePublisher实现（符合规范）
- ✅ RangeSubscription实现（背压协议）
- ✅ TCK测试通过（3/3）
- ✅ 规范解读文档完成
- ✅ Mono创建操作符演示（10个demo）
- ✅ Flux创建操作符演示（12个demo）
- 📝 基础操作符演示（开发中）
- 📝 StepVerifier测试框架（计划中）

**关键文件**：
```
src/test/java/nan/tech/lab10/spec/
├── RangePublisher.java        ✅ 自定义Publisher
├── RangeSubscription.java     ✅ Subscription实现
├── RangePublisherTest.java    ✅ TCK + 单元测试
└── README.md                  ✅ 详细文档

src/main/java/nan/tech/lab10/creation/
├── MonoCreationDemo.java      ✅ Mono创建演示（10个demo）
├── FluxCreationDemo.java      ✅ Flux创建演示（12个demo）
└── README.md                  ✅ 详细文档
```

**测试结果**：
```
TCK Tests: 3/3 passing
✅ testRangePublisherBasicBehavior
✅ testBackpressureSupport
✅ testCancelBehavior

Creation Demos: All passing
✅ MonoCreationDemo (10 demos: just/empty/error/defer/create/delay/etc)
✅ FluxCreationDemo (12 demos: just/range/interval/generate/create/etc)
```

**演示覆盖率**：
- Mono: 10种创建方式 (就/空/错/延迟/创建/可选等)
- Flux: 12种创建方式 (范围/时间间隔/状态机/手动控制等)
- 冷流特性: ✅ 多次订阅演示
- 热流演示: ✅ share() 对比
- 错误处理: ✅ 完整

### ⏳ Phase 2 (2周)：背压机制与流量控制

**预计启动**：Phase 1完成后

**关键输出物**：
- 背压协议深度剖析文档
- 4种背压策略演示代码
- limitRate源码分析
- 背压失效场景（5+个）
- 背压策略决策树

### ⏳ Phase 3 (1.5周)：调度器与线程模型

**预计启动**：Phase 2完成后

**关键输出物**：
- Schedulers详解（4种）
- publishOn vs subscribeOn演示
- 线程切换可视化
- 调度器选择决策树
- boundedElastic调优指南

### ⏳ Phase 4 (1.5周)：Context与高级特性

**预计启动**：Phase 3完成后

**关键输出物**：
- Context深度解析
- Context实战演示
- 异常恢复策略（5种）
- 热流vs冷流演示
- 错误处理决策树

### ⏳ Phase 5 (1周)：性能对标与最佳实践

**预计启动**：Phase 4完成后

**关键输出物**：
- 三维对标分析（Stream/RxJava/Akka）
- JMH基准测试（10+个场景）
- 火焰图分析
- 常见坑库（30+个）
- 最佳实践集合（20+条）

## 📂 项目结构

```
lab-10-reactor-core/
├── src/main/java/nan/tech/lab10/
│   ├── creation/                    # Phase 1: 创建操作符
│   ├── operators/                   # Phase 1: 基础操作符
│   ├── backpressure/                # Phase 2: 背压策略
│   ├── schedulers/                  # Phase 3: 调度器
│   ├── context/                     # Phase 4: Context传播
│   ├── errhandling/                 # Phase 4: 异常恢复
│   ├── hotvscold/                   # Phase 4: 热流冷流
│   └── combinators/                 # Phase 4: 组合操作符
│
├── src/test/java/nan/tech/lab10/
│   ├── spec/                        # ✅ Phase 1: 规范实现
│   │   ├── RangePublisher.java
│   │   ├── RangeSubscription.java
│   │   ├── RangePublisherTest.java
│   │   └── README.md
│   ├── testing/                     # Phase 1: 测试框架
│   ├── backpressure/pitfalls/       # Phase 2: 背压失效
│   └── benchmark/                   # Phase 5: 性能测试
│
├── docs/
│   ├── phase-1-reactive-streams-spec.md
│   ├── phase-2-backpressure-explained.md
│   ├── phase-3-schedulers-guide.md
│   ├── phase-4-context-explained.md
│   └── phase-5-comparison-with-alternatives.md
│
├── scripts/
│   ├── run-benchmarks.sh
│   ├── generate-flamegraph.sh
│   └── run-tests.sh
│
├── pom.xml                          ✅ Maven配置
└── README.md                        ← 本文件
```

## 🚀 快速开始

### 1. 环境要求

```bash
# 验证环境
java -version       # Java 17+
mvn -v             # Maven 3.9+
git --version      # Git
```

### 2. 构建项目

```bash
# 在项目根目录
mvn clean install -DskipTests

# 或进入lab-10目录
cd lab-10-reactor-core
mvn clean compile
```

### 3. 运行Phase 1测试

```bash
# 运行所有Phase 1测试
mvn clean test -Dtest=RangePublisherTest

# 运行特定测试
mvn clean test -Dtest=RangePublisherTest#testRangePublisherBasicBehavior

# 查看覆盖率报告
mvn clean test jacoco:report
```

### 4. 查看代码和文档

```bash
# 查看Phase 1详细文档
cat src/test/java/nan/tech/lab10/spec/README.md

# 查看RangePublisher源码
cat src/test/java/nan/tech/lab10/spec/RangePublisher.java
```

## 📚 核心概念

### Reactive Streams规范的4个接口

```java
// 1. Publisher: 数据源
Publisher<T> {
    void subscribe(Subscriber<? super T> s);
}

// 2. Subscriber: 数据接收方
Subscriber<T> {
    void onSubscribe(Subscription s);
    void onNext(T t);
    void onError(Throwable t);
    void onComplete();
}

// 3. Subscription: 发布者和订阅者之间的合约
Subscription {
    void request(long n);  // 背压核心：订阅者请求n个元素
    void cancel();         // 取消订阅
}

// 4. Processor: 既是Publisher又是Subscriber
Processor<T, R> extends Publisher<T>, Subscriber<R> {
}
```

### 背压协议的核心

```
Subscriber                          Publisher
    │                                  │
    ├─ onSubscribe(subscription) ─────>│
    │                                  │
    ├─ request(10) ────────────────────>│ (请求10个元素)
    │                                  │
    │<──────── onNext(elem1) ────────┤
    │<──────── onNext(elem2) ────────┤
    │  ...                           │
    │<──────── onNext(elem10) ───────┤
    │                                  │
    ├─ request(5) ────────────────────>│ (继续请求5个)
    │<──────── onNext(elem11) ───────┤
    │  ...                           │
    │                                  │
    │<──── onComplete() ─────────────┤
```

### 典型场景

#### 场景1：完整流

```java
Publisher<Integer> pub = new RangePublisher(1, 5);

pub.subscribe(new Subscriber<Integer>() {
    @Override
    public void onSubscribe(Subscription s) {
        s.request(Long.MAX_VALUE);  // 请求所有元素
    }

    @Override
    public void onNext(Integer value) {
        System.out.println(value);  // 输出：1, 2, 3, 4, 5
    }

    @Override
    public void onError(Throwable t) {
        t.printStackTrace();
    }

    @Override
    public void onComplete() {
        System.out.println("完成");  // 输出：完成
    }
});
```

#### 场景2：背压控制

```java
pub.subscribe(new Subscriber<Integer>() {
    private Subscription s;

    @Override
    public void onSubscribe(Subscription subscription) {
        this.s = subscription;
        s.request(2);  // 首先只请求2个
    }

    @Override
    public void onNext(Integer value) {
        System.out.println(value);  // 输出：1, 2
        s.request(1);  // 每次接收一个，继续请求一个
    }

    @Override
    public void onError(Throwable t) {}

    @Override
    public void onComplete() {}
});
```

## 📊 质量评分标准（100分制，目标≥90分）

| 维度 | 满分 | 目标 | 检查项 |
|------|------|------|--------|
| **代码质量** | 40 | 35+ | 规范符合性10 + 线程安全10 + 异常处理8 + 操作符使用8 + 代码规范4 |
| **测试覆盖** | 20 | 17+ | 单元测试8 + 背压测试5 + 并发测试4 + TCK测试3 |
| **文档完整** | 25 | 22+ | README 8 + Javadoc 7 + 注释 5 + 架构文档 5 |
| **教学价值** | 15 | 13+ | 规范解读5 + 对标分析4 + 常见坑3 + 最佳实践3 |
| **总分** | **100** | **≥90** | 目标 94 分 |

## 🔗 相关资源

### 官方文档
- [Reactive Streams官网](https://www.reactive-streams.org/)
- [Project Reactor文档](https://projectreactor.io/docs)
- [Spring WebFlux](https://docs.spring.io/spring-framework/reference/web/webflux.html)

### 学习资源
- [Reactor官方指南](https://projectreactor.io/learn)
- [Reactive Streams规范](https://github.com/reactive-streams/reactive-streams-jvm)
- [TCK测试套件](https://github.com/reactive-streams/reactive-streams-jvm/tree/main/tck)

### 相关Lab
- [Lab-09: Spring WebFlux](../lab-09-springmvc-vs-webflux)
- [Lab-08: Spring MVC Async](../lab-08-springmvc-async)
- [Lab-07: Netty](../lab-07-netty)

## 📋 当前进度

### Phase 1 进度

```
总体进度: [████████░░░░░░░░░░░░] 40%

✅ 核心代码实现
  ├─ RangePublisher.java (✅ 完成)
  ├─ RangeSubscription.java (✅ 完成)
  └─ RangePublisherTest.java (✅ 完成，3/3测试通过)

📝 文档编写
  ├─ spec/README.md (✅ 完成)
  ├─ phase-1-reactive-streams-spec.md (进行中)
  └─ 规范深度解读 (计划中)

⏳ 创建操作符演示 (待启动)
  ├─ Mono.just vs Mono.defer
  ├─ Flux.create
  ├─ Flux.generate
  └─ Flux.using

⏳ 基础操作符演示 (待启动)
  ├─ map (1对1转换)
  ├─ flatMap (1对N异步链)
  ├─ filter/take/skip/distinct
  └─ 每个3+个使用场景

⏳ StepVerifier测试框架 (待启动)
  ├─ 基础用法
  ├─ 高级用法
  └─ 虚拟时间测试
```

### 下一步计划

**本周目标**：
- [ ] 完成创建操作符演示
- [ ] 完成基础操作符演示
- [ ] 编写规范深度解读文档

**下周目标**：
- [ ] 完成StepVerifier测试框架
- [ ] 启动Phase 2：背压机制

## 💡 开发建议

### 学习路径

1. **理解规范**（1-2天）
   - 阅读Reactive Streams规范（§1-§3）
   - 理解4个接口和43条规则

2. **实现Publisher**（2-3天）
   - 实现RangePublisher
   - 实现RangeSubscription
   - 通过TCK测试

3. **创建操作符**（3-5天）
   - 实现Mono创建操作符
   - 实现Flux创建操作符
   - 编写演示和文档

4. **测试框架**（2-3天）
   - 学习StepVerifier
   - 编写虚拟时间测试
   - 掌握背压测试

### 常见坑点

- ❌ 忘记调用`onSubscribe()`
- ❌ 在`request(n)`前发送数据
- ❌ 忽略背压限制
- ❌ 异常没有传递给`onError()`
- ❌ `cancel()`调用后还继续发送

## 📞 获取帮助

### 遇到问题？

1. **查看Phase 1文档**：`src/test/java/nan/tech/lab10/spec/README.md`
2. **检查代码注释**：详细的Javadoc和中文注释
3. **运行测试**：看测试代码如何使用
4. **查看规范**：https://www.reactive-streams.org/

### 提交反馈

- GitHub Issues: 提交bug或功能建议
- 更新文档：贡献经验和最佳实践
- 共享坑库：记录遇到的问题和解决方案

## 📈 质量检查清单

- [ ] 所有代码编译通过
- [ ] 所有单元测试通过
- [ ] 代码覆盖率≥85%
- [ ] Javadoc覆盖率100%
- [ ] 中文注释密度≥70%
- [ ] 没有编译警告
- [ ] 遵循命名规范
- [ ] 异常处理完整

## 🎓 成功标准

**Phase 1完成标准**：
- ✅ RangePublisher通过TCK测试
- ✅ 理解背压协议核心
- ✅ 掌握创建和基础操作符
- ✅ 能够独立实现符合规范的Publisher
- ✅ 文档完整，能够解释所有概念

## 📝 版本信息

| 项 | 值 |
|------|------|
| **版本** | 1.0.0 |
| **Lab编号** | Lab-10 |
| **创建时间** | 2025-10-24 |
| **预期完成** | 2025-12-19 |
| **质量目标** | 94/100 分 |

---

**Happy Learning! 🚀**
