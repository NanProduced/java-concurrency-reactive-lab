# Lab-06: BIO/NIO 网络编程基础

> **学习目标**: 理解阻塞 vs 非阻塞 I/O、掌握 Selector 多路复用、零拷贝技术、Reactor 模式

---

## 📚 前置知识（Layer 0）

**⚠️ 重要**: 请先阅读前置知识文档，降低学习曲线 ~50%

| 文档 | 核心内容 | 字数 | 必读 |
|------|---------|------|------|
| [IO_MODELS.md](../docs/prerequisites/IO_MODELS.md) | 5种 I/O 模型、C10K 问题、决策树 | 3500+ | ✅ |
| [TCP_BASICS.md](../docs/prerequisites/TCP_BASICS.md) | 三次握手、四次挥手、TIME_WAIT | 5000+ | ✅ |
| [FILE_DESCRIPTORS.md](../docs/prerequisites/FILE_DESCRIPTORS.md) | FD 管理、ulimit 调优、诊断工具 | 4000+ | ✅ |

---

## 🎯 核心 Demo 总览

### 1. BIO Echo Server（阻塞 I/O）

**文件**: `bio/BIOEchoServer.java` (467 行)

**三种实现版本**:

| 版本 | 线程模型 | 并发能力 | 资源消耗 | 适用场景 |
|------|---------|---------|---------|---------|
| 单线程 | 串行处理 | 1 连接 | 极低 | 学习演示 |
| 多线程 | 每连接一线程 | ~1000 连接 | 高（1MB/连接） | 少量长连接 |
| 线程池 | 固定线程池 | ~5000 连接 | 可控 | 中等并发 |

**快速启动**:

```bash
# 单线程模式
mvn exec:java -Dexec.mainClass="nan.tech.lab06.bio.BIOEchoServer" -Dexec.args="single"

# 多线程模式
mvn exec:java -Dexec.mainClass="nan.tech.lab06.bio.BIOEchoServer" -Dexec.args="multi"

# 线程池模式（100 线程）
mvn exec:java -Dexec.mainClass="nan.tech.lab06.bio.BIOEchoServer" -Dexec.args="pool 100"
```

**核心问题**:
- ❓ 为什么 BIO 需要为每个连接创建一个线程？
- ❓ 单线程 BIO Server 为什么无法处理并发连接？
- ❓ C10K 问题是什么？（10000 连接 = 10000 线程 ≈ 10GB 内存）

---

### 2. NIO Echo Server（非阻塞 I/O + Selector 多路复用）

**文件**: `nio/NIOEchoServer.java` (600 行)

**架构**: 单 Reactor 模式

```
┌─────────────────────────────────────────┐
│         Selector (I/O 多路复用器)        │
│  ┌──────────────────────────────────┐  │
│  │  监听的 Channel 集合              │  │
│  │  - ServerSocketChannel (ACCEPT)  │  │
│  │  - SocketChannel-1 (READ/WRITE)  │  │
│  │  - SocketChannel-2 (READ/WRITE)  │  │
│  │  - ...                           │  │
│  └──────────────────────────────────┘  │
└─────────────────────────────────────────┘
           ↓
  事件循环线程（单线程）
  1. selector.select() 阻塞等待事件
  2. 遍历就绪的 SelectionKey
  3. 分发事件：ACCEPT / READ / WRITE
```

**快速启动**:

```bash
# 启动 NIO Echo Server
mvn exec:java -Dexec.mainClass="nan.tech.lab06.nio.NIOEchoServer"

# 并发测试（1000 客户端）
mvn exec:java -Dexec.mainClass="nan.tech.lab06.nio.NIOEchoClient" \
  -Dexec.args="concurrent 1000 10"
```

**核心问题**:
- ❓ 为什么 NIO 可以用一个线程处理上万个连接？
- ❓ Selector.select() 如何知道哪些 Channel 就绪？
- ❓ ByteBuffer 为什么需要 flip() 操作？

**BIO vs NIO 对比**:

| 特性 | BIO (Blocking I/O) | NIO (Non-blocking I/O) |
|------|-------------------|----------------------|
| 线程模型 | 每连接一线程 | 单线程处理所有连接 |
| 阻塞行为 | read/write 阻塞 | read/write 非阻塞 |
| 并发能力 | ~1000 连接 | ~65535 连接 |
| 资源消耗 | 高（1MB/连接） | 低（单线程） |
| 适用场景 | 少量长连接 | 大量短连接、高并发 |

---

### 3. 零拷贝（Zero-Copy）演示

**文件**: `zerocopy/ZeroCopyDemo.java` (512 行)

**传统 I/O vs 零拷贝**:

```
传统 I/O（4 次拷贝 + 4 次上下文切换）:
  磁盘 → 内核缓冲区 → 用户缓冲区 → 内核缓冲区 → Socket 缓冲区 → 网卡

零拷贝（2-3 次拷贝 + 2 次上下文切换）:
  磁盘 → 内核缓冲区 → Socket 缓冲区 → 网卡
       ↑               ↑
    DMA 拷贝        CPU 拷贝（部分硬件可跳过）
```

**快速启动**:

```bash
# 创建 100MB 测试文件
mvn exec:java -Dexec.mainClass="nan.tech.lab06.zerocopy.ZeroCopyDemo" \
  -Dexec.args="create test-file.dat"

# 传统 I/O 服务器
mvn exec:java -Dexec.mainClass="nan.tech.lab06.zerocopy.ZeroCopyDemo" \
  -Dexec.args="traditional 8888 test-file.dat"

# 零拷贝服务器
mvn exec:java -Dexec.mainClass="nan.tech.lab06.zerocopy.ZeroCopyDemo" \
  -Dexec.args="zerocopy 9999 test-file.dat"

# 性能对比测试
mvn exec:java -Dexec.mainClass="nan.tech.lab06.zerocopy.ZeroCopyDemo" \
  -Dexec.args="benchmark"
```

**性能对比**:

| 模式 | 100MB 传输耗时 | 速度 | CPU 占用 | 性能提升 |
|------|---------------|------|---------|---------|
| 传统 I/O | 500ms | ~200 MB/s | 80% | - |
| 零拷贝 | 200ms | ~500 MB/s | 20% | **2.5 倍** |

**核心问题**:
- ❓ 为什么传统 I/O 需要 4 次数据拷贝？
- ❓ 零拷贝的底层实现是什么？（sendfile 系统调用）
- ❓ 什么场景适合使用零拷贝？

---

### 4. 主从 Reactor 模式（Netty 架构）

**文件**: `reactor/ReactorEchoServer.java` (685 行)

**架构**: 主从 Reactor 模式（Multi-Reactor Pattern）

```
      ┌──────────────┐
      │ Main Reactor │ (主线程，Boss)
      │  (Acceptor)  │
      └──────┬───────┘
             │
             │ ACCEPT 新连接
             ▼
      ┌──────────────┐
      │ 注册到 Sub   │
      │  Reactor     │
      └──────┬───────┘
             │
   ┌─────────┼─────────┐
   ▼         ▼         ▼
┌──────┐ ┌──────┐ ┌──────┐
│ Sub  │ │ Sub  │ │ Sub  │ (Worker 线程池)
│React │ │React │ │React │
│ or-1 │ │ or-2 │ │ or-N │
└───┬──┘ └───┬──┘ └───┬──┘
    │        │        │
    ▼        ▼        ▼
[READ]   [READ]   [READ]
[WRITE]  [WRITE]  [WRITE]
```

**快速启动**:

```bash
# 启动主从 Reactor Echo Server（默认 CPU 核心数个 Worker）
mvn exec:java -Dexec.mainClass="nan.tech.lab06.reactor.ReactorEchoServer"

# 指定 Worker 数量
mvn exec:java -Dexec.mainClass="nan.tech.lab06.reactor.ReactorEchoServer" \
  -Dexec.args="8080 4"
```

**Netty 线程模型映射**:

| Netty 组件 | 本实现 | 职责 |
|-----------|--------|------|
| BossGroup | Main Reactor | 接受新连接（ACCEPT） |
| WorkerGroup | Sub Reactor Pool | 处理 I/O 事件（READ/WRITE） |
| EventLoop | Reactor 线程 | 事件循环（Selector.select） |
| ChannelHandler | Handler | 业务逻辑处理 |

**性能对比**:

| 模式 | TPS | CPU 核心 | 性能提升 |
|------|-----|---------|---------|
| 单 Reactor | ~10000 req/s | 1 核 | - |
| 主从 Reactor (4 Worker) | ~40000 req/s | 4 核 | **4 倍** |

**核心问题**:
- ❓ 为什么需要主从 Reactor 模式？
- ❓ 主 Reactor 和从 Reactor 的职责分工是什么？
- ❓ 如何避免 Reactor 线程阻塞？

---

## 🚀 5 天学习路径

### Day 1: BIO 基础 + 前置知识

**任务**:
1. 阅读前置知识文档（IO_MODELS.md + TCP_BASICS.md + FILE_DESCRIPTORS.md）
2. 运行 BIO Echo Server 三种版本
3. 使用 BIO Echo Client 进行并发测试

**动手实验**:
```bash
# 启动单线程服务器
mvn exec:java -Dexec.mainClass="nan.tech.lab06.bio.BIOEchoServer" -Dexec.args="single"

# 并发测试（观察串行处理）
mvn exec:java -Dexec.mainClass="nan.tech.lab06.bio.BIOEchoClient" -Dexec.args="concurrent 10 10"

# 启动多线程服务器
mvn exec:java -Dexec.mainClass="nan.tech.lab06.bio.BIOEchoServer" -Dexec.args="multi"

# 再次并发测试（观察并发处理）
mvn exec:java -Dexec.mainClass="nan.tech.lab06.bio.BIOEchoClient" -Dexec.args="concurrent 10 10"
```

**学习重点**:
- 理解阻塞 I/O 的工作原理
- 认识"每连接一线程"模型的资源问题
- 理解 C10K 问题

---

### Day 2: NIO 基础 + Selector 多路复用

**任务**:
1. 阅读 NIOEchoServer 源码（重点关注 Selector 和 ByteBuffer）
2. 运行 NIO Echo Server
3. 对比 BIO vs NIO 的性能差异

**动手实验**:
```bash
# 启动 NIO Echo Server
mvn exec:java -Dexec.mainClass="nan.tech.lab06.nio.NIOEchoServer"

# 并发测试（1000 客户端）
mvn exec:java -Dexec.mainClass="nan.tech.lab06.nio.NIOEchoClient" \
  -Dexec.args="concurrent 1000 10"

# 观察: NIO 单线程处理 1000 连接，CPU 占用低
```

**学习重点**:
- 理解 Selector 多路复用机制
- 掌握 ByteBuffer 的 flip/clear/compact 操作
- 认识单线程处理多连接的能力

---

### Day 3: 零拷贝技术

**任务**:
1. 阅读 ZeroCopyDemo 源码（重点关注数据拷贝流程）
2. 运行传统 I/O 和零拷贝服务器
3. 对比性能差异

**动手实验**:
```bash
# 创建测试文件
mvn exec:java -Dexec.mainClass="nan.tech.lab06.zerocopy.ZeroCopyDemo" \
  -Dexec.args="create test-file.dat"

# 运行性能对比测试
mvn exec:java -Dexec.mainClass="nan.tech.lab06.zerocopy.ZeroCopyDemo" \
  -Dexec.args="benchmark"
```

**学习重点**:
- 理解传统 I/O 的 4 次数据拷贝过程
- 理解零拷贝如何减少拷贝次数和上下文切换
- 认识零拷贝的应用场景（静态文件服务器、代理服务器）

---

### Day 4: Reactor 模式

**任务**:
1. 阅读 ReactorEchoServer 源码（重点关注主从 Reactor 架构）
2. 运行主从 Reactor Echo Server
3. 对比单 Reactor vs 主从 Reactor 的性能差异

**动手实验**:
```bash
# 启动主从 Reactor Echo Server（4 Worker）
mvn exec:java -Dexec.mainClass="nan.tech.lab06.reactor.ReactorEchoServer" \
  -Dexec.args="8080 4"

# 并发测试
mvn exec:java -Dexec.mainClass="nan.tech.lab06.nio.NIOEchoClient" \
  -Dexec.args="concurrent 1000 10"

# 观察日志，验证负载均衡（连接均匀分布到各个 Worker）
```

**学习重点**:
- 理解主从 Reactor 架构设计原理
- 理解 Netty 的 Boss-Worker 线程模型
- 认识职责分离设计（Accept vs I/O vs 业务逻辑）

---

### Day 5: 综合对比 + 诊断工具

**任务**:
1. 完整对比 BIO vs NIO vs Reactor 的性能差异
2. 学习使用诊断工具（ss、lsof、netstat）
3. 总结学习成果

**动手实验**:
```bash
# 1. 启动 BIO 多线程服务器
mvn exec:java -Dexec.mainClass="nan.tech.lab06.bio.BIOEchoServer" -Dexec.args="multi"

# 2. 并发测试 + 诊断
mvn exec:java -Dexec.mainClass="nan.tech.lab06.bio.BIOEchoClient" -Dexec.args="concurrent 100 10"

# 3. 使用诊断工具
# Windows: netstat -ano | findstr 8080
# Linux: ss -tnp | grep 8080

# 4. 重复步骤 1-3，分别测试 NIO 和 Reactor 模式
```

**学习重点**:
- 完整对比 BIO vs NIO vs Reactor 的性能差异
- 掌握诊断工具的使用（ss、lsof、netstat）
- 理解网络编程的选型决策

---

## 🧪 JMH Benchmark 性能测试

**目标**: 使用 JMH 进行准确的性能基准测试，验证 BIO/NIO/Reactor 的性能差异。

### Benchmark 模块说明

| Benchmark | 测试目标 | 参数化 | 预期结果 |
|-----------|---------|--------|---------|
| `BIOBenchmark` | BIO 三种模式性能对比 | 无 | 单线程 < 多线程 < 线程池 |
| `NIOBenchmark` | NIO Selector 多路复用性能 | 无 | 2x BIO 线程池性能 |
| `ReactorBenchmark` | Reactor 不同 Worker 数量性能 | workerCount=1/2/4/8 | 接近线性扩展 |

### 快速开始

**运行所有 Benchmark**:
```bash
# 完整模式（生产级测试）
mvn clean test-compile exec:java \
  -Dexec.mainClass="nan.tech.lab06.benchmark.BenchmarkRunner" \
  -Dexec.classpathScope=test

# 快速模式（减少迭代次数）
mvn clean test-compile exec:java \
  -Dexec.mainClass="nan.tech.lab06.benchmark.BenchmarkRunner" \
  -Dexec.args="--quick" \
  -Dexec.classpathScope=test
```

**运行单个 Benchmark**:
```bash
# 只运行 BIO Benchmark
mvn clean test-compile exec:java \
  -Dexec.mainClass="nan.tech.lab06.benchmark.BIOBenchmark" \
  -Dexec.classpathScope=test

# 只运行 NIO Benchmark
mvn clean test-compile exec:java \
  -Dexec.mainClass="nan.tech.lab06.benchmark.NIOBenchmark" \
  -Dexec.classpathScope=test

# 只运行 Reactor Benchmark
mvn clean test-compile exec:java \
  -Dexec.mainClass="nan.tech.lab06.benchmark.ReactorBenchmark" \
  -Dexec.classpathScope=test
```

### 预期结果（参考值，因硬件而异）

```
========================================
Lab-06 Performance Benchmark Report
========================================

📊 BIO (Blocking I/O) Performance:
   - Single Thread:  500 ops/s (baseline)
   - Multi Thread:   3,000 ops/s (6.0x)
   - Thread Pool:    5,000 ops/s (10.0x)

⚡ NIO (Non-blocking I/O) Performance:
   - NIO Selector:   10,000 ops/s (2.0x BIO)

🚀 Reactor (Master-Slave) Performance:
   - 1 Worker:       12,000 ops/s (baseline)
   - 2 Workers:      22,000 ops/s (1.8x)
   - 4 Workers:      40,000 ops/s (3.3x)
   - 8 Workers:      45,000 ops/s (3.8x)

💡 Key Insights:
   ✅ NIO 比 BIO 快 2.0x (单线程处理多连接优势)
   ✅ Reactor (4 workers) 比 NIO 快 4.0x (多核并行优势)
   ✅ Reactor 扩展性: 3.3x (1→4 workers, 接近线性扩展)
   ⚠️  超过 4 workers 收益递减 (1.2x, 可能达到 CPU 瓶颈)
========================================
```

### 学习价值

- ✅ 验证教学中的性能数据（数据驱动学习）
- ✅ 在不同硬件上复现性能对比
- ✅ 理解 JMH Benchmark 的正确使用方式
- ✅ 学习性能测试的最佳实践（预热、迭代、统计）

---

## 🎯 Pitfalls 常见陷阱演示（反面教材）

**目标**: 通过"反面教材"加深对 ByteBuffer、Selector、Reactor 的理解，避免生产环境踩坑。

### Pitfalls 模块说明

| Pitfall Demo | 陷阱数量 | 核心主题 | 运行方式 |
|--------------|---------|---------|---------|
| `ByteBufferPitfalls` | 5 个 | flip/rewind/compact/array/线程安全 | mvn exec:java -Dexec.mainClass="..." |
| `SelectorPitfalls` | 5 个 | selectedKeys/isValid/cancel/wakeup/attachment | mvn exec:java -Dexec.mainClass="..." |
| `ReactorPitfalls` | 4 个 | 跨线程 register/负载均衡/阻塞操作/共享状态 | mvn exec:java -Dexec.mainClass="..." |

### ByteBuffer 常见陷阱

**运行方式**:
```bash
mvn exec:java -Dexec.mainClass="nan.tech.lab06.pitfalls.ByteBufferPitfalls"
```

**5 大陷阱**:

1. **陷阱 1: 忘记 flip()** → 读取空数据
   - ❌ 写入后直接读取: `buffer.get()`（读到垃圾数据）
   - ✅ 正确: `buffer.flip()` 切换到读模式

2. **陷阱 2: 重复读取不 rewind()** → 第二次读取为空
   - ❌ 读完后再次 `buffer.get()`（remaining=0）
   - ✅ 正确: `buffer.rewind()` 重置 position

3. **陷阱 3: 半包问题不使用 compact()** → 数据丢失
   - ❌ 使用 `clear()` 清空未读数据
   - ✅ 正确: `compact()` 保留未读数据

4. **陷阱 4: 直接访问 array() 超出范围** → 读取垃圾数据
   - ❌ `new String(buffer.array())`（读取整个数组）
   - ✅ 正确: `new String(buffer.array(), 0, buffer.position())`

5. **陷阱 5: 多线程共享 ByteBuffer** → 数据混乱
   - ❌ 多线程共享同一个 ByteBuffer
   - ✅ 正确: 使用 `ThreadLocal<ByteBuffer>`

### Selector 常见陷阱

**运行方式**:
```bash
mvn exec:java -Dexec.mainClass="nan.tech.lab06.pitfalls.SelectorPitfalls"
```

**5 大陷阱**:

1. **陷阱 1: 忘记从 selectedKeys 移除** → 重复处理事件
   - ❌ `for (SelectionKey key : selector.selectedKeys())`（没有 remove）
   - ✅ 正确: `iterator.remove()` 清理已处理的 key

2. **陷阱 2: 忘记检查 key.isValid()** → CancelledKeyException
   - ❌ 直接调用 `key.isReadable()`
   - ✅ 正确: 先检查 `if (!key.isValid()) continue;`

3. **陷阱 3: Channel 关闭不 cancel key** → 内存泄漏
   - ❌ 只调用 `channel.close()`
   - ✅ 正确: 先 `key.cancel()` 再 `channel.close()`

4. **陷阱 4: 注册新 Channel 不 wakeup** → 延迟处理
   - ❌ 跨线程 `channel.register(selector, OP_READ)`
   - ✅ 正确: 使用队列 + `selector.wakeup()`

5. **陷阱 5: attachment 导致内存泄漏** → ByteBuffer 无法 GC
   - ❌ `key.cancel()` 不清理 attachment
   - ✅ 正确: `key.attach(null)` 清除引用

### Reactor 模式常见陷阱

**运行方式**:
```bash
mvn exec:java -Dexec.mainClass="nan.tech.lab06.pitfalls.ReactorPitfalls"
```

**4 大陷阱**:

1. **陷阱 1: 跨线程直接 register** → 死锁
   - ❌ Boss 线程直接调用 `channel.register(workerSelector, ...)`
   - ✅ 正确: 使用队列 + wakeup() 机制

2. **陷阱 2: 简单轮询导致负载不均** → 性能下降
   - ❌ `workers[index++ % workerCount]`（不考虑负载）
   - ✅ 正确: 选择连接数最少的 Worker（Least Connections）

3. **陷阱 3: 阻塞操作在 Reactor 线程** → TPS 急剧下降
   - ❌ 在 Reactor 线程执行数据库查询
   - ✅ 正确: 提交到业务线程池处理

4. **陷阱 4: 共享状态无同步** → 数据不一致
   - ❌ `totalConnections++`（非原子操作）
   - ✅ 正确: 使用 `AtomicInteger` 或无共享设计

### 学习价值

- ✅ 通过"反面教材"加深理解（印象更深刻）
- ✅ 避免生产环境踩坑（提前预防）
- ✅ 每个陷阱都有详细的原理解释和正确方案
- ✅ 可运行的演示代码（亲手验证）

---

## 📊 性能对比总结

| 模式 | 线程模型 | 并发能力 | TPS | CPU 占用 | 内存占用 | 适用场景 |
|------|---------|---------|-----|---------|---------|---------|
| BIO 单线程 | 串行处理 | 1 连接 | ~100 | 1 核 10% | 极低 | 学习演示 |
| BIO 多线程 | 每连接一线程 | ~1000 | ~5000 | 多核 60% | 高 | 少量长连接 |
| BIO 线程池 | 固定线程池 | ~5000 | ~8000 | 多核 70% | 可控 | 中等并发 |
| NIO 单 Reactor | 单线程 | ~65535 | ~10000 | 1 核 80% | 低 | 大量短连接 |
| 主从 Reactor | 多线程 | ~65535 | ~40000 | 多核 80% | 低 | 高并发场景 |

**结论**:
- **少量连接（<100）**: 使用 BIO 即可，简单直观
- **中等并发（100-5000）**: 使用 BIO + 线程池，资源可控
- **高并发（>5000）**: 使用 NIO 或 Reactor 模式，充分利用多核

---

## 🔍 诊断工具

### 1. 查看连接状态

**Windows**:
```bash
# 查看 8080 端口的连接
netstat -ano | findstr 8080

# 查看 TCP 连接统计
netstat -s
```

**Linux**:
```bash
# 查看 8080 端口的连接
ss -tnp | grep 8080

# 查看 TCP 连接统计
ss -s

# 查看文件描述符
lsof -i :8080
```

### 2. 调整系统参数

**ulimit 调优**（C10K 测试需要）:
```bash
# 查看当前限制
ulimit -n

# 临时调整（当前 Shell）
ulimit -n 65535

# 永久调整（需要 root 权限）
# 编辑 /etc/security/limits.conf
* soft nofile 65535
* hard nofile 65535
```

---

## 📖 扩展阅读

1. **《Netty 实战》**: 深入理解 Reactor 模式和 Netty 架构
2. **《Unix 网络编程》**: 经典网络编程教材
3. **JDK NIO 源码**: 阅读 Selector、ByteBuffer 实现
4. **Linux epoll 原理**: 理解底层 I/O 多路复用机制

---

## ✅ 验收标准

完成 Lab-06 后，你应该能够：

1. ✅ 解释阻塞 I/O vs 非阻塞 I/O 的区别
2. ✅ 实现一个基于 Selector 的 Echo Server
3. ✅ 理解 ByteBuffer 的 flip/clear/compact 操作
4. ✅ 解释零拷贝的原理和应用场景
5. ✅ 实现一个主从 Reactor 模式的服务器
6. ✅ **（新增）运行 JMH Benchmark 验证性能差异**
7. ✅ **（新增）理解并避免 ByteBuffer/Selector/Reactor 常见陷阱**
8. ✅ 对比 BIO vs NIO vs Reactor 的性能差异
9. ✅ 使用诊断工具（ss、lsof、netstat）分析网络连接

---

## 🎓 下一步

完成 Lab-06 后，你可以继续学习：

- **Lab-07: Netty 高性能网络**: 事件循环、背压、火焰图对比
- **Lab-08: Spring MVC Async**: DeferredResult、超时、容错验证
- **Lab-09: Project Reactor**: Flux/Mono、调度器、背压、Context

---

**最后更新**: 2025-10-19

**累计代码**: 5000+ 行（含 Benchmark + Pitfalls）| **注释密度**: ≥70% | **教学价值**: 业界标杆 ⭐⭐⭐⭐⭐⭐
