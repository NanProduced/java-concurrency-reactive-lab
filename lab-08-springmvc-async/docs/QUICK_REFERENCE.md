# Spring MVC 异步支持快速参考

> **快速查阅**: Lab-08 开发时的速查手册
> **版本**: Spring Boot 3.3.x

---

## 1. 三种异步模式速查

### 模式选择决策树

```
需要异步处理？
    │
    ├─ 简单异步任务（数据库查询/HTTP调用）
    │   └─ 使用 Callable<T>
    │
    ├─ 需要在其他线程设置结果（消息队列/事件驱动）
    │   └─ 使用 DeferredResult<T>
    │
    └─ 需要自定义超时/线程池/回调
        └─ 使用 WebAsyncTask<T>
```

---

## 2. 代码模板

### 2.1 Callable（最简单）

```java
@GetMapping("/api/simple-async")
public Callable<ResponseEntity<String>> simpleAsync() {
    return () -> {
        // 在 Spring TaskExecutor 中执行
        Thread.sleep(2000);
        return ResponseEntity.ok("Result");
    };
}
```

**适用场景**：
- ✅ 简单的异步数据库查询
- ✅ 调用外部 HTTP API
- ✅ 不需要自定义超时和线程池

---

### 2.2 DeferredResult（灵活）

```java
@GetMapping("/api/deferred")
public DeferredResult<String> deferredAsync() {
    DeferredResult<String> result = new DeferredResult<>(5000L); // 5秒超时

    // 超时处理
    result.onTimeout(() -> {
        result.setResult("Timeout fallback");
    });

    // 错误处理
    result.onError(throwable -> {
        result.setResult("Error fallback");
    });

    // 在应用管理的线程中设置结果
    CompletableFuture.runAsync(() -> {
        try {
            // 业务逻辑
            String data = fetchData();
            result.setResult(data);
        } catch (Exception e) {
            result.setErrorResult(e);
        }
    });

    return result;
}
```

**适用场景**：
- ✅ 消息队列回调（Kafka/RabbitMQ）
- ✅ 事件驱动架构
- ✅ 需要在应用自己管理的线程中设置结果

---

### 2.3 WebAsyncTask（完整控制）

```java
@GetMapping("/api/web-async-task")
public WebAsyncTask<String> webAsyncTask() {
    Callable<String> callable = () -> {
        Thread.sleep(2000);
        return "Result";
    };

    WebAsyncTask<String> task = new WebAsyncTask<>(
        5000L,              // 超时 5 秒
        "asyncExecutor",    // 线程池名称（可选）
        callable
    );

    // 超时回调
    task.onTimeout(() -> "Timeout fallback");

    // 错误回调
    task.onError(() -> "Error fallback");

    // 完成回调
    task.onCompletion(() -> {
        // 清理资源
    });

    return task;
}
```

**适用场景**：
- ✅ 需要指定特定的线程池
- ✅ 需要自定义超时时间
- ✅ 需要丰富的回调支持

---

## 3. 配置模板

### 3.1 全局异步配置

```java
@Configuration
@EnableWebMvc
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        // 1. 设置默认超时（30秒）
        configurer.setDefaultTimeout(30000L);

        // 2. 设置默认 TaskExecutor
        configurer.setTaskExecutor(asyncTaskExecutor());

        // 3. 注册拦截器
        configurer.registerCallableInterceptors(
            new TimeoutCallableProcessingInterceptor()
        );
        configurer.registerDeferredResultInterceptors(
            new TimeoutDeferredResultProcessingInterceptor()
        );
    }

    @Bean(name = "asyncExecutor")
    public AsyncTaskExecutor asyncTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
```

### 3.2 application.yml 配置

```yaml
spring:
  mvc:
    async:
      request-timeout: 30000  # 默认超时 30 秒

  task:
    execution:
      pool:
        core-size: 10
        max-size: 50
        queue-capacity: 100
      thread-name-prefix: async-
```

---

## 4. MDC 上下文传播

### 4.1 TaskDecorator 方式（推荐）

```java
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        // 捕获主线程的 MDC 上下文
        Map<String, String> mdcContext = MDC.getCopyOfContextMap();

        return () -> {
            try {
                // 恢复 MDC 上下文到工作线程
                if (mdcContext != null) {
                    MDC.setContextMap(mdcContext);
                }
                runnable.run();
            } finally {
                // 清理 MDC 上下文
                MDC.clear();
            }
        };
    }
}
```

**配置**：
```java
@Bean
public AsyncTaskExecutor asyncTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    // ... 其他配置
    executor.setTaskDecorator(new MdcTaskDecorator()); // 注入装饰器
    executor.initialize();
    return executor;
}
```

### 4.2 Micrometer Context Propagation

**依赖**：
```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>context-propagation</artifactId>
</dependency>
```

**使用**：
```java
import io.micrometer.context.ContextSnapshot;

// 在主线程中捕获上下文
ContextSnapshot snapshot = ContextSnapshot.captureAll();

// 在工作线程中恢复上下文
CompletableFuture.runAsync(() -> {
    try (ContextSnapshot.Scope scope = snapshot.setThreadLocals()) {
        // 此处可以访问 MDC、Security Context 等
        String requestId = MDC.get("requestId");
        // 业务逻辑
    }
});
```

---

## 5. 异常处理模板

### 5.1 全局异常处理器

```java
@RestControllerAdvice
public class AsyncExceptionHandler {

    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public ResponseEntity<ErrorResponse> handleTimeout(
            AsyncRequestTimeoutException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse("Request timeout"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Internal error: " + ex.getMessage()));
    }
}
```

### 5.2 回调中处理异常

```java
DeferredResult<String> result = new DeferredResult<>();

result.onError(throwable -> {
    log.error("Async error", throwable);
    result.setResult("Error fallback");
});
```

---

## 6. 测试模板

### 6.1 MockMvc 异步测试

```java
@SpringBootTest
@AutoConfigureMockMvc
public class AsyncControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testAsyncEndpoint() throws Exception {
        // 1. 发起异步请求
        MvcResult mvcResult = mockMvc.perform(get("/api/async"))
                .andExpect(request().asyncStarted())
                .andReturn();

        // 2. 等待异步处理完成
        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().string("Expected result"));
    }

    @Test
    public void testAsyncTimeout() throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/api/async-timeout"))
                .andExpect(request().asyncStarted())
                .andReturn();

        // 模拟超时
        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isServiceUnavailable());
    }
}
```

### 6.2 Awaitility 测试

```java
@Test
public void testAsyncWithAwaitility() {
    DeferredResult<String> result = controller.handleAsync();

    // 等待异步结果（最多 5 秒）
    await().atMost(5, TimeUnit.SECONDS)
            .until(() -> result.hasResult());

    assertThat(result.getResult()).isEqualTo("Expected result");
}
```

---

## 7. 常见问题速查

### 7.1 问题：异步请求超时

**症状**：请求返回 503 Service Unavailable

**解决方案**：
1. 检查超时配置是否合理
2. 确认业务逻辑执行时间
3. 调整超时时间或优化业务逻辑

```java
// 方式 1: DeferredResult 构造函数
DeferredResult<String> result = new DeferredResult<>(10000L); // 10秒

// 方式 2: WebAsyncTask 构造函数
WebAsyncTask<String> task = new WebAsyncTask<>(10000L, callable);

// 方式 3: 全局配置
configurer.setDefaultTimeout(60000L); // 60秒
```

---

### 7.2 问题：MDC 上下文丢失

**症状**：工作线程中无法获取 MDC 变量（如 requestId）

**解决方案**：使用 TaskDecorator 传播 MDC

```java
@Bean
public AsyncTaskExecutor asyncTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setTaskDecorator(new MdcTaskDecorator()); // 关键配置
    executor.initialize();
    return executor;
}
```

---

### 7.3 问题：异常被吞掉

**症状**：异步处理异常未被捕获，客户端无响应

**解决方案**：配置异常处理

```java
// 方式 1: onError 回调
result.onError(throwable -> {
    result.setResult("Error fallback");
});

// 方式 2: 全局异常处理器
@ExceptionHandler(Exception.class)
public ResponseEntity<String> handleException(Exception ex) {
    return ResponseEntity.status(500).body("Error: " + ex.getMessage());
}
```

---

### 7.4 问题：线程池耗尽

**症状**：大量请求等待，响应缓慢

**解决方案**：调整线程池参数

```java
executor.setCorePoolSize(20);    // 增加核心线程数
executor.setMaxPoolSize(100);    // 增加最大线程数
executor.setQueueCapacity(200);  // 增加队列容量
```

**监控指标**：
- 活跃线程数 vs 核心线程数
- 队列大小 vs 队列容量
- 拒绝任务数

---

## 8. 性能调优速查

### 8.1 线程池参数计算

**CPU 密集型任务**：
```
核心线程数 = CPU 核心数 + 1
```

**IO 密集型任务**：
```
核心线程数 = CPU 核心数 × (1 + 平均等待时间 / 平均计算时间)
```

**示例**：
```java
// CPU 密集型
int cpuCount = Runtime.getRuntime().availableProcessors();
executor.setCorePoolSize(cpuCount + 1);

// IO 密集型（假设等待:计算 = 9:1）
executor.setCorePoolSize(cpuCount * 10);
```

### 8.2 超时时间设置

| 场景 | 推荐超时 | 说明 |
|------|---------|------|
| 数据库查询 | 3-5 秒 | 简单查询应该在 1 秒内完成 |
| HTTP API 调用 | 5-10 秒 | 根据外部 API 响应时间调整 |
| 消息队列处理 | 30-60 秒 | 复杂业务逻辑 |
| 批量处理 | 5-10 分钟 | 大批量数据处理 |

---

## 9. 监控指标速查

### 9.1 关键指标

| 指标 | 正常范围 | 告警阈值 |
|------|---------|---------|
| 异步请求成功率 | > 99% | < 95% |
| 异步请求超时率 | < 0.1% | > 1% |
| P99 处理时长 | < 3 秒 | > 10 秒 |
| 线程池使用率 | 30-70% | > 90% |
| 队列使用率 | < 50% | > 80% |

### 9.2 Micrometer 监控代码

```java
@Component
public class AsyncMetrics {

    private final MeterRegistry registry;

    public AsyncMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordSuccess(long duration) {
        Counter.builder("async.requests.success")
                .register(registry).increment();
        Timer.builder("async.requests.duration")
                .register(registry)
                .record(duration, TimeUnit.MILLISECONDS);
    }

    public void recordTimeout() {
        Counter.builder("async.requests.timeout")
                .register(registry).increment();
    }

    public void recordError() {
        Counter.builder("async.requests.error")
                .register(registry).increment();
    }
}
```

---

## 10. 检查清单

### 开发前检查

- [ ] 确认是否真的需要异步（不要过度设计）
- [ ] 选择合适的异步模式（Callable/DeferredResult/WebAsyncTask）
- [ ] 设计超时时间和降级策略
- [ ] 设计异常处理策略

### 开发中检查

- [ ] 设置合理的超时时间
- [ ] 配置 MDC 上下文传播（如果需要）
- [ ] 添加 onTimeout/onError/onCompletion 回调
- [ ] 添加监控指标埋点

### 上线前检查

- [ ] 编写单元测试（包括超时和异常场景）
- [ ] 配置线程池参数（根据业务类型）
- [ ] 配置监控告警阈值
- [ ] 压测验证性能指标

---

**最后更新**: 2025-10-21
**适用版本**: Spring Boot 3.3.x
