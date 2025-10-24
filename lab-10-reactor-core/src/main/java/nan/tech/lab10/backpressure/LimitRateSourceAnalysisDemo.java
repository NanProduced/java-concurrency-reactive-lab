package nan.tech.lab10.backpressure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Phase 2: LimitRate 源码分析与参数调优演示 (LimitRateSourceAnalysisDemo)
 *
 * 学习目标：
 * - 理解 limitRate() 的内部工作原理（请求批处理、预取机制）
 * - 掌握 limitRate(prefetch, lowTide) 两个参数的含义
 * - 根据网络延迟选择合适的参数
 * - 对比不同参数组合的性能差异
 *
 * 难度: ⭐⭐⭐⭐⭐ (非常高)
 * 阅读时间: 25-35 分钟
 *
 * 核心概念:
 * ┌─────────────────────────────────────────────────────────────┐
 * │ limitRate(prefetch, lowTide) 的工作流程                      │
 * ├─────────────────────────────────────────────────────────────┤
 * │ 1. 初始请求: 消费者请求 prefetch 个元素                      │
 * │ 2. 批处理: 一次性请求 prefetch 个元素，减少请求次数          │
 * │ 3. lowTide 阈值: 当待消费元素 <= lowTide 时，补充请求        │
 * │ 4. 请求补充: 一次请求 (prefetch - lowTide) 个新元素          │
 * │ 5. 循环: 重复步骤 3-4，直到流结束                            │
 * │                                                               │
 * │ 请求流程示意:                                                │
 * │   时间轴: ────────────────────────────────────────>          │
 * │   请求: [prefetch] ----消费----> [refill] ----消费----> ...  │
 * │   缓冲: [========] → [.......] → [========] → [.......]      │
 * │         (满)         (低)         (补充)      (低)           │
 * └─────────────────────────────────────────────────────────────┘
 *
 * limitRate 参数选择决策树:
 * ┌─ 是否关心请求数量？
 * │  ├─ 是 → 调整 prefetch (默认 32, 可选 64/128/256)
 * │  └─ 否 → 保持默认
 * │
 * └─ 网络延迟情况？
 *    ├─ 低延迟 (< 1ms) → prefetch 小（16-32）, lowTide 高（prefetch * 0.75）
 *    ├─ 中延迟 (1-10ms) → prefetch 中（32-64）, lowTide 中（prefetch * 0.5）
 *    └─ 高延迟 (> 10ms) → prefetch 大（128-256）, lowTide 低（prefetch * 0.25）
 */
public class LimitRateSourceAnalysisDemo {
    private static final Logger log = LoggerFactory.getLogger(LimitRateSourceAnalysisDemo.class);

    /**
     * Demo 1: limitRate 基础工作原理 - 请求批处理
     *
     * 演示目标:
     *   - 展示 limitRate() 如何减少上游请求次数
     *   - 观察请求的批处理特性
     *   - 理解批处理对背压的影响
     *
     * 代码示例:
     */
    public static void demo1_LimitRateBasic() {
        log.info("=== Demo 1: LimitRate Basic Request Batching ===");
        log.info("概念: limitRate() 将多个元素请求合并成一次请求");
        log.info("");

        // 性能监控
        AtomicLong requestCount = new AtomicLong(0);
        AtomicLong consumedCount = new AtomicLong(0);

        try {
            Flux.range(1, 1000)
                    .doOnRequest(n -> {
                        requestCount.addAndGet(n);
                        if (requestCount.get() <= 200) {
                            log.info("  📤 Request {} elements (total requested: {})", n, requestCount.get());
                        }
                    })
                    .limitRate(32)  // 每次请求 32 个元素
                    .subscribe(
                            item -> {
                                consumedCount.incrementAndGet();
                                if (item % 100 == 0) {
                                    log.info("  📥 Consumed: {}", item);
                                }
                            },
                            error -> log.error("❌ Error: {}", error.getMessage()),
                            () -> {
                                log.info("✅ Stream completed");
                                log.info("统计: 总消费数={}, 总请求数={}, 请求合并比例={}",
                                    consumedCount.get(), requestCount.get(),
                                    String.format("%.2f", (double) requestCount.get() / consumedCount.get()));
                                log.info("  说明: 1000 个元素，limitRate(32) 约需 31 次请求");
                                log.info("  对比: 无 limitRate 需要 1000 次单元素请求");
                            }
                    );

            log.info("");
        } catch (Exception e) {
            log.error("❌ Demo 1 failed: {}", e.getMessage());
        }
    }

    /**
     * Demo 2: prefetch 参数的影响 - 初始请求大小
     *
     * 演示目标:
     *   - 展示不同 prefetch 值对请求次数的影响
     *   - 理解 prefetch 的内存开销
     *   - 学习如何选择合适的 prefetch 值
     *
     * prefetch 含义:
     *   - limitRate(n) 相当于 limitRate(n, n/2)
     *   - 第一次请求 prefetch 个元素
     *   - 当缓冲降到 lowTide 以下时，补充请求 (prefetch - lowTide) 个
     */
    public static void demo2_PrefetchImpact() {
        log.info("=== Demo 2: Prefetch Parameter Impact ===");
        log.info("概念: prefetch 控制每次请求的元素数量");
        log.info("");

        int dataSize = 10000;
        int[] prefetchValues = {8, 32, 128, 256};

        for (int prefetch : prefetchValues) {
            log.info("📊 Testing prefetch={}:", prefetch);
            AtomicLong requestCount = new AtomicLong(0);
            long startTime = System.nanoTime();

            Flux.range(1, dataSize)
                    .doOnRequest(n -> requestCount.incrementAndGet())
                    .limitRate(prefetch)
                    .subscribe(
                            item -> {
                                // 模拟快速消费
                            },
                            error -> {},
                            () -> {
                                long duration = (System.nanoTime() - startTime) / 1_000_000;
                                double requestPerElement = (double) requestCount.get() / dataSize;
                                log.info("  ✅ 完成: 请求次数={}, 元素数={}, 请求比={:.4f}, 耗时={}ms",
                                    requestCount.get(), dataSize, requestPerElement, duration);
                            }
                    );
        }

        log.info("");
        log.info("小结:");
        log.info("  - prefetch 越大: 请求次数越少，但内存占用越多");
        log.info("  - prefetch 越小: 请求次数越多，但内存占用越少");
        log.info("  - 权衡: 通常 32-64 是较好的平衡点");
        log.info("");
    }

    /**
     * Demo 3: lowTide 参数的影响 - 补充请求阈值
     *
     * 演示目标:
     *   - 展示 lowTide 对请求时机的影响
     *   - 理解缓冲策略与网络延迟的关系
     *   - 学习高延迟场景下的参数调优
     *
     * lowTide 含义:
     *   - 当缓冲中元素数 <= lowTide 时，触发补充请求
     *   - 补充请求大小 = prefetch - lowTide
     *   - 低 lowTide: 缓冲用尽再补，请求频繁
     *   - 高 lowTide: 提前补充，请求较少，内存占用大
     */
    public static void demo3_LowTideImpact() {
        log.info("=== Demo 3: LowTide Parameter Impact ===");
        log.info("概念: lowTide 控制何时进行补充请求");
        log.info("");

        int dataSize = 5000;
        int prefetch = 64;
        int[] lowTideValues = {8, 16, 32, 48};  // 64 的 1/8, 1/4, 1/2, 3/4

        for (int lowTide : lowTideValues) {
            log.info("📊 Testing prefetch={}, lowTide={}:", prefetch, lowTide);
            AtomicLong requestCount = new AtomicLong(0);
            AtomicLong refillCount = new AtomicLong(0);
            long startTime = System.nanoTime();

            Flux.range(1, dataSize)
                    .doOnRequest(n -> {
                        requestCount.incrementAndGet();
                        if (requestCount.get() > 1) {
                            refillCount.incrementAndGet();
                        }
                    })
                    .limitRate(prefetch, lowTide)
                    .subscribe(
                            item -> {
                                // 模拟消费
                            },
                            error -> {},
                            () -> {
                                long duration = (System.nanoTime() - startTime) / 1_000_000;
                                log.info("  ✅ 完成: 初始请求=1, 补充请求={}, 总请求={}, 耗时={}ms",
                                    refillCount.get(), requestCount.get(), duration);
                            }
                    );
        }

        log.info("");
        log.info("小结:");
        log.info("  - lowTide 低: 补充请求频繁，适合高速网络");
        log.info("  - lowTide 高: 补充请求少，适合高延迟网络");
        log.info("  - 建议: lowTide = prefetch / 2（平衡点）");
        log.info("");
    }

    /**
     * Demo 4: 网络延迟场景下的参数调优
     *
     * 演示目标:
     *   - 展示高延迟场景下的背压问题
     *   - 演示如何通过调整 limitRate 缓解延迟
     *   - 对比最优参数与次优参数的性能差异
     *
     * 场景描述:
     *   - 模拟数据库查询（每个查询 10ms 延迟）
     *   - 消费速度与网络延迟不匹配
     *   - 需要合理调整 limitRate 参数
     */
    public static void demo4_NetworkLatencyScenario() {
        log.info("=== Demo 4: Network Latency Scenario Optimization ===");
        log.info("场景: 模拟高延迟数据库查询");
        log.info("");

        // 场景参数
        int queryLatencyMs = 10;  // 数据库查询延迟
        int totalQueries = 500;

        // 参数组合对比
        int[][] parameterSets = {
            {32, 16},    // 默认参数
            {64, 32},    // 预取更多
            {128, 32},   // 大幅预取
            {256, 64}    // 最激进预取
        };

        for (int[] params : parameterSets) {
            int prefetch = params[0];
            int lowTide = params[1];

            log.info("📊 配置: prefetch={}, lowTide={}", prefetch, lowTide);

            long startTime = System.currentTimeMillis();
            AtomicLong completedCount = new AtomicLong(0);
            AtomicLong totalLatency = new AtomicLong(0);

            Flux.range(1, totalQueries)
                    .flatMap(id -> {
                        // 模拟数据库查询 (延迟 10ms)
                        return Mono.just(id)
                                .delayElement(Duration.ofMillis(queryLatencyMs))
                                .doOnNext(x -> totalLatency.addAndGet(queryLatencyMs));
                    }, prefetch)  // 重要: 限制并发查询数
                    .limitRate(prefetch, lowTide)
                    .subscribe(
                            item -> completedCount.incrementAndGet(),
                            error -> log.error("❌ Error: {}", error.getMessage()),
                            () -> {
                                long duration = System.currentTimeMillis() - startTime;
                                double throughput = (double) totalQueries / duration * 1000;
                                log.info("  ✅ 完成: 耗时={}ms, 吞吐量={:.2f} req/s",
                                    duration, throughput);
                            }
                    );
        }

        log.info("");
        log.info("分析:");
        log.info("  - 高延迟场景: 需要更大的 prefetch 保持管道满载");
        log.info("  - prefetch 过小: 频繁等待、吞吐量低");
        log.info("  - prefetch 过大: 内存占用高、GC 压力大");
        log.info("");
    }

    /**
     * Demo 5: 源码分析 - LimitRate 的内部原理
     *
     * 演示目标:
     *   - 理解 Reactor 源码中的 limitRate 实现
     *   - 学习如何判断何时补充请求
     *   - 了解背压的实现细节
     *
     * 核心算法（伪代码）:
     * ┌─────────────────────────────────────────────────────┐
     * │ class LimitRate {                                     │
     * │   long requested = 0;          // 已请求的元素数      │
     * │   long consumed = 0;           // 已消费的元素数      │
     * │                                                       │
     * │   void onSubscribe() {                                │
     * │     requested = prefetch;      // 初始请求 prefetch   │
     * │     upstream.request(prefetch);                       │
     * │   }                                                   │
     * │                                                       │
     * │   void onNext(T value) {                              │
     * │     downstream.onNext(value);  // 转发给下游          │
     * │     consumed++;                                       │
     * │                                                       │
     * │     if (consumed >= requested - lowTide) {            │
     * │       // 缓冲即将用尽，补充请求                        │
     * │       long refill = prefetch - lowTide;               │
     * │       upstream.request(refill);                       │
     * │       requested += refill;                            │
     * │     }                                                 │
     * │   }                                                   │
     * │ }                                                     │
     * └─────────────────────────────────────────────────────┘
     */
    public static void demo5_SourceCodeAnalysis() {
        log.info("=== Demo 5: Source Code Analysis & Parameter Recommendation ===");
        log.info("深度分析: limitRate() 的内部实现");
        log.info("");

        log.info("1️⃣  LimitRate 工作流程:");
        log.info("  ┌─ 初始订阅");
        log.info("  │  └─ 请求 prefetch 个元素");
        log.info("  │");
        log.info("  ├─ 接收元素");
        log.info("  │  └─ 传递给下游消费者");
        log.info("  │");
        log.info("  └─ 监控缓冲");
        log.info("     ├─ if (缓冲 > lowTide) → 继续等待");
        log.info("     └─ if (缓冲 <= lowTide) → 补充请求 (prefetch - lowTide) 个");
        log.info("");

        log.info("2️⃣  参数调优决策树:");
        log.info("  场景 1: 快速内存操作 (< 1ms)");
        log.info("    ├─ prefetch: 16 ~ 32");
        log.info("    ├─ lowTide: prefetch * 0.75");
        log.info("    └─ 理由: 减少请求次数, 内存占用不高");
        log.info("");

        log.info("  场景 2: 本地数据库 (1-5ms)");
        log.info("    ├─ prefetch: 32 ~ 64");
        log.info("    ├─ lowTide: prefetch / 2");
        log.info("    └─ 理由: 平衡请求频率与内存占用");
        log.info("");

        log.info("  场景 3: 远程网络 (> 10ms)");
        log.info("    ├─ prefetch: 128 ~ 256");
        log.info("    ├─ lowTide: prefetch / 4");
        log.info("    └─ 理由: 大缓冲充分利用网络带宽");
        log.info("");

        log.info("3️⃣  性能指标对标:");
        demonstratePerformanceMetrics();
        log.info("");

        log.info("4️⃣  常见误区:");
        log.info("  ❌ 误区 1: prefetch 越大越好");
        log.info("    └─ 后果: 内存占用爆炸, GC 压力大");
        log.info("");
        log.info("  ❌ 误区 2: 忽视 lowTide 参数");
        log.info("    └─ 后果: 补充请求时机不当, 背压失效");
        log.info("");
        log.info("  ❌ 误区 3: 同一参数用于所有场景");
        log.info("    └─ 后果: 高延迟场景性能差, 低延迟场景资源浪费");
        log.info("");

        log.info("5️⃣  调优步骤:");
        log.info("  1. 度量实际网络延迟 (使用 System.nanoTime)");
        log.info("  2. 根据延迟选择初始 prefetch");
        log.info("  3. 设置 lowTide = prefetch / 2");
        log.info("  4. 运行性能测试 (关键指标: 吞吐量、内存、GC)");
        log.info("  5. 逐步调整 prefetch ±50%, 找到最优点");
        log.info("  6. 固定参数, 添加文档说明");
        log.info("");
    }

    /**
     * 演示性能指标对标
     */
    private static void demonstratePerformanceMetrics() {
        log.info("📊 性能指标演示 (基于 1000 元素):");

        Instant start = Instant.now();
        long[] requestCounts = new long[4];
        String[] configs = {"limitRate(8)", "limitRate(32)", "limitRate(128)", "limitRate(256)"};
        int[] prefetchValues = {8, 32, 128, 256};

        for (int i = 0; i < prefetchValues.length; i++) {
            final int idx = i;
            final int prefetch = prefetchValues[i];
            AtomicLong reqCount = new AtomicLong(0);

            Flux.range(1, 1000)
                    .doOnRequest(n -> reqCount.incrementAndGet())
                    .limitRate(prefetch)
                    .subscribe(item -> {});

            requestCounts[i] = reqCount.get();
        }

        // 输出对标数据
        for (int i = 0; i < configs.length; i++) {
            double requestRatio = 1000.0 / requestCounts[i];
            log.info("  {}  → 请求次数={}, 压缩比={:.2f}x",
                configs[i], requestCounts[i], requestRatio);
        }
    }

    /**
     * 主程序
     */
    public static void main(String[] args) {
        log.info("");
        log.info("╔════════════════════════════════════════════════════════════╗");
        log.info("║  Lab-10 Phase 2: LimitRateSourceAnalysisDemo               ║");
        log.info("║  深入理解 limitRate() 的工作原理和参数调优                  ║");
        log.info("╚════════════════════════════════════════════════════════════╝");
        log.info("");

        // Demo 1: 基础工作原理
        demo1_LimitRateBasic();
        sleepBetweenDemos();

        // Demo 2: prefetch 参数影响
        demo2_PrefetchImpact();
        sleepBetweenDemos();

        // Demo 3: lowTide 参数影响
        demo3_LowTideImpact();
        sleepBetweenDemos();

        // Demo 4: 网络延迟场景
        demo4_NetworkLatencyScenario();
        sleepBetweenDemos();

        // Demo 5: 源码分析
        demo5_SourceCodeAnalysis();

        log.info("╔════════════════════════════════════════════════════════════╗");
        log.info("║  ✅ 所有演示完成！                                          ║");
        log.info("║  下一步: 性能对标测试 (性能数据收集与分析)                   ║");
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
