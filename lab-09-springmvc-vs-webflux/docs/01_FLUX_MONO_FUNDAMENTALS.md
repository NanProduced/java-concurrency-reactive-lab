# Layer 0: Flux 与 Mono - 响应式编程的两个基石

> **目标**: 理解 Flux 和 Mono 的本质，为 Lab-09 的所有示例打基础
> **前置知识**: 看完 `00_REACTOR_VS_REACTIVE_CONCEPTS.md`
> **难度**: ⭐⭐ (简单)
> **阅读时间**: 10-15 分钟

---

## 核心概念（必须记住）

```
┌────────────────────────────────────────────────────┐
│ Mono<T> 和 Flux<T> 都是"异步结果的容器"          │
│                                                    │
│ Mono<T>: 0 个或 1 个元素                          │
│   用于: 单个结果 (用户查询、API 调用结果)         │
│   例子: Mono<User>, Mono<String>                  │
│                                                    │
│ Flux<T>: 0 个、多个、或无限多个元素               │
│   用于: 多个结果 (数据列表、事件流、SSE)         │
│   例子: Flux<Post>, Flux<Message>                │
└────────────────────────────────────────────────────┘
```

---

## Mono：单个异步结果

### 什么是 Mono?

```java
// ✅ Mono = "我现在去查数据库，稍后给你一个用户对象"

Mono<User> userMono = userRepository.findById(1);

// 此时：
// - 数据库查询还没有开始！
// - userMono 只是定义，还没有执行

// 只有订阅时才会执行
userMono.subscribe(user -> System.out.println(user));
//       ^
//    这一刻，查询才真正开始
```

### Mono 的生命周期

```
时刻 1: 定义          时刻 2: 订阅         时刻 3: 执行        时刻 4: 完成
  ↓                    ↓                    ↓                    ↓
Mono<User> mono    mono.subscribe(    数据库查询开始    获得结果 User
= userRepository.    user -> {...}      │                │
  findById(1);                          │ (等待)         │
                                        │                onNext(user)
                                        └─ (异步) ───→ onComplete()

关键点：
  ❌ 定义 Mono 时不执行
  ✅ 订阅时才执行
  ✅ 执行是异步的
```

### Mono 的三种结果

```java
// 结果 1: 成功 (onNext + onComplete)
Mono.just("Hello")
    .subscribe(
        value -> System.out.println("值: " + value),    // onNext
        error -> System.err.println("错误: " + error),   // onError
        () -> System.out.println("完成")                // onComplete
    );

// 结果 2: 错误 (onError)
Mono.error(new RuntimeException("出错"))
    .subscribe(
        value -> System.out.println("值: " + value),    // ❌ 不会执行
        error -> System.err.println("捕获错误: " + error), // ✅ 执行
        () -> System.out.println("完成")                 // ❌ 不会执行
    );

// 结果 3: 空 (onComplete，无 onNext)
Mono.empty()
    .subscribe(
        value -> System.out.println("值: " + value),    // ❌ 不会执行
        error -> System.err.println("错误: " + error),   // ❌ 不会执行
        () -> System.out.println("完成")                 // ✅ 执行
    );
```

### 常见的 Mono 创建方式

```java
// 1. 从值创建
Mono.just("Hello");

// 2. 从 Callable 创建 (延迟执行)
Mono.fromCallable(() -> {
    System.out.println("执行业务逻辑");
    return "结果";
});

// 3. 从 CompletableFuture 创建
CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> "异步结果");
Mono.fromFuture(future);

// 4. 异步 API 创建 (最常见)
userRepository.findById(1);  // 返回 Mono<User>

// 5. 手工创建 (高级，不建议初学者用)
Mono.create(sink -> {
    // 后台线程调用 sink.success(value) 或 sink.error(ex)
});
```

---

## Flux：多个异步结果的流

### 什么是 Flux?

```java
// ✅ Flux = "我现在去查数据库，给你一个用户列表（可能是多个用户）"

Flux<User> userFlux = userRepository.findAll();

// 此时：
// - 数据库查询还没有开始！
// - userFlux 只是定义一个"流"，还没有执行

// 只有订阅时才会执行
userFlux.subscribe(
    user -> System.out.println("接收用户: " + user),  // 每收到一个用户就调用一次
    error -> System.err.println("错误: " + error),
    () -> System.out.println("所有用户接收完毕")
);
//     ^
//  这一刻，查询才真正开始，并开始流式返回结果
```

### Flux 的生命周期

```
时刻 1: 定义                时刻 2: 订阅              时刻 3: 流式执行
  ↓                          ↓                          ↓
Flux<User> flux         flux.subscribe(           数据库开始扫描
= userRepository.        user -> {...}               │
  findAll();                                        onNext(User-1)
                                                    onNext(User-2)
                                                    onNext(User-3)
                                                    ...
                                                    onNext(User-N)
                                                    onComplete()

关键点：
  ❌ 定义 Flux 时不执行
  ✅ 订阅时才执行
  ✅ 执行是异步的
  ✅ 结果分次返回（流式）
```

### Flux 的几种情形

```java
// 情形 1: 普通列表数据
Flux.fromIterable(Arrays.asList("A", "B", "C"))
    .subscribe(
        item -> System.out.println("项: " + item),  // 调用 3 次
        error -> System.err.println("错误"),        // ❌ 不会调用
        () -> System.out.println("完成")             // ✅ 调用 1 次
    );

// 情形 2: 无限流
Flux.interval(Duration.ofSeconds(1))  // 每秒发射一个数字
    .subscribe(
        num -> System.out.println("数字: " + num),   // 无限调用
        error -> System.err.println("错误"),
        () -> System.out.println("完成")             // ❌ 永不会到达
    );

// 情形 3: 错误情形
Flux.error(new RuntimeException("查询失败"))
    .subscribe(
        item -> System.out.println("项: " + item),   // ❌ 不会调用
        error -> System.err.println("捕获: " + error), // ✅ 调用
        () -> System.out.println("完成")              // ❌ 不会调用
    );

// 情形 4: 空流
Flux.empty()
    .subscribe(
        item -> System.out.println("项: " + item),   // ❌ 不会调用
        error -> System.err.println("错误"),         // ❌ 不会调用
        () -> System.out.println("完成")              // ✅ 调用
    );
```

### 常见的 Flux 创建方式

```java
// 1. 从列表创建
Flux.fromIterable(users);

// 2. 从多个值创建
Flux.just("A", "B", "C");

// 3. 从数组创建
Flux.fromArray(new String[]{"X", "Y", "Z"});

// 4. 从数据库查询创建 (最常见)
userRepository.findAll();  // 返回 Flux<User>

// 5. 按范围生成
Flux.range(1, 5);  // 生成 1, 2, 3, 4, 5

// 6. 按间隔生成 (无限)
Flux.interval(Duration.ofSeconds(1));

// 7. 手工创建 (高级)
Flux.create(sink -> {
    sink.next("第一项");
    sink.next("第二项");
    sink.complete();
});
```

---

## Mono vs Flux 快速对比

| 对比项 | Mono<T> | Flux<T> |
|--------|---------|---------|
| **可能的元素数** | 0 或 1 | 0、1、多个、无限 |
| **用途** | 单个结果 | 多个结果或流 |
| **常见例子** | 用户查询、API 调用 | 列表、事件流、SSE |
| **subscribe() 参数** | (onNext, onError, onComplete) | (onNext, onError, onComplete) |
| **onNext() 调用次数** | 最多 1 次 | 0+ 次 |
| **数据库例子** | SELECT * FROM user WHERE id=1 | SELECT * FROM users |
| **网络例子** | GET /users/1 返回 1 个用户 | GET /users 返回列表 |
| **时间序列例子** | 一次性获得结果 | 持续推送事件 |

---

## 订阅的三种方式

### 方式 1: 三参数订阅 (最完整)

```java
Mono.just("Hello")
    .subscribe(
        value -> System.out.println("值: " + value),
        error -> System.err.println("错误: " + error),
        () -> System.out.println("完成")
    );

// 输出：
// 值: Hello
// 完成
```

### 方式 2: 两参数订阅 (忽略完成)

```java
Mono.just("Hello")
    .subscribe(
        value -> System.out.println("值: " + value),
        error -> System.err.println("错误: " + error)
    );

// 输出：
// 值: Hello
```

### 方式 3: 一参数订阅 (只处理值)

```java
Mono.just("Hello")
    .subscribe(value -> System.out.println("值: " + value));

// 输出：
// 值: Hello

// ⚠️ 警告: 错误无法捕获！如果有错误会被吞掉！
Mono.error(new RuntimeException("错误"))
    .subscribe(value -> System.out.println("值: " + value));
// 输出: 什么都没有 (错误被吞掉，很危险！)
```

---

## 常见的坑

### 坑 1: 定义后没有订阅

```java
// ❌ 错误
Mono<User> userMono = userRepository.findById(1);
// 此时数据库查询没有执行！

// ✅ 正确
Mono<User> userMono = userRepository.findById(1);
userMono.subscribe(user -> System.out.println(user));
//       ^^^^^^^^^ 必须调用 subscribe()
```

### 坑 2: 忘记处理错误

```java
// ❌ 错误（错误会被吞掉）
userRepository.findById(1)
    .subscribe(user -> System.out.println(user));

// ✅ 正确（显式处理错误）
userRepository.findById(1)
    .subscribe(
        user -> System.out.println(user),
        error -> System.err.println("查询失败: " + error)
    );
```

### 坑 3: 混淆 Mono 和值

```java
// ❌ 错误
Mono<User> userMono = userRepository.findById(1);
System.out.println(userMono.get());  // ❌ Mono 没有 get() 方法！

// ✅ 正确（方式 1: 订阅）
Mono<User> userMono = userRepository.findById(1);
userMono.subscribe(user -> System.out.println(user));

// ✅ 正确（方式 2: 链式调用）
userRepository.findById(1)
    .map(user -> user.getName())  // 在 Mono 上进行变换
    .subscribe(name -> System.out.println(name));

// ✅ 正确（方式 3: 阻塞获取，仅用于测试）
User user = userRepository.findById(1).block();  // ⚠️ 会阻塞当前线程！
System.out.println(user);
```

### 坑 4: 假设顺序执行

```java
// ❌ 错误的思路
Mono<User> user = userRepository.findById(1);
System.out.println("用户: " + user);  // 这时候还没有查询结果！

// ✅ 正确的思路
userRepository.findById(1)
    .subscribe(user -> System.out.println("用户: " + user));
```

---

## 执行图解

### Mono 执行时间线

```
main 线程                数据库线程                  反应式框架线程
   │                      │                           │
   ├─ 定义 Mono           │                           │
   │  mono = userRepository.findById(1)
   │                      │                           │
   ├─ subscribe()         │                           │
   │                      │                           │
   │                      ├─ 异步查询开始              │
   │                      │                           │
   │  (main 线程继续)      │                           │
   │  (可处理其他工作)      │ (查询进行中)              │
   │                      │                           │
   │                      ├─ 查询完毕                  │
   │                      │ User 对象返回              │
   │                      │                           ├─ onNext(user)
   │                      │                           ├─ 执行消费者代码
   │                      │                           ├─ onComplete()
   │                      │                           │
```

### Flux 执行时间线

```
main 线程                数据库线程                反应式框架线程
   │                      │                         │
   ├─ 定义 Flux           │                         │
   │  flux = userRepository.findAll()
   │                      │                         │
   ├─ subscribe()         │                         │
   │                      │                         │
   │                      ├─ 异步查询开始            │
   │                      │                         │
   │  (main 线程继续)      │                         │
   │                      │                         │
   │                      ├─ 返回第一行记录          │
   │                      │                         ├─ onNext(User-1)
   │                      │                         ├─ 处理 User-1
   │                      │                         │
   │                      ├─ 返回第二行记录          │
   │                      │                         ├─ onNext(User-2)
   │                      │                         ├─ 处理 User-2
   │                      │                         │
   │                      ├─ ...                    │
   │                      │                         │
   │                      ├─ 返回最后一行            │
   │                      │                         ├─ onNext(User-N)
   │                      │                         ├─ onComplete()
   │                      │                         │
```

---

## 实际代码示例（来自 Lab-09）

### 示例 1: 简单的 Mono

```java
@GetMapping("/users/{id}")
public Mono<User> getUserById(@PathVariable Long id) {
    return userRepository.findById(id)
        .doOnNext(user -> log.info("找到用户: {}", user.getName()))
        .doOnError(error -> log.error("查询失败", error));
}
```

**执行流程**:
```
HTTP 请求到达
  ↓
Spring 调用 getUserById(1)
  ↓
返回 Mono<User> (还没执行！)
  ↓
Spring 订阅这个 Mono
  ↓
数据库查询开始 (异步)
  ↓
查询完毕，触发 onNext(user)
  ↓
执行 doOnNext 中的日志记录
  ↓
执行 onComplete
  ↓
Http 响应返回给客户端
```

### 示例 2: 简单的 Flux

```java
@GetMapping("/users")
public Flux<User> getAllUsers() {
    return userRepository.findAll()
        .doOnNext(user -> log.info("发送用户: {}", user.getName()))
        .doOnError(error -> log.error("查询失败", error))
        .doOnComplete(() -> log.info("所有用户已发送"));
}
```

**执行流程**:
```
HTTP 请求到达
  ↓
Spring 调用 getAllUsers()
  ↓
返回 Flux<User> (还没执行！)
  ↓
Spring 订阅这个 Flux
  ↓
数据库开始扫描所有行 (异步)
  ↓
第一行到达 → onNext(User-1) → 发送给客户端
第二行到达 → onNext(User-2) → 发送给客户端
...
最后一行到达 → onNext(User-N) → 发送给客户端
扫描完毕 → onComplete()
  ↓
Http 连接关闭
```

---

## 总结与记忆技巧

### 记忆 Mono vs Flux

```
Mono = "Mono" 发音像 "one" (一个)
Flux = "Flux" 表示流动、多个

Mono<User>: 一个用户
Flux<User>: 一流(多个)用户
```

### 订阅的本质

```
Mono<T> 和 Flux<T> 都遵循一个通用的模式：

subscribe(
    onNext:   T -> Unit,           // 每当有数据时调用
    onError:  Exception -> Unit,   // 如果发生错误，调用一次
    onComplete: () -> Unit         // 数据全部发送完毕，调用一次
)

这三个回调遵循规则：
  ✅ onNext 可能被调用 0、1 或多次
  ✅ onError 和 onComplete 最多调用 1 次
  ✅ 如果调用了 onError，就不会调用 onComplete
```

### 何时用 Mono，何时用 Flux

```
使用 Mono:
  ✅ 查询单个用户: userRepository.findById(id)
  ✅ 调用单个 API: apiClient.getPermissions(userId)
  ✅ 单次计算结果: Mono.fromCallable(() -> expensiveComputation())

使用 Flux:
  ✅ 查询多个用户: userRepository.findAll()
  ✅ 分页查询: userRepository.findByPage(pageNum, pageSize)
  ✅ 事件流: Flux.interval(Duration.ofSeconds(1))
  ✅ SSE 推送: Flux<Message>
  ✅ 消息队列消费: kafkaTemplate.receive(topic)
```

---

## 下一步

✅ 已理解 Mono 和 Flux 的基础

现在开始：
1. **Phase 1**: 运行 FluxController + MonoController 实现（10+ Demo）
2. **Phase 2**: 学习操作符 (map/flatMap/merge/zip) 和背压
3. **Phase 3**: 集成 R2DBC、Redis、Kafka

