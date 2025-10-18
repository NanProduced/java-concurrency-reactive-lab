# 决策树库 (DECISION_TREES)

> **目标**: 记录 Java 并发编程中的关键决策流程和参考数据
> **维护**: 每个 Lab 完成后补充新的决策树
> **使用场景**: 面临技术选择时快速查阅决策过程

---

## 决策树索引

| 决策 ID | 标题 | 应用场景 | 状态 |
|--------|------|---------|------|
| DT-001 | 线程池大小计算 | Lab-03 | ✅ 已记录 |
| DT-002 | 同步原语选择 | Lab-02 | ✅ 已记录 |
| DT-003 | Lock vs Synchronized 选择 | Lab-02 | ✅ 已记录 |
| DT-004 | 公平锁 vs 非公平锁 | Lab-02 | ✅ 已记录 |
| DT-005 | 异步方案选择 | Lab-05 | ⏳ 待补充 |
| DT-006 | 背压策略选择 | Lab-07 | ⏳ 待补充 |
| DT-007 | GC 算法选择 | Lab-09 | ⏳ 待补充 |
| DT-008 | 响应式框架选择 | Lab-11 | ⏳ 待补充 |

---

## DT-001: 线程池配置完整决策树（增强版）

**应用场景**: 创建自定义线程池时的完整配置决策
**更新日期**: 2025-10-18（Lab-03 增强）

### 第 1 步：任务类型识别

```
任务特性是什么？
  ├─ CPU 密集型（持续计算，无 IO 等待）
  │  例如：图像处理、视频编码、数据压缩、加密解密、科学计算
  │  特征：CPU 使用率接近 100%，很少有线程阻塞
  │  → 进入 CPU 密集型配置流程
  │
  ├─ IO 密集型（大量 IO 等待）
  │  例如：数据库查询、网络请求、文件读写、RPC 调用
  │  特征：CPU 使用率低（<30%），线程大量时间在等待
  │  → 进入 IO 密集型配置流程
  │
  └─ 混合型（既有计算又有 IO）
     例如：Web 应用、微服务、数据处理管道
     特征：CPU 使用率中等（30-70%），有一定的计算和 IO
     → 进入混合型配置流程
```

### 第 2 步：线程池大小计算

#### 2.1 CPU 密集型配置

```
核心线程数 = CPU 核数 + 1
最大线程数 = CPU 核数 + 1

为什么 +1？
  - 当某个线程偶尔因为页缺失或其他原因暂停时
  - 额外的线程可以补上，保证 CPU 利用率接近 100%

示例（8 核机器）：
  corePoolSize    = 9
  maximumPoolSize = 9
```

#### 2.2 IO 密集型配置（Amdahl 定律）

```
核心线程数 = CPU 核数 * (1 + W/C)
最大线程数 = 核心线程数 * 2

其中:
  W = 等待时间 (Wait Time)
  C = 计算时间 (Compute Time)

如何估算 W/C：
  1. 使用分析工具（JProfiler、YourKit）
  2. 经验值：
     - 数据库查询（简单）: W/C ≈ 2-4
     - 数据库查询（复杂）: W/C ≈ 5-10
     - HTTP 请求: W/C ≈ 10-20
     - 文件 IO: W/C ≈ 1-3

示例（8 核机器，W/C = 4）：
  corePoolSize    = 8 * (1 + 4) = 40
  maximumPoolSize = 40 * 2 = 80
```

#### 2.3 混合型配置（经验公式）

```
核心线程数 = CPU 核数 * 2
最大线程数 = 核心线程数 * 2

这是大多数 Web 应用和微服务的推荐配置

示例（8 核机器）：
  corePoolSize    = 16
  maximumPoolSize = 32
```

### 第 3 步：队列类型选择

```
队列类型决策：

问题：任务到达后能否立即执行？
  ├─ 是（低延迟要求）→ SynchronousQueue
  │  特点：不缓冲任务，必须有空闲线程才接受任务
  │  适用：CPU 密集型、实时系统
  │  容量：0
  │
  └─ 否（允许缓冲）→ 是否有明确的内存限制？
      ├─ 是 → ArrayBlockingQueue
      │  特点：数组实现，内存局部性好，有界
      │  适用：混合型任务、内存敏感场景
      │  容量：建议 500-2000
      │
      └─ 否 → LinkedBlockingQueue
         特点：链表实现，性能稳定，有界
         适用：IO 密集型、高吞吐量场景
         容量：建议 1000-5000
         ⚠️ 不要使用 Integer.MAX_VALUE（无界），可能 OOM
```

### 第 4 步：拒绝策略选择

```
拒绝策略决策：

问题：队列满时如何处理新任务？
  ├─ 需要快速失败通知调用方？
  │  → AbortPolicy（抛出 RejectedExecutionException）
  │  适用：IO 密集型、需要感知系统过载
  │
  ├─ 需要背压（反压）机制？
  │  → CallerRunsPolicy（调用者线程执行）
  │  适用：CPU 密集型、混合型、Web 应用
  │  效果：降低任务提交速度，避免系统崩溃
  │
  ├─ 可以静默丢弃任务？
  │  → DiscardPolicy（静默丢弃）
  │  适用：日志、监控数据（非关键任务）
  │  ⚠️ 需要监控丢弃率
  │
  └─ 可以丢弃旧任务，保留新任务？
     → DiscardOldestPolicy（丢弃队列头部最老的任务）
     适用：实时数据处理（最新数据更重要）
     ⚠️ 可能丢失重要任务
```

### 完整配置模板

#### 模板 1: CPU 密集型任务

```java
int cpuCores = Runtime.getRuntime().availableProcessors();

ThreadPoolExecutor executor = new ThreadPoolExecutor(
    cpuCores + 1,              // corePoolSize
    cpuCores + 1,              // maximumPoolSize
    60L,                       // keepAliveTime
    TimeUnit.SECONDS,
    new SynchronousQueue<>(),  // workQueue: 不缓冲
    new ThreadPoolExecutor.CallerRunsPolicy()  // 背压机制
);

// 适用场景：图像处理、视频编码、数据压缩
```

#### 模板 2: IO 密集型任务

```java
int cpuCores = Runtime.getRuntime().availableProcessors();
double waitComputeRatio = 4.0; // W/C = 4 (80% 时间在等待 IO)

ThreadPoolExecutor executor = new ThreadPoolExecutor(
    (int) (cpuCores * (1 + waitComputeRatio)), // corePoolSize
    (int) (cpuCores * (1 + waitComputeRatio) * 2), // maximumPoolSize
    60L,                                       // keepAliveTime
    TimeUnit.SECONDS,
    new LinkedBlockingQueue<>(1000),           // workQueue: 有界缓冲
    new ThreadPoolExecutor.AbortPolicy()       // 快速失败
);

// 适用场景：数据库查询、HTTP 请求、文件 IO
```

#### 模板 3: 混合型任务

```java
int cpuCores = Runtime.getRuntime().availableProcessors();

ThreadPoolExecutor executor = new ThreadPoolExecutor(
    cpuCores * 2,              // corePoolSize
    cpuCores * 4,              // maximumPoolSize
    60L,                       // keepAliveTime
    TimeUnit.SECONDS,
    new ArrayBlockingQueue<>(500), // workQueue: 有界缓冲
    new ThreadPoolExecutor.CallerRunsPolicy() // 背压机制
);

// 适用场景：Web 应用、微服务、批处理
```

### 计算示例

**场景 1: 数据库查询服务（8 核机器）**
```
任务分析:
  - 任务类型: IO 密集型
  - 每次查询: 计算 20ms，等待数据库 80ms
  - W/C = 80/20 = 4

配置计算:
  核心线程数 = 8 * (1 + 4) = 40
  最大线程数 = 40 * 2 = 80
  队列类型 = LinkedBlockingQueue(1000)
  拒绝策略 = AbortPolicy（快速失败）

代码:
  new ThreadPoolExecutor(40, 80, 60L, TimeUnit.SECONDS,
      new LinkedBlockingQueue<>(1000),
      new ThreadPoolExecutor.AbortPolicy())
```

**场景 2: 图像处理服务（8 核机器）**
```
任务分析:
  - 任务类型: CPU 密集型
  - 持续计算，无 IO 等待

配置计算:
  核心线程数 = 8 + 1 = 9
  最大线程数 = 9
  队列类型 = SynchronousQueue（不缓冲）
  拒绝策略 = CallerRunsPolicy（背压）

代码:
  new ThreadPoolExecutor(9, 9, 60L, TimeUnit.SECONDS,
      new SynchronousQueue<>(),
      new ThreadPoolExecutor.CallerRunsPolicy())
```

**场景 3: Web 应用（8 核机器）**
```
任务分析:
  - 任务类型: 混合型
  - 请求处理 + 数据库查询 + 业务逻辑

配置计算:
  核心线程数 = 8 * 2 = 16
  最大线程数 = 16 * 2 = 32
  队列类型 = ArrayBlockingQueue(500)
  拒绝策略 = CallerRunsPolicy（背压）

代码:
  new ThreadPoolExecutor(16, 32, 60L, TimeUnit.SECONDS,
      new ArrayBlockingQueue<>(500),
      new ThreadPoolExecutor.CallerRunsPolicy())
```

### 快速参考表（8 核机器）

| 任务类型 | 核心线程 | 最大线程 | 队列类型 | 队列容量 | 拒绝策略 |
|---------|---------|---------|---------|---------|---------|
| **CPU 密集** | 9 | 9 | SynchronousQueue | 0 | CallerRunsPolicy |
| **IO 密集**（W/C=4） | 40 | 80 | LinkedBlockingQueue | 1000 | AbortPolicy |
| **混合型** | 16 | 32 | ArrayBlockingQueue | 500 | CallerRunsPolicy |
| **异步任务** | 16 | 32 | LinkedBlockingQueue | 1000 | AbortPolicy |
| **定时任务** | 8 | 8 | DelayQueue | - | AbortPolicy |

### 调优建议

1. **从经验值开始**: 使用上述公式和模板作为起点
2. **压测验证**: 模拟实际负载，观察性能指标
3. **监控关键指标**:
   - 活跃线程数 vs 核心线程数
   - 队列长度 vs 队列容量
   - 任务拒绝率
   - P95/P99 延迟
4. **逐步调整**: 每次调整 20-30%，观察效果
5. **记录结果**: 建立自己的性能基线数据

### 常见陷阱

| 陷阱 | 问题 | 解决方案 |
|------|------|---------|
| ❌ 线程数过多 | 频繁上下文切换，性能下降 | 使用公式计算，不要拍脑袋 |
| ❌ 线程数过少 | CPU 利用率低，吞吐量不足 | 根据 W/C 比例增加线程 |
| ❌ 无界队列 | 内存溢出（OOM） | 使用有界队列，设置合理容量 |
| ❌ 拒绝策略不当 | 任务丢失或系统崩溃 | 根据业务需求选择策略 |
| ❌ 未监控指标 | 无法发现性能问题 | 集成 Micrometer/Prometheus |

### 参考实现

查看以下实现获取完整代码：
- **Lab-03**: `ThreadPoolCalculator.java` - 完整的参数计算和对比演示
- **Lab-00**: `ApplicationConfiguration.java` - 实际生产配置
- **Lab-00**: `ThreadUtil.java` - 工具方法

### 参考资源

1. **Java Concurrency in Practice** (Brian Goetz et al.)
   - Chapter 8: Applying Thread Pools
2. **Oracle Java Concurrency Tutorial**
3. **Lab-03 README**: 线程池配置完整指南

---

## DT-002: 同步原语选择

**应用场景**: 多线程场景下选择合适的同步机制

### 决策流程

```
问题: 需要限制并发数？
  ├─ 是 → Semaphore
  │       例如：数据库连接池、API 限流、资源池管理
  │       配置：permits = 实际资源容量
  │
  └─ 否 → 需要多线程互相等待？
      ├─ 是 → 需要重复使用？
      │   ├─ 是 → CyclicBarrier
      │   │       例如：多轮游戏、分阶段任务、迭代计算
      │   │       特点：可重用、支持 barrierAction
      │   │
      │   └─ 否 → CountDownLatch
      │           例如：赛跑发令、主线程等待子任务
      │           特点：一次性、等待归零
      │
      └─ 否 → 需要生产者-消费者模式？
          ├─ 是 → 优先使用 BlockingQueue
          │       备选：wait-notify（底层机制）
          │       例如：任务队列、缓冲区、消息传递
          │
          └─ 否 → 需要高级特性（可中断/超时/公平锁）？
              ├─ 是 → ReentrantLock
              │       高级特性：
              │       - lockInterruptibly() 可中断
              │       - tryLock(timeout) 超时控制
              │       - 公平锁选项
              │       - 多个 Condition
              │
              └─ 否 → synchronized（默认选择）
                      优点：简洁、自动释放、JVM 优化
                      适用：简单同步、方法级锁定
```

### 快速选择表

| 场景 | 推荐方案 | 关键特性 |
|------|---------|---------|
| 限制并发数 | Semaphore | permits 控制 |
| 多线程集合点（可重用） | CyclicBarrier | 可循环、barrierAction |
| 主线程等待子任务 | CountDownLatch | 一次性、递减计数 |
| 生产者-消费者 | BlockingQueue | 内置阻塞、线程安全 |
| 可中断的锁 | ReentrantLock | lockInterruptibly() |
| 超时获取锁 | ReentrantLock | tryLock(timeout) |
| 公平锁 | ReentrantLock(true) | FIFO 顺序 |
| 简单同步 | synchronized | 简洁、自动释放 |

### 参考实现

查看 Lab-02 中的完整演示：
- `LockVsSynchronizedDemo.java` - Lock vs Synchronized 对比
- `SemaphoreDemo.java` - 资源池限流
- `BarrierDemo.java` - CyclicBarrier vs CountDownLatch
- `WaitNotifyDemo.java` - 生产者-消费者
- `DeadlockDemo.java` - 死锁避免

### 参考资源

- Java Concurrency in Practice, Chapter 5
- Oracle Java Concurrency Tutorial
- lab-02-synchronization/README.md

---

## DT-003: Lock vs Synchronized 选择

**应用场景**: 在 Lock 和 synchronized 之间做选择

### 决策流程

```
需要以下任一高级特性？
  - 可中断获取锁（lockInterruptibly）
  - 超时获取锁（tryLock(timeout)）
  - 公平锁（ReentrantLock(true)）
  - 多个条件变量（multiple Conditions）
  - 尝试非阻塞获取锁（tryLock()）

  ├─ 是 → 使用 ReentrantLock
  │       原因：synchronized 不支持这些高级特性
  │
  └─ 否 → 简单的同步需求？
      ├─ 是 → 使用 synchronized（强烈推荐）
      │       优点：
      │       - 代码简洁，自动释放锁
      │       - JVM 优化（偏向锁、轻量级锁）
      │       - 不会忘记 unlock()
      │
      └─ 否 → 性能敏感且高竞争场景？
          ├─ 是 → 压测对比后选择
          │       建议：
          │       - 低竞争：synchronized 稍快
          │       - 高竞争：性能相当
          │       - 默认优先 synchronized
          │
          └─ 否 → 使用 synchronized（默认选择）
```

### 特性对比详表

| 特性 | synchronized | ReentrantLock |
|------|-------------|---------------|
| 使用便利性 | ✅ 简洁，自动释放 | ❌ 需手动释放（finally） |
| 可重入性 | ✅ 支持 | ✅ 支持 |
| 公平/非公平 | ❌ 仅非公平 | ✅ 可选择 |
| 可中断 | ❌ 不支持 | ✅ lockInterruptibly() |
| 可超时 | ❌ 不支持 | ✅ tryLock(timeout) |
| 非阻塞尝试 | ❌ 不支持 | ✅ tryLock() |
| 条件变量 | ❌ 单一 wait/notify | ✅ 多个 Condition |
| 性能（低竞争） | ✅ 稍快 | ✅ 相当 |
| 性能（高竞争） | ✅ 相当 | ✅ 稍快 |
| JVM 优化 | ✅ 偏向锁、轻量级锁 | ❌ 无特殊优化 |
| 锁状态查询 | ❌ 不支持 | ✅ isLocked(), getQueueLength() |

### 使用场景建议

**优先使用 synchronized：**
1. 简单的同步块或方法
2. 方法级别的同步
3. 无高级特性需求
4. 团队成员经验不足（减少出错）
5. 代码可读性优先

**优先使用 ReentrantLock：**
1. 需要响应中断（如用户取消操作）
2. 需要超时控制（避免无限期等待）
3. 需要公平锁（防止线程饥饿）
4. 需要多个条件变量（复杂协调）
5. 需要非阻塞尝试获取锁

### 代码示例对比

**synchronized 示例：**
```java
// 简洁明了
public synchronized void increment() {
    count++;
} // 自动释放锁
```

**ReentrantLock 示例：**
```java
private final Lock lock = new ReentrantLock();

public void increment() {
    lock.lock();
    try {
        count++;
    } finally {
        lock.unlock(); // 必须手动释放
    }
}
```

### 最佳实践

1. **默认选择 synchronized**: 除非明确需要高级特性
2. **ReentrantLock 必须用 finally**: 100% 在 finally 中 unlock
3. **避免过早优化**: 先用 synchronized，有性能问题再优化
4. **代码审查重点**: 检查 Lock 是否在 finally 中释放

### 参考资源

- Java Concurrency in Practice, Chapter 13
- lab-02-synchronization/README.md
- ReentrantLock vs synchronized benchmark

---

## DT-004: 公平锁 vs 非公平锁

**应用场景**: 使用 ReentrantLock 时选择公平策略

### 决策流程

```
是否存在线程饥饿风险？
  ├─ 是 → 是否可接受性能损失（约 10-30%）？
  │   ├─ 是 → 使用公平锁
  │   │       new ReentrantLock(true)
  │   │       适用：
  │   │       - 用户请求处理（保证响应性）
  │   │       - 长时间运行的任务
  │   │       - 需要严格 FIFO 顺序
  │   │
  │   └─ 否 → 使用非公平锁 + 监控
  │           监控线程等待时间，设置告警
  │
  └─ 否 → 性能优先？
      ├─ 是 → 使用非公平锁（默认）
      │       new ReentrantLock() 或 new ReentrantLock(false)
      │       优点：
      │       - 更高的吞吐量
      │       - 更少的线程上下文切换
      │
      └─ 否 → 使用非公平锁（默认）
```

### 性能对比

| 策略 | 吞吐量 | 延迟 | 公平性 | 适用场景 |
|------|-------|------|-------|---------|
| 非公平锁 | ★★★★★ | ★★★★☆ | ★☆☆☆☆ | 高性能、短任务 |
| 公平锁 | ★★★☆☆ | ★★★★★ | ★★★★★ | 用户交互、长任务 |

**性能差异**：
- 公平锁比非公平锁慢约 10-30%
- 原因：公平锁需要维护 FIFO 队列，增加上下文切换

### 特性对比

**非公平锁（默认）：**
- 新线程可以"插队"，优先尝试获取锁
- 如果锁恰好释放，新线程直接获取
- 优点：更高吞吐量，减少线程切换
- 缺点：可能导致某些线程长时间等待（饥饿）

**公平锁：**
- 严格按照请求顺序（FIFO）分配锁
- 等待最久的线程优先获取锁
- 优点：保证公平性，无饥饿
- 缺点：性能较低，更多上下文切换

### 使用建议

**使用非公平锁（默认）：**
1. 性能敏感的场景
2. 短时间持有锁
3. 高吞吐量要求
4. 内部组件、框架代码

**使用公平锁：**
1. 用户请求处理（保证响应性）
2. 长时间持有锁的场景
3. 需要严格顺序保证
4. 防止线程饥饿

### 代码示例

```java
// 非公平锁（默认，性能更好）
Lock unfairLock = new ReentrantLock(); // 等价于 new ReentrantLock(false)

// 公平锁（保证公平性）
Lock fairLock = new ReentrantLock(true);
```

### 最佳实践

1. **默认使用非公平锁**: 性能更好，适用于大多数场景
2. **监控线程等待时间**: 发现饥饿问题时再考虑公平锁
3. **用户交互场景用公平锁**: 保证每个用户请求都能得到响应
4. **压测验证**: 在实际场景中测试性能差异

### 参考资源

- Java Concurrency in Practice, Chapter 13.3
- ReentrantLock Javadoc
- lab-02-synchronization: LockVsSynchronizedDemo.java

---

## 待补充的决策树

### DT-005: 异步方案选择 (Lab-05)
决策条件:
- 是否需要 Java 8+ 兼容性?
- 是否涉及多个依赖的异步操作?
- 是否需要背压支持?

选项:
- CompletableFuture (Java 8, 简单)
- RxJava (功能丰富，但复杂)
- Reactor (Spring 生态)
- 虚拟线程 (Java 21+, 简化异步)

### DT-003: 背压策略选择 (Lab-07)
决策条件:
- 生产速度 vs 消费速度?
- 内存约束?
- 丢弃数据是否可接受?

选项:
- BUFFER (缓冲所有元素)
- DROP (丢弃新元素)
- LATEST (保留最新元素)
- ERROR (抛出异常)

### DT-004: GC 算法选择 (Lab-09)
决策条件:
- 堆大小?
- 停顿时间要求?
- 吞吐量优先还是延迟优先?

选项:
- G1GC (推荐，通用)
- ZGC (低延迟，堆大小>4GB)
- Shenandoah (极低延迟，企业版)

### DT-005: 响应式框架选择 (Lab-11)
决策条件:
- Spring 生态集成需求?
- 背压必需?
- 学习曲线容忍度?

选项:
- Spring WebFlux + Reactor
- RxJava 3
- Vert.x

---

**最后更新**: 2025-10-17
**贡献者**: AI Assistant
**版本**: 1.0

