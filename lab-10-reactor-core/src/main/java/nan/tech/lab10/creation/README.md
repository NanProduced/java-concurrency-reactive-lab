# Phase 1: 创建操作符演示

## 📌 概述

本目录包含 Lab-10 Phase 1 的创建操作符（Creation Operators）演示，重点是：
1. 深度理解 Mono 和 Flux 的本质
2. 掌握各种创建操作符的用法和区别
3. 理解冷流（Cold Stream）的概念
4. 区分不同创建方式的触发时机

## 📂 文件结构

```
creation/
├── MonoCreationDemo.java         # Mono 创建操作符演示 (10个 demo)
├── FluxCreationDemo.java          # Flux 创建操作符演示 (12个 demo)
└── README.md                      # 本文件
```

## 🎯 核心概念

### Mono 和 Flux 对比

```
┌──────────────────────────────────────────────────────────┐
│                    Publisher<T>                          │
│   (Reactive Streams 规范中的发布者)                      │
└──────────────────────────────────────────────┬───────────┘
       ▲
       │ 继承
       │
┌──────┴───────────────────────────────────────────────────┐
│                                                            │
│   ┌─────────────────┐      ┌─────────────────┐           │
│   │   Mono<T>      │      │   Flux<T>      │           │
│   ├─────────────────┤      ├─────────────────┤           │
│   │ 最多 1 个元素    │      │ 0 到 N 个元素    │           │
│   │ (0 或 1)        │      │ (0..∞)          │           │
│   │                 │      │                 │           │
│   │ 使用场景:       │      │ 使用场景:       │           │
│   │ - 单一结果      │      │ - 数据流        │           │
│   │ - 可选值        │      │ - 批量数据      │           │
│   │ - 异步操作      │      │ - 事件流        │           │
│   │ - 异常处理      │      │ - 时间序列      │           │
│   │                 │      │                 │           │
│   └─────────────────┘      └─────────────────┘           │
│                                                            │
└────────────────────────────────────────────────────────────┘
```

### Mono 创建方式

| 创建方式 | 触发时机 | 流类型 | 典型用途 |
|--------|--------|------|--------|
| `Mono.just(T)` | 立即 | 冷流 | 包装已知值 |
| `Mono.empty()` | 立即 | 冷流 | 创建空 Mono |
| `Mono.error()` | 立即 | 冷流 | 创建错误 Mono |
| `Mono.fromCallable()` | 订阅时 | 冷流 | 包装 Callable |
| `Mono.defer()` | 订阅时 | 冷流 | 延迟创建 |
| `Mono.justOrEmpty()` | 立即 | 冷流 | 包装 Optional |
| `Mono.fromSupplier()` | 订阅时 | 冷流 | 包装 Supplier |
| `Mono.delay()` | 延迟 | 冷流 | 延迟发送 |
| `Mono.create()` | 订阅时 | 冷流 | 手动控制 |
| `Mono.never()` | 无 | 冷流 | 永不完成 |

### Flux 创建方式

| 创建方式 | 触发时机 | 流类型 | 典型用途 |
|--------|--------|------|--------|
| `Flux.just()` | 立即 | 冷流 | 包装多个值 |
| `Flux.fromIterable()` | 订阅时 | 冷流 | 从集合创建 |
| `Flux.fromArray()` | 订阅时 | 冷流 | 从数组创建 |
| `Flux.range()` | 订阅时 | 冷流 | 整数范围 |
| `Flux.empty()` | 立即 | 冷流 | 创建空 Flux |
| `Flux.error()` | 立即 | 冷流 | 创建错误 Flux |
| `Flux.never()` | 无 | 冷流 | 永不完成 |
| `Flux.interval()` | 定时 | 冷流 | 时间间隔 |
| `Flux.create()` | 订阅时 | 冷流 | 手动控制 |
| `Flux.generate()` | 订阅时 | 冷流 | 状态机生成 |
| `Flux.fromStream()` | 订阅时 | 冷流 | 从 Stream 创建 |
| `Flux.using()` | 订阅时 | 冷流 | 资源管理 |

## 🌊 冷流 vs 热流

### 冷流（Cold Stream）

**定义**：每次订阅时都会重新创建和执行

```java
// 示例：冷流
Mono<Integer> coldMono = Mono.fromCallable(() -> {
    System.out.println("Callable 被执行");  // 每次订阅都会打印
    return 42;
});

coldMono.subscribe(System.out::println);  // 打印"Callable 被执行"
coldMono.subscribe(System.out::println);  // 再次打印"Callable 被执行"
```

**特点**：
- ✅ 每个订阅者都能获得完整的流
- ✅ 避免错过元素（如果有多个订阅者）
- ❌ 重复执行逻辑，浪费资源
- ❌ 大量订阅者时性能较差

**适用场景**：
- 数据库查询（每次订阅查询一次）
- API 调用（每次订阅调用一次）
- 异步操作（每次订阅执行一次）

### 热流（Hot Stream）

**定义**：独立于订阅者存在，所有订阅者共享同一个流

```java
// 示例：热流（使用 share()）
Mono<Integer> hotMono = Mono.fromCallable(() -> {
    System.out.println("Callable 被执行");  // 只执行一次
    return 42;
}).share();  // 转为热流

hotMono.subscribe(System.out::println);  // 打印"Callable 被执行"
hotMono.subscribe(System.out::println);  // 不会再打印（共享同一个流）
```

**特点**：
- ✅ 避免重复执行，节省资源
- ✅ 所有订阅者共享同一个流
- ❌ 晚到的订阅者会错过之前的元素
- ❌ 需要显式转换（`.share()`, `.replay()` 等）

**适用场景**：
- 实时事件流（股票价格、传感器数据）
- 共享数据源（多个消费者消费同一份数据）
- 性能关键场景（避免重复计算）

## 📖 演示内容

### MonoCreationDemo.java - 10 个演示

#### 1. Mono.just() - 包装已知值
```java
Mono<String> mono = Mono.just("Hello");
// 立即可用，每次订阅都发送相同的值
```

#### 2. Mono.empty() - 创建空 Mono
```java
Mono<String> mono = Mono.empty();
// 0 个元素，立即调用 onComplete()
```

#### 3. Mono.error() - 创建错误 Mono
```java
Mono<String> mono = Mono.error(new Exception("Error"));
// 立即触发 onError()
```

#### 4. Mono.fromCallable() - 延迟执行
```java
Mono<Integer> mono = Mono.fromCallable(() -> {
    // 订阅时才执行
    return expensiveOperation();
});
```

#### 5. Mono.defer() - 延迟创建
```java
Mono<Integer> mono = Mono.defer(() -> {
    // 每次订阅都创建新的 Mono
    return Mono.just(counter++);
});
```

#### 6. Mono.justOrEmpty() - 包装 Optional
```java
Optional<String> opt = Optional.of("value");
Mono<String> mono = Mono.justOrEmpty(opt);
// 有值：发送元素，无值：empty
```

#### 7. Mono.delay() - 延迟发送
```java
Mono<Long> mono = Mono.delay(Duration.ofSeconds(1));
// 等待 1 秒后发送元素
```

#### 8. 冷流 vs 热流
```java
// 冷流：每次订阅都执行（演示多次调用）
Mono<Integer> coldMono = Mono.fromCallable(() -> ++counter);

// 热流：只执行一次（使用 share()）
Mono<Integer> hotMono = coldMono.share();
```

#### 9. Mono.create() - 手动控制
```java
Mono<String> mono = Mono.create(sink -> {
    sink.success("value");  // 或 sink.error()
});
```

#### 10. Null Safety - 安全处理 null
```java
// 错误：Mono.just(null) 抛异常
// 正确：Mono.justOrEmpty(null) 返回 empty
```

### FluxCreationDemo.java - 12 个演示

#### 1. Flux.just() - 包装多个值
```java
Flux<Integer> flux = Flux.just(1, 2, 3, 4, 5);
```

#### 2. Flux.fromIterable() - 从集合创建
```java
List<String> list = Arrays.asList("a", "b", "c");
Flux<String> flux = Flux.fromIterable(list);
```

#### 3. Flux.fromArray() - 从数组创建
```java
Integer[] array = {1, 2, 3};
Flux<Integer> flux = Flux.fromArray(array);
```

#### 4. Flux.range() - 整数范围
```java
Flux<Integer> flux = Flux.range(5, 10);  // 5-14
```

#### 5. Flux.empty() 和 Flux.error()
```java
Flux<String> empty = Flux.empty();
Flux<String> error = Flux.error(new Exception());
```

#### 6. Flux.interval() - 时间间隔
```java
Flux<Long> flux = Flux.interval(Duration.ofSeconds(1));
// 每秒发送 0, 1, 2, ...（永不完成）
```

#### 7. Flux.create() - 手动控制
```java
Flux<Integer> flux = Flux.create(sink -> {
    for (int i = 1; i <= 5; i++) {
        sink.next(i * 10);
    }
    sink.complete();
});
```

#### 8. Flux.generate() - 状态机生成
```java
Flux<Integer> flux = Flux.generate(
    () -> 0,  // 初始状态
    (state, sink) -> {
        sink.next(state);
        return state + 1;  // 返回新状态
    }
);
```

#### 9. Flux.fromIterable() 等效演示
```java
// 使用 fromIterable 处理集合
var list = Arrays.asList(100, 200, 300);
Flux<Integer> flux = Flux.fromIterable(list);
```

#### 10. Flux.never() - 永不完成
```java
Flux<String> flux = Flux.never();
// 什么都不做，永远等待
```

#### 11. Flux vs Mono 对比
```java
// Flux：0-N 个元素
Flux.just(1, 2, 3).subscribe(...);

// Mono：最多 1 个元素
Mono.just(42).subscribe(...);

// Mono.empty()：0 个元素
Mono.empty().subscribe(...);
```

#### 12. 冷流特性 - 多次订阅
```java
Flux<Integer> flux = Flux.create(sink -> {
    System.out.println("create consumer 被执行");
    sink.next(100);
    sink.complete();
});

flux.subscribe(...);  // "create consumer 被执行"
flux.subscribe(...);  // 再次"create consumer 被执行"
```

## 🧪 运行演示

### 运行 MonoCreationDemo
```bash
mvn compile exec:java \
  -Dexec.mainClass="nan.tech.lab10.creation.MonoCreationDemo" \
  -pl lab-10-reactor-core
```

### 运行 FluxCreationDemo
```bash
mvn compile exec:java \
  -Dexec.mainClass="nan.tech.lab10.creation.FluxCreationDemo" \
  -pl lab-10-reactor-core
```

## 📚 学习资源

### 官方文档
- [Project Reactor 文档](https://projectreactor.io/docs)
- [Reactor JavaDoc](https://projectreactor.io/docs/core/release/api/)

### 相关概念
- Reactive Streams 规范
- Publisher/Subscriber 模式
- 背压协议（Backpressure）
- 线程调度和并发

## 💡 最佳实践

### 1. 选择正确的创建方式
```java
// ✅ 已知值：使用 just()
Mono.just(value)

// ✅ 可选值：使用 justOrEmpty()
Mono.justOrEmpty(optional)

// ✅ 延迟计算：使用 fromCallable()
Mono.fromCallable(() -> expensiveOperation())

// ✅ 动态创建：使用 defer()
Mono.defer(() -> computeNextMono())

// ✅ 手动控制：使用 create()
Mono.create(sink -> { ... })
```

### 2. 避免常见错误
```java
// ❌ 错误：Mono.just(null) - 抛异常
Mono<String> mono = Mono.just(null);

// ✅ 正确：Mono.justOrEmpty(null) - 返回 empty
Mono<String> mono = Mono.justOrEmpty(null);

// ❌ 错误：在 subscribe 外修改状态（线程不安全）
Mono.just(list).subscribe(v -> list.add(v));

// ✅ 正确：在 Mono 内部操作
Mono.just(list)
    .map(l -> { l.add(item); return l; })
    .subscribe();
```

### 3. 理解冷流特性
```java
// 冷流特性：每次订阅都重新执行
Mono<Integer> mono = Mono.fromCallable(() -> {
    System.out.println("执行了");
    return getValue();
});

mono.subscribe();  // 打印"执行了"
mono.subscribe();  // 再打印"执行了"
mono.subscribe();  // 又打印"执行了"

// 如果不想重复执行，使用 cache() 或 share()
Mono<Integer> cached = mono.cache();
cached.subscribe();  // 打印"执行了"
cached.subscribe();  // 不打印（使用缓存值）
```

### 4. 异步操作处理
```java
// ✅ 使用 Mono.fromCallable() 包装同步操作
Mono<String> result = Mono.fromCallable(() ->
    databaseQuery()
);

// ✅ 使用 Mono.delay() 处理延迟
Mono<Void> delayed = Mono.delay(Duration.ofSeconds(1))
    .then();

// ✅ 使用 flatMap 处理异步链式调用
Mono<User> user = getUserId(1)
    .flatMap(id -> getUserName(id))
    .flatMap(name -> getUserEmail(name));
```

## 📊 性能考虑

### 冷流性能问题
```
场景：多个订阅者订阅同一个 Mono

冷流（无 share()）：
  Subscriber 1 → execute logic → emit value
  Subscriber 2 → execute logic → emit value  (重复执行！)
  Subscriber 3 → execute logic → emit value  (重复执行！)

  总执行次数：N（N = 订阅者数）

热流（使用 share()）：
  execute logic → emit value → [Subscriber 1, 2, 3]

  总执行次数：1
```

### 建议
- 少量订阅者（< 5）：使用冷流，避免额外开销
- 多个订阅者（> 5）或昂贵操作：使用 `.cache()` 或 `.share()`
- 实时数据流：使用热流（`.share()`, `.replay()` 等）

## 🔧 故障排查

### 问题：元素没有被发送

**可能原因**：
1. 忘记调用 `subscribe()`
2. 使用了 `Mono.never()` 或 `Flux.never()`
3. 流使用了 `filter()` 导致所有元素被过滤

**解决方案**：
```java
// 确保调用 subscribe()
mono.subscribe(
    value -> logger.info("received: {}", value),
    error -> logger.error("error", error),
    () -> logger.info("completed")
);

// 检查 filter() 条件
flux.filter(x -> x > 0).subscribe(...);
```

### 问题：Mono.just(null) 抛异常

**原因**：不能包装 null 值

**解决方案**：
```java
// 使用 justOrEmpty()
Mono.justOrEmpty(null);          // ✅
Mono.justOrEmpty(Optional.empty());  // ✅
```

### 问题：流永不完成

**可能原因**：
1. 使用了 `Flux.never()` 或 `Mono.never()`
2. `Flux.interval()` 永不自动完成
3. 无穷循环在 `Flux.generate()` 或 `Flux.create()` 中

**解决方案**：
```java
// Flux.interval() 需要使用 take() 限制
Flux.interval(Duration.ofSeconds(1))
    .take(10)  // 只发送 10 个元素
    .subscribe(...);

// Flux.generate() 中调用 sink.complete()
Flux.generate(
    () -> 0,
    (state, sink) -> {
        if (state >= 10) {
            sink.complete();  // 显式完成
        }
        sink.next(state);
        return state + 1;
    }
);
```

## 📋 自检清单

学完本模块后，检查以下要点：

- [ ] 理解 Mono 和 Flux 的本质和区别
- [ ] 掌握 10+ 种 Mono 创建方式
- [ ] 掌握 10+ 种 Flux 创建方式
- [ ] 理解冷流的概念和特性
- [ ] 知道何时使用 `.cache()` 或 `.share()`
- [ ] 理解 "trigger time"（何时执行逻辑）
- [ ] 能够避免常见错误（null 值、忘记 subscribe 等）
- [ ] 了解性能考虑（冷流 vs 热流）

## 📝 总结

**创建操作符的核心**：
1. **多种选择**：不同的创建方式适合不同场景
2. **冷流特性**：每次订阅都重新执行，避免共享状态问题
3. **触发时机**：有些立即执行，有些订阅时执行，有些延迟执行
4. **null 安全**：使用 `justOrEmpty()` 处理可能的 null 值
5. **性能优化**：多订阅者场景使用 `.cache()` 或 `.share()`

## 🎓 下一步

学完创建操作符后，继续学习：
- **Phase 1B**: 基础操作符（map, flatMap, filter, reduce 等）
- **Phase 1C**: StepVerifier 测试框架
- **Phase 2**: 背压与流量控制
- **Phase 3**: 调度器与线程模型
