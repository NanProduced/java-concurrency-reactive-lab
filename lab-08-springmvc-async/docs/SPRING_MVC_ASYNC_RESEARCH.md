# Spring MVC 异步支持技术文档研究报告

> **研究目标**: 为 Lab-08: Spring MVC Async 提供全面的官方文档、最佳实践和实现指南
> **适用版本**: Spring Boot 3.3.x (Spring Framework 6.2.11)
> **研究日期**: 2025-10-21

---

## 1. 核心概念总览

### 1.1 Spring MVC 异步请求处理模型

Spring MVC 从 3.2 版本开始支持异步请求处理，允许在不阻塞 Servlet 容器线程的情况下处理长时间运行的请求。

**核心优势**：
- **释放容器线程**: Servlet 容器线程在等待异步操作完成时可以处理其他请求
- **提升吞吐量**: 在高并发场景下，减少线程阻塞，提升系统整体吞吐量
- **优化资源使用**: 避免线程池耗尽，优化服务器资源利用率

**三种核心异步模式**：

| 模式 | 适用场景 | 线程模型 | 复杂度 |
|------|---------|---------|--------|
| **Callable** | 简单异步任务，单一结果 | Spring 管理的 TaskExecutor | 低 |
| **DeferredResult** | 需要在其他线程中设置结果（如消息队列回调） | 应用自己管理线程 | 中 |
| **WebAsyncTask** | 需要自定义超时、线程池、回调的场景 | 自定义 TaskExecutor + 超时控制 | 高 |

---

## 2. 核心 API 详解

### 2.1 DeferredResult<T>

**定义**：
```java
public class DeferredResult<T> {
    // 构造函数
    public DeferredResult();
    public DeferredResult(Long timeoutValue);
    public DeferredResult(Long timeoutValue, Object timeoutResult);
    public DeferredResult(Long timeoutValue, Supplier<?> timeoutResultSupplier);

    // 核心方法
    public boolean setResult(T result);
    public boolean setErrorResult(Object result);
    public void onTimeout(Runnable callback);
    public void onCompletion(Runnable callback);
    public void onError(Consumer<Throwable> callback);
}
```

**典型使用场景**：
- 消息队列回调（如 Kafka、RabbitMQ）
- 事件驱动架构（Event-Driven）
- 需要在应用管理的线程中设置结果

**示例**：
```java
@RestController
public class AsyncController {

    @GetMapping("/async-deferred")
    public DeferredResult<String> handleAsyncRequest() {
        // 创建 DeferredResult，设置超时为 5 秒
        DeferredResult<String> deferredResult = new DeferredResult<>(5000L);

        // 超时回调
        deferredResult.onTimeout(() -> {
            deferredResult.setErrorResult("Request timeout!");
        });

        // 完成回调（无论成功或失败）
        deferredResult.onCompletion(() -> {
            System.out.println("Request completed");
        });

        // 在另一个线程中设置结果（例如消息队列回调）
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(3000);
                deferredResult.setResult("Async result");
            } catch (Exception e) {
                deferredResult.setErrorResult(e);
            }
        });

        return deferredResult;
    }
}
```

**生命周期**：
1. **创建阶段**: Controller 返回 `DeferredResult` 对象
2. **Servlet 容器线程释放**: 请求进入异步模式，容器线程返回线程池
3. **异步处理**: 应用在其他线程中执行业务逻辑
4. **结果设置**: 调用 `setResult()` 或 `setErrorResult()`
5. **请求分发**: 请求重新分发到 Servlet 容器，完成响应

---

### 2.2 WebAsyncTask<V>

**定义**：
```java
public class WebAsyncTask<V> {
    // 构造函数
    public WebAsyncTask(Callable<V> callable);
    public WebAsyncTask(long timeout, Callable<V> callable);
    public WebAsyncTask(Long timeout, String executorName, Callable<V> callable);
    public WebAsyncTask(Long timeout, AsyncTaskExecutor executor, Callable<V> callable);

    // 配置方法
    public void onTimeout(Callable<V> callback);
    public void onCompletion(Runnable callback);
    public void onError(Callable<V> callback);
}
```

**典型使用场景**：
- 需要自定义超时时间
- 需要指定特定的 TaskExecutor
- 需要超时/错误/完成回调

**示例**：
```java
@RestController
public class WebAsyncTaskController {

    @Autowired
    @Qualifier("asyncTaskExecutor")
    private AsyncTaskExecutor taskExecutor;

    @GetMapping("/async-task")
    public WebAsyncTask<String> handleWithWebAsyncTask() {
        Callable<String> callable = () -> {
            Thread.sleep(2000);
            return "WebAsyncTask result";
        };

        // 创建 WebAsyncTask，设置超时为 5 秒，指定线程池
        WebAsyncTask<String> task = new WebAsyncTask<>(5000L, taskExecutor, callable);

        // 超时回调
        task.onTimeout(() -> {
            System.out.println("Request timed out");
            return "Timeout fallback result";
        });

        // 错误回调
        task.onError(() -> {
            System.out.println("Error occurred");
            return "Error fallback result";
        });

        // 完成回调
        task.onCompletion(() -> {
            System.out.println("Request completed");
        });

        return task;
    }
}
```

**关键特性**：
- **封装 Callable + 超时 + Executor**: 一站式配置
- **Spring 托管执行**: 使用 Spring 的 TaskExecutor 执行 Callable
- **生命周期回调**: 支持 onTimeout/onError/onCompletion

---

### 2.3 Callable<V>

**定义**：
```java
@FunctionalInterface
public interface Callable<V> {
    V call() throws Exception;
}
```

**典型使用场景**：
- 简单的异步任务，不需要自定义超时和线程池
- Spring 自动使用默认的 TaskExecutor 执行

**示例**：
```java
@RestController
public class CallableController {

    @GetMapping("/async-callable")
    public Callable<String> handleWithCallable() {
        return () -> {
            Thread.sleep(2000);
            return "Callable result";
        };
    }
}
```

**内部机制**：
- Spring 自动将 `Callable` 包装为 `WebAsyncTask`
- 使用默认的 `TaskExecutor`（可通过 `configureAsyncSupport()` 自定义）
- 使用默认超时（可通过 `configureAsyncSupport()` 自定义）

---

## 3. 超时控制与配置

### 3.1 全局超时配置

**方式 1: Java Config**
```java
@Configuration
@EnableWebMvc
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        // 设置默认超时时间（毫秒）
        configurer.setDefaultTimeout(30000L); // 30 秒

        // 设置默认的 TaskExecutor
        configurer.setTaskExecutor(asyncTaskExecutor());
    }

    @Bean
    public AsyncTaskExecutor asyncTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-");
        executor.initialize();
        return executor;
    }
}
```

**方式 2: application.properties/yml**
```yaml
# Spring Boot 3.x 配置
spring:
  mvc:
    async:
      request-timeout: 30000  # 默认超时 30 秒
```

### 3.2 单个请求超时配置

**DeferredResult 超时**：
```java
// 构造函数设置超时
DeferredResult<String> result = new DeferredResult<>(5000L);

// 超时后返回默认值
DeferredResult<String> result = new DeferredResult<>(5000L, "Timeout fallback");

// 超时后通过 Supplier 生成默认值
DeferredResult<String> result = new DeferredResult<>(5000L, () -> "Timeout fallback");
```

**WebAsyncTask 超时**：
```java
// 构造函数设置超时
WebAsyncTask<String> task = new WebAsyncTask<>(5000L, callable);

// 超时回调
task.onTimeout(() -> {
    // 返回降级结果
    return "Timeout fallback";
});
```

### 3.3 超时处理最佳实践

**超时异常处理**：
```java
@RestControllerAdvice
public class AsyncExceptionHandler {

    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public ResponseEntity<String> handleAsyncTimeout(AsyncRequestTimeoutException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("Request timeout, please retry later");
    }
}
```

---

## 4. 拦截器与生命周期管理

### 4.1 CallableProcessingInterceptor

**接口定义**：
```java
public interface CallableProcessingInterceptor {

    // 在 Callable 开始执行前调用（主线程）
    default <T> void beforeConcurrentHandling(NativeWebRequest request,
                                               Callable<T> task) throws Exception {}

    // 在 Callable 开始执行前调用（工作线程）
    default <T> void preProcess(NativeWebRequest request,
                                 Callable<T> task) throws Exception {}

    // 在 Callable 执行后调用（工作线程）
    default <T> void postProcess(NativeWebRequest request,
                                  Callable<T> task,
                                  Object concurrentResult) throws Exception {}

    // 超时时调用（容器线程）
    default <T> Object handleTimeout(NativeWebRequest request,
                                      Callable<T> task) throws Exception {
        return RESULT_NONE; // 返回 RESULT_NONE 允许其他拦截器处理
    }

    // 发生错误时调用（工作线程）
    default <T> Object handleError(NativeWebRequest request,
                                    Callable<T> task,
                                    Throwable t) throws Exception {
        return RESULT_NONE;
    }

    // 异步处理完成后调用（容器线程）
    default <T> void afterCompletion(NativeWebRequest request,
                                      Callable<T> task) throws Exception {}
}
```

**示例：MDC 上下文传播拦截器**：
```java
public class MdcCallableInterceptor implements CallableProcessingInterceptor {

    @Override
    public <T> void beforeConcurrentHandling(NativeWebRequest request,
                                               Callable<T> task) {
        // 在主线程中捕获 MDC 上下文
        Map<String, String> mdcContext = MDC.getCopyOfContextMap();
        request.setAttribute("mdcContext", mdcContext,
                            RequestAttributes.SCOPE_REQUEST);
    }

    @Override
    public <T> void preProcess(NativeWebRequest request, Callable<T> task) {
        // 在工作线程中恢复 MDC 上下文
        Map<String, String> mdcContext =
            (Map<String, String>) request.getAttribute("mdcContext",
                                    RequestAttributes.SCOPE_REQUEST);
        if (mdcContext != null) {
            MDC.setContextMap(mdcContext);
        }
    }

    @Override
    public <T> void afterCompletion(NativeWebRequest request, Callable<T> task) {
        // 清理 MDC 上下文
        MDC.clear();
    }
}
```

**注册拦截器**：
```java
@Configuration
@EnableWebMvc
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        // 注册 Callable 拦截器
        configurer.registerCallableInterceptors(new MdcCallableInterceptor());
    }
}
```

### 4.2 DeferredResultProcessingInterceptor

**接口定义**：
```java
public interface DeferredResultProcessingInterceptor {

    // 在并发处理开始前调用（主线程）
    default <T> void beforeConcurrentHandling(NativeWebRequest request,
                                               DeferredResult<T> deferredResult)
                                               throws Exception {}

    // 在 DeferredResult 设置结果后调用（设置结果的线程）
    default <T> void preProcess(NativeWebRequest request,
                                 DeferredResult<T> deferredResult)
                                 throws Exception {}

    // 在结果设置后调用（设置结果的线程）
    default <T> void postProcess(NativeWebRequest request,
                                  DeferredResult<T> deferredResult,
                                  Object concurrentResult) throws Exception {}

    // 超时时调用（容器线程）
    default <T> boolean handleTimeout(NativeWebRequest request,
                                       DeferredResult<T> deferredResult)
                                       throws Exception {
        return true; // 返回 true 允许其他拦截器处理
    }

    // 发生错误时调用（设置错误的线程）
    default <T> boolean handleError(NativeWebRequest request,
                                     DeferredResult<T> deferredResult,
                                     Throwable t) throws Exception {
        return true;
    }

    // 异步处理完成后调用（容器线程）
    default <T> void afterCompletion(NativeWebRequest request,
                                      DeferredResult<T> deferredResult)
                                      throws Exception {}
}
```

**内置拦截器**：
- **TimeoutDeferredResultProcessingInterceptor**: 超时时发送 503 (SERVICE_UNAVAILABLE) 响应
- **TimeoutCallableProcessingInterceptor**: 超时时发送 503 (SERVICE_UNAVAILABLE) 响应

**注册拦截器**：
```java
@Configuration
@EnableWebMvc
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        // 注册 DeferredResult 拦截器
        configurer.registerDeferredResultInterceptors(
            new TimeoutDeferredResultProcessingInterceptor()
        );
    }
}
```

---

## 5. 异常处理与容错机制

### 5.1 异步异常处理策略

**方式 1: 回调中处理**
```java
@GetMapping("/async-error-callback")
public DeferredResult<String> handleWithCallback() {
    DeferredResult<String> result = new DeferredResult<>();

    result.onError(throwable -> {
        System.err.println("Error: " + throwable.getMessage());
        result.setResult("Error fallback result");
    });

    CompletableFuture.runAsync(() -> {
        try {
            // 业务逻辑可能抛出异常
            throw new RuntimeException("Business error");
        } catch (Exception e) {
            result.setErrorResult(e);
        }
    });

    return result;
}
```

**方式 2: 全局异常处理器**
```java
@RestControllerAdvice
public class AsyncExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleException(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error: " + ex.getMessage());
    }

    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public ResponseEntity<String> handleTimeout(AsyncRequestTimeoutException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("Request timeout");
    }
}
```

**方式 3: WebAsyncTask 错误回调**
```java
@GetMapping("/async-task-error")
public WebAsyncTask<String> handleWithErrorCallback() {
    Callable<String> callable = () -> {
        throw new RuntimeException("Task error");
    };

    WebAsyncTask<String> task = new WebAsyncTask<>(5000L, callable);

    task.onError(() -> {
        return "Error fallback result";
    });

    return task;
}
```

### 5.2 容错降级策略

**降级模式**：
```java
@RestController
public class ResilientAsyncController {

    private final FallbackService fallbackService;

    @GetMapping("/async-resilient")
    public DeferredResult<String> handleWithResilience() {
        DeferredResult<String> result = new DeferredResult<>(3000L);

        // 超时降级
        result.onTimeout(() -> {
            result.setResult(fallbackService.getFallbackData());
        });

        // 错误降级
        result.onError(throwable -> {
            result.setResult(fallbackService.getFallbackData());
        });

        // 异步调用主服务
        CompletableFuture.supplyAsync(() -> {
            try {
                return primaryService.getData();
            } catch (Exception e) {
                return fallbackService.getFallbackData();
            }
        }).thenAccept(result::setResult);

        return result;
    }
}
```

---

## 6. 上下文传播（MDC/ThreadLocal）

### 6.1 问题场景

**问题**：异步处理时，ThreadLocal 变量（如 MDC、Spring Security Context）不会自动传播到工作线程。

**解决方案**：使用 Micrometer Context Propagation 或自定义拦截器。

### 6.2 Micrometer Context Propagation（推荐）

**依赖**：
```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>context-propagation</artifactId>
</dependency>
```

**使用示例**：
```java
import io.micrometer.context.ContextSnapshot;

// 在主线程中捕获上下文
ContextSnapshot snapshot = ContextSnapshot.captureAll();

// 在工作线程中恢复上下文
CompletableFuture.runAsync(() -> {
    try (ContextSnapshot.Scope scope = snapshot.setThreadLocals()) {
        // 此处可以访问 MDC、Security Context 等
        String requestId = MDC.get("requestId");
        // ... 业务逻辑
    }
});
```

### 6.3 自定义 TaskDecorator

**装饰器实现**：
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

**配置 TaskExecutor**：
```java
@Bean
public AsyncTaskExecutor asyncTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(10);
    executor.setMaxPoolSize(50);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("async-");
    executor.setTaskDecorator(new MdcTaskDecorator()); // 注入装饰器
    executor.initialize();
    return executor;
}
```

---

## 7. 性能监控与指标

### 7.1 关键指标

| 指标 | 说明 | 监控方式 |
|------|------|---------|
| **异步请求数** | 当前正在处理的异步请求数量 | Micrometer Gauge |
| **异步请求成功率** | 成功完成的异步请求比例 | Micrometer Counter |
| **异步请求超时率** | 超时的异步请求比例 | Micrometer Counter |
| **异步处理时长** | 异步请求的处理时间分布（P50/P95/P99） | Micrometer Timer |
| **线程池使用率** | TaskExecutor 线程池的使用情况 | ThreadPoolTaskExecutor Metrics |

### 7.2 Micrometer 监控示例

**自定义拦截器统计指标**：
```java
@Component
public class MetricsCallableInterceptor implements CallableProcessingInterceptor {

    private final MeterRegistry meterRegistry;
    private final Counter successCounter;
    private final Counter timeoutCounter;
    private final Counter errorCounter;

    public MetricsCallableInterceptor(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.successCounter = Counter.builder("async.requests.success")
                .description("Successful async requests")
                .register(meterRegistry);
        this.timeoutCounter = Counter.builder("async.requests.timeout")
                .description("Timeout async requests")
                .register(meterRegistry);
        this.errorCounter = Counter.builder("async.requests.error")
                .description("Error async requests")
                .register(meterRegistry);
    }

    @Override
    public <T> void beforeConcurrentHandling(NativeWebRequest request,
                                               Callable<T> task) {
        request.setAttribute("startTime", System.currentTimeMillis(),
                            RequestAttributes.SCOPE_REQUEST);
    }

    @Override
    public <T> void afterCompletion(NativeWebRequest request, Callable<T> task) {
        Long startTime = (Long) request.getAttribute("startTime",
                                        RequestAttributes.SCOPE_REQUEST);
        if (startTime != null) {
            long duration = System.currentTimeMillis() - startTime;
            Timer.builder("async.requests.duration")
                    .description("Async request duration")
                    .register(meterRegistry)
                    .record(duration, TimeUnit.MILLISECONDS);
        }
        successCounter.increment();
    }

    @Override
    public <T> Object handleTimeout(NativeWebRequest request, Callable<T> task) {
        timeoutCounter.increment();
        return CallableProcessingInterceptor.RESULT_NONE;
    }

    @Override
    public <T> Object handleError(NativeWebRequest request,
                                   Callable<T> task,
                                   Throwable t) {
        errorCounter.increment();
        return CallableProcessingInterceptor.RESULT_NONE;
    }
}
```

---

## 8. 测试异步端点

### 8.1 MockMvc 异步测试

**示例**：
```java
@SpringBootTest
@AutoConfigureMockMvc
public class AsyncControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testAsyncEndpoint() throws Exception {
        // 发起异步请求
        MvcResult mvcResult = mockMvc.perform(get("/async-deferred"))
                .andExpect(request().asyncStarted())
                .andReturn();

        // 等待异步处理完成（最多 5 秒）
        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().string("Async result"));
    }

    @Test
    public void testAsyncTimeout() throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/async-timeout"))
                .andExpect(request().asyncStarted())
                .andReturn();

        // 模拟超时
        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isServiceUnavailable());
    }
}
```

### 8.2 Awaitility 测试

**依赖**：
```xml
<dependency>
    <groupId>org.awaitility</groupId>
    <artifactId>awaitility</artifactId>
    <scope>test</scope>
</dependency>
```

**示例**：
```java
@Test
public void testAsyncWithAwaitility() throws Exception {
    DeferredResult<String> result = controller.handleAsyncRequest();

    // 等待异步结果（最多 5 秒）
    await().atMost(5, TimeUnit.SECONDS)
            .until(() -> result.hasResult());

    assertThat(result.getResult()).isEqualTo("Async result");
}
```

---

## 9. 常见陷阱与最佳实践

### 9.1 常见陷阱

| 陷阱 | 描述 | 解决方案 |
|------|------|---------|
| **忘记设置超时** | 异步请求无限期等待 | 始终设置合理的超时时间 |
| **ThreadLocal 丢失** | MDC、Security Context 等不传播 | 使用 TaskDecorator 或 Context Propagation |
| **异常未处理** | 异步异常被吞掉 | 使用 onError 回调或全局异常处理器 |
| **资源泄漏** | DeferredResult 从未设置结果 | 确保在超时/错误回调中设置结果 |
| **线程池耗尽** | TaskExecutor 配置不当 | 合理配置核心线程数、最大线程数、队列容量 |

### 9.2 最佳实践

1. **始终设置超时**：
   - 全局默认超时：30-60 秒
   - 单个请求超时：根据业务场景调整

2. **正确处理异常**：
   - 使用 `onError` 回调
   - 配置全局异常处理器
   - 避免吞掉异常

3. **上下文传播**：
   - 使用 Micrometer Context Propagation
   - 或自定义 TaskDecorator

4. **监控与告警**：
   - 监控异步请求成功率、超时率
   - 监控线程池使用情况
   - 设置合理的告警阈值

5. **测试覆盖**：
   - 测试正常流程
   - 测试超时场景
   - 测试异常场景

6. **线程池配置**：
   - 根据业务类型（CPU 密集 vs IO 密集）配置
   - 监控线程池使用情况，动态调整

---

## 10. 版本兼容性说明

### 10.1 Spring Boot 3.3.x (Spring Framework 6.2.x)

**支持的特性**：
- ✅ Callable、DeferredResult、WebAsyncTask
- ✅ CallableProcessingInterceptor、DeferredResultProcessingInterceptor
- ✅ AsyncSupportConfigurer
- ✅ AsyncRequestTimeoutException
- ✅ Micrometer Context Propagation 集成

**配置方式**：
```yaml
# application.yml
spring:
  mvc:
    async:
      request-timeout: 30000  # 默认超时 30 秒
```

**Java Config**：
```java
@Configuration
@EnableWebMvc
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setDefaultTimeout(30000L);
        configurer.setTaskExecutor(asyncTaskExecutor());
        configurer.registerCallableInterceptors(/* ... */);
        configurer.registerDeferredResultInterceptors(/* ... */);
    }
}
```

---

## 11. 参考资源

### 11.1 官方文档

- **Spring Framework 6.2.11 文档**: https://docs.spring.io/spring-framework/docs/6.2.11/reference/html/web.html#mvc-ann-async
- **Spring Boot 3.3.x 文档**: https://docs.spring.io/spring-boot/docs/3.3.x/reference/html/
- **Javadoc API**: https://docs.spring.io/spring-framework/docs/6.2.11/javadoc-api/

### 11.2 核心类与接口

**异步返回值类型**：
- `org.springframework.web.context.request.async.DeferredResult<T>`
- `org.springframework.web.context.request.async.WebAsyncTask<V>`
- `java.util.concurrent.Callable<V>`

**拦截器接口**：
- `org.springframework.web.context.request.async.CallableProcessingInterceptor`
- `org.springframework.web.context.request.async.DeferredResultProcessingInterceptor`

**配置类**：
- `org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer`
- `org.springframework.web.servlet.config.annotation.WebMvcConfigurer`

**异常类**：
- `org.springframework.web.context.request.async.AsyncRequestTimeoutException`

**任务执行器**：
- `org.springframework.core.task.AsyncTaskExecutor`
- `org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor`

---

## 12. 总结

### 12.1 核心要点

1. **三种异步模式**：Callable（简单）、DeferredResult（灵活）、WebAsyncTask（完整控制）
2. **超时控制**：全局默认 + 单个请求自定义
3. **拦截器**：CallableProcessingInterceptor、DeferredResultProcessingInterceptor
4. **上下文传播**：Micrometer Context Propagation 或自定义 TaskDecorator
5. **异常处理**：onError 回调 + 全局异常处理器
6. **性能监控**：Micrometer 指标 + 线程池监控

### 12.2 适用场景对比

| 场景 | 推荐模式 | 理由 |
|------|---------|------|
| 简单异步任务（数据库查询） | Callable | 代码简洁，Spring 自动管理 |
| 消息队列回调 | DeferredResult | 应用自己管理线程 |
| 需要自定义超时/线程池 | WebAsyncTask | 完整的配置和回调支持 |
| 需要上下文传播（MDC） | 任意 + TaskDecorator | 确保上下文正确传播 |
| 需要容错降级 | DeferredResult | onTimeout/onError 回调 |

---

**最后更新**: 2025-10-21
**适用版本**: Spring Boot 3.3.x (Spring Framework 6.2.11)
