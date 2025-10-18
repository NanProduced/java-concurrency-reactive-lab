package nan.tech.lab03.gc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * GC 影响分析演示
 *
 * <p>演示线程池配置对 GC 的影响：
 * <ul>
 *   <li>线程数过多 → 对象分配速率高 → Young GC 频繁</li>
 *   <li>队列过大 → 堆内存占用高 → Full GC 风险</li>
 *   <li>任务对象过大 → Old Gen 压力 → Full GC 频繁</li>
 *   <li>合理配置 → GC 压力小 → 性能稳定</li>
 * </ul>
 *
 * <h2>教学目标</h2>
 * <ol>
 *   <li>理解线程池配置与 GC 的关系</li>
 *   <li>掌握 GC 监控的基本方法</li>
 *   <li>学会通过 GC 指标优化线程池参数</li>
 *   <li>理解对象分配速率对 GC 的影响</li>
 * </ol>
 *
 * <h2>关键知识点</h2>
 * <ul>
 *   <li><b>Young GC</b>: 新生代垃圾回收，频率高，停顿时间短 (通常 <10ms)</li>
 *   <li><b>Full GC</b>: 全堆垃圾回收，频率低，停顿时间长 (可能 >100ms)</li>
 *   <li><b>对象分配速率</b>: 每秒分配的对象大小，影响 Young GC 频率</li>
 *   <li><b>堆内存占用</b>: 影响 Full GC 触发频率</li>
 * </ul>
 *
 * <h2>常见陷阱</h2>
 * <ul>
 *   <li>线程数过多导致上下文切换和对象分配速率过高</li>
 *   <li>无界队列导致内存溢出 (OOM)</li>
 *   <li>任务对象过大导致 Old Gen 压力</li>
 *   <li>忽略 GC 监控，只关注吞吐量</li>
 * </ul>
 *
 * <h2>优化建议</h2>
 * <ul>
 *   <li>控制线程数在合理范围内 (CPU 密集型: cores+1, IO 密集型: cores*(1+W/C))</li>
 *   <li>使用有界队列，设置合理的容量限制</li>
 *   <li>避免在任务中创建大对象</li>
 *   <li>监控 GC 频率和停顿时间，及时调优</li>
 * </ul>
 *
 * @author nan
 * @since 2025-10-18
 */
public class GCImpactDemo {

    private static final Logger logger = LoggerFactory.getLogger(GCImpactDemo.class);

    // GC 统计
    private static class GCStats {
        long youngGcCount = 0;
        long youngGcTime = 0;
        long fullGcCount = 0;
        long fullGcTime = 0;
    }

    /**
     * 错误配置 1: 线程数过多
     *
     * <p>问题：线程数过多导致：
     * <ul>
     *   <li>上下文切换频繁</li>
     *   <li>对象分配速率过高</li>
     *   <li>Young GC 频繁</li>
     * </ul>
     *
     * <p>配置：100 个线程 (远超 CPU 核数)
     */
    public static void demonstrateTooManyThreads() {
        logger.info("=== 错误配置 1: 线程数过多 (100 线程) ===");

        GCStats before = collectGCStats();
        AtomicLong objectsCreated = new AtomicLong(0);

        // 创建线程池: 100 个线程 (过多)
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                100, 100, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1000)
        );

        long startTime = System.currentTimeMillis();

        try {
            // 提交 1000 个任务
            for (int i = 0; i < 1000; i++) {
                executor.execute(() -> {
                    // 模拟对象分配
                    byte[] data = new byte[1024];  // 1KB
                    objectsCreated.incrementAndGet();
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }

            executor.shutdown();
            executor.awaitTermination(30, TimeUnit.SECONDS);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("演示被中断", e);
        }

        long duration = System.currentTimeMillis() - startTime;
        GCStats after = collectGCStats();

        printGCReport("线程数过多", before, after, duration, objectsCreated.get());

        logger.info("💡 分析: 线程数过多导致上下文切换频繁，对象分配速率高，Young GC 频繁");
        logger.info("");
    }

    /**
     * 错误配置 2: 队列过大
     *
     * <p>问题：队列过大导致：
     * <ul>
     *   <li>堆内存占用高</li>
     *   <li>任务堆积</li>
     *   <li>Full GC 风险</li>
     * </ul>
     *
     * <p>配置：队列容量 100000 (过大)
     */
    public static void demonstrateTooLargeQueue() {
        logger.info("=== 错误配置 2: 队列过大 (100000) ===");

        GCStats before = collectGCStats();
        AtomicLong objectsCreated = new AtomicLong(0);

        // 创建线程池: 队列容量 100000 (过大)
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                10, 20, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(100000)
        );

        long startTime = System.currentTimeMillis();

        try {
            // 快速提交 10000 个任务，模拟任务堆积
            for (int i = 0; i < 10000; i++) {
                final int taskId = i;
                executor.execute(() -> {
                    // 模拟对象分配
                    byte[] data = new byte[10 * 1024];  // 10KB
                    objectsCreated.incrementAndGet();
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }

            // 等待部分任务执行
            Thread.sleep(5000);

            logger.info("  当前队列长度: {}", executor.getQueue().size());
            logger.info("  活跃线程数: {}", executor.getActiveCount());

            executor.shutdown();
            executor.awaitTermination(60, TimeUnit.SECONDS);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("演示被中断", e);
        }

        long duration = System.currentTimeMillis() - startTime;
        GCStats after = collectGCStats();

        printGCReport("队列过大", before, after, duration, objectsCreated.get());

        logger.info("💡 分析: 队列过大导致任务堆积，堆内存占用高，增加 Full GC 风险");
        logger.info("");
    }

    /**
     * 正确配置: 合理的线程数和队列大小
     *
     * <p>配置：
     * <ul>
     *   <li>核心线程数: CPU 核数 * 2 (混合型任务)</li>
     *   <li>最大线程数: 核心线程数 * 2</li>
     *   <li>队列容量: 500 (有界队列)</li>
     * </ul>
     */
    public static void demonstrateCorrectConfiguration() {
        logger.info("=== 正确配置: 合理的线程数和队列大小 ===");

        GCStats before = collectGCStats();
        AtomicLong objectsCreated = new AtomicLong(0);

        int cpuCores = Runtime.getRuntime().availableProcessors();

        // 创建线程池: 合理配置
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                cpuCores * 2,
                cpuCores * 4,
                60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(500)
        );

        long startTime = System.currentTimeMillis();

        try {
            // 提交 1000 个任务
            int submitted = 0;
            for (int i = 0; i < 1000; i++) {
                try {
                    executor.execute(() -> {
                        // 模拟对象分配
                        byte[] data = new byte[1024];  // 1KB
                        objectsCreated.incrementAndGet();
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    });
                    submitted++;
                } catch (RejectedExecutionException e) {
                    // 触发背压，稍微等待
                    Thread.sleep(10);
                    i--;  // 重试
                }
            }

            executor.shutdown();
            executor.awaitTermination(30, TimeUnit.SECONDS);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("演示被中断", e);
        }

        long duration = System.currentTimeMillis() - startTime;
        GCStats after = collectGCStats();

        printGCReport("正确配置", before, after, duration, objectsCreated.get());

        logger.info("💡 分析: 合理配置减少了 GC 压力，性能稳定");
        logger.info("");
    }

    /**
     * 对比演示 - 展示不同配置对 GC 的影响
     */
    public static void compareGCImpact() {
        logger.info("");
        logger.info("╔══════════════════════════════════════════════════════════════════╗");
        logger.info("║                    GC 影响对比汇总表                              ║");
        logger.info("╠══════════════════════════════════════════════════════════════════╣");
        logger.info("║ 配置         | Young GC | Full GC | 对象分配速率 | 堆内存占用    ║");
        logger.info("╠══════════════════════════════════════════════════════════════════╣");
        logger.info("║ 线程数过多   | 高       | 低      | 高           | 中等          ║");
        logger.info("║ 队列过大     | 中等     | 高      | 中等         | 高            ║");
        logger.info("║ 正确配置     | 低       | 低      | 低           | 低            ║");
        logger.info("╚══════════════════════════════════════════════════════════════════╝");
        logger.info("");

        logger.info("📖 优化建议:");
        logger.info("  1. 控制线程数在合理范围内");
        logger.info("  2. 使用有界队列，避免任务堆积");
        logger.info("  3. 避免在任务中创建大对象");
        logger.info("  4. 监控 GC 指标，及时调优");
        logger.info("");

        logger.info("📊 GC 监控工具:");
        logger.info("  1. jstat -gc <pid> 1000  # 每秒输出 GC 统计");
        logger.info("  2. jmap -heap <pid>      # 查看堆内存使用");
        logger.info("  3. GCEasy (gceasy.io)    # 在线 GC 日志分析");
        logger.info("  4. JFR (Java Flight Recorder) # 生产环境性能分析");
        logger.info("");
    }

    /**
     * 收集 GC 统计信息
     *
     * @return GC 统计
     */
    private static GCStats collectGCStats() {
        GCStats stats = new GCStats();
        List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();

        for (GarbageCollectorMXBean gcBean : gcBeans) {
            String name = gcBean.getName().toLowerCase();
            long count = gcBean.getCollectionCount();
            long time = gcBean.getCollectionTime();

            if (name.contains("young") || name.contains("scavenge") || name.contains("parnew") || name.contains("copy")) {
                // Young GC
                stats.youngGcCount = count;
                stats.youngGcTime = time;
            } else if (name.contains("old") || name.contains("marksweep") || name.contains("cms") || name.contains("g1")) {
                // Full GC (或 Old GC)
                stats.fullGcCount = count;
                stats.fullGcTime = time;
            }
        }

        return stats;
    }

    /**
     * 打印 GC 报告
     *
     * @param scenario      场景名称
     * @param before        之前的 GC 统计
     * @param after         之后的 GC 统计
     * @param duration      执行时长 (ms)
     * @param objectsCreated 创建的对象数
     */
    private static void printGCReport(String scenario, GCStats before, GCStats after, long duration, long objectsCreated) {
        long youngGcCount = after.youngGcCount - before.youngGcCount;
        long youngGcTime = after.youngGcTime - before.youngGcTime;
        long fullGcCount = after.fullGcCount - before.fullGcCount;
        long fullGcTime = after.fullGcTime - before.fullGcTime;

        logger.info("");
        logger.info("📊 {} GC 统计:", scenario);
        logger.info("  执行时长: {} ms", duration);
        logger.info("  创建对象数: {}", objectsCreated);
        logger.info("  Young GC 次数: {}", youngGcCount);
        logger.info("  Young GC 耗时: {} ms", youngGcTime);
        logger.info("  Full GC 次数: {}", fullGcCount);
        logger.info("  Full GC 耗时: {} ms", fullGcTime);

        if (youngGcCount > 0) {
            logger.info("  平均 Young GC 耗时: {} ms", youngGcTime / youngGcCount);
        }
        if (fullGcCount > 0) {
            logger.info("  平均 Full GC 耗时: {} ms", fullGcTime / fullGcCount);
            logger.warn("  ⚠️ 警告: 发生了 {} 次 Full GC，可能影响性能", fullGcCount);
        }

        logger.info("");
    }

    /**
     * 主函数 - 自启动演示
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        logger.info("╔════════════════════════════════════════════════════════════════╗");
        logger.info("║          Lab-03: GC 影响分析演示                                ║");
        logger.info("║          GC Impact Analysis Demo                               ║");
        logger.info("╚════════════════════════════════════════════════════════════════╝");
        logger.info("");

        logger.info("⚙️  JVM 参数建议: -Xms512m -Xmx512m -XX:+PrintGC -XX:+PrintGCDetails");
        logger.info("");

        try {
            // 1. 错误配置 1: 线程数过多
            demonstrateTooManyThreads();
            Thread.sleep(2000);
            System.gc();  // 建议 GC，清理垃圾
            Thread.sleep(1000);

            // 2. 错误配置 2: 队列过大
            demonstrateTooLargeQueue();
            Thread.sleep(2000);
            System.gc();  // 建议 GC，清理垃圾
            Thread.sleep(1000);

            // 3. 正确配置
            demonstrateCorrectConfiguration();
            Thread.sleep(2000);
            System.gc();  // 建议 GC，清理垃圾
            Thread.sleep(1000);

            // 4. 对比汇总
            compareGCImpact();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("演示被中断", e);
        }

        logger.info("╔════════════════════════════════════════════════════════════════╗");
        logger.info("║                    演示完成                                      ║");
        logger.info("╚════════════════════════════════════════════════════════════════╝");
    }
}
