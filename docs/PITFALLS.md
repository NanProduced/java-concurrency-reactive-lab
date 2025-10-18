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

### Lab-07: 响应式编程
- 背压处理不当导致的内存溢出
- 订阅未完成导致资源泄漏
- 错误恢复策略不当

---

**最后更新**: 2025-10-17
**贡献者**: AI Assistant
**版本**: 1.0

