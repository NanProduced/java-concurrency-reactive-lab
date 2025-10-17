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

