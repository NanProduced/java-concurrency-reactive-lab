package nan.tech.lab10.backpressure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Phase 2: 背压失效场景演示 (BackpressureFailureDemo)
 *
 * 学习目标:
 * - 理解 5 个常见的背压失效场景
 * - 学会诊断背压失效的症状
 * - 掌握每个场景的解决方案
 *
 * 背压失效的症状:
 *   ❌ 内存占用急速增长
 *   ❌ GC 频繁（Full GC）
 *   ❌ 响应时间变长
 *   ❌ 线程数增多（线程爆炸）
 *   ❌ 最终 OOM 异常
 *
 * 难度: ⭐⭐⭐⭐⭐ (很高)
 * 阅读时间: 30-40 分钟
 */
public class BackpressureFailureDemo {
    private static final Logger log = LoggerFactory.getLogger(BackpressureFailureDemo.class);

    /**
     * 失效场景 1: flatMap 无界并发导致背压失效
     *
     * 问题描述:
     *   flatMap() 默认并发度无限制，会创建大量内部流
     *   每个内部流都独立处理元素，背压在 flatMap 处中断
     *   导致所有上游请求一次性发出，内存爆炸
     *
     * 症状:
     *   ⚠️ 内存占用快速增长
     *   ⚠️ 线程数增多（创建大量内部流线程）
     *   ⚠️ GC 频繁
     *
     * 根本原因:
     *   flatMap 内部维护一个 Queue，存放所有待处理的元素
     *   没有并发限制 → 无限接收元素 → 无限 Queue → OOM
     *
     * 解决方案:
     *   ✅ 显式指定 maxConcurrency 参数限制并发数
     */
    public static void scenario1_FlatMapUnboundedConcurrency() {
        log.info("=== Scenario 1: FlatMap 无界并发 ===");
        log.info("问题: flatMap 没有并发限制，导致内存爆炸");
        log.info("");

        // ❌ 危险演示：无界并发
        {
            log.info("❌ 危险做法：flatMap 无并发限制");
            long startMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

            try {
                AtomicInteger concurrentRequests = new AtomicInteger(0);

                Flux.range(1, 1000)
                        .flatMap(userId ->
                                // 每个 userId 都创建一个异步请求
                                Mono.fromCallable(() -> {
                                    concurrentRequests.incrementAndGet();
                                    Thread.sleep(100);  // 模拟异步 I/O
                                    return "User: " + userId;
                                })
                                        .subscribeOn(Schedulers.boundedElastic())
                                // ⚠️ 没有 maxConcurrency 参数！
                        )
                        .blockLast();

                long endMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
                log.warn("  最大并发请求数: {}", concurrentRequests.get());
                log.warn("  内存增长: {} MB", (endMemory - startMemory) / 1024 / 1024);
            } catch (Exception e) {
                log.error("  ❌ 失败: {}", e.getMessage());
            }
            log.info("");
        }

        // ✅ 正确做法：限制并发
        {
            log.info("✅ 正确做法：flatMap 限制 maxConcurrency");
            long startMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

            try {
                AtomicInteger concurrentRequests = new AtomicInteger(0);
                AtomicInteger maxConcurrent = new AtomicInteger(0);

                Flux.range(1, 1000)
                        .flatMap(
                                userId ->
                                        Mono.fromCallable(() -> {
                                            int current = concurrentRequests.incrementAndGet();
                                            maxConcurrent.updateAndGet(m -> Math.max(m, current));
                                            Thread.sleep(100);
                                            concurrentRequests.decrementAndGet();
                                            return "User: " + userId;
                                        })
                                                .subscribeOn(Schedulers.boundedElastic()),
                                32  // ✅ 限制最多 32 个并发
                        )
                        .blockLast();

                long endMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
                log.info("  ✅ 最大并发数受控: {}", maxConcurrent.get());
                log.info("  内存增长: {} MB", (endMemory - startMemory) / 1024 / 1024);
            } catch (Exception e) {
                log.error("  ❌ 失败: {}", e.getMessage());
            }
            log.info("");
        }
    }

    /**
     * 失效场景 2: publishOn 后面跟阻塞操作
     *
     * 问题描述:
     *   publishOn 切换到另一个调度器（通常是有限线程池）
     *   如果之后有阻塞操作，会耗尽线程池
     *   导致背压无法生效，其他请求堆积
     *
     * 症状:
     *   ⚠️ 线程池饱和（Thread.sleep 或同步 I/O）
     *   ⚠️ 响应时间变长
     *   ⚠️ 其他任务等待
     *
     * 根本原因:
     *   有限线程池 + 阻塞操作 → 线程被占满
     *   背压信号无法及时传播 → 上游继续发送 → 内存积压
     *
     * 解决方案:
     *   ✅ 避免在反应式流中使用阻塞操作
     *   ✅ 如果必须使用阻塞，使用专门的阻塞线程池
     */
    public static void scenario2_BlockingInPublishOn() {
        log.info("=== Scenario 2: publishOn 后的阻塞操作 ===");
        log.info("问题: 在线程切换后进行阻塞操作导致线程池饱和");
        log.info("");

        // ❌ 危险演示：阻塞操作
        {
            log.info("❌ 危险做法：publishOn 后是阻塞操作");

            try {
                Flux.range(1, 100)
                        .publishOn(Schedulers.boundedElastic())
                        .map(id -> {
                            try {
                                Thread.sleep(100);  // ⚠️ 阻塞操作
                                return id * 2;
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                return 0;
                            }
                        })
                        .blockLast();

                log.warn("  ⚠️ 线程池被阻塞操作占满");
            } catch (Exception e) {
                log.error("  ❌ 失败: {}", e.getMessage());
            }
            log.info("");
        }

        // ✅ 正确做法：使用非阻塞替代
        {
            log.info("✅ 正确做法：使用非阻塞异步操作");

            try {
                Flux.range(1, 100)
                        .publishOn(Schedulers.boundedElastic())
                        .flatMap(id ->
                                // 使用异步替代 Thread.sleep
                                Mono.delay(java.time.Duration.ofMillis(100))
                                        .map(x -> id * 2)
                        )
                        .blockLast();

                log.info("  ✅ 使用 Mono.delay 替代 Thread.sleep");
            } catch (Exception e) {
                log.error("  ❌ 失败: {}", e.getMessage());
            }
            log.info("");
        }
    }

    /**
     * 失效场景 3: subscribeOn 使用无限制线程池
     *
     * 问题描述:
     *   subscribeOn 指定上游订阅发生的线程
     *   如果线程池无限制，会为每个订阅创建新线程
     *   导致背压在线程创建阶段丢失
     *
     * 症状:
     *   ⚠️ 线程数激增（可能达到系统限制）
     *   ⚠️ 无法创建新线程 → 异常
     *   ⚠️ OOM（线程栈内存）
     *
     * 根本原因:
     *   无限制线程池允许任意数量的线程创建
     *   背压的限制在线程级别失效
     *
     * 解决方案:
     *   ✅ 使用有限线程池（如 Schedulers.parallel()）
     *   ✅ 在数据源层面应用背压
     */
    public static void scenario3_UnlimitedThreadPoolInSubscribeOn() {
        log.info("=== Scenario 3: subscribeOn 无限制线程池 ===");
        log.info("问题: 无限制线程池导致线程爆炸");
        log.info("");

        // ❌ 危险演示：无限线程池（演示时有意降低数量防止真正崩溃）
        {
            log.info("❌ 危险做法：subscribeOn 无限线程池");

            try {
                AtomicInteger threadCount = new AtomicInteger(0);
                int maxThreads = 1000;  // 创建大量线程

                Flux.range(1, maxThreads)
                        .subscribeOn(Schedulers.newParallel("unbounded", maxThreads))
                        .doOnNext(item -> threadCount.incrementAndGet())
                        .blockLast();

                log.warn("  ⚠️ 创建了 {} 个线程（非常危险！）", threadCount.get());
            } catch (Exception e) {
                log.error("  ❌ 线程创建失败: {}", e.getMessage());
            }
            log.info("");
        }

        // ✅ 正确做法：使用有限线程池
        {
            log.info("✅ 正确做法：subscribeOn 使用有限线程池");

            try {
                Flux.range(1, 1000)
                        .subscribeOn(Schedulers.parallel())  // ✅ CPU 核心数限制
                        .blockLast();

                log.info("  ✅ 线程数受控（= CPU 核心数）");
            } catch (Exception e) {
                log.error("  ❌ 失败: {}", e.getMessage());
            }
            log.info("");
        }
    }

    /**
     * 失效场景 4: buffer 缓冲区无大小限制
     *
     * 问题描述:
     *   onBackpressureBuffer() 不指定大小参数
     *   缓冲区无限增长，最终导致 OOM
     *
     * 症状:
     *   ⚠️ 内存持续增长
     *   ⚠️ GC 频繁（越来越频繁）
     *   ⚠️ 最终 OutOfMemoryError
     *
     * 根本原因:
     *   无限缓冲区 = 生产速度永远赢 = 内存无限占用
     *
     * 解决方案:
     *   ✅ 指定缓冲区大小
     *   ✅ 定义溢出策略（DROP_OLDEST 等）
     */
    public static void scenario4_UnboundedBufferOverflow() {
        log.info("=== Scenario 4: Buffer 缓冲区无限制 ===");
        log.info("问题: 无限制缓冲导致 OOM");
        log.info("");

        // ❌ 危险演示：无限缓冲（演示时有意限制数据量）
        {
            log.info("❌ 危险做法：onBackpressureBuffer() 无大小限制");

            try {
                AtomicInteger maxBufferSize = new AtomicInteger(0);

                Flux.range(1, 100000)
                        .onBackpressureBuffer()  // ⚠️ 没有大小限制！
                        .subscribe(
                                item -> {
                                    try {
                                        Thread.sleep(1);  // 缓慢消费
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                    }
                                },
                                error -> log.warn("  ⚠️ 缓冲区溢出: {}", error.getMessage())
                        );

                log.warn("  ⚠️ 缓冲区无限增长，最终会导致 OOM");
            } catch (Exception e) {
                log.error("  ❌ 失败: {}", e.getMessage());
            }
            log.info("");
        }

        // ✅ 正确做法：指定缓冲区大小和溢出策略
        {
            log.info("✅ 正确做法：指定缓冲区大小和溢出策略");

            try {
                AtomicInteger droppedCount = new AtomicInteger(0);

                Flux.range(1, 100000)
                        .onBackpressureBuffer(
                                10000,  // ✅ 缓冲区大小限制
                                item -> droppedCount.incrementAndGet()  // 溢出时的动作
                        )
                        .subscribe(
                                item -> {
                                    try {
                                        Thread.sleep(1);
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                    }
                                }
                        );

                log.info("  ✅ 缓冲区大小受控（10000），溢出 {} 个元素", droppedCount.get());
            } catch (Exception e) {
                log.error("  ❌ 失败: {}", e.getMessage());
            }
            log.info("");
        }
    }

    /**
     * 失效场景 5: fromIterable 一次性加载大数据集
     *
     * 问题描述:
     *   fromIterable() 将整个集合加载到内存中
     *   然后逐个发送元素，但数据已全部在内存
     *   背压无法帮助减少内存占用
     *
     * 症状:
     *   ⚠️ 启动时内存尖峰
     *   ⚠️ 加载大文件时整个文件必须在内存
     *
     * 根本原因:
     *   fromIterable 不是流式加载，是批量加载
     *   背压只能控制发送速度，无法控制加载速度
     *
     * 解决方案:
     *   ✅ 使用 Flux.generate 或 Flux.create 进行流式加载
     *   ✅ 按需加载数据而不是预加载
     */
    public static void scenario5_FromIterableFullLoad() {
        log.info("=== Scenario 5: fromIterable 一次性加载 ===");
        log.info("问题: 将整个集合加载到内存，背压无法帮助");
        log.info("");

        // ❌ 危险演示：一次性加载大集合
        {
            log.info("❌ 危险做法：fromIterable 加载整个列表");

            try {
                long startMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

                // 创建一个大列表（模拟加载大量数据）
                List<String> hugeList = new ArrayList<>();
                for (int i = 0; i < 100000; i++) {
                    hugeList.add("Item-" + i);
                }

                long afterLoadMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
                log.warn("  ⚠️ 加载 {} 个元素后，内存增长 {} MB",
                    hugeList.size(),
                    (afterLoadMemory - startMemory) / 1024 / 1024);

                Flux.fromIterable(hugeList)
                        .subscribe(item -> {
                            try {
                                Thread.sleep(1);  // 缓慢消费
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        });

                log.warn("  ⚠️ 整个列表已在内存，背压无法减少内存占用");
            } catch (Exception e) {
                log.error("  ❌ 失败: {}", e.getMessage());
            }
            log.info("");
        }

        // ✅ 正确做法：流式加载
        {
            log.info("✅ 正确做法：使用 Flux.generate 流式加载");

            try {
                Flux.generate(
                        () -> 0,  // 初始状态
                        (state, sink) -> {
                            if (state < 100000) {
                                sink.next("Item-" + state);
                                return state + 1;
                            } else {
                                sink.complete();
                                return state;
                            }
                        }
                )
                        .subscribe(item -> {
                            try {
                                Thread.sleep(1);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        });

                log.info("  ✅ 流式加载，按需生成元素，内存占用最小");
            } catch (Exception e) {
                log.error("  ❌ 失败: {}", e.getMessage());
            }
            log.info("");
        }
    }

    /**
     * 诊断背压失效的检查清单
     */
    public static void printDiagnosisChecklist() {
        log.info("");
        log.info("╔════════════════════════════════════════════════════════════╗");
        log.info("║  背压失效诊断检查清单                                        ║");
        log.info("╚════════════════════════════════════════════════════════════╝");
        log.info("");
        log.info("如果你的应用出现以下症状，可能背压失效：");
        log.info("");
        log.info("❌ 症状 1: 内存持续增长（即使消费在进行）");
        log.info("   可能原因: Buffer 无限制、fromIterable 一次性加载");
        log.info("   解决: 指定 buffer 大小，使用流式加载");
        log.info("");
        log.info("❌ 症状 2: 线程数增加（甚至爆炸）");
        log.info("   可能原因: flatMap 无并发限制、subscribeOn 无限线程池");
        log.info("   解决: flatMap 指定 maxConcurrency、使用有限线程池");
        log.info("");
        log.info("❌ 症状 3: 响应时间变长");
        log.info("   可能原因: publishOn 后有阻塞操作");
        log.info("   解决: 使用非阻塞异步操作");
        log.info("");
        log.info("❌ 症状 4: GC 频繁（Full GC）");
        log.info("   可能原因: 内存占用过高");
        log.info("   解决: 检查 Buffer、fromIterable、flatMap 配置");
        log.info("");
        log.info("✅ 预防检查清单：");
        log.info("   □ flatMap 有 maxConcurrency 限制吗？");
        log.info("   □ publishOn 后面是否有阻塞操作？");
        log.info("   □ buffer 指定了大小吗？");
        log.info("   □ 使用了流式加载而不是一次性加载？");
        log.info("   □ 使用了 limitRate 精细控制吗？");
        log.info("");
    }

    /**
     * 主程序
     */
    public static void main(String[] args) {
        log.info("");
        log.info("╔════════════════════════════════════════════════════════════╗");
        log.info("║  Lab-10 Phase 2: BackpressureFailureDemo                   ║");
        log.info("║  学习 5 个常见的背压失效场景和解决方案                       ║");
        log.info("╚════════════════════════════════════════════════════════════╝");
        log.info("");

        scenario1_FlatMapUnboundedConcurrency();
        scenario2_BlockingInPublishOn();
        scenario3_UnlimitedThreadPoolInSubscribeOn();
        scenario4_UnboundedBufferOverflow();
        scenario5_FromIterableFullLoad();

        printDiagnosisChecklist();

        log.info("╔════════════════════════════════════════════════════════════╗");
        log.info("║  ✅ 所有失效场景演示完成！                                  ║");
        log.info("║  下一步: 学习 limitRate 源码分析 (LimitRateAnalysisDemo)   ║");
        log.info("╚════════════════════════════════════════════════════════════╝");
        log.info("");
    }
}
