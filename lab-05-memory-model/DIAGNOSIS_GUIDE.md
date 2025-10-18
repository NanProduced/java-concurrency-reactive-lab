# JMM 可见性问题诊断指南

> **目标**：为生产环境提供系统化的可见性问题诊断方法和修复方案
> **适用场景**：多线程并发、内存可见性、对象发布、happens-before 规则
> **难度等级**：⭐⭐⭐⭐ (需要理解 JMM 和 CPU 缓存)

---

## 1. 可见性问题的典型症状

### 1.1 程序表现

| 症状 | 描述 | 严重程度 |
|------|------|---------|
| 🔴 **无限循环** | 线程持续检查标志位，但永远看不到修改 | 严重 |
| 🟡 **偶尔挂起** | 程序在低负载时正常，高负载时偶尔卡死 | 中等 |
| 🟡 **数据不一致** | 读取到的字段值不符合业务逻辑 | 中等 |
| 🟢 **调试后消失** | 添加日志或断点后问题消失 | 轻微但难诊断 |

### 1.2 环境特征

**容易触发的环境**：
- ✅ 多核 CPU（4+ 核）
- ✅ 高并发负载（TPS > 1000）
- ✅ 生产环境（JIT 编译优化）
- ✅ 不同 CPU 架构（ARM、x86）

**不易触发的环境**：
- ❌ 单核 CPU 或低负载
- ❌ 开发环境（解释执行）
- ❌ 添加日志后（偶然引入内存屏障）

---

## 2. 诊断流程（6 步法）

### 步骤 1: 识别共享变量

**检查清单**：
- [ ] 是否有多个线程访问同一个变量？
- [ ] 变量是否有 `volatile`、`synchronized` 或 `final` 修饰？
- [ ] 是否使用线程安全容器（如 `ConcurrentHashMap`）？

**工具**：
```bash
# 搜索可疑的共享变量
grep -rn "private.*boolean\|private.*int" src/ | grep -v "volatile\|final"
```

**危险模式**：
```java
// ❌ 危险：无保护的共享变量
class Worker {
    private boolean stopFlag = false;  // 多线程访问，无 volatile

    public void stop() {
        stopFlag = true;  // 写线程
    }

    public void run() {
        while (!stopFlag) {  // 读线程，可能永远看不到修改
            // ...
        }
    }
}
```

---

### 步骤 2: 检查 Happens-Before 规则

**核心问题**：写操作和读操作之间是否存在 happens-before 关系？

**Happens-Before 规则速查表**：

| 规则 | 说明 | 示例 |
|------|------|------|
| **程序顺序规则** | 单线程内，前一个操作 hb 后一个操作 | `x = 1; y = x;` |
| **volatile 规则** | volatile 写 hb volatile 读 | `volatile flag; flag = true; if (flag) {...}` |
| **锁规则** | 解锁 hb 后续的加锁 | `synchronized (obj) {...}` |
| **传递性** | A hb B, B hb C => A hb C | volatile + 锁 + final |
| **线程启动规则** | Thread.start() hb 线程内的所有操作 | `thread.start();` |
| **线程终止规则** | 线程内所有操作 hb Thread.join() 返回 | `thread.join();` |
| **中断规则** | interrupt() hb 检测到中断 | `thread.interrupt();` |
| **final 规则** | final 字段赋值 hb 构造函数结束 | `final int x = 42;` |

**诊断方法**：
```java
// 检查写线程和读线程之间的 happens-before 链

// ❌ 无 happens-before 关系
Thread A: data = 42;      Thread B: if (ready) { use(data); }
          ready = true;

// ✅ 有 happens-before 关系（volatile）
Thread A: data = 42;      Thread B: if (ready) { use(data); }
          ready = true;   // volatile 写      // volatile 读

// ✅ 有 happens-before 关系（synchronized）
Thread A: synchronized(lock) {   Thread B: synchronized(lock) {
            data = 42;                         use(data);
            ready = true;                      if (ready) {...}
          }                                  }
```

---

### 步骤 3: 排查对象发布问题

**不安全发布的典型场景**：

#### 场景 A: 构造函数中的 this 逸出
```java
// ❌ 危险：this 在构造完成前逸出
class EventListener {
    private final int threshold;

    public EventListener(EventSource source) {
        source.registerListener(this);  // ❌ this 逸出
        this.threshold = 100;  // 其他线程可能在此之前就访问 this
    }
}

// ✅ 修复：使用工厂方法
class EventListener {
    private final int threshold;

    private EventListener() {
        this.threshold = 100;
    }

    public static EventListener create(EventSource source) {
        EventListener listener = new EventListener();
        source.registerListener(listener);  // ✅ 构造完成后再发布
        return listener;
    }
}
```

#### 场景 B: 不安全的双重检查锁定
```java
// ❌ 危险：无 volatile 的 DCL
class Singleton {
    private static Singleton instance;  // ❌ 缺少 volatile

    public static Singleton getInstance() {
        if (instance == null) {
            synchronized (Singleton.class) {
                if (instance == null) {
                    instance = new Singleton();  // ❌ 可能重排序
                }
            }
        }
        return instance;
    }
}

// ✅ 修复方案 1：添加 volatile
private static volatile Singleton instance;

// ✅ 修复方案 2：静态内部类（推荐）
class Singleton {
    private static class Holder {
        static final Singleton INSTANCE = new Singleton();
    }
    public static Singleton getInstance() {
        return Holder.INSTANCE;
    }
}
```

#### 场景 C: 非 final 字段的不安全发布
```java
// ❌ 危险：非 final 字段可能被其他线程看到默认值
class Config {
    private String host;  // ❌ 非 final
    private int port;     // ❌ 非 final

    public Config(String host, int port) {
        this.host = host;
        this.port = port;
    }
}

// 发布
private Config config;  // ❌ 无 volatile
public void init() {
    config = new Config("localhost", 8080);  // ❌ 不安全发布
}

// ✅ 修复方案 1：使用 final 字段
class Config {
    private final String host;  // ✅ final 保证可见性
    private final int port;
}

// ✅ 修复方案 2：使用 volatile 发布
private volatile Config config;  // ✅ volatile 保证安全发布
```

---

### 步骤 4: 使用诊断工具

#### 4.1 JConsole / VisualVM

**查看线程状态**：
```bash
# 连接到运行中的 JVM
jconsole <pid>

# 查看线程堆栈
# Threads → 选择可疑线程 → Dump Thread Stack
```

**识别死循环**：
- 线程状态：`RUNNABLE`
- CPU 使用率：100%
- 堆栈不变化（多次 dump 相同）

#### 4.2 jstack 线程堆栈

```bash
# 导出线程堆栈
jstack <pid> > thread_dump.txt

# 查找可疑线程
grep -A 20 "RUNNABLE" thread_dump.txt

# 多次导出对比（间隔 3 秒）
jstack <pid> > dump1.txt
sleep 3
jstack <pid> > dump2.txt
diff dump1.txt dump2.txt  # 堆栈无变化说明死循环
```

#### 4.3 JFR (Java Flight Recorder)

```bash
# 启动 JFR 记录
jcmd <pid> JFR.start duration=60s filename=recording.jfr

# 分析记录
jfr print --events jdk.ThreadPark recording.jfr
```

**分析指标**：
- 线程阻塞时间
- 锁竞争情况
- 内存分配模式

#### 4.4 添加诊断日志

```java
// 添加详细日志（注意：日志可能偶然修复问题）
public void run() {
    while (!stopFlag) {
        // 添加日志可能引入内存屏障
        if (System.currentTimeMillis() % 1000 == 0) {
            log.debug("Still running, stopFlag={}", stopFlag);
        }
        // ...
    }
}
```

---

### 步骤 5: 验证修复方案

**修复方案对比表**：

| 方案 | 可见性 | 原子性 | 有序性 | 性能 | 适用场景 |
|------|-------|-------|-------|------|---------|
| **volatile** | ✅ | ❌ | ✅ | 快 | 状态标志、单次赋值 |
| **synchronized** | ✅ | ✅ | ✅ | 中等 | 复合操作、多字段保护 |
| **Atomic 类** | ✅ | ✅ | ✅ | 快 | 计数器、状态切换 |
| **Lock** | ✅ | ✅ | ✅ | 中等 | 高级锁（公平锁、条件变量） |
| **final 字段** | ✅ | N/A | ✅ | 最快 | 不可变对象 |

**验证步骤**：
1. **单元测试**：编写并发测试用例
2. **压力测试**：高并发场景下运行 1 小时+
3. **代码审查**：确认修复符合 JMM 规则
4. **性能测试**：确认修复未引入性能问题

---

### 步骤 6: 预防性检查

**代码审查清单**：

```markdown
## 可见性问题预防检查清单

### 共享变量检查
- [ ] 所有多线程共享的字段是否有同步保护？
- [ ] 是否误用了普通字段而非 volatile/final？
- [ ] 是否有 check-then-act 竞态条件？

### 对象发布检查
- [ ] 构造函数中是否有 this 逸出（启动线程、注册监听器）？
- [ ] 是否使用了不安全的双重检查锁定（无 volatile）？
- [ ] 是否直接发布了可变对象（无 volatile/synchronized）？

### Happens-Before 检查
- [ ] 写操作和读操作之间是否有 happens-before 关系？
- [ ] 是否依赖了不保证的操作顺序？
- [ ] 是否误用了 volatile（期望原子性但 volatile 不保证）？

### 不可变对象检查
- [ ] 是否优先使用了不可变对象（final 字段）？
- [ ] final 字段引用的对象是否也是不可变的？
- [ ] 是否避免了不可变对象的 this 逸出？
```

---

## 3. 常见错误模式与修复

### 模式 1: 状态标志位（最常见）

#### ❌ 错误代码
```java
class Worker implements Runnable {
    private boolean running = true;  // ❌ 无 volatile

    public void run() {
        while (running) {  // 可能永远循环
            doWork();
        }
    }

    public void stop() {
        running = false;  // 可能永远不可见
    }
}
```

#### ✅ 修复方案
```java
class Worker implements Runnable {
    private volatile boolean running = true;  // ✅ volatile

    public void run() {
        while (running) {
            doWork();
        }
    }

    public void stop() {
        running = false;  // volatile 写，立即可见
    }
}
```

**原理**：volatile 保证写操作对所有线程立即可见。

---

### 模式 2: 懒加载单例

#### ❌ 错误代码
```java
class Singleton {
    private static Singleton instance;  // ❌ 无 volatile

    public static Singleton getInstance() {
        if (instance == null) {
            synchronized (Singleton.class) {
                if (instance == null) {
                    instance = new Singleton();  // ❌ 可能重排序
                }
            }
        }
        return instance;
    }
}
```

#### ✅ 修复方案（推荐：静态内部类）
```java
class Singleton {
    private Singleton() {}

    private static class Holder {
        static final Singleton INSTANCE = new Singleton();
    }

    public static Singleton getInstance() {
        return Holder.INSTANCE;  // ✅ JVM 保证线程安全
    }
}
```

**原理**：JVM 类加载机制保证线程安全和懒加载。

---

### 模式 3: 配置对象发布

#### ❌ 错误代码
```java
class ConfigManager {
    private Config config;  // ❌ 无 volatile

    public void reload() {
        Config newConfig = loadFromFile();
        config = newConfig;  // ❌ 不安全发布
    }

    public Config getConfig() {
        return config;  // 可能读到未初始化的字段
    }
}
```

#### ✅ 修复方案
```java
class ConfigManager {
    private volatile Config config;  // ✅ volatile 保证安全发布

    public void reload() {
        Config newConfig = loadFromFile();
        config = newConfig;  // ✅ volatile 写
    }

    public Config getConfig() {
        return config;  // ✅ volatile 读
    }
}

// Config 应该是不可变对象
class Config {
    private final String host;  // ✅ final
    private final int port;     // ✅ final

    public Config(String host, int port) {
        this.host = host;
        this.port = port;
    }
}
```

**原理**：volatile + 不可变对象 = 安全发布。

---

### 模式 4: 初始化标志

#### ❌ 错误代码
```java
class Service {
    private boolean initialized = false;  // ❌ 无 volatile
    private String resource;

    public void init() {
        resource = loadResource();
        initialized = true;  // ❌ 其他线程可能先看到 initialized=true
    }

    public void use() {
        if (initialized) {
            process(resource);  // ❌ resource 可能为 null
        }
    }
}
```

#### ✅ 修复方案
```java
class Service {
    private volatile boolean initialized = false;  // ✅ volatile
    private String resource;  // 由 volatile 的 happens-before 保护

    public void init() {
        resource = loadResource();
        initialized = true;  // ✅ volatile 写
    }

    public void use() {
        if (initialized) {  // ✅ volatile 读
            process(resource);  // 保证 resource 已赋值
        }
    }
}
```

**原理**：volatile 写之前的所有操作对 volatile 读可见。

---

## 4. 性能影响与权衡

### 4.1 各种同步机制的性能对比

| 机制 | 读性能 | 写性能 | 内存开销 | 适用场景 |
|------|-------|-------|---------|---------|
| **无同步** | 最快 | 最快 | 无 | ❌ 不适用 |
| **volatile** | 快 | 中等 | 无 | 状态标志 |
| **synchronized** | 中等 | 慢 | 锁对象 | 复合操作 |
| **ReentrantLock** | 中等 | 慢 | Lock 对象 | 高级锁 |
| **Atomic** | 快 | 快 | 无 | 计数器 |
| **final** | 最快 | N/A | 无 | 不可变对象 |

### 4.2 性能优化建议

1. **优先使用不可变对象**：零开销，最安全
2. **volatile 用于读多写少**：写操作有缓存刷新开销
3. **Atomic 用于高并发计数**：无锁，性能优异
4. **synchronized 用于临界区保护**：简单可靠
5. **避免过度同步**：只保护必要的代码

---

## 5. 实战案例

### 案例 1: 线程池关闭标志

**问题**：`shutdown()` 后线程池仍在运行

**根因**：
```java
class MyThreadPool {
    private boolean shutdown = false;  // ❌ 无 volatile

    public void shutdown() {
        shutdown = true;
    }

    private class Worker implements Runnable {
        public void run() {
            while (!shutdown) {  // 可能永远循环
                // ...
            }
        }
    }
}
```

**修复**：
```java
private volatile boolean shutdown = false;  // ✅ volatile
```

**验证**：压测 10000 次关闭操作，0 次失败。

---

### 案例 2: 配置热更新

**问题**：配置更新后部分请求仍使用旧配置

**根因**：
```java
class ConfigService {
    private Map<String, String> config = new HashMap<>();  // ❌ 非线程安全

    public void updateConfig(Map<String, String> newConfig) {
        config = newConfig;  // ❌ 不安全发布
    }
}
```

**修复**：
```java
class ConfigService {
    private volatile Map<String, String> config = new HashMap<>();  // ✅ volatile
    // 或使用 ConcurrentHashMap
}
```

**验证**：配置更新后 1 秒内所有请求使用新配置。

---

## 6. 参考资料

### 官方文档
- [JSR-133: Java Memory Model and Thread Specification](https://jcp.org/en/jsr/detail?id=133)
- [Java Language Specification: Chapter 17 - Threads and Locks](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html)

### 书籍
- *Java Concurrency in Practice* by Brian Goetz (Chapter 3: Sharing Objects)
- *Effective Java* 第 3 版 by Joshua Bloch (Item 78-84)

### 工具
- JConsole: JVM 自带监控工具
- VisualVM: 高级性能分析工具
- JFR: Java Flight Recorder 生产级性能记录
- jstack: 线程堆栈分析

---

## 7. 总结

### 核心要点

1. **可见性问题难以重现**：在开发环境可能正常，生产环境偶发
2. **Happens-Before 是关键**：写操作和读操作必须有 happens-before 关系
3. **volatile 不是万能的**：只保证可见性和有序性，不保证原子性
4. **优先使用不可变对象**：final 字段 + 正确构造 = 零开销的安全发布
5. **避免过度同步**：根据实际需求选择合适的同步机制

### 决策树

```
是否需要线程间共享数据？
├─ 否 → 使用 ThreadLocal 或局部变量
└─ 是 → 数据是否可变？
    ├─ 否 → 使用不可变对象（final 字段）
    └─ 是 → 是否需要原子性？
        ├─ 否 → 是否是单一字段？
        │   ├─ 是 → 使用 volatile
        │   └─ 否 → 使用 synchronized 或 Lock
        └─ 是 → 是否是简单操作（计数器、标志）？
            ├─ 是 → 使用 Atomic 类
            └─ 否 → 使用 synchronized 或 Lock
```

---

**最后更新**: 2025-01-18
**维护者**: Nan
**版本**: 1.0.0
