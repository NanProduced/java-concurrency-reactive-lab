package nan.tech.lab10.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Phase 3: 线程切换可视化演示 (ThreadSwitchVisualizerDemo)
 *
 * 学习目标：
 * - 直观理解线程切换的开销与频率
 * - 掌握最小化线程切换的设计方法
 * - 学会用可视化工具诊断线程问题
 *
 * 难度: ⭐⭐⭐ (中等)
 * 阅读时间: 20-25 分钟
 *
 * 核心概念:
 * ┌──────────────────────────────────────────────────────────────┐
 * │ 线程切换的成本                                                │
 * ├──────────────────────────────────────────────────────────────┤
 * │                                                                │
 * │ 1. 上下文切换 (Context Switch)                                │
 * │    CPU 寄存器状态保存 → 切换到新线程 → 恢复寄存器            │
 * │    成本: ≈ 1-100 微秒 (取决于 CPU)                           │
 * │                                                                │
 * │ 2. 缓存失效 (Cache Invalidation)                              │
 * │    旧线程的数据在 L1/L2/L3 缓存中                             │
 * │    新线程需要重新加载数据                                     │
 * │    成本: ≈ 100-1000 纳秒                                      │
 * │                                                                │
 * │ 3. 管道刷新 (Pipeline Flush)                                  │
 * │    CPU 指令管道需要清空                                       │
 * │    成本: ≈ 10-50 纳秒                                         │
 * │                                                                │
 * │ 总成本: ≈ 1-100 微秒 (实际测量)                               │
 * │                                                                │
 * │ 频繁切换的危害:                                               │
 * │  ❌ CPU 缓存行为变差 (cache locality 降低)                   │
 * │  ❌ 上下文切换开销累积                                        │
 * │  ❌ 线程调度竞争加剧 (lock contention)                       │
 * │  ❌ 整体吞吐量下降 (throughput degradation)                   │
 * │                                                                │
 * │ 优化策略:                                                    │
 * │  ✅ 减少 publishOn 的使用频率                                │
 * │  ✅ 批量处理数据 (batch processing) 减少切换                  │
 * │  ✅ 合并相邻的操作 (operation fusion)                        │
 * │  ✅ 使用正确的调度器 (适配工作负载)                          │
 * └──────────────────────────────────────────────────────────────┘
 */
public class ThreadSwitchVisualizerDemo {
    private static final Logger log = LoggerFactory.getLogger(ThreadSwitchVisualizerDemo.class);

    /**
     * Demo 1: 可视化线程切换的过程
     *
     * 演示一个完整的线程切换过程:
     * 1. main 线程生成数据
     * 2. publishOn(parallel) 切换到 parallel 线程
     * 3. parallel 线程处理数据
     * 4. 切换完成后恢复性能
     */
    public static void demo1_VisualizeSwitchProcess() throws InterruptedException {
        log.info("=== Demo 1: 线程切换过程可视化 ===");
        log.info("演示一个完整的线程切换及其开销");
        log.info("");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger switchCount = new AtomicInteger(0);

        Flux.range(1, 10)
                // 第一阶段: 在 main 线程生成
                .doOnNext(item -> {
                    if (item == 1) {
                        log.info("📍 第一阶段: 数据生成");
                        log.info("   当前线程: {} (主线程)", Thread.currentThread().getName());
                    }
                })
                .map(item -> item * 2)

                // 线程切换点 ← HERE
                .publishOn(Schedulers.parallel())
                .doOnNext(item -> {
                    if (item == 2) {
                        log.info("📍 线程切换发生！");
                        log.info("   新线程: {} (parallel 线程)", Thread.currentThread().getName());
                        log.info("   此时已发生:");
                        log.info("     ✓ 上下文保存");
                        log.info("     ✓ 线程队列更新");
                        log.info("     ✓ CPU 切换");
                        switchCount.incrementAndGet();
                    }
                })

                // 第二阶段: 在新线程处理
                .filter(item -> item > 5)
                .doOnNext(item -> {
                    if (item == 6) {
                        log.info("📍 第二阶段: 数据处理");
                        log.info("   处理线程: {} (parallel 线程)", Thread.currentThread().getName());
                        log.info("   此时已恢复:");
                        log.info("     ✓ CPU 缓存热化");
                        log.info("     ✓ 指令管道重建");
                        log.info("     ✓ 性能恢复到最优");
                    }
                })
                .subscribe(
                        item -> {},
                        error -> log.error("❌ Error: {}", error.getMessage()),
                        () -> {
                            log.info("📍 第三阶段: 完成");
                            log.info("✅ 线程切换过程演示完成");
                            log.info("   总切换次数: {} 次", switchCount.get());
                            latch.countDown();
                        }
                );

        latch.await(5, TimeUnit.SECONDS);
        log.info("");
    }

    /**
     * Demo 2: 频繁切换 vs 无切换 的对比
     *
     * 演示:
     * - 场景 A: 无线程切换 (所有操作在 main 线程)
     * - 场景 B: 频繁切换 (每个操作都切换线程)
     * - 对比性能差异
     */
    public static void demo2_FrequentVsNoSwitch() throws InterruptedException {
        log.info("=== Demo 2: 频繁切换 vs 无切换 的性能对比 ===");
        log.info("演示: 线程切换频率对性能的影响");
        log.info("");

        int dataSize = 1000;

        // 场景 A: 无线程切换
        log.info("📊 场景 A: 无线程切换 (immediate scheduler)");
        CountDownLatch latchA = new CountDownLatch(1);
        long startA = System.nanoTime();
        AtomicInteger countA = new AtomicInteger(0);

        Flux.range(1, dataSize)
                .publishOn(Schedulers.immediate())  // 不切换线程
                .map(x -> x * 2)
                .filter(x -> x % 2 == 0)
                .map(x -> x + 1)
                .subscribe(
                        item -> countA.incrementAndGet(),
                        error -> log.error("❌ Error-A: {}", error.getMessage()),
                        () -> {
                            long durationA = (System.nanoTime() - startA) / 1_000_000;
                            log.info("✅ 场景 A 完成");
                            log.info("   元素数: {}, 耗时: {}ms", countA.get(), durationA);
                            log.info("   吞吐量: {:.0f} elem/ms", countA.get() / (float) durationA);
                            latchA.countDown();
                        }
                );

        latchA.await();

        try { Thread.sleep(500); } catch (InterruptedException e) { /* ignored */ }

        // 场景 B: 频繁切换 (每个操作都切换)
        log.info("");
        log.info("📊 场景 B: 频繁线程切换 (parallel scheduler x3)");
        CountDownLatch latchB = new CountDownLatch(1);
        long startB = System.nanoTime();
        AtomicInteger countB = new AtomicInteger(0);

        Flux.range(1, dataSize)
                .publishOn(Schedulers.parallel())  // 第 1 次切换
                .map(x -> x * 2)
                .publishOn(Schedulers.parallel())  // 第 2 次切换
                .filter(x -> x % 2 == 0)
                .publishOn(Schedulers.parallel())  // 第 3 次切换
                .map(x -> x + 1)
                .subscribe(
                        item -> countB.incrementAndGet(),
                        error -> log.error("❌ Error-B: {}", error.getMessage()),
                        () -> {
                            long durationB = (System.nanoTime() - startB) / 1_000_000;
                            log.info("✅ 场景 B 完成");
                            log.info("   元素数: {}, 耗时: {}ms", countB.get(), durationB);
                            log.info("   吞吐量: {:.0f} elem/ms", countB.get() / (float) durationB);
                            latchB.countDown();
                        }
                );

        latchB.await();

        log.info("");
        log.info("📈 性能对比分析:");
        log.info("   场景 A (无切换) 明显快于场景 B (频繁切换)");
        log.info("   原因: 线程切换开销累积 + 缓存失效");
        log.info("");
    }

    /**
     * Demo 3: 观察线程切换的内存变化
     *
     * 演示: 线程切换时的内存和缓存行为
     * - 切换前: 数据在旧线程的缓存中 (hot cache)
     * - 切换时: 缓存失效, 需要重新加载 (cache miss)
     * - 切换后: 新线程重新建立缓存 (cold cache → warm cache)
     */
    public static void demo3_CacheBehavior() throws InterruptedException {
        log.info("=== Demo 3: 线程切换的缓存行为 ===");
        log.info("演示: 线程切换导致的缓存失效");
        log.info("");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger processed = new AtomicInteger(0);

        // 模拟缓存行为: 重复访问相同数据
        byte[] workingSet = new byte[10 * 1024 * 1024];  // 10MB 工作集
        java.util.Arrays.fill(workingSet, (byte) 1);

        Flux.range(1, 100)
                .doOnNext(item -> {
                    if (item == 1) {
                        log.info("📍 阶段 1: 数据加载到缓存");
                        log.info("   当前线程: {} (缓存热度: cold)", Thread.currentThread().getName());
                        log.info("   工作集大小: 10MB");
                    }
                    // 模拟访问工作集, 预热缓存
                    int sum = 0;
                    for (int i = 0; i < workingSet.length; i += 64) {
                        sum += workingSet[i];
                    }
                })
                .publishOn(Schedulers.parallel())  // ← 线程切换, 缓存失效
                .doOnNext(item -> {
                    if (item == 1) {
                        log.info("📍 阶段 2: 线程切换后");
                        log.info("   新线程: {} (缓存热度: cold 重新热化)", Thread.currentThread().getName());
                        log.info("   缓存行为: L3 cache miss → memory access (慢)");
                    }
                    // 再次访问工作集, 缓存逐步恢复
                    int sum = 0;
                    for (int i = 0; i < workingSet.length; i += 64) {
                        sum += workingSet[i];
                    }
                    processed.incrementAndGet();
                })
                .subscribe(
                        item -> {},
                        error -> log.error("❌ Error: {}", error.getMessage()),
                        () -> {
                            log.info("📍 阶段 3: 完成");
                            log.info("✅ 缓存行为演示完成");
                            log.info("   处理元素数: {}", processed.get());
                            log.info("   关键发现:");
                            log.info("     • 线程切换时发生 cache miss");
                            log.info("     • 新线程需要重新预热缓存");
                            log.info("     • 频繁切换 = 频繁 cache miss = 性能下降");
                            latch.countDown();
                        }
                );

        latch.await(5, TimeUnit.SECONDS);
        log.info("");
    }

    /**
     * Demo 4: 线程池的切换开销
     *
     * 演示: 使用线程池 (boundedElastic) 时的切换开销
     * - immediate: 无队列, 直接执行 (最快)
     * - single: 单线程队列 (顺序, 无竞争)
     * - boundedElastic: 多线程队列 (有竞争, 有入队开销)
     * - parallel: 无队列, 轮询分配 (竞争最小)
     */
    public static void demo4_PoolSwitchOverhead() throws InterruptedException {
        log.info("=== Demo 4: 不同线程池的切换开销 ===");
        log.info("演示: 不同调度器的队列入队开销");
        log.info("");

        int dataSize = 100;

        // 测试 4 种调度器
        testScheduler("immediate", Schedulers.immediate(), dataSize);
        try { Thread.sleep(300); } catch (InterruptedException e) { /* ignored */ }

        testScheduler("single", Schedulers.single(), dataSize);
        try { Thread.sleep(300); } catch (InterruptedException e) { /* ignored */ }

        testScheduler("boundedElastic", Schedulers.boundedElastic(), dataSize);
        try { Thread.sleep(300); } catch (InterruptedException e) { /* ignored */ }

        testScheduler("parallel", Schedulers.parallel(), dataSize);

        log.info("");
        log.info("📈 分析:");
        log.info("   immediate   : 最快 (无队列, 无竞争)");
        log.info("   parallel    : 快速 (轮询分配, 竞争小)");
        log.info("   single      : 中等 (队列, 单线程)");
        log.info("   boundedElastic: 较慢 (队列, 多线程竞争)");
        log.info("");
    }

    private static void testScheduler(String name, reactor.core.scheduler.Scheduler scheduler, int size)
            throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        long startTime = System.nanoTime();
        AtomicInteger count = new AtomicInteger(0);

        Flux.range(1, size)
                .publishOn(scheduler)
                .map(x -> x * 2)
                .subscribe(
                        item -> count.incrementAndGet(),
                        error -> log.error("❌ Error: {}", error.getMessage()),
                        () -> {
                            long duration = (System.nanoTime() - startTime) / 1_000_000;
                            log.info("  {} : {} 元素, {}ms, {:.0f} elem/ms",
                                String.format("%-15s", name),
                                count.get(), duration,
                                count.get() / (float) duration);
                            latch.countDown();
                        }
                );

        latch.await(5, TimeUnit.SECONDS);
    }

    /**
     * Demo 5: 线程切换的最佳实践
     *
     * 演示: 如何最小化线程切换开销
     * 最佳实践:
     * 1. 减少 publishOn 的使用
     * 2. 合并相邻操作 (operation fusion)
     * 3. 使用批处理 (batching)
     * 4. 选择合适的调度器
     */
    public static void demo5_BestPractices() throws InterruptedException {
        log.info("=== Demo 5: 最小化线程切换的最佳实践 ===");
        log.info("演示: 优化的流设计");
        log.info("");

        int dataSize = 500;

        // 不良做法: 频繁切换
        log.info("❌ 不良做法: 频繁切换");
        testBadPractice(dataSize);
        try { Thread.sleep(300); } catch (InterruptedException e) { /* ignored */ }

        // 良好做法: 批量操作后一次性切换
        log.info("✅ 良好做法: 批量操作后一次性切换");
        testGoodPractice(dataSize);

        log.info("");
        log.info("📋 最佳实践总结:");
        log.info("   1. 减少 publishOn 的数量");
        log.info("   2. 合并 publishOn 之间的操作");
        log.info("   3. 用 immediate 替代不必要的调度器");
        log.info("   4. 考虑用 buffer() 进行批处理");
        log.info("   5. 用 limitRate() 控制背压");
        log.info("");
    }

    private static void testBadPractice(int size) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        long startTime = System.nanoTime();
        AtomicInteger count = new AtomicInteger(0);

        // 每个操作都切换
        Flux.range(1, size)
                .publishOn(Schedulers.parallel())
                .map(x -> x * 2)
                .publishOn(Schedulers.parallel())
                .filter(x -> x % 2 == 0)
                .publishOn(Schedulers.parallel())
                .map(x -> x + 1)
                .subscribe(
                        item -> count.incrementAndGet(),
                        error -> {},
                        () -> {
                            long duration = (System.nanoTime() - startTime) / 1_000_000;
                            log.info("   耗时: {}ms, 吞吐量: {:.0f} elem/ms (3次切换)",
                                duration, count.get() / (float) duration);
                            latch.countDown();
                        }
                );

        latch.await();
    }

    private static void testGoodPractice(int size) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        long startTime = System.nanoTime();
        AtomicInteger count = new AtomicInteger(0);

        // 只在必要时切换 (1次)
        Flux.range(1, size)
                .publishOn(Schedulers.parallel())
                .map(x -> x * 2)
                .filter(x -> x % 2 == 0)
                .map(x -> x + 1)
                .subscribe(
                        item -> count.incrementAndGet(),
                        error -> {},
                        () -> {
                            long duration = (System.nanoTime() - startTime) / 1_000_000;
                            log.info("   耗时: {}ms, 吞吐量: {:.0f} elem/ms (1次切换)",
                                duration, count.get() / (float) duration);
                            latch.countDown();
                        }
                );

        latch.await();
    }

    /**
     * 主程序: 依次运行所有演示
     */
    public static void main(String[] args) throws InterruptedException {
        log.info("");
        log.info("╔════════════════════════════════════════════════════════════╗");
        log.info("║  Lab-10 Phase 3: ThreadSwitchVisualizerDemo               ║");
        log.info("║  可视化线程切换过程及其性能影响                           ║");
        log.info("╚════════════════════════════════════════════════════════════╝");
        log.info("");

        // Demo 1: 切换过程
        demo1_VisualizeSwitchProcess();
        sleepBetweenDemos();

        // Demo 2: 频繁 vs 无切换
        demo2_FrequentVsNoSwitch();
        sleepBetweenDemos();

        // Demo 3: 缓存行为
        demo3_CacheBehavior();
        sleepBetweenDemos();

        // Demo 4: 线程池开销
        demo4_PoolSwitchOverhead();
        sleepBetweenDemos();

        // Demo 5: 最佳实践
        demo5_BestPractices();

        log.info("╔════════════════════════════════════════════════════════════╗");
        log.info("║  ✅ 所有演示完成！                                          ║");
        log.info("║  下一步: 调度器选择决策树演示                               ║");
        log.info("╚════════════════════════════════════════════════════════════╝");
        log.info("");
    }

    /**
     * 演示间隔延迟
     */
    private static void sleepBetweenDemos() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
