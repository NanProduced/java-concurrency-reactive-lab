# Phase 1C: StepVerifier 测试框架演示

## 📌 概述

本目录包含 Lab-10 Phase 1C 的 StepVerifier 测试框架演示，共 12 个可运行的测试示例，重点是：

1. 掌握 StepVerifier 的基础验证方法
2. 理解虚拟时间测试机制
3. 掌握背压协议测试
4. 学会使用 TestPublisher 创建自定义流

## 📂 文件结构

```
testing/
├── StepVerifierDemo.java      # StepVerifier 演示 (12个demo)
└── README.md                  # 本文件
```

## 🎯 核心概念

### StepVerifier 工作原理

```
StepVerifier 是 Reactor 提供的反应式流测试框架。

工作流程：
  1. 创建一个 Publisher (Flux/Mono)
  2. 通过 StepVerifier.create() 创建验证器
  3. 定义期望行为（expectNext、expectError 等）
  4. 通过 verify() 执行验证
  5. 比对实际值和期望值

特点：
  ✅ 同步/非阻塞验证
  ✅ 虚拟时间支持（加速延迟流测试）
  ✅ 背压测试能力
  ✅ 错误和超时处理
  ✅ 灵活的自定义断言
```

### 验证类型分类

#### 1️⃣ 基础验证 (BasicVerification)

```
├─ expectNext(T)           - 验证下一个元素
├─ expectNextCount(long)   - 验证N个元素存在（不关心值）
├─ expectComplete()        - 验证流正常完成
├─ expectError(Class<E>)   - 验证特定错误类型
└─ expectErrorMessage()    - 验证错误消息
```

**Demo**: `demo1_BasicExpectNext()`, `demo2_ExpectNextCount()`, `demo3_ExpectError()`

**示例代码**：
```java
Flux.just(1, 2, 3)
    .as(flux -> StepVerifier.create(flux)
        .expectNext(1)
        .expectNext(2)
        .expectNext(3)
        .expectComplete()
        .verify()
    );
```

**关键点**：
- expectNext() 验证精确值
- expectComplete() 必须是最后一步
- verify() 执行验证并阻塞等待

#### 2️⃣ 断言验证 (Assertion Verification)

```
├─ assertNext(Consumer<T>)     - 对元素进行自定义断言
├─ consumeNextWith(Consumer<T>)- 消费元素并执行操作
└─ verifyThenAssertThat()      - 验证后进行额外断言
```

**Demo**: `demo5_AssertNext()`, `demo6_VerifyThenAssertThat()`, `demo11_ConsumeNext()`

**示例代码**：
```java
Flux.range(1, 5)
    .map(x -> x * 2)
    .as(flux -> StepVerifier.create(flux)
        .assertNext(x -> {
            assert x == 2 : "first element should be 2";
        })
        .assertNext(x -> assert x == 4 : "second element should be 4")
        .expectComplete()
        .verify()
    );
```

**关键点**：
- assertNext() 支持任意 Consumer 逻辑
- consumeNextWith() 类似但可以消费元素
- verifyThenAssertThat() 返回 StepVerifier.Assertions 对象用于额外检查

#### 3️⃣ 虚拟时间测试 (Virtual Time Testing)

```
├─ withVirtualTime()        - 启用虚拟时间模式
├─ thenAwait(Duration)      - 虚拟时间等待
├─ expectNoEvent(Duration)  - 期望在时间内没有事件
└─ expectSubscription()      - 验证订阅
```

**Demo**: `demo7_VirtualTime()`

**时间轴示例**：
```
实际时间轴：
  0ms:  start
  1s:   delay完成，第一个元素 → 0
  2s:   delay完成，第二个元素 → 1
  3s:   delay完成，第三个元素 → 2

虚拟时间轴：
  0ms:  start (瞬间)
  ↓ expectSubscription() - 验证已订阅
  ↓ expectNoEvent(1s) - 虚拟时间快进1秒（无事件）
  ↓ expectNext(0) - 得到第一个元素
  ↓ thenAwait(1s) - 继续快进1秒
  ↓ expectNext(1) - 得到第二个元素
  ↓ thenAwait(1s) - 继续快进1秒
  ↓ expectNext(2) - 得到第三个元素
  ✅ expectComplete() - 流完成

总执行时间：<100ms（不是3秒）
```

**示例代码**：
```java
StepVerifier.withVirtualTime(() ->
        Flux.interval(Duration.ofSeconds(1))
            .take(3)
    )
    .expectSubscription()
    .expectNoEvent(Duration.ofSeconds(1))
    .expectNext(0L)
    .thenAwait(Duration.ofSeconds(1))
    .expectNext(1L)
    .thenAwait(Duration.ofSeconds(1))
    .expectNext(2L)
    .expectComplete()
    .verify();
```

**关键点**：
- 虚拟时间测试加快速度（3秒缩短到毫秒）
- withVirtualTime() 接受 Supplier<Publisher>
- expectNoEvent() 用于验证空白期
- thenAwait() 虚拟快进时间

#### 4️⃣ 背压测试 (Backpressure Testing)

```
├─ create(Publisher, initialDemand) - 指定初始需求数
├─ thenRequest(long)               - 请求更多元素
└─ consumeRecordedWith()           - 消费已记录的元素
```

**Demo**: `demo8_BackpressureTest()`

**背压协议图示**：
```
Publisher: ┬─1─┬─2─┬─3─┬─4─┬─5─┬─6─┬─7─┬─8─┬─9─┬─10─┐
           │   │   │   │   │   │   │   │   │   │    │
StepVerifier发送请求：
  [step 1]  request(1)  ← 只接收第1个
  [step 2]  thenRequest(2) ← 接收第2-3个
  [step 3]  thenRequest(5) ← 接收第4-8个
  [step 4]  thenRequest(2) ← 接收第9-10个
```

**示例代码**：
```java
Flux.range(1, 10)
    .as(flux -> StepVerifier.create(flux, 1) // 初始需求=1
        .expectNext(1)      // 得到第1个
        .thenRequest(2)     // 请求2个
        .expectNext(2, 3)   // 得到第2-3个
        .thenRequest(5)     // 请求5个
        .expectNextCount(5) // 得到第4-8个
        .thenRequest(2)     // 请求2个
        .expectNextCount(2) // 得到第9-10个
        .expectComplete()
        .verify()
    );
```

**关键点**：
- StepVerifier.create(publisher, initialDemand)
- initialDemand = 初始请求数
- thenRequest(n) = 请求n个额外元素
- 验证 Publisher 是否尊重背压

#### 5️⃣ TestPublisher（自定义流）

```
├─ TestPublisher.create()     - 创建测试发布者
├─ next(T)                    - 发送元素
├─ complete()                 - 完成流
├─ error(Throwable)          - 发送错误
└─ then(Runnable)            - 执行操作后继续
```

**Demo**: `demo10_TestPublisher()`

**示例代码**：
```java
TestPublisher<String> publisher = TestPublisher.create();

StepVerifier.create(publisher)
    .expectSubscription()
    .then(() -> publisher.next("hello"))     // 发送元素
    .expectNext("hello")
    .then(() -> publisher.next("world"))
    .expectNext("world")
    .then(() -> publisher.complete())        // 完成流
    .expectComplete()
    .verify();
```

**关键点**：
- 手动控制流的发送时机
- 用于测试复杂的流行为
- 可以精确验证订阅-发送时序

#### 6️⃣ Mono 特殊处理

```
├─ expectNext(T)     - 验证唯一元素
├─ expectComplete()  - 验证完成
└─ expectError()     - 验证错误
```

**Demo**: `demo9_MonoVerification()`

**示例代码**：
```java
Mono.just(42)
    .as(mono -> StepVerifier.create(mono)
        .expectNext(42)
        .expectComplete()
        .verify()
    );
```

**关键点**：
- Mono 最多只有 1 个元素
- 相比 Flux 验证更简单

#### 7️⃣ 超时控制

```
└─ verify(Duration) - 指定超时时间
```

**Demo**: `demo12_Timeout()`

**示例代码**：
```java
Flux.range(1, 5)
    .as(flux -> StepVerifier.create(flux)
        .expectNextCount(5)
        .expectComplete()
        .verify(Duration.ofSeconds(1))  // 1秒超时
    );
```

**关键点**：
- verify() 默认超时 = 5 秒
- 可以自定义超时时间
- 防止测试无限等待

## 🧪 运行演示

### 运行所有测试

```bash
cd lab-10-reactor-core
mvn clean test -Dtest=StepVerifierDemo
```

### 运行特定测试

```bash
# 运行基础验证测试
mvn clean test -Dtest=StepVerifierDemo#demo1_BasicExpectNext

# 运行虚拟时间测试
mvn clean test -Dtest=StepVerifierDemo#demo7_VirtualTime

# 运行背压测试
mvn clean test -Dtest=StepVerifierDemo#demo8_BackpressureTest
```

### 查看详细日志

```bash
mvn clean test -Dtest=StepVerifierDemo -X
```

## 📊 演示统计

| Demo | 名称 | 功能 | 难度 |
|------|------|------|------|
| demo1 | BasicExpectNext | 基础元素验证 | ⭐ |
| demo2 | ExpectNextCount | 元素计数验证 | ⭐ |
| demo3 | ExpectError | 错误类型验证 | ⭐ |
| demo4 | ExpectErrorMessage | 错误消息验证 | ⭐ |
| demo5 | AssertNext | 自定义断言 | ⭐⭐ |
| demo6 | VerifyThenAssertThat | 验证后断言 | ⭐⭐ |
| demo7 | VirtualTime | 虚拟时间测试 | ⭐⭐⭐ |
| demo8 | BackpressureTest | 背压协议测试 | ⭐⭐⭐ |
| demo9 | MonoVerification | Mono 验证 | ⭐ |
| demo10 | TestPublisher | 自定义流 | ⭐⭐⭐ |
| demo11 | ConsumeNext | 元素消费 | ⭐⭐ |
| demo12 | Timeout | 超时控制 | ⭐⭐ |

## 💡 常见模式总结

### 模式1：验证简单流

```java
StepVerifier.create(
    Flux.just(1, 2, 3)
)
.expectNext(1, 2, 3)
.expectComplete()
.verify();
```

### 模式2：验证错误流

```java
StepVerifier.create(
    Flux.error(new RuntimeException("Boom!"))
)
.expectErrorMessage("Boom!")
.verify();
```

### 模式3：虚拟时间（快速测试延迟流）

```java
StepVerifier.withVirtualTime(() ->
    Flux.interval(Duration.ofSeconds(1)).take(3)
)
.expectSubscription()
.expectNoEvent(Duration.ofSeconds(1))
.expectNext(0L)
.thenAwait(Duration.ofSeconds(2))
.expectNext(1L, 2L)
.expectComplete()
.verify();
```

### 模式4：背压测试

```java
StepVerifier.create(
    Flux.range(1, 10),
    1  // 初始需求=1
)
.expectNext(1)
.thenRequest(5)
.expectNextCount(5)
.thenRequest(4)
.expectNextCount(4)
.expectComplete()
.verify();
```

### 模式5：自定义流控制

```java
TestPublisher<Integer> publisher = TestPublisher.create();

StepVerifier.create(publisher)
    .expectSubscription()
    .then(() -> publisher.next(1))
    .expectNext(1)
    .then(() -> publisher.complete())
    .expectComplete()
    .verify();
```

### 模式6：带断言的验证

```java
StepVerifier.create(
    Flux.range(1, 3).map(x -> x * 10)
)
.assertNext(x -> assert x == 10)
.assertNext(x -> assert x == 20)
.assertNext(x -> assert x == 30)
.expectComplete()
.verify();
```

## 🚀 进阶主题

### 1. 超时与边界条件

```java
// 测试超时
StepVerifier.create(
    Flux.interval(Duration.ofSeconds(10)).take(1)
)
.expectSubscription()
.expectTimeout(Duration.ofSeconds(1))  // 期望超时
.verify();

// 测试无限流
StepVerifier.create(
    Flux.interval(Duration.ofMillis(100))
)
.expectSubscription()
.expectNoEvent(Duration.ofSeconds(2))  // 2秒内只期望事件
.thenCancel()  // 取消订阅
.verify();
```

### 2. 复杂的背压场景

```java
StepVerifier.create(
    Flux.range(1, 100),
    0  // 初始需求=0（背压）
)
.expectSubscription()
.thenRequest(10)    // 第1批：1-10
.expectNextCount(10)
.thenRequest(20)    // 第2批：11-30
.expectNextCount(20)
.thenRequest(70)    // 第3批：31-100
.expectNextCount(70)
.expectComplete()
.verify();
```

### 3. 动态流生成

```java
StepVerifier.create(
    Mono.just(1)
        .flatMapMany(n ->
            Flux.range(1, n)
                .delayElement(Duration.ofMillis(100))
        )
)
.expectSubscription()
.expectNext(1)
.expectComplete()
.verify();
```

## 📚 学习检查清单

学完本模块，检查以下要点：

- [ ] 理解 StepVerifier 的工作原理
- [ ] 掌握 expectNext/expectError/expectComplete 的使用
- [ ] 理解虚拟时间测试的优势（加速）
- [ ] 掌握背压测试的基本方法
- [ ] 能够使用 TestPublisher 创建自定义流
- [ ] 理解 assertNext 和 consumeNextWith 的区别
- [ ] 掌握超时控制的使用
- [ ] 能够为异步流编写有效的测试
- [ ] 理解虚拟时间如何加快测试速度
- [ ] 能够验证 Publisher 是否遵守背压协议

## 🎓 完成标准

**知识点掌握**：
- ✅ 7种验证方式理解深度
- ✅ 虚拟时间测试机制
- ✅ 背压测试方法
- ✅ TestPublisher 使用能力

**实战能力**：
- ✅ 能独立编写反应式流测试
- ✅ 理解测试框架能力边界
- ✅ 能设计复杂流的测试策略

---

**Status**: ✅ Phase 1C - StepVerifier 测试框架演示（12个demo）
**Next**: Phase 2 - 高级操作符与并发控制
**Quality Target**: ≥90/100

---

*本文档生成于 2025-10-24*
*包含12个可运行的测试演示*
