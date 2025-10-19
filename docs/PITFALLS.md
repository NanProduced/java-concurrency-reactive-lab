# 常见坑库 (PITFALLS)

> **目标**: 记录 Java 并发编程中遇到的常见坑、问题及其解决方案
> **维护**: 每个 Lab 完成后补充
> **使用场景**: 快速查阅已知问题的解决方案

---

## 坑索引

| 坑 ID | 标题 | Lab | 状态 |
|-------|------|-----|------|
| PITFALL-001 | ThreadLocal 内存泄漏 | Lab-01 | ⏳ 待补充 |
| PITFALL-002 | 线程池拒绝策略不当 | Lab-03 | ⏳ 待补充 |
| PITFALL-003 | CompletableFuture 异常丢失 | Lab-05 | ⏳ 待补充 |
| PITFALL-004 | Reactor 背压处理不当 | Lab-07 | ⏳ 待补充 |
| PITFALL-005 | 容器环境中的 CPU 检测失败 | Lab-00 | ✅ 已记录 |
| PITFALL-006 | wait-notify 使用 if 而非 while | Lab-02 | ✅ 已记录 |
| PITFALL-007 | ReentrantLock 忘记在 finally 中释放锁 | Lab-02 | ✅ 已记录 |
| PITFALL-008 | synchronized 锁定不同对象 | Lab-02 | ✅ 已记录 |
| PITFALL-009 | 死锁的四种场景及避免策略 | Lab-02 | ✅ 已记录 |
| PITFALL-010 | Semaphore 许可数设置不当 | Lab-02 | ✅ 已记录 |
| PITFALL-011 | Netty ByteBuf 内存泄漏 | Lab-07 | ✅ 已记录 |
| PITFALL-012 | Netty EventLoop 线程阻塞 | Lab-07 | ✅ 已记录 |
| PITFALL-013 | Netty 共享 Handler 线程不安全 | Lab-07 | ✅ 已记录 |
| PITFALL-014 | Netty 背压未处理导致 OOM | Lab-07 | ✅ 已记录 |

---

## PITFALL-005: 容器环境中的 CPU 检测失败

**Lab**: Lab-00
**发现日期**: 2025-10-17
**严重程度**: 中等

### 现象

在 Docker 容器中运行 Java 应用时，`Runtime.getRuntime().availableProcessors()` 返回的值与实际容器的 CPU 限制不一致。例如：
- 容器配置: `--cpus=2`
- `availableProcessors()` 返回: 16 (宿主机的 CPU 数)
- 结果: 线程池配置过大，导致资源浪费或 OOM

### 根因分析

Java 早期版本不能识别 Linux cgroups 的 CPU 限制。`availableProcessors()` 直接读取 `/proc/cpuinfo` 而不是 cgroups 限制。

### 修复方案

**方案 1**: 使用 JVM 参数显式指定 (推荐容器环境)
```bash
java -XX:ActiveProcessorCount=2 -jar app.jar
```

**方案 2**: 在代码中检测 cgroups 限制
```java
public static int getCgroupCpuLimit() {
    try {
        // 读取 /sys/fs/cgroup/cpu.max
        String limit = new String(Files.readAllBytes(Paths.get("/sys/fs/cgroup/cpu.max")));
        if (limit.contains("max")) return Runtime.getRuntime().availableProcessors();
        return Integer.parseInt(limit.split(" ")[0]);
    } catch (Exception e) {
        return Runtime.getRuntime().availableProcessors();
    }
}
```

**方案 3**: 更新到 JDK 11+ (原生支持)
- JDK 11+ 默认支持 cgroups v1
- JDK 16+ 支持 cgroups v2

### 最佳实践

1. **容器环境**: 总是使用 `-XX:ActiveProcessorCount=N` 显式指定
2. **本地开发**: 可以不指定（使用宿主机 CPU）
3. **监控**: 记录实际使用的 CPU 数，避免过度配置

### 代码位置

`nan/tech/common/utils/ThreadUtil.java:43`

```java
public static int getProcessorCount() {
    // 当前实现: return Runtime.getRuntime().availableProcessors();
    // TODO: 在容器环境中应检查 cgroups 限制
}
```

### 参考资源

- Java Concurrency in Practice, Chapter 8
- Docker CPU limits: https://docs.docker.com/config/containers/resource_constraints/
- JDK 11 Release Notes: cgroups support

---

## PITFALL-006: wait-notify 使用 if 而非 while

**Lab**: Lab-02
**发现日期**: 2025-10-18
**严重程度**: 高

### 现象

在使用 wait-notify 机制时，使用 `if` 而不是 `while` 检查条件，导致虚假唤醒（spurious wakeup）问题。例如：

```java
// ❌ 错误：使用 if 检查条件
synchronized (lock) {
    if (queue.isEmpty()) {  // 问题：使用 if
        lock.wait();
    }
    // 可能队列仍然是空的（虚假唤醒）
    Object item = queue.remove(); // 抛出 NoSuchElementException
}
```

### 根因分析

1. **虚假唤醒（Spurious Wakeup）**: JVM 规范允许线程在没有被 notify/notifyAll 的情况下唤醒
2. **多消费者竞争**: 多个消费者被 notifyAll 唤醒后，第一个消费者可能已经取走了元素
3. **条件再次检查**: 线程被唤醒后，条件可能已经不再满足

### 修复方案

**正确做法：使用 while 循环检查条件**

```java
// ✅ 正确：使用 while 循环
synchronized (lock) {
    while (queue.isEmpty()) {  // 正确：使用 while
        lock.wait();
    }
    // 确保条件满足后才执行
    Object item = queue.remove();
}
```

### 错误示例与正确示例对比

**错误示例：**
```java
public synchronized void consume() throws InterruptedException {
    if (queue.isEmpty()) {  // ❌ if
        wait();
    }
    Integer item = queue.poll();
    logger.info("消费: {}", item);
    notifyAll();
}
```

**正确示例：**
```java
public synchronized void consume() throws InterruptedException {
    while (queue.isEmpty()) {  // ✅ while
        wait();
    }
    Integer item = queue.poll();
    logger.info("消费: {}", item);
    notifyAll();
}
```

### 最佳实践

1. **永远使用 while 而不是 if**: 这是 wait-notify 的黄金法则
2. **多线程环境必须检查**: 即使是单生产者单消费者，也要用 while
3. **重新检查条件**: 被唤醒后，条件可能已经改变

### 代码位置

`lab-02-synchronization/src/main/java/nan/tech/lab02/waitnotify/WaitNotifyDemo.java:151-175`

### 参考资源

- Java Concurrency in Practice, Chapter 14.2
- Effective Java, Item 81: Prefer concurrency utilities to wait and notify
- Oracle Java Documentation: Object.wait()

---

## PITFALL-007: ReentrantLock 忘记在 finally 中释放锁

**Lab**: Lab-02
**发现日期**: 2025-10-18
**严重程度**: 高

### 现象

使用 ReentrantLock 时，忘记在 finally 块中释放锁，导致异常发生时锁无法释放，造成死锁或资源泄漏。

```java
// ❌ 错误：没有 finally
lock.lock();
doSomething(); // 如果抛出异常，锁永远不会释放
lock.unlock();
```

### 根因分析

1. **异常导致跳过 unlock**: 如果 `doSomething()` 抛出异常，`unlock()` 永远不会执行
2. **锁泄漏**: 其他等待该锁的线程将永远阻塞
3. **与 synchronized 的区别**: synchronized 会自动释放锁，ReentrantLock 必须手动释放

### 修复方案

**正确做法：在 finally 中释放锁**

```java
// ✅ 正确：finally 中释放锁
lock.lock();
try {
    doSomething();
} finally {
    lock.unlock(); // 保证一定会释放
}
```

### 完整示例

```java
public class LockCounter {
    private final Lock lock = new ReentrantLock();
    private int count = 0;

    // ❌ 错误示例
    public void incrementWrong() {
        lock.lock();
        count++; // 如果这里抛出异常（虽然不太可能）
        lock.unlock(); // 不会执行
    }

    // ✅ 正确示例
    public void increment() {
        lock.lock();
        try {
            count++;
        } finally {
            lock.unlock(); // 保证释放
        }
    }
}
```

### 最佳实践

1. **强制使用 finally**: 100% 的 ReentrantLock 都应该在 finally 中 unlock
2. **IDE 模板**: 配置 IDE 代码模板，自动生成 lock-try-finally 结构
3. **代码审查重点**: 检查所有 lock() 是否都有对应的 finally unlock()

### 代码位置

`lab-02-synchronization/src/main/java/nan/tech/lab02/lock/LockVsSynchronizedDemo.java:335-357`

### 参考资源

- Java Concurrency in Practice, Chapter 13
- ReentrantLock Javadoc
- Effective Java, Item 9: Prefer try-with-resources to try-finally

---

## PITFALL-008: synchronized 锁定不同对象

**Lab**: Lab-02
**发现日期**: 2025-10-18
**严重程度**: 高

### 现象

使用 synchronized 时，每次锁定不同的对象实例，导致无法实现同步。

```java
// ❌ 错误：每次都是新对象
synchronized (new Object()) {
    count++; // 无法同步，多个线程可以同时进入
}
```

### 根因分析

1. **锁对象不同**: 每次 `new Object()` 创建不同的对象，相当于不同的锁
2. **无法互斥**: 多个线程锁定不同对象，不会互相阻塞
3. **数据竞争**: 共享变量 `count` 没有受到保护

### 修复方案

**正确做法：锁定同一个对象**

```java
// ✅ 正确：锁定同一个对象
private final Object lock = new Object(); // 固定的锁对象

synchronized (lock) {
    count++; // 所有线程锁定同一个对象
}
```

### 常见错误模式

**错误 1：每次创建新锁**
```java
// ❌ 错误
public void increment() {
    synchronized (new Object()) { // 每次都是新对象
        count++;
    }
}
```

**错误 2：锁定局部变量**
```java
// ❌ 错误
public void increment() {
    Object lock = new Object(); // 局部变量，每次调用都不同
    synchronized (lock) {
        count++;
    }
}
```

**正确做法：**
```java
// ✅ 正确
private final Object lock = new Object(); // 实例变量或静态变量

public void increment() {
    synchronized (lock) {
        count++;
    }
}
```

### 最佳实践

1. **使用实例变量或静态变量**: 确保所有线程锁定同一个对象
2. **final 修饰锁对象**: 防止锁对象被意外修改
3. **命名规范**: 锁对象命名为 `lock` 或 `mutex`，提高可读性
4. **避免锁定公共对象**: 不要锁定 String、Integer 等公共对象

### 代码位置

参考正确实现：`lab-02-synchronization/src/main/java/nan/tech/lab02/lock/LockVsSynchronizedDemo.java:318-329`

### 参考资源

- Java Concurrency in Practice, Chapter 2
- Effective Java, Item 82: Document thread safety
- Oracle Java Documentation: Intrinsic Locks and Synchronization

---

## PITFALL-009: 死锁的四种场景及避免策略

**Lab**: Lab-02
**发现日期**: 2025-10-18
**严重程度**: 高

### 现象

多个线程因为循环等待资源而永久阻塞，系统无法继续执行。

### 死锁的四个必要条件

1. **互斥条件**: 资源不能被共享，只能由一个线程使用
2. **持有并等待**: 线程持有资源的同时，等待获取其他资源
3. **不可抢占**: 资源不能被强制剥夺，只能主动释放
4. **循环等待**: 存在资源的循环等待链

> **破坏任一条件即可避免死锁！**

### 场景 1：循环等待（经典死锁）

**错误示例：**
```java
// 线程 A
synchronized (lock1) {
    synchronized (lock2) { ... }
}

// 线程 B
synchronized (lock2) {  // ❌ 顺序不同
    synchronized (lock1) { ... }
}
```

**正确做法：固定锁顺序**
```java
// 所有线程都按 lock1 → lock2 的顺序
// 线程 A
synchronized (lock1) {
    synchronized (lock2) { ... }
}

// 线程 B
synchronized (lock1) {  // ✅ 相同顺序
    synchronized (lock2) { ... }
}
```

### 场景 2：转账死锁（顺序死锁）

**错误示例：**
```java
// A 转账给 B
synchronized (accountA) {
    synchronized (accountB) { ... }
}

// B 转账给 A（同时发生）
synchronized (accountB) {  // ❌ 可能死锁
    synchronized (accountA) { ... }
}
```

**正确做法：使用对象哈希值排序**
```java
public void transferTo(Account target, int amount) {
    // 按哈希值排序，确保锁的顺序一致
    Account first = this.hashCode() < target.hashCode() ? this : target;
    Account second = this == first ? target : this;

    synchronized (first) {
        synchronized (second) {
            // 执行转账
        }
    }
}
```

### 场景 3：使用 tryLock 避免死锁

```java
// ✅ 使用 tryLock 避免无限等待
while (true) {
    if (lock1.tryLock()) {
        try {
            if (lock2.tryLock()) {
                try {
                    // 成功获取两个锁
                    return;
                } finally {
                    lock2.unlock();
                }
            } else {
                // 获取 lock2 失败，释放 lock1 并重试
            }
        } finally {
            lock1.unlock();
        }
    }
    Thread.sleep(10); // 短暂等待后重试
}
```

### 场景 4：死锁检测

**使用 jstack 检测：**
```bash
# 找到 Java 进程 ID
jps

# 导出线程堆栈
jstack <pid> > thread_dump.txt

# 查看死锁信息
# 搜索 "Found one Java-level deadlock"
```

**使用代码检测：**
```java
ThreadMXBean bean = ManagementFactory.getThreadMXBean();
long[] threadIds = bean.findDeadlockedThreads();
if (threadIds != null) {
    logger.error("检测到死锁！涉及线程数: {}", threadIds.length);
}
```

### 避免策略总结

| 策略 | 适用场景 | 优点 | 缺点 |
|------|---------|------|------|
| 固定锁顺序 | 静态锁集合 | 简单有效 | 需要全局协调 |
| 锁排序（哈希值） | 动态锁（如转账） | 自动化 | 哈希冲突处理复杂 |
| tryLock | 高级场景 | 避免永久阻塞 | 需要重试逻辑 |
| 超时机制 | 用户交互 | 响应性好 | 需要处理超时 |
| 死锁检测 | 监控告警 | 发现问题 | 不能预防 |

### 最佳实践

1. **优先使用固定锁顺序**: 最简单有效的方式
2. **转账场景用锁排序**: 使用对象哈希值或 ID 排序
3. **使用 tryLock 作为保险**: 在可能死锁的地方使用 tryLock
4. **定期检测死锁**: 在生产环境中定期运行死锁检测
5. **监控和告警**: 配置线程监控和死锁告警

### 代码位置

`lab-02-synchronization/src/main/java/nan/tech/lab02/deadlock/DeadlockDemo.java:63-393`

### 参考资源

- Java Concurrency in Practice, Chapter 10: Avoiding Liveness Hazards
- Operating System Concepts, Deadlock chapter
- jstack command reference

---

## PITFALL-010: Semaphore 许可数设置不当

**Lab**: Lab-02
**发现日期**: 2025-10-18
**严重程度**: 中等

### 现象

Semaphore 的许可数（permits）设置过大或过小，导致性能问题或资源浪费。

### 场景 1：许可数过小

```java
// ❌ 许可数过小
Semaphore semaphore = new Semaphore(1); // 数据库连接池只有 1 个连接

// 在高并发场景下，大量线程等待
semaphore.acquire(); // 大部分线程阻塞在这里
try {
    // 数据库操作
} finally {
    semaphore.release();
}
```

**问题**: 并发度过低，系统吞吐量受限

### 场景 2：许可数过大

```java
// ❌ 许可数过大
Semaphore semaphore = new Semaphore(1000); // 数据库连接池 1000 个

// 资源不足时会导致：
// 1. 数据库连接耗尽
// 2. 内存溢出
// 3. 系统崩溃
```

**问题**: 资源耗尽，系统不稳定

### 修复方案

**正确做法：根据实际资源容量设置**

```java
// ✅ 根据数据库连接池大小设置
int dbPoolSize = dataSource.getMaxPoolSize(); // 例如 20
Semaphore semaphore = new Semaphore(dbPoolSize);

// 或者使用经验值
Semaphore apiLimiter = new Semaphore(100); // API 限流：最多 100 并发请求
```

### 最佳实践

1. **数据库连接池**: permits = 数据库连接池大小（通常 10-50）
2. **API 限流**: permits = 期望的最大 QPS / 平均响应时间（秒）
3. **资源池**: permits = 实际资源数量
4. **监控调整**: 通过监控数据动态调整许可数

### 经验值参考

| 场景 | 建议许可数 | 说明 |
|------|-----------|------|
| 数据库连接池 | 10-50 | 根据数据库性能 |
| 文件句柄 | 1000-5000 | 操作系统限制 |
| HTTP 连接 | 100-500 | 根据服务器能力 |
| API 限流 | 100-1000 | 根据业务需求 |

### 代码位置

`lab-02-synchronization/src/main/java/nan/tech/lab02/semaphore/SemaphoreDemo.java`

### 参考资源

- Java Concurrency in Practice, Chapter 5.5
- Semaphore Javadoc

---

## PITFALL-011: Netty ByteBuf 内存泄漏

**Lab**: Lab-07
**发现日期**: 2025-10-19
**严重程度**: 高

### 现象

使用 Netty ByteBuf 时，忘记释放内存引用计数，导致堆外内存（Direct Memory）泄漏，最终引发 OOM。

```java
// ❌ 错误：未释放 ByteBuf
@Override
public void channelRead(ChannelHandlerContext ctx, Object msg) {
    ByteBuf in = (ByteBuf) msg;
    // 处理数据...
    // 忘记释放！导致内存泄漏
}
```

### 根因分析

1. **引用计数机制**: Netty ByteBuf 使用引用计数管理内存，必须显式调用 `release()`
2. **堆外内存**: Direct ByteBuf 使用堆外内存，不受 JVM GC 管理
3. **内存泄漏检测**: Netty 的 `-Dio.netty.leakDetection.level=PARANOID` 可以检测泄漏

### 修复方案

**方式 1：try-finally 手动释放**
```java
// ✅ 正确：try-finally
@Override
public void channelRead(ChannelHandlerContext ctx, Object msg) {
    ByteBuf in = (ByteBuf) msg;
    try {
        // 处理数据...
    } finally {
        ReferenceCountUtil.release(in);  // 必须释放
    }
}
```

**方式 2：使用 SimpleChannelInboundHandler（推荐）**
```java
// ✅ 推荐：自动释放
public class MyHandler extends SimpleChannelInboundHandler<ByteBuf> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
        // 处理数据...
        // SimpleChannelInboundHandler 自动释放，无需手动 release()
    }
}
```

**方式 3：retain() 和 release() 配对**
```java
// ✅ 传递给其他 Handler 时需要 retain()
@Override
public void channelRead(ChannelHandlerContext ctx, Object msg) {
    ByteBuf in = (ByteBuf) msg;
    try {
        // 传递给下一个 Handler（增加引用计数）
        ctx.fireChannelRead(in.retain());
    } finally {
        // 释放当前 Handler 的引用
        ReferenceCountUtil.release(in);
    }
}
```

### 内存泄漏检测

**启用 Netty 内存泄漏检测**:
```bash
# 开发环境：PARANOID 级别（性能损失 100%+）
java -Dio.netty.leakDetection.level=PARANOID -jar app.jar

# 生产环境：SIMPLE 级别（性能损失 1%）
java -Dio.netty.leakDetection.level=SIMPLE -jar app.jar

# 级别说明：
# DISABLED: 完全禁用（不推荐）
# SIMPLE:   采样检测（生产环境推荐）
# ADVANCED: 详细报告（测试环境）
# PARANOID: 全量检测（开发环境）
```

**检测输出示例**:
```
ERROR io.netty.util.ResourceLeakDetector - LEAK: ByteBuf.release() was not called before it's garbage-collected.
Recent access records:
#1:
    io.netty.buffer.AdvancedLeakAwareByteBuf.writeBytes(...)
    nan.tech.lab07.echo.NettyEchoServer$EchoHandler.channelRead(NettyEchoServer.java:85)
```

### 最佳实践

1. **优先使用 SimpleChannelInboundHandler**: 自动释放，减少出错
2. **手动管理时使用 try-finally**: 确保 release() 总是被调用
3. **开启泄漏检测**: 开发环境使用 PARANOID，生产环境使用 SIMPLE
4. **retain() 和 release() 配对**: 传递 ByteBuf 时注意引用计数
5. **不要重复释放**: 同一个 ByteBuf 不要多次 release()

### 代码位置

- 错误示例参考：README.md 常见坑章节
- 正确实现：`lab-07-netty/src/main/java/nan/tech/lab07/echo/NettyEchoServer.java:85-95`

### 参考资源

- [Netty Reference Counted Objects](https://netty.io/wiki/reference-counted-objects.html)
- [Resource Leak Detector](https://netty.io/wiki/reference-counted-objects.html#wiki-h3-11)
- Netty in Action, Chapter 5: ByteBuf

---

## PITFALL-012: Netty EventLoop 线程阻塞

**Lab**: Lab-07
**发现日期**: 2025-10-19
**严重程度**: 高

### 现象

在 EventLoop 线程中执行阻塞操作（如数据库查询、文件 I/O、Thread.sleep），导致其他 Channel 的 I/O 事件无法及时处理，系统吞吐量下降。

```java
// ❌ 错误：在 EventLoop 线程中执行阻塞操作
@Override
public void channelRead(ChannelHandlerContext ctx, Object msg) {
    // 数据库查询（阻塞 100ms）
    User user = userDao.queryById(123);  // ❌ 阻塞 EventLoop！

    // 文件 I/O（阻塞）
    String data = Files.readString(Paths.get("data.txt"));  // ❌ 阻塞！

    ctx.writeAndFlush(user);
}
```

### 根因分析

1. **单线程模型**: 每个 EventLoop 是单线程执行，阻塞会影响所有绑定的 Channel
2. **I/O 事件延迟**: 阻塞期间，其他 Channel 的 read/write 事件无法处理
3. **系统吞吐量下降**: 假设 100ms 阻塞，TPS 从 10K 降到 10 req/s（1000x 下降）

### 修复方案

**方式 1：使用独立线程池**
```java
// ✅ 正确：独立线程池执行阻塞操作
private final ExecutorService executor = Executors.newFixedThreadPool(10);

@Override
public void channelRead(ChannelHandlerContext ctx, Object msg) {
    executor.submit(() -> {
        // 阻塞操作在独立线程池执行
        User user = userDao.queryById(123);

        // 切回 EventLoop 线程发送响应
        ctx.executor().execute(() -> {
            ctx.writeAndFlush(user);
        });
    });
}
```

**方式 2：使用 EventExecutorGroup**
```java
// ✅ 正确：配置独立的 EventExecutorGroup
EventExecutorGroup businessGroup = new DefaultEventExecutorGroup(10);

// 在 Pipeline 中添加 Handler 时指定
pipeline.addLast(businessGroup, "businessHandler", new BusinessHandler());

// BusinessHandler 中的阻塞操作不会阻塞 I/O 线程
public class BusinessHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        // 在 businessGroup 线程池中执行，不阻塞 EventLoop
        User user = userDao.queryById(123);
        ctx.writeAndFlush(user);
    }
}
```

**方式 3：使用异步 API**
```java
// ✅ 最佳：使用异步 API
@Override
public void channelRead(ChannelHandlerContext ctx, Object msg) {
    // 异步数据库查询
    CompletableFuture<User> future = userDao.queryByIdAsync(123);

    future.thenAccept(user -> {
        // 回调在异步线程执行后切回 EventLoop
        ctx.executor().execute(() -> {
            ctx.writeAndFlush(user);
        });
    });
}
```

### 性能对比

| 场景 | EventLoop 阻塞 | 独立线程池 | 提升 |
|------|---------------|-----------|------|
| 数据库查询（100ms） | 10 req/s | 10,000+ req/s | **1000x** |
| 文件 I/O（50ms） | 20 req/s | 20,000+ req/s | **1000x** |
| 外部 API（200ms） | 5 req/s | 5,000+ req/s | **1000x** |

### 最佳实践

1. **禁止阻塞操作**: EventLoop 线程中禁止任何阻塞操作
2. **使用异步 API**: 优先使用异步数据库、异步 HTTP 客户端
3. **独立线程池**: 必须阻塞时使用独立线程池
4. **监控 EventLoop**: 监控 EventLoop 线程的执行时间，发现异常

### 代码位置

- 正确实现：`lab-07-netty/README.md` 常见坑章节示例代码
- EventExecutorGroup 用法：参考 Netty 官方示例

### 参考资源

- Netty in Action, Chapter 7: EventLoop and threading model
- [Netty Best Practices](https://netty.io/wiki/user-guide-for-4.x.html)

---

## PITFALL-013: Netty 共享 Handler 线程不安全

**Lab**: Lab-07
**发现日期**: 2025-10-19
**严重程度**: 中等

### 现象

使用 `@ChannelHandler.Sharable` 注解标记 Handler 为共享，但在 Handler 中使用实例变量，导致多线程竞争和数据错乱。

```java
// ❌ 错误：共享 Handler 使用实例变量
@ChannelHandler.Sharable  // 标记为共享
public class MyHandler extends ChannelInboundHandlerAdapter {
    private int requestCount = 0;  // ❌ 线程不安全！

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        requestCount++;  // 多线程竞争
        logger.info("请求数: {}", requestCount);  // 数据错乱
    }
}
```

### 根因分析

1. **共享实例**: `@Sharable` 使得一个 Handler 实例被多个 Channel 共享
2. **多线程访问**: 不同 Channel 可能在不同 EventLoop 线程中同时访问
3. **数据竞争**: 实例变量 `requestCount` 存在 race condition

### 修复方案

**方式 1：使用 ThreadLocal**
```java
// ✅ 正确：ThreadLocal
@ChannelHandler.Sharable
public class MyHandler extends ChannelInboundHandlerAdapter {
    private final ThreadLocal<Integer> requestCount = ThreadLocal.withInitial(() -> 0);

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        requestCount.set(requestCount.get() + 1);
        logger.info("请求数: {}", requestCount.get());
    }
}
```

**方式 2：使用 AtomicInteger**
```java
// ✅ 正确：AtomicInteger（推荐）
@ChannelHandler.Sharable
public class MyHandler extends ChannelInboundHandlerAdapter {
    private final AtomicInteger requestCount = new AtomicInteger(0);

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        int count = requestCount.incrementAndGet();
        logger.info("请求数: {}", count);
    }
}
```

**方式 3：不共享 Handler（推荐）**
```java
// ✅ 最佳：每个 Channel 独立实例
// 移除 @Sharable 注解
public class MyHandler extends ChannelInboundHandlerAdapter {
    private int requestCount = 0;  // ✅ 每个 Channel 独立，线程安全

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        requestCount++;
        logger.info("请求数: {}", requestCount);
    }
}

// Pipeline 配置
pipeline.addLast(new MyHandler());  // 每次创建新实例
```

**方式 4：使用 Channel 属性**
```java
// ✅ 正确：Channel 属性（适合 Channel 级别的状态）
@ChannelHandler.Sharable
public class MyHandler extends ChannelInboundHandlerAdapter {
    private static final AttributeKey<Integer> REQUEST_COUNT =
        AttributeKey.valueOf("requestCount");

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        Attribute<Integer> attr = ctx.channel().attr(REQUEST_COUNT);
        Integer count = attr.get();
        if (count == null) count = 0;
        attr.set(count + 1);

        logger.info("请求数: {}", attr.get());
    }
}
```

### 最佳实践

1. **避免共享 Handler**: 除非性能关键，否则不要使用 @Sharable
2. **无状态设计**: 共享 Handler 应该无状态（只读字段或 final 字段）
3. **使用 Channel 属性**: 需要 Channel 级别状态时使用 AttributeKey
4. **线程安全工具**: 必须用实例变量时使用 AtomicXxx 或 Concurrent 集合

### 何时使用 @Sharable

**适合共享的场景**:
```java
// ✅ 适合共享：纯业务逻辑，无状态
@ChannelHandler.Sharable
public class LoggingHandler extends ChannelInboundHandlerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(LoggingHandler.class);

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        logger.info("收到消息: {}", msg);  // 仅日志，无状态
        ctx.fireChannelRead(msg);
    }
}
```

**不适合共享的场景**:
```java
// ❌ 不适合共享：有状态（计数器、缓存、连接状态等）
public class StatefulHandler extends ChannelInboundHandlerAdapter {
    private int requestCount = 0;  // 状态
    private final Map<String, Object> cache = new HashMap<>();  // 状态
}
```

### 代码位置

- 错误示例：README.md 常见坑章节
- 正确实现：`lab-07-netty/src/main/java/nan/tech/lab07/echo/NettyEchoServer.java`

### 参考资源

- Netty in Action, Chapter 6: ChannelHandler and ChannelPipeline
- [Netty @Sharable Annotation](https://netty.io/4.1/api/io/netty/channel/ChannelHandler.Sharable.html)

---

## PITFALL-014: Netty 背压未处理导致 OOM

**Lab**: Lab-07
**发现日期**: 2025-10-19
**严重程度**: 高

### 现象

网络写入速度慢于生产速度时，未处理背压（Backpressure），导致 ChannelOutboundBuffer 积压大量数据，最终内存溢出（OOM）。

```java
// ❌ 错误：不检查 isWritable，持续写入
@Override
public void channelRead(ChannelHandlerContext ctx, Object msg) {
    ctx.writeAndFlush(msg);  // ❌ 网络慢时会积压大量数据
}
```

### 根因分析

1. **生产速度 > 消费速度**: 应用产生数据的速度超过网络发送速度
2. **缓冲区积压**: 未发送的数据在 ChannelOutboundBuffer 中堆积
3. **内存溢出**: 缓冲区无限增长，最终导致 OOM

### 修复方案

**方式 1：检查 isWritable() + 暂停读取**
```java
// ✅ 正确：检查可写状态
@Override
public void channelRead(ChannelHandlerContext ctx, Object msg) {
    if (ctx.channel().isWritable()) {
        ctx.writeAndFlush(msg);
    } else {
        // 背压触发：暂停读取
        ctx.channel().config().setAutoRead(false);
        logger.warn("背压触发，暂停读取");

        // 或者丢弃消息
        ReferenceCountUtil.release(msg);
        logger.warn("背压触发，丢弃消息");
    }
}

@Override
public void channelWritabilityChanged(ChannelHandlerContext ctx) {
    if (ctx.channel().isWritable()) {
        // 背压释放：恢复读取
        ctx.channel().config().setAutoRead(true);
        logger.info("背压释放，恢复读取");
    }
}
```

**方式 2：配置水位线（High/Low Water Mark）**
```java
// ✅ 配置：设置高低水位线
ServerBootstrap b = new ServerBootstrap();
b.group(bossGroup, workerGroup)
    .channel(NioServerSocketChannel.class)
    .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK,
        new WriteBufferWaterMark(
            32 * 1024,    // Low Water Mark:  32 KB
            64 * 1024))   // High Water Mark: 64 KB
    .childHandler(...);

// 工作原理：
// - 缓冲区 > 64 KB → isWritable() = false（触发背压）
// - 缓冲区 < 32 KB → isWritable() = true（释放背压）
```

**方式 3：使用 FlowControlHandler（推荐）**
```java
// ✅ 最佳：使用 FlowControlHandler
pipeline.addLast("flowControl", new FlowControlHandler(
    100,    // 最大并发 100
    1000,   // QPS 限制 1000
    FlowControlHandler.RejectStrategy.RESPONSE
));

// FlowControlHandler 自动处理：
// 1. 并发控制（Semaphore）
// 2. QPS 限流（滑动窗口）
// 3. 背压集成（isWritable）
```

### 背压策略对比

| 策略 | 实现 | 适用场景 | 优点 | 缺点 |
|------|------|----------|------|------|
| **等待** | `setAutoRead(false)` | 生产者/消费者速率可预测 | 不丢数据 | 可能阻塞 |
| **丢弃** | `ReferenceCountUtil.release()` | 日志、监控等可丢失 | 性能高 | 丢失数据 |
| **降级** | 返回 503 响应 | HTTP API、流量控制 | 用户感知 | 需客户端配合 |
| **排队** | `LinkedBlockingQueue` | 需缓冲的场景 | 削峰填谷 | 内存占用 |

### 最佳实践

1. **总是检查 isWritable()**: 写入前必须检查可写状态
2. **配置合理的水位线**: 根据业务场景调整 High/Low Water Mark
3. **实现 channelWritabilityChanged**: 监听可写状态变化
4. **使用 FlowControlHandler**: 生产环境推荐使用可复用组件
5. **监控和告警**: 监控 ChannelOutboundBuffer 大小，设置告警

### 水位线配置建议

| 场景 | Low Water Mark | High Water Mark | 说明 |
|------|----------------|-----------------|------|
| 低延迟 API | 8 KB | 32 KB | 快速响应 |
| 普通 Web 服务 | 32 KB | 64 KB | 默认配置 |
| 大文件传输 | 128 KB | 512 KB | 提高吞吐量 |
| 流式传输 | 256 KB | 1 MB | 削峰填谷 |

### 代码位置

- 背压演示：`lab-07-netty/src/main/java/nan/tech/lab07/backpressure/BackpressureDemo.java`
- FlowControlHandler：`lab-07-netty/src/main/java/nan/tech/lab07/backpressure/FlowControlHandler.java`
- 压力测试：`lab-07-netty/src/main/java/nan/tech/lab07/backpressure/StressTestClient.java`

### 参考资源

- Netty in Action, Chapter 8: Backpressure
- [Netty WriteBufferWaterMark](https://netty.io/4.1/api/io/netty/channel/WriteBufferWaterMark.html)
- Lab-07 前置文档：`docs/prerequisites/BACKPRESSURE_STRATEGY.md`

---

## 待补充的常见坑

以下是后续 Lab 预期会遇到的常见坑：

### Lab-01: 线程基础
- `Thread.start()` vs `Thread.run()` 的错误使用
- 共享状态未同步导致的数据竞争
- 线程命名规范不当导致调试困难

### Lab-03: 线程池
- ThreadPoolExecutor 参数配置错误
- 拒绝策略导致的任务丢失
- 线程池不关闭导致内存泄漏

### Lab-05: 异步编程
- CompletableFuture 异常被吞掉
- 异步链路中的线程切换问题
- 死锁（get() 在事件循环线程中调用）

### Lab-07: Netty 高性能网络编程 ✅ 已完成
- ✅ PITFALL-011: ByteBuf 内存泄漏
- ✅ PITFALL-012: EventLoop 线程阻塞
- ✅ PITFALL-013: 共享 Handler 线程不安全
- ✅ PITFALL-014: 背压未处理导致 OOM

---

**最后更新**: 2025-10-19
**贡献者**: AI Assistant
**版本**: 1.1

