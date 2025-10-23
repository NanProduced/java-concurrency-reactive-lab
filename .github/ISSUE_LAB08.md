# Lab-08: Spring MVC 异步编程实现 (Servlet Async + DeferredResult)

## 概述

实现 Spring MVC 异步编程模块，通过 `Callable`、`DeferredResult`、超时控制和容错机制，演示如何使用异步 Servlet 3.0+ API 提升 Web 应用的并发处理能力和资源利用率。

**验收标准**:
- ✅ **线程占用对比**: 同步阻塞 vs DeferredResult 释放线程的量化数据
- ✅ **P99 延迟改善**: 异步模式下 P99 延迟降低 ≥30%
- ✅ **超时验证**: 完整的超时处理机制演示（超时回调 + 降级策略）
- ✅ **MVC 异步范例**: 可复用的异步模式库（5+ 场景）

**预计周期**: 0.5 周 (2-3 天)

---

## 学习目标

完成 Lab-08 后，学习者将能够：

1. **理解异步 Servlet 原理**
   - Servlet 3.0+ 异步 API (`AsyncContext`)
   - 请求线程 vs 业务线程的解耦机制
   - 异步请求生命周期（启动 → 处理 → 分发 → 响应）

2. **掌握 Spring MVC 异步模式**
   - `Callable<T>`: 简单异步任务
   - `WebAsyncTask<T>`: 可定制的异步任务
   - `DeferredResult<T>`: 外部事件驱动的异步模式 ⭐

3. **实现超时与容错机制**
   - 超时配置三层次：全局 → WebAsyncTask → DeferredResult
   - 超时回调与降级策略（默认值/缓存/快速失败）
   - 错误处理链（onError + 全局异常处理器）

4. **优化线程池配置**
   - `AsyncTaskExecutor` 配置参数（核心线程数/最大线程数/队列容量）
   - 拒绝策略选择（`CallerRunsPolicy` vs `AbortPolicy`）
   - 线程池监控与告警

5. **量化性能提升**
   - 同步 vs 异步 TPS 对比
   - 线程占用数据（同步 N 个线程 vs 异步 M 个线程）
   - P50/P95/P99 延迟改善分析

---

## 技术方案

### 架构设计

```
┌─────────────────────────────────────────────────────────────────┐
│                         HTTP Request                             │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Spring MVC Controller                         │
│  @GetMapping("/async/deferred")                                 │
│  DeferredResult<String> asyncEndpoint() { ... }                 │
└────────────────────────────┬────────────────────────────────────┘
                             │ 返回 DeferredResult
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│              Servlet 容器 (释放请求线程)                         │
│  ┌──────────────────────────────────────────────────────┐      │
│  │  AsyncContext: 保持响应打开，请求线程返回线程池       │      │
│  └──────────────────────────────────────────────────────┘      │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                 AsyncTaskExecutor 线程池                         │
│  ┌────────────────┐  ┌────────────────┐  ┌────────────────┐   │
│  │  Worker Thread │  │  Worker Thread │  │  Worker Thread │   │
│  │   执行业务逻辑  │  │   执行业务逻辑  │  │   执行业务逻辑  │   │
│  └────────┬───────┘  └────────┬───────┘  └────────┬───────┘   │
└───────────┼──────────────────┼──────────────────┼──────────────┘
            │                  │                  │
            └──────────────────┴──────────────────┘
                             │
                             ▼ setResult(T)
┌─────────────────────────────────────────────────────────────────┐
│                    DeferredResult<T>                             │
│  ┌──────────────────────────────────────────────────────┐      │
│  │  回调机制:                                            │      │
│  │  • onTimeout()  → 超时处理                           │      │
│  │  • onError()    → 错误处理                           │      │
│  │  • onCompletion() → 资源清理                         │      │
│  └──────────────────────────────────────────────────────┘      │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼ 请求重新分派到容器
┌─────────────────────────────────────────────────────────────────┐
│              Spring MVC 渲染响应并返回客户端                      │
└─────────────────────────────────────────────────────────────────┘
```

---

### 目录结构

```
lab-08-springmvc-async/
├── docs/
│   └── prerequisites/          # Layer 0 前置知识（降低学习曲线 50%）
│       ├── SERVLET_ASYNC_GUIDE.md      # Servlet 3.0+ 异步 API
│       ├── DEFERREDRESULT_PATTERN.md   # DeferredResult 模式详解
│       └── TIMEOUT_STRATEGY.md         # 超时与容错策略
│
├── src/main/java/nan/tech/lab08/springmvc/
│   ├── basics/                 # 基础演示
│   │   ├── CallableController.java           # Callable 异步演示
│   │   ├── WebAsyncTaskController.java       # WebAsyncTask 演示
│   │   └── DeferredResultController.java     # DeferredResult 演示 ⭐
│   │
│   ├── timeout/                # 超时控制
│   │   ├── TimeoutHandlingController.java    # 超时处理完整演示
│   │   └── TimeoutInterceptor.java           # 超时拦截器
│   │
│   ├── resilience/             # 容错设计
│   │   ├── FallbackStrategyController.java   # 降级策略（3种）
│   │   └── ErrorHandlingController.java      # 异常处理
│   │
│   ├── benchmark/              # 性能对比
│   │   └── SyncVsAsyncBenchmark.java         # 同步 vs 异步对比
│   │
│   ├── config/                 # 配置类
│   │   ├── AsyncWebConfiguration.java        # Web 异步配置
│   │   └── AsyncExecutorConfig.java          # 线程池配置
│   │
│   └── service/                # 业务服务
│       └── AsyncBusinessService.java         # 模拟异步业务逻辑
│
└── src/test/java/nan/tech/lab08/springmvc/
    └── basics/
        ├── CallableControllerTest.java       # MockMvc 异步测试
        └── DeferredResultControllerTest.java # 两步测试法
```

---

## 实现阶段

### Phase 1: 环境准备与前置知识（Day 1 上午）

**任务清单**:
- [ ] 创建 `lab-08-springmvc-async` Maven 模块
- [ ] 配置 `pom.xml` 依赖（Spring Boot Web + Lab-00 Foundation）
- [ ] 创建 Layer 0 前置知识文档（3 篇，共 ~9000 字）

**Layer 0 文档内容**:

#### docs/prerequisites/SERVLET_ASYNC_GUIDE.md
- Servlet 3.0+ 异步 API 演进
- `AsyncContext` 工作原理与生命周期
- 传统同步模型 vs 异步模型对比
- 适用场景与性能收益
- 常见陷阱与最佳实践

#### docs/prerequisites/DEFERREDRESULT_PATTERN.md
- DeferredResult 设计模式
- 事件驱动架构（Event Bus + Listener）
- 与 Callable/WebAsyncTask 的对比
- 适用场景（外部事件驱动/消息队列）
- 反模式与陷阱

#### docs/prerequisites/TIMEOUT_STRATEGY.md
- 超时配置三层次（全局/任务/结果）
- 降级策略决策树（默认值/缓存/快速失败）
- 容错设计模式（熔断/重试/隔离）
- 超时监控与告警

**验收标准**:
- ✅ Layer 0 文档 ≥ 9000 字
- ✅ 包含至少 3 个架构图（ASCII 格式）
- ✅ 提供决策树和对比表

---

### Phase 2: 核心异步模式实现（Day 1 下午 + Day 2 上午）

#### 2.1 Callable 基础演示

**文件**: `basics/CallableController.java`

**功能**:
1. 同步阻塞 vs Callable 异步对比
2. 线程占用数据实测（日志打印线程名）
3. 性能提升量化（TPS 对比）

**学习价值**:
- ✅ 理解请求线程与业务线程的解耦
- ✅ 观察线程名变化（http-nio-xxx → async-mvc-xxx）
- ✅ 对比式教学：WITH vs WITHOUT 异步

---

#### 2.2 DeferredResult 高级演示 ⭐

**文件**: `basics/DeferredResultController.java`

**功能**:
1. DeferredResult 完整生命周期演示
2. 事件驱动模式（模拟消息队列触发）
3. 三种回调机制（onTimeout/onError/onCompletion）

**学习价值**:
- ✅ 理解事件驱动架构
- ✅ 掌握回调机制的使用
- ✅ 学习资源管理（pendingRequests 清理）

---

#### 2.3 WebAsyncTask 演示

**文件**: `basics/WebAsyncTaskController.java`

**功能**:
1. 自定义执行器指定
2. 内置超时配置
3. 与 Callable 的对比

---

### Phase 3: 超时控制与容错设计（Day 2 下午）

#### 3.1 超时处理演示

**文件**: `timeout/TimeoutHandlingController.java`

**功能**:
1. 三层超时配置演示（全局 → WebAsyncTask → DeferredResult）
2. 超时回调 vs 超时默认值
3. 超时监控与告警

**学习价值**:
- ✅ 理解超时配置优先级
- ✅ 掌握降级策略模式
- ✅ 学习容错设计

---

#### 3.2 容错策略演示

**文件**: `resilience/FallbackStrategyController.java`

**功能**:
1. 三种降级策略（默认值/缓存/快速失败）
2. 错误处理链（onError + 全局异常处理器）
3. 集成 Resilience4j（可选）

---

### Phase 4: 性能对比与基准测试（Day 3 上午）

#### 4.1 性能基准测试

**文件**: `benchmark/SyncVsAsyncBenchmark.java`

**功能**:
1. 同步 vs 异步 TPS 对比
2. 线程池大小影响分析
3. P50/P95/P99 延迟对比

**验收标准**:
- ✅ 异步 TPS ≥ 同步 TPS × 1.5
- ✅ 异步 P99 ≤ 同步 P99 × 0.7 (改善 ≥30%)
- ✅ 线程占用: 异步 < 同步 (日志验证)

---

### Phase 5: 配置与文档（Day 3 下午）

#### 5.1 线程池配置

**文件**: `config/AsyncExecutorConfig.java`

**功能**:
1. 生产级线程池配置
2. 拒绝策略选择
3. 线程池监控

---

#### 5.2 README 文档

**文件**: `lab-08-springmvc-async/README.md`

**内容结构**:
1. **学习目标**（3 个核心目标）
2. **前置知识**（Layer 0 文档导航）
3. **快速开始**（5 个快速命令）
4. **3 天学习路径**
   - Day 1: Callable + DeferredResult 基础
   - Day 2: 超时控制 + 容错设计
   - Day 3: 性能对比 + 线程池优化
5. **核心演示**（5 个场景）
6. **性能对比数据**（同步 vs 异步）
7. **常见陷阱**（≥ 5 个）
8. **进度追踪**（百分比 + 待办）

---

## 验收标准

### 功能验收

- [ ] ✅ **Callable 演示**: 同步 vs 异步对比 + 线程名打印
- [ ] ✅ **DeferredResult 演示**: 三种回调机制 + 事件驱动模式
- [ ] ✅ **WebAsyncTask 演示**: 自定义执行器 + 超时配置
- [ ] ✅ **超时处理**: 三层配置 + 降级策略（≥ 3 种）
- [ ] ✅ **容错设计**: 错误处理链 + 全局异常处理器
- [ ] ✅ **性能对比**: TPS 提升 ≥50%, P99 改善 ≥30%

### 质量验收

- [ ] ✅ **代码注释密度**: ≥70% (允许架构图替代部分注释)
- [ ] ✅ **Javadoc 覆盖**: 100% (公开 API)
- [ ] ✅ **线程安全检查**: PASS (ConcurrentHashMap 使用正确)
- [ ] ✅ **资源管理**: ExecutorService 优雅关闭 + DeferredResult 清理
- [ ] ✅ **自启动 Demo**: 所有 Controller 可独立运行（集成测试）
- [ ] ✅ **测试质量**: MockMvc 两步测试法 + 集成测试

### 文档验收

- [ ] ✅ **Layer 0 前置知识**: ≥ 9000 字（3 篇文档）
- [ ] ✅ **架构图**: ≥ 3 个（ASCII 格式）
- [ ] ✅ **README 完整**: 学习路径 + 快速开始 + 陷阱库
- [ ] ✅ **常见陷阱**: ≥ 5 个（含解决方案）
- [ ] ✅ **决策树**: 超时策略决策树 + 异步模式选择树

### 教学价值

- [ ] ✅ **对比式教学**: WITH vs WITHOUT 完整演示
- [ ] ✅ **量化数据**: 性能提升数据 + 线程占用对比
- [ ] ✅ **实战价值**: 生产级配置 + 监控告警
- [ ] ✅ **知识沉淀**: 贡献 ≥ 5 个新陷阱到项目级 `docs/PITFALLS.md`

**目标评分**: ≥90/100

---

## 常见陷阱预警 ⚠️

### 陷阱 #1: 使用默认线程池

**问题**: `SimpleAsyncTaskExecutor` 不是真正的线程池，每次请求创建新线程

**解决**: 配置 `ThreadPoolTaskExecutor`

**文件**: `config/AsyncExecutorConfig.java`

---

### 陷阱 #2: 在异步代码中阻塞

**问题**: 违背异步编程初衷，浪费线程资源

**解决**: 使用非阻塞 API (CompletableFuture.delay, R2DBC, WebClient)

---

### 陷阱 #3: 队列容量设置为 Integer.MAX_VALUE

**问题**: 队列永远不满，线程池无法从 corePoolSize 扩展到 maxPoolSize

**解决**: 设置合理队列容量（如 100）

---

### 陷阱 #4: 没有设置超时

**问题**: 请求可能永远挂起，资源无法释放

**解决**: 始终设置超时 + 注册回调

---

### 陷阱 #5: 忘记清理资源

**问题**: DeferredResult 保存在 Map 中未清理，导致内存泄漏

**解决**: 在 `onCompletion()` 回调中清理

---

## 参考资源

### 官方文档

1. **Spring Framework Async Requests**
   https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-async.html

2. **DeferredResult JavaDoc**
   https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/context/request/async/DeferredResult.html

3. **AsyncSupportConfigurer**
   https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/servlet/config/annotation/AsyncSupportConfigurer.html

### 最佳实践

1. **Guide to DeferredResult in Spring** (Baeldung)
   https://www.baeldung.com/spring-deferred-result

2. **Testing Async Responses with MockMvc**
   https://sadique.io/blog/2015/11/24/testing-async-responses-using-mockmvc/

---

## 快速开始命令

```bash
# 1. 创建模块
cd java-concurrency-reactive-lab
mvn archetype:generate -DgroupId=nan.tech -DartifactId=lab-08-springmvc-async

# 2. 运行应用
cd lab-08-springmvc-async
mvn spring-boot:run

# 3. 测试同步端点
curl http://localhost:8080/api/async/sync

# 4. 测试 Callable 端点
curl http://localhost:8080/api/async/callable

# 5. 测试 DeferredResult 端点
curl http://localhost:8080/api/async/deferred?userId=user123

# 6. 触发 DeferredResult 完成
curl -X POST "http://localhost:8080/api/async/deferred/complete?userId=user123&result=Success"

# 7. 运行测试
mvn clean test

# 8. 生成 Javadoc
mvn javadoc:javadoc
```

---

## 成功标准

完成 Lab-08 后，应达到：

- ✅ **代码**: 1500+ 行（5 个核心演示）
- ✅ **文档**: Layer 0 (9000+ 字) + README (3000+ 字)
- ✅ **性能**: TPS 提升 ≥50%, P99 改善 ≥30%
- ✅ **质量**: 评分 ≥90/100
- ✅ **教学价值**: 优秀 ⭐⭐⭐⭐⭐
