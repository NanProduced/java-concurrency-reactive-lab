# Lab-02: Synchronization Primitives（同步原语）

> **学习目标**：深入掌握 Java 同步原语的使用场景、性能特性和最佳实践

---

## 📚 学习内容

### 核心主题
1. **Lock vs Synchronized** - 理解两种锁机制的异同和选择标准
2. **Semaphore** - 掌握信号量的资源控制和限流策略
3. **Barrier** - 理解 CyclicBarrier 和 CountDownLatch 的区别
4. **wait-notify** - 掌握经典的线程通信机制
5. **死锁** - 理解死锁原理、检测方法和避免策略

### 能力提升
- ✅ 掌握 5 种核心同步原语的使用场景
- ✅ 理解公平锁vs非公平锁的性能权衡
- ✅ 学会使用决策树选择合适的同步机制
- ✅ 掌握死锁的诊断和避免方法

---

## 🚀 快速开始

### 1. 运行所有演示

```bash
# 演示 1: Lock vs Synchronized
mvn exec:java -Dexec.mainClass="nan.tech.lab02.lock.LockVsSynchronizedDemo"

# 演示 2: Semaphore（资源池限流）
mvn exec:java -Dexec.mainClass="nan.tech.lab02.semaphore.SemaphoreDemo"

# 演示 3: Barrier（多线程协调）
mvn exec:java -Dexec.mainClass="nan.tech.lab02.barrier.BarrierDemo"

# 演示 4: wait-notify（生产者-消费者）
mvn exec:java -Dexec.mainClass="nan.tech.lab02.waitnotify.WaitNotifyDemo"

# 演示 5: 死锁与避免
mvn exec:java -Dexec.mainClass="nan.tech.lab02.deadlock.DeadlockDemo"
```

### 2. 推荐学习顺序

```
第 1 步: Lock vs Synchronized
  → 理解基础锁机制的异同
  → 掌握可重入、公平锁、可中断、可超时的特性

第 2 步: Semaphore
  → 学习资源池管理和限流
  → 理解公平 vs 非公平策略

第 3 步: Barrier
  → 掌握 CountDownLatch 和 CyclicBarrier 的区别
  → 理解一次性 vs 可重用的场景

第 4 步: wait-notify
  → 掌握经典的生产者-消费者模式
  → 理解为什么用 while 而不是 if

第 5 步: 死锁
  → 理解死锁的 4 个必要条件
  → 学会诊断和避免死锁
```

---

## 📖 详细内容

### 1. Lock vs Synchronized 对比

**文件**: `nan.tech.lab02.lock.LockVsSynchronizedDemo`

#### 核心对比表

| 特性 | synchronized | ReentrantLock |
|------|-------------|---------------|
| 使用便利性 | ✅ 简洁，自动释放 | ❌ 需手动释放 |
| 可重入性 | ✅ 支持 | ✅ 支持 |
| 公平/非公平 | ❌ 仅非公平 | ✅ 可选择 |
| 可中断 | ❌ 不支持 | ✅ 支持 |
| 可超时 | ❌ 不支持 | ✅ 支持 |
| 条件变量 | ❌ 单一 wait/notify | ✅ 多个 Condition |
| 性能（低竞争） | ✅ 稍快 | ✅ 相当 |
| 性能（高竞争） | ✅ 相当 | ✅ 稍快 |
| JVM 优化 | ✅ 偏向锁、轻量级锁 | ❌ 无特殊优化 |

#### 决策树

```
需要高级特性（可中断/超时/公平锁/多条件）？
  ├─ 是 → 使用 ReentrantLock
  └─ 否 → 简单场景？
      ├─ 是 → 使用 synchronized（推荐）
      └─ 否 → 性能敏感且高竞争？
          ├─ 是 → 测试对比后选择
          └─ 否 → 使用 synchronized（默认选择）
```

#### 演示场景
1. 基本计数器对比（性能测试）
2. 可重入性演示（嵌套调用）
3. 公平锁 vs 非公平锁（性能权衡）
4. 可中断锁（响应中断）
5. 超时获取锁（避免无限等待）

---

### 2. Semaphore（信号量）

**文件**: `nan.tech.lab02.semaphore.SemaphoreDemo`

#### 核心概念

```
Semaphore = 停车场管理
┌─────────────────────────────────┐
│  停车场（资源池）                │
│  ┌──┐ ┌──┐ ┌──┐ ← 3 个车位      │
│  │🚗│ │🚗│ │🚗│   （permits=3）  │
│  └──┘ └──┘ └──┘                │
│                                 │
│  等待区（阻塞队列）              │
│  🚗 🚗 🚗 ← 等待进入            │
└─────────────────────────────────┘

acquire() → 获取许可（进入停车场）
release() → 释放许可（离开停车场）
```

#### 使用场景
- 数据库连接池限流（最多 N 个连接）
- API 限流（QPS 控制）
- 资源池管理（线程池、对象池）
- 流量控制（限制并发请求数）

#### 演示场景
1. 数据库连接池（最多 3 个并发连接）
2. 公平 vs 非公平策略（先到先得 vs 性能优先）
3. tryAcquire 超时机制（避免无限期阻塞）

---

### 3. CyclicBarrier vs CountDownLatch

**文件**: `nan.tech.lab02.barrier.BarrierDemo`

#### 核心对比

| 特性 | CountDownLatch | CyclicBarrier |
|------|----------------|---------------|
| 计数方向 | 递减（N → 0） | 递增（0 → N） |
| 可重用性 | ❌ 一次性 | ✅ 可循环使用 |
| 等待方式 | await() 等待归零 | await() 等待集齐 |
| 触发动作 | ❌ 无 | ✅ barrierAction |
| 典型场景 | 主线程等待子任务 | 多线程互相等待 |

#### 形象比喻
- **CountDownLatch** = 赛跑起跑枪：所有选手就位 → 发令枪响 → 比赛开始
- **CyclicBarrier** = 旅游集合点：所有游客到齐 → 继续前进 → 下一站再集合

#### 演示场景
1. CountDownLatch - 赛跑发令（主线程等待）
2. CyclicBarrier - 旅游集合（多线程互等）
3. CyclicBarrier 可重用性（多轮游戏）

---

### 4. wait-notify 机制

**文件**: `nan.tech.lab02.waitnotify.WaitNotifyDemo`

#### 核心原理

```
wait/notify = 餐厅叫号系统
┌─────────────────────────────────────┐
│  厨房（生产者）    取餐区（缓冲队列） │
│     👨‍🍳  ───→   🍔🍔🍔              │
│                   ↓                │
│  等候区（消费者）  notify()         │
│     👤👤  ←───  wait()             │
└─────────────────────────────────────┘

队列满 → 生产者 wait()
队列空 → 消费者 wait()
生产后 → notify() 唤醒消费者
消费后 → notify() 唤醒生产者
```

#### 关键要点
1. wait() 必须在 synchronized 块中调用
2. wait() 会释放锁，让其他线程执行
3. notify() 唤醒一个等待线程
4. notifyAll() 唤醒所有等待线程
5. **使用 while 循环检查条件（防止虚假唤醒）**

#### 演示场景
1. 基本生产者-消费者（单生产者单消费者）
2. 多生产者多消费者（notifyAll 的必要性）

---

### 5. 死锁与避免

**文件**: `nan.tech.lab02.deadlock.DeadlockDemo`

#### 死锁的 4 个必要条件

1. **互斥条件**：资源不能被共享，只能由一个线程使用
2. **持有并等待**：线程持有资源的同时，等待获取其他资源
3. **不可抢占**：资源不能被强制剥夺，只能主动释放
4. **循环等待**：存在资源的循环等待链

> **破坏任一条件即可避免死锁！**

#### 死锁场景分类

```
场景 1：循环等待（经典死锁）
  A 持有锁1，等待锁2
  B 持有锁2，等待锁1
  → A ←
  ↓   ↑
  B ←

场景 2：资源竞争（多个锁）
  多个线程以不同顺序获取多个锁

场景 3：顺序死锁（隐藏的循环）
  转账: A→B 和 B→A 同时发生

场景 4：动态死锁（运行时产生）
  根据条件动态获取锁，顺序不一致
```

#### 避免策略
1. **固定锁顺序**：所有线程按相同顺序获取锁
2. **尝试获取锁**：使用 tryLock() 避免无限等待
3. **超时机制**：设置获取锁的超时时间
4. **死锁检测**：使用 JConsole/JStack 检测

#### 演示场景
1. 经典循环等待死锁（教学演示）
2. 正确的锁顺序（避免死锁）
3. 使用 tryLock 避免死锁
4. 转账死锁与解决（锁排序）

---

## 🎯 同步原语选择决策树

### 场景分析决策

```
问题: 需要限制并发数？
  ├─ 是 → Semaphore
  │       例如：数据库连接池、API 限流
  │
  └─ 否 → 需要多线程互相等待？
      ├─ 是 → 需要重复使用？
      │   ├─ 是 → CyclicBarrier
      │   │       例如：多轮游戏、分阶段任务
      │   │
      │   └─ 否 → CountDownLatch
      │           例如：赛跑发令、主线程等待
      │
      └─ 否 → 需要生产者-消费者模式？
          ├─ 是 → wait-notify 或 BlockingQueue
          │       例如：任务队列、缓冲区
          │
          └─ 否 → 需要高级特性（可中断/超时/公平锁）？
              ├─ 是 → ReentrantLock
              │       例如：可中断的任务、超时控制
              │
              └─ 否 → synchronized（默认选择）
                      例如：简单的同步块
```

---

## 📊 性能对比与建议

### 性能特性对比表

| 同步原语 | 性能 | 灵活性 | 使用复杂度 | 推荐场景 |
|---------|------|-------|-----------|---------|
| synchronized | ★★★★★ | ★★☆☆☆ | ★★★★★ | 简单同步 |
| ReentrantLock | ★★★★☆ | ★★★★★ | ★★★☆☆ | 高级特性 |
| Semaphore | ★★★★☆ | ★★★★☆ | ★★★★☆ | 资源限流 |
| CountDownLatch | ★★★★★ | ★★★☆☆ | ★★★★☆ | 一次性协调 |
| CyclicBarrier | ★★★★☆ | ★★★★☆ | ★★★☆☆ | 可重用协调 |
| wait-notify | ★★★★★ | ★★☆☆☆ | ★★☆☆☆ | 传统通信 |

### 使用建议

#### 1. synchronized（默认选择）
**优先使用场景**：
- 简单的同步块
- 方法级别同步
- 无高级特性需求

**避免使用**：
- 需要可中断
- 需要超时控制
- 需要公平锁

#### 2. ReentrantLock（高级特性）
**优先使用场景**：
- 需要响应中断
- 需要超时控制
- 需要公平锁
- 需要多个条件变量

**避免使用**：
- 简单场景（过度设计）
- 容易忘记 unlock()

#### 3. Semaphore（资源限流）
**优先使用场景**：
- 连接池管理
- API 限流
- 资源池控制

**避免使用**：
- 简单的互斥锁（用 synchronized）
- 一对一协调（用 CountDownLatch）

#### 4. CountDownLatch（一次性协调）
**优先使用场景**：
- 主线程等待多个子任务
- 一次性的启动/结束信号

**避免使用**：
- 需要重复使用（用 CyclicBarrier）

#### 5. CyclicBarrier（可重用协调）
**优先使用场景**：
- 多线程互相等待
- 分阶段任务
- 多轮游戏

**避免使用**：
- 主线程等待（用 CountDownLatch）

---

## 🔍 常见问题与陷阱

### 1. synchronized 的常见误区

```java
// ❌ 错误：锁定不同对象
synchronized (new Object()) {
    // 每次都是新对象，无法同步
}

// ✅ 正确：锁定同一个对象
private final Object lock = new Object();
synchronized (lock) {
    // 所有线程锁定同一个对象
}
```

### 2. ReentrantLock 忘记释放锁

```java
// ❌ 错误：可能不释放锁
lock.lock();
doSomething(); // 如果抛异常，锁不会释放
lock.unlock();

// ✅ 正确：finally 中释放锁
lock.lock();
try {
    doSomething();
} finally {
    lock.unlock(); // 保证释放
}
```

### 3. wait-notify 使用 if 而不是 while

```java
// ❌ 错误：虚假唤醒问题
synchronized (lock) {
    if (condition) { // ❌ 使用 if
        lock.wait();
    }
}

// ✅ 正确：防止虚假唤醒
synchronized (lock) {
    while (condition) { // ✅ 使用 while
        lock.wait();
    }
}
```

### 4. 死锁的常见原因

```java
// ❌ 错误：不同顺序获取锁
// 线程 A
synchronized (lock1) {
    synchronized (lock2) { ... }
}
// 线程 B
synchronized (lock2) { // ❌ 顺序不同
    synchronized (lock1) { ... }
}

// ✅ 正确：固定锁顺序
// 所有线程都按 lock1 → lock2 的顺序
```

---

## 🛠️ 诊断与调试

### 死锁检测

#### 1. 使用 jstack
```bash
# 找到 Java 进程 ID
jps

# 导出线程堆栈
jstack <pid> > thread_dump.txt

# 查看死锁信息
# 在输出中搜索 "Found one Java-level deadlock"
```

#### 2. 使用 JConsole
1. 启动 JConsole：`jconsole`
2. 连接到目标进程
3. 选择"线程"选项卡
4. 点击"检测死锁"按钮

#### 3. 使用 ThreadMXBean（代码检测）
```java
ThreadMXBean bean = ManagementFactory.getThreadMXBean();
long[] threadIds = bean.findDeadlockedThreads();
if (threadIds != null) {
    System.out.println("检测到死锁！");
}
```

---

## 📚 进阶主题

### 1. StampedLock（Java 8+）
- 乐观读锁
- 更高的性能
- 适合读多写少场景

### 2. LockSupport
- 更底层的线程阻塞/唤醒
- park() / unpark()
- 比 wait/notify 更灵活

### 3. Phaser（Java 7+）
- CyclicBarrier 的增强版
- 动态调整参与者数量
- 适合复杂的多阶段任务

---

## ✅ 完成检查清单

- [ ] 运行所有 5 个演示程序
- [ ] 理解每种同步原语的适用场景
- [ ] 能够根据决策树选择合适的同步机制
- [ ] 理解死锁的 4 个必要条件
- [ ] 掌握使用 jstack 检测死锁
- [ ] 理解公平锁 vs 非公平锁的权衡
- [ ] 理解为什么 wait 必须用 while 循环

---

## 📖 参考资料

- [Java Concurrency in Practice](https://jcip.net/)
- [The Art of Multiprocessor Programming](https://www.elsevier.com/books/the-art-of-multiprocessor-programming/herlihy/978-0-12-415950-1)
- [Doug Lea - Concurrent Programming in Java](http://gee.cs.oswego.edu/dl/cpj/)
- [Oracle Java Documentation - Concurrency Utilities](https://docs.oracle.com/javase/tutorial/essential/concurrency/)

---

**下一步**: 进入 [Lab-03: Executors & Pools](../lab-03-executors) 学习线程池设计与调优
