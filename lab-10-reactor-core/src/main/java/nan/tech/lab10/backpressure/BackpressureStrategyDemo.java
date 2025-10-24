package nan.tech.lab10.backpressure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple2;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Phase 2: 背压策略演示 (BackpressureStrategyDemo)
 *
 * 学习目标：
 * - 理解 4 种背压策略的工作原理
 * - 对比各策略的内存占用和吞吐量
 * - 根据场景选择最合适的背压策略
 *
 * 难度: ⭐⭐⭐⭐ (高)
 * 阅读时间: 20-30 分钟
 */
public class BackpressureStrategyDemo {
    private static final Logger log = LoggerFactory.getLogger(BackpressureStrategyDemo.class);

    /**
     * Demo 1: BUFFER 策略 - 缓冲溢出的元素
     *
     * 工作原理:
     *   - 当消费速度慢时，将超出的元素缓冲在内存中
     *   - 缓冲区有大小限制（避免 OOM）
     *   - 缓冲满时触发 BackpressureOverflowException
     *
     * 优点:
     *   ✅ 不丢失数据（完整性高）
     *   ✅ 简单易用
     *   ✅ 适合大多数场景
     *
     * 缺点:
     *   ❌ 内存占用较大（取决于缓冲区大小）
     *   ❌ 缓冲区满时流中断
     *
     * 适用场景:
     *   ✅ 数据不能丢失（订单、日志）
     *   ✅ 生产消费速度差异不大
     *   ✅ 内存充足
     *
     * 代码示例:
     */
    public static void demo1_BufferStrategy() {
        log.info("=== Demo 1: BUFFER Strategy ===");
        log.info("概念: 缓冲超出的元素到内存");
        log.info("");

        // 性能监控
        AtomicLong producedCount = new AtomicLong(0);
        AtomicLong consumedCount = new AtomicLong(0);
        AtomicLong maxBufferSize = new AtomicLong(0);

        try {
            Flux.range(1, 10000)
                    .doOnNext(item -> producedCount.incrementAndGet())
                    .onBackpressureBuffer(
                            1000,  // 缓冲区大小：最多 1000 个元素
                            // 缓冲满时的处理策略（可选）
                            dropItem -> log.warn("Buffer dropped: {}", dropItem)
                    )
                    .subscribe(
                            item -> {
                                // 缓慢消费：每个元素消费 1ms
                                try {
                                    Thread.sleep(1);
                                    consumedCount.incrementAndGet();
                                    if (item % 1000 == 0) {
                                        log.info("Consumed: {}", item);
                                    }
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                            },
                            error -> log.error("Buffer Strategy Error: {}", error.getMessage()),
                            () -> log.info("✅ Stream completed successfully")
                    );

            log.info("性能数据: 生产={}, 消费={}", producedCount.get(), consumedCount.get());
            log.info("");
        } catch (Exception e) {
            log.error("❌ Buffer strategy failed: {}", e.getMessage());
        }
    }

    /**
     * Demo 2: DROP 策略 - 丢弃无法及时消费的元素
     *
     * 工作原理:
     *   - 当生产速度 > 消费速度时，丢弃新产生的元素
     *   - 消费者只能看到某些元素（其他被静默丢弃）
     *   - 不提供任何警告
     *
     * 优点:
     *   ✅ 内存占用小（无缓冲）
     *   ✅ 性能最好
     *   ✅ 适合实时数据
     *
     * 缺点:
     *   ❌ 数据丢失（完整性低）
     *   ❌ 难以诊断（哪些数据丢了？）
     *   ❌ 不适合订单、金融场景
     *
     * 适用场景:
     *   ✅ 允许丢失数据（实时监控、日志）
     *   ✅ 内存受限
     *   ✅ 需要最新数据（旧数据过时）
     *
     * 代码示例:
     */
    public static void demo2_DropStrategy() {
        log.info("=== Demo 2: DROP Strategy ===");
        log.info("概念: 丢弃无法及时消费的元素");
        log.info("");

        // 性能监控
        AtomicLong producedCount = new AtomicLong(0);
        AtomicLong consumedCount = new AtomicLong(0);
        AtomicLong droppedCount = new AtomicLong(0);

        try {
            Flux.range(1, 10000)
                    .doOnNext(item -> producedCount.incrementAndGet())
                    .onBackpressureDrop(
                            droppedItem -> {
                                droppedCount.incrementAndGet();
                                if (droppedCount.get() % 1000 == 0) {
                                    log.warn("Dropped {} items", droppedCount.get());
                                }
                            }
                    )
                    .subscribe(
                            item -> {
                                // 缓慢消费
                                try {
                                    Thread.sleep(1);
                                    consumedCount.incrementAndGet();
                                    if (item % 1000 == 0) {
                                        log.info("Consumed: {}", item);
                                    }
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                            },
                            error -> log.error("Drop Strategy Error: {}", error.getMessage()),
                            () -> {
                                log.info("✅ Stream completed");
                                log.info("统计: 生产={}, 消费={}, 丢弃={}",
                                    producedCount.get(), consumedCount.get(), droppedCount.get());
                            }
                    );

            log.info("");
        } catch (Exception e) {
            log.error("❌ Drop strategy failed: {}", e.getMessage());
        }
    }

    /**
     * Demo 3: LATEST 策略 - 保留最新的一个元素，覆盖之前未消费的
     *
     * 工作原理:
     *   - 缓冲区大小固定为 1
     *   - 新元素覆盖缓冲区中的旧元素
     *   - 消费者总是获得最新值
     *
     * 优点:
     *   ✅ 内存占用最小（仅 1 个元素）
     *   ✅ 总是获取最新值
     *   ✅ 适合实时更新场景
     *
     * 缺点:
     *   ❌ 数据丢失（严重）
     *   ❌ 不适合历史数据
     *   ❌ 难以诊断
     *
     * 适用场景:
     *   ✅ 只关心最新值（实时汇率、温度）
     *   ✅ 历史数据无关紧要
     *   ✅ 更新频繁
     *
     * 代码示例:
     */
    public static void demo3_LatestStrategy() {
        log.info("=== Demo 3: LATEST Strategy ===");
        log.info("概念: 保留最新的一个元素，覆盖旧元素");
        log.info("");

        // 性能监控
        AtomicLong producedCount = new AtomicLong(0);
        AtomicLong consumedCount = new AtomicLong(0);

        try {
            Flux.range(1, 10000)
                    .doOnNext(item -> producedCount.incrementAndGet())
                    .onBackpressureLatest()  // 最新值策略
                    .subscribe(
                            item -> {
                                // 缓慢消费
                                try {
                                    Thread.sleep(2);  // 比 DROP 演示慢 2 倍
                                    consumedCount.incrementAndGet();
                                    if (item % 1000 == 0) {
                                        log.info("Latest value: {}", item);
                                    }
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                            },
                            error -> log.error("Latest Strategy Error: {}", error.getMessage()),
                            () -> {
                                log.info("✅ Stream completed");
                                log.info("统计: 生产={}, 消费={}, 缓冲区大小=1",
                                    producedCount.get(), consumedCount.get());
                                log.info("说明: 由于大量元素被覆盖，消费数 << 生产数");
                            }
                    );

            log.info("");
        } catch (Exception e) {
            log.error("❌ Latest strategy failed: {}", e.getMessage());
        }
    }

    /**
     * Demo 4: ERROR 策略 - 背压违规时立即抛异常
     *
     * 工作原理:
     *   - 监控生产是否超过请求量
     *   - 如果生产 > 请求，立即抛 BackpressureOverflowException
     *   - 流中断，消费者得到异常通知
     *
     * 优点:
     *   ✅ 快速失败，易于检测背压问题
     *   ✅ 零缓冲，最小内存
     *   ✅ 清晰的错误信号
     *
     * 缺点:
     *   ❌ 流中断，不能继续
     *   ❌ 消费者需要处理异常
     *   ❌ 不适合生产环境
     *
     * 适用场景:
     *   ✅ 调试和测试
     *   ✅ 需要快速失败的场景
     *   ✅ 验证背压配置是否正确
     *
     * 代码示例:
     */
    public static void demo4_ErrorStrategy() {
        log.info("=== Demo 4: ERROR Strategy ===");
        log.info("概念: 背压违规时立即抛异常（用于测试）");
        log.info("");

        // 性能监控
        AtomicLong producedCount = new AtomicLong(0);
        AtomicLong consumedCount = new AtomicLong(0);

        try {
            Flux.range(1, 10000)
                    .doOnNext(item -> producedCount.incrementAndGet())
                    .onBackpressureError()  // ERROR 策略
                    .subscribe(
                            item -> {
                                // 缓慢消费，导致背压溢出
                                try {
                                    Thread.sleep(5);
                                    consumedCount.incrementAndGet();
                                    if (item % 1000 == 0) {
                                        log.info("Consumed: {}", item);
                                    }
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                            },
                            error -> {
                                log.error("⚠️ Backpressure Error (expected): {}",
                                    error.getClass().getSimpleName());
                                log.info("错误信息: {}", error.getMessage());
                            },
                            () -> log.info("✅ Stream completed")
                    );

            log.info("");
        } catch (Exception e) {
            log.error("❌ Error strategy failed: {}", e.getMessage());
        }
    }

    /**
     * 对比演示：4 种策略的性能差异
     *
     * 对比指标:
     *   - 内存占用
     *   - 消费完成数
     *   - 执行时间
     */
    public static void demo5_StrategyComparison() {
        log.info("=== Demo 5: Strategy Comparison ===");
        log.info("对比 4 种背压策略的性能差异");
        log.info("");

        int dataSize = 10000;
        int sleepMs = 1;

        // Strategy 1: BUFFER
        {
            log.info("1️⃣  BUFFER 策略");
            long startTime = System.currentTimeMillis();
            AtomicLong count = new AtomicLong(0);

            Flux.range(1, dataSize)
                    .onBackpressureBuffer(1000)
                    .subscribe(
                            item -> {
                                try {
                                    Thread.sleep(sleepMs);
                                    count.incrementAndGet();
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                            }
                    );

            long duration = System.currentTimeMillis() - startTime;
            log.info("  ✅ 消费: {} 个, 耗时: {} ms", count.get(), duration);
        }

        // Strategy 2: DROP
        {
            log.info("2️⃣  DROP 策略");
            long startTime = System.currentTimeMillis();
            AtomicLong count = new AtomicLong(0);
            AtomicLong dropped = new AtomicLong(0);

            Flux.range(1, dataSize)
                    .onBackpressureDrop(item -> dropped.incrementAndGet())
                    .subscribe(
                            item -> {
                                try {
                                    Thread.sleep(sleepMs);
                                    count.incrementAndGet();
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                            }
                    );

            long duration = System.currentTimeMillis() - startTime;
            log.info("  ✅ 消费: {} 个, 丢弃: {} 个, 耗时: {} ms", count.get(), dropped.get(), duration);
        }

        // Strategy 3: LATEST
        {
            log.info("3️⃣  LATEST 策略");
            long startTime = System.currentTimeMillis();
            AtomicLong count = new AtomicLong(0);

            Flux.range(1, dataSize)
                    .onBackpressureLatest()
                    .subscribe(
                            item -> {
                                try {
                                    Thread.sleep(sleepMs);
                                    count.incrementAndGet();
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                            }
                    );

            long duration = System.currentTimeMillis() - startTime;
            log.info("  ✅ 消费: {} 个, 耗时: {} ms", count.get(), duration);
        }

        log.info("");
        log.info("小结:");
        log.info("  - BUFFER: 完整性最高，内存占用较大");
        log.info("  - DROP: 吞吐量最好，但丢失数据");
        log.info("  - LATEST: 内存最少，丢失大量数据");
        log.info("  - ERROR: 用于测试，不应该用于生产");
        log.info("");
    }

    /**
     * 主程序：依次运行所有演示
     */
    public static void main(String[] args) {
        log.info("");
        log.info("╔════════════════════════════════════════════════════════════╗");
        log.info("║  Lab-10 Phase 2: BackpressureStrategyDemo                   ║");
        log.info("║  学习 4 种背压策略的工作原理和性能差异                       ║");
        log.info("╚════════════════════════════════════════════════════════════╝");
        log.info("");

        // Demo 1: BUFFER 策略
        demo1_BufferStrategy();
        sleepBetweenDemos();

        // Demo 2: DROP 策略
        demo2_DropStrategy();
        sleepBetweenDemos();

        // Demo 3: LATEST 策略
        demo3_LatestStrategy();
        sleepBetweenDemos();

        // Demo 4: ERROR 策略
        demo4_ErrorStrategy();
        sleepBetweenDemos();

        // Demo 5: 对比分析
        demo5_StrategyComparison();

        log.info("╔════════════════════════════════════════════════════════════╗");
        log.info("║  ✅ 所有演示完成！                                          ║");
        log.info("║  下一步: 学习背压失效场景 (BackpressureFailureDemo)         ║");
        log.info("╚════════════════════════════════════════════════════════════╝");
        log.info("");
    }

    /**
     * 演示间隔延迟（便于观察）
     */
    private static void sleepBetweenDemos() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
