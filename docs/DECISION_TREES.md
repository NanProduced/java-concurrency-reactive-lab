# 决策树库 (DECISION_TREES)

> **目标**: 记录 Java 并发编程中的关键决策流程和参考数据
> **维护**: 每个 Lab 完成后补充新的决策树
> **使用场景**: 面临技术选择时快速查阅决策过程

---

## 决策树索引

| 决策 ID | 标题 | 应用场景 | 状态 |
|--------|------|---------|------|
| DT-001 | 线程池大小计算 | Lab-03 | ✅ 已记录 |
| DT-002 | 异步方案选择 | Lab-05 | ⏳ 待补充 |
| DT-003 | 背压策略选择 | Lab-07 | ⏳ 待补充 |
| DT-004 | GC 算法选择 | Lab-09 | ⏳ 待补充 |
| DT-005 | 响应式框架选择 | Lab-11 | ⏳ 待补充 |

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

## 待补充的决策树

### DT-002: 异步方案选择 (Lab-05)
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

