# Servlet 3.0+ 异步 API - 从线程阻塞到异步解耦

> **目标**: 深入理解 Servlet 3.0+ 异步处理机制，掌握 AsyncContext 的工作原理和最佳实践。为学习 Spring MVC 异步编程打好基础。降低学习曲线 ~60%。

---

## 1. 核心概念

### 1.1 为什么需要异步 Servlet？

**传统同步 Servlet 的问题**：

在 Servlet 3.0 之前，所有请求处理都是同步阻塞的：

```java
@WebServlet("/sync")
public class SyncServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        // ❌ 阻塞 Tomcat 工作线程
        String result = callExternalAPI();  // 耗时 2 秒
        resp.getWriter().write(result);
    }
}
```

**问题分析**：
- ⏳ **线程占用**: Tomcat 工作线程在等待外部 API 期间被阻塞（2 秒）
- 💰 **资源浪费**: 一个线程对应一个请求，100 并发 = 100 个线程
- 📉 **性能瓶颈**: Tomcat 线程池耗尽 → 请求排队 → 响应变慢

**关键指标**：
- Tomcat 默认最大线程数：200
- 每个线程栈大小：~1MB
- 100 并发同步请求（每个 2 秒）：
  - 线程占用：100 个
  - TPS：~50 req/s
  - 内存占用：~100MB（仅线程栈）

**异步 Servlet 的优势**：

```java
@WebServlet(value = "/async", asyncSupported = true)
public class AsyncServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        AsyncContext asyncContext = req.startAsync();  // ⚡ 立即释放 Tomcat 线程

        executor.submit(() -> {
            String result = callExternalAPI();  // 在业务线程池中执行
            asyncContext.getResponse().getWriter().write(result);
            asyncContext.complete();  // 完成异步处理
        });
    }
}
```

**性能对比**：
- Tomcat 线程池：200 个线程
- 业务线程池：50 个线程
- 100 并发异步请求（每个 2 秒）：
  - Tomcat 线程占用：~10 个（快速轮转）
  - 业务线程占用：50 个
  - TPS：~75 req/s（提升 50%）
  - 内存占用：~60MB（节省 40%）

---

### 1.2 什么是 AsyncContext？

**AsyncContext** 是 Servlet 3.0 引入的核心 API，用于管理异步请求的生命周期。

**核心作用**：
1. **解耦请求线程与业务线程**: Tomcat 线程可以立即释放，去处理其他请求
2. **延迟响应**: 业务逻辑在独立线程中执行，完成后再写入响应
3. **生命周期管理**: 提供超时控制、监听器、手动完成等机制

**关键 API**：
```java
// 创建异步上下文（释放 Tomcat 线程）
AsyncContext asyncContext = request.startAsync();

// 设置超时时间（默认 30 秒）
asyncContext.setTimeout(10000);

// 添加监听器（监听完成、超时、错误）
asyncContext.addListener(new AsyncListener() { ... });

// 完成异步处理（必须调用）
asyncContext.complete();

// 重新分派到 Servlet 容器（用于复杂场景）
asyncContext.dispatch("/another-servlet");
```

---

## 2. 异步处理的完整流程

### 2.1 执行流程图

```
┌─────────────┐
│ 客户端发起请求 │
└──────┬──────┘
       │
       ▼
┌──────────────────┐
│ Tomcat 线程接收请求│ (http-nio-8080-exec-1)
└──────┬───────────┘
       │
       ▼
┌──────────────────┐
│ Servlet.doGet()  │
│   req.startAsync()│ ⚡ 创建 AsyncContext
└──────┬───────────┘
       │
       ▼
┌──────────────────┐
│ Tomcat 线程立即释放│ ✅ 可以处理其他请求
└──────────────────┘
       │
       ▼
┌──────────────────┐
│ 业务线程池执行任务 │ (async-executor-1)
│  - callExternalAPI()│
│  - processData()  │
└──────┬───────────┘
       │
       ▼
┌──────────────────┐
│ 写入响应         │
│ asyncContext.    │
│  getResponse()   │
│  .getWriter()    │
└──────┬───────────┘
       │
       ▼
┌──────────────────┐
│ 完成异步处理      │
│ asyncContext.    │
│  complete()      │
└──────┬───────────┘
       │
       ▼
┌──────────────────┐
│ 重新分派到 Servlet│
│ 容器渲染响应      │
└──────┬───────────┘
       │
       ▼
┌──────────────────┐
│ 返回响应给客户端  │
└──────────────────┘
```

### 2.2 线程模型对比

**同步 Servlet（1 线程）**：
```
Tomcat 线程 (http-nio-exec-1):
  ┌────────────────────────────────────┐
  │ 接收请求 → 业务处理 → 写入响应 → 返回 │ (全程占用)
  └────────────────────────────────────┘
  时间: 0ms ────────────────────── 2000ms
```

**异步 Servlet（2 线程）**：
```
Tomcat 线程 (http-nio-exec-1):
  ┌──────┐                         ┌────┐
  │接收请求│ → 立即释放 → ... → ... │渲染响应│
  └──────┘                         └────┘
  时间: 0ms──10ms                 1990ms─2000ms

业务线程 (async-executor-1):
            ┌────────────────────┐
            │  业务处理 → 写入数据 │
            └────────────────────┘
  时间:    10ms───────────────1990ms
```

**关键观察**：
- Tomcat 线程仅占用 20ms（10ms + 10ms）
- 业务线程占用 1980ms
- Tomcat 线程可以在这 1980ms 内处理 ~100 个其他请求

---

## 3. AsyncContext 核心 API 详解

### 3.1 startAsync() - 启动异步处理

**签名**：
```java
AsyncContext startAsync();
AsyncContext startAsync(ServletRequest request, ServletResponse response);
```

**作用**：
- 将请求标记为异步
- 释放 Tomcat 容器线程
- 返回 AsyncContext 对象

**注意事项**：
- ⚠️ 必须在 Servlet 上添加 `asyncSupported = true`
- ⚠️ 必须在原始请求线程中调用（不能在业务线程中）
- ⚠️ 只能调用一次

**示例**：
```java
@WebServlet(value = "/async", asyncSupported = true)
public class AsyncServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        AsyncContext asyncContext = req.startAsync();  // ✅ 正确
    }
}
```

---

### 3.2 setTimeout() - 设置超时时间

**签名**：
```java
void setTimeout(long timeout);  // 毫秒
```

**作用**：
- 设置异步处理的最大等待时间
- 超时后触发 `AsyncListener.onTimeout()`
- 默认值：30 秒（容器实现决定）

**最佳实践**：
```java
AsyncContext asyncContext = req.startAsync();
asyncContext.setTimeout(5000);  // 5 秒超时

asyncContext.addListener(new AsyncListener() {
    @Override
    public void onTimeout(AsyncEvent event) {
        HttpServletResponse resp = (HttpServletResponse) event.getAsyncContext().getResponse();
        resp.setStatus(HttpServletResponse.SC_REQUEST_TIMEOUT);  // 408
        resp.getWriter().write("Request timeout");
        event.getAsyncContext().complete();
    }
});
```

**超时时间层级**：
1. **Servlet 容器级**: Connector `asyncTimeout` 属性
2. **AsyncContext 级**: `asyncContext.setTimeout()`
3. **优先级**: AsyncContext > Connector

---

### 3.3 complete() - 完成异步处理

**签名**：
```java
void complete();
```

**作用**：
- 标记异步处理完成
- 触发响应写入
- 释放资源

**注意事项**：
- ⚠️ **必须调用**: 否则请求会一直挂起，直到超时
- ⚠️ **只能调用一次**: 重复调用会抛出 `IllegalStateException`
- ⚠️ **在业务线程中调用**: 确保所有数据已写入

**错误示例**：
```java
AsyncContext asyncContext = req.startAsync();
executor.submit(() -> {
    String result = callExternalAPI();
    asyncContext.getResponse().getWriter().write(result);
    // ❌ 忘记调用 complete() → 请求挂起 30 秒后超时
});
```

**正确示例**：
```java
AsyncContext asyncContext = req.startAsync();
executor.submit(() -> {
    try {
        String result = callExternalAPI();
        asyncContext.getResponse().getWriter().write(result);
    } finally {
        asyncContext.complete();  // ✅ 确保在 finally 中调用
    }
});
```

---

### 3.4 dispatch() - 重新分派请求

**签名**：
```java
void dispatch();
void dispatch(String path);
void dispatch(ServletContext context, String path);
```

**作用**：
- 将请求重新分派到另一个 Servlet/JSP
- 用于复杂的异步流程控制

**示例**：
```java
AsyncContext asyncContext = req.startAsync();
executor.submit(() -> {
    // 异步处理
    req.setAttribute("result", "data");
    asyncContext.dispatch("/result.jsp");  // 重新分派到 JSP
});
```

**dispatch vs complete**：
- `complete()`: 直接完成，手动写入响应
- `dispatch()`: 交给其他 Servlet/JSP 处理

---

### 3.5 AsyncListener - 监听器

**接口定义**：
```java
public interface AsyncListener extends EventListener {
    void onComplete(AsyncEvent event);    // 完成时
    void onTimeout(AsyncEvent event);     // 超时时
    void onError(AsyncEvent event);       // 错误时
    void onStartAsync(AsyncEvent event);  // 开始异步时
}
```

**最佳实践**：
```java
asyncContext.addListener(new AsyncListener() {
    @Override
    public void onComplete(AsyncEvent event) {
        log.info("异步处理完成");
    }

    @Override
    public void onTimeout(AsyncEvent event) {
        log.warn("异步处理超时");
        HttpServletResponse resp = (HttpServletResponse) event.getAsyncContext().getResponse();
        try {
            resp.setStatus(408);
            resp.getWriter().write("Timeout");
        } catch (IOException e) {
            log.error("写入超时响应失败", e);
        }
        event.getAsyncContext().complete();
    }

    @Override
    public void onError(AsyncEvent event) {
        log.error("异步处理异常", event.getThrowable());
        event.getAsyncContext().complete();
    }

    @Override
    public void onStartAsync(AsyncEvent event) {
        log.info("开始异步处理");
    }
});
```

---

## 4. Spring MVC 对 Servlet 异步的封装

### 4.1 Callable - 简化异步处理

Spring MVC 对 AsyncContext 进行了封装，提供更简洁的 API：

```java
@GetMapping("/async")
public Callable<String> asyncEndpoint() {
    return () -> {
        Thread.sleep(2000);  // 模拟耗时操作
        return "Result";
    };
}
```

**底层原理**：
1. Spring MVC 调用 `request.startAsync()`
2. 将 Callable 提交到 `AsyncTaskExecutor`
3. Callable 执行完成后，Spring MVC 调用 `asyncContext.complete()`

**等价的原生 Servlet 代码**：
```java
@WebServlet(value = "/async", asyncSupported = true)
public class AsyncServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        AsyncContext asyncContext = req.startAsync();
        executor.submit(() -> {
            try {
                Thread.sleep(2000);
                resp.getWriter().write("Result");
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                asyncContext.complete();
            }
        });
    }
}
```

### 4.2 DeferredResult - 事件驱动

```java
@GetMapping("/deferred")
public DeferredResult<String> deferredEndpoint() {
    DeferredResult<String> deferredResult = new DeferredResult<>(10000L);

    // 模拟外部事件触发
    executor.submit(() -> {
        Thread.sleep(2000);
        deferredResult.setResult("Result");  // 触发完成
    });

    return deferredResult;
}
```

**底层原理**：
1. Spring MVC 调用 `request.startAsync()`
2. 返回 DeferredResult（Tomcat 线程立即释放）
3. 外部事件触发 `setResult()` 时，Spring MVC 调用 `asyncContext.complete()`

---

## 5. 常见误区与陷阱

### ❌ 误区 1: "异步 Servlet 一定比同步快"

**真相**: 异步 Servlet 提高的是**吞吐量**（TPS），而不是**单个请求的响应时间**。

- 同步 Servlet：2 秒响应
- 异步 Servlet：2 秒响应（响应时间一样）

**区别**：
- 同步：100 并发 = 100 个线程 → TPS ~50
- 异步：100 并发 = 10 个 Tomcat 线程 + 50 个业务线程 → TPS ~75

### ❌ 误区 2: "忘记调用 complete()"

**现象**：请求挂起 30 秒后超时

**原因**：AsyncContext 没有被正确关闭

**解决**：
```java
try {
    // 业务逻辑
} finally {
    asyncContext.complete();  // ✅ 确保在 finally 中调用
}
```

### ❌ 误区 3: "在业务线程中调用 startAsync()"

**错误代码**：
```java
executor.submit(() -> {
    AsyncContext asyncContext = req.startAsync();  // ❌ IllegalStateException
});
```

**正确做法**：
```java
AsyncContext asyncContext = req.startAsync();  // ✅ 在 Tomcat 线程中调用
executor.submit(() -> {
    // 业务逻辑
});
```

### ❌ 误区 4: "异步 Servlet 不需要线程池"

**真相**: 异步 Servlet 释放的是 Tomcat 线程，但业务逻辑仍然需要线程执行。

**最佳实践**: 使用独立的业务线程池，隔离 Tomcat 线程池。

---

## 6. 性能优化建议

### 6.1 线程池配置

**Tomcat 连接器配置**：
```xml
<Connector port="8080" protocol="HTTP/1.1"
           maxThreads="200"           <!-- 最大线程数 -->
           minSpareThreads="25"       <!-- 最小空闲线程 -->
           maxConnections="10000"     <!-- 最大连接数 -->
           acceptCount="100"          <!-- 等待队列长度 -->
           asyncTimeout="30000" />    <!-- 异步超时时间 -->
```

**业务线程池配置**：
```java
ThreadPoolExecutor executor = new ThreadPoolExecutor(
    50,                             // 核心线程数（IO 密集型：CPU * 2）
    100,                            // 最大线程数（CPU * 4）
    60, TimeUnit.SECONDS,           // 空闲存活时间
    new LinkedBlockingQueue<>(500), // 队列容量
    new ThreadPoolExecutor.CallerRunsPolicy()  // 拒绝策略：背压
);
```

### 6.2 超时时间设置

**三层超时控制**：
1. **Tomcat 全局超时**: `asyncTimeout="30000"` (30 秒)
2. **AsyncContext 超时**: `asyncContext.setTimeout(10000)` (10 秒)
3. **业务超时**: 使用 `Future.get(timeout)`

**优先级**: AsyncContext > Tomcat 全局

---

## 7. 决策树：何时使用异步 Servlet？

```
并发请求数？
  ├─ < 100 → 同步 Servlet（简单易维护）
  ├─ 100-1000 → 异步 Servlet（提升吞吐量）
  └─ > 1000 → 异步 Servlet + 响应式编程（Reactor/RxJava）

业务逻辑类型？
  ├─ CPU 密集型（计算） → 同步 Servlet（异步无明显收益）
  ├─ IO 密集型（网络、数据库） → 异步 Servlet（显著提升 TPS）
  └─ 事件驱动（消息队列） → 异步 Servlet + DeferredResult

团队技术栈？
  ├─ 传统 Servlet → 同步 Servlet
  ├─ Spring MVC → Callable/DeferredResult
  └─ Spring WebFlux → 响应式编程
```

---

## 8. 最佳实践总结

1. ✅ **总是设置超时时间**: 避免请求无限挂起
2. ✅ **使用独立线程池**: 隔离 Tomcat 线程池和业务线程池
3. ✅ **在 finally 中调用 complete()**: 确保资源释放
4. ✅ **添加 AsyncListener**: 监听超时和错误
5. ✅ **使用 Spring MVC 封装**: Callable/DeferredResult 更简洁
6. ✅ **监控线程池状态**: 防止线程池耗尽
7. ✅ **性能测试**: 验证异步带来的收益

---

## 9. 延伸阅读

- **Servlet 3.1 规范** - JSR 340 (Chapter 2.3: Asynchronous Processing)
- **Spring MVC 异步请求处理** - Spring 官方文档
- **Tomcat 异步 I/O 源码分析**
- **响应式编程** - Project Reactor

---

**下一步**: 阅读 `DEFERREDRESULT_PATTERN.md`，深入理解 Spring MVC DeferredResult 的事件驱动模式。
