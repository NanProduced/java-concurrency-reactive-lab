package nan.tech.lab10.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Phase 3: 调度器性能对比测试 (SchedulersPerformanceComparisonTest)
 *
 * 学习目标：
 * - 收集 4 种调度器的性能基准数据
 * - 理解调度器性能的量化指标
 * - 掌握性能测试的方法
 *
 * 难度: ⭐⭐⭐ (中等)
 * 阅读时间: 15-20 分钟
 *
 * 测试场景:
 * ┌──────────────────────────────────────────────────────────────┐
 * │ 三种工作负载的性能对比                                        │
 * ├──────────────────────────────────────────────────────────────┤
 * │                                                                │
 * │ 工作负载 1: 轻量级 (纯计算, 无阻塞)                           │
 * │   - 元素数: 10,000                                             │
 * │   - 每个元素: 简单计算 (1 + 2 + 3 + ...)                      │
 * │   - 预期: immediate 最快, parallel 次之                       │
 * │                                                                │
 * │ 工作负载 2: 中等 (有少量 IO)                                  │
 * │   - 元素数: 100                                                │
 * │   - 每个元素: 10ms IO 延迟                                     │
 * │   - 预期: boundedElastic 最优, 其他差异不大                   │
 * │                                                                │
 * │ 工作负载 3: 重型 (大量 IO)                                    │
 * │   - 元素数: 50                                                 │
 * │   - 每个元素: 100ms IO 延迟                                    │
 * │   - 预期: boundedElastic + 并发度 > 其他                      │
 * │                                                                │
 * │ 性能指标:                                                    │
 * │   - 耗时 (Duration): 总的执行时间                             │
 * │   - 吞吐量 (Throughput): 元素/毫秒                             │
 * │   - 线程名: 显示使用的线程                                    │
 * │   - 内存: GC 影响 (可选)                                      │
 * └──────────────────────────────────────────────────────────────┘
 */
public class SchedulersPerformanceComparisonTest {
    private static final Logger log = LoggerFactory.getLogger(SchedulersPerformanceComparisonTest.class);

    /**
     * 工作负载 1: 轻量级计算
     * - 10,000 个元素
     * - 每个元素: map + filter + map (3 个操作)
     */
    public static void test1_LightweightComputation() throws InterruptedException {
        log.info("");
        log.info("╔════════════════════════════════════════════════════════════╗");
        log.info("║  工作负载 1: 轻量级计算 (10,000 元素)                       ║");
        log.info("╚════════════════════════════════════════════════════════════╝");
        log.info("");

        int dataSize = 10000;

        // 测试 immediate
        testScheduler("immediate", Schedulers.immediate(), dataSize, false);
        Thread.sleep(500);

        // 测试 single
        testScheduler("single", Schedulers.single(), dataSize, false);
        Thread.sleep(500);

        // 测试 boundedElastic
        testScheduler("boundedElastic", Schedulers.boundedElastic(), dataSize, false);
        Thread.sleep(500);

        // 测试 parallel
        testScheduler("parallel", Schedulers.parallel(), dataSize, false);
        Thread.sleep(500);

        log.info("");
        log.info("📊 工作负载 1 总结:");
        log.info("   场景: 纯计算, 无阻塞");
        log.info("   预期: immediate > parallel > single > boundedElastic");
        log.info("   原因: 线程切换开销");
        log.info("");
    }

    /**
     * 工作负载 2: 中等 IO (10ms 延迟)
     * - 100 个元素
     * - 每个元素: 10ms IO 延迟 + 计算
     */
    public static void test2_MediumIO() throws InterruptedException {
        log.info("");
        log.info("╔════════════════════════════════════════════════════════════╗");
        log.info("║  工作负载 2: 中等 IO (100 元素, 10ms 延迟)                 ║");
        log.info("╚════════════════════════════════════════════════════════════╝");
        log.info("");

        int dataSize = 100;

        // 测试 immediate (无并发)
        testSchedulerWithIO("immediate", Schedulers.immediate(), dataSize, 10, 1);
        Thread.sleep(500);

        // 测试 single (无并发)
        testSchedulerWithIO("single", Schedulers.single(), dataSize, 10, 1);
        Thread.sleep(500);

        // 测试 boundedElastic (有并发)
        testSchedulerWithIO("boundedElastic", Schedulers.boundedElastic(), dataSize, 10, 5);
        Thread.sleep(500);

        // 测试 parallel (有并发)
        testSchedulerWithIO("parallel", Schedulers.parallel(), dataSize, 10, 5);
        Thread.sleep(500);

        log.info("");
        log.info("📊 工作负载 2 总结:");
        log.info("   场景: 中等 IO, 有并发");
        log.info("   预期: boundedElastic (并发5) 最优");
        log.info("   原因: IO延迟 >> 线程切换开销, 并发隐藏延迟");
        log.info("");
    }

    /**
     * 工作负载 3: 重型 IO (100ms 延迟)
     * - 50 个元素
     * - 每个元素: 100ms IO 延迟
     */
    public static void test3_HeavyIO() throws InterruptedException {
        log.info("");
        log.info("╔════════════════════════════════════════════════════════════╗");
        log.info("║  工作负载 3: 重型 IO (50 元素, 100ms 延迟)                 ║");
        log.info("╚════════════════════════════════════════════════════════════╝");
        log.info("");

        int dataSize = 50;

        // 测试 immediate (无并发)
        testSchedulerWithIO("immediate", Schedulers.immediate(), dataSize, 100, 1);
        Thread.sleep(500);

        // 测试 single (无并发)
        testSchedulerWithIO("single", Schedulers.single(), dataSize, 100, 1);
        Thread.sleep(500);

        // 测试 boundedElastic (高并发)
        testSchedulerWithIO("boundedElastic", Schedulers.boundedElastic(), dataSize, 100, 10);
        Thread.sleep(500);

        // 测试 parallel (高并发)
        testSchedulerWithIO("parallel", Schedulers.parallel(), dataSize, 100, 10);
        Thread.sleep(500);

        log.info("");
        log.info("📊 工作负载 3 总结:");
        log.info("   场景: 重型 IO, 高并发");
        log.info("   预期: boundedElastic (并发10) 最优");
        log.info("   原因: 高并发隐藏长延迟, 吞吐量最高");
        log.info("");
    }

    /**
     * 通用测试方法 (无 IO)
     */
    private static void testScheduler(String name, reactor.core.scheduler.Scheduler scheduler,
                                     int size, boolean withIO) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicLong count = new AtomicLong(0);
        long startTime = System.nanoTime();

        Flux.range(1, size)
                .publishOn(scheduler)
                .map(x -> x * 2)
                .filter(x -> x % 2 == 0)
                .map(x -> x + 1)
                .subscribe(
                        item -> count.incrementAndGet(),
                        error -> log.error("❌ Error: {}", error.getMessage()),
                        () -> {
                            long duration = (System.nanoTime() - startTime) / 1_000_000;
                            double throughput = count.get() / (duration > 0 ? duration : 1) * 1000;
                            log.info("  %-15s : %6d 元素, %6dms, %8.0f elem/s",
                                name, count.get(), duration, throughput);
                            latch.countDown();
                        }
                );

        latch.await(30, TimeUnit.SECONDS);
    }

    /**
     * 通用测试方法 (有 IO)
     */
    private static void testSchedulerWithIO(String name, reactor.core.scheduler.Scheduler scheduler,
                                           int size, int ioDelayMs, int parallelism)
            throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicLong count = new AtomicLong(0);
        long startTime = System.nanoTime();

        Flux.range(1, size)
                .subscribeOn(scheduler)
                .flatMap(id -> {
                    // 模拟 IO 操作
                    return Mono.just(id)
                            .delayElement(Duration.ofMillis(ioDelayMs))
                            .map(x -> x * 2);
                }, parallelism)
                .subscribe(
                        item -> count.incrementAndGet(),
                        error -> log.error("❌ Error: {}", error.getMessage()),
                        () -> {
                            long duration = (System.nanoTime() - startTime) / 1_000_000;
                            double throughput = count.get() / (duration > 0 ? duration : 1) * 1000;
                            log.info("  %-15s (p=%d) : %6d 元素, %6dms, %8.0f elem/s",
                                name, parallelism, count.get(), duration, throughput);
                            latch.countDown();
                        }
                );

        latch.await(30, TimeUnit.SECONDS);
    }

    /**
     * 综合对比和分析
     */
    public static void analysis() {
        log.info("");
        log.info("╔════════════════════════════════════════════════════════════╗");
        log.info("║  性能分析总结                                              ║");
        log.info("╚════════════════════════════════════════════════════════════╝");
        log.info("");

        log.info("📋 关键发现:");
        log.info("");
        log.info("1️⃣  轻量级计算 (无阻塞):");
        log.info("    • immediate() 最快 (无线程切换开销)");
        log.info("    • parallel() 次之 (线程切换 + 并行化)");
        log.info("    • single() 较慢 (单线程队列)");
        log.info("    • boundedElastic() 最慢 (线程池竞争)");
        log.info("");

        log.info("2️⃣  中等 IO (10ms 延迟):");
        log.info("    • immediate/single: 顺序执行, 无并发");
        log.info("    • boundedElastic: 并发执行, 吞吐量高 2-5 倍");
        log.info("    • parallel: 也有并发, 但线程管理开销");
        log.info("");

        log.info("3️⃣  重型 IO (100ms 延迟):");
        log.info("    • IO 延迟 >> 线程切换开销");
        log.info("    • 并发度越高, 吞吐量越好");
        log.info("    • boundedElastic (p=10) 最优");
        log.info("");

        log.info("💡 选择指南:");
        log.info("┌─────────────────┬──────────────┬──────────────┐");
        log.info("│  工作特征       │  推荐调度器  │  理由        │");
        log.info("├─────────────────┼──────────────┼──────────────┤");
        log.info("│ 纯计算, 无 IO  │ immediate   │ 最快, 无开销  │");
        log.info("│ 有序性必须     │ single      │ 顺序保证      │");
        log.info("│ 网络/数据库 IO│ boundedElas │ 线程池+并发   │");
        log.info("│ CPU 密集       │ parallel    │ 核数优化      │");
        log.info("│ 混合负载       │ 组合使用    │ 阶段优化      │");
        log.info("└─────────────────┴──────────────┴──────────────┘");
        log.info("");

        log.info("⚡ 性能优化建议:");
        log.info("  1. 减少不必要的线程切换 (用 immediate 替代)");
        log.info("  2. 增加适当的并发度 (IO 场景)");
        log.info("  3. 使用 limitRate() 控制背压");
        log.info("  4. 监控实际的吞吐量, 基于数据决策");
        log.info("  5. 定期进行性能测试和基准对比");
        log.info("");
    }

    /**
     * 主程序
     */
    public static void main(String[] args) throws InterruptedException {
        log.info("");
        log.info("╔════════════════════════════════════════════════════════════╗");
        log.info("║  Lab-10 Phase 3: SchedulersPerformanceComparisonTest      ║");
        log.info("║  4 种调度器的性能对比与基准数据收集                       ║");
        log.info("╚════════════════════════════════════════════════════════════╝");
        log.info("");

        // 工作负载 1: 轻量级计算
        test1_LightweightComputation();

        // 工作负载 2: 中等 IO
        test2_MediumIO();

        // 工作负载 3: 重型 IO
        test3_HeavyIO();

        // 分析总结
        analysis();

        log.info("╔════════════════════════════════════════════════════════════╗");
        log.info("║  ✅ 性能对比测试完成！                                      ║");
        log.info("║  建议: 将这些数据作为生产环境的参考基准                     ║");
        log.info("╚════════════════════════════════════════════════════════════╝");
        log.info("");
    }
}
