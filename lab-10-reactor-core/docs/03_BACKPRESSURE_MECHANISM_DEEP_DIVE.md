# 背压机制深度剖析

> **文档版本**: 1.0
> **创建日期**: 2025-10-24
> **难度等级**: ⭐⭐⭐⭐ (高)
> **阅读时间**: 30-45 分钟
> **先修知识**: Phase 1（Reactive Streams 规范、Subscription）

---

## 目录

1. [核心概念](#核心概念)
2. [背压协议完整流程](#背压协议完整流程)
3. [4 种背压策略](#4-种背压策略)
4. [背压失效场景](#背压失效场景)
5. [limitRate 源码分析](#limitrate-源码分析)
6. [性能影响分析](#性能影响分析)
7. [背压策略决策树](#背压策略决策树)
8. [最佳实践](#最佳实践)
9. [常见误区](#常见误区)

---

## 核心概念

### 背压的本质

**定义**：背压（Backpressure）是消费者控制生产速度的机制。当消费者处理速度较慢时，告诉生产者"我只能处理 N 个元素"，生产者遵守这个限制。

```
背压前（无限制）:
生产者: ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓... (无限快速生产)
消费者: ░░░ (缓慢消费)
内存: 内存缓冲区爆炸 → OOM ❌

背压后（受控）:
生产者: ▓░░░ ▓░░░ ▓░░░ (等待消费者请求)
消费者: ░▓▓▓ (主动请求)
内存: 稳定可控 ✅
```

### 背压的三个核心参与者

1. **生产者 (Publisher)**: 生成数据，等待消费请求
2. **消费者 (Subscriber)**: 消费数据，控制流速
3. **订阅管理 (Subscription)**: 中介，传递请求

### 背压的关键方法：`request(n)`

```java
subscription.request(100);  // "我现在可以处理 100 个元素"
```

**关键特性**:
- ✅ n > 0 验证（§3.9）
- ✅ 可多次调用 `request(10), request(20), request(70)`
- ✅ 可累积：总共请求 100 个
- ✅ 无上限验证（可请求 Long.MAX_VALUE）

---

## 背压协议完整流程

### Step 1: 定义流程图

```
┌─────────────────────────────────────────────────────────────┐
│ Timeline: 背压协议完整流程                                    │
└─────────────────────────────────────────────────────────────┘

时刻 T0:
  Subscriber:  调用 subscribe()
  Publisher:   创建 Subscription，发送 onSubscribe(subscription)
  内存:        需求量 = 0 (初始状态)

时刻 T1:
  Subscriber:  subscription.request(50)
  内存:        需求量 = 50

时刻 T2-T51:
  Publisher:   每生成 1 个元素，需求量 -= 1
               调用 onNext(element)，需求量 = 49, 48, ...

时刻 T51:
  需求量 = 0  → Publisher 停止生产（背压开始生效）

时刻 T52:
  Subscriber:  subscription.request(30)
  内存:        需求量 = 30

时刻 T53-T82:
  Publisher:   继续生产，30 个元素

时刻 T82:
  需求量 = 0  → 再次等待

时刻 T83:
  Subscriber:  subscription.request(Long.MAX_VALUE)
  内存:        需求量 = Long.MAX_VALUE (无限)

时刻 T84+:
  Publisher:   尽快生产所有元素
```

### Step 2: 关键约束

#### 约束 1: 需求递增原则（§3.17）

```java
// ✅ 正确：需求只增不减
subscription.request(50);   // 总需求 = 50
subscription.request(30);   // 总需求 = 80
subscription.request(20);   // 总需求 = 100

// ❌ 错误：尝试减少需求
subscription.request(-10);  // 违反！应该抛异常
```

#### 约束 2: 公平性（§3.16）

```java
// ✅ 正确：Publisher 公平处理请求
flux.request(50);   // T1 时刻请求 50 个
flux.request(30);   // T2 时刻请求 30 个
// Publisher 应该按时序完成 80 个元素

// ❌ 错误：反复请求导致饥饿
while(true) {
    subscriber1.request(1);  // 高频请求，可能导致其他订阅者等待
}
```

#### 约束 3: 过度需求（Over-requesting）

```java
// ⚠️ 注意：可以请求超过现有元素数
Flux.range(1, 10)
    .subscribe(item -> log.info("Item: {}", item),
               onNext -> subscription.request(100));  // 请求 100，但只有 10 个

// 结果：Publisher 生产完 10 个元素后，自动调用 onComplete()
// 不会卡住，但浪费了 90 个的"配额"
```

### Step 3: 背压失效的关键信号

```java
// 背压 WORKING（工作中）
Flux.range(1, 1000000)  // 百万级数据
    .subscribe(
        item -> {
            Thread.sleep(100);  // 缓慢消费（每个 100ms）
            log.info("Item: {}", item);
        },
        onNext -> subscription.request(10)  // 小步长，精确控制
    );
// 结果：内存稳定 ✅

// 背压 FAILED（失效）
Flux.range(1, 1000000)
    .subscribe(
        item -> log.info("Item: {}", item),
        onNext -> subscription.request(Long.MAX_VALUE)  // 无限请求！
    );
// 结果：所有 100 万个元素立即加载到内存 → OOM ❌
```

---

## 4 种背压策略

### 策略 1: BUFFER（缓冲）

**概念**: 生产者超速时，将超出的元素缓冲在内存中，消费者逐个消费。

#### 工作流程

```
生产速度: 10 elem/s (快)
消费速度: 3 elem/s (慢)
缓冲区大小: 100 elem

时刻 T=0:
  已生产: 0
  已消费: 0
  缓冲: 0

时刻 T=1s:
  已生产: 10
  已消费: 3
  缓冲: 7

时刻 T=2s:
  已生产: 20
  已消费: 6
  缓冲: 14

...

时刻 T=10s:
  已生产: 100
  已消费: 30
  缓冲: 70

时刻 T=34s:
  已生产: 340
  已消费: 102
  缓冲: 238 ❌ 超出限制！
```

#### 实现代码

```java
public static void demonstrateBufferStrategy() {
    log.info("=== Buffer Strategy Demo ===");

    Flux.range(1, 1000)
        .onBackpressureBuffer(100)  // 最多缓冲 100 个
        .subscribe(
            item -> {
                try {
                    Thread.sleep(1);  // 缓慢消费
                    log.info("Consumed: {}", item);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            },
            error -> log.error("Error: {}", error.getMessage())
        );
}
```

#### 优缺点

| 优点 | 缺点 |
|------|------|
| 不丢失数据 | 内存占用大 |
| 简单易用 | 缓冲区满时触发 BackpressureOverflowException |
| 适合大多数场景 | 可能导致 OOM |

#### 何时使用

- ✅ 数据不能丢失（订单、日志）
- ✅ 生产消费差异不大
- ✅ 内存充足

### 策略 2: DROP（丢弃）

**概念**: 生产者超速时，消费者无法跟上，丢弃新产生的元素。

#### 工作流程

```
生产速度: 10 elem/s (快)
消费速度: 3 elem/s (慢)
处理策略: DROP 无法消费的元素

时刻 T=0:
  已生产: 0
  已消费: 0
  已丢弃: 0

时刻 T=1s:
  已生产: 10
  已消费: 3
  已丢弃: 7 ⚠️

时刻 T=2s:
  已生产: 20
  已消费: 6
  已丢弃: 14 ⚠️

结果：消费者只看到某些元素，其他被静默丢弃
```

#### 实现代码

```java
public static void demonstrateDropStrategy() {
    log.info("=== Drop Strategy Demo ===");

    Flux.range(1, 1000)
        .onBackpressureDrop(item ->
            log.warn("Dropped: {}", item)  // 丢弃时的回调
        )
        .subscribe(
            item -> {
                try {
                    Thread.sleep(1);
                    log.info("Consumed: {}", item);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            },
            error -> log.error("Error: {}", error.getMessage())
        );
}
```

#### 优缺点

| 优点 | 缺点 |
|------|------|
| 内存占用小 | 数据丢失 ❌ |
| 性能最好 | 不适合金融/订单场景 |
| 适合实时数据 | 难以诊断（哪些数据丢了？） |

#### 何时使用

- ✅ 允许丢失数据（实时监控、日志）
- ✅ 内存受限
- ✅ 需要最新数据（旧数据过时）

### 策略 3: LATEST（最新值）

**概念**: 生产者超速时，保留最新的一个元素，覆盖之前未消费的元素。

#### 工作流程

```
生产: 1, 2, 3, 4, 5, 6, 7, 8, 9, 10
消费能力: 每 10ms 消费 1 个，但生产间隔只有 1ms

缓冲阶段（大小=1）：
  T=1ms:  产 1 → 缓: [1]
  T=2ms:  产 2 → 缓: [2]（覆盖 1）
  T=3ms:  产 3 → 缓: [3]（覆盖 2）
  ...
  T=11ms: 消 1 ← 缓: [10]（只能消费最新的 10）
  ...

最终消费: 10（其他 1-9 被覆盖）
```

#### 实现代码

```java
public static void demonstrateLatestStrategy() {
    log.info("=== Latest Strategy Demo ===");

    Flux.range(1, 100)
        .onBackpressureLatest()  // 仅保留最新元素
        .subscribe(
            item -> {
                try {
                    Thread.sleep(10);  // 缓慢消费
                    log.info("Consumed: {}", item);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            },
            error -> log.error("Error: {}", error.getMessage())
        );
}
```

#### 优缺点

| 优点 | 缺点 |
|------|------|
| 内存占用小（固定 1） | 数据丢失 |
| 总是获取最新值 | 不适合历史数据 |
| 适合实时更新 | 难以诊断 |

#### 何时使用

- ✅ 只关心最新值（实时汇率、温度）
- ✅ 历史数据无关紧要
- ✅ 更新频繁

### 策略 4: ERROR（错误）

**概念**: 生产者无法遵守背压时，直接抛异常，让消费者处理。

#### 工作流程

```
背压请求: request(10)
生产能力: 100 elem/s
消费能力: 10 elem/s

缓冲区: 无
监控: 如果生产 > 请求量，即刻抛异常

T=1ms:  产 10 → OK（满足请求）
T=2ms:  产 11 → ❌ BackpressureOverflowException
```

#### 实现代码

```java
public static void demonstrateErrorStrategy() {
    log.info("=== Error Strategy Demo ===");

    Flux.range(1, 1000)
        .onBackpressureError()  // 超出请求时抛异常
        .subscribe(
            item -> {
                try {
                    Thread.sleep(5);
                    log.info("Consumed: {}", item);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            },
            error -> log.error("Backpressure Error: {}", error.getMessage()),
            () -> log.info("Stream completed")
        );
}
```

#### 优缺点

| 优点 | 缺点 |
|------|------|
| 快速失败，易于检测 | 流中断，不能继续 |
| 零缓冲，最小内存 | 消费者需要处理异常 |
| 清晰的错误信号 | 不适合生产环境 |

#### 何时使用

- ✅ 调试和测试
- ✅ 需要快速失败的场景
- ❌ 生产环境谨慎使用

---

## 背压失效场景

### 失效场景 1: flatMap 无界并发

**问题描述**: `flatMap` 默认并发度无限制，可能导致背压无效。

```java
// ❌ 危险：flatMap 会创建无限个内部流
Flux.range(1, 1000000)
    .flatMap(id ->
        fetchUserData(id)  // 假设这是异步 I/O
            .doOnNext(user -> log.info("User: {}", user))
    )
    .subscribe(
        user -> log.info("Got: {}", user),
        onNext -> subscription.request(100)  // 背压无效！
    );
// 背压在 flatMap 处中断，所有 100 万个请求立即发出 ❌

// ✅ 解决：限制 flatMap 的并发度
Flux.range(1, 1000000)
    .flatMap(
        id -> fetchUserData(id),
        maxConcurrency = 32  // 限制并发数
    )
    .subscribe(
        user -> log.info("Got: {}", user),
        onNext -> subscription.request(100)
    );
// 背压正确传播，内存稳定 ✅
```

### 失效场景 2: publishOn 之后的阻塞操作

**问题描述**: 在 `publishOn` 切换线程后，如果有阻塞操作，背压机制失效。

```java
// ❌ 危险：线程切换 + 阻塞操作
Flux.range(1, 1000)
    .publishOn(Schedulers.boundedElastic())  // 切换线程
    .map(id -> {
        blockingCall(id);  // 阻塞操作！
        return id * 2;
    })
    .subscribe(
        item -> log.info("Item: {}", item),
        onNext -> subscription.request(10)  // 背压无效
    );
// 阻塞线程池被占满 ❌

// ✅ 解决：使用非阻塞替代
Flux.range(1, 1000)
    .publishOn(Schedulers.boundedElastic())
    .flatMap(id ->
        nonBlockingCall(id)  // 异步调用
            .map(result -> result * 2)
    )
    .subscribe(
        item -> log.info("Item: {}", item),
        onNext -> subscription.request(10)
    );
// 背压正确传播 ✅
```

### 失效场景 3: subscribeOn 无线程限制

**问题描述**: `subscribeOn` 使用的线程池无限制时，所有订阅请求同时执行。

```java
// ❌ 危险：无限线程
Flux.range(1, 100000)
    .subscribeOn(Schedulers.newParallel("custom", 999))  // 999 个线程！
    .subscribe(
        item -> log.info("Item: {}", item),
        onNext -> subscription.request(100)
    );
// 背压无效，线程爆炸 ❌

// ✅ 解决：使用有限线程池
Flux.range(1, 100000)
    .subscribeOn(Schedulers.parallel())  // 默认 CPU 核心数
    .subscribe(
        item -> log.info("Item: {}", item),
        onNext -> subscription.request(100)
    );
// 背压正确传播 ✅
```

### 失效场景 4: buffer 缓冲区溢出

**问题描述**: `buffer` 缓冲区无限制时，导致 OOM。

```java
// ❌ 危险：无限缓冲
Flux.range(1, Integer.MAX_VALUE)
    .onBackpressureBuffer()  // 无大小限制！
    .subscribe(
        item -> {
            Thread.sleep(1000);  // 极端缓慢的消费
            log.info("Item: {}", item);
        },
        onNext -> subscription.request(1)
    );
// 缓冲区会累积到 Integer.MAX_VALUE，导致 OOM ❌

// ✅ 解决：指定缓冲区大小
Flux.range(1, Integer.MAX_VALUE)
    .onBackpressureBuffer(
        10000,  // 最多缓冲 10000 个
        BufferOverflowStrategy.DROP_OLDEST  // 缓冲满时丢弃最旧元素
    )
    .subscribe(
        item -> {
            Thread.sleep(1000);
            log.info("Item: {}", item);
        },
        onNext -> subscription.request(1)
    );
// 内存稳定在 10000 元素以内 ✅
```

### 失效场景 5: fromIterable 转换无背压

**问题描述**: `fromIterable` 一次性加载整个集合到内存。

```java
// ❌ 危险：一次性加载所有数据
List<String> hugeList = loadMillionRecords();  // 内存中已有所有数据
Flux.fromIterable(hugeList)
    .subscribe(
        item -> {
            Thread.sleep(1000);  // 缓慢消费
            log.info("Item: {}", item);
        },
        onNext -> subscription.request(1)
    );
// 所有数据已在内存，背压无效 ❌

// ✅ 解决：使用 Flux.generate 或流式加载
Flux.generate(
    () -> 0,
    (state, sink) -> {
        String record = loadRecord(state);  // 按需加载
        if (record != null) {
            sink.next(record);
            return state + 1;
        } else {
            sink.complete();
            return state;
        }
    }
)
.subscribe(
    item -> {
        Thread.sleep(1000);
        log.info("Item: {}", item);
    },
    onNext -> subscription.request(1)
);
// 按需加载，背压有效 ✅
```

---

## limitRate 源码分析

### 什么是 limitRate

`limitRate` 是 Reactor 中最常用的背压优化操作符。它在消费端主动控制请求速率。

### 工作原理

```java
/**
 * limitRate(prefetch, lowTide)
 *
 * prefetch: 预取数量（初始请求大小）
 * lowTide: 低水位（触发补充请求的阈值）
 *
 * 默认: limitRate(256, 256/4=64)
 */
public static void demonstrateLimitRate() {
    Flux.range(1, 10000)
        .limitRate(256, 64)
        // 含义：
        // 1. 初始请求 256 个元素
        // 2. 当消费到 256-64=192 时（即消费了 64 个），补充请求 64 个
        // 3. 保持需求量在 64-256 之间
        .subscribe(item -> {
            log.info("Item: {}", item);
            Thread.sleep(1);
        });
}
```

### 流程可视化

```
│ 时间 │ 已请求 │ 已消费 │ 需求量 │ 动作 │
├──────┼──────┼──────┼──────┼────────┤
│ T=0  │ 256  │ 0    │ 256  │ 初始请求 256 │
│ T=1  │ 256  │ 1    │ 255  │        │
│ ...  │ ...  │ ...  │ ...  │        │
│ T=64 │ 256  │ 64   │ 192  │ 触发补充请求（低水位） │
│ T=65 │ 320  │ 65   │ 255  │ 补充 64 个 │
│ ...  │ ...  │ ...  │ ...  │        │
│ T=128│ 320  │ 128  │ 192  │ 再次触发补充 │
│ T=129│ 384  │ 129  │ 255  │ 补充 64 个 │
```

### 性能参数调优

```java
// 小请求频率高（网络延迟大）
.limitRate(16, 4)    // 小步长，减少网络往返

// 中等频率
.limitRate(256, 64)  // Reactor 默认值，适合大多数场景

// 大流量，消费快
.limitRate(1024, 256)  // 大步长，减少请求频率

// 超高吞吐（内存充足）
.limitRate(4096, 1024)  // 最大化吞吐量
```

---

## 性能影响分析

### 内存占用对比

```
┌────────────────┬──────────┬──────────┬─────────────┐
│ 策略           │ 平均内存 │ 峰值内存 │ 稳定性      │
├────────────────┼──────────┼──────────┼─────────────┤
│ BUFFER(无限)   │ 很高     │ 很高     │ 差（OOM风险）│
│ BUFFER(100k)   │ 100KB    │ 110KB    │ 好          │
│ DROP           │ 低       │ 低       │ 好          │
│ LATEST         │ 最低     │ 最低     │ 优          │
│ ERROR          │ 最低     │ 最低     │ 优          │
└────────────────┴──────────┴──────────┴─────────────┘
```

### 吞吐量对比

```
┌────────────────┬─────────┬────────┐
│ 策略           │ 吞吐量  │ 相对值 │
├────────────────┼─────────┼────────┤
│ BUFFER(无限)   │ 100k/s  │ 100%   │ （未进行背压限制）
│ BUFFER(100k)   │ 98k/s   │ 98%    │
│ DROP           │ 95k/s   │ 95%    │
│ LATEST         │ 92k/s   │ 92%    │ （频繁覆盖）
│ ERROR          │ 90k/s   │ 90%    │ （异常处理开销）
└────────────────┴─────────┴────────┘
```

---

## 背压策略决策树

```
应用场景分析
  │
  ├─ 问：数据能否丢失?
  │   │
  │   ├─ 是（允许丢失）
  │   │   │
  │   │   ├─ 问：需要最新值?
  │   │   │   ├─ 是 → LATEST 策略
  │   │   │   │   使用场景：实时汇率、温度监控
  │   │   │   │   参考代码：.onBackpressureLatest()
  │   │   │   │
  │   │   │   └─ 否 → DROP 策略
  │   │   │       使用场景：日志、非关键指标
  │   │   │       参考代码：.onBackpressureDrop()
  │   │   │
  │   │   └─ 问：需要快速失败（用于测试）?
  │   │       ├─ 是 → ERROR 策略
  │   │       │   使用场景：单元测试、开发调试
  │   │       │   参考代码：.onBackpressureError()
  │   │       │
  │   │       └─ 否 → DROP 策略
  │   │
  │   └─ 否（不能丢失）
  │       │
  │       ├─ 问：内存充足?
  │       │   ├─ 是 → BUFFER 策略（指定大小）
  │       │   │   使用场景：订单处理、金融交易
  │       │   │   参考代码：.onBackpressureBuffer(10000)
  │       │   │
  │       │   └─ 否 → 重新设计架构
  │       │       建议：
  │       │       - 使用外部消息队列（Kafka、RabbitMQ）
  │       │       - 分批处理
  │       │       - 增加消费者数
  │       │
  │       └─ 问：生产消费速度差异大?
  │           ├─ 是（>10 倍） → BUFFER 策略
  │           │   原因：DROP/LATEST 会丢大量数据
  │           │
  │           └─ 否（<10 倍） → BUFFER 或 limitRate
  │               参考代码：.limitRate(256, 64)
  │
  └─ 最终建议：使用 limitRate() 精细控制
      默认：.limitRate(256, 64)
      网络延迟大：.limitRate(16, 4)
      超高吞吐：.limitRate(4096, 1024)
```

---

## 最佳实践

### 最佳实践 1: 始终使用 limitRate

```java
// ❌ 不要这样做：使用默认背压
Flux.range(1, 1000000)
    .subscribe(item -> consume(item));

// ✅ 应该这样做：显式控制
Flux.range(1, 1000000)
    .limitRate(256)  // 或 limitRate(256, 64)
    .subscribe(item -> consume(item));
```

**原因**：
- 防止内存爆炸
- 提高性能（减少中断）
- 明确表达意图

### 最佳实践 2: flatMap 必须限制并发

```java
// ❌ 危险
Flux.range(1, 100000)
    .flatMap(id -> fetchData(id))
    .subscribe(data -> process(data));

// ✅ 安全
Flux.range(1, 100000)
    .flatMap(id -> fetchData(id), maxConcurrency = 32)
    .limitRate(256)
    .subscribe(data -> process(data));
```

### 最佳实践 3: 选择合适的背压策略

```java
// 不同场景的最佳选择

// 场景 A: 实时监控（允许丢失）
Flux.range(1, 1000000)
    .onBackpressureLatest()
    .subscribe(metric -> sendAlert(metric));

// 场景 B: 订单处理（不能丢失）
Flux.range(1, 1000000)
    .onBackpressureBuffer(10000)
    .subscribe(order -> saveOrder(order));

// 场景 C: 日志流（允许丢失，不关心顺序）
Flux.range(1, 1000000)
    .onBackpressureDrop()
    .subscribe(log -> writeLog(log));
```

### 最佳实践 4: 监控背压

```java
Flux.range(1, 1000000)
    .doOnRequest(requested ->
        log.info("Requested: {}", requested)
    )
    .doOnNext(item ->
        log.debug("Processing: {}", item)
    )
    .limitRate(256)
    .subscribe(
        item -> process(item),
        error -> log.error("Backpressure error", error),
        () -> log.info("Stream completed")
    );
```

---

## 常见误区

### 误区 1: 背压是自动的

❌ **错误理解**：
> "Reactor 自动处理背压，我不需要做什么"

✅ **正确理解**：
- 背压是**可能的**，但需要**主动配置**
- 如果不指定 `limitRate`，可能导致内存爆炸
- 必须在数据源处应用背压策略

### 误区 2: 背压只影响内存

❌ **错误理解**：
> "背压只是为了省内存"

✅ **正确理解**：
- 背压影响**吞吐量、延迟、稳定性**
- 合理的背压配置可以提升吞吐量 30%+
- 背压协调了**生产和消费的节奏**

### 误区 3: 选择最"安全"的策略

❌ **错误理解**：
> "我应该使用 BUFFER，因为它最安全"

✅ **正确理解**：
- 没有"最安全"的策略，只有"最合适"的
- BUFFER 可能导致 OOM
- 应该根据**业务需求**选择策略

### 误区 4: limitRate 参数不重要

❌ **错误理解**：
> ".limitRate(256, 64) 默认值适合所有场景"

✅ **正确理解**：
- 参数应该根据网络延迟和消费速度调优
- 网络延迟大：使用小参数（减少请求往返）
- 本地快速消费：使用大参数（减少请求频率）

---

## 总结表

| 维度 | BUFFER | DROP | LATEST | ERROR |
|------|--------|------|--------|-------|
| **数据完整** | ✅ | ❌ | ❌ | ✅ |
| **内存占用** | 高 | 低 | 最低 | 最低 |
| **吞吐量** | 最高 | 高 | 中 | 低 |
| **使用难度** | 低 | 低 | 低 | 低 |
| **生产就绪** | ✅ | ⚠️ | ✅ | ❌ |
| **推荐度** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐ |

---

## 参考资源

- [Reactor 官方文档 - Backpressure](https://projectreactor.io/docs/core/latest/api/)
- [Reactive Streams 规范](https://www.reactive-streams.org/)
- [背压实战指南](../phase-2/BackpressureStrategyDemo.java)

---

**下一步学习**：查看 Phase 2 演示代码和失效场景复现

