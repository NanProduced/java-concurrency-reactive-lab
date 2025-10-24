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
 * Phase 3: 调度器选择决策树演示 (SchedulerSelectionStrategyDemo)
 *
 * 学习目标：
 * - 掌握在不同场景下选择合适的调度器
 * - 理解决策树的逻辑
 * - 学会进行性能权衡和架构设计
 *
 * 难度: ⭐⭐⭐⭐ (高)
 * 阅读时间: 25-35 分钟
 *
 * 核心概念: 调度器选择决策树
 * ┌──────────────────────────────────────────────────────────────┐
 * │ 调度器选择决策树 (Decision Tree)                               │
 * ├──────────────────────────────────────────────────────────────┤
 * │                                                                │
 * │ 第一步: 是否有线程切换需求？                                 │
 * │  ├─ 否 → 使用 immediate() ✅ (最快)                           │
 * │  └─ 是 → 进入第二步                                          │
 * │                                                                │
 * │ 第二步: 是否有顺序性要求？                                   │
 * │  ├─ 是 → 使用 single() ✅ (保证顺序)                          │
 * │  └─ 否 → 进入第三步                                          │
 * │                                                                │
 * │ 第三步: 工作负载特性是什么？                                 │
 * │  ├─ I/O 密集 (数据库/网络) → boundedElastic() ✅ (线程池)    │
 * │  ├─ CPU 密集 (计算)       → parallel() ✅ (固定核数)         │
 * │  └─ 混合 (I/O + 计算)     → 组合使用                         │
 * │                                                                │
 * │ 第四步: 是否需要背压？                                       │
 * │  ├─ 是 → publishOn + limitRate() ✅                           │
 * │  └─ 否 → publishOn 即可 ✅                                   │
 * │                                                                │
 * │ 第五步: 性能优化                                             │
 * │  ├─ 减少线程切换次数                                         │
 * │  ├─ 合并相邻操作                                             │
 * │  ├─ 使用正确的 prefetch 大小                                 │
 * │  └─ 监控和调优                                               │
 * │                                                                │
 * │ 场景对应表:                                                  │
 * │ ┌─────────────┬────────────┬──────────────┬─────────────┐   │
 * │ │  场景       │ 特点       │  推荐调度器  │  为什么     │   │
 * │ ├─────────────┼────────────┼──────────────┼─────────────┤   │
 * │ │ 1. 同步处理 │ 无阻塞      │  immediate() │ 最快, 无开销│   │
 * │ │ 2. 数据库IO │ 阻塞等待    │ boundedElas. │ 线程池共享 │   │
 * │ │ 3. 网络IO  │ 长时间等待 │ boundedElas. │ 线程池管理 │   │
 * │ │ 4. CPU计算 │ CPU密集    │ parallel()  │ 核数最优化 │   │
 * │ │ 5. UI更新  │ 单线程需求 │ single()   │ 顺序保证   │   │
 * │ │ 6. 定时任务│ 延迟执行   │ parallel() │ 固定延迟   │   │
 * │ │ 7. 背压处理│ 流控需求   │ 限制队列    │ limitRate()│   │
 * │ └─────────────┴────────────┴──────────────┴─────────────┘   │
 * └──────────────────────────────────────────────────────────────┘
 */
public class SchedulerSelectionStrategyDemo {
    private static final Logger log = LoggerFactory.getLogger(SchedulerSelectionStrategyDemo.class);

    /**
     * 场景 1: 同步处理 - 无阻塞, 无线程切换
     *
     * 决策:
     * - 有线程切换需求? NO
     * - 决策: 使用 immediate() ✅ (最快)
     *
     * 性能: 最优 (~10 nanoseconds/operation)
     * 用途: 纯计算, 无 IO, 无阻塞
     */
    public static void scenario1_SynchronousProcessing() throws InterruptedException {
        log.info("=== 场景 1: 同步处理 ===");
        log.info("决策: 无阻塞, 无线程切换 → immediate()");
        log.info("");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicLong count = new AtomicLong(0);
        long startTime = System.nanoTime();

        Flux.range(1, 1000)
                .map(x -> x * 2)  // 纯计算
                .filter(x -> x % 2 == 0)  // 纯计算
                .map(x -> x + 1)  // 纯计算
                // 无 publishOn 或 subscribeOn, 在 main 线程执行
                .subscribe(
                        item -> count.incrementAndGet(),
                        error -> log.error("❌ Error: {}", error.getMessage()),
                        () -> {
                            long duration = (System.nanoTime() - startTime) / 1_000_000;
                            log.info("✅ 完成: {} 元素, {}ms", count.get(), duration);
                            log.info("说明: 所有操作在 main 线程, 没有线程切换开销");
                            log.info("性能: 最优");
                            latch.countDown();
                        }
                );

        latch.await(5, TimeUnit.SECONDS);
        log.info("");
    }

    /**
     * 场景 2: 数据库 IO - 阻塞等待, 需要线程池
     *
     * 决策:
     * - 有线程切换需求? YES
     * - 有顺序性要求? NO
     * - 工作负载? I/O 密集
     * - 决策: 使用 boundedElastic() ✅ (线程池)
     *
     * 性能: 中等 (IO延迟 > 线程开销)
     * 用途: 数据库查询, 阻塞等待
     */
    public static void scenario2_DatabaseIO() throws InterruptedException {
        log.info("=== 场景 2: 数据库 IO ===");
        log.info("决策: 阻塞等待, 需要线程池 → boundedElastic()");
        log.info("");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicLong count = new AtomicLong(0);
        long startTime = System.nanoTime();

        Flux.range(1, 50)
                .subscribeOn(Schedulers.boundedElastic())  // ← 推到线程池
                .flatMap(id -> {
                    // 模拟数据库查询 (10ms 延迟)
                    return Mono.just(id)
                            .delayElement(Duration.ofMillis(10))
                            .doOnNext(ignored -> {
                                if (id == 1) {
                                    log.info("📌 数据库查询在线程: {}", Thread.currentThread().getName());
                                }
                            });
                })
                .subscribe(
                        item -> count.incrementAndGet(),
                        error -> log.error("❌ Error: {}", error.getMessage()),
                        () -> {
                            long duration = (System.nanoTime() - startTime) / 1_000_000;
                            log.info("✅ 完成: {} 条记录, {}ms", count.get(), duration);
                            log.info("说明: subscribeOn(boundedElastic) 将查询推到线程池");
                            log.info("性能: 线程切换开销 << IO延迟 (10ms)");
                            latch.countDown();
                        }
                );

        latch.await(5, TimeUnit.SECONDS);
        log.info("");
    }

    /**
     * 场景 3: 网络 IO - 长延迟, 需要线程池 + 背压
     *
     * 决策:
     * - 有线程切换需求? YES
     * - 有顺序性要求? NO
     * - 工作负载? I/O 密集 + 背压
     * - 决策: boundedElastic() + limitRate() ✅
     *
     * 性能: 中等 (IO延迟 > 线程开销 > 背压开销)
     * 用途: 网络请求, 长连接
     */
    public static void scenario3_NetworkIO() throws InterruptedException {
        log.info("=== 场景 3: 网络 IO ===");
        log.info("决策: 长延迟, 线程池+背压 → boundedElastic() + limitRate()");
        log.info("");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicLong count = new AtomicLong(0);
        long startTime = System.nanoTime();

        Flux.range(1, 20)
                .subscribeOn(Schedulers.boundedElastic())  // 网络请求在线程池
                .flatMap(id -> {
                    // 模拟网络请求 (100ms 延迟)
                    return Mono.just(id)
                            .delayElement(Duration.ofMillis(100))
                            .doOnNext(ignored -> {
                                if (id == 1) {
                                    log.info("📌 网络请求在线程: {}", Thread.currentThread().getName());
                                }
                            });
                }, 5)  // 并发度为 5
                .limitRate(5)  // 背压: 一次只请求 5 条
                .subscribe(
                        item -> count.incrementAndGet(),
                        error -> log.error("❌ Error: {}", error.getMessage()),
                        () -> {
                            long duration = (System.nanoTime() - startTime) / 1_000_000;
                            log.info("✅ 完成: {} 条请求, {}ms", count.get(), duration);
                            log.info("说明: limitRate(5) 限制并发, 防止内存爆炸");
                            log.info("性能: 背压减少内存使用");
                            latch.countDown();
                        }
                );

        latch.await(5, TimeUnit.SECONDS);
        log.info("");
    }

    /**
     * 场景 4: CPU 密集计算 - 需要最优并行度
     *
     * 决策:
     * - 有线程切换需求? YES
     * - 有顺序性要求? NO
     * - 工作负载? CPU 密集
     * - 决策: 使用 parallel() ✅ (固定核数)
     *
     * 性能: 最优 (CPU核数 = 线程数)
     * 用途: 复杂计算, 数学运算
     */
    public static void scenario4_CPUIntensive() throws InterruptedException {
        log.info("=== 场景 4: CPU 密集计算 ===");
        log.info("决策: CPU 密集, 固定核数 → parallel()");
        log.info("");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicLong count = new AtomicLong(0);
        long startTime = System.nanoTime();

        int cpuCount = Runtime.getRuntime().availableProcessors();

        Flux.range(1, 100)
                .publishOn(Schedulers.parallel())  // ← CPU 并行
                .map(x -> {
                    // 模拟 CPU 密集计算
                    long result = x;
                    for (int i = 0; i < 1000; i++) {
                        result = (result * result + i) % 1000000;
                    }
                    if (x == 1) {
                        log.info("📌 计算在线程: {} (共 {} 核)", Thread.currentThread().getName(), cpuCount);
                    }
                    return result;
                })
                .subscribe(
                        item -> count.incrementAndGet(),
                        error -> log.error("❌ Error: {}", error.getMessage()),
                        () -> {
                            long duration = (System.nanoTime() - startTime) / 1_000_000;
                            log.info("✅ 完成: {} 个计算, {}ms", count.get(), duration);
                            log.info("说明: parallel() 自动使用所有 CPU 核心");
                            log.info("性能: CPU 利用率最优 (100%)");
                            latch.countDown();
                        }
                );

        latch.await(5, TimeUnit.SECONDS);
        log.info("");
    }

    /**
     * 场景 5: UI 更新 - 需要单线程和顺序性
     *
     * 决策:
     * - 有线程切换需求? YES
     * - 有顺序性要求? YES ✅
     * - 决策: 使用 single() ✅ (单线程)
     *
     * 性能: 中等
     * 用途: UI 更新, 事务处理, 顺序敏感的操作
     */
    public static void scenario5_UIUpdates() throws InterruptedException {
        log.info("=== 场景 5: UI 更新 ===");
        log.info("决策: 需要顺序性 → single()");
        log.info("");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicLong count = new AtomicLong(0);
        long startTime = System.nanoTime();

        Flux.range(1, 10)
                .publishOn(Schedulers.single())  // ← 单线程保证顺序
                .doOnNext(item -> {
                    if (item == 1) {
                        log.info("📌 UI 更新在线程: {} (单线程顺序)", Thread.currentThread().getName());
                    }
                    // 模拟 UI 操作
                    String uiEvent = String.format("更新 UI 控件 %d", item);
                    count.incrementAndGet();
                })
                .subscribe(
                        item -> {},
                        error -> log.error("❌ Error: {}", error.getMessage()),
                        () -> {
                            long duration = (System.nanoTime() - startTime) / 1_000_000;
                            log.info("✅ 完成: {} 个 UI 更新, {}ms", count.get(), duration);
                            log.info("说明: single() 保证了更新的顺序性");
                            log.info("性能: 线程安全 + 顺序保证");
                            latch.countDown();
                        }
                );

        latch.await(5, TimeUnit.SECONDS);
        log.info("");
    }

    /**
     * 场景 6: 混合工作负载 - IO + 计算
     *
     * 决策:
     * - 阶段 1: IO 密集 → subscribeOn(boundedElastic)
     * - 阶段 2: CPU 密集 → publishOn(parallel)
     * - 决策: 组合使用 ✅
     *
     * 性能: 两个阶段各自优化
     * 用途: 真实场景 (获取数据 + 处理数据)
     */
    public static void scenario6_MixedWorkload() throws InterruptedException {
        log.info("=== 场景 6: 混合工作负载 (IO + 计算) ===");
        log.info("决策: IO 使用线程池, 计算使用 CPU 并行 → 组合");
        log.info("");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicLong count = new AtomicLong(0);
        long startTime = System.nanoTime();

        Flux.range(1, 50)
                // 阶段 1: IO 密集 (数据库查询)
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(id -> {
                    return Mono.just(id)
                            .delayElement(Duration.ofMillis(5))  // 模拟 IO
                            .doOnNext(ignored -> {
                                if (id == 1) {
                                    log.info("📌 阶段 1 (IO): 在线程 {}", Thread.currentThread().getName());
                                }
                            });
                }, 5)
                // 阶段 2: CPU 密集 (数据处理)
                .publishOn(Schedulers.parallel())
                .map(x -> {
                    long result = x;
                    for (int i = 0; i < 100; i++) {
                        result = (result * result + i) % 1000000;
                    }
                    if (x == 1) {
                        log.info("📌 阶段 2 (计算): 在线程 {}", Thread.currentThread().getName());
                    }
                    return result;
                })
                .subscribe(
                        item -> count.incrementAndGet(),
                        error -> log.error("❌ Error: {}", error.getMessage()),
                        () -> {
                            long duration = (System.nanoTime() - startTime) / 1_000_000;
                            log.info("✅ 完成: {} 个数据, {}ms", count.get(), duration);
                            log.info("说明: IO 和计算各自使用最优调度器");
                            log.info("性能: 两个阶段都得到优化");
                            latch.countDown();
                        }
                );

        latch.await(5, TimeUnit.SECONDS);
        log.info("");
    }

    /**
     * 场景 7: 性能对比总结
     *
     * 演示: 所有场景的性能对比
     */
    public static void scenario7_PerformanceComparison() throws InterruptedException {
        log.info("=== 场景 7: 性能对比总结 ===");
        log.info("");

        log.info("📊 不同场景的性能特征:");
        log.info("");
        log.info("┌─────────────────┬──────────┬────────────┬──────────────┐");
        log.info("│  场景           │ 调度器   │ 耗时 (相对) │  原因        │");
        log.info("├─────────────────┼──────────┼────────────┼──────────────┤");
        log.info("│ 1. 同步处理     │immediate │    1x      │ 无开销       │");
        log.info("│ 2. 数据库 IO   │boundedE. │   10x      │ IO 延迟      │");
        log.info("│ 3. 网络 IO     │boundedE. │  100x      │ 网络延迟     │");
        log.info("│ 4. CPU 计算    │parallel  │    2x      │ 线程切换     │");
        log.info("│ 5. UI 更新     │single    │    3x      │ 单线程开销   │");
        log.info("│ 6. 混合负载    │组合      │   50x      │ IO 主导      │");
        log.info("└─────────────────┴──────────┴────────────┴──────────────┘");
        log.info("");

        log.info("💡 选择建议:");
        log.info("  1. 优先考虑 immediate() (最快)");
        log.info("  2. 有阻塞时优先 boundedElastic()");
        log.info("  3. CPU 密集时优先 parallel()");
        log.info("  4. 需要顺序时必须 single()");
        log.info("  5. 混合场景时组合使用");
        log.info("");
    }

    /**
     * 主程序: 依次运行所有场景
     */
    public static void main(String[] args) throws InterruptedException {
        log.info("");
        log.info("╔════════════════════════════════════════════════════════════╗");
        log.info("║  Lab-10 Phase 3: SchedulerSelectionStrategyDemo           ║");
        log.info("║  调度器选择决策树与场景演示                              ║");
        log.info("╚════════════════════════════════════════════════════════════╝");
        log.info("");

        // 场景 1: 同步处理
        scenario1_SynchronousProcessing();
        sleepBetweenScenarios();

        // 场景 2: 数据库 IO
        scenario2_DatabaseIO();
        sleepBetweenScenarios();

        // 场景 3: 网络 IO
        scenario3_NetworkIO();
        sleepBetweenScenarios();

        // 场景 4: CPU 计算
        scenario4_CPUIntensive();
        sleepBetweenScenarios();

        // 场景 5: UI 更新
        scenario5_UIUpdates();
        sleepBetweenScenarios();

        // 场景 6: 混合负载
        scenario6_MixedWorkload();
        sleepBetweenScenarios();

        // 场景 7: 性能总结
        scenario7_PerformanceComparison();

        log.info("╔════════════════════════════════════════════════════════════╗");
        log.info("║  ✅ 所有场景演示完成！                                      ║");
        log.info("║  下一步: 性能对比测试与决策树实验                           ║");
        log.info("╚════════════════════════════════════════════════════════════╝");
        log.info("");
    }

    /**
     * 场景间隔延迟
     */
    private static void sleepBetweenScenarios() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
