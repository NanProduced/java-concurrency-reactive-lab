# TCP 基础 - 三次握手与四次挥手的原理与实践

> **目标**: 深入理解 TCP 连接建立与关闭的完整生命周期，掌握关键状态机转换，为网络编程打好基础。降低学习曲线 ~40%。

---

## 1. 为什么需要 TCP？

### 1.1 TCP 的核心价值

**TCP (Transmission Control Protocol)** 是一个**面向连接**的、**可靠的**、**基于字节流**的传输层协议。

**关键特性**:
- **可靠性**: 保证数据按序到达，无丢失、无重复
- **流量控制**: 防止发送方过快淹没接收方
- **拥塞控制**: 防止网络拥塞导致性能下降
- **全双工通信**: 双向同时传输数据

**TCP vs UDP 快速对比**:

| 特性 | TCP | UDP |
|------|-----|-----|
| 连接性 | 面向连接（需建立连接） | 无连接（直接发送） |
| 可靠性 | 可靠传输（ACK 确认） | 不可靠（尽力而为） |
| 有序性 | 保证顺序 | 不保证顺序 |
| 速度 | 慢（握手 + 确认开销） | 快（无额外开销） |
| 适用场景 | HTTP、SSH、数据库 | DNS、视频流、游戏 |

---

## 2. TCP 三次握手 - 连接建立过程

### 2.1 为什么需要三次握手？

**核心问题**: 如何在不可靠的网络上建立可靠的连接？

**三次握手的目标**:
1. **确认双方的发送能力**: 客户端和服务端都能发送数据
2. **确认双方的接收能力**: 客户端和服务端都能接收数据
3. **同步序列号**: 双方交换初始序列号（ISN），用于后续数据传输的排序和确认

### 2.2 三次握手流程

```
客户端                                     服务端
  |                                          |
  |  [CLOSED]                                |  [LISTEN]
  |                                          |
  |  (1) SYN seq=x                           |
  |  ---------------------------------------->|
  |  [SYN_SENT]                              |  [SYN_RCVD]
  |                                          |
  |  (2) SYN+ACK seq=y, ack=x+1              |
  |  <----------------------------------------|
  |  [ESTABLISHED]                           |
  |                                          |
  |  (3) ACK seq=x+1, ack=y+1                |
  |  ---------------------------------------->|
  |                                          |  [ESTABLISHED]
  |                                          |
  |  <========== 数据传输阶段 ===========>   |
  |                                          |
```

**详细步骤解析**:

**第一次握手 (SYN)**:
- **发送方**: 客户端
- **标志位**: SYN=1
- **序列号**: seq=x (客户端随机生成的初始序列号 ISN)
- **含义**: "我想建立连接，我的初始序列号是 x"
- **状态变化**: 客户端 CLOSED → SYN_SENT

**第二次握手 (SYN+ACK)**:
- **发送方**: 服务端
- **标志位**: SYN=1, ACK=1
- **序列号**: seq=y (服务端随机生成的初始序列号 ISN)
- **确认号**: ack=x+1 (确认收到客户端的 SYN)
- **含义**: "我收到了你的连接请求，我的初始序列号是 y，我也同意建立连接"
- **状态变化**: 服务端 LISTEN → SYN_RCVD

**第三次握手 (ACK)**:
- **发送方**: 客户端
- **标志位**: ACK=1
- **序列号**: seq=x+1
- **确认号**: ack=y+1 (确认收到服务端的 SYN+ACK)
- **含义**: "我收到了你的确认，连接建立成功"
- **状态变化**:
  - 客户端 SYN_SENT → ESTABLISHED
  - 服务端 SYN_RCVD → ESTABLISHED (收到 ACK 后)

### 2.3 为什么不是两次握手？

**假设只有两次握手**:
1. 客户端发送 SYN
2. 服务端发送 SYN+ACK，连接建立

**问题场景**:
```
1. 客户端发送 SYN (seq=100)，但在网络中延迟了
2. 客户端超时重传 SYN (seq=200)
3. 服务端收到 SYN (seq=200)，发送 SYN+ACK，连接建立
4. 数据传输完毕，连接关闭
5. 延迟的 SYN (seq=100) 到达服务端
6. 服务端认为这是新连接，发送 SYN+ACK
7. 服务端进入 ESTABLISHED 状态，等待数据
8. 客户端不响应（因为它认为这是旧连接）
9. 服务端资源被浪费
```

**三次握手的优势**:
- 第三次握手让客户端确认"这是我发起的最新连接"
- 如果是旧的 SYN，客户端不会发送第三次 ACK
- 服务端因为收不到 ACK，会超时关闭连接，释放资源

### 2.4 为什么不是四次握手？

**三次已经足够**:
- 第一次: 客户端 → 服务端 (证明客户端发送能力)
- 第二次: 服务端 → 客户端 (证明服务端发送和接收能力)
- 第三次: 客户端 → 服务端 (证明客户端接收能力)

**第二次握手合并了两个动作**:
- SYN: 服务端发起连接
- ACK: 服务端确认收到客户端的 SYN

如果分开发送，就是四次握手，但没有必要，增加了网络开销。

---

## 3. TCP 四次挥手 - 连接关闭过程

### 3.1 为什么需要四次挥手？

**核心原因**: TCP 是**全双工**通信，每个方向都需要单独关闭。

**关闭场景**:
- 客户端发送完数据，想关闭发送方向
- 服务端可能还有数据要发送，只能关闭接收方向
- 服务端发送完数据后，再关闭发送方向
- 客户端确认后，双方连接完全关闭

### 3.2 四次挥手流程

```
客户端                                     服务端
  |                                          |
  |  [ESTABLISHED]                           |  [ESTABLISHED]
  |                                          |
  |  (1) FIN seq=u                           |
  |  ---------------------------------------->|
  |  [FIN_WAIT_1]                            |  [CLOSE_WAIT]
  |                                          |
  |  (2) ACK seq=v, ack=u+1                  |
  |  <----------------------------------------|
  |  [FIN_WAIT_2]                            |  [CLOSE_WAIT]
  |                                          |  (服务端可能还有数据发送)
  |                                          |
  |  (3) FIN seq=w, ack=u+1                  |
  |  <----------------------------------------|
  |  [TIME_WAIT]                             |  [LAST_ACK]
  |                                          |
  |  (4) ACK seq=u+1, ack=w+1                |
  |  ---------------------------------------->|
  |  [TIME_WAIT]                             |  [CLOSED]
  |  (等待 2MSL)                             |
  |                                          |
  |  [CLOSED]                                |
  |                                          |
```

**详细步骤解析**:

**第一次挥手 (FIN)**:
- **发送方**: 客户端（主动关闭方）
- **标志位**: FIN=1
- **序列号**: seq=u
- **含义**: "我没有数据要发送了，想关闭连接"
- **状态变化**: 客户端 ESTABLISHED → FIN_WAIT_1

**第二次挥手 (ACK)**:
- **发送方**: 服务端（被动关闭方）
- **标志位**: ACK=1
- **确认号**: ack=u+1
- **含义**: "我收到了你的关闭请求，但我可能还有数据要发送"
- **状态变化**:
  - 服务端 ESTABLISHED → CLOSE_WAIT
  - 客户端 FIN_WAIT_1 → FIN_WAIT_2 (收到 ACK 后)

**第三次挥手 (FIN)**:
- **发送方**: 服务端
- **标志位**: FIN=1
- **序列号**: seq=w
- **确认号**: ack=u+1
- **含义**: "我也没有数据要发送了，可以关闭连接了"
- **状态变化**: 服务端 CLOSE_WAIT → LAST_ACK

**第四次挥手 (ACK)**:
- **发送方**: 客户端
- **标志位**: ACK=1
- **确认号**: ack=w+1
- **含义**: "我收到了你的关闭请求，连接关闭"
- **状态变化**:
  - 客户端 FIN_WAIT_2 → TIME_WAIT
  - 服务端 LAST_ACK → CLOSED (收到 ACK 后)
  - 客户端 TIME_WAIT → CLOSED (等待 2MSL 后)

### 3.3 为什么不能三次挥手？

**为什么第二次和第三次不能合并？**

**场景**: 服务端收到客户端的 FIN 后，可能还有数据要发送
- 第二次挥手 (ACK): "我收到了你的关闭请求，但我还有数据要发送"
- 第三次挥手 (FIN): "我发送完了，可以关闭了"

**如果合并 (ACK+FIN)**:
- 服务端必须在收到 FIN 的瞬间关闭发送通道
- 服务端缓冲区中的数据可能丢失
- 违反了"全双工独立关闭"的原则

**结论**: 四次挥手保证了双方都能优雅地完成数据发送后再关闭。

---

## 4. 关键状态详解

### 4.1 TIME_WAIT 状态

**状态定义**: 主动关闭方在发送最后一个 ACK 后进入的状态。

**持续时间**: **2MSL** (Maximum Segment Lifetime，最大报文段生存时间)
- MSL: 报文段在网络中的最大生存时间（通常 30 秒 ~ 2 分钟）
- 2MSL: 60 秒 ~ 4 分钟（Linux 默认 60 秒）

**为什么需要 TIME_WAIT？**

**原因 1: 确保最后一个 ACK 被对方收到**
```
场景:
1. 客户端发送最后一个 ACK 后立即关闭
2. ACK 在网络中丢失
3. 服务端超时重传 FIN
4. 客户端已关闭，收到 FIN 后发送 RST（复位）
5. 服务端异常关闭，可能丢失数据
```

**解决方案**: 客户端等待 2MSL
- 如果 ACK 丢失，服务端会重传 FIN
- 客户端在 TIME_WAIT 期间可以重传 ACK
- 确保连接正常关闭

**原因 2: 防止旧连接的数据干扰新连接**
```
场景:
1. 连接 A (192.168.1.1:8080 → 192.168.1.2:3306) 关闭
2. 立即创建连接 B (相同四元组)
3. 连接 A 的延迟数据到达
4. 连接 B 误以为是自己的数据
```

**解决方案**: 等待 2MSL
- 确保旧连接的所有数据包（往返 2 次）都已消失
- 新连接不会收到旧连接的数据

**TIME_WAIT 带来的问题**:

**问题**: 高并发场景下，大量 TIME_WAIT 占用端口资源
```bash
# 查看 TIME_WAIT 数量
netstat -an | grep TIME_WAIT | wc -l
```

**解决方案**:
1. **启用 SO_REUSEADDR**: 允许复用 TIME_WAIT 端口
   ```java
   serverSocket.setReuseAddress(true);
   ```

2. **调整系统参数** (Linux):
   ```bash
   # 审慎调整以下参数
   net.ipv4.tcp_tw_reuse = 1
   net.ipv4.tcp_fin_timeout = 30
   ```
   - `net.ipv4.tcp_tw_reuse` 仅对客户端短连接生效，并要求启用 TCP 时间戳；生产环境需经过验证后再开启。
   - `net.ipv4.tcp_fin_timeout` 只会缩短 FIN_WAIT2 的停留时间，对 TIME_WAIT 没有帮助。
   - `net.ipv4.tcp_tw_recycle` 已在 Linux 4.12 中移除，即便旧内核开启也会导致 NAT 环境异常，请勿启用。

3. **使用连接池**: 复用连接，减少频繁建立/关闭

### 4.2 CLOSE_WAIT 状态

**状态定义**: 被动关闭方在收到 FIN 后，发送 ACK 之后进入的状态。

**状态持续**: 直到应用程序调用 `close()` 主动关闭连接。

**正常流程**:
```
1. 服务端收到客户端的 FIN
2. 服务端 TCP 栈自动发送 ACK (进入 CLOSE_WAIT)
3. 服务端应用程序调用 close()
4. 服务端发送 FIN (进入 LAST_ACK)
5. 收到客户端的 ACK 后，进入 CLOSED
```

**CLOSE_WAIT 累积问题**:

**现象**: 大量连接停留在 CLOSE_WAIT 状态
```bash
# 查看 CLOSE_WAIT 数量
netstat -an | grep CLOSE_WAIT | wc -l
```

**根本原因**: **应用程序没有调用 `close()`**

**常见场景**:
1. **忘记关闭连接**:
   ```java
   // ❌ 错误示例
   Socket socket = serverSocket.accept();
   InputStream in = socket.getInputStream();
   // 处理数据...
   // 忘记 socket.close()
   ```

2. **异常处理不当**:
   ```java
   // ❌ 错误示例
   try {
       Socket socket = serverSocket.accept();
       // 处理数据...
       throw new Exception(); // 异常跳过了 close()
   } catch (Exception e) {
       // 忘记关闭 socket
   }
   ```

3. **业务逻辑阻塞**:
   ```java
   // ❌ 问题示例
   Socket socket = serverSocket.accept();
   while (true) {
       // 无限循环，永远不会 close()
   }
   ```

**解决方案**:

**1. 使用 try-with-resources (推荐)**:
```java
// ✅ 正确示例
try (Socket socket = serverSocket.accept();
     InputStream in = socket.getInputStream();
     OutputStream out = socket.getOutputStream()) {
    // 处理数据...
} // 自动调用 close()
```

**2. 显式关闭**:
```java
// ✅ 正确示例
Socket socket = null;
try {
    socket = serverSocket.accept();
    // 处理数据...
} finally {
    if (socket != null) {
        socket.close();
    }
}
```

**3. 监控和诊断**:
```bash
# 查看 CLOSE_WAIT 连接的详细信息
ss -tan | grep CLOSE-WAIT

# 查看持有 CLOSE_WAIT 连接的进程
lsof -i | grep CLOSE_WAIT
```

### 4.3 半关闭状态 (Half-Close)

**定义**: 一方关闭发送通道，但仍可接收数据。

**状态**: FIN_WAIT_2 (主动关闭方) 和 CLOSE_WAIT (被动关闭方)

**应用场景**: 客户端发送完请求后关闭发送，但继续接收服务端响应
```java
// 客户端示例
socket.shutdownOutput();  // 关闭发送，发送 FIN
// 继续读取服务端数据
InputStream in = socket.getInputStream();
// ...
socket.close();  // 完全关闭
```

**注意事项**:
- `shutdownOutput()`: 只关闭发送，发送 FIN
- `shutdownInput()`: 只关闭接收，不发送 FIN
- `close()`: 完全关闭，发送 FIN

---

## 5. TCP 状态机全景图

```
                              +---------+
                              |  CLOSED |
                              +---------+
                                   |
                                   | (主动打开)
                                   v
                              +---------+
                              | LISTEN  |
                              +---------+
                                   |
                    (收到 SYN)     |     (发送 SYN)
                                   v
          +------------+      +---------+      +------------+
          |  SYN_RCVD  |<-----|         |----->| SYN_SENT   |
          +------------+      +---------+      +------------+
                 |                                   |
    (发送 SYN+ACK)|                                   | (收到 SYN+ACK)
                 v                                   v
          +------------+                      +------------+
          |            |<-------------------->|            |
          | ESTABLISHED|                      | ESTABLISHED|
          |            |<-------------------->|            |
          +------------+                      +------------+
                 |                                   |
      (主动关闭) |                                   | (被动关闭)
      发送 FIN   |                                   | 收到 FIN
                 v                                   v
          +------------+                      +------------+
          | FIN_WAIT_1 |                      | CLOSE_WAIT |
          +------------+                      +------------+
                 |                                   |
   (收到 ACK)    |                                   | (发送 FIN)
                 v                                   v
          +------------+                      +------------+
          | FIN_WAIT_2 |                      |  LAST_ACK  |
          +------------+                      +------------+
                 |                                   |
   (收到 FIN)    |                                   | (收到 ACK)
   发送 ACK      |                                   |
                 v                                   v
          +------------+                      +---------+
          | TIME_WAIT  |                      |  CLOSED |
          +------------+                      +---------+
                 |
   (等待 2MSL)   |
                 v
          +---------+
          |  CLOSED |
          +---------+
```

**状态说明**:

| 状态 | 描述 | 角色 |
|------|------|------|
| CLOSED | 初始状态，无连接 | - |
| LISTEN | 服务端等待连接 | 服务端 |
| SYN_SENT | 发送 SYN，等待 ACK | 客户端 |
| SYN_RCVD | 收到 SYN，发送 SYN+ACK | 服务端 |
| ESTABLISHED | 连接建立，可传输数据 | 双方 |
| FIN_WAIT_1 | 发送 FIN，等待 ACK | 主动关闭方 |
| FIN_WAIT_2 | 收到 ACK，等待对方 FIN | 主动关闭方 |
| CLOSE_WAIT | 收到 FIN，等待应用程序关闭 | 被动关闭方 |
| LAST_ACK | 发送 FIN，等待最后的 ACK | 被动关闭方 |
| TIME_WAIT | 等待 2MSL，确保连接正常关闭 | 主动关闭方 |
| CLOSING | 同时关闭的特殊状态（罕见） | 双方 |

---

## 6. 常见问题与陷阱

### ❌ 陷阱 1: "大量 TIME_WAIT 导致服务无法接受新连接"

**误解**: TIME_WAIT 会占用服务端端口，导致无法监听

**真相**:
- **服务端监听端口**: 不会进入 TIME_WAIT（除非服务端主动关闭）
- **TIME_WAIT 影响客户端**: 客户端频繁建立连接时，本地端口耗尽

**正确场景**:
- **客户端作为主动关闭方**: 短连接客户端频繁关闭连接
- **解决方案**: 启用 `SO_REUSEADDR`，使用连接池

### ❌ 陷阱 2: "CLOSE_WAIT 是 TCP 协议的 bug"

**误解**: CLOSE_WAIT 是 TCP 的问题

**真相**: **CLOSE_WAIT 是应用层的问题**
- TCP 已经正常发送了 ACK
- 应用程序没有调用 `close()`
- 根本原因: 代码 bug、资源泄漏

**排查方法**:
```bash
# 1. 查看 CLOSE_WAIT 连接
netstat -anp | grep CLOSE_WAIT

# 2. 查看进程持有的文件描述符
lsof -p <PID> | grep TCP

# 3. 查看 Java 线程栈
jstack <PID> | grep -A 10 "Socket"
```

### ❌ 陷阱 3: "握手失败后立即重试"

**问题**: 客户端握手失败后立即重试，导致连接风暴

**场景**:
```
1. 客户端发送 SYN
2. 服务端无响应（网络抖动、服务过载）
3. 客户端立即重试（发送新的 SYN）
4. 大量重试导致服务端更加过载
```

**解决方案**: **指数退避 (Exponential Backoff)**
```java
int retries = 0;
int maxRetries = 5;
int baseDelay = 1000; // 1 秒

while (retries < maxRetries) {
    try {
        Socket socket = new Socket("localhost", 8080);
        // 连接成功
        break;
    } catch (IOException e) {
        retries++;
        int delay = baseDelay * (1 << (retries - 1)); // 1s, 2s, 4s, 8s, 16s
        Thread.sleep(delay);
    }
}
```

---

## 7. 实战诊断技巧

### 7.1 查看 TCP 连接状态

**Linux/Mac**:
```bash
# 查看所有 TCP 连接状态
netstat -tan

# 统计各状态的连接数
netstat -tan | awk '{print $6}' | sort | uniq -c

# 查看特定端口的连接
ss -tan 'sport = :8080'

# 实时监控连接状态变化
watch -n 1 "netstat -tan | grep ESTABLISHED | wc -l"
```

**Java 程序**:
```java
// 查看 Socket 状态（Java 11+）
Socket socket = new Socket("localhost", 8080);
System.out.println("Connected: " + socket.isConnected());
System.out.println("Closed: " + socket.isClosed());
System.out.println("Input Shutdown: " + socket.isInputShutdown());
System.out.println("Output Shutdown: " + socket.isOutputShutdown());
```

### 7.2 诊断 TIME_WAIT 问题

**场景**: 高并发短连接导致 TIME_WAIT 过多

**诊断步骤**:
```bash
# 1. 统计 TIME_WAIT 数量
netstat -tan | grep TIME_WAIT | wc -l

# 2. 查看本地端口使用情况
cat /proc/sys/net/ipv4/ip_local_port_range
# 默认: 32768 - 60999 (约 28000 个端口)

# 3. 如果 TIME_WAIT > 20000，考虑优化
```

**优化方案**:
1. **使用连接池** (推荐)
2. **启用端口复用**:
   ```java
   socket.setReuseAddress(true);
   ```
3. **调整系统参数**:
   ```bash
   net.ipv4.tcp_tw_reuse = 1
   net.ipv4.tcp_fin_timeout = 30
   ```
   - `net.ipv4.tcp_tw_reuse` 仅对客户端短连接生效，并要求启用 TCP 时间戳；生产环境需经过验证后再开启。
   - `net.ipv4.tcp_fin_timeout` 只会缩短 FIN_WAIT2 的停留时间，对 TIME_WAIT 没有帮助。
   - `net.ipv4.tcp_tw_recycle` 已在 Linux 4.12 中移除，即便旧内核开启也会导致 NAT 环境异常，请勿启用。

### 7.3 诊断 CLOSE_WAIT 问题

**场景**: 服务端 CLOSE_WAIT 累积，最终无法接受新连接

**诊断步骤**:
```bash
# 1. 查看 CLOSE_WAIT 数量
netstat -tan | grep CLOSE_WAIT | wc -l

# 2. 查看进程和线程
lsof -i TCP:8080 | grep CLOSE_WAIT

# 3. 查看 Java 线程栈
jstack <PID> > thread_dump.txt
```

**代码审查**:
```java
// ❌ 常见错误 1: 忘记关闭
Socket socket = serverSocket.accept();
// 处理...
// 忘记 socket.close()

// ❌ 常见错误 2: 异常路径未关闭
try {
    Socket socket = serverSocket.accept();
    // 处理...
} catch (Exception e) {
    // 忘记关闭 socket
}

// ✅ 正确方式: try-with-resources
try (Socket socket = serverSocket.accept()) {
    // 处理...
} // 自动关闭
```

---

## 8. 性能优化建议

### 8.1 减少握手开销

**问题**: 每次连接都需要三次握手，增加延迟

**优化方案**:

**1. 使用 TCP Fast Open (TFO)**:
- 允许在 SYN 包中携带数据
- 减少一次 RTT (Round-Trip Time)
- Linux 3.7+ 支持

```bash
# 启用 TFO
echo 3 > /proc/sys/net/ipv4/tcp_fastopen
```

**2. 使用 HTTP/2 或 HTTP/3**:
- 单连接多路复用，减少握手次数
- HTTP/3 基于 QUIC (UDP)，进一步减少延迟

**3. 使用连接池**:
```java
// HikariCP 连接池示例
HikariConfig config = new HikariConfig();
config.setMaximumPoolSize(10);
config.setMinimumIdle(5);
HikariDataSource ds = new HikariDataSource(config);
```

### 8.2 优雅关闭连接

**问题**: 粗暴关闭导致数据丢失或资源泄漏

**最佳实践**:

**1. 使用 shutdownOutput() 实现半关闭**:
```java
// 客户端发送完数据后
socket.shutdownOutput(); // 发送 FIN，但仍可接收

// 继续接收服务端响应
BufferedReader in = new BufferedReader(
    new InputStreamReader(socket.getInputStream())
);
String response = in.readLine();

// 完全关闭
socket.close();
```

**2. 设置合理的超时**:
```java
socket.setSoTimeout(30000); // 30 秒读超时
socket.setSoLinger(true, 5); // 5 秒优雅关闭
```

**3. 处理连接关闭异常**:
```java
try {
    socket.close();
} catch (IOException e) {
    // 记录日志，但不要抛出异常
    logger.warn("Failed to close socket", e);
}
```

---

## 9. 延伸阅读

- **TCP/IP Illustrated, Volume 1** - W. Richard Stevens (第 13、17、18 章)
- **UNIX Network Programming** - W. Richard Stevens (第 2、6 章)
- **High Performance Browser Networking** - Ilya Grigorik (第 2 章)
- **RFC 793** - TCP 协议规范
- **RFC 1323** - TCP 扩展（窗口缩放、时间戳）
- **Linux 内核 TCP 实现**: `net/ipv4/tcp.c`

---

**下一步**: 阅读 `FILE_DESCRIPTORS.md`，理解 Socket 在操作系统层面的文件描述符管理。
