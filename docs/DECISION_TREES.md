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

## DT-001: 线程池大小计算

**应用场景**: 创建自定义线程池时的关键决策

### 决策流程

```
当前有新的并发任务?
  ├─ YES: 需要线程池 → 继续
  └─ NO: 返回（使用 ForkJoinPool）

任务特性是什么?
  ├─ CPU 密集型
  │  ├─ 核心线程数 = CPU 核数
  │  ├─ 最大线程数 = CPU 核数
  │  ├─ 队列容量 = 100-200
  │  └─ 公式: N = CPU_count * 1.0
  │
  ├─ IO 密集型
  │  ├─ 核心线程数 = CPU 核数
  │  ├─ 最大线程数 = CPU 核数 * (5-10)
  │  ├─ 队列容量 = 1000-5000
  │  └─ 公式: N = CPU_count * U_cpu * (1 + W/C)
  │
  └─ 混合型
     ├─ 分离为两个线程池（CPU + IO）
     └─ 分别按各自特性配置
```

### 公式详解

**IO 密集型线程池大小公式**:
```
N_threads = N_cpu * U_cpu * (1 + W/C)

其中:
  N_cpu = 处理器数量 (CPU 核数)
  U_cpu = 目标 CPU 利用率 (0-1)
  W/C = 等待时间 / 计算时间的比率
```

### 计算示例

**示例 1: IO 密集型 (数据库查询)**
```
配置:
  - CPU 核数: 8
  - 目标 CPU 利用率: 70% (0.7)
  - W/C 比率: 10:1 (大量等待 IO)

计算:
  N = 8 * 0.7 * (1 + 10/1)
    = 8 * 0.7 * 11
    = 61.6

建议: 核心线程 = 8, 最大线程 = 64
```

**示例 2: CPU 密集型 (算法计算)**
```
配置:
  - CPU 核数: 8
  - 目标 CPU 利用率: 100% (1.0)
  - W/C 比率: 0:1 (几乎无等待)

计算:
  N = 8 * 1.0 * (1 + 0/1)
    = 8 * 1.0 * 1
    = 8

建议: 核心线程 = 8, 最大线程 = 8
```

### 决策建议

| 场景 | 核心线程 | 最大线程 | 队列容量 | 保活时间 |
|------|---------|---------|---------|---------|
| CPU 密集 | CPU数 | CPU数 | 100 | 300s |
| IO 密集 | CPU数 | CPU数*5-10 | 1000-5000 | 300s |
| 异步任务 | CPU数 | CPU数*2 | 1000 | 300s |
| 定时任务 | CPU数 | CPU数 | 100 | 60s |

### 参考实现

查看 Lab-00 中的实现:
- `ApplicationConfiguration.java:54` - asyncExecutor
- `ApplicationConfiguration.java:84` - ioExecutor
- `ApplicationConfiguration.java:115` - cpuExecutor
- `ThreadUtil.java:117` - calculateOptimalThreadPoolSize()

### 参考资源

1. **Java Concurrency in Practice** (Brian Goetz et al.)
   - Chapter 8: Applying Thread Pools
   - 线程池大小计算的经典参考

2. **Thread Pool Sizing**
   - https://docs.oracle.com/javase/tutorial/concurrency/pools/

3. **Tomcat ThreadPool Configuration**
   - 实际生产环境的配置参考

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

