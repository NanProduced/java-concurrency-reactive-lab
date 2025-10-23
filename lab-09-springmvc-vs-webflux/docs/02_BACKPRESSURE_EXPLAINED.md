# Layer 0: 背压 (Backpressure) - 响应式编程的秘密武器

> **目标**: 理解什么是背压，为什么重要，以及在 Lab-09 中如何处理
> **前置知识**: 看完 `01_FLUX_MONO_FUNDAMENTALS.md`
> **难度**: ⭐⭐ (简单)
> **阅读时间**: 8-10 分钟

---

## 问题：生产者 > 消费者怎么办？

### 场景：流失控

```
假设你有一个无限流：

Flux.generate(sink -> {
    sink.next(System.nanoTime());  // 生产数据（非常快！）
})
.subscribe(timestamp -> {
    Thread.sleep(1000);  // 消费数据（很慢！）
    System.out.println(timestamp);
});

发生了什么？
┌─────────────────────────────────────────┐
│ 时刻 0-100ms:                           │
│ 生产: 生成了 100 万个 nanoTime 值       │
│ 消费: 还在处理第一个                    │
│                                         │
│ 时刻 100ms:                             │
│ 内存中有 100 万个值在等待处理           │
│                                         │
│ 时刻 1秒:                               │
│ OOM (Out of Memory)！                   │
│ ❌ 程序崩溃                             │
└─────────────────────────────────────────┘
```

### 为什么会这样？

```
生产速度 >> 消费速度

生产速度可能达到: 百万/秒
消费速度可能仅有: 百/秒

中间的数据需要缓存，最终导致内存溢出
```

---

## 解决方案：背压 (Backpressure)

### 什么是背压？

```
背压 = 消费者告诉生产者"我处理不了这么快，请慢点"

现实例子：
  ├─ 水管: 当下游堵了，会影响上游的流量
  ├─ 交通: 前面堵了，后面的车也会堵，甚至停止
  └─ 呼吸: 吸气快 → 肺胀 → 自动减速吸气速度

响应式编程中的背压:
  消费者: "我只能每秒处理 100 个数据"
  生产者: "好的，我会每秒只生产 100 个"
```

### 背压的执行流程

```
无背压 (危险):
  生产者 ────────> [缓存] ────────> 消费者
    1M/s      堆积越来越多      100/s
                   ▲
                  OOM!

有背压 (安全):
  生产者 <──背压信号── [缓存] ────────> 消费者
    100/s    "我只能处理100个"  100/s
                   ▲
               达到平衡
```

---

## Project Reactor 中的背压

### 背压是内置的！

```java
// ✅ 好消息：Project Reactor 自动处理背压
// 你不需要显式编写背压逻辑

Flux.range(1, Integer.MAX_VALUE)  // 可能是数十亿个数
    .delayElement(Duration.ofMillis(100))  // 每个延迟 100ms
    .subscribe(num -> System.out.println(num));

// Reactor 会自动：
// 1. 检测消费者的处理速度
// 2. 调整生产速度以匹配
// 3. 避免内存溢出
```

### 背压的三个策略

#### 策略 1: Buffer (缓冲)

```java
// 当消费速度慢时，缓冲一些数据
Flux.range(1, 1_000_000)
    .buffer(100)  // 每 100 个分组缓冲一次
    .subscribe(batch -> {
        batch.forEach(num -> System.out.println(num));
        Thread.sleep(100);  // 每批处理慢一点
    });

优点: 简单
缺点: 如果缓冲区满了，仍然会阻塞或报错
```

#### 策略 2: Drop (丢弃)

```java
// 当缓冲满了，丢弃新数据
Flux.range(1, 1_000_000)
    .onBackpressureDrop()  // 超过缓冲时丢弃
    .subscribe(num -> {
        System.out.println(num);
        Thread.sleep(10);
    });

优点: 永远不会 OOM
缺点: 会丢失数据，可能无法接受
```

#### 策略 3: Latest (最新)

```java
// 只保留最新的数据，丢弃过期的
Flux.range(1, 1_000_000)
    .onBackpressureLatest()  // 超过缓冲时，丢弃旧数据，保留新数据
    .subscribe(num -> {
        System.out.println(num);
        Thread.sleep(10);
    });

优点: 保证处理最新的数据
缺点: 中间的数据会丢失
```

#### 策略 4: Error (错误)

```java
// 背压无法处理时，返回错误
Flux.range(1, 1_000_000)
    .onBackpressureError()  // 超过缓冲时，报 IllegalStateException
    .subscribe(
        num -> {
            System.out.println(num);
            Thread.sleep(10);
        },
        error -> System.err.println("背压失败: " + error)
    );

优点: 及时发现问题
缺点: 应用可能崩溃
```

---

## 实战：背压演示

### 演示 1: 无限生产 + 背压

```java
public static void main(String[] args) {
    // 无限生产时间戳
    Flux.generate(sink -> {
        sink.next(System.nanoTime());
    })
    .take(10)  // 只取前 10 个（这就是背压！）
    .subscribe(timestamp -> {
        System.out.println("接收: " + timestamp);
        try { Thread.sleep(100); } catch (Exception e) {}
    });

    // 执行特点：
    // ✅ 仅生产 10 个值
    // ✅ 不会 OOM
    // ✅ 消费速度控制了生产速度
    // ⚡ 这就是背压！
}
```

### 演示 2: 数据库流 + 背压

```java
public Flux<User> getUsers() {
    return userRepository.findAll()  // 可能有数百万用户
        .buffer(1000)  // 每次处理 1000 个用户
        .map(batch -> {
            // 处理这一批 1000 个用户
            return batch;
        });

    // 数据库也会感受到背压：
    // "消费者说我只能处理 1000 个，所以我分批发送"
}
```

### 演示 3: HTTP 响应 + 背压

```java
@GetMapping("/stream/users")
public Flux<User> streamUsers() {
    return userRepository.findAll()
        .delayElement(Duration.ofMillis(10));  // 每个用户延迟 10ms

    // HTTP 客户端会自动应用背压：
    // "浏览器只能这么快接收数据，Reactor 会调整速度"
}
```

---

## 常见的背压场景

### 场景 1: 数据库分页（自动背压）

```java
// ✅ 数据库驱动自动处理背压
Flux<User> users = userRepository.findAll();  // R2DBC

// 即使有数百万用户，也不会 OOM
// 因为 R2DBC 分页返回数据
```

### 场景 2: WebSocket 连接（自动背压）

```java
@PostMapping("/subscribe")
public Flux<Event> subscribe() {
    return eventPublisher.events()
        .doOnCancel(() -> log.info("客户端断开连接"));

    // WebSocket 连接会自动应用背压：
    // 网络速度慢 → Reactor 检测到 → 放慢生产速度
}
```

### 场景 3: Kafka 消费（显式背压）

```java
public Flux<Message> consumeMessages() {
    return kafkaTemplate.receive(topic)
        .buffer(100)  // 每次处理 100 条消息
        .flatMap(batch -> processBatch(batch))
        .onBackpressureDrop()  // 处理不过来就丢弃
        .doOnError(error -> log.error("消费失败", error));
}
```

---

## 背压 vs 非背压的对比

### 没有背压（传统异步）

```
CompletableFuture<List<User>> future = CompletableFuture.supplyAsync(() -> {
    return database.queryAllUsers();  // 一次性读取所有用户到内存
});

// 如果有数百万用户，会一次性加载到内存
// 结果: 内存溢出
```

### 有背压（响应式流）

```
Flux<User> flux = userRepository.findAll();  // Flux 流
flux.subscribe(user -> {
    process(user);  // 每个用户单独处理
});

// 数据一个一个来，处理一个释放一个
// 结果: 内存恒定
```

---

## 背压的核心法则

```
┌────────────────────────────────────────────┐
│ 背压法则                                    │
│                                            │
│ 如果不做任何处理：                        │
│   消费速度 < 生产速度 → OOM                 │
│                                            │
│ 使用背压后：                               │
│   生产速度 = 消费速度 = 吞吐量             │
│                                            │
│ 不会丢失数据，也不会 OOM                   │
│ 系统自动达到平衡                          │
└────────────────────────────────────────────┘
```

---

## 在 Lab-09 中的应用

### Phase 1: 理解基础
- ✅ Mono/Flux 是否订阅
- ✅ 结果如何到达 (onNext/onError/onComplete)

### Phase 2: 学习背压
- ✅ buffer() 缓冲策略
- ✅ take() 限制元素数
- ✅ onBackpressureDrop/Latest/Error

### Phase 3: 生产集成
- ✅ R2DBC 自动背压
- ✅ Redis 连接池背压
- ✅ Kafka 显式背压策略

### Phase 4: 性能对标
- ✅ 背压效果的量化测量
- ✅ 不同背压策略的性能差异
- ✅ 何时使用哪种背压

---

## 总结

```
背压 = 响应式编程解决高并发的秘密

理由:
  ✅ 自动检测消费速度
  ✅ 自动调整生产速度
  ✅ 避免内存溢出
  ✅ 系统自动达到平衡

在 Project Reactor 中:
  ✅ 大部分情况自动处理
  ✅ 需要时可显式配置 (buffer/take/onBackpressure)
  ✅ 是 WebFlux 高效的核心原因
```

