# Lab-04: CompletableFuture & 异步编排

> **教学目标**：系统掌握 CompletableFuture 异步编程，理解异步链式调用、异常处理、超时控制、上下文传递等核心技术，建立完整的异步范例库。

---

## 📚 学习目标

### 核心目标
1. **链式调用**：掌握 CompletableFuture 的基础链式操作（thenApply, thenCompose, thenCombine）
2. **异步聚合**：实现三下游服务聚合 + 容错策略（验证标准）
3. **异常处理**：理解异步异常传播链与恢复机制
4. **超时控制**：掌握 Java 9+ 超时 API 与取消机制
5. **上下文穿透**：解决 MDC/ThreadLocal 在异步场景下的丢失问题

### 核心产出
- **异步范例库**：5+ 可复用的异步模式（基础链式、聚合、异常、超时、MDC）
- **教学代码**：2400+ 行高质量演示代码，注释密度 ≥70%
- **对比设计**：每个场景都提供 WITHOUT vs WITH 对比
- **性能数据**：实测的性能改进指标（如串行 vs 并行）

---

## 🚀 快速开始

### 1. 环境要求

```bash
✅ JDK 17+
✅ Maven 3.9+
✅ Spring Boot 3.3.x
```

### 2. 运行所有演示

```bash
# 切换到模块目录
cd lab-04-completablefuture

# 方式1：运行所有演示（推荐）
mvn spring-boot:run

# 方式2：运行单个演示
mvn exec:java -Dexec.mainClass="nan.tech.lab04.completablefuture.basics.CompletableFutureBasicsDemo"
mvn exec:java -Dexec.mainClass="nan.tech.lab04.completablefuture.aggregation.ThreeDownstreamAggregationDemo"
mvn exec:java -Dexec.mainClass="nan.tech.lab04.completablefuture.exception.ExceptionHandlingDemo"
mvn exec:java -Dexec.mainClass="nan.tech.lab04.completablefuture.timeout.TimeoutCancellationDemo"
mvn exec:java -Dexec.mainClass="nan.tech.lab04.completablefuture.context.MDCPropagationDemo"
```

### 3. 编译与测试

```bash
# 编译
mvn clean compile

# 运行测试（如果有）
mvn test

# 生成 Javadoc
mvn javadoc:javadoc
open target/site/apidocs/index.html
```

---

## 📖 学习路径

### 推荐学习顺序

```
第1步: 基础链式调用
  └─ CompletableFutureBasicsDemo
      ├─ 同步 vs 异步对比
      ├─ thenApply vs thenApplyAsync（线程复用 vs 切换）
      ├─ thenCompose（扁平化嵌套）
      ├─ thenCombine（并行合并）
      └─ 自定义线程池 vs ForkJoinPool

第2步: 异步聚合 + 容错【验证标准】
  └─ ThreeDownstreamAggregationDemo
      ├─ 三下游服务聚合（用户 + 订单 + 推荐）
      ├─ 串行 vs 并行性能对比（55%性能提升）
      ├─ 容错策略1：全有或全无
      ├─ 容错策略2：部分成功 + 默认值
      └─ 容错策略3：快速失败 + 取消

第3步: 异常处理链
  └─ ExceptionHandlingDemo
      ├─ exceptionally（异常恢复）
      ├─ handle（统一处理成功和失败）
      ├─ whenComplete（后置操作）
      ├─ 异常传播链行为
      ├─ 多层异常处理
      └─ CompletionException 包装/解包

第4步: 超时与取消
  └─ TimeoutCancellationDemo
      ├─ orTimeout（超时抛异常）
      ├─ completeOnTimeout（超时返回默认值）
      ├─ cancel() 手动取消
      ├─ 状态查询（isDone/isCancelled/isCompletedExceptionally）
      ├─ 超时后资源清理
      └─ 超时降级策略

第5步: 上下文穿透
  └─ MDCPropagationDemo
      ├─ MDC 基本用法
      ├─ 问题演示：异步调用中 MDC 丢失
      ├─ 手动传递 MDC
      ├─ 封装 MDC 装饰器
      └─ 完整的异步链路 MDC 传递
```

---

## 🎯 核心场景详解

### 场景1: 基础链式调用

**文件**: `CompletableFutureBasicsDemo.java`

**核心知识点**:
- **thenApply**: 同步转换，在上一步的线程中执行
- **thenApplyAsync**: 异步转换，在线程池中执行
- **thenCompose**: 扁平化嵌套 CompletableFuture（类似 flatMap）
- **thenCombine**: 并行执行两个独立任务并合并结果

**性能对比**:
```
串行执行: 250ms (100ms + 150ms)
并行执行: 150ms (max(100ms, 150ms))
性能提升: 40%
```

**关键代码**:
```java
// ✅ 并行执行 + 合并
CompletableFuture<String> userFuture = CompletableFuture.supplyAsync(() -> fetchUser());
CompletableFuture<String> orderFuture = CompletableFuture.supplyAsync(() -> fetchOrder());

CompletableFuture<String> result = userFuture.thenCombine(orderFuture,
    (user, order) -> user + " -> " + order
);
```

---

### 场景2: 三下游聚合 + 容错 【验证标准】

**文件**: `ThreeDownstreamAggregationDemo.java`

**业务场景**: 聚合用户、订单、推荐三个下游服务

**性能对比**:
```
串行调用: 450ms (150ms + 150ms + 150ms)
并行调用: 200ms (max(150ms, 150ms, 150ms) + 聚合开销)
性能提升: 55%
```

**三种容错策略**:

| 策略 | 描述 | 适用场景 | 实现方式 |
|------|------|----------|----------|
| **全有或全无** | 任意服务失败则整体失败 | 关键业务路径 | `allOf().join()` + 异常处理 |
| **部分成功** | 失败服务使用默认值 | 非关键服务可降级 | `exceptionally()` 返回默认值 |
| **快速失败** | 首个失败立即取消其他任务 | 高延迟不可接受 | `anyOf()` + `cancel()` |

**关键代码**:
```java
// 容错策略2: 部分成功
CompletableFuture<String> orderFuture = CompletableFuture
    .supplyAsync(() -> fetchOrder(userId))
    .exceptionally(ex -> {
        log.warn("订单服务失败，使用默认值: {}", ex.getMessage());
        return "无订单";  // 降级
    });

CompletableFuture<String> result = CompletableFuture
    .allOf(userFuture, orderFuture, recommendFuture)
    .thenApply(v -> aggregateResults(userFuture, orderFuture, recommendFuture));
```

---

### 场景3: 异常处理链

**文件**: `ExceptionHandlingDemo.java`

**核心方法对比**:

| 方法 | 作用 | 是否改变结果 | 类比 |
|------|------|--------------|------|
| **exceptionally** | 异常恢复 | ✅ 是 | `catch` + `return` |
| **handle** | 统一处理成功/失败 | ✅ 是 | `finally` + `return` |
| **whenComplete** | 后置操作 | ❌ 否 | `finally` |

**异常传播规则**:
```
步骤1成功 → 步骤2异常 → 步骤3跳过 → exceptionally捕获
```

**关键代码**:
```java
CompletableFuture<String> future = CompletableFuture
    .supplyAsync(() -> "Step1")
    .thenApply(s -> {
        throw new RuntimeException("步骤2异常");
    })
    .thenApply(s -> s + "-Step3")  // 不会执行
    .exceptionally(ex -> {
        log.warn("捕获异常: {}", ex.getMessage());
        return "Recovered";  // 恢复
    });
```

**陷阱**:
- ❌ **CompletionException 包装**: 需要用 `getCause()` 获取原始异常
- ❌ **异常吞没**: `whenComplete` 不重新抛出异常会导致异常被吞没
- ❌ **多层嵌套**: 多次包装导致异常难以定位

---

### 场景4: 超时与取消

**文件**: `TimeoutCancellationDemo.java`

**Java 9+ 超时 API**:

| 方法 | 超时行为 | 适用场景 |
|------|----------|----------|
| **orTimeout** | 抛出 `TimeoutException` | 严格超时要求 |
| **completeOnTimeout** | 返回默认值 | 服务降级 |

**状态查询**:
- `isDone()`: 是否完成（成功/失败/取消）
- `isCancelled()`: 是否被取消
- `isCompletedExceptionally()`: 是否异常完成

**关键代码**:
```java
// orTimeout: 超时抛异常
CompletableFuture<String> future1 = CompletableFuture
    .supplyAsync(() -> slowTask())
    .orTimeout(100, TimeUnit.MILLISECONDS);

// completeOnTimeout: 超时返回默认值
CompletableFuture<String> future2 = CompletableFuture
    .supplyAsync(() -> slowTask())
    .completeOnTimeout("Default", 100, TimeUnit.MILLISECONDS);

// 超时降级策略
CompletableFuture<String> future3 = CompletableFuture
    .supplyAsync(() -> callPrimaryService())
    .orTimeout(100, TimeUnit.MILLISECONDS)
    .exceptionally(ex -> {
        if (ex.getCause() instanceof TimeoutException) {
            return "Cached Result";  // 使用缓存
        }
        return "Default Result";
    });
```

**陷阱**:
- ❌ **取消不会中断线程**: `cancel()` 只是标记状态，不会真正中断正在执行的任务
- ❌ **资源泄漏**: 超时后任务可能仍在后台执行，需要手动清理
- ❌ **超时时间设置**: 过短误杀，过长影响用户体验

---

### 场景5: MDC 穿透

**文件**: `MDCPropagationDemo.java`

**问题**: MDC 基于 ThreadLocal，线程切换时会丢失

**解决方案**: MDC 装饰器模式

**关键代码**:
```java
// MDC 装饰器
private static <T> Supplier<T> withMDC(Supplier<T> supplier) {
    // 在调用线程捕获 MDC
    Map<String, String> contextMap = MDC.getCopyOfContextMap();

    return () -> {
        // 在执行线程恢复 MDC
        if (contextMap != null) {
            MDC.setContextMap(contextMap);
        }
        try {
            return supplier.get();
        } finally {
            // 清理 MDC（重要！避免线程池复用时污染）
            MDC.clear();
        }
    };
}

// 使用装饰器
CompletableFuture<String> future = CompletableFuture
    .supplyAsync(withMDC(() -> {
        log.info("traceId={}", MDC.get("traceId"));
        return "Success";
    }));
```

**完整链路传递**:
```java
CompletableFuture<String> result = CompletableFuture
    .supplyAsync(withMDC(() -> fetchUser()))
    .thenApplyAsync(withMDC(user -> fetchOrder(user)))
    .thenApplyAsync(withMDC(order -> aggregate(order)));
```

**陷阱**:
- ❌ **内存泄漏**: 使用线程池时必须清理 MDC
- ❌ **传递时机**: 必须在异步调用前捕获 MDC
- ❌ **性能开销**: 每次都复制 Map 有一定开销

---

## 🎨 异步范例库

### 1. 基础链式模式

```java
// 模式: 串行转换
CompletableFuture<String> result = CompletableFuture
    .supplyAsync(() -> fetchData())
    .thenApply(data -> transform(data))
    .thenApply(transformed -> save(transformed));

// 模式: 扁平化嵌套
CompletableFuture<String> result = CompletableFuture
    .supplyAsync(() -> getUserId())
    .thenCompose(userId -> fetchUserProfile(userId));

// 模式: 并行合并
CompletableFuture<String> user = CompletableFuture.supplyAsync(() -> fetchUser());
CompletableFuture<String> order = CompletableFuture.supplyAsync(() -> fetchOrder());
CompletableFuture<String> result = user.thenCombine(order, (u, o) -> u + o);
```

### 2. 聚合模式

```java
// 模式: 等待所有任务
CompletableFuture<Void> allTasks = CompletableFuture.allOf(task1, task2, task3);
allTasks.thenRun(() -> log.info("所有任务完成"));

// 模式: 取最快结果
CompletableFuture<Object> fastest = CompletableFuture.anyOf(task1, task2, task3);
fastest.thenAccept(result -> log.info("最快结果: {}", result));
```

### 3. 异常处理模式

```java
// 模式: 异常恢复
CompletableFuture<String> result = CompletableFuture
    .supplyAsync(() -> riskyOperation())
    .exceptionally(ex -> "Default Value");

// 模式: 统一处理
CompletableFuture<String> result = CompletableFuture
    .supplyAsync(() -> operation())
    .handle((res, ex) -> ex != null ? "Error" : "Success: " + res);
```

### 4. 超时控制模式

```java
// 模式: 超时异常
CompletableFuture<String> result = CompletableFuture
    .supplyAsync(() -> slowService())
    .orTimeout(1000, TimeUnit.MILLISECONDS);

// 模式: 超时降级
CompletableFuture<String> result = CompletableFuture
    .supplyAsync(() -> slowService())
    .completeOnTimeout("Cached", 1000, TimeUnit.MILLISECONDS);
```

### 5. 上下文传递模式

```java
// 模式: MDC 装饰器
CompletableFuture<String> result = CompletableFuture
    .supplyAsync(withMDC(() -> operation()))
    .thenApplyAsync(withMDC(data -> transform(data)));

// 模式: 自定义上下文
CompletableFuture<String> result = CompletableFuture
    .supplyAsync(withContext(context, () -> operation()));
```

---

## ⚠️ 常见陷阱

### 1. 默认线程池的局限

**问题**: CompletableFuture 默认使用 `ForkJoinPool.commonPool()`
- 线程数 = CPU 核数 - 1
- 不适合 IO 密集型任务

**解决**:
```java
// 自定义线程池
ExecutorService executor = new ThreadPoolExecutor(
    cpuCores * 2,      // 核心线程数（混合型任务）
    cpuCores * 4,      // 最大线程数
    60L, TimeUnit.SECONDS,
    new LinkedBlockingQueue<>(2000)
);

CompletableFuture.supplyAsync(() -> ioTask(), executor);
```

### 2. 异常被包装

**问题**: 原始异常被包装成 `CompletionException`

**解决**:
```java
try {
    future.join();
} catch (CompletionException ex) {
    Throwable cause = ex.getCause();  // 获取原始异常
    log.error("原始异常: {}", cause.getMessage());
}
```

### 3. MDC 丢失

**问题**: ThreadLocal 在线程切换时丢失

**解决**: 使用 MDC 装饰器（见场景5）

### 4. 资源泄漏

**问题**: 超时后任务仍在执行，占用资源

**解决**:
```java
CompletableFuture<String> future = CompletableFuture
    .supplyAsync(() -> operation())
    .orTimeout(1000, TimeUnit.MILLISECONDS)
    .whenComplete((result, ex) -> {
        // 无论成功还是失败都清理资源
        cleanup();
    });
```

### 5. 取消无效

**问题**: `cancel()` 不会真正中断正在执行的任务

**解决**:
```java
// 在任务中定期检查中断状态
CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
    for (int i = 0; i < 100; i++) {
        if (Thread.currentThread().isInterrupted()) {
            return "Cancelled";
        }
        // 执行任务
    }
    return "Completed";
});
```

---

## 📊 性能对比

### 串行 vs 并行

| 场景 | 串行耗时 | 并行耗时 | 性能提升 |
|------|----------|----------|----------|
| 三下游聚合 | 450ms | 200ms | **55%** |
| 双任务合并 | 250ms | 150ms | **40%** |

### 线程池选择

| 任务类型 | 线程池配置 | 依据 |
|----------|-----------|------|
| **CPU 密集型** | 核心线程 = CPU核数 | 避免线程切换开销 |
| **IO 密集型** | 核心线程 = CPU核数 × 10 | 大量等待时间可以更多线程 |
| **混合型** | 核心线程 = CPU核数 × 2 | 平衡 CPU 和 IO |

---

## 📚 参考资料

### 官方文档
- [CompletableFuture JavaDoc](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html)
- [Java 9 CompletableFuture Enhancements](https://www.baeldung.com/java-9-completablefuture)

### 推荐阅读
- **Java 8 in Action**: CompletableFuture 完整指南
- **Effective Java**: 异步编程最佳实践
- **Reactive Programming**: 响应式编程思想

### 相关 Lab
- **Lab-03**: ExecutorService 线程池管理
- **Lab-05**: Reactive Streams（后续）

---

## 🎯 验证标准

### 代码质量
- ✅ 注释密度 ≥ 70%
- ✅ Javadoc 覆盖率 100%（公开 API）
- ✅ 所有 Demo 可独立运行（main 方法）
- ✅ 线程安全检查通过
- ✅ 资源管理规范（线程池关闭、异常清理）

### 教学价值
- ✅ 每个场景提供 WITHOUT vs WITH 对比
- ✅ 丰富的注释能够独立讲解
- ✅ 完整的异常处理示例
- ✅ 实测的性能数据

### 核心产出
- ✅ 三下游聚合 + 容错策略（验证标准）
- ✅ 异步范例库（5+ 模式）
- ✅ 完整的 README 文档
- ✅ 2400+ 行高质量代码

---

## 🚧 已知问题

### 当前无已知问题

（后续发现问题会在此记录）

---

## 📝 下一步

### 立即行动
1. 按学习路径运行所有 Demo
2. 理解每个场景的核心原理
3. 尝试修改参数观察行为变化

### 进阶学习
1. 集成到 Spring Boot 项目
2. 结合 Reactive Streams（Lab-05）
3. 性能压测与调优
4. 生产环境最佳实践

---

## 📧 联系方式

如有问题或建议，请提 Issue 或 PR。

---

**License**: MIT
**Author**: Nan
**Last Updated**: 2025-01-18
