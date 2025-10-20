# DeferredResult 模式 - Spring MVC 事件驱动异步编程

> **目标**: 深入理解 DeferredResult 的事件驱动模式，掌握外部事件触发响应的最佳实践。为构建高性能长轮询、消息推送系统打好基础。降低学习曲线 ~70%。

---

## 1. 核心概念

### 1.1 DeferredResult 是什么？

**DeferredResult** 是 Spring MVC 3.2 引入的异步处理机制，用于处理**需要等待外部事件**的异步请求。

**核心特性**：
- ⚡ **立即释放请求线程**: Controller 返回 DeferredResult 后，Tomcat 线程立即释放
- 🎯 **事件驱动**: 由外部事件（消息队列、WebSocket、定时任务等）触发完成
- 🔧 **手动控制**: 开发者完全控制何时、如何完成异步请求
- 🎪 **最高灵活度**: 适用于复杂的异步场景（长轮询、服务器推送、分布式任务）

**与 Callable 的本质区别**：
- **Callable**: Spring 自动管理执行（提交到线程池），适合简单异步任务
- **DeferredResult**: 开发者手动管理完成时机，适合事件驱动场景

---

### 1.2 典型应用场景

**场景 1：长轮询 (Long Polling)**

客户端请求后等待服务器推送消息，直到有新消息或超时：

```java
@GetMapping("/messages")
public DeferredResult<Message> getNewMessage(@RequestParam String userId) {
    DeferredResult<Message> deferredResult = new DeferredResult<>(60000L);  // 60 秒超时

    // 存储到待处理队列（等待新消息）
    messageQueue.addPendingRequest(userId, deferredResult);

    // 超时处理
    deferredResult.onTimeout(() -> {
        deferredResult.setResult(Message.empty());
        messageQueue.removePendingRequest(userId);
    });

    return deferredResult;  // ⚡ Tomcat 线程立即释放
}

// 新消息到达时（由消息队列触发）
@KafkaListener(topics = "user-messages")
public void onNewMessage(Message message) {
    DeferredResult<Message> deferredResult = messageQueue.getPendingRequest(message.getUserId());
    if (deferredResult != null) {
        deferredResult.setResult(message);  // ✅ 触发完成
    }
}
```

**场景 2：订单状态推送**

用户下单后，等待支付回调或物流更新：

```java
@PostMapping("/orders/{orderId}/status")
public DeferredResult<OrderStatus> waitForOrderUpdate(@PathVariable String orderId) {
    DeferredResult<OrderStatus> deferredResult = new DeferredResult<>(120000L);  // 2 分钟超时

    orderService.registerStatusListener(orderId, status -> {
        deferredResult.setResult(status);  // 支付回调或物流更新时触发
    });

    return deferredResult;
}
```

**场景 3：异步任务查询**

客户端提交任务后，轮询任务结果：

```java
@GetMapping("/tasks/{taskId}/result")
public DeferredResult<TaskResult> getTaskResult(@PathVariable String taskId) {
    DeferredResult<TaskResult> deferredResult = new DeferredResult<>(30000L);

    // 注册任务完成监听器
    taskService.onTaskComplete(taskId, result -> {
        deferredResult.setResult(result);
    });

    return deferredResult;
}
```

---

## 2. DeferredResult vs Callable 对比

### 2.1 执行流程对比

**Callable 模式（Spring 自动管理）**：

```
┌────────────┐
│ Controller │ → return Callable
└─────┬──────┘
      │
      ▼
┌─────────────────┐
│ Spring MVC      │ → 提交到 AsyncTaskExecutor
│ 调用 Callable   │
└─────┬───────────┘
      │
      ▼
┌─────────────────┐
│ 线程池执行      │ → call() 方法
│ Callable.call() │
└─────┬───────────┘
      │
      ▼
┌─────────────────┐
│ Spring MVC      │ → 自动调用 asyncContext.complete()
│ 完成响应        │
└─────────────────┘
```

**DeferredResult 模式（开发者手动管理）**：

```
┌────────────┐
│ Controller │ → return DeferredResult
└─────┬──────┘
      │
      ▼
┌─────────────────┐
│ Tomcat 线程释放 │ ⚡ 立即返回
└─────────────────┘
      │
      ▼
┌─────────────────┐
│ 等待外部事件... │ (消息队列、定时任务、回调等)
└─────┬───────────┘
      │
      ▼
┌─────────────────┐
│ 外部事件触发    │ → deferredResult.setResult(data)
└─────┬───────────┘
      │
      ▼
┌─────────────────┐
│ Spring MVC      │ → 自动调用 asyncContext.complete()
│ 完成响应        │
└─────────────────┘
```

### 2.2 特性对比表

| 特性 | Callable | DeferredResult |
|------|----------|----------------|
| **执行模型** | 线程池执行 | 事件驱动 |
| **控制权** | Spring 自动管理 | 开发者手动控制 |
| **适用场景** | 简单异步任务（DB 查询、API 调用） | 外部事件驱动（MQ、WebSocket、长轮询） |
| **超时控制** | 全局配置 + WebAsyncTask | DeferredResult 构造函数 |
| **线程占用** | 业务线程池 | 无线程占用（等待事件） |
| **复杂度** | 低 | 中 |
| **灵活度** | 低 | 高 |
| **性能** | 中（占用线程） | 高（无线程占用） |

### 2.3 代码对比

**Callable 示例**：
```java
@GetMapping("/api-call")
public Callable<String> callExternalAPI() {
    return () -> {
        // ⚠️ 占用线程池线程
        Thread.sleep(2000);  // 模拟 API 调用
        return "Result";
    };
}
```

**DeferredResult 示例**：
```java
@GetMapping("/event-driven")
public DeferredResult<String> eventDriven() {
    DeferredResult<String> deferredResult = new DeferredResult<>(5000L);

    // ⚡ 不占用线程，等待外部事件
    eventBus.subscribe(event -> {
        deferredResult.setResult(event.getData());
    });

    return deferredResult;
}
```

---

## 3. DeferredResult 完整生命周期

### 3.1 生命周期回调

DeferredResult 提供 4 个生命周期回调：

```java
DeferredResult<String> deferredResult = new DeferredResult<>(10000L);

// 1. onTimeout - 超时时触发
deferredResult.onTimeout(() -> {
    log.warn("请求超时");
    deferredResult.setResult("Timeout");  // 设置超时响应
});

// 2. onError - 异常时触发
deferredResult.onError(throwable -> {
    log.error("请求异常", throwable);
    deferredResult.setErrorResult("Error: " + throwable.getMessage());
});

// 3. onCompletion - 完成时触发（无论成功、失败、超时）
deferredResult.onCompletion(() -> {
    log.info("请求完成");
    // 清理资源
    pendingRequests.remove(userId);
});
```

### 3.2 状态机

```
┌──────────────┐
│   CREATED    │ 初始状态
└──────┬───────┘
       │
       ▼
┌──────────────┐
│   PENDING    │ 等待外部事件
└──────┬───────┘
       │
       ├────────────────┐
       │                │
       ▼                ▼
┌──────────────┐   ┌──────────────┐
│  COMPLETED   │   │   TIMEOUT    │
│  (成功)      │   │   (超时)     │
└──────┬───────┘   └──────┬───────┘
       │                  │
       └────────┬─────────┘
                │
                ▼
       ┌──────────────┐
       │   FINISHED   │ 最终状态
       │ (onCompletion)│
       └──────────────┘
```

### 3.3 最佳实践示例

```java
@RestController
@RequestMapping("/api")
public class DeferredResultController {

    private final ConcurrentHashMap<String, DeferredResult<Message>> pendingRequests = new ConcurrentHashMap<>();

    @GetMapping("/messages/{userId}")
    public DeferredResult<Message> getNewMessage(@PathVariable String userId) {
        DeferredResult<Message> deferredResult = new DeferredResult<>(60000L);

        // ========== 回调1: onTimeout ==========
        deferredResult.onTimeout(() -> {
            log.warn("[DeferredResult] 超时 - 用户: {}", userId);
            deferredResult.setResult(Message.empty());
        });

        // ========== 回调2: onError ==========
        deferredResult.onError(throwable -> {
            log.error("[DeferredResult] 异常 - 用户: {}", userId, throwable);
            deferredResult.setErrorResult("Internal Server Error");
        });

        // ========== 回调3: onCompletion ==========
        deferredResult.onCompletion(() -> {
            log.info("[DeferredResult] 完成 - 用户: {}", userId);
            // ⚠️ 清理资源（防止内存泄漏）
            pendingRequests.remove(userId);
        });

        // 存储到待处理队列
        pendingRequests.put(userId, deferredResult);

        return deferredResult;
    }

    // 外部事件触发完成
    @PostMapping("/messages/{userId}/push")
    public String pushMessage(@PathVariable String userId, @RequestBody Message message) {
        DeferredResult<Message> deferredResult = pendingRequests.get(userId);

        if (deferredResult != null) {
            deferredResult.setResult(message);  // ✅ 触发完成
            return "Pushed";
        } else {
            return "No pending request";
        }
    }
}
```

---

## 4. 资源管理与内存泄漏防护

### 4.1 内存泄漏风险

**问题场景**：
```java
private final ConcurrentHashMap<String, DeferredResult<Message>> pendingRequests = new ConcurrentHashMap<>();

@GetMapping("/messages/{userId}")
public DeferredResult<Message> getNewMessage(@PathVariable String userId) {
    DeferredResult<Message> deferredResult = new DeferredResult<>(60000L);
    pendingRequests.put(userId, deferredResult);  // ⚠️ 存储 DeferredResult

    // ❌ 如果外部事件永远不触发，DeferredResult 永远不会被移除
    // → 内存泄漏

    return deferredResult;
}
```

**风险分析**：
- 客户端断开连接 → DeferredResult 仍在 Map 中
- 超时未处理 → DeferredResult 仍在 Map 中
- 异常未清理 → DeferredResult 仍在 Map 中

### 4.2 解决方案

**方案 1：在 onCompletion 中清理**（✅ 推荐）

```java
deferredResult.onCompletion(() -> {
    pendingRequests.remove(userId);  // ✅ 无论成功、失败、超时都会执行
});
```

**方案 2：使用带过期时间的 Map**

```java
// 使用 Guava Cache
private final Cache<String, DeferredResult<Message>> pendingRequests = CacheBuilder.newBuilder()
    .expireAfterWrite(65, TimeUnit.SECONDS)  // 65 秒后自动过期（超时时间 + 5 秒缓冲）
    .build();
```

**方案 3：定时清理任务**

```java
@Scheduled(fixedRate = 60000)  // 每 60 秒清理一次
public void cleanupExpiredRequests() {
    long now = System.currentTimeMillis();
    pendingRequests.entrySet().removeIf(entry -> {
        DeferredResult<?> deferredResult = entry.getValue();
        return deferredResult.isSetOrExpired();  // 移除已完成或超时的请求
    });
}
```

---

## 5. 高级模式：DeferredResult 管理器

### 5.1 封装 DeferredResultManager

```java
@Component
public class DeferredResultManager {

    private final ConcurrentHashMap<String, DeferredResult<Message>> pendingRequests = new ConcurrentHashMap<>();

    /**
     * 创建并注册 DeferredResult
     */
    public DeferredResult<Message> createDeferredResult(String userId, long timeout) {
        DeferredResult<Message> deferredResult = new DeferredResult<>(timeout);

        // 设置回调
        deferredResult.onTimeout(() -> {
            log.warn("超时 - 用户: {}", userId);
            deferredResult.setResult(Message.empty());
        });

        deferredResult.onError(throwable -> {
            log.error("异常 - 用户: {}", userId, throwable);
        });

        deferredResult.onCompletion(() -> {
            log.info("完成 - 用户: {}", userId);
            pendingRequests.remove(userId);  // ✅ 清理资源
        });

        // 注册
        pendingRequests.put(userId, deferredResult);

        return deferredResult;
    }

    /**
     * 触发完成
     */
    public boolean complete(String userId, Message message) {
        DeferredResult<Message> deferredResult = pendingRequests.get(userId);
        if (deferredResult != null) {
            deferredResult.setResult(message);
            return true;
        }
        return false;
    }

    /**
     * 批量完成
     */
    public int completeAll(List<String> userIds, Message message) {
        int count = 0;
        for (String userId : userIds) {
            if (complete(userId, message)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 获取待处理请求数
     */
    public int getPendingCount() {
        return pendingRequests.size();
    }
}
```

### 5.2 使用 Manager

```java
@RestController
@RequiredArgsConstructor
public class MessageController {

    private final DeferredResultManager deferredResultManager;

    @GetMapping("/messages/{userId}")
    public DeferredResult<Message> getNewMessage(@PathVariable String userId) {
        return deferredResultManager.createDeferredResult(userId, 60000L);
    }

    @PostMapping("/messages/broadcast")
    public String broadcastMessage(@RequestBody Message message) {
        List<String> userIds = Arrays.asList("user1", "user2", "user3");
        int count = deferredResultManager.completeAll(userIds, message);
        return "Broadcasted to " + count + " users";
    }
}
```

---

## 6. 性能优化与监控

### 6.1 性能指标

**关键指标**：
- ⏱️ **平均等待时间**: 从请求创建到完成的时间
- 📊 **待处理请求数**: 实时监控 `pendingRequests.size()`
- ⚡ **完成率**: 成功完成 / 总请求数
- ⏰ **超时率**: 超时请求 / 总请求数

**监控示例**：
```java
@Component
@RequiredArgsConstructor
public class DeferredResultMetrics {

    private final MeterRegistry meterRegistry;
    private final DeferredResultManager manager;

    @PostConstruct
    public void registerMetrics() {
        // 待处理请求数
        Gauge.builder("deferred_result.pending_count", manager, DeferredResultManager::getPendingCount)
            .register(meterRegistry);
    }

    public void recordCompletion(String userId, long duration) {
        meterRegistry.timer("deferred_result.completion_time").record(duration, TimeUnit.MILLISECONDS);
        meterRegistry.counter("deferred_result.completed").increment();
    }

    public void recordTimeout(String userId) {
        meterRegistry.counter("deferred_result.timeout").increment();
    }
}
```

### 6.2 性能优化建议

1. ✅ **使用 ConcurrentHashMap**: 线程安全且高性能
2. ✅ **设置合理的超时时间**: 避免请求永久挂起
3. ✅ **使用 onCompletion 清理**: 防止内存泄漏
4. ✅ **监控待处理请求数**: 防止无限增长
5. ✅ **使用连接池**: 外部事件触发时避免频繁创建连接

---

## 7. 常见误区与陷阱

### ❌ 误区 1: "忘记清理资源"

**问题**：
```java
pendingRequests.put(userId, deferredResult);
// ❌ 忘记在 onCompletion 中清理 → 内存泄漏
```

**解决**：
```java
deferredResult.onCompletion(() -> {
    pendingRequests.remove(userId);  // ✅ 清理资源
});
```

### ❌ 误区 2: "重复设置结果"

**问题**：
```java
deferredResult.setResult("Result 1");
deferredResult.setResult("Result 2");  // ❌ IllegalStateException
```

**解决**：
```java
if (!deferredResult.isSetOrExpired()) {
    deferredResult.setResult("Result");  // ✅ 检查状态
}
```

### ❌ 误区 3: "超时未处理"

**问题**：
```java
DeferredResult<String> deferredResult = new DeferredResult<>(10000L);
// ❌ 超时后返回 503 错误
```

**解决**：
```java
deferredResult.onTimeout(() -> {
    deferredResult.setResult("Timeout");  // ✅ 设置超时响应
});
```

---

## 8. 决策树：何时使用 DeferredResult？

```
需要等待外部事件？
  ├─ 是 → DeferredResult
  └─ 否 → Callable

外部事件类型？
  ├─ 消息队列（Kafka, RabbitMQ） → DeferredResult ✅
  ├─ WebSocket 推送 → DeferredResult ✅
  ├─ 定时任务触发 → DeferredResult ✅
  ├─ 支付回调 → DeferredResult ✅
  └─ 简单异步任务（DB 查询） → Callable

并发请求数？
  ├─ < 1000 → DeferredResult (单机)
  ├─ 1000-10000 → DeferredResult + Redis
  └─ > 10000 → DeferredResult + Redis + 集群

需要手动控制完成时机？
  ├─ 是 → DeferredResult
  └─ 否 → Callable
```

---

## 9. 最佳实践总结

1. ✅ **总是设置超时时间**: 避免请求永久挂起
2. ✅ **使用 onCompletion 清理资源**: 防止内存泄漏
3. ✅ **使用 ConcurrentHashMap**: 线程安全的存储
4. ✅ **封装 DeferredResultManager**: 统一管理和监控
5. ✅ **监控性能指标**: 待处理请求数、完成率、超时率
6. ✅ **处理超时和错误**: 提供友好的错误响应
7. ✅ **避免重复设置结果**: 检查 `isSetOrExpired()`
8. ✅ **使用分布式缓存**: 集群环境使用 Redis

---

## 10. 延伸阅读

- **Spring MVC Async Request Processing** - Spring 官方文档
- **Long Polling vs WebSocket** - 实时通信方案对比
- **响应式编程** - Project Reactor, RxJava

---

**下一步**: 阅读 `TIMEOUT_STRATEGY.md`，掌握 Spring MVC 异步请求的超时控制策略。
