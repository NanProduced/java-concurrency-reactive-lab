# I/O 模型对比 - 从阻塞到异步的进化之路

> **目标**: 理解 5 种 I/O 模型的本质区别，为学习 BIO/NIO 打好基础。降低学习曲线 ~50%。

---

## 1. 核心概念

### 1.1 什么是 I/O？

**I/O (Input/Output)** 是程序与外部设备（磁盘、网络、终端等）交换数据的过程。

**关键问题**: 当应用程序发起 I/O 操作时，**数据不是立即可用的**。
- 网络数据需要等待网卡接收
- 磁盘数据需要等待磁盘读取
- 用户输入需要等待用户操作

**问题的本质**: 应用程序在等待数据期间，应该做什么？
- **阻塞等待** (Blocking)？→ 浪费 CPU 时间
- **轮询检查** (Polling)？→ 消耗 CPU 资源
- **通知机制** (Event-driven)？→ 高效但复杂

---

## 2. 五种 I/O 模型

### 2.1 模型 1：阻塞 I/O (Blocking I/O)

**工作原理**：
1. 应用程序调用 `read()`
2. 内核等待数据准备好（网卡接收数据 → 内核缓冲区）
3. 内核将数据从内核缓冲区拷贝到用户空间
4. `read()` 返回，应用程序继续执行

**特点**：
- ✅ **编程简单**: 同步顺序执行，易理解
- ❌ **效率低**: 线程阻塞期间无法处理其他任务
- ❌ **并发受限**: 每个连接需要一个线程（C10K 问题）

**状态机**：
```
应用程序         内核
    |             |
  read()--------->|  等待数据
    |             |  (阻塞)
    |             |  数据到达
    |<------------|  数据拷贝
    |             |  (阻塞)
  返回<-----------|  完成
    |             |
```

**Java 示例**：
```java
ServerSocket serverSocket = new ServerSocket(8080);
Socket client = serverSocket.accept();  // 阻塞，直到客户端连接

InputStream in = client.getInputStream();
byte[] buffer = new byte[1024];
int len = in.read(buffer);  // 阻塞，直到数据到达
```

**适用场景**：
- 连接数 < 100
- 简单的请求-响应模式
- 对并发性能要求不高

---

### 2.2 模型 2：非阻塞 I/O (Non-blocking I/O)

**工作原理**：
1. 应用程序调用 `read()`，设置为非阻塞模式
2. 如果数据未准备好，内核**立即返回错误** (EWOULDBLOCK)
3. 应用程序**轮询检查**，重复调用 `read()`
4. 数据准备好后，内核拷贝数据，`read()` 返回

**特点**：
- ✅ **不阻塞**: 线程可以处理其他任务
- ❌ **轮询消耗 CPU**: 频繁的系统调用开销
- ❌ **编程复杂**: 需要手动轮询

**状态机**：
```
应用程序         内核
    |             |
  read()--------->|  数据未准备好
    |<------------|  返回 EWOULDBLOCK
    |             |
  read()--------->|  数据未准备好
    |<------------|  返回 EWOULDBLOCK
    |             |
  read()--------->|  数据准备好
    |<------------|  数据拷贝
  返回<-----------|  完成
```

**Java 示例**：
```java
ServerSocketChannel serverChannel = ServerSocketChannel.open();
serverChannel.configureBlocking(false);  // 设置非阻塞
serverChannel.bind(new InetSocketAddress(8080));

while (true) {
    SocketChannel client = serverChannel.accept();  // 立即返回，可能为 null
    if (client != null) {
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        int len = client.read(buffer);  // 立即返回，可能为 0
        if (len > 0) {
            // 处理数据
        }
    }
}
```

**适用场景**：
- 需要轮询的场景
- 结合事件驱动机制使用

---

### 2.3 模型 3：I/O 多路复用 (I/O Multiplexing)

**工作原理**：
1. 应用程序调用 `select`/`poll`/`epoll`，**监视多个文件描述符**
2. 阻塞在 `select` 上，直到**至少一个**文件描述符就绪
3. `select` 返回就绪的文件描述符
4. 应用程序调用 `read()`，数据拷贝到用户空间

**特点**：
- ✅ **单线程处理多连接**: 解决 C10K 问题
- ✅ **高效**: 避免频繁轮询
- ⚠️ **2 次系统调用**: `select` + `read`

**状态机**：
```
应用程序         内核
    |             |
  select()------->|  监视多个 FD
    |             |  (阻塞)
    |             |  FD 就绪
    |<------------|  返回就绪 FD
    |             |
  read()--------->|  数据拷贝
  返回<-----------|  完成
```

**Java 示例 (Selector)**：
```java
Selector selector = Selector.open();
ServerSocketChannel serverChannel = ServerSocketChannel.open();
serverChannel.configureBlocking(false);
serverChannel.register(selector, SelectionKey.OP_ACCEPT);

while (true) {
    selector.select();  // 阻塞，直到至少一个通道就绪
    Set<SelectionKey> keys = selector.selectedKeys();
    for (SelectionKey key : keys) {
        if (key.isAcceptable()) {
            // 处理新连接
        } else if (key.isReadable()) {
            // 处理读事件
        }
    }
    keys.clear();
}
```

**适用场景**：
- 高并发服务器 (100-10000 连接)
- 需要单线程处理多连接

---

### 2.4 模型 4：信号驱动 I/O (Signal-driven I/O)

**工作原理**：
1. 应用程序注册 `SIGIO` 信号处理函数
2. 应用程序继续执行其他任务（非阻塞）
3. 数据准备好后，内核发送 `SIGIO` 信号
4. 信号处理函数调用 `read()`，数据拷贝到用户空间

**特点**：
- ✅ **异步通知**: 避免轮询
- ⚠️ **信号处理复杂**: 信号丢失、信号处理函数限制
- ⚠️ **不常用**: Java 不支持

**状态机**：
```
应用程序         内核
    |             |
注册信号-------->|  监听数据
    |             |
继续执行          |  数据到达
    |             |
    |<------------|  发送 SIGIO
信号处理函数      |
    |             |
  read()--------->|  数据拷贝
  返回<-----------|  完成
```

**适用场景**：
- Unix/Linux 环境
- 特定的高性能场景

---

### 2.5 模型 5：异步 I/O (Asynchronous I/O, AIO)

**工作原理**：
1. 应用程序调用 `aio_read()`，**传入缓冲区地址**
2. 应用程序继续执行其他任务（非阻塞）
3. 内核等待数据准备好，**并自动拷贝到用户空间**
4. 内核通知应用程序操作完成

**特点**：
- ✅ **真正异步**: 内核完成数据拷贝
- ✅ **无需轮询**: 事件驱动
- ⚠️ **编程复杂**: 回调函数、状态管理
- ⚠️ **系统支持有限**: Windows IOCP 良好，Linux AIO 支持不完善

**状态机**：
```
应用程序         内核
    |             |
aio_read()------>|  注册异步读
    |             |  (立即返回)
继续执行          |  等待数据
    |             |  数据到达
    |             |  数据拷贝
    |<------------|  完成通知
处理数据          |
```

**Java 示例 (AsynchronousSocketChannel)**：
```java
AsynchronousSocketChannel channel = AsynchronousSocketChannel.open();
channel.connect(new InetSocketAddress("localhost", 8080), null,
    new CompletionHandler<Void, Void>() {
        @Override
        public void completed(Void result, Void attachment) {
            ByteBuffer buffer = ByteBuffer.allocate(1024);
            channel.read(buffer, buffer, new CompletionHandler<Integer, ByteBuffer>() {
                @Override
                public void completed(Integer result, ByteBuffer attachment) {
                    // 数据已拷贝到 buffer，可以直接使用
                }
                @Override
                public void failed(Throwable exc, ByteBuffer attachment) {
                    exc.printStackTrace();
                }
            });
        }
        @Override
        public void failed(Throwable exc, Void attachment) {
            exc.printStackTrace();
        }
    });
```

**适用场景**：
- 高性能服务器 (> 10000 连接)
- Windows 环境 (IOCP)
- 需要真正异步的场景

---

## 3. 五种模型对比表

| 模型 | 阻塞点 | 优点 | 缺点 | Java 支持 | 并发性能 |
|------|--------|------|------|-----------|----------|
| **阻塞 I/O** | `read()` 调用期间 | 编程简单 | 效率低，每连接一线程 | `Socket`, `ServerSocket` | 低 (< 100) |
| **非阻塞 I/O** | 无（但需轮询） | 不阻塞线程 | 轮询消耗 CPU | `SocketChannel` (configureBlocking(false)) | 中 (100-1000) |
| **I/O 多路复用** | `select()`/`epoll()` | 单线程处理多连接 | 2 次系统调用 | `Selector` + `Channel` | 高 (1000-10000) |
| **信号驱动 I/O** | 无（信号通知） | 异步通知 | 信号处理复杂 | 不支持 | 高 |
| **异步 I/O** | 无（回调通知） | 真正异步 | 编程复杂，系统支持有限 | `AsynchronousSocketChannel` | 极高 (> 10000) |

---

## 4. 同步 vs 异步、阻塞 vs 非阻塞

### 4.1 概念澄清

**阻塞 vs 非阻塞** (关注**调用方式**):
- **阻塞**: 调用后，**线程挂起**，直到操作完成
- **非阻塞**: 调用后，**立即返回**，可能返回错误或部分数据

**同步 vs 异步** (关注**数据拷贝**):
- **同步**: 应用程序**亲自拷贝数据**（调用 `read()`）
- **异步**: 内核**代为拷贝数据**，完成后通知应用程序

### 4.2 模型分类

| 模型 | 同步/异步 | 阻塞/非阻塞 |
|------|-----------|-------------|
| 阻塞 I/O | 同步 | 阻塞 |
| 非阻塞 I/O | 同步 | 非阻塞 |
| I/O 多路复用 | 同步 | 阻塞 (`select`) |
| 信号驱动 I/O | 同步 | 非阻塞 |
| 异步 I/O | 异步 | 非阻塞 |

**关键理解**:
- **I/O 多路复用**虽然 `select()` 阻塞，但监视多个连接，整体性能高
- **只有异步 I/O** 是真正的异步（内核完成数据拷贝）

---

## 5. Java NIO vs BIO vs AIO

### 5.1 BIO (Blocking I/O)
- **类**: `Socket`, `ServerSocket`, `InputStream`, `OutputStream`
- **模型**: 阻塞 I/O
- **并发**: 每连接一线程
- **适用**: 连接数 < 100

### 5.2 NIO (Non-blocking I/O)
- **类**: `Selector`, `Channel`, `ByteBuffer`
- **模型**: I/O 多路复用 (`select`/`epoll`)
- **并发**: 单线程处理多连接
- **适用**: 连接数 100-10000

### 5.3 AIO (Asynchronous I/O)
- **类**: `AsynchronousSocketChannel`, `CompletionHandler`
- **模型**: 异步 I/O (Windows IOCP, Linux AIO)
- **并发**: 真正异步，回调驱动
- **适用**: 连接数 > 10000

---

## 6. 常见误区与陷阱

### ❌ 误区 1: "NIO 就是非阻塞 I/O"
**真相**: NIO 底层使用 I/O 多路复用 (`select`/`epoll`)，`Selector.select()` 是阻塞的。

### ❌ 误区 2: "异步 I/O 一定比同步 I/O 快"
**真相**: 异步 I/O 引入回调、状态管理开销。在低并发场景下，BIO 可能更快。

### ❌ 误区 3: "I/O 多路复用可以避免所有阻塞"
**真相**: `select()` 本身是阻塞的，只是可以同时监视多个连接，提高吞吐量。

---

## 7. C10K 问题

**问题**: 如何在单机上同时处理 10000 个并发连接？

**传统方案 (BIO)**:
- 每连接一线程
- 10000 连接 = 10000 线程
- **内存消耗**: 10000 线程 × 1MB 栈空间 = 10GB
- **上下文切换**: 性能急剧下降

**解决方案 (NIO + I/O 多路复用)**:
- 单线程 + `Selector`
- 1 线程处理 10000 连接
- **内存消耗**: 极低
- **性能**: 高效

---

## 8. 决策树：选择哪种 I/O 模型？

```
并发连接数？
  ├─ < 100 → BIO (简单易用)
  ├─ 100-10000 → NIO + Selector (高性能)
  └─ > 10000 → AIO 或 Netty (企业级框架)

复杂度容忍度？
  ├─ 低 → BIO
  ├─ 中 → NIO
  └─ 高 → AIO 或 Reactor 模式

系统平台？
  ├─ Windows → AIO (IOCP 支持好)
  ├─ Linux → NIO (epoll 成熟)
  └─ 跨平台 → NIO + Netty
```

---

## 9. 延伸阅读

- **UNIX Network Programming** - W. Richard Stevens (Chapter 6: I/O Multiplexing)
- **Netty in Action** - Norman Maurer
- **Linux epoll 源码分析**
- **Windows IOCP 机制**

---

**下一步**: 阅读 `TCP_BASICS.md`，理解 TCP 三次握手、四次挥手和状态机。
