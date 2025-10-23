# Lab-09: Spring WebFlux - 非阻塞异步 HTTP 栈完整实现

> **Lab 类型**: 响应式编程范式 + 性能对标
> **预计工期**: 3-4 周 (15-19 天)
> **学习难度**: ⭐⭐⭐ (中等-高)
> **前置知识**: Lab-01~08 完成，理解 Reactor 模式和异步编程

---

## 📚 学习目标

- [ ] **理解响应式编程** (Flux/Mono/背压)
- [ ] **掌握非阻塞 I/O** (R2DBC vs JDBC, 异步网络调用)
- [ ] **实现生产级集成** (R2DBC、Redis、Kafka、SSE)
- [ ] **性能对标** (MVC 同步 vs MVC 异步 vs WebFlux 的完整对比)
- [ ] **架构选型决策** (何时用 Sync/Async/WebFlux)

---

## 🚀 快速开始

### 前置条件
```bash
# 确保已完成 Lab-00 到 Lab-08
# Java 17+, Maven 3.9+, Spring Boot 3.3.x

# 项目根目录
cd java-concurrency-reactive-lab
```

### 启动应用
```bash
# 1. 构建项目
mvn clean install -DskipTests

# 2. 进入 Lab-09 目录
cd lab-09-springmvc-vs-webflux

# 3. 启动应用
mvn spring-boot:run

# 4. 验证启动成功
curl http://localhost:8080/basic/mono/simple
```

### 学习路径（推荐）

```
时间        学习内容                           预期产出
════════════════════════════════════════════════════════════════
Day 1-2     前置知识                          理解 3 个关键概念
  ├─ docs/00_REACTOR_VS_REACTIVE_CONCEPTS.md  (Reactor vs Reactive vs System)
  ├─ docs/01_FLUX_MONO_FUNDAMENTALS.md        (Flux/Mono 本质)
  └─ docs/02_BACKPRESSURE_EXPLAINED.md        (背压机制)

Day 3-5     Phase 1: Flux/Mono 基础 (100%)   10+ 基础演示
  ├─ FluxController.java (5 个 Demo)
  ├─ MonoController.java (5 个 Demo)
  └─ SubscriptionController.java (生命周期演示)

Day 6-10    Phase 2: 操作符 + 背压 (100%)     4 个操作符 + 背压
  ├─ OperatorController.java (map/flatMap/merge/zip)
  ├─ BackpressureController.java (背压演示)
  ├─ ErrorHandlingController.java (异常处理)
  └─ docs/phase-2-operators.md

Day 11-15   Phase 3: 生产集成 (100%)          完整的生产级代码
  ├─ DatabaseController.java (R2DBC)
  ├─ CacheController.java (Redis)
  ├─ MessageQueueController.java (Kafka)
  ├─ StreamingController.java (SSE)
  └─ docs/phase-3-production.md

Day 16-19   Phase 4: 性能对标 (100%)          对标数据 + 决策树
  ├─ WebFluxPerformanceTest.java (JMH)
  ├─ docs/phase-4-performance.md
  ├─ benchmark-mvc-vs-webflux.sh (负载脚本)
  └─ performance-data.json
```

---

## 📖 关键概念快速查阅

### Reactor 模式 vs 响应式编程 vs 响应式系统 🔑

**用户建议的重点文档** (必读！很多学习者搞混这三个概念)

```
┌─────────────────────────────────────────────────────────┐
│ Reactor 模式 (网络编程架构)                              │
│ ├─ 什么: 使用 select/poll/epoll 处理多个连接            │
│ ├─ 实现: Netty EventLoop                              │
│ └─ 特点: 单线程多连接，高效                             │
│                                                         │
│ ┌─────────────────────────────────────────────────────┐ │
│ │ 响应式编程 (编程范式)                                 │ │
│ │ ├─ 什么: 使用 Flux/Mono 异步链式调用                │ │
│ │ ├─ 实现: Project Reactor                           │ │
│ │ └─ 特点: 声明式异步，背压自动处理                   │ │
│ │                                                     │ │
│ │ ┌───────────────────────────────────────────────┐ │ │
│ │ │ 响应式系统 (系统设计理念)                       │ │ │
│ │ │ ├─ 什么: 微服务异步+自动恢复+可伸缩           │ │ │
│ │ │ ├─ 实现: Spring Cloud + Kafka + K8s          │ │ │
│ │ │ └─ 特点: 高可用，端到端背压                   │ │ │
│ │ └───────────────────────────────────────────────┘ │ │
│ └─────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────┘

关键发现: WebFlux = Reactor 模式 + 响应式编程
```

👉 **详见**: [docs/00_REACTOR_VS_REACTIVE_CONCEPTS.md](docs/00_REACTOR_VS_REACTIVE_CONCEPTS.md)

### Mono vs Flux

| 对比项 | Mono<T> | Flux<T> |
|--------|---------|---------|
| **元素数** | 0 或 1 | 0+ 或无限 |
| **用途** | 单个结果 | 多个结果或流 |
| **例子** | 用户查询 | 用户列表 |
| **onNext() 次数** | ≤1 | 0+ 次 |
| **Database** | SELECT ... WHERE id=1 | SELECT * FROM users |

👉 **详见**: [docs/01_FLUX_MONO_FUNDAMENTALS.md](docs/01_FLUX_MONO_FUNDAMENTALS.md)

### 背压 (Backpressure) - 响应式编程的秘密武器

```
问题: 生产速度 >> 消费速度 → OOM

解决方案: 背压
  消费者: "我只能处理 100 个/秒"
  生产者: "好的，我会控制在 100 个/秒"
  结果: 自动平衡，永不 OOM

Project Reactor 的背压:
  ✅ 大部分自动处理 (内置)
  ✅ 需要时显式配置 (buffer/take/onBackpressure)
  ✅ 是 WebFlux 高效的核心原因
```

👉 **详见**: [docs/02_BACKPRESSURE_EXPLAINED.md](docs/02_BACKPRESSURE_EXPLAINED.md)

---

## 🎯 Phase 1-4 详细规划

### Phase 1: Flux/Mono 基础 (Day 3-5, 3-4 天)

**目标**: 理解响应式编程的基础概念

**交付物**:
- `FluxController.java` (5 个基础 Demo)
- `MonoController.java` (5 个基础 Demo)
- `SubscriptionController.java` (生命周期演示)
- WebTestClient 异步测试
- 性能基线收集 (用于 Phase 4 对比)

**代码示例**:
```java
// Demo 1: 简单的 Mono
@GetMapping("/mono/simple")
public Mono<String> simpleMono() {
    return Mono.just("Hello, Reactive World!")
        .doOnNext(msg -> log.info("Mono 输出: {}", msg));
}

// Demo 2: 简单的 Flux
@GetMapping("/flux/simple")
public Flux<String> simpleFlux() {
    return Flux.just("A", "B", "C")
        .doOnNext(item -> log.info("Flux 输出: {}", item));
}

// Demo 3: 延迟的 Mono（非阻塞）
@GetMapping("/mono/delay")
public Mono<String> delayMono() {
    return Mono.just("延迟结果")
        .delayElement(Duration.ofSeconds(2))
        .doOnNext(msg -> log.info("2 秒后: {}", msg));
}
```

**质量清单**:
- [ ] 所有 Demo 都可独立运行
- [ ] 注释密度 ≥70%（@教学 @陷阱 标记）
- [ ] Javadoc 100% (公开 API)
- [ ] 线程安全检查通过
- [ ] 性能基线收集完整

---

### Phase 2: 操作符 + 背压 (Day 6-10, 3-4 天)

**目标**: 学习响应式流的转换和背压处理

**交付物**:
- `OperatorController.java` (map/flatMap/merge/zip)
- `BackpressureController.java` (背压演示)
- `ErrorHandlingController.java` (onError/retry/recover)
- `TransformController.java` (buffer/collect/reduce)
- Reactor Test 单元测试 (≥70%)
- `docs/phase-2-operators.md` (详细说明)

**关键操作符**:
```java
// map: 元素变换
Flux.range(1, 5)
    .map(i -> i * 2)
    .subscribe(System.out::println);

// flatMap: 异步链式调用
Flux.range(1, 3)
    .flatMap(userId -> userRepository.findById(userId))
    .subscribe(user -> System.out.println(user));

// merge: 合并多个流
Flux.merge(flux1, flux2, flux3)
    .subscribe(item -> System.out.println(item));

// zip: 拉链操作
Flux.zip(names, ages)
    .subscribe(tuple -> System.out.println(tuple.getT1() + ": " + tuple.getT2()));
```

**背压处理**:
```java
// buffer: 缓冲策略
Flux.range(1, 1000)
    .buffer(100)  // 每 100 个一批
    .subscribe(batch -> processBatch(batch));

// take: 限制元素数
Flux.interval(Duration.ofMillis(100))
    .take(10)  // 只取 10 个
    .subscribe(num -> System.out.println(num));

// onBackpressureDrop: 丢弃超出的
Flux.range(1, Integer.MAX_VALUE)
    .onBackpressureDrop()
    .subscribe(num -> expensiveOperation(num));
```

**质量清单**:
- [ ] 4 个操作符完整实现
- [ ] 背压处理演示至少 3 种
- [ ] 错误处理场景覆盖 ≥5 种
- [ ] 单元测试覆盖 ≥70%
- [ ] 性能对比: 不同操作符的吞吐量差异

---

### Phase 3: 生产集成 (Day 11-15, 4-5 天)

**目标**: 整合真实的数据源和消息队列

**交付物**:
- `DatabaseController.java` (R2DBC 异步 CRUD)
- `CacheController.java` (Redis Reactive)
- `MessageQueueController.java` (Kafka 消费)
- `StreamingController.java` (Server-Sent Events)
- MDC 上下文传播演示
- 集成测试 (TestContainers + WebTestClient)
- `docs/phase-3-production.md`

**代码示例**:
```java
// R2DBC: 异步数据库查询
@GetMapping("/users/{id}")
public Mono<User> getUserById(@PathVariable Long id) {
    return userRepository.findById(id)
        .flatMap(user ->
            postRepository.findByUserId(id)
                         .collectList()
                         .map(posts -> new UserWithPosts(user, posts))
        )
        .onErrorResume(ex -> {
            log.error("查询失败", ex);
            return Mono.empty();
        });
}

// Redis 缓存
@GetMapping("/cache/users/{id}")
public Mono<User> getCachedUser(@PathVariable Long id) {
    return cache.get("user:" + id)
        .switchIfEmpty(
            userRepository.findById(id)
                         .flatMap(user ->
                             cache.set("user:" + id, user)
                                  .thenReturn(user)
                         )
        );
}

// Kafka 消费
@GetMapping("/stream/messages")
public Flux<Message> streamMessages() {
    return kafkaTemplate.receive(topic)
        .doOnNext(record -> log.info("收到消息: {}", record.value()))
        .map(ConsumerRecord::value)
        .onErrorResume(ex -> {
            log.error("消费失败", ex);
            return Flux.empty();
        });
}

// SSE 推送
@GetMapping("/stream/events")
public Flux<ServerSentEvent<Event>> streamEvents() {
    return eventPublisher.events()
        .map(event -> ServerSentEvent.builder()
            .data(event)
            .build());
}
```

**质量清单**:
- [ ] R2DBC 完整 CRUD
- [ ] Redis 连接池管理
- [ ] Kafka 异常处理 + 重试
- [ ] SSE 连接断开处理
- [ ] MDC 链路传播验证
- [ ] 集成测试覆盖 ≥80%

---

### Phase 4: 性能对标 (Day 16-19, 3-4 天)

**目标**: 完整的性能数据对比和选型决策指南

**交付物**:
- `WebFluxPerformanceTest.java` (JMH 微基准)
- `benchmark-mvc-vs-webflux.sh` (负载脚本)
- `docs/phase-4-performance.md` (性能分析)
- `performance-data.json` (对标数据)
- 决策树 (何时用 Sync/Async/WebFlux)
- 火焰图对比 (CPU 使用)

**性能指标**:

| 并发 | Sync | MVC Async | WebFlux | 胜者 |
|-----|------|-----------|---------|------|
| 10 | 2000 req/s | 2000 req/s | 2050 req/s | 差异不大 |
| 100 | 2000 req/s | 2100 req/s | 2300 req/s | WebFlux 小幅领先 |
| 500 | 1200 req/s | 1800 req/s | 2800 req/s | **WebFlux 显著** |
| 1000 | 800 req/s | 1200 req/s | 2800 req/s | **WebFlux 3.5 倍** |

**线程数对比**:

| 并发 | Sync | MVC Async | WebFlux |
|-----|------|-----------|---------|
| 100 | 120 线程 | 70 线程 | 16 线程 |
| 1000 | 1050 线程 | 350 线程 | 24 线程 |

**决策树**:
```
预期并发用户数？
├─ <100      → Sync 足够 (差异不大)
├─ 100-500   → MVC Async 较优 (学习成本低)
└─ >500      → WebFlux 推荐 (资源使用少，吞吐量高)

是否有异步 API 调用？
├─ 否 → Sync/Async
└─ 是 → 优先 WebFlux (响应式链式)

数据库访问方式？
├─ JDBC (阻塞) → Sync/Async
└─ R2DBC (非阻塞) → WebFlux (优势最大)
```

**质量清单**:
- [ ] JMH 基准测试完整
- [ ] 负载测试脚本可复现
- [ ] 对标数据有 3+ 个并发级别
- [ ] P50/P95/P99 延迟都有记录
- [ ] CPU、内存、GC 都有监控
- [ ] 决策树清晰明确

---

## 📋 代码质量检查清单

### P0 (必须)
- [ ] **线程安全**: 没有 SharedState 问题，响应式背压正确
- [ ] **异常处理**: 所有异步操作都有 onError 处理，不吞错
- [ ] **资源释放**: Disposable 正确处理，连接池管理得当
- [ ] **注释密度**: ≥70% (Javadoc + @教学 + @陷阱)
- [ ] **测试覆盖**: ≥85% 业务逻辑 (业务优先，非覆盖率驱动)

### P1 (重要)
- [ ] **文档完整**: README + Javadoc + Phase 文档
- [ ] **性能数据**: 完整对标数据和可视化图表
- [ ] **决策指南**: 清晰的选型决策树
- [ ] **常见坑**: 至少 5+ 个陷阱演示和解决方案
- [ ] **代码示例**: 每个特性都有 3+ 个用法示例

### P2 (可选)
- [ ] **火焰图**: CPU 热点可视化
- [ ] **CI/CD**: GitHub Actions 自动测试
- [ ] **Docker**: 容器化部署脚本

---

## 🐛 常见坑库 (来自 Lab-09)

### 坑 1: 定义了 Mono/Flux 但没有订阅

```java
// ❌ 错误：数据库查询不会执行！
Mono<User> userMono = userRepository.findById(1);
// 此时什么都没有发生

// ✅ 正确：必须订阅
userMono.subscribe(user -> System.out.println(user));
```

### 坑 2: 背压无法处理，导致 OOM

```java
// ❌ 错误：无限生产，消费慢 → OOM
Flux.generate(sink -> sink.next(System.nanoTime()))
    .subscribe(timestamp -> {
        Thread.sleep(1000);  // 太慢了！
        System.out.println(timestamp);
    });

// ✅ 正确：限制元素数
Flux.generate(sink -> sink.next(System.nanoTime()))
    .take(1000)
    .subscribe(timestamp -> System.out.println(timestamp));
```

### 坑 3: 块阻塞操作阻断 EventLoop

```java
// ❌ 错误：Thread.sleep 阻塞 EventLoop
Mono.just(1)
    .map(i -> { Thread.sleep(1000); return i * 2; })
    .subscribe(System.out::println);

// ✅ 正确：使用 Mono.delay 或 Schedulers
Mono.just(1)
    .delayElement(Duration.ofSeconds(1))
    .map(i -> i * 2)
    .subscribe(System.out::println);
```

### 坑 4: 上下文传播丢失

```java
// ❌ 错误：MDC 在异步边界丢失
MDC.put("userId", 123);
Mono.fromCallable(() -> {
    System.out.println(MDC.get("userId"));  // null!
}).subscribe();

// ✅ 正确：显式传播
Context ctx = Context.of("userId", 123);
Mono.fromCallable(() -> {
    // Context 传播进来
    return doSomething();
})
.contextWrite(ctx)
.subscribe();
```

---

## 🔗 关联 Lab

- **前置**: Lab-01~08 (并发基础 + 异步编程 + Netty)
- **后续**: Lab-10 (Project Reactor 深入) + Lab-11~14 (生产系统)

---

## 📊 质量评分标准

| 维度 | 满分 | 目标 | 说明 |
|------|------|------|------|
| **代码质量** | 40 | 25+ | 线程安全、异常处理、资源释放 |
| **测试覆盖** | 20 | 16+ | 单元 + 集成 + 性能测试 |
| **文档完整** | 25 | 21+ | README + Javadoc + 注释 |
| **教学价值** | 15 | 13+ | 对比、数据、决策、坑库 |
| **总分** | **100** | **≥75** | 目标 94 分 ⭐⭐⭐⭐⭐ |

---

## 🚀 立即行动

### Week 1: 规划 + 前置知识 ✅

- [x] Day 1: 阅读 `00_REACTOR_VS_REACTIVE_CONCEPTS.md` (讲清三个关键概念！)
- [x] Day 2: 阅读 `01_FLUX_MONO_FUNDAMENTALS.md` + `02_BACKPRESSURE_EXPLAINED.md`

### Week 2-3: 核心实现 (开始)

- [ ] Phase 1: Flux/Mono 基础示例 (3-4 天)
- [ ] Phase 2: 操作符 + 背压演示 (3-4 天)
- [ ] Phase 3: R2DBC + 生产集成 (4-5 天)

### Week 4: 性能对标 + Review

- [ ] Phase 4: 性能测试 + 对标报告 (3-4 天)
- [ ] Review: 代码审查 + 优化 (2 天)

---

## 📚 参考资源

### 官方文档
- [Spring Framework 6.2.11 WebFlux](https://docs.spring.io/spring-framework/reference/web/webflux.html)
- [Spring Boot 3.3.x](https://docs.spring.io/spring-boot/docs/3.3.x/reference/html/)
- [Project Reactor](https://projectreactor.io/docs)
- [Reactive Streams](https://www.reactive-streams.org/)

### 学习资源
- [Baeldung WebFlux](https://www.baeldung.com/spring-webflux)
- [Spring Guides](https://spring.io/guides/gs/reactive-rest-service/)

### 工具
- **测试**: WebTestClient, Reactor Test, AssertJ, Awaitility
- **性能**: JMH (微基准), wrk/k6 (压力测试)
- **监控**: Micrometer, Prometheus, Grafana (可选)

---

## ✅ 检查清单 (Project Completion)

- [ ] Phase 1-4 代码实现完成
- [ ] 所有 Demo 都可独立运行
- [ ] 文档完整 (README + Javadoc + Phase 文档)
- [ ] 测试覆盖 ≥85%
- [ ] 性能对标数据完整
- [ ] 常见坑库更新 (≥5 个)
- [ ] 模板库更新 (≥3 个)
- [ ] 决策树清晰明确
- [ ] 代码审查通过
- [ ] 最终质量评分 ≥94 分

---

**创建于**: 2025-10-23
**预期完成**: 2025-11-10
**质量目标**: 94/100 ⭐⭐⭐⭐⭐

