# Context7 技术文档查询集成方案

> **目标**：确保代码准确性，在面临不确定的技术问题时自动查询 Context7 官方文档

---

## 1. 集成流程

### 1.1 自动触发机制

AI 在编写代码时遇到以下情况会自动查询 Context7：

```
触发条件:

1️⃣ 框架特性查询
   - 使用新的 Spring Boot/Reactor/WebFlux 特性
   - 不确定某个配置参数的含义
   - 需要验证 API 用法是否最新

2️⃣ 最佳实践确认
   - 线程安全的实现方式
   - 异步处理的推荐模式
   - 性能优化的标准做法

3️⃣ 版本兼容性检查
   - 确认某个功能在 JDK 17 中是否可用
   - 确认依赖库的版本支持

4️⃣ 未确定的技术细节
   - 背压策略的文档确认
   - GC 参数的官方建议
   - 死锁防护的推荐方案

5️⃣ 关键决策前的文档验证
   - 选择 MVC vs WebFlux 前
   - 选择线程池参数前
   - 选择错误处理策略前
```

### 1.2 查询流程

```
遇到不确定问题
  ↓
触发 Context7 查询
  ↓
mcp__context7__resolve-library-id
  └─ 解析需要查询的库/框架名
  ↓
mcp__context7__get-library-docs
  └─ 获取官方文档与代码示例
  ↓
分析文档结果
  ↓
根据文档更新代码/注释
  ↓
在代码注释中记录 @参考 来源
```

---

## 2. Context7 查询用例库

### 2.1 Spring Boot 3.3.x 查询

**场景**：需要配置 WebFlux 连接池

```python
# AI 执行以下查询：

library_id = mcp__context7__resolve-library-id(
    libraryName="Spring Boot WebFlux"
)
# 返回: /spring-projects/spring-webflux

docs = mcp__context7__get-library-docs(
    context7CompatibleLibraryID="/spring-projects/spring-webflux",
    topic="connection pool configuration"
)

# 返回：官方关于连接池的配置文档
# AI 根据文档更新代码，并添加注释：
# @参考 Spring WebFlux 官方文档: Connection Pool Configuration
```

### 2.2 Project Reactor 查询

**场景**：不确定背压策略的实现细节

```python
library_id = mcp__context7__resolve-library-id(
    libraryName="Project Reactor"
)

docs = mcp__context7__get-library-docs(
    context7CompatibleLibraryID="/project-reactor/reactor-core",
    topic="backpressure handling"
)

# 返回：背压的官方实现指南
# @参考 Project Reactor 官方: Backpressure Specification
```

### 2.3 JDK 17 特性查询

**场景**：确认某个 API 在 JDK 17 中的状态

```python
library_id = mcp__context7__resolve-library-id(
    libraryName="Java Development Kit"
)

docs = mcp__context7__get-library-docs(
    context7CompatibleLibraryID="/openjdk/jdk/17",
    topic="CompletableFuture improvements"
)

# 返回：JDK 17 中对 CompletableFuture 的改进
```

### 2.4 Resilience4j 查询

**场景**：选择超时与重试策略

```python
library_id = mcp__context7__resolve-library-id(
    libraryName="Resilience4j"
)

docs = mcp__context7__get-library-docs(
    context7CompatibleLibraryID="/resilience4j/resilience4j",
    topic="timeout and retry strategy"
)
```

---

## 3. 每个 Lab 的关键查询清单

### Lab-01: Thread Basics

```
□ volatile 关键字的语义
  → mcp__context7__get-library-docs("Java Memory Model")

□ Happens-Before 规则的详细定义
  → mcp__context7__get-library-docs("JLS Chapter 17")

□ synchronized 的实现机制
  → mcp__context7__get-library-docs("JVM Specification")
```

### Lab-02: Locks & Synchronizers

```
□ ReentrantLock 与 synchronized 的区别
  → query(/java-util-concurrent/locks)

□ AQS (Abstract Queued Synchronizer) 的设计
  → query(/java-util-concurrent/synchronizers)
```

### Lab-03: Executors & Pools

```
□ ThreadPoolExecutor 的配置参数含义
  → query(/java-util-concurrent/executor-framework)

□ 拒绝策略的工作原理
  → query(/java-util-concurrent/rejection-policies)
```

### Lab-04: CompletableFuture

```
□ 异常链的传播规则
  → query(/java-util-concurrent/completable-future)

□ 上下文在异步转换中的传递
  → query(/jdk-17/executors)

□ 与 MVC 之外的上下文整合
  → query(/spring-framework/async-execution)
```

### Lab-09: Reactor Core

```
□ Flux/Mono 的背压实现细节
  → query(/project-reactor/reactive-streams)

□ Context 的生命周期与传播
  → query(/project-reactor/reactor-context)

□ Schedulers 的选择与影响
  → query(/project-reactor/scheduler)
```

### Lab-10: Spring WebFlux

```
□ WebFlux 的非阻塞模型
  → query(/spring-projects/spring-webflux/reactive-stack)

□ R2DBC 与 JDBC 的对比
  → query(/r2dbc/r2dbc-spi)

□ 连接池配置
  → query(/spring-projects/spring-webflux/connection-pool)
```

---

## 4. Context7 查询的标准流程

### Step 1: 识别需求

```
代码中出现不确定性标记:
  - "我不确定..."
  - "需要验证..."
  - "官方文档说..."
  - "是否还有更新..."
```

### Step 2: 触发 Context7 查询

```python
# 示例：在 lab-09 中处理背压

try:
    # 实现 onBackpressureBuffer 的 maxSize 参数
    maxSize = 10000  # 但不确定这个数字是否合理

    # 触发查询
    docs = context7_query(
        library="Project Reactor",
        topic="onBackpressureBuffer recommended sizes",
        context="memory usage vs throughput trade-off"
    )

    # 根据文档更新代码
    # @参考 Project Reactor Buffer Size Guidelines

except UnCertainty as e:
    # 未找到确切答案时的处理
    log_and_flag_for_review()
```

### Step 3: 记录 @参考

所有从 Context7 获取的信息都要记录在代码中：

```java
/**
 * 背压缓冲区大小配置
 *
 * @参考 Project Reactor 官方文档:
 *   - "Backpressure Handling" section
 *   - 推荐: 内存充足时 10000, 受限时 1000
 *   - 链接: https://projectreactor.io/docs/core/release/reference/
 *   - 查询时间: lab-09, 2024-XX-XX
 *
 * 根据文档, 我们选择 maxSize = 10000 因为:
 * - 目标是吞吐优先 (throughput-first scenario)
 * - 堆内存 ≥ 2GB
 * - P99 延迟要求 < 100ms
 */
int maxSize = 10000;
```

---

## 5. Context7 查询的决策树

```
遇到技术不确定性
  ├─ 是否是框架 API 问题?
  │  ├─ YES → query 相关框架
  │  │  ├─ Spring Boot: /spring-projects/spring-boot
  │  │  ├─ Reactor: /project-reactor/reactor-core
  │  │  ├─ WebFlux: /spring-projects/spring-webflux
  │  │  └─ Netty: /netty-io/netty
  │  └─ NO → 继续判断
  │
  ├─ 是否是 JDK/Java 语言问题?
  │  ├─ YES → query JDK/JLS
  │  │  ├─ JDK 17: /openjdk/jdk/17
  │  │  ├─ JLS: /oracle/jls
  │  │  └─ JVM: /oracle/jvm-spec
  │  └─ NO → 继续判断
  │
  ├─ 是否是最佳实践问题?
  │  ├─ YES → query 相关库的指南
  │  │  ├─ Concurrency Patterns: search for pattern name
  │  │  └─ Performance Tuning: search for tuning guide
  │  └─ NO → 继续判断
  │
  └─ 查询结果
     ├─ 找到明确答案 → 应用到代码 + 添加 @参考
     ├─ 找到多个选项 → 选择最合适 + 记录取舍理由
     └─ 未找到 → 在代码中添加 TODO 与优先级标记
```

---

## 6. 与代码注释的集成

### 6.1 @参考 注释规范

```java
/**
 * 线程池核心线程数计算
 *
 * @参考 Java Concurrency in Practice, 第 8 章
 *   - 作者: Brian Goetz et al.
 *   - 章节: "Sizing ThreadPool"
 *   - 公式: N_threads = N_cpu * U_cpu * (1 + W/C)
 *     其中: N_cpu = 处理器数量
 *           U_cpu = 目标 CPU 利用率 (0-1)
 *           W/C = 等待时间 / 计算时间
 *
 * @参考 Spring ThreadPoolExecutor 官方文档
 *   - Query: Spring Framework ThreadPoolTaskExecutor
 *   - URL: https://docs.spring.io/spring-framework/docs/...
 *   - 查询日期: 2024-03-15
 *
 * 根据上述文献, 我们选择:
 *   corePoolSize = Runtime.getRuntime().availableProcessors()
 *   这是因为任务大多是 IO 密集 (W/C ≈ 10)
 */
public ThreadPoolExecutor configureThreadPool() {
    int corePoolSize = Runtime.getRuntime().availableProcessors();
    // ...
}
```

### 6.2 @教学 中包含 Context7 内容

```java
/**
 * CompletableFuture 的异常处理
 *
 * @教学
 *   - 异常链的概念: 异步操作中异常的传播路径
 *   - 三种处理方式: exceptionally / handle / whenComplete
 *   - 为什么选择 handle: 因为既要处理异常也要处理正常情况
 *
 * @参考 Context7 查询结果 (2024-XX-XX):
 *   - 库: JDK 17 CompletableFuture
 *   - 主题: Exception handling in async chains
 *   - 文档确认: handle() 是最通用的选择
 */
```

---

## 7. 与 CLAUDE.md 的集成

在 `CLAUDE.md` 中新增：

```markdown
## Context7 技术文档查询集成

### 自动查询触发

当 AI 遇到以下情况时会自动查询 Context7:
- 使用新框架特性，需要验证文档
- 不确定最佳实践的定义
- 需要确认版本兼容性
- 线程安全/并发相关的关键决策

### 查询流程

1. mcp__context7__resolve-library-id
   └─ 解析需要查询的库名

2. mcp__context7__get-library-docs
   └─ 获取官方文档与代码示例

3. 代码更新 + 注释记录
   └─ 添加 @参考 标记与查询信息

### 相关文件

- .claude/CONTEXT7_INTEGRATION.md (详细规范)
- 每个 lab README 中的 @参考 标记
- 代码注释中的 Context7 查询来源

### 质量保证

所有关键技术决策都必须有 Context7 查询的追踪。
这确保:
✅ 代码准确性与权威性
✅ 可溯性与可维护性
✅ 教学价值的完整性
```

---

## 8. 检查清单

每个 lab 完成前运行：

```
□ 所有不确定的技术问题都有 Context7 查询记录
□ 所有 @参考 标记都包含查询来源
□ 代码中的关键决策都有文档支持
□ 没有"我觉得应该这样"的无依据代码
□ @参考 与实际代码实现保持一致
```

---

**下一步**：在 CLAUDE.md 中记录如何触发 Context7 查询
