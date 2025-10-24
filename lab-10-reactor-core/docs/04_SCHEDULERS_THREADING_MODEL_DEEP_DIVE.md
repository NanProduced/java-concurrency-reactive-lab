# Lab-10 Phase 3: 调度器与线程模型深度剖析

> **学习目标**: 理解 Reactor 的调度器设计、线程模型以及如何通过调度器优化性能
>
> **难度**: ⭐⭐⭐⭐ (较高，涉及多线程与架构设计)
>
> **阅读时间**: 30-40 分钟

---

## 1. 核心概念导览

### 什么是调度器（Scheduler）？

调度器是 Reactor 中**控制异步任务执行的地点**的抽象。它决定：
- **在哪个线程执行**（当前线程、线程池、事件循环等）
- **何时执行**（立即、延迟、按需）
- **如何执行**（串行、并行、有序）

```
发布者 ---publish---> 调度器 ---execute---> 订阅者
                     ↓
              决定执行环境
              (线程/时机/方式)
```

### 为什么需要调度器？

**问题**: 在单线程中，所有操作都阻塞，性能很差

**解决**: 调度器将操作分散到不同线程，提高吞吐量

```
单线程（❌ 不好）      多线程调度（✅ 推荐）
────────────────────────────────────────
操作1 (100ms)  →  线程1: 操作1 (100ms)
  ↓             线程2: 操作2 (100ms)  ← 并行执行
操作2 (100ms)  线程3: 操作3 (100ms)
  ↓
操作3 (100ms)  总耗时: ~100ms (而不是 300ms)

总耗时: 300ms   ✅ 吞吐量提升 3 倍
```

---

## 2. 四种核心调度器详解

### 2.1 Schedulers.immediate() - 立即调度器

**工作原理**: 在当前线程立即执行，**不创建新线程**

```java
Flux.range(1, 3)
    .publishOn(Schedulers.immediate())  // 在当前线程执行
    .map(i -> {
        System.out.println(Thread.currentThread().getName());
        return i * 2;
    })
    .subscribe();

输出: main (一直都是)
```

**特点**:
- ✅ 无线程切换开销
- ✅ 适合轻量级操作
- ❌ 无法并行化
- ❌ 可能阻塞调用线程

**适用场景**:
- CPU 密集但耗时短的操作（< 1ms）
- 需要最小延迟的场景
- 快速的数据转换

**性能特征**:
```
吞吐量:        最高（无线程切换）
延迟:         最低（立即执行）
内存占用:      最少（无线程对象）
上下文切换:    无
```

---

### 2.2 Schedulers.single() - 单线程调度器

**工作原理**: 使用**单个线程**顺序执行所有任务

```java
Schedulers.single()  // 每次都返回同一个线程
    .schedule(() -> task1);   // 线程T1
    .schedule(() -> task2);   // 线程T1 (等待 task1)
    .schedule(() -> task3);   // 线程T1 (等待 task2)
```

**线程模型图**:
```
任务队列        线程 T1
─────────────┬───────────────────
task1        │ 执行中
task2        │ 等待...
task3        │ 等待...
             │
time ─────────────────────────>
     |task1| |task2| |task3|
```

**特点**:
- ✅ 保证顺序执行
- ✅ 适合状态变化敏感的操作
- ❌ 无法并行化
- ❌ 如果一个任务阻塞，整条链堵塞

**适用场景**:
- 数据库操作（需要事务一致性）
- 状态管理（防止竞态）
- 串行处理流

**性能特征**:
```
吞吐量:        中等（单线程，有队列）
延迟:         中等（可能排队等待）
内存占用:      小（只有一个线程）
上下文切换:    少（只有一个线程）
```

---

### 2.3 Schedulers.boundedElastic() - 有界弹性调度器

**工作原理**:
- 使用**线程池**（最多 `核数*10` 个线程）
- **可重用线程**（有效期 60s）
- **队列有上限**（避免内存溢出）

```java
Schedulers.boundedElastic()
    // 创建线程池: max = CPU核数 * 10
    //           queueSize = 100000
    // 线程空闲 60s 后回收
```

**线程池模型**:
```
任务队列 (max 100k)     线程池 (max CPU*10)
──────────────────────┬──────────────────────
[task1] → 线程1  执行中 ✅
[task2] → 线程2  执行中 ✅
[task3] → 线程3  等待...
[task4] → 队列    等待中
...
[taskN] → 队列    等待中

✅ 任务并行执行，吞吐量高
⚠️ 队列满时拒绝新任务
```

**特点**:
- ✅ 并行执行能力强
- ✅ 线程复用，创建开销小
- ✅ 队列有限，防止 OOM
- ❌ 有队列等待延迟

**配置参数**:
```java
// 默认参数
maxThreads = Math.max(8, Runtime.getRuntime().availableProcessors() * 10)
queueSize = 100000
keepAliveTime = 60 seconds
```

**适用场景**:
- I/O 密集操作（网络、数据库）
- 需要中等并行度的场景
- 生产环境标准选择

**性能特征**:
```
吞吐量:        高（并行执行）
延迟:         中等（可能排队）
内存占用:      中等（线程+队列）
上下文切换:    多（频繁切换线程）
```

---

### 2.4 Schedulers.parallel() - 并行调度器

**工作原理**:
- 固定线程数 = **CPU 核数**
- 针对 **CPU 密集** 优化
- 无队列（任务直接分配）

```java
Schedulers.parallel()
    // 线程数 = CPU 核数（通常 4-8）
    // 每个线程一个任务，不排队
    // 如果线程都忙，新任务等待
```

**线程分配模型**:
```
4 核 CPU 场景：
──────────────────────────────────────────
线程1    线程2    线程3    线程4
 │       │        │        │
task1   task2    task3    task4
 │       │        │        │
⏳       ⏳       ⏳       ⏳
(等待)  (等待)  (等待)  (等待)

✅ 最优利用 CPU 核数
❌ 如果任务 I/O 阻塞，线程浪费
```

**特点**:
- ✅ CPU 利用率最高
- ✅ 上下文切换最少
- ❌ 不适合 I/O 密集
- ❌ 阻塞一个线程会降低吞吐量

**适用场景**:
- CPU 密集计算（数学、加密、编码）
- 流处理（实时数据聚合）
- 避免 I/O 操作

**性能特征**:
```
吞吐量:        高（无队列等待）
延迟:         低（少量上下文切换）
内存占用:      最少（固定小线程数）
上下文切换:    最少（线程数 = CPU 核数）
```

---

## 3. publishOn vs subscribeOn 对比

### 核心区别

| 操作 | publishOn | subscribeOn |
|------|-----------|------------|
| **作用位置** | 在链的下游切换 | 在链的上游切换 |
| **何时生效** | 处理数据时 | 请求时（向上游） |
| **线程切换点** | onNext/onError/onComplete | 订阅链 |
| **位置影响** | 后面的操作生效 | 前面的操作生效 |

### 执行流程对比

**场景**: 三个操作 map1 → map2 → map3

```
publishOn(T2):
──────────────────────────────────────────────────
线程 T1          线程 T2           线程 T3
  │               │                 │
map1            map2              map3
  └──publishOn──→  │                │
（T1 执行）      （T2 执行）      （T3 执行？）
                  └──publishOn──→
```

```
subscribeOn(T2):
──────────────────────────────────────────────────
线程 T2          线程 T2           线程 T1
  │               │                 │
map1            map2              map3
  │               │                 │
（T2 执行）      （T2 执行）      （T2 执行？）
  └──subscribeOn──┘
（都在 T2 上）
```

### 实战示例

```java
// ❌ 错误用法：publishOn/subscribeOn 位置不对
Flux.range(1, 5)
    .map(i -> {
        System.out.println("map1: " + Thread.currentThread().getName());
        return i;
    })
    .subscribeOn(Schedulers.boundedElastic())  // ❌ 这里改变不了 range 的线程
    .map(i -> {
        System.out.println("map2: " + Thread.currentThread().getName());
        return i * 2;
    })
    .publishOn(Schedulers.parallel())           // ❌ 这里 publishOn 已经太晚
    .subscribe();
```

```java
// ✅ 正确用法：明确的线程模型
Flux.range(1, 5)
    .subscribeOn(Schedulers.boundedElastic())  // ✅ 在线程池上执行 range
    .map(i -> {
        System.out.println("map1: " + Thread.currentThread().getName());  // 线程池
        return i;
    })
    .publishOn(Schedulers.parallel())           // ✅ 切换到 CPU 线程池
    .filter(i -> {
        System.out.println("filter: " + Thread.currentThread().getName()); // parallel
        return i > 2;
    })
    .map(i -> expensiveCpuWork(i))             // ✅ CPU 密集在 parallel 上
    .subscribe();
```

---

## 4. 线程切换的代价

### 上下文切换成本

```
场景：1000 个元素，每个元素处理 1μs

单线程（无切换）：
  总耗时 = 1000 * 1μs = 1ms

多线程切换（每次 10μs）：
  切换成本 = 1000 * 10μs = 10ms
  处理成本 = 1000 * 1μs = 1ms
  ────────────────────────
  总耗时 = 11ms ❌ 性能反而下降！

结论：如果单个操作耗时 < 10μs，不值得线程切换
```

### 切换成本的三个来源

```
1. 线程创建      2. 上下文切换      3. 缓存失效
   (1ms)          (100-1000μs)       (缓存命中率↓)
      ↓               ↓                   ↓
   ┌──────┐      ┌─────────┐         ┌────────┐
   │Thread│      │CPU 状态 │         │L1/L2  │
   │创建  │ →    │保存/恢复│ →       │缓存   │
   │      │      │         │         │混乱   │
   └──────┘      └─────────┘         └────────┘
```

---

## 5. 调度器选择决策树

```
开始
  │
  ├─ 是否涉及 I/O（网络、数据库、文件）？
  │  ├─ 是 → boundedElastic() ✅
  │  │       (为阻塞预留充足线程)
  │  │
  │  └─ 否 → 继续
  │
  ├─ 是否是 CPU 密集计算？
  │  ├─ 是 → parallel() ✅
  │  │       (充分利用 CPU 核数)
  │  │
  │  └─ 否 → 继续
  │
  ├─ 是否需要顺序执行（事务、状态）？
  │  ├─ 是 → single() ✅
  │  │       (保证顺序)
  │  │
  │  └─ 否 → 继续
  │
  ├─ 是否是轻量级操作（< 1μs）？
  │  ├─ 是 → immediate() ✅
  │  │       (无线程切换开销)
  │  │
  │  └─ 否 → boundedElastic() ✅ (默认)
  │
  结束
```

### 场景决策表

| 场景 | 操作类型 | 推荐调度器 | 理由 |
|------|---------|-----------|------|
| 数据库查询 | I/O 阻塞 | boundedElastic | 阻塞线程充足 |
| HTTP 请求 | I/O 阻塞 | boundedElastic | 网络延迟高 |
| 数学计算 | CPU 密集 | parallel | 充分利用 CPU |
| 数据加密 | CPU 密集 | parallel | 无网络等待 |
| 状态更新 | 同步强 | single | 避免竞态 |
| 日志记录 | I/O + 快 | boundedElastic | 兼顾性能 |
| 实时数据 | CPU 密集 | parallel | 低延迟需求 |

---

## 6. 性能对标基线

### 场景 1: I/O 密集（数据库查询）

```
调度器对比（1000 个 10ms 延迟的查询）

immediate():        ❌ 阻塞主线程，无法并发
                    → 总耗时 ≈ 10s

single():          ⚠️ 顺序执行，一个接一个
                    → 总耗时 ≈ 10s

boundedElastic():  ✅ 并发执行，充分利用
                    → 总耗时 ≈ 100ms (吞吐量提升 100 倍)

parallel():        ⚠️ 线程数有限（CPU 核数）
                    → 总耗时 ≈ 1-2s (可用，但不理想)

推荐：boundedElastic()
```

### 场景 2: CPU 密集（数据加密）

```
调度器对比（1000 个 1ms CPU 密集操作）

immediate():       ❌ 完全阻塞，无法并行
                    → 总耗时 ≈ 1s (单线程)

single():         ❌ 顺序执行，同样阻塞
                    → 总耗时 ≈ 1s

boundedElastic(): ⚠️ 线程过多导致过度切换
                    → 总耗时 ≈ 200-300ms (上下文切换多)

parallel():       ✅ 线程数 = CPU 核数，最优
                    → 总耗时 ≈ 125ms (在 8 核 CPU 上)

推荐：parallel()
```

---

## 7. 常见陷阱与最佳实践

### ❌ 陷阱 1: subscribeOn 位置错误

```java
// ❌ 错误：subscribeOn 在下游，无效
Flux.range(1, 1000)
    .map(this::expensiveOperation)
    .subscribeOn(Schedulers.parallel())  // ❌ 太晚了！
```

```java
// ✅ 正确：subscribeOn 在上游
Flux.range(1, 1000)
    .subscribeOn(Schedulers.parallel())  // ✅ 在创建时指定
    .map(this::expensiveOperation)
```

### ❌ 陷阱 2: I/O 操作使用 parallel()

```java
// ❌ 错误：parallel() 线程数有限，I/O 阻塞会饥饿
Flux.range(1, 10000)
    .flatMap(id ->
        fetchUserFromDB(id),  // 10ms 延迟
        parallelism = 100     // ❌ parallel() 没这么多线程
    )
    .subscribeOn(Schedulers.parallel())
```

```java
// ✅ 正确：I/O 使用 boundedElastic()
Flux.range(1, 10000)
    .flatMap(id ->
        fetchUserFromDB(id),
        parallelism = 100     // ✅ boundedElastic 足够
    )
    .subscribeOn(Schedulers.boundedElastic())
```

### ❌ 陷阱 3: 多个 publishOn 导致过度切换

```java
// ❌ 不必要的切换（每 1 个操作就切换一次）
Flux.range(1, 1000)
    .publishOn(Schedulers.parallel())
    .map(i -> i * 2)
    .publishOn(Schedulers.boundedElastic())  // ❌ 不必要
    .filter(i -> i > 100)
    .publishOn(Schedulers.single())          // ❌ 不必要
    .subscribe();
```

```java
// ✅ 推荐：按逻辑阶段切换
Flux.range(1, 1000)
    .subscribeOn(Schedulers.parallel())     // ✅ 计算阶段
    .map(i -> i * 2)
    .filter(i -> i > 100)
    .publishOn(Schedulers.boundedElastic()) // ✅ 一次切换到 I/O 阶段
    .flatMap(i -> saveToDatabase(i))
    .subscribe();
```

### ✅ 最佳实践

1. **明确线程模型**
   - 开始时用 `subscribeOn()` 指定
   - 根据阶段变化用 `publishOn()` 切换

2. **按操作类型分组**
   - CPU 密集 → `parallel()`
   - I/O 密集 → `boundedElastic()`
   - 同步逻辑 → `single()`

3. **测量而非猜测**
   - 用 JMH 基准测试对比
   - 监控线程占用和切换

4. **避免不必要切换**
   - 相同调度器的操作组合
   - 减少上下文切换

---

## 8. 快速参考表

### 调度器选择表

```
┌─────────────────┬───────────┬──────────┬─────────┬────────┐
│ 调度器          │ 线程数    │ 吞吐量   │ 延迟    │ CPU利用│
├─────────────────┼───────────┼──────────┼─────────┼────────┤
│immediate()      │ 0（当前） │ 最高     │ 最低    │ 中等   │
│single()         │ 1         │ 低       │ 高      │ 低     │
│boundedElastic() │ CPU*10    │ 中等     │ 中等    │ 中等   │
│parallel()       │ CPU       │ 高       │ 低      │ 最高   │
└─────────────────┴───────────┴──────────┴─────────┴────────┘
```

### 操作位置总结

```
Chain 结构：
  source → op1 → op2 → op3 → sink
    ↑                           ↑
  subscribeOn 作用     publishOn 作用

subscribeOn(T):
- 改变 source 的执行线程
- 只有第一个 subscribeOn 生效
- 放在 chain 的开始

publishOn(T):
- 改变 downstream 的执行线程
- 每个 publishOn 都有效
- 可以多个，但要避免过度切换
```

---

## 9. 总结

**关键要点**:

1. **Schedulers.immediate()**: 无线程切换，适合轻量级操作
2. **Schedulers.single()**: 单线程，保证顺序
3. **Schedulers.boundedElastic()**: 线程池，适合 I/O 密集
4. **Schedulers.parallel()**: 固定线程 = CPU 核数，适合 CPU 密集

5. **publishOn**: 影响下游，处理阶段的线程
6. **subscribeOn**: 影响上游，数据源的线程

7. **决策**: 根据操作类型选择调度器
8. **陷阱**: 避免不必要的线程切换

---

**下一步**: Phase 3 演示代码 (5 个 Demo)
