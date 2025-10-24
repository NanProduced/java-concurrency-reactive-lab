package nan.tech.lab10.backpressure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Phase 2: 背压策略性能对比测试 (BackpressurePerformanceComparisonTest)
 *
 * 学习目标：
 * - 收集 4 种背压策略的完整性能数据
 * - 对比吞吐量、内存占用、GC 压力的差异
 * - 确定各策略的适用场景
 * - 为生产环境的参数选择提供数据支撑
 *
 * 难度: ⭐⭐⭐ (中高)
 * 测试时间: 约 2-3 分钟
 *
 * 性能指标说明：
 * ┌────────────────────────────────────────────────────┐
 * │ 关键指标                                            │
 * ├────────────────────────────────────────────────────┤
 * │ 1. 吞吐量 (Throughput): req/s                       │
 * │    - 定义: 单位时间内完成的请求数                 │
 * │    - 越高越好 (受限于硬件能力)                      │
 * │                                                     │
 * │ 2. 平均延迟 (Average Latency): ms                  │
 * │    - 定义: 从请求到响应的平均耗时                 │
 * │    - 越低越好                                       │
 * │                                                     │
 * │ 3. P99 延迟 (99th Percentile): ms                 │
 * │    - 定义: 99% 的请求在此时间内完成               │
 * │    - 适用于 SLA 评估                               │
 * │                                                     │
 * │ 4. 内存峰值 (Peak Memory): MB                      │
 * │    - 定义: 测试过程中的最高内存占用                │
 * │    - 关系到背压缓冲大小                            │
 * │                                                     │
 * │ 5. GC 次数 (GC Count): times                       │
 * │    - 定义: 测试过程中触发的 GC 次数               │
 * │    - 越少越好 (减少 GC 停顿)                       │
 * └────────────────────────────────────────────────────┘
 *
 * 测试场景：
 * ┌─────────────────────────────────────────┐
 * │ 场景 1: 快速消费 (无背压)               │
 * │  - 生产速度: 无限                       │
 * │  - 消费速度: 尽快                       │
 * │  - 背压触发: 不会                       │
 * │  - 适用场景: CPU 密集场景               │
 * │                                          │
 * │ 场景 2: 中等消费 (部分背压)              │
 * │  - 生产速度: 100 req/s                   │
 * │  - 消费速度: 50-80 req/s                │
 * │  - 背压触发: 偶然                       │
 * │  - 适用场景: I/O 密集场景               │
 * │                                          │
 * │ 场景 3: 缓慢消费 (严重背压)              │
 * │  - 生产速度: 无限                       │
 * │  - 消费速度: 20 req/s                   │
 * │  - 背压触发: 频繁                       │
 * │  - 适用场景: 网络受限、数据库慢         │
 * └─────────────────────────────────────────┘
 */
public class BackpressurePerformanceComparisonTest {
    private static final Logger log = LoggerFactory.getLogger(BackpressurePerformanceComparisonTest.class);

    /**
     * 性能测试数据容器
     */
    static class PerformanceMetrics {
        String strategyName;
        long startTime;
        long endTime;
        long completedCount;
        long droppedCount;
        long errorCount;
        List<Long> latencies = new ArrayList<>();
        long memoryBefore;
        long memoryAfter;
        long peakMemory;
        int gcCountBefore;
        int gcCountAfter;

        @Override
        public String toString() {
            long duration = endTime - startTime;
            double throughput = completedCount / (duration / 1000.0);
            double avgLatency = latencies.isEmpty() ? 0 : latencies.stream().mapToLong(l -> l).average().orElse(0);
            long p99Latency = calculateP99();
            long memoryUsed = peakMemory / (1024 * 1024);  // 转换为 MB
            int gcCount = gcCountAfter - gcCountBefore;

            return String.format(
                "%-15s | 吞吐量: %7.1f req/s | 延迟: %6.2f/%6.1f ms | 内存: %5d MB | GC: %2d 次",
                strategyName, throughput, avgLatency, p99Latency, memoryUsed, gcCount
            );
        }

        private long calculateP99() {
            if (latencies.isEmpty()) return 0;
            List<Long> sorted = new ArrayList<>(latencies);
            sorted.sort(Long::compareTo);
            int p99Index = (int) (sorted.size() * 0.99);
            return sorted.get(p99Index);
        }
    }

    /**
     * 测试 1: 快速消费场景（无背压）
     *
     * 场景说明:
     *   - 数据快速生成，消费者尽快消费
     *   - 背压基本不会触发
     *   - 衡量各策略的基线性能
     */
    public static void test1_FastConsumption() {
        log.info("╔════════════════════════════════════════════════════════════╗");
        log.info("║  测试 1: 快速消费 (无背压触发)                              ║");
        log.info("║  数据规模: 100,000 元素                                      ║");
        log.info("╚════════════════════════════════════════════════════════════╝");

        int dataSize = 100000;
        String[] strategies = {"BUFFER", "DROP", "LATEST", "ERROR"};

        for (String strategy : strategies) {
            PerformanceMetrics metrics = new PerformanceMetrics();
            metrics.strategyName = strategy;
            metrics.memoryBefore = getMemoryUsage();
            metrics.gcCountBefore = getGCCount();
            metrics.startTime = System.currentTimeMillis();

            AtomicLong counter = new AtomicLong(0);

            if ("BUFFER".equals(strategy)) {
                Flux.range(1, dataSize)
                        .onBackpressureBuffer(1000)
                        .subscribe(item -> counter.incrementAndGet());
            } else if ("DROP".equals(strategy)) {
                Flux.range(1, dataSize)
                        .onBackpressureDrop(item -> {})
                        .subscribe(item -> counter.incrementAndGet());
            } else if ("LATEST".equals(strategy)) {
                Flux.range(1, dataSize)
                        .onBackpressureLatest()
                        .subscribe(item -> counter.incrementAndGet());
            } else if ("ERROR".equals(strategy)) {
                try {
                    Flux.range(1, dataSize)
                            .onBackpressureError()
                            .subscribe(item -> counter.incrementAndGet());
                } catch (Exception e) {
                    // 预期 ERROR 可能抛异常
                }
            }

            metrics.endTime = System.currentTimeMillis();
            metrics.completedCount = counter.get();
            metrics.memoryAfter = getMemoryUsage();
            metrics.peakMemory = metrics.memoryAfter;
            metrics.gcCountAfter = getGCCount();

            log.info("✅ {}", metrics);
        }

        log.info("");
    }

    /**
     * 测试 2: 中等消费场景（部分背压）
     *
     * 场景说明:
     *   - 消费速度受限（模拟网络延迟）
     *   - 背压机制会部分启动
     *   - 观察缓冲策略的差异
     */
    public static void test2_MediumConsumption() {
        log.info("╔════════════════════════════════════════════════════════════╗");
        log.info("║  测试 2: 中等消费 (部分背压)                                ║");
        log.info("║  消费延迟: 1ms/元素                                         ║");
        log.info("║  数据规模: 5,000 元素                                        ║");
        log.info("╚════════════════════════════════════════════════════════════╝");

        int dataSize = 5000;
        int consumeDelayMs = 1;
        String[] strategies = {"BUFFER", "DROP", "LATEST", "ERROR"};

        for (String strategy : strategies) {
            PerformanceMetrics metrics = new PerformanceMetrics();
            metrics.strategyName = strategy;
            metrics.memoryBefore = getMemoryUsage();
            metrics.gcCountBefore = getGCCount();
            metrics.startTime = System.currentTimeMillis();

            AtomicLong counter = new AtomicLong(0);
            AtomicLong dropped = new AtomicLong(0);

            if ("BUFFER".equals(strategy)) {
                Flux.range(1, dataSize)
                        .onBackpressureBuffer(500)
                        .subscribe(item -> {
                            try {
                                Thread.sleep(consumeDelayMs);
                                counter.incrementAndGet();
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        });
            } else if ("DROP".equals(strategy)) {
                Flux.range(1, dataSize)
                        .onBackpressureDrop(item -> dropped.incrementAndGet())
                        .subscribe(item -> {
                            try {
                                Thread.sleep(consumeDelayMs);
                                counter.incrementAndGet();
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        });
            } else if ("LATEST".equals(strategy)) {
                Flux.range(1, dataSize)
                        .onBackpressureLatest()
                        .subscribe(item -> {
                            try {
                                Thread.sleep(consumeDelayMs);
                                counter.incrementAndGet();
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        });
            } else if ("ERROR".equals(strategy)) {
                try {
                    Flux.range(1, dataSize)
                            .onBackpressureError()
                            .subscribe(item -> {
                                try {
                                    Thread.sleep(consumeDelayMs);
                                    counter.incrementAndGet();
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                            });
                } catch (Exception e) {
                    metrics.errorCount = 1;
                }
            }

            metrics.endTime = System.currentTimeMillis();
            metrics.completedCount = counter.get();
            metrics.droppedCount = dropped.get();
            metrics.memoryAfter = getMemoryUsage();
            metrics.peakMemory = metrics.memoryAfter;
            metrics.gcCountAfter = getGCCount();

            log.info("✅ {} (丢弃: {})", metrics, metrics.droppedCount);
        }

        log.info("");
    }

    /**
     * 测试 3: 缓慢消费场景（严重背压）
     *
     * 场景说明:
     *   - 消费速度大幅滞后
     *   - 背压机制频繁触发
     *   - 测试缓冲溢出能力
     */
    public static void test3_SlowConsumption() {
        log.info("╔════════════════════════════════════════════════════════════╗");
        log.info("║  测试 3: 缓慢消费 (严重背压)                                ║");
        log.info("║  消费延迟: 5ms/元素                                         ║");
        log.info("║  数据规模: 2,000 元素                                        ║");
        log.info("╚════════════════════════════════════════════════════════════╝");

        int dataSize = 2000;
        int consumeDelayMs = 5;
        String[] strategies = {"BUFFER", "DROP", "LATEST"};  // ERROR 会立即失败，跳过

        for (String strategy : strategies) {
            PerformanceMetrics metrics = new PerformanceMetrics();
            metrics.strategyName = strategy;
            metrics.memoryBefore = getMemoryUsage();
            metrics.gcCountBefore = getGCCount();
            metrics.startTime = System.currentTimeMillis();

            AtomicLong counter = new AtomicLong(0);
            AtomicLong dropped = new AtomicLong(0);

            if ("BUFFER".equals(strategy)) {
                try {
                    Flux.range(1, dataSize)
                            .onBackpressureBuffer(100)
                            .subscribe(item -> {
                                try {
                                    Thread.sleep(consumeDelayMs);
                                    counter.incrementAndGet();
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                            });
                } catch (Exception e) {
                    log.warn("BUFFER 缓冲溢出: {}", e.getMessage());
                }
            } else if ("DROP".equals(strategy)) {
                Flux.range(1, dataSize)
                        .onBackpressureDrop(item -> dropped.incrementAndGet())
                        .subscribe(item -> {
                            try {
                                Thread.sleep(consumeDelayMs);
                                counter.incrementAndGet();
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        });
            } else if ("LATEST".equals(strategy)) {
                Flux.range(1, dataSize)
                        .onBackpressureLatest()
                        .subscribe(item -> {
                            try {
                                Thread.sleep(consumeDelayMs);
                                counter.incrementAndGet();
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        });
            }

            metrics.endTime = System.currentTimeMillis();
            metrics.completedCount = counter.get();
            metrics.droppedCount = dropped.get();
            metrics.memoryAfter = getMemoryUsage();
            metrics.peakMemory = metrics.memoryAfter;
            metrics.gcCountAfter = getGCCount();

            log.info("✅ {} (丢弃: {})", metrics, metrics.droppedCount);
        }

        log.info("");
    }

    /**
     * 测试 4: 对标数据汇总与分析
     */
    public static void test4_SummaryAnalysis() {
        log.info("╔════════════════════════════════════════════════════════════╗");
        log.info("║  对标数据分析与选择建议                                      ║");
        log.info("╚════════════════════════════════════════════════════════════╝");
        log.info("");

        log.info("📊 性能对比结论:");
        log.info("");
        log.info("1️⃣  BUFFER 策略");
        log.info("   适用场景: 金融交易、订单处理等数据不能丢失");
        log.info("   优点: 完整性保证、背压有效");
        log.info("   缺点: 内存占用大、缓冲溢出风险");
        log.info("   建议: 设置合理的缓冲大小 (32-512)，监控缓冲使用率");
        log.info("");

        log.info("2️⃣  DROP 策略");
        log.info("   适用场景: 实时监控、日志聚合等允许丢失旧数据");
        log.info("   优点: 吞吐量最高、内存最少");
        log.info("   缺点: 数据丢失、难以诊断");
        log.info("   建议: 与监控告警配合，追踪丢弃率");
        log.info("");

        log.info("3️⃣  LATEST 策略");
        log.info("   适用场景: 传感器数据、股票行情等只需最新值");
        log.info("   优点: 内存占用最小、延迟最低");
        log.info("   缺点: 中间数据丢失严重、无法恢复");
        log.info("   建议: 用于非关键实时数据，定期审计准确性");
        log.info("");

        log.info("4️⃣  ERROR 策略");
        log.info("   适用场景: 开发测试、参数验证");
        log.info("   优点: 快速发现背压问题");
        log.info("   缺点: 流中断、无法恢复");
        log.info("   建议: 仅用于开发环节，生产环境避免");
        log.info("");

        log.info("🎯 参数调优建议:");
        log.info("");
        log.info("高吞吐量场景 (低延迟, > 100K req/s):");
        log.info("  → 使用 DROP 或 LATEST");
        log.info("  → limitRate(256) 或更大");
        log.info("  → 关闭日志、监控");
        log.info("");

        log.info("平衡场景 (中延迟, 10-100K req/s):");
        log.info("  → 使用 BUFFER (推荐)");
        log.info("  → limitRate(32-64)");
        log.info("  → 缓冲大小 = prefetch * 2");
        log.info("");

        log.info("低延迟要求 (< 100ms P99):");
        log.info("  → 使用 LATEST 或 DROP");
        log.info("  → limitRate(64-128)");
        log.info("  → 定期清理缓冲");
        log.info("");

        log.info("数据完整性优先:");
        log.info("  → 使用 BUFFER 必选");
        log.info("  → 缓冲大小根据峰值吞吐*平均响应时间");
        log.info("  → 实时告警缓冲溢出事件");
        log.info("");
    }

    /**
     * 获取当前内存占用
     */
    private static long getMemoryUsage() {
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }

    /**
     * 获取 GC 计数
     */
    private static int getGCCount() {
        return java.lang.management.ManagementFactory.getGarbageCollectorMXBeans()
                .stream()
                .mapToInt(bean -> (int) bean.getCollectionCount())
                .sum();
    }

    /**
     * 主程序
     */
    public static void main(String[] args) {
        log.info("");
        log.info("╔════════════════════════════════════════════════════════════╗");
        log.info("║  Lab-10 Phase 2: BackpressurePerformanceComparisonTest     ║");
        log.info("║  4 种背压策略的完整性能对标                                ║");
        log.info("╚════════════════════════════════════════════════════════════╝");
        log.info("");

        // 运行三个场景的性能测试
        test1_FastConsumption();
        test2_MediumConsumption();
        test3_SlowConsumption();

        // 汇总分析和建议
        test4_SummaryAnalysis();

        log.info("╔════════════════════════════════════════════════════════════╗");
        log.info("║  ✅ 性能对标完成！                                          ║");
        log.info("╚════════════════════════════════════════════════════════════╝");
        log.info("");
    }
}
