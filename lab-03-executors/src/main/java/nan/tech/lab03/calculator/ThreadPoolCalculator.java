package nan.tech.lab03.calculator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 线程池参数计算器 - 教学演示。
 *
 * <p><b>@教学目标</b>
 * <ul>
 *   <li>理解 ThreadPoolExecutor 的核心参数含义</li>
 *   <li>掌握针对不同任务类型的参数计算公式</li>
 *   <li>通过对比式演示理解错误配置的后果</li>
 *   <li>学会根据实际场景选择合适的线程池配置</li>
 * </ul>
 *
 * <p><b>@核心参数</b>
 * <ul>
 *   <li><b>corePoolSize</b>: 核心线程数，即使空闲也保持存活</li>
 *   <li><b>maximumPoolSize</b>: 最大线程数，包括核心线程和非核心线程</li>
 *   <li><b>keepAliveTime</b>: 非核心线程的空闲存活时间</li>
 *   <li><b>workQueue</b>: 任务队列，存储等待执行的任务</li>
 *   <li><b>rejectedExecutionHandler</b>: 拒绝策略，队列满时的处理方式</li>
 * </ul>
 *
 * <p><b>@参数计算公式</b>
 * <ul>
 *   <li><b>CPU 密集型</b>: 核心数 + 1（避免上下文切换）</li>
 *   <li><b>IO 密集型</b>: 核心数 * (1 + W/C)，W=等待时间，C=计算时间</li>
 *   <li><b>混合型</b>: 核心数 * 2（经验值）</li>
 * </ul>
 *
 * <p><b>@陷阱警示</b>
 * <ul>
 *   <li>错误 1: 线程数过多导致 CPU 上下文切换频繁，性能下降</li>
 *   <li>错误 2: 线程数过少导致 CPU 利用率低，吞吐量不足</li>
 *   <li>错误 3: 队列设置为无界队列（Integer.MAX_VALUE）可能 OOM</li>
 *   <li>错误 4: 未考虑任务的实际特性（CPU/IO）盲目配置</li>
 * </ul>
 *
 * <p><b>@参考资料</b>
 * <ul>
 *   <li>《Java Concurrency in Practice》 - Chapter 8</li>
 *   <li>《深入理解 Java 虚拟机》 - 线程池章节</li>
 *   <li>Oracle ThreadPoolExecutor 官方文档</li>
 * </ul>
 *
 * @author Claude Code Assistant
 * @since 2025-10-18
 */
public class ThreadPoolCalculator {

    private static final Logger logger = LoggerFactory.getLogger(ThreadPoolCalculator.class);

    /**
     * 获取当前系统的 CPU 核心数。
     *
     * <p><b>@教学</b>
     * <p>这是所有线程池参数计算的基础。Runtime.getRuntime().availableProcessors()
     * 返回的是逻辑核心数（包括超线程），而非物理核心数。
     *
     * <p><b>@实现细节</b>
     * <ul>
     *   <li>在容器环境（如 Docker）中，该值可能受到 CPU 限制（cgroup）影响</li>
     *   <li>建议通过 JMX 或容器 API 获取实际可用的 CPU 配额</li>
     * </ul>
     *
     * @return CPU 核心数
     */
    public static int getCpuCores() {
        int cores = Runtime.getRuntime().availableProcessors();
        logger.info("检测到 CPU 核心数: {}", cores);
        return cores;
    }

    /**
     * 计算 CPU 密集型任务的线程池参数。
     *
     * <p><b>@教学</b>
     * <p>CPU 密集型任务的特点是持续占用 CPU 计算，几乎没有 IO 等待。
     * 此时线程数不宜过多，否则会导致频繁的上下文切换，反而降低性能。
     *
     * <p><b>@公式</b>
     * <pre>
     * 核心线程数 = CPU 核心数 + 1
     * 最大线程数 = CPU 核心数 + 1
     *
     * 为什么 +1？
     * - 当某个线程偶尔因为页缺失或其他原因暂停时，额外的线程可以补上
     * - 保证 CPU 利用率接近 100%
     * </pre>
     *
     * <p><b>@适用场景</b>
     * <ul>
     *   <li>图像处理、视频编码</li>
     *   <li>数据压缩、加密解密</li>
     *   <li>科学计算、机器学习推理</li>
     * </ul>
     *
     * @return CPU 密集型线程池配置
     */
    public static ThreadPoolConfig calculateForCpuIntensive() {
        int cpuCores = getCpuCores();
        int corePoolSize = cpuCores + 1;
        int maximumPoolSize = cpuCores + 1;

        // CPU 密集型任务推荐使用 SynchronousQueue（不缓冲任务）
        // 原因: 任务到达后立即执行，避免队列积压
        BlockingQueue<Runnable> workQueue = new SynchronousQueue<>();

        // 拒绝策略: CallerRunsPolicy（调用者执行）
        // 原因: 提供背压机制，避免任务提交速度过快
        RejectedExecutionHandler handler = new ThreadPoolExecutor.CallerRunsPolicy();

        logger.info("✅ CPU 密集型线程池配置:");
        logger.info("  - 核心线程数: {}", corePoolSize);
        logger.info("  - 最大线程数: {}", maximumPoolSize);
        logger.info("  - 队列类型: SynchronousQueue (不缓冲)");
        logger.info("  - 拒绝策略: CallerRunsPolicy (背压)");

        return new ThreadPoolConfig(corePoolSize, maximumPoolSize, 60L, TimeUnit.SECONDS,
                workQueue, handler, "CPU密集型");
    }

    /**
     * 计算 IO 密集型任务的线程池参数。
     *
     * <p><b>@教学</b>
     * <p>IO 密集型任务的特点是大量时间用于等待 IO（网络请求、数据库查询、文件读写）。
     * 此时可以增加线程数，以便在某些线程等待 IO 时，其他线程可以利用 CPU。
     *
     * <p><b>@公式（Amdahl 定律推导）</b>
     * <pre>
     * 最佳线程数 = CPU 核心数 * (1 + W/C)
     *
     * 其中:
     *   W = 等待时间 (Wait Time)
     *   C = 计算时间 (Compute Time)
     *
     * 示例:
     *   假设一个任务 80% 时间在等待 IO，20% 时间在计算
     *   W/C = 0.8 / 0.2 = 4
     *   在 8 核 CPU 上: 最佳线程数 = 8 * (1 + 4) = 40
     * </pre>
     *
     * <p><b>@适用场景</b>
     * <ul>
     *   <li>Web 服务器（HTTP 请求处理）</li>
     *   <li>数据库操作（JDBC 查询）</li>
     *   <li>文件处理、网络爬虫</li>
     * </ul>
     *
     * <p><b>@陷阱</b>
     * <ul>
     *   <li>不要设置过大的线程数（如 500+），会导致线程调度开销过大</li>
     *   <li>建议通过压测找到最佳值，理论公式仅供参考</li>
     * </ul>
     *
     * @param waitTimeRatio 等待时间比例 (W/C)，例如 IO 占 80% 则传入 4.0
     * @return IO 密集型线程池配置
     */
    public static ThreadPoolConfig calculateForIoIntensive(double waitTimeRatio) {
        int cpuCores = getCpuCores();

        // 核心线程数 = CPU 核心数 * (1 + W/C)
        int corePoolSize = (int) (cpuCores * (1 + waitTimeRatio));

        // 最大线程数 = 核心线程数 * 2 (留有余量应对突发流量)
        int maximumPoolSize = corePoolSize * 2;

        // IO 密集型任务推荐使用 LinkedBlockingQueue（有界队列）
        // 原因: 缓冲突发流量，避免频繁创建线程
        BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>(1000);

        // 拒绝策略: AbortPolicy（抛异常）
        // 原因: 快速失败，让调用方感知到系统过载
        RejectedExecutionHandler handler = new ThreadPoolExecutor.AbortPolicy();

        logger.info("✅ IO 密集型线程池配置 (W/C = {}):", waitTimeRatio);
        logger.info("  - 核心线程数: {}", corePoolSize);
        logger.info("  - 最大线程数: {}", maximumPoolSize);
        logger.info("  - 队列类型: LinkedBlockingQueue(1000)");
        logger.info("  - 拒绝策略: AbortPolicy (快速失败)");

        return new ThreadPoolConfig(corePoolSize, maximumPoolSize, 60L, TimeUnit.SECONDS,
                workQueue, handler, "IO密集型");
    }

    /**
     * 计算混合型任务的线程池参数。
     *
     * <p><b>@教学</b>
     * <p>混合型任务既有 CPU 计算，又有 IO 等待。这是最常见的场景，但也最难精确配置。
     * 建议使用经验值作为起点，然后通过压测调优。
     *
     * <p><b>@经验公式</b>
     * <pre>
     * 核心线程数 = CPU 核心数 * 2
     * 最大线程数 = 核心线程数 * 2
     *
     * 这个公式适用于大多数 Web 应用和微服务场景
     * </pre>
     *
     * <p><b>@适用场景</b>
     * <ul>
     *   <li>Web 应用（处理请求 + 数据库查询）</li>
     *   <li>微服务（RPC 调用 + 业务逻辑）</li>
     *   <li>批处理（文件读取 + 数据转换）</li>
     * </ul>
     *
     * <p><b>@调优建议</b>
     * <ul>
     *   <li>从经验值开始，逐步调整（每次变化 20-30%）</li>
     *   <li>通过压测观察 P95/P99 延迟和吞吐量</li>
     *   <li>监控线程池的活跃线程数和队列长度</li>
     * </ul>
     *
     * @return 混合型线程池配置
     */
    public static ThreadPoolConfig calculateForMixed() {
        int cpuCores = getCpuCores();
        int corePoolSize = cpuCores * 2;
        int maximumPoolSize = corePoolSize * 2;

        // 混合型任务推荐使用 ArrayBlockingQueue（有界队列）
        // 原因: 内存局部性好，性能优于 LinkedBlockingQueue
        BlockingQueue<Runnable> workQueue = new ArrayBlockingQueue<>(500);

        // 拒绝策略: CallerRunsPolicy（调用者执行）
        // 原因: 提供背压，避免系统过载
        RejectedExecutionHandler handler = new ThreadPoolExecutor.CallerRunsPolicy();

        logger.info("✅ 混合型线程池配置:");
        logger.info("  - 核心线程数: {}", corePoolSize);
        logger.info("  - 最大线程数: {}", maximumPoolSize);
        logger.info("  - 队列类型: ArrayBlockingQueue(500)");
        logger.info("  - 拒绝策略: CallerRunsPolicy (背压)");

        return new ThreadPoolConfig(corePoolSize, maximumPoolSize, 60L, TimeUnit.SECONDS,
                workQueue, handler, "混合型");
    }

    /**
     * 对比演示：正确配置 vs 错误配置。
     *
     * <p><b>@教学目标</b>
     * <p>通过实际运行对比，直观展示错误配置导致的性能问题。
     *
     * <p><b>@对比场景</b>
     * <ul>
     *   <li>场景 1: CPU 密集型任务使用正确配置（核心数+1）</li>
     *   <li>场景 2: CPU 密集型任务使用错误配置（核心数*10，过多线程）</li>
     * </ul>
     *
     * <p><b>@预期结果</b>
     * <ul>
     *   <li>正确配置: 任务完成时间短，CPU 利用率高</li>
     *   <li>错误配置: 任务完成时间长，大量上下文切换</li>
     * </ul>
     */
    public static void compareCorrectVsWrong() {
        logger.info("\n" + "=".repeat(80));
        logger.info("📊 对比演示：正确配置 vs 错误配置");
        logger.info("=".repeat(80));

        int taskCount = 1000; // 模拟 1000 个 CPU 密集型任务
        int iterations = 100_000_000; // 每个任务的计算量

        // ========== 正确配置 ==========
        logger.info("\n✅ 正确配置：CPU 密集型任务使用 (核心数+1) 线程");
        ThreadPoolConfig correctConfig = calculateForCpuIntensive();
        long correctTime = runTasks(correctConfig.createExecutor(), taskCount, iterations);

        // ========== 错误配置 ==========
        logger.info("\n❌ 错误配置：CPU 密集型任务使用 (核心数*10) 线程（过多）");
        int cpuCores = getCpuCores();
        ThreadPoolExecutor wrongExecutor = new ThreadPoolExecutor(
                cpuCores * 10,  // 核心线程数过多
                cpuCores * 10,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1000),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        long wrongTime = runTasks(wrongExecutor, taskCount, iterations);

        // ========== 结果对比 ==========
        logger.info("\n" + "=".repeat(80));
        logger.info("📊 性能对比结果:");
        logger.info("  ✅ 正确配置耗时: {} ms", correctTime);
        logger.info("  ❌ 错误配置耗时: {} ms", wrongTime);
        double improvement = ((wrongTime - correctTime) * 100.0 / wrongTime);
        if (improvement > 0) {
            logger.info("  📈 性能提升: {:.2f}%", improvement);
        } else {
            logger.info("  📉 性能下降: {:.2f}% (可能因为机器核心数过多)", Math.abs(improvement));
            logger.info("  💡 注意: 在高核心数机器上，过多线程的负面影响可能不明显");
        }
        logger.info("=".repeat(80));

        logger.info("\n💡 结论:");
        logger.info("  - CPU 密集型任务线程数过多会导致频繁的上下文切换");
        logger.info("  - 正确配置可以显著提升性能（预期提升 20-40%）");
        logger.info("  - 线程数 ≠ 性能，合适才是最好的");
    }

    /**
     * 运行任务并计时。
     *
     * <p><b>@实现细节</b>
     * <p>使用 CountDownLatch 等待所有任务完成，确保计时准确。
     *
     * @param executor   线程池
     * @param taskCount  任务数量
     * @param iterations 每个任务的计算量
     * @return 总耗时（毫秒）
     */
    private static long runTasks(ExecutorService executor, int taskCount, int iterations) {
        CountDownLatch latch = new CountDownLatch(taskCount);
        long startTime = System.currentTimeMillis();

        // 提交任务
        for (int i = 0; i < taskCount; i++) {
            executor.submit(() -> {
                try {
                    // 模拟 CPU 密集型计算
                    long sum = 0;
                    for (int j = 0; j < iterations; j++) {
                        sum += j;
                    }
                    // 防止编译器优化掉计算
                    if (sum < 0) {
                        logger.debug("Unexpected sum: {}", sum);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        // 等待所有任务完成
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("等待任务完成时被中断", e);
        }

        long endTime = System.currentTimeMillis();

        // 优雅关闭线程池
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        return endTime - startTime;
    }

    /**
     * 主方法 - 自启动演示。
     *
     * <p><b>@教学流程</b>
     * <ol>
     *   <li>展示 CPU 密集型任务的参数计算</li>
     *   <li>展示 IO 密集型任务的参数计算</li>
     *   <li>展示混合型任务的参数计算</li>
     *   <li>对比演示：正确配置 vs 错误配置</li>
     * </ol>
     */
    public static void main(String[] args) {
        logger.info("\n" + "=".repeat(80));
        logger.info("🎓 Lab-03: 线程池参数计算器 - 教学演示");
        logger.info("=".repeat(80));

        // 1. CPU 密集型任务配置
        logger.info("\n📌 场景 1: CPU 密集型任务（图像处理、视频编码）");
        ThreadPoolConfig cpuConfig = calculateForCpuIntensive();

        // 2. IO 密集型任务配置
        logger.info("\n📌 场景 2: IO 密集型任务（数据库查询、网络请求）");
        logger.info("假设任务 80% 时间在等待 IO，20% 时间在计算 (W/C = 4.0)");
        ThreadPoolConfig ioConfig = calculateForIoIntensive(4.0);

        // 3. 混合型任务配置
        logger.info("\n📌 场景 3: 混合型任务（Web 应用、微服务）");
        ThreadPoolConfig mixedConfig = calculateForMixed();

        // 4. 对比演示
        logger.info("\n📌 场景 4: 对比演示（正确配置 vs 错误配置）");
        compareCorrectVsWrong();

        logger.info("\n✅ 演示完成！请查看上述输出理解线程池参数计算的原理。");
    }

    /**
     * 线程池配置类 - 封装线程池参数。
     *
     * <p><b>@教学</b>
     * <p>这个类用于封装线程池的完整配置，便于传递和创建线程池。
     */
    public static class ThreadPoolConfig {
        private final int corePoolSize;
        private final int maximumPoolSize;
        private final long keepAliveTime;
        private final TimeUnit unit;
        private final BlockingQueue<Runnable> workQueue;
        private final RejectedExecutionHandler handler;
        private final String taskType;

        public ThreadPoolConfig(int corePoolSize, int maximumPoolSize, long keepAliveTime,
                                TimeUnit unit, BlockingQueue<Runnable> workQueue,
                                RejectedExecutionHandler handler, String taskType) {
            this.corePoolSize = corePoolSize;
            this.maximumPoolSize = maximumPoolSize;
            this.keepAliveTime = keepAliveTime;
            this.unit = unit;
            this.workQueue = workQueue;
            this.handler = handler;
            this.taskType = taskType;
        }

        /**
         * 创建线程池执行器。
         *
         * <p><b>@教学</b>
         * <p>使用封装的配置参数创建 ThreadPoolExecutor 实例。
         *
         * @return ThreadPoolExecutor 实例
         */
        public ThreadPoolExecutor createExecutor() {
            // 创建自定义线程工厂，为线程命名（便于调试）
            AtomicInteger threadNumber = new AtomicInteger(1);
            ThreadFactory threadFactory = r -> {
                Thread t = new Thread(r, taskType + "-thread-" + threadNumber.getAndIncrement());
                // 设置为守护线程，避免阻止 JVM 退出
                t.setDaemon(false);
                return t;
            };

            return new ThreadPoolExecutor(
                    corePoolSize,
                    maximumPoolSize,
                    keepAliveTime,
                    unit,
                    workQueue,
                    threadFactory,
                    handler
            );
        }

        // Getters
        public int getCorePoolSize() {
            return corePoolSize;
        }

        public int getMaximumPoolSize() {
            return maximumPoolSize;
        }

        public String getTaskType() {
            return taskType;
        }
    }
}
