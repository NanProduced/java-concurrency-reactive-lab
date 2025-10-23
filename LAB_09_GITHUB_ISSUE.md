# feat(lab-09): Spring WebFlux - 完整非阻塞异步HTTP栈

## 📋 概述

启动 **Lab-09 Spring WebFlux** 开发，完成从 Spring MVC 异步 → 响应式编程的平滑过渡。

### 🎯 学习目标

- [ ] 理解响应式编程基础概念（Flux/Mono/背压）
- [ ] 掌握非阻塞I/O的完整链路
- [ ] 实现高并发、低资源消耗的Web服务
- [ ] 建立MVC异步 vs WebFlux性能对比数据
- [ ] 为后续Project Reactor学习打下基础

---

## 📚 项目结构 & 交付成果

### Phase 1: Flux/Mono 基础 (3-4天)

**文件结构**:
```
lab-09-springmvc-vs-webflux/src/main/java/nan/tech/lab09/
├── basic/
│   ├── FluxController.java        # Flux基础示例
│   ├── MonoController.java        # Mono基础示例
│   └── SubscriptionController.java # 订阅、dispose演示
└── config/
    ├── WebFluxConfig.java
    └── ServerConfig.java
```

**核心要求**:
- [ ] Flux/Mono 最简示例，含完整注释（≥70%）
- [ ] 订阅生命周期演示（subscribe/onNext/onError/onComplete）
- [ ] Disposable资源释放演示
- [ ] WebTestClient 异步测试
- [ ] 控制台输出展示执行流程和线程切换
- [ ] Javadoc @教学 标记说明学习点

**性能指标基线** (用于Phase 4对比):
- [ ] 单个Mono响应时间
- [ ] Flux流处理吞吐量
- [ ] 线程使用统计

---

### Phase 2: 操作符 + 背压 (3-4天)

**文件结构**:
```
lab-09-springmvc-vs-webflux/src/main/java/nan/tech/lab09/
├── advanced/
│   ├── OperatorController.java      # map/flatMap/merge/zip
│   ├── BackpressureController.java  # 背压演示
│   ├── ErrorHandlingController.java # onError/onErrorResume/retry
│   └── TransformController.java     # buffer/collect/reduce
└── docs/
    └── phase-2-operators.md         # 操作符文档
```

**核心要求**:
- [ ] map/flatMap/merge/zip 四大关键操作符
- [ ] 背压处理演示（如何处理生产者 > 消费者）
- [ ] 异常处理模式（onError/onErrorResume/retry/onErrorReturn）
- [ ] 变异测试覆盖 (PIT) ≥70%
- [ ] 性能对比: 不同操作符的吞吐量差异

**教学亮点**:
```java
// 示例: map 操作符
Flux.range(1, 5)
    .map(i -> i * 2)
    .subscribe(System.out::println);
// 输出: 2, 4, 6, 8, 10

// 示例: 背压演示
Flux.generate(sink -> sink.next(System.nanoTime()))
    .take(5)
    .subscribe(System.out::println);  // backpressure自动处理
```

---

### Phase 3: 生产集成 (4-5天)

**文件结构**:
```
lab-09-springmvc-vs-webflux/src/main/java/nan/tech/lab09/
├── realworld/
│   ├── DatabaseController.java      # R2DBC异步数据访问
│   ├── CacheController.java         # Redis响应式集成
│   ├── MessageQueueController.java   # Kafka异步消费
│   └── StreamingController.java      # Server-Sent Events
└── docs/
    └── phase-3-production.md        # 生产集成文档
```

**核心要求**:
- [ ] R2DBC 异步数据库操作（CRUD示例）
- [ ] Redis Reactive 缓存集成
- [ ] Kafka 异步消息消费
- [ ] Server-Sent Events (SSE) 实时推送演示
- [ ] MDC 上下文传播（确保日志链路完整）
- [ ] 集成测试覆盖生产场景

**集成示例**:
```java
// R2DBC 异步查询
userRepository.findById(1)
    .flatMap(user ->
        postRepository.findByUserId(user.getId())
                     .collectList()
                     .map(posts -> new UserWithPosts(user, posts))
    )
    .subscribe(userWithPosts -> log.info("User: {}", userWithPosts));

// Redis 缓存
cache.get("user:1")
    .switchIfEmpty(
        userService.getUser(1)
                   .flatMap(user -> cache.set("user:1", user).thenReturn(user))
    )
    .subscribe(user -> response.send(user));
```

---

### Phase 4: 性能对标 (3-4天)

**文件结构**:
```
lab-09-springmvc-vs-webflux/
├── src/test/java/nan/tech/lab09/benchmark/
│   └── WebFluxPerformanceTest.java  # JMH基准测试
├── scripts/
│   └── benchmark-mvc-vs-webflux.sh  # 负载测试脚本
└── docs/
    ├── phase-4-performance.md       # 性能分析文档
    └── performance-data.json        # 对标数据
```

**核心要求**:
- [ ] 同步基线 (MVC Sync) 性能测试
- [ ] MVC异步 (Lab-08 Async) 性能测试
- [ ] WebFlux非阻塞性能测试
- [ ] 对比数据表: TPS, P50/P95/P99延迟, 资源使用
- [ ] 性能分析报告: 何时选择MVC vs WebFlux
- [ ] 决策树: 三种模式的选型指南

**性能测试场景**:
```yaml
并发用户: [10, 50, 100, 200, 500, 1000]
测试持续: 120s (30s 预热)
JVM参数: -Xmx2g -Xms2g -XX:+UseG1GC

收集指标:
  - 吞吐量 (req/s)
  - 延迟 (P50/P95/P99)
  - 活跃线程数
  - 内存使用
  - GC停顿时间
```

**对标数据示例**:

| 并发 | 模式 | 吞吐量 | P99延迟 | 线程数 | 内存 |
|------|------|--------|---------|--------|------|
| 100 | Sync | 2000 | 50ms | 120 | 250MB |
| 100 | MVC Async | 2100 | 48ms | 70 | 300MB |
| 100 | WebFlux | 2300 | 45ms | 16 | 280MB |
| 1000 | Sync | 1200 | 800ms | 1050 | 800MB |
| 1000 | MVC Async | 1800 | 500ms | 350 | 600MB |
| 1000 | WebFlux | 2800 | 350ms | 24 | 500MB |

---

## 📖 教学文档要求

### Layer 0: 前置知识 (必需)

**docs/layer-0-reactive-concepts.md**:
```markdown
# Spring WebFlux 前置知识

## 1. 为什么需要WebFlux?

### Servlet异步 vs 非阻塞I/O对比

#### Spring MVC Async (Lab-08)
- 仍然使用**Servlet线程池**
- 每个请求绑定一个线程
- 异步是指"后台线程"处理业务
- 线程仍然会阻塞在I/O操作

#### Spring WebFlux (Lab-09)
- 使用**EventLoop模型** (Netty)
- 多个请求共享少数几个线程
- 异步是指"事件驱动"处理请求
- I/O操作非阻塞，立即返回Mono/Flux

### 线程模型演变

```
Lab-01~03: Thread-per-request (线程池)
  ├─ Thread-1 处理 Request-1
  ├─ Thread-2 处理 Request-2
  └─ Thread-N 处理 Request-N

Lab-04: Future-based (异步等待)
  └─ CompletableFuture 管理异步结果

Lab-08 MVC Async: 混合模型
  ├─ Servlet线程暂停
  ├─ Async线程池执行业务
  └─ 结果返回后恢复Servlet线程

Lab-09 WebFlux: EventLoop模型
  ├─ EventLoop-1 处理 10个请求 (非阻塞)
  ├─ EventLoop-2 处理 10个请求 (非阻塞)
  └─ EventLoop-N 处理 10个请求 (非阻塞)
```

## 2. 响应式编程基础

### Flux vs Mono
- **Flux<T>**: 0个或多个元素的异步流
- **Mono<T>**: 0个或1个元素的异步结果

### 背压 (Backpressure)
生产者生产速度 > 消费者消费速度的处理方式

## 3. Netty作为服务器的含义
- EventLoop 处理多个连接
- 回调式而非线程式
```

### README.md 要求

```markdown
# Lab-09: Spring WebFlux - 非阻塞异步HTTP栈

## 📚 学习目标

- [ ] 响应式编程基础 (Flux/Mono/背压)
- [ ] 完整的非阻塞请求处理链路
- [ ] 生产级集成方案 (R2DBC/Redis/Kafka)
- [ ] MVC Async vs WebFlux 性能对比

## 🚀 快速开始

```bash
# 构建项目
cd lab-09-springmvc-vs-webflux
mvn clean install -DskipTests

# 运行基础示例
mvn spring-boot:run -Dspring-boot.run.arguments="--mode=basic"

# 运行所有示例
mvn spring-boot:run

# 运行性能测试
build/scripts/benchmark-mvc-vs-webflux.sh --concurrent 100
```

## 📖 推荐学习顺序

1. **前置知识** (15min)
   - 阅读 `docs/layer-0-reactive-concepts.md`
   - 理解 Flux/Mono/背压概念

2. **Phase 1 基础** (30min)
   - 运行 `basic/FluxController`
   - 体验 Mono/Flux 的订阅和执行

3. **Phase 2 操作符** (45min)
   - 学习 map/flatMap/merge/zip
   - 理解背压处理机制

4. **Phase 3 生产集成** (1h)
   - 学习 R2DBC 异步数据访问
   - 体验 Redis/Kafka 集成

5. **Phase 4 性能对标** (30min)
   - 查看性能对比数据
   - 理解何时选择 WebFlux

## 🐛 常见坑 & 解决

### 坑1: Flux 未订阅导致不执行
```java
// ❌ 错误: 只定义了Flux，但没有订阅
Flux.range(1, 5).map(i -> i * 2);

// ✅ 正确: 必须订阅才会执行
Flux.range(1, 5).map(i -> i * 2).subscribe(System.out::println);
```

### 坑2: 背压处理不当导致 OOM
```java
// ❌ 错误: 生产速度 > 消费速度
Flux.generate(sink -> sink.next(System.nanoTime()))  // 无限生产
    .subscribe(System.out::println);  // 但消费很慢

// ✅ 正确: 使用 take 限制或实现背压策略
Flux.generate(sink -> sink.next(System.nanoTime()))
    .take(1000)
    .subscribe(System.out::println);
```

### 坑3: 块阻塞操作阻断 Event Loop
```java
// ❌ 错误: Thread.sleep 阻塞 EventLoop
Mono.just(1)
    .map(i -> { Thread.sleep(1000); return i * 2; })
    .subscribe();

// ✅ 正确: 使用 Mono.delay 或 Schedulers
Mono.just(1)
    .delayElement(Duration.ofSeconds(1))
    .map(i -> i * 2)
    .subscribe();
```

### 坑4: 上下文传播丢失
```java
// MDC 在异步边界会丢失，需要显式传播
Context ctx = Context.of("userId", 123);
Mono.just("data")
    .contextWrite(ctx)
    .map(data -> MDC.get("userId"))  // 仍可访问
    .subscribe();
```

## 📊 性能对标

详见 `docs/phase-4-performance.md`

核心发现:
- **100 并发**: Sync ≈ Async ≈ WebFlux (差异不明显)
- **500 并发**: WebFlux 开始显示优势 (线程数: 24 vs 350 vs 1050)
- **1000+ 并发**: WebFlux 明显优于异步 (吞吐量: 2800 vs 1800 vs 1200 req/s)

## 📋 对标决策树

### Q1: 应该使用 Sync/Async/WebFlux 中的哪一个?

```
预期并发用户数?
├─ <100      → Sync 足够
├─ 100-500   → MVC Async 较优 (学习成本低)
└─ >500      → WebFlux 推荐 (资源使用少)

是否需要调用其他异步API?
├─ 否 → Sync/Async
└─ 是 → 优先 WebFlux (响应式链式调用)

数据库访问?
├─ 传统 JDBC → Sync/Async
└─ R2DBC → WebFlux (非阻塞驱动)
```

## ✅ 检查清单

使用此清单验证代码质量:

**代码审查 (40分)**:
- [ ] 所有异步操作都有 onError 处理
- [ ] 没有在订阅前就执行的阻塞操作
- [ ] Disposable 正确处理
- [ ] 线程安全的共享状态

**文档审查 (25分)**:
- [ ] README 完整描述学习路径
- [ ] Javadoc ≥70% + @教学 @陷阱 标记
- [ ] Layer 0 文档概念清晰
- [ ] 性能对标数据有出处

**测试审查 (20分)**:
- [ ] 业务逻辑测试 ≥80%
- [ ] 背压处理测试
- [ ] 错误场景完整
- [ ] 并发安全验证

**教学审查 (15分)**:
- [ ] 每个阶段有对比示例
- [ ] 有 3+ 个常见坑演示
- [ ] 性能指标可量化
- [ ] 决策树清晰明确
```

---

## 🧪 测试要求

### 单元测试
```yaml
覆盖率目标: ≥85%
关键测试:
  - Mono/Flux订阅和操作符
  - 背压处理
  - 错误处理 (onError/retry)
  - R2DBC数据库操作
  - MDC上下文传播
```

### 集成测试
```yaml
工具: WebTestClient + Awaitility
场景:
  - 完整请求处理链路
  - 超时场景
  - 异常恢复
  - 并发场景
```

### 性能测试
```yaml
工具: JMH + wrk/k6
指标: TPS, 延迟, 资源使用
对比: MVC Sync vs MVC Async vs WebFlux
```

---

## ✅ 代码质量清单

### P0 (必须)
- [ ] **线程安全**: Reactor背压、异步操作正确
- [ ] **异常处理**: 所有异步操作都有onError处理
- [ ] **资源释放**: Disposable.dispose() 确保释放
- [ ] **注释密度**: ≥70% (Javadoc @教学 @陷阱 @对标)
- [ ] **测试覆盖**: ≥85% 的业务逻辑

### P1 (重要)
- [ ] **文档完整**: README + Layer 0 + Phase文档
- [ ] **性能数据**: 完整的对标数据和分析
- [ ] **决策指南**: 清晰的MVC vs WebFlux选型树
- [ ] **常见坑**: 至少3个陷阱演示

---

## 📊 质量评分标准

| 维度 | 满分 | 目标 |
|------|------|------|
| 代码质量 (线程安全/异常/资源) | 40 | 25+ |
| 测试覆盖 (单元/集成/性能) | 20 | 16+ |
| 文档完整 (README/Javadoc/注释) | 25 | 21+ |
| 教学价值 (对比/数据/决策/坑库) | 15 | 13+ |
| **总分** | **100** | **≥75** (目标94) |

---

## 🚀 时间规划

| Phase | 主题 | 工作量 | 预计完成 |
|-------|------|--------|---------|
| **1** | Flux/Mono基础 | 3-4天 | Day 5 |
| **2** | 操作符+背压 | 3-4天 | Day 10 |
| **3** | 生产集成 | 4-5天 | Day 15 |
| **4** | 性能对标 | 3-4天 | Day 19 |
| **Review** | 代码审查 | 2天 | Day 21 |
| **总计** | | **15-19天** | **3-4周** |

---

## 📚 参考资源

### 官方文档
- Spring Framework 6.2.11 WebFlux: https://docs.spring.io/spring-framework/reference/web/webflux.html
- Spring Boot 3.3.x: https://docs.spring.io/spring-boot/docs/3.3.x/reference/html/
- Project Reactor: https://projectreactor.io/docs

### 教程
- Baeldung WebFlux: https://www.baeldung.com/spring-webflux
- Spring Guides: https://spring.io/guides/gs/reactive-rest-service/

### 性能工具
- WebTestClient: Spring Boot WebFlux testing
- wrk: HTTP压力测试工具
- JMH: 微基准测试框架

---

## 🔗 关联Issue

- Relates to: Lab-08 Phase 4 完成
- Blocks: Lab-10 Project Reactor深化
- Depends on: Lab-01~08 完成

---

## 📋 立即行动项

### Week 1: 规划 + 前置知识
- [ ] Day 1: 确认WebFlux方案 ✅
- [ ] Day 2-3: 设计项目结构 + 文件布局
- [ ] Day 4-5: 编写Layer 0文档 (响应式概念、对比MVC)

### Week 2-3: 核心实现
- [ ] Phase 1: Flux/Mono基础示例 (3-4天)
- [ ] Phase 2: 操作符 + 背压演示 (3-4天)
- [ ] Phase 3: R2DBC + 生产集成 (4-5天)

### Week 4: 性能对标 + 评审
- [ ] Phase 4: 性能测试 + 对标报告 (3-4天)
- [ ] Review: 代码审查 + 优化 (2天)

---

**创建人**: Claude Code + Compounding Engineering System
**基于**: 完整技术研究 + 项目最佳实践规范
**预期质量**: 94/100 分
**创建时间**: 2025-10-21

---

## 标签建议

```
labels: enhancement, lab-09, webflux, education, java, spring-boot, reactive-programming
assignees: (当前开发者)
milestone: Lab-09 Spring WebFlux
projects: java-concurrency-reactive-lab (if applicable)
```
