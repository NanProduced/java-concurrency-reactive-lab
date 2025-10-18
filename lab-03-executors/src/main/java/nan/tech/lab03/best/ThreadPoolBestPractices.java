package nan.tech.lab03.best;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 线程池最佳实践汇总
 *
 * <p>汇总线程池使用的 10 大最佳实践：
 * <ol>
 *   <li>使用有界队列，避免 OOM</li>
 *   <li>命名线程池，便于问题定位</li>
 *   <li>设置合理的拒绝策略</li>
 *   <li>监控线程池关键指标</li>
 *   <li>优雅关闭线程池</li>
 *   <li>避免使用 Executors 工厂方法</li>
 *   <li>设置合理的线程存活时间</li>
 *   <li>根据任务类型选择队列类型</li>
 *   <li>预热核心线程</li>
 *   <li>处理任务异常</li>
 * </ol>
 *
 * <h2>教学目标</h2>
 * <ol>
 *   <li>掌握线程池使用的最佳实践</li>
 *   <li>学会避免常见的线程池陷阱</li>
 *   <li>理解每个最佳实践的重要性</li>
 *   <li>能够在实际项目中应用这些实践</li>
 * </ol>
 *
 * @author nan
 * @since 2025-10-18
 */
public class ThreadPoolBestPractices {

    private static final Logger logger = LoggerFactory.getLogger(ThreadPoolBestPractices.class);

    /**
     * 最佳实践 1: 使用有界队列，避免 OOM
     *
     * <p>问题：无界队列可能导致内存溢出
     * <p>解决：使用有界队列，设置合理的容量限制
     */
    public static void practice1_UseBoundedQueue() {
        logger.info("=== 最佳实践 1: 使用有界队列，避免 OOM ===");

        // ❌ 错误: 使用无界队列
        // ThreadPoolExecutor bad = new ThreadPoolExecutor(
        //     10, 20, 60L, TimeUnit.SECONDS,
        //     new LinkedBlockingQueue<>()  // 默认容量 Integer.MAX_VALUE
        // );

        // ✅ 正确: 使用有界队列
        ThreadPoolExecutor good = new ThreadPoolExecutor(
                10, 20, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1000),  // 容量限制为 1000
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        logger.info("  ✅ 使用有界队列 (容量: 1000)");
        logger.info("  💡 建议: 根据实际负载设置队列容量，避免 OOM");

        good.shutdown();
        logger.info("");
    }

    /**
     * 最佳实践 2: 命名线程池，便于问题定位
     *
     * <p>问题：默认线程名难以识别
     * <p>解决：使用 ThreadFactory 自定义线程名
     */
    public static void practice2_NameThreadPool() {
        logger.info("=== 最佳实践 2: 命名线程池，便于问题定位 ===");

        // ✅ 正确: 自定义线程名
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                5, 10, 60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(100),
                new NamedThreadFactory("MyBusiness"),  // 自定义线程名
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        executor.execute(() -> {
            logger.info("  当前线程: {}", Thread.currentThread().getName());
        });

        logger.info("  ✅ 使用命名线程池 (前缀: MyBusiness)");
        logger.info("  💡 好处: jstack 分析时，能快速定位问题线程");

        executor.shutdown();
        logger.info("");
    }

    /**
     * 最佳实践 3: 设置合理的拒绝策略
     *
     * <p>问题：默认 AbortPolicy 可能导致任务丢失
     * <p>解决：根据业务需求选择合适的拒绝策略
     */
    public static void practice3_SetRejectionPolicy() {
        logger.info("=== 最佳实践 3: 设置合理的拒绝策略 ===");

        logger.info("  策略选择:");
        logger.info("    - AbortPolicy: 关键业务，需要感知过载");
        logger.info("    - CallerRunsPolicy: Web 应用，背压机制");
        logger.info("    - DiscardPolicy: 非关键任务 (日志、监控)");
        logger.info("    - DiscardOldestPolicy: 实时数据处理");
        logger.info("");

        // ✅ 示例: Web 应用使用 CallerRunsPolicy
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                10, 20, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(500),
                new ThreadPoolExecutor.CallerRunsPolicy()  // 背压机制
        );

        logger.info("  ✅ Web 应用推荐: CallerRunsPolicy (背压机制)");
        logger.info("  💡 效果: 降低任务提交速度，避免系统过载");

        executor.shutdown();
        logger.info("");
    }

    /**
     * 最佳实践 4: 监控线程池关键指标
     *
     * <p>问题：缺乏监控，无法发现问题
     * <p>解决：定期收集线程池指标，设置告警
     */
    public static void practice4_MonitorThreadPool() {
        logger.info("=== 最佳实践 4: 监控线程池关键指标 ===");

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                5, 10, 60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(100)
        );

        // 提交一些任务
        for (int i = 0; i < 20; i++) {
            final int taskId = i;
            executor.execute(() -> {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        // 监控指标
        logger.info("  📊 关键指标:");
        logger.info("    - 活跃线程数: {} / {}", executor.getActiveCount(), executor.getCorePoolSize());
        logger.info("    - 队列长度: {} / {}", executor.getQueue().size(), 100);
        logger.info("    - 已完成任务数: {}", executor.getCompletedTaskCount());
        logger.info("    - 总任务数: {}", executor.getTaskCount());
        logger.info("");

        logger.info("  💡 建议:");
        logger.info("    - 使用 Prometheus + Grafana 监控");
        logger.info("    - 设置告警: 队列长度 >80%, 拒绝率 >1%");

        executor.shutdown();
        logger.info("");
    }

    /**
     * 最佳实践 5: 优雅关闭线程池
     *
     * <p>问题：直接 shutdownNow() 可能导致任务丢失
     * <p>解决：先 shutdown()，等待一段时间，再 shutdownNow()
     */
    public static void practice5_GracefulShutdown() {
        logger.info("=== 最佳实践 5: 优雅关闭线程池 ===");

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                5, 10, 60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(100)
        );

        // 提交一些任务
        for (int i = 0; i < 10; i++) {
            final int taskId = i;
            executor.execute(() -> {
                logger.debug("  执行任务 {}", taskId);
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        // ✅ 正确: 优雅关闭
        logger.info("  正在优雅关闭线程池...");
        executor.shutdown();  // 不再接受新任务

        try {
            // 等待 60 秒
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                logger.warn("  ⚠️ 超时，强制关闭");
                executor.shutdownNow();  // 强制关闭

                // 再等待 60 秒
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    logger.error("  ❌ 线程池无法正常关闭");
                }
            } else {
                logger.info("  ✅ 线程池已优雅关闭");
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        logger.info("  💡 最佳实践: shutdown() → awaitTermination() → shutdownNow()");
        logger.info("");
    }

    /**
     * 最佳实践 6: 避免使用 Executors 工厂方法
     *
     * <p>问题：Executors 工厂方法有坑
     * <p>解决：直接使用 ThreadPoolExecutor 构造函数
     */
    public static void practice6_AvoidExecutors() {
        logger.info("=== 最佳实践 6: 避免使用 Executors 工厂方法 ===");

        logger.info("  ❌ 避免:");
        logger.info("    - Executors.newFixedThreadPool()   // 无界队列，OOM 风险");
        logger.info("    - Executors.newCachedThreadPool()  // 无限线程，资源耗尽");
        logger.info("    - Executors.newSingleThreadExecutor() // 无界队列，OOM 风险");
        logger.info("");

        logger.info("  ✅ 推荐:");
        logger.info("    - 直接使用 ThreadPoolExecutor 构造函数");
        logger.info("    - 明确指定所有参数");
        logger.info("");

        // ✅ 示例
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                10,  // corePoolSize
                20,  // maximumPoolSize
                60L, TimeUnit.SECONDS,  // keepAliveTime
                new LinkedBlockingQueue<>(1000),  // 有界队列
                new NamedThreadFactory("MyPool"),  // 自定义线程名
                new ThreadPoolExecutor.CallerRunsPolicy()  // 拒绝策略
        );

        logger.info("  💡 好处: 参数清晰，避免隐藏的坑");

        executor.shutdown();
        logger.info("");
    }

    /**
     * 最佳实践 7: 设置合理的线程存活时间
     *
     * <p>问题：线程存活时间过长或过短
     * <p>解决：根据任务特点设置合理的 keepAliveTime
     */
    public static void practice7_SetKeepAliveTime() {
        logger.info("=== 最佳实践 7: 设置合理的线程存活时间 ===");

        logger.info("  推荐值:");
        logger.info("    - CPU 密集型: 60 秒 (通用)");
        logger.info("    - IO 密集型: 60 秒 (通用)");
        logger.info("    - 混合型: 60 秒 (通用)");
        logger.info("    - 短生命周期任务: 30 秒");
        logger.info("    - 长生命周期任务: 120 秒");
        logger.info("");

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                5, 10,
                60L, TimeUnit.SECONDS,  // ✅ 60 秒通用
                new ArrayBlockingQueue<>(100)
        );

        logger.info("  💡 建议: 通常使用 60 秒即可，根据实际情况调整");

        executor.shutdown();
        logger.info("");
    }

    /**
     * 最佳实践 8: 根据任务类型选择队列类型
     *
     * <p>问题：队列类型选择不当
     * <p>解决：根据任务特点选择合适的队列
     */
    public static void practice8_ChooseQueueType() {
        logger.info("=== 最佳实践 8: 根据任务类型选择队列类型 ===");

        logger.info("  队列选择:");
        logger.info("    - SynchronousQueue: CPU 密集型 (不缓冲)");
        logger.info("    - LinkedBlockingQueue: IO 密集型 (性能稳定)");
        logger.info("    - ArrayBlockingQueue: 混合型 (内存局部性好)");
        logger.info("    - PriorityBlockingQueue: 有优先级需求");
        logger.info("");

        logger.info("  💡 建议: 优先考虑 LinkedBlockingQueue 和 ArrayBlockingQueue");
        logger.info("");
    }

    /**
     * 最佳实践 9: 预热核心线程
     *
     * <p>问题：首次请求可能较慢
     * <p>解决：预热核心线程，提前创建
     */
    public static void practice9_PrestartCoreThreads() {
        logger.info("=== 最佳实践 9: 预热核心线程 ===");

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                5, 10, 60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(100)
        );

        logger.info("  预热前活跃线程数: {}", executor.getPoolSize());

        // ✅ 预热核心线程
        executor.prestartAllCoreThreads();

        logger.info("  预热后活跃线程数: {}", executor.getPoolSize());
        logger.info("  ✅ 已预热 {} 个核心线程", executor.getCorePoolSize());
        logger.info("  💡 好处: 避免首次请求较慢");

        executor.shutdown();
        logger.info("");
    }

    /**
     * 最佳实践 10: 处理任务异常
     *
     * <p>问题：任务异常可能被吞掉
     * <p>解决：使用 try-catch 或自定义 ThreadFactory
     */
    public static void practice10_HandleTaskException() {
        logger.info("=== 最佳实践 10: 处理任务异常 ===");

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                5, 10, 60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(100),
                new ExceptionHandlingThreadFactory("SafePool")  // 自定义异常处理
        );

        // 提交会抛异常的任务
        executor.execute(() -> {
            logger.info("  执行任务...");
            throw new RuntimeException("模拟异常");
        });

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        logger.info("  💡 建议: 使用自定义 ThreadFactory 或在任务中 try-catch");

        executor.shutdown();
        logger.info("");
    }

    /**
     * 汇总所有最佳实践
     */
    public static void summarizeAllPractices() {
        logger.info("");
        logger.info("╔══════════════════════════════════════════════════════════════════╗");
        logger.info("║              线程池最佳实践汇总 (Top 10)                          ║");
        logger.info("╠══════════════════════════════════════════════════════════════════╣");
        logger.info("║ 1. 使用有界队列，避免 OOM                                        ║");
        logger.info("║ 2. 命名线程池，便于问题定位                                       ║");
        logger.info("║ 3. 设置合理的拒绝策略                                            ║");
        logger.info("║ 4. 监控线程池关键指标                                            ║");
        logger.info("║ 5. 优雅关闭线程池                                                ║");
        logger.info("║ 6. 避免使用 Executors 工厂方法                                   ║");
        logger.info("║ 7. 设置合理的线程存活时间                                         ║");
        logger.info("║ 8. 根据任务类型选择队列类型                                       ║");
        logger.info("║ 9. 预热核心线程                                                  ║");
        logger.info("║ 10. 处理任务异常                                                 ║");
        logger.info("╚══════════════════════════════════════════════════════════════════╝");
        logger.info("");

        logger.info("📖 完整的线程池配置示例:");
        logger.info("```java");
        logger.info("ThreadPoolExecutor executor = new ThreadPoolExecutor(");
        logger.info("    10,                                  // corePoolSize");
        logger.info("    20,                                  // maximumPoolSize");
        logger.info("    60L, TimeUnit.SECONDS,              // keepAliveTime");
        logger.info("    new LinkedBlockingQueue<>(1000),    // 有界队列");
        logger.info("    new NamedThreadFactory(\"MyPool\"),   // 自定义线程名");
        logger.info("    new CallerRunsPolicy()              // 拒绝策略");
        logger.info(");");
        logger.info("executor.prestartAllCoreThreads();      // 预热核心线程");
        logger.info("```");
        logger.info("");
    }

    /**
     * 自定义 ThreadFactory - 命名线程
     */
    private static class NamedThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;

        public NamedThreadFactory(String namePrefix) {
            this.namePrefix = namePrefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, namePrefix + "-thread-" + threadNumber.getAndIncrement());
            thread.setDaemon(false);
            return thread;
        }
    }

    /**
     * 自定义 ThreadFactory - 异常处理
     */
    private static class ExceptionHandlingThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;

        public ExceptionHandlingThreadFactory(String namePrefix) {
            this.namePrefix = namePrefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, namePrefix + "-thread-" + threadNumber.getAndIncrement());
            thread.setDaemon(false);

            // 设置未捕获异常处理器
            thread.setUncaughtExceptionHandler((t, e) -> {
                logger.error("  ❌ 线程 {} 抛出未捕获异常: {}", t.getName(), e.getMessage());
            });

            return thread;
        }
    }

    /**
     * 主函数 - 自启动演示
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        logger.info("╔════════════════════════════════════════════════════════════════╗");
        logger.info("║          Lab-03: 线程池最佳实践汇总                             ║");
        logger.info("║          Thread Pool Best Practices                            ║");
        logger.info("╚════════════════════════════════════════════════════════════════╝");
        logger.info("");

        // 逐个演示最佳实践
        practice1_UseBoundedQueue();
        practice2_NameThreadPool();
        practice3_SetRejectionPolicy();
        practice4_MonitorThreadPool();
        practice5_GracefulShutdown();
        practice6_AvoidExecutors();
        practice7_SetKeepAliveTime();
        practice8_ChooseQueueType();
        practice9_PrestartCoreThreads();
        practice10_HandleTaskException();

        // 汇总
        summarizeAllPractices();

        logger.info("╔════════════════════════════════════════════════════════════════╗");
        logger.info("║                    演示完成                                      ║");
        logger.info("╚════════════════════════════════════════════════════════════════╝");
    }
}
