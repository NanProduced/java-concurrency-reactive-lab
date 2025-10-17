# Lab-01: Thread Basics (线程基础)

> **学习目标**: 掌握 Java 线程的基础概念：volatile、synchronized、原子性、happens-before 规则

## 📋 模块概览

本模块通过理论与实验结合，深入理解 Java 线程安全的核心机制。

### 核心学习点

| 主题 | 内容 | 验收标准 |
|------|------|--------|
| **volatile** | 可见性、禁止重排序、内存屏障 | 写穿透/读脏验证 |
| **synchronized** | 监听器锁、同步块、方法同步 | 竞态条件重现 |
| **原子性** | LongAdder vs AtomicLong、CAS | 性能对标 (JMH) |
| **Happens-Before** | 6 条规则、内存模型 | 规则验证用例 |

## 🎯 实验清单

### Tier 1: 基础验证 (1-2 天)

- [ ] **VolatileVisibilityTest** - volatile 可见性演示
- [ ] **SynchronizedRaceTest** - synchronized 竞态条件修复
- [ ] **AtomicVsLongAdderTest** - 原子操作对比

### Tier 2: 深度理解 (2-3 天)

- [ ] **HappensBeforeTest** - 6 条规则验证
- [ ] **MemoryModelDiagnosticTest** - 诊断工具演示
- [ ] **ThreadSafetyPatternTest** - 常见模式验证

### Tier 3: 性能分析 (1-2 天)

- [ ] **AtomicBenchmark** - 原子操作基准测试
- [ ] **VolatileBenchmark** - volatile vs 普通字段性能对比
- [ ] **ThreadingScalabilityTest** - 并发度扩展性测试

## 📚 关键知识点

### 1. volatile 关键字

```java
// 问题：多线程环境下可见性与重排序
volatile boolean flag = false;

// 保证：
// 1. 可见性：写操作立即对所有线程可见
// 2. 顺序性：禁止重排序 (在其他操作之前/之后)
// 3. 内存屏障：编译器和 CPU 级别的同步
```

**常见坑**：
- volatile 不保证原子性 (volatile i++不安全)
- 过度使用 volatile 会影响性能

### 2. synchronized 同步机制

```java
// 对象级别锁 (互斥锁)
synchronized(lock) {
    // 原子操作：
    // 1. 获取锁
    // 2. 执行同步块
    // 3. 释放锁 (即使抛异常)
}

// 保证可见性与原子性
```

**常见坑**：
- 死锁：嵌套持有多个锁且获取顺序不一致
- 锁竞争：高并发下性能下降
- 错误的锁对象：synchronized(new Object()) 每次新建

### 3. 原子操作

```java
// AtomicLong: 单个字段的原子操作 (CAS)
// 性能：中等，适合竞争不剧烈的场景

// LongAdder: 多个 Cell 的累加 (分段)
// 性能：高，适合高竞争场景 (读性能差)

// 适用场景决策树：
// - 高竞争 + 写多 → LongAdder
// - 低竞争 + 需要 get 精确值 → AtomicLong
```

### 4. Happens-Before 规则

6 条保证内存可见性的规则：

1. **Program Order**: 单线程内的有序性
2. **Volatile**: 写 volatile 发生于读 volatile 之前
3. **Synchronized**: 解锁发生于加锁之前
4. **Thread Start**: start() 发生于线程中的代码之前
5. **Thread Termination**: 线程中的代码发生于 join() 之前
6. **Transitivity**: 传递性

## 📚 5 天学习路径

### Day 1: volatile 关键字（基础 → 进阶）
```
目标: 理解为什么需要 volatile，以及 volatile 能做什么/不能做什么

步骤:
  1. 阅读本 README 中的 "volatile 关键字" 部分 (10 min)
  2. 运行 Demo:
     mvn exec:java -Dexec.mainClass="nan.tech.lab01.thread.VolatileDemo"
  3. 观察输出，特别是:
     ✓ 演示 1 & 2: 可见性问题（无 volatile 的无限等待 vs WITH volatile）
     ✓ 演示 3 & 4: 有序性问题（重排序风险）
     ✓ 演示 5: volatile 不保证原子性
  4. 思考题: "如果没有 volatile，程序会怎样？"
  5. 完成 5 min 小测验
```

### Day 2: synchronized 同步机制（基础 → 陷阱）
```
目标: 理解 synchronized 如何解决竞态条件，以及如何避免死锁

步骤:
  1. 阅读本 README 中的 "synchronized 同步机制" 部分 (15 min)
  2. 运行 Demo:
     mvn exec:java -Dexec.mainClass="nan.tech.lab01.synchronization.SynchronizedDemo"
  3. 观察输出，特别是:
     ✓ 演示 1 & 2: 竞态条件（数据丢失 vs 正确结果）
     ✓ 演示 3: 三种 synchronized 形式（方法 vs 块 vs 监视器）
     ✓ 演示 4 & 5: 死锁问题和解决方案
  4. 思考题: "为什么死锁会发生？如何防止？"
  5. 动手:修改代码尝试不同的锁顺序
```

### Day 3: 原子操作（CAS 原理 → 性能选择）
```
目标: 理解无锁原子性（CAS），以及何时选择 AtomicLong vs LongAdder

步骤:
  1. 阅读本 README 中的 "原子操作" 部分 (20 min)
  2. 运行 Demo:
     mvn exec:java -Dexec.mainClass="nan.tech.lab01.atomicity.AtomicVsLongAdder"
  3. 观察输出，特别是:
     ✓ 演示 1: CAS 原理（比较并交换的硬件级原子性）
     ✓ 演示 2 & 3: 低竞争 vs 高竞争性能对比
     ✓ 演示 5: 决策树（何时用哪个）
  4. 思考题: "为什么 LongAdder 在高竞争下更快？"
  5. 实验: 修改线程数观察性能变化
```

### Day 4: Happens-Before 规则（6 条规则的理论与应用）
```
目标: 理解内存模型，掌握 6 条 HB 规则的实际应用

步骤:
  1. 阅读本 README 中的 "Happens-Before 规则" 部分 (20 min)
  2. 如果 HappensBeforeDemo 已创建，运行它:
     mvn exec:java -Dexec.mainClass="nan.tech.lab01.memory.HappensBeforeDemo"
  3. 或者通过前面的 Demo 中的演示 6 理解 HB 规则
  4. 重点掌握:
     ✓ 规则 1: Program Order（单线程顺序）
     ✓ 规则 2: Volatile（volatile 读写的 HB 关系）
     ✓ 规则 3: Synchronized（锁的 HB 关系）
     ✓ 规则 4 & 5: Thread Start/Termination
     ✓ 规则 6: Transitivity（传递性）
  5. 思考题: "volatile 读写之间的 HB 关系如何保证可见性？"
```

### Day 5: 综合应用（诊断 + 决策）
```
目标: 能够诊断线程问题，做出正确的同步方案选择

步骤:
  1. 学习诊断指南（见下方 "诊断指南" 部分）
  2. 学习决策树（见下方 "决策树" 部分）
  3. 完成综合练习:
     - 给定一段有竞态条件的代码，识别问题
     - 选择合适的解决方案（volatile/synchronized/Atomic）
     - 解释为什么这样选择
  4. 回答关键问题:
     ✓ "为什么需要 volatile/synchronized/atomic？"
     ✓ "如何排查程序的线程问题？"
     ✓ "在高竞争场景下应该选择什么？"
```

---

## 🔧 快速启动

### 前置条件

```bash
# 确保已完成 Lab-00
cd ../lab-00-foundation && mvn clean install

# 返回 Lab-01
cd ../lab-01-thread-basics
```

### 构建与测试

```bash
# 编译
mvn clean compile

# 运行所有 Demo（带对比演示）
mvn exec:java -Dexec.mainClass="nan.tech.lab01.thread.VolatileDemo"
mvn exec:java -Dexec.mainClass="nan.tech.lab01.synchronization.SynchronizedDemo"
mvn exec:java -Dexec.mainClass="nan.tech.lab01.atomicity.AtomicVsLongAdder"

# 运行单元测试
mvn clean test

# 运行覆盖率分析
mvn clean test jacoco:report
open target/site/jacoco/index.html

# 运行变异测试
mvn org.pitest:pitest-maven:mutationCoverage
```

## 📊 预期成果

### 代码产出

```
src/main/java/nan/tech/lab01/
├── thread/
│   ├── VolatileDemo.java        # volatile 演示
│   ├── SynchronizedDemo.java    # synchronized 演示
│   └── ThreadStateMonitor.java  # 线程状态监控
├── synchronization/
│   ├── SynchronizedCounter.java # synchronized 计数器
│   ├── RaceConditionDemo.java   # 竞态条件演示
│   └── DeadlockDemo.java        # 死锁演示
├── atomicity/
│   ├── AtomicVsLongAdder.java   # 原子操作对比
│   └── CasDemo.java             # CAS 演示
└── examples/
    ├── HappensBeforeExample.java # HB 规则示例
    ├── MemoryBarrierDemo.java    # 内存屏障演示
    └── ThreadSafePatterns.java   # 线程安全模式集合
```

### 测试覆盖率目标

```
- 单元测试: ≥ 85% 代码覆盖率
- 变异测试: ≥ 75% 变异杀死率
- 集成测试: ≥ 5 个完整场景
```

### 性能基线

| 操作 | 吞吐量 (ops/sec) | 延迟 p99 | 备注 |
|-----|-----------------|---------|------|
| volatile 读 | ~2B | <1μs | 无竞争 |
| volatile 写 | ~1B | <1μs | 无竞争 |
| synchronized 获取 | ~10M | <1μs | 偏向锁 |
| AtomicLong.incrementAndGet() | ~100M | <1μs | 低竞争 |
| LongAdder.increment() | ~1B | <1μs | 高竞争 |

## 🐛 常见坑

### Pitfall-1: volatile 不保证原子性

```java
// ❌ 错误：volatile 不能保证 i++ 原子性
volatile int i = 0;
i++;  // 竞态条件！

// ✅ 正确：使用原子操作或同步
AtomicInteger i = new AtomicInteger(0);
i.incrementAndGet();
```

### Pitfall-2: synchronized 死锁

```java
// ❌ 死锁风险：嵌套获取不同顺序的锁
Thread-1: synchronized(lockA) { synchronized(lockB) {...} }
Thread-2: synchronized(lockB) { synchronized(lockA) {...} }

// ✅ 修复：确保所有线程按相同顺序获取
Thread-1: synchronized(lockA) { synchronized(lockB) {...} }
Thread-2: synchronized(lockA) { synchronized(lockB) {...} }
```

### Pitfall-3: 错误的 happens-before 理解

```java
// ❌ 错误：认为普通字段读写有可见性保证
int x = 0;
boolean flag = false;

// x 的写对 flag 的写后面，但对 flag 的读在另一线程没有顺序保证
// 原因：它们之间没有 HB 关系

// ✅ 正确：使用 volatile 建立 HB 关系
int x = 0;
volatile boolean flag = false;

// 现在：x 的写 → flag 的写 → flag 的读 → x 的读 (有保证)
```

## 🔍 诊断指南 - 如何排查线程问题

### 症状 1: 程序卡住（无限等待）

**可能原因**:
  - 缺少 volatile，导致读线程永远看不到写线程的更新
  - 死锁：多个线程互相等待对方的锁
  - 条件变量 wait() 没有被 notify()

**诊断步骤**:
```bash
# 1. 获取 Java 进程 ID
jps -l

# 2. 生成线程堆栈（查看是否都在等待）
jstack <pid> > threadstacks.txt

# 3. 查看输出中是否有 "waiting to lock" 字样
# 如果有，说明存在锁竞争/死锁

# 4. 如果找不到锁竞争，检查是否有 volatile 问题
# 在 Demo 中，可以尝试改为普通变量观察无限等待

# 5. 如果都是 RUNNABLE，说明可能是忙循环或高 CPU 占用
# 用 jps -l | xargs jstat -gccause 查看 GC 情况
```

**常见解决方案**:
  - ✅ 给 flag/condition 变量加 volatile
  - ✅ 检查锁获取顺序是否一致
  - ✅ 确保 wait/notify 配对使用

---

### 症状 2: 结果错误（数据丢失）

**可能原因**:
  - 竞态条件：没有同步，导致多个线程并发修改
  - volatile 误用：认为 volatile++ 是原子的

**诊断步骤**:
```bash
# 1. 运行 Demo 观察输出
# AtomicVsLongAdder 演示 5 会显示数据丢失

# 2. 改为 synchronized/AtomicLong/LongAdder 观察结果是否正确

# 3. 如果结果总是小于期望值，说明是竞态条件

# 4. 用工具检测数据竞争
# ThreadSanitizer (TSan) for Java: 需要特殊 JVM
# 或者用 IDE 的代码分析工具
```

**常见解决方案**:
  - ✅ 使用 synchronized 保护临界区
  - ✅ 改为 AtomicLong/AtomicInteger
  - ✅ 在高竞争下改为 LongAdder

---

### 症状 3: 性能差

**可能原因**:
  - 高竞争下用了 AtomicLong 而不是 LongAdder
  - synchronized 导致的线程阻塞
  - 频繁的 CAS 失败和重试

**诊断步骤**:
```bash
# 1. 运行 Demo 观察性能数据
# AtomicVsLongAdder 演示 3 对比高竞争场景

# 2. 获取 CPU 火焰图
# async-profiler:
#   ./profiler.sh -d 30 -f /tmp/flamegraph.html <pid>

# 3. 查看是否有高竞争的热点

# 4. 用 jmc (Java Mission Control) 查看 lock profiling
```

**常见解决方案**:
  - ✅ 高竞争时改用 LongAdder
  - ✅ 减少锁持有的时间
  - ✅ 使用分段锁或其他并发结构

---

### 症状 4: 不确定性（结果不稳定）

**可能原因**:
  - Happens-Before 关系不明确
  - 编译器重排序导致的顺序问题
  - 多核 CPU 的可见性问题

**诊断步骤**:
```bash
# 1. 运行 VolatileDemo 演示 3 & 4，观察是否有重排序

# 2. 改为 synchronized 或 volatile 观察是否稳定

# 3. 如果改为 volatile 后就稳定，说明是 Happens-Before 问题
```

**常见解决方案**:
  - ✅ 明确建立 Happens-Before 关系（volatile/synchronized）
  - ✅ 使用 j.u.c 中的并发工具而不是手动同步

---

## 🌳 决策树 - 选择正确的同步方案

### 问题: 多个线程需要访问共享变量，如何保证安全？

```
需要同步吗？
├─ 否 → 用 ThreadLocal 或每个线程独立变量
│  └─ 示例：thread-local 的 SimpleDateFormat
│
└─ 是 → 竞争度如何？
   │
   ├─ 无竞争（主要是读）→ 用 volatile
   │  ├─ 原因: 只需可见性，不需要原子性
   │  ├─ 示例: 标志位、关闭信号
   │  └─ 代码: private volatile boolean running = true;
   │
   ├─ 低到中等竞争（≤8 线程）→ 用 synchronized 或 AtomicLong
   │  ├─ synchronized 优势: 简洁、自动处理异常
   │  ├─ Atomic 优势: 无锁、性能稍好
   │  └─ 代码: synchronized(obj) { count++; } 或 atomicCount.incrementAndGet();
   │
   ├─ 高竞争（≥8 线程，热点操作）→ 用 LongAdder
   │  ├─ 原因: 分段设计，减少竞争
   │  ├─ 缺点: sum() 需要遍历所有分段
   │  └─ 代码: adder.increment(); final long value = adder.sum();
   │
   └─ 很多线程，需要复杂逻辑 → 用 ReadWriteLock 或 StampedLock
      ├─ 原因: 允许多个读线程并发
      └─ 示例: ConcurrentHashMap 内部使用

需要原子操作吗？
├─ 否（只读或简单变量）→ 用 volatile
│
└─ 是（read-modify-write）→
   ├─ 低竞争 → AtomicLong
   │  ├─ 优势: 精确值、无遍历开销
   │  └─ 示例: 错误计数、性能指标单点追踪
   │
   └─ 高竞争 → LongAdder
      ├─ 优势: 高吞吐量
      └─ 示例: 请求计数、访问日志计数
```

### 快速参考表

| 场景 | 推荐方案 | 原因 |
|------|---------|------|
| 只需可见性，无竞争 | `volatile` | 性能最好，够用 |
| 低竞争计数 | `synchronized` / `AtomicLong` | 简洁/无锁 |
| 高竞争计数 | `LongAdder` | 分段降低竞争 |
| 低竞争读多 | `AtomicLong` | 精确值 |
| 高竞争读少 | `LongAdder` | 吞吐量优先 |
| 旗标/开关 | `volatile boolean` | 轻量、足够 |
| 复杂状态 | `synchronized` 块 | 原子性+可见性 |
| 多读少写 | `ReadWriteLock` | 并发读优化 |

---

## 📖 参考资源

- [Java Memory Model](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.4)
- [Doug Lea 的 JMM Cookbook](http://gee.cs.oswego.edu/dl/cpj/jmm.html)
- [JSR 133](https://www.jcp.org/en/jsr/detail?id=133) - 内存模型规范
- [本 Lab 完整对比演示代码](./src/main/java/nan/tech/lab01/) - 对比式学习

## ✅ 验收标准

### 代码质量

- [ ] 所有核心类有 Javadoc 注释
- [ ] 代码注释密度 ≥ 70%
- [ ] 线程安全检查通过 (no data races)

### 测试完整性

- [ ] 单元测试覆盖率 ≥ 85%
- [ ] 变异测试杀死率 ≥ 75%
- [ ] 集成测试通过所有场景

### 文档完整性

- [ ] README 完成度 ≥ 90%
- [ ] 所有代码示例可独立运行
- [ ] 性能基线数据完整

### 学习价值

- [ ] 包含至少 3 个常见坑的完整演示代码
- [ ] 有清晰的诊断指南 (如何排查线程问题)
- [ ] 提供决策树 (何时用 volatile/synchronized/Atomic)

## 🚀 下一步

完成本模块后：
1. 更新 PROGRESS_TRACKER.md
2. 提交代码到 git
3. 准备 Lab-02: Synchronization Primitives

---

**创建时间**: 2025-10-17
**状态**: ⏳ 进行中
**预期完成**: 2025-10-24
