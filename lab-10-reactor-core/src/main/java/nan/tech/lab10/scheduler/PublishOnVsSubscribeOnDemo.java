package nan.tech.lab10.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Phase 3: publishOn 与 subscribeOn 对比演示 (PublishOnVsSubscribeOnDemo)
 *
 * 学习目标：
 * - 理解 publishOn() 和 subscribeOn() 的执行时机
 * - 掌握它们对线程切换的影响
 * - 学会在实际场景中做出正确的选择
 *
 * 难度: ⭐⭐⭐⭐⭐ (非常难)
 * 阅读时间: 30-40 分钟
 *
 * 核心概念:
 * ┌──────────────────────────────────────────────────────────────┐
 * │ publishOn() vs subscribeOn() 执行时序对比                    │
 * ├──────────────────────────────────────────────────────────────┤
 * │                                                                │
 * │ publishOn(): 改变下游的执行线程                               │
 * │  subscriber → map → publishOn(A) → filter → subscribe        │
 * │  执行顺序:                                                   │
 * │   1. map 在 subscriber 线程执行                               │
 * │   2. publishOn(A) 将线程切换为 A                             │
 * │   3. filter 在 A 线程执行                                    │
 * │   4. 最后的操作都在 A 线程执行                               │
 * │                                                                │
 * │ subscribeOn(): 改变上游的执行线程                             │
 * │  subscribeOn(B) → source → map → filter → subscribe          │
 * │  执行顺序:                                                   │
 * │   1. subscribeOn(B) 将订阅链推送给 B 线程                    │
 * │   2. source 在 B 线程创建                                   │
 * │   3. map 和 filter 都在 B 线程执行                          │
 * │   4. 但最后 subscribe callback 仍在调用线程                │
 * │                                                                │
 * │ 关键区别:                                                    │
 * │  - publishOn: 影响下游操作 (map/filter/...)                 │
 * │  - subscribeOn: 影响上游操作 (source creation)              │
 * │  - publishOn 可以出现多次 (形成线程链)                       │
 * │  - subscribeOn 只有第一个有效 (后续的被忽略)                 │
 * │                                                                │
 * │ 适用场景:                                                    │
 * │  - publishOn: 某个操作需要在特定线程 (UI/IO/CPU)             │
 * │  - subscribeOn: 整个响应流需要在特定线程                    │
 * └──────────────────────────────────────────────────────────────┘
 */
public class PublishOnVsSubscribeOnDemo {
    private static final Logger log = LoggerFactory.getLogger(PublishOnVsSubscribeOnDemo.class);

    /**
     * Demo 1: publishOn() 的线程切换效果
     *
     * 演示: publishOn() 在流中间改变线程
     * - source 在 main 线程创建数据
     * - map 操作在 main 线程执行
     * - publishOn(parallel) 将线程切换到 parallel
     * - filter 操作在 parallel 线程执行
     */
    public static void demo1_PublishOnEffects() throws InterruptedException {
        log.info("=== Demo 1: publishOn() 的线程切换效果 ===");
        log.info("概念: 在下游操作中改变执行线程");
        log.info("");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicLong count = new AtomicLong(0);

        Flux.range(1, 5)
                .doOnNext(item -> log.info("  [source] 在线程: {} 创建数据: {}",
                    Thread.currentThread().getName(), item))
                .map(item -> {
                    log.info("  [map] 在线程: {} 处理数据: {}",
                        Thread.currentThread().getName(), item);
                    return item * 10;
                })
                .publishOn(Schedulers.parallel())  // ← 线程切换点
                .filter(item -> {
                    log.info("  [filter] 在线程: {} 过滤数据: {}",
                        Thread.currentThread().getName(), item);
                    return item > 20;
                })
                .doOnNext(item -> log.info("  [onNext] 在线程: {} 处理结果: {}",
                    Thread.currentThread().getName(), item))
                .subscribe(
                        item -> count.incrementAndGet(),
                        error -> log.error("❌ Error: {}", error.getMessage()),
                        () -> {
                            log.info("✅ 完成: 处理 {} 个元素", count.get());
                            log.info("说明: publishOn() 只影响下游操作, filter 和 onNext 在 parallel 线程");
                            latch.countDown();
                        }
                );

        latch.await(5, TimeUnit.SECONDS);
        log.info("");
    }

    /**
     * Demo 2: subscribeOn() 的线程切换效果
     *
     * 演示: subscribeOn() 改变整个订阅链的线程
     * - subscribeOn(boundedElastic) 将整个流推送到 boundedElastic 线程
     * - source 在 boundedElastic 线程创建
     * - map 在 boundedElastic 线程执行
     * - filter 在 boundedElastic 线程执行
     * - 但 subscribe callback 仍在调用线程执行
     */
    public static void demo2_SubscribeOnEffects() throws InterruptedException {
        log.info("=== Demo 2: subscribeOn() 的线程切换效果 ===");
        log.info("概念: 将整个订阅链推送到指定线程");
        log.info("");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicLong count = new AtomicLong(0);

        Flux.range(1, 5)
                .subscribeOn(Schedulers.boundedElastic())  // ← 订阅线程切换点
                .doOnNext(item -> log.info("  [source] 在线程: {} 创建数据: {}",
                    Thread.currentThread().getName(), item))
                .map(item -> {
                    log.info("  [map] 在线程: {} 处理数据: {}",
                        Thread.currentThread().getName(), item);
                    return item * 10;
                })
                .filter(item -> {
                    log.info("  [filter] 在线程: {} 过滤数据: {}",
                        Thread.currentThread().getName(), item);
                    return item > 20;
                })
                .subscribe(
                        item -> {
                            count.incrementAndGet();
                            log.info("  [onNext] 在线程: {} 处理结果: {}",
                                Thread.currentThread().getName(), item);
                        },
                        error -> log.error("❌ Error: {}", error.getMessage()),
                        () -> {
                            log.info("✅ 完成: 处理 {} 个元素", count.get());
                            log.info("说明: subscribeOn() 影响整个流, source/map/filter 都在 boundedElastic");
                            latch.countDown();
                        }
                );

        latch.await(5, TimeUnit.SECONDS);
        log.info("");
    }

    /**
     * Demo 3: publishOn() 和 subscribeOn() 结合
     *
     * 演示: 两个操作符的组合效果
     * - subscribeOn(single) 将整个流推送到 single 线程
     * - map 在 single 线程执行
     * - publishOn(parallel) 将线程切换到 parallel
     * - filter 在 parallel 线程执行
     */
    public static void demo3_CombinedEffects() throws InterruptedException {
        log.info("=== Demo 3: publishOn() 和 subscribeOn() 结合 ===");
        log.info("概念: 组合使用实现多个线程的协作");
        log.info("");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicLong count = new AtomicLong(0);

        Flux.range(1, 5)
                .subscribeOn(Schedulers.single())  // ← 第一个线程切换: single
                .doOnNext(item -> log.info("  [source] 在线程: {} 创建数据: {}",
                    Thread.currentThread().getName(), item))
                .map(item -> {
                    log.info("  [map] 在线程: {} 处理数据: {}",
                        Thread.currentThread().getName(), item);
                    return item * 10;
                })
                .publishOn(Schedulers.parallel())  // ← 第二个线程切换: parallel
                .filter(item -> {
                    log.info("  [filter] 在线程: {} 过滤数据: {}",
                        Thread.currentThread().getName(), item);
                    return item > 20;
                })
                .doOnNext(item -> log.info("  [onNext] 在线程: {} 处理结果: {}",
                    Thread.currentThread().getName(), item))
                .subscribe(
                        item -> count.incrementAndGet(),
                        error -> log.error("❌ Error: {}", error.getMessage()),
                        () -> {
                            log.info("✅ 完成: 处理 {} 个元素", count.get());
                            log.info("说明: subscribeOn 决定 source 线程, publishOn 决定下游线程");
                            log.info("      形成两层线程链: source(single) → map(single) → filter(parallel)");
                            latch.countDown();
                        }
                );

        latch.await(5, TimeUnit.SECONDS);
        log.info("");
    }

    /**
     * Demo 4: 多个 publishOn() 形成线程链
     *
     * 演示: publishOn() 可以出现多次, 形成线程切换链
     * - source 在 main 线程
     * - publishOn(boundedElastic) → map 在 boundedElastic
     * - publishOn(parallel) → filter 在 parallel
     * - publishOn(single) → doOnNext 在 single
     */
    public static void demo4_MultiplePublishOn() throws InterruptedException {
        log.info("=== Demo 4: 多个 publishOn() 形成线程链 ===");
        log.info("概念: publishOn() 可以多次出现, 形成链式线程切换");
        log.info("");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicLong count = new AtomicLong(0);

        Flux.range(1, 5)
                .map(item -> {
                    log.info("  [map-1] 在线程: {} 处理数据: {}",
                        Thread.currentThread().getName(), item);
                    return item * 10;
                })
                .publishOn(Schedulers.boundedElastic())  // ← 第一次切换
                .filter(item -> {
                    log.info("  [filter] 在线程: {} 过滤数据: {}",
                        Thread.currentThread().getName(), item);
                    return item > 20;
                })
                .publishOn(Schedulers.parallel())  // ← 第二次切换
                .map(item -> {
                    log.info("  [map-2] 在线程: {} 转换数据: {}",
                        Thread.currentThread().getName(), item);
                    return item + 1;
                })
                .publishOn(Schedulers.single())  // ← 第三次切换
                .doOnNext(item -> {
                    log.info("  [onNext] 在线程: {} 处理结果: {}",
                        Thread.currentThread().getName(), item);
                    count.incrementAndGet();
                })
                .subscribe(
                        item -> {},
                        error -> log.error("❌ Error: {}", error.getMessage()),
                        () -> {
                            log.info("✅ 完成: 处理 {} 个元素", count.get());
                            log.info("说明: 形成三层线程链");
                            log.info("      map-1(main) → publishOn(BE) → filter(BE)");
                            log.info("      → publishOn(parallel) → map-2(parallel)");
                            log.info("      → publishOn(single) → onNext(single)");
                            latch.countDown();
                        }
                );

        latch.await(5, TimeUnit.SECONDS);
        log.info("");
    }

    /**
     * Demo 5: subscribeOn() 只有第一个有效
     *
     * 演示: 即使写了多个 subscribeOn(), 也只有第一个有效
     * - subscribeOn(single) 第一个有效
     * - subscribeOn(parallel) 第二个被忽略
     * - 所有操作都在 single 线程执行
     */
    public static void demo5_MultipleSubscribeOn() throws InterruptedException {
        log.info("=== Demo 5: subscribeOn() 只有第一个有效 ===");
        log.info("概念: 多个 subscribeOn() 时, 只有第一个生效, 其他被忽略");
        log.info("");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicLong count = new AtomicLong(0);

        Flux.range(1, 5)
                .subscribeOn(Schedulers.single())  // ← 第一个 subscribeOn, 有效
                .doOnNext(item -> log.info("  [source] 在线程: {} 创建数据: {}",
                    Thread.currentThread().getName(), item))
                .map(item -> {
                    log.info("  [map] 在线程: {} 处理数据: {}",
                        Thread.currentThread().getName(), item);
                    return item * 10;
                })
                .subscribeOn(Schedulers.parallel())  // ← 第二个 subscribeOn, 被忽略
                .filter(item -> {
                    log.info("  [filter] 在线程: {} 过滤数据: {}",
                        Thread.currentThread().getName(), item);
                    return item > 20;
                })
                .subscribeOn(Schedulers.boundedElastic())  // ← 第三个 subscribeOn, 被忽略
                .subscribe(
                        item -> {
                            count.incrementAndGet();
                            log.info("  [onNext] 在线程: {} 处理结果: {}",
                                Thread.currentThread().getName(), item);
                        },
                        error -> log.error("❌ Error: {}", error.getMessage()),
                        () -> {
                            log.info("✅ 完成: 处理 {} 个元素", count.get());
                            log.info("说明: 只有第一个 subscribeOn(single) 有效");
                            log.info("      其他两个 subscribeOn(parallel/boundedElastic) 被忽略");
                            log.info("      所有操作都在 single 线程执行");
                            latch.countDown();
                        }
                );

        latch.await(5, TimeUnit.SECONDS);
        log.info("");
    }

    /**
     * Demo 6: 决策树 - 何时使用 publishOn 或 subscribeOn
     *
     * 演示场景:
     * 场景 1: 需要用线程池处理 IO 操作
     *   → 用 subscribeOn(boundedElastic) 整个流推给线程池
     *
     * 场景 2: 某个操作需要在特定线程 (如 UI 线程)
     *   → 在该操作前用 publishOn(specific_scheduler)
     *
     * 场景 3: 多个操作不同线程需求
     *   → 用多个 publishOn() 形成链
     *
     * 场景 4: 整个流 IO, 但结果处理需要在 main 线程
     *   → 用 subscribeOn(boundedElastic) + publishOn(Schedulers.immediate())
     */
    public static void demo6_DecisionTree() throws InterruptedException {
        log.info("=== Demo 6: 决策树演示 ===");
        log.info("场景: IO 密集操作后, 结果需要在 main 线程处理");
        log.info("");

        // 模拟场景: subscribeOn 在线程池, publishOn 回到 main 线程
        CountDownLatch latch = new CountDownLatch(1);
        AtomicLong count = new AtomicLong(0);
        String mainThread = Thread.currentThread().getName();

        Flux.range(1, 5)
                .subscribeOn(Schedulers.boundedElastic())  // ← IO 操作在线程池
                .map(item -> {
                    log.info("  [io] 在线程: {} (IO 线程池) 模拟 IO 操作: {}",
                        Thread.currentThread().getName(), item);
                    try { Thread.sleep(10); } catch (InterruptedException e) { /* ignored */ }
                    return item * 10;
                })
                .publishOn(Schedulers.immediate())  // ← 切换回 main 线程
                .doOnNext(item -> {
                    log.info("  [ui] 在线程: {} (应该是 main {}) 处理 UI: {}",
                        Thread.currentThread().getName(), mainThread, item);
                    count.incrementAndGet();
                })
                .subscribe(
                        item -> {},
                        error -> log.error("❌ Error: {}", error.getMessage()),
                        () -> {
                            log.info("✅ 完成: 处理 {} 个元素", count.get());
                            log.info("说明: 完美的模式 - IO 在后台线程, UI 更新在 main");
                            latch.countDown();
                        }
                );

        latch.await(5, TimeUnit.SECONDS);
        log.info("");
    }

    /**
     * 主程序: 依次运行所有演示
     */
    public static void main(String[] args) throws InterruptedException {
        log.info("");
        log.info("╔════════════════════════════════════════════════════════════╗");
        log.info("║  Lab-10 Phase 3: PublishOnVsSubscribeOnDemo               ║");
        log.info("║  对比 publishOn() 和 subscribeOn() 的执行时序              ║");
        log.info("╚════════════════════════════════════════════════════════════╝");
        log.info("");

        // Demo 1: publishOn
        demo1_PublishOnEffects();
        sleepBetweenDemos();

        // Demo 2: subscribeOn
        demo2_SubscribeOnEffects();
        sleepBetweenDemos();

        // Demo 3: 组合
        demo3_CombinedEffects();
        sleepBetweenDemos();

        // Demo 4: 多个 publishOn
        demo4_MultiplePublishOn();
        sleepBetweenDemos();

        // Demo 5: 多个 subscribeOn
        demo5_MultipleSubscribeOn();
        sleepBetweenDemos();

        // Demo 6: 决策树
        demo6_DecisionTree();

        log.info("╔════════════════════════════════════════════════════════════╗");
        log.info("║  ✅ 所有演示完成！                                          ║");
        log.info("║  下一步: 线程切换可视化演示                                 ║");
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
