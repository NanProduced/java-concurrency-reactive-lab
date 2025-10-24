package nan.tech.lab10.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Phase 3: 调度器对比演示 (SchedulersComparisonDemo)
 *
 * 学习目标：
 * - 理解 4 种调度器的工作原理
 * - 掌握各调度器的性能特征
 * - 根据场景选择合适的调度器
 *
 * 难度: ⭐⭐⭐⭐ (高)
 * 阅读时间: 25-35 分钟
 *
 * 核心概念:
 * ┌──────────────────────────────────────────────────────────┐
 * │ 4 种调度器的工作模式对比                                  │
 * ├──────────────────────────────────────────────────────────┤
 * │ 1. immediate()      - 无线程切换，立即执行                │
 * │ 2. single()         - 单线程顺序执行                      │
 * │ 3. boundedElastic() - 线程池执行（适合 I/O）             │
 * │ 4. parallel()       - 固定核数线程（适合 CPU 密集）       │
 * │                                                            │
 * │ 执行流程：                                                │
 * │  immediate():      没有队列 → 直接执行                    │
 * │  single():        [队列] → 单个线程（顺序）              │
 * │  boundedElastic(): [队列] → 线程池（并行）               │
 * │  parallel():      无队列 → 核数线程（最优 CPU）           │
 * └──────────────────────────────────────────────────────────┘
 */
public class SchedulersComparisonDemo {
    private static final Logger log = LoggerFactory.getLogger(SchedulersComparisonDemo.class);

    /**
     * Demo 1: immediate() - 立即调度器
     *
     * 特点:
     * - 在当前线程立即执行
     * - 无线程创建开销
     * - 无线程切换开销
     * - 不能并行化
     */
    public static void demo1_ImmediateScheduler() {
        log.info("=== Demo 1: Schedulers.immediate() ===");
        log.info("概念: 在当前线程立即执行，无线程切换");
        log.info("");

        AtomicLong executedCount = new AtomicLong(0);
        long startTime = System.nanoTime();

        Flux.range(1, 100)
                .publishOn(Schedulers.immediate())
                .doOnNext(item -> {
                    String threadName = Thread.currentThread().getName();
                    if (item <= 3) {
                        log.info("  执行在: {}", threadName);
                    }
                    executedCount.incrementAndGet();
                })
                .subscribe(
                        item -> {},
                        error -> log.error("❌ Error: {}", error.getMessage()),
                        () -> {
                            long duration = (System.nanoTime() - startTime) / 1_000_000;
                            log.info("✅ 完成: 100 个元素, 耗时: {}ms", duration);
                            log.info("说明: 所有执行都在同一线程（main）");
                        }
                );

        log.info("");
    }

    /**
     * Demo 2: single() - 单线程调度器
     *
     * 特点:
     * - 使用一个固定线程顺序执行
     * - 保证顺序性
     * - 适合事务操作
     * - 不能并行化
     */
    public static void demo2_SingleScheduler() {
        log.info("=== Demo 2: Schedulers.single() ===");
        log.info("概念: 单线程顺序执行所有任务");
        log.info("");

        AtomicLong executedCount = new AtomicLong(0);
        long startTime = System.nanoTime();

        Flux.range(1, 100)
                .publishOn(Schedulers.single())
                .doOnNext(item -> {
                    String threadName = Thread.currentThread().getName();
                    if (item <= 3) {
                        log.info("  执行在: {}", threadName);
                    }
                    executedCount.incrementAndGet();
                })
                .subscribe(
                        item -> {},
                        error -> log.error("❌ Error: {}", error.getMessage()),
                        () -> {
                            long duration = (System.nanoTime() - startTime) / 1_000_000;
                            log.info("✅ 完成: 100 个元素, 耗时: {}ms", duration);
                            log.info("说明: 所有执行都在同一线程（single-1）");
                        }
                );

        log.info("");
    }

    /**
     * Demo 3: boundedElastic() - 有界弹性调度器
     *
     * 特点:
     * - 线程池执行（最多 CPU核数*10 个线程）
     * - 可以并行化
     * - 适合 I/O 密集操作
     * - 线程复用（60s 空闲回收）
     */
    public static void demo3_BoundedElasticScheduler() {
        log.info("=== Demo 3: Schedulers.boundedElastic() ===");
        log.info("概念: 线程池并行执行，适合 I/O 密集");
        log.info("");

        AtomicLong executedCount = new AtomicLong(0);
        long startTime = System.nanoTime();

        Flux.range(1, 100)
                .publishOn(Schedulers.boundedElastic())
                .doOnNext(item -> {
                    String threadName = Thread.currentThread().getName();
                    if (item <= 5) {
                        log.info("  执行在: {}", threadName);
                    }
                    executedCount.incrementAndGet();
                })
                .subscribe(
                        item -> {},
                        error -> log.error("❌ Error: {}", error.getMessage()),
                        () -> {
                            long duration = (System.nanoTime() - startTime) / 1_000_000;
                            log.info("✅ 完成: 100 个元素, 耗时: {}ms", duration);
                            log.info("说明: 执行分散到多个线程（boundedElastic-1/2/3...）");
                            log.info("特点: 吞吐量高，支持并行");
                        }
                );

        log.info("");
    }

    /**
     * Demo 4: parallel() - 并行调度器
     *
     * 特点:
     * - 固定线程数 = CPU 核数
     * - 最优化 CPU 利用率
     * - 适合 CPU 密集操作
     * - 无队列，任务直接分配
     */
    public static void demo4_ParallelScheduler() {
        log.info("=== Demo 4: Schedulers.parallel() ===");
        log.info("概念: 固定核数线程，适合 CPU 密集");
        log.info("");

        AtomicLong executedCount = new AtomicLong(0);
        long startTime = System.nanoTime();

        Flux.range(1, 100)
                .publishOn(Schedulers.parallel())
                .doOnNext(item -> {
                    String threadName = Thread.currentThread().getName();
                    if (item <= 5) {
                        log.info("  执行在: {}", threadName);
                    }
                    executedCount.incrementAndGet();
                })
                .subscribe(
                        item -> {},
                        error -> log.error("❌ Error: {}", error.getMessage()),
                        () -> {
                            long duration = (System.nanoTime() - startTime) / 1_000_000;
                            int cpuCount = Runtime.getRuntime().availableProcessors();
                            log.info("✅ 完成: 100 个元素, 耗时: {}ms", duration);
                            log.info("说明: 使用 {} 个线程（CPU 核数）", cpuCount);
                            log.info("特点: CPU 利用率最高，上下文切换最少");
                        }
                );

        log.info("");
    }

    /**
     * Demo 5: 四种调度器的对标
     *
     * 对比维度:
     * - 执行线程数
     * - 耗时
     * - 线程名称
     * - 适用场景
     */
    public static void demo5_SchedulersComparison() {
        log.info("=== Demo 5: 四种调度器对标 ===");
        log.info("对比不同调度器的性能特征");
        log.info("");

        String[] schedulerNames = {"immediate", "single", "boundedElastic", "parallel"};
        int[] dataSizes = {1000};

        for (String schedulerName : schedulerNames) {
            for (int dataSize : dataSizes) {
                log.info("📊 测试: {} (数据量: {})", schedulerName, dataSize);

                AtomicLong count = new AtomicLong(0);
                long startTime = System.nanoTime();

                if ("immediate".equals(schedulerName)) {
                    testWithScheduler(dataSize, Schedulers.immediate(), count, startTime, schedulerName);
                } else if ("single".equals(schedulerName)) {
                    testWithScheduler(dataSize, Schedulers.single(), count, startTime, schedulerName);
                } else if ("boundedElastic".equals(schedulerName)) {
                    testWithScheduler(dataSize, Schedulers.boundedElastic(), count, startTime, schedulerName);
                } else if ("parallel".equals(schedulerName)) {
                    testWithScheduler(dataSize, Schedulers.parallel(), count, startTime, schedulerName);
                }
            }
        }

        log.info("");
        log.info("性能总结:");
        log.info("  immediate():      最快（无开销），但无并行");
        log.info("  single():         顺序执行，保证顺序");
        log.info("  boundedElastic(): 适合 I/O 密集");
        log.info("  parallel():       适合 CPU 密集");
        log.info("");
    }

    /**
     * 通用测试方法
     */
    private static void testWithScheduler(int dataSize, reactor.core.scheduler.Scheduler scheduler,
                                         AtomicLong count, long startTime, String schedulerName) {
        Flux.range(1, dataSize)
                .publishOn(scheduler)
                .subscribe(
                        item -> count.incrementAndGet(),
                        error -> log.error("❌ Error: {}", error.getMessage()),
                        () -> {
                            long duration = (System.nanoTime() - startTime) / 1_000_000;
                            double throughput = (double) count.get() / duration * 1000;
                            log.info("  ✅ 完成: {} 个元素, 耗时: {}ms, 吞吐量: {:.0f} elem/s",
                                count.get(), duration, throughput);
                        }
                );
    }

    /**
     * 主程序：依次运行所有演示
     */
    public static void main(String[] args) {
        log.info("");
        log.info("╔════════════════════════════════════════════════════════════╗");
        log.info("║  Lab-10 Phase 3: SchedulersComparisonDemo                  ║");
        log.info("║  对比 4 种调度器的工作原理与性能特征                        ║");
        log.info("╚════════════════════════════════════════════════════════════╝");
        log.info("");

        // Demo 1: immediate
        demo1_ImmediateScheduler();
        sleepBetweenDemos();

        // Demo 2: single
        demo2_SingleScheduler();
        sleepBetweenDemos();

        // Demo 3: boundedElastic
        demo3_BoundedElasticScheduler();
        sleepBetweenDemos();

        // Demo 4: parallel
        demo4_ParallelScheduler();
        sleepBetweenDemos();

        // Demo 5: 对标
        demo5_SchedulersComparison();

        log.info("╔════════════════════════════════════════════════════════════╗");
        log.info("║  ✅ 所有演示完成！                                          ║");
        log.info("║  下一步: publishOn vs subscribeOn 对比演示                   ║");
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
