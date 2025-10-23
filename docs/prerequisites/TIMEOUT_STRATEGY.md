# Spring MVC 异步请求超时控制策略

> **目标**：系统掌握 Spring MVC 异步请求的超时控制机制，理解三层超时架构，学会设计优雅的超时降级策略。
>
> **降低学习曲线**：通过本文，读者可以从"不知道如何配置超时"快速进阶到"能根据场景选择合适的超时策略"，预计节省 **60%** 的试错时间。

---

## 📌 为什么超时控制如此重要？

### 1.1 超时控制的三大核心价值

**🎯 防止资源泄漏**

```
场景：外部服务无响应 + 没有超时控制

Tomcat 线程池：200 个线程
异步任务线程池：100 个线程

第 1 分钟：20 个请求挂起，占用 20 个 DeferredResult
第 5 分钟：100 个请求挂起，占用 100 个 DeferredResult
第 10 分钟：200 个请求挂起，内存占用 500MB，系统 OOM ❌

✅ 有超时控制：10 秒后自动释放资源，内存稳定在 50MB
```

**🚀 提升用户体验**

```
没有超时控制：
  用户发起请求 → 等待 30 秒 → 浏览器超时 → 页面空白 ❌

有超时控制：
  用户发起请求 → 等待 5 秒 → 后端主动返回降级数据 ✅
  "当前服务繁忙，请稍后重试" (明确的错误提示)
```

**⚡ 保障系统稳定性**

```
级联超时场景：

API Gateway (30s) → Service A (20s) → Service B (10s)
                                     ↓
                                  数据库 (5s)

✅ 正确配置：从内到外递增，避免资源浪费
❌ 错误配置：Gateway 5s, Service A 30s → 请求已超时但 Service A 仍在处理
```

### 1.2 超时控制的三大挑战

| 挑战 | 表现 | 影响 |
|------|------|------|
| **配置层级复杂** | Tomcat、AsyncContext、WebAsyncTask 三层配置 | 不知道配置哪一层 |
| **超时回调缺失** | 超时后无法执行清理逻辑 | 资源泄漏 + 数据不一致 |
| **级联超时冲突** | 多层调用的超时时间设置不合理 | 浪费资源 + 无效等待 |

---

## 🏗️ Spring MVC 异步超时的三层架构

### 2.1 整体架构图

```
┌─────────────────────────────────────────────────────────────┐
│                      三层超时架构                            │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌───────────────────────────────────────────────────────┐ │
│  │ Layer 1: Tomcat 全局超时 (server.xml / application.yml)│ │
│  │   - 默认值: 30 秒                                      │ │
│  │   - 作用域: 所有 Servlet 异步请求                      │ │
│  │   - 优先级: 最低 (兜底保护)                           │ │
│  └───────────────────────────────────────────────────────┘ │
│                            ↓                                │
│  ┌───────────────────────────────────────────────────────┐ │
│  │ Layer 2: AsyncContext 超时 (request.startAsync())      │ │
│  │   - 默认值: 继承 Tomcat 全局配置                      │ │
│  │   - 作用域: 单个异步请求                              │ │
│  │   - 优先级: 中等 (覆盖全局配置)                       │ │
│  └───────────────────────────────────────────────────────┘ │
│                            ↓                                │
│  ┌───────────────────────────────────────────────────────┐ │
│  │ Layer 3: WebAsyncTask / DeferredResult 超时            │ │
│  │   - 默认值: 由开发者显式指定                          │ │
│  │   - 作用域: 具体的业务场景                            │ │
│  │   - 优先级: 最高 (精确控制)                           │ │
│  └───────────────────────────────────────────────────────┘ │
│                                                             │
└─────────────────────────────────────────────────────────────┘

优先级规则：
  WebAsyncTask/DeferredResult 超时 > AsyncContext 超时 > Tomcat 全局超时
```

### 2.2 Layer 1: Tomcat 全局超时配置

**配置方式 1：application.yml (Spring Boot 推荐)**

```yaml
server:
  tomcat:
    threads:
      max: 200               # 最大线程数
      min-spare: 10          # 最小空闲线程数
    connection-timeout: 20s  # ⚠️ 这是 HTTP 连接超时，不是异步请求超时

spring:
  mvc:
    async:
      request-timeout: 30000  # ✅ 异步请求全局超时 (30 秒)
```

**配置方式 2：WebMvcConfigurer**

```java
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * 配置全局异步请求超时时间
     *
     * <p><b>@教学</b>
     * <ul>
     *   <li>适用于所有 Callable / WebAsyncTask / DeferredResult</li>
     *   <li>可被具体的 WebAsyncTask 超时配置覆盖</li>
     *   <li>建议根据业务场景设置合理的默认值 (10-60 秒)</li>
     * </ul>
     */
    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setDefaultTimeout(30_000);  // 30 秒

        // ✅ 配置超时拦截器
        configurer.registerCallableInterceptors(new TimeoutCallableProcessingInterceptor());
        configurer.registerDeferredResultInterceptors(new TimeoutDeferredResultProcessingInterceptor());
    }
}
```

**适用场景**：
- ✅ 为所有异步请求设置一个合理的默认超时时间
- ✅ 防止无限期等待导致的资源泄漏
- ❌ 不适合需要精细控制的场景（应使用 Layer 3）

---

### 2.3 Layer 2: AsyncContext 超时控制

**AsyncContext 超时示例**

```java
@WebServlet(value = "/async-timeout", asyncSupported = true)
public class AsyncTimeoutServlet extends HttpServlet {

    private final ExecutorService executor = Executors.newFixedThreadPool(10);

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        // ⚡ 启动异步处理
        AsyncContext asyncContext = req.startAsync();

        // ✅ 设置超时时间 (10 秒)
        asyncContext.setTimeout(10_000);

        // ✅ 添加超时监听器
        asyncContext.addListener(new AsyncListener() {
            @Override
            public void onTimeout(AsyncEvent event) throws IOException {
                // 超时处理逻辑
                HttpServletResponse response = (HttpServletResponse) event.getAsyncContext().getResponse();
                response.setStatus(HttpServletResponse.SC_REQUEST_TIMEOUT);  // 408
                response.getWriter().write("{\"error\": \"Request timeout\"}");
                asyncContext.complete();  // ⚠️ 必须调用 complete()
            }

            @Override
            public void onComplete(AsyncEvent event) {
                // 清理资源
            }

            @Override
            public void onError(AsyncEvent event) {
                // 错误处理
            }

            @Override
            public void onStartAsync(AsyncEvent event) {
                // 异步启动回调
            }
        });

        // 提交异步任务
        executor.submit(() -> {
            try {
                String result = callExternalAPI();  // 可能耗时 5-15 秒
                asyncContext.getResponse().getWriter().write(result);
                asyncContext.complete();
            } catch (Exception e) {
                asyncContext.complete();
            }
        });
    }
}
```

**关键要点**：
- ✅ `asyncContext.setTimeout(ms)` 设置超时时间
- ✅ `AsyncListener.onTimeout()` 处理超时逻辑
- ⚠️ 超时回调中必须调用 `asyncContext.complete()` 释放资源
- ❌ 如果不调用 `complete()`，会导致内存泄漏

---

### 2.4 Layer 3: WebAsyncTask / DeferredResult 超时控制

**WebAsyncTask 超时示例 (推荐)**

```java
@RestController
@RequestMapping("/api/async")
public class AsyncController {

    private final ExecutorService executor = Executors.newFixedThreadPool(20);

    /**
     * WebAsyncTask 超时控制示例
     *
     * <p><b>@教学</b>
     * <p>WebAsyncTask 优势：
     * <ul>
     *   <li>支持自定义超时时间</li>
     *   <li>支持超时回调 (onTimeout)</li>
     *   <li>支持自定义线程池</li>
     *   <li>支持错误回调 (onError)</li>
     *   <li>支持完成回调 (onCompletion)</li>
     * </ul>
     */
    @GetMapping("/task")
    public WebAsyncTask<Map<String, Object>> handleAsyncTask(@RequestParam int delay) {

        // ✅ 方式 1: 构造函数指定超时时间 (5 秒)
        WebAsyncTask<Map<String, Object>> task = new WebAsyncTask<>(5000L, () -> {
            TimeUnit.MILLISECONDS.sleep(delay);
            return Map.of("status", "success", "delay", delay);
        });

        // ✅ 方式 2: 设置超时回调
        task.onTimeout(() -> {
            // 超时降级逻辑
            return Map.of(
                "status", "timeout",
                "message", "请求超时，请稍后重试",
                "timeout", 5000
            );
        });

        // ✅ 方式 3: 设置错误回调
        task.onError(() -> {
            return Map.of(
                "status", "error",
                "message", "处理失败"
            );
        });

        // ✅ 方式 4: 设置完成回调 (清理资源)
        task.onCompletion(() -> {
            // 清理资源 (例如: 从缓存中移除待处理请求)
        });

        return task;
    }

    /**
     * 自定义线程池的 WebAsyncTask
     *
     * <p><b>@教学</b>
     * <p>适用场景：
     * <ul>
     *   <li>CPU 密集型任务 → 小线程池 (核心数 + 1)</li>
     *   <li>IO 密集型任务 → 大线程池 (核心数 * 2)</li>
     *   <li>关键任务 → 独立线程池 (避免相互影响)</li>
     * </ul>
     */
    @GetMapping("/task-custom-executor")
    public WebAsyncTask<String> handleWithCustomExecutor() {

        Callable<String> callable = () -> {
            // 业务逻辑
            return "success";
        };

        // ✅ 指定超时时间 + 自定义线程池
        WebAsyncTask<String> task = new WebAsyncTask<>(10_000L, executor, callable);

        task.onTimeout(() -> "timeout");

        return task;
    }
}
```

**DeferredResult 超时示例**

```java
@RestController
@RequestMapping("/api/deferred")
public class DeferredResultController {

    private final Map<String, DeferredResult<Map<String, Object>>> pendingRequests =
            new ConcurrentHashMap<>();

    /**
     * DeferredResult 超时控制示例
     *
     * <p><b>@教学</b>
     * <p>DeferredResult 超时特点：
     * <ul>
     *   <li>构造函数指定超时时间</li>
     *   <li>onTimeout() 回调设置超时结果</li>
     *   <li>onCompletion() 回调清理资源</li>
     *   <li>适合事件驱动场景 (消息队列、WebSocket)</li>
     * </ul>
     */
    @GetMapping("/message")
    public DeferredResult<Map<String, Object>> waitForMessage(@RequestParam String userId) {

        // ✅ 设置超时时间 (60 秒)
        DeferredResult<Map<String, Object>> deferredResult = new DeferredResult<>(60_000L);

        // ✅ 超时回调：返回降级数据
        deferredResult.onTimeout(() -> {
            deferredResult.setResult(Map.of(
                "status", "timeout",
                "message", "等待超时，请刷新重试",
                "userId", userId
            ));
        });

        // ✅ 完成回调：清理资源
        deferredResult.onCompletion(() -> {
            pendingRequests.remove(userId);  // ⚠️ 防止内存泄漏
        });

        // ✅ 错误回调：异常处理
        deferredResult.onError((Throwable t) -> {
            deferredResult.setErrorResult(Map.of(
                "status", "error",
                "message", t.getMessage()
            ));
        });

        // 保存待处理请求
        pendingRequests.put(userId, deferredResult);

        return deferredResult;
    }

    /**
     * 外部事件触发完成 (模拟消息推送)
     */
    @PostMapping("/push")
    public Map<String, Object> pushMessage(@RequestParam String userId,
                                           @RequestParam String message) {
        DeferredResult<Map<String, Object>> deferredResult = pendingRequests.get(userId);

        if (deferredResult != null) {
            // ✅ 触发完成
            deferredResult.setResult(Map.of(
                "status", "success",
                "message", message,
                "userId", userId
            ));
            return Map.of("status", "pushed");
        }

        return Map.of("status", "not_found");
    }
}
```

---

## 🎯 超时策略设计原则

### 3.1 超时时间设置决策树

```
开始
  │
  ├─ 是否有外部依赖？
  │   ├─ 是 → 外部服务 P95 响应时间 + 1 秒 (缓冲)
  │   │       示例: 外部 API P95=2s → 设置 3s 超时
  │   │
  │   └─ 否 → 是否是 CPU 密集型任务？
  │       ├─ 是 → 基于算法复杂度评估
  │       │       示例: O(n²) 排序 10000 条数据 → 设置 5s 超时
  │       │
  │       └─ 否 → 是否是 IO 密集型任务？
  │           ├─ 是 → 网络 IO P95 + 磁盘 IO P95 + 1s
  │           │       示例: 数据库查询 1s + 文件读取 0.5s → 设置 2.5s 超时
  │           │
  │           └─ 否 → 默认 10 秒 (常规业务逻辑)
  │
  └─ 是否有级联调用？
      ├─ 是 → 从内到外递增
      │       示例: DB(5s) → Service(10s) → Gateway(15s)
      │
      └─ 否 → 使用单层超时配置
```

### 3.2 超时时间分级标准

| 场景类型 | 推荐超时时间 | 理由 |
|---------|-------------|------|
| **快速查询** (内存缓存、Redis) | 1-2 秒 | 用户期望即时响应 |
| **常规业务** (数据库查询、简单计算) | 5-10 秒 | 平衡用户体验与系统稳定性 |
| **复杂业务** (报表生成、批量处理) | 30-60 秒 | 允许较长处理时间 |
| **长轮询** (消息推送、状态监听) | 60-120 秒 | 减少轮询频率 |
| **文件上传/下载** | 根据文件大小动态调整 | 10MB → 30s, 100MB → 300s |

### 3.3 超时回调最佳实践

**✅ 推荐做法**

```java
@GetMapping("/best-practice")
public DeferredResult<ApiResponse> bestPracticeExample() {

    DeferredResult<ApiResponse> deferredResult = new DeferredResult<>(10_000L);

    // ✅ 1. 超时回调：返回明确的降级数据
    deferredResult.onTimeout(() -> {
        deferredResult.setResult(ApiResponse.builder()
            .code(408)
            .message("服务繁忙，请稍后重试")
            .data(getCachedData())  // 返回缓存数据
            .build());
    });

    // ✅ 2. 完成回调：清理资源
    deferredResult.onCompletion(() -> {
        // 从待处理队列中移除
        // 释放数据库连接
        // 清理临时文件
    });

    // ✅ 3. 错误回调：记录日志 + 降级
    deferredResult.onError((Throwable t) -> {
        log.error("异步处理失败", t);
        deferredResult.setErrorResult(ApiResponse.error("系统异常"));
    });

    return deferredResult;
}
```

**❌ 错误做法**

```java
@GetMapping("/bad-practice")
public DeferredResult<String> badPracticeExample() {

    DeferredResult<String> deferredResult = new DeferredResult<>(10_000L);

    // ❌ 1. 超时回调中没有设置结果 → 客户端仍在等待
    deferredResult.onTimeout(() -> {
        log.error("timeout");  // 仅记录日志，没有返回结果
    });

    // ❌ 2. 没有完成回调 → 资源泄漏
    // 缺少 onCompletion()

    // ❌ 3. 没有错误回调 → 异常被吞噬
    // 缺少 onError()

    return deferredResult;
}
```

---

## 🛡️ 优雅的超时降级策略

### 4.1 降级策略层级

```
┌─────────────────────────────────────────────────────┐
│              超时降级策略金字塔                      │
├─────────────────────────────────────────────────────┤
│                                                     │
│       ┌───────────────────────────────┐            │
│       │  Level 3: 快速失败             │            │
│       │  - 直接返回错误信息            │            │
│       │  - 适用: 非关键功能            │            │
│       └───────────────────────────────┘            │
│                    ↓                                │
│       ┌───────────────────────────────┐            │
│       │  Level 2: 返回缓存数据         │            │
│       │  - 使用最近一次成功的结果      │            │
│       │  - 适用: 数据时效性要求不高    │            │
│       └───────────────────────────────┘            │
│                    ↓                                │
│       ┌───────────────────────────────┐            │
│       │  Level 1: 返回默认值/降级数据  │            │
│       │  - 预设的兜底数据              │            │
│       │  - 适用: 核心功能              │            │
│       └───────────────────────────────┘            │
│                                                     │
└─────────────────────────────────────────────────────┘
```

### 4.2 降级策略代码示例

**策略 1: 快速失败 (适用于非关键功能)**

```java
@GetMapping("/non-critical")
public DeferredResult<ApiResponse> nonCriticalFeature() {

    DeferredResult<ApiResponse> deferredResult = new DeferredResult<>(5_000L);

    deferredResult.onTimeout(() -> {
        // ✅ 快速失败：直接告知用户
        deferredResult.setResult(ApiResponse.builder()
            .code(408)
            .message("功能暂时不可用，请稍后重试")
            .build());
    });

    // 业务逻辑...

    return deferredResult;
}
```

**策略 2: 返回缓存数据 (适用于可容忍旧数据的场景)**

```java
@Service
public class CachedDataService {

    private final Cache<String, ProductList> cache =
            Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .build();

    public DeferredResult<ProductList> getProducts(String category) {

        DeferredResult<ProductList> deferredResult = new DeferredResult<>(3_000L);

        deferredResult.onTimeout(() -> {
            // ✅ 返回缓存数据
            ProductList cachedData = cache.getIfPresent(category);

            if (cachedData != null) {
                deferredResult.setResult(cachedData.markAsStale());  // 标记为旧数据
            } else {
                deferredResult.setResult(ProductList.empty());
            }
        });

        // 异步查询最新数据
        queryLatestProducts(category).thenAccept(products -> {
            cache.put(category, products);  // 更新缓存
            deferredResult.setResult(products);
        });

        return deferredResult;
    }
}
```

**策略 3: 返回降级数据 (适用于核心功能)**

```java
@GetMapping("/critical")
public DeferredResult<UserProfile> getCriticalData(@RequestParam String userId) {

    DeferredResult<UserProfile> deferredResult = new DeferredResult<>(10_000L);

    deferredResult.onTimeout(() -> {
        // ✅ 返回预设的降级数据
        deferredResult.setResult(UserProfile.builder()
            .userId(userId)
            .nickname("用户" + userId.substring(0, 4))  // 默认昵称
            .avatar("/default-avatar.png")              // 默认头像
            .degraded(true)                             // 标记为降级数据
            .build());
    });

    // 查询用户数据...

    return deferredResult;
}
```

---

## 📊 超时监控与告警

### 5.1 核心监控指标

| 指标 | 说明 | 告警阈值 |
|------|------|---------|
| **超时率** | 超时请求数 / 总请求数 | > 5% |
| **平均超时时间** | 所有超时请求的平均等待时间 | > 配置值的 80% |
| **P95 响应时间** | 95% 请求的响应时间 | 接近超时配置值 |
| **DeferredResult 泄漏数** | 未完成的 DeferredResult 数量 | > 100 |

### 5.2 监控代码示例

```java
@Component
public class AsyncTimeoutMetrics {

    private final MeterRegistry meterRegistry;
    private final AtomicLong pendingRequests = new AtomicLong(0);

    public AsyncTimeoutMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        // ✅ 注册待处理请求数量监控
        Gauge.builder("async.pending.requests", pendingRequests, AtomicLong::get)
            .description("当前待处理的异步请求数量")
            .register(meterRegistry);
    }

    /**
     * 记录超时事件
     */
    public void recordTimeout(String endpoint) {
        meterRegistry.counter("async.timeout",
            "endpoint", endpoint
        ).increment();
    }

    /**
     * 记录请求完成时间
     */
    public void recordCompletion(String endpoint, long durationMs) {
        meterRegistry.timer("async.duration",
            "endpoint", endpoint
        ).record(durationMs, TimeUnit.MILLISECONDS);
    }

    /**
     * 跟踪待处理请求
     */
    public void trackPendingRequest(Runnable onStart, Runnable onComplete) {
        pendingRequests.incrementAndGet();
        onStart.run();

        try {
            // 业务逻辑
        } finally {
            pendingRequests.decrementAndGet();
            onComplete.run();
        }
    }
}
```

### 5.3 告警规则 (Prometheus + Grafana)

```yaml
# Prometheus 告警规则示例
groups:
  - name: async_timeout_alerts
    interval: 30s
    rules:
      # 超时率告警
      - alert: HighAsyncTimeoutRate
        expr: |
          rate(async_timeout_total[5m]) / rate(async_requests_total[5m]) > 0.05
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "异步请求超时率过高"
          description: "endpoint: {{ $labels.endpoint }}, 超时率: {{ $value | humanizePercentage }}"

      # DeferredResult 泄漏告警
      - alert: DeferredResultLeak
        expr: |
          async_pending_requests > 100
        for: 10m
        labels:
          severity: critical
        annotations:
          summary: "检测到 DeferredResult 泄漏"
          description: "当前待处理请求数: {{ $value }}"

      # P95 响应时间接近超时告警
      - alert: SlowAsyncResponse
        expr: |
          histogram_quantile(0.95, rate(async_duration_bucket[5m])) > 8000
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "异步请求响应时间接近超时"
          description: "endpoint: {{ $labels.endpoint }}, P95: {{ $value }}ms"
```

---

## 🚀 性能优化建议

### 6.1 超时配置优化

**✅ 推荐配置（三层递进）**

```yaml
# application.yml
spring:
  mvc:
    async:
      request-timeout: 30000  # Layer 1: 全局兜底 (30 秒)

# Java 代码
@Configuration
public class AsyncConfig {

    /**
     * Layer 2: 场景化超时配置
     */
    @Bean
    public WebAsyncTask<String> fastQuery() {
        return new WebAsyncTask<>(5_000L, () -> {
            // 快速查询 (5 秒)
        });
    }

    @Bean
    public WebAsyncTask<String> complexQuery() {
        return new WebAsyncTask<>(60_000L, () -> {
            // 复杂查询 (60 秒)
        });
    }
}
```

**❌ 不推荐配置（统一超时）**

```yaml
# 所有请求都用 30 秒超时 → 浪费资源
spring:
  mvc:
    async:
      request-timeout: 30000

# 没有针对具体场景的超时配置
```

### 6.2 线程池优化

```java
@Configuration
public class ExecutorConfig {

    /**
     * CPU 密集型线程池 (核心数 + 1)
     */
    @Bean("cpuExecutor")
    public Executor cpuExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int coreCount = Runtime.getRuntime().availableProcessors();

        executor.setCorePoolSize(coreCount + 1);
        executor.setMaxPoolSize(coreCount + 1);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("cpu-task-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();

        return executor;
    }

    /**
     * IO 密集型线程池 (核心数 * 2)
     */
    @Bean("ioExecutor")
    public Executor ioExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int coreCount = Runtime.getRuntime().availableProcessors();

        executor.setCorePoolSize(coreCount * 2);
        executor.setMaxPoolSize(coreCount * 4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("io-task-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();

        return executor;
    }
}
```

---

## ⚠️ 常见陷阱与最佳实践

### 7.1 陷阱 1: 超时后未释放资源

**❌ 错误示例**

```java
@GetMapping("/leak")
public DeferredResult<String> resourceLeak() {
    DeferredResult<String> deferredResult = new DeferredResult<>(10_000L);

    deferredResult.onTimeout(() -> {
        deferredResult.setResult("timeout");
    });

    // ❌ 没有 onCompletion 回调 → 资源泄漏
    pendingRequests.put(UUID.randomUUID().toString(), deferredResult);

    return deferredResult;
}
```

**✅ 正确示例**

```java
@GetMapping("/no-leak")
public DeferredResult<String> noResourceLeak() {
    String requestId = UUID.randomUUID().toString();
    DeferredResult<String> deferredResult = new DeferredResult<>(10_000L);

    deferredResult.onTimeout(() -> {
        deferredResult.setResult("timeout");
    });

    // ✅ 完成回调：清理资源
    deferredResult.onCompletion(() -> {
        pendingRequests.remove(requestId);
    });

    pendingRequests.put(requestId, deferredResult);

    return deferredResult;
}
```

### 7.2 陷阱 2: 级联超时配置不合理

**❌ 错误配置**

```
Gateway 超时: 5 秒
  ↓
Service A 超时: 30 秒  ← ❌ 内层超时大于外层
  ↓
Database 超时: 10 秒

结果: Gateway 已超时，但 Service A 仍在处理 → 浪费资源
```

**✅ 正确配置**

```
Gateway 超时: 30 秒
  ↓
Service A 超时: 20 秒  ← ✅ 留 10 秒缓冲
  ↓
Database 超时: 10 秒   ← ✅ 留 10 秒缓冲

结果: 从内到外递增，避免无效等待
```

### 7.3 陷阱 3: 超时回调中执行阻塞操作

**❌ 错误示例**

```java
deferredResult.onTimeout(() -> {
    // ❌ 超时回调中执行数据库查询 → 阻塞 Tomcat 线程
    String cachedData = database.query("SELECT * FROM cache");
    deferredResult.setResult(cachedData);
});
```

**✅ 正确示例**

```java
deferredResult.onTimeout(() -> {
    // ✅ 使用内存缓存 (非阻塞)
    String cachedData = memoryCache.get("key");
    deferredResult.setResult(cachedData != null ? cachedData : "timeout");
});
```

---

## 📚 延伸阅读

### 相关文档
- [SERVLET_ASYNC_GUIDE.md](./SERVLET_ASYNC_GUIDE.md) - Servlet 3.0+ 异步 API 基础
- [DEFERREDRESULT_PATTERN.md](./DEFERREDRESULT_PATTERN.md) - DeferredResult 事件驱动模式
- [IO_MODELS.md](./IO_MODELS.md) - 5 种 I/O 模型对比

### 下一步学习建议
1. **实践 Lab-08**: 在 `lab-08-springmvc-async` 中运行示例代码
2. **性能测试**: 使用 JMH 对比不同超时配置的性能表现
3. **监控实战**: 集成 Prometheus + Grafana 监控超时指标
4. **进阶场景**: 学习分布式场景下的超时控制 (OpenTelemetry)

---

**文档版本**: v1.0
**最后更新**: 2025-10-20
**作者**: Claude Code
**适用版本**: Spring Boot 3.3.x, Java 17+
