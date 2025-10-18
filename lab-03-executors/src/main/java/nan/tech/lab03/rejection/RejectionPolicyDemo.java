package nan.tech.lab03.rejection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 拒绝策略对比演示
 *
 * <p>演示 ThreadPoolExecutor 的 4 种拒绝策略在队列满时的不同行为：
 * <ul>
 *   <li>AbortPolicy: 抛出 RejectedExecutionException (默认策略)</li>
 *   <li>CallerRunsPolicy: 调用者线程执行任务 (背压机制)</li>
 *   <li>DiscardPolicy: 静默丢弃新任务</li>
 *   <li>DiscardOldestPolicy: 丢弃队列头部最老的任务</li>
 * </ul>
 *
 * <h2>教学目标</h2>
 * <ol>
 *   <li>理解每种拒绝策略的行为特征</li>
 *   <li>掌握不同策略的适用场景</li>
 *   <li>学会根据业务需求选择合适的策略</li>
 *   <li>理解背压机制的工作原理</li>
 * </ol>
 *
 * <h2>关键知识点</h2>
 * <ul>
 *   <li><b>AbortPolicy</b>: 快速失败，适用于需要感知系统过载的场景</li>
 *   <li><b>CallerRunsPolicy</b>: 降低任务提交速度，适用于需要保证所有任务都被执行的场景</li>
 *   <li><b>DiscardPolicy</b>: 任务丢失无感知，适用于非关键任务 (如日志、监控数据)</li>
 *   <li><b>DiscardOldestPolicy</b>: 最新数据更重要，适用于实时数据处理</li>
 * </ul>
 *
 * <h2>常见陷阱</h2>
 * <ul>
 *   <li>使用 DiscardPolicy 时，任务丢失可能导致数据丢失，需要有监控和告警</li>
 *   <li>CallerRunsPolicy 会阻塞调用者线程，可能导致提交任务的线程池饥饿</li>
 *   <li>DiscardOldestPolicy 可能丢弃重要任务，需要确认业务逻辑允许</li>
 *   <li>AbortPolicy 需要调用方处理异常，否则会导致任务丢失</li>
 * </ul>
 *
 * @author nan
 * @since 2025-10-18
 */
public class RejectionPolicyDemo {

    private static final Logger logger = LoggerFactory.getLogger(RejectionPolicyDemo.class);

    // 任务计数器
    private static final AtomicInteger submittedTaskCount = new AtomicInteger(0);
    private static final AtomicInteger executedTaskCount = new AtomicInteger(0);
    private static final AtomicInteger rejectedTaskCount = new AtomicInteger(0);

    /**
     * AbortPolicy 演示 - 快速失败策略
     *
     * <p>行为：当队列满时，抛出 RejectedExecutionException
     *
     * <p>适用场景：
     * <ul>
     *   <li>IO 密集型任务</li>
     *   <li>需要感知系统过载的场景</li>
     *   <li>关键业务任务，不允许静默丢弃</li>
     * </ul>
     *
     * <p>风险：调用方必须处理异常，否则任务丢失
     */
    public static void demonstrateAbortPolicy() {
        logger.info("=== AbortPolicy 演示 - 快速失败策略 ===");

        // 重置计数器
        resetCounters();

        // 创建线程池: 核心1个，最大2个，队列容量2
        // 当提交第 4 个任务时，队列已满且没有空闲线程，触发拒绝策略
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, 2, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(2),
                new ThreadPoolExecutor.AbortPolicy()  // 默认策略
        );

        try {
            // 提交 5 个任务，前 3 个能执行，第 4-5 个会被拒绝
            for (int i = 1; i <= 5; i++) {
                final int taskId = i;
                try {
                    executor.execute(() -> executeTask(taskId));
                    submittedTaskCount.incrementAndGet();
                    logger.info("  ✅ 任务 {} 提交成功", taskId);
                } catch (RejectedExecutionException e) {
                    rejectedTaskCount.incrementAndGet();
                    logger.warn("  ❌ 任务 {} 被拒绝: {}", taskId, e.getMessage());
                }

                // 稍微延迟，观察队列变化
                Thread.sleep(100);
            }

            // 等待所有任务执行完成
            Thread.sleep(2000);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("演示被中断", e);
        } finally {
            executor.shutdown();
        }

        printSummary("AbortPolicy");

        logger.info("💡 总结: AbortPolicy 会抛出异常，调用方可以感知系统过载并采取措施");
        logger.info("");
    }

    /**
     * CallerRunsPolicy 演示 - 背压机制
     *
     * <p>行为：当队列满时，任务在调用者线程中执行
     *
     * <p>适用场景：
     * <ul>
     *   <li>CPU 密集型任务</li>
     *   <li>混合型任务</li>
     *   <li>需要保证所有任务都被执行的场景</li>
     *   <li>Web 应用 (降低请求速度，避免系统过载)</li>
     * </ul>
     *
     * <p>风险：降低任务提交速度，可能导致调用者线程阻塞
     */
    public static void demonstrateCallerRunsPolicy() {
        logger.info("=== CallerRunsPolicy 演示 - 背压机制 ===");

        // 重置计数器
        resetCounters();

        // 创建线程池: 核心1个，最大2个，队列容量2
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, 2, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(2),
                new ThreadPoolExecutor.CallerRunsPolicy()  // 调用者执行
        );

        try {
            // 提交 5 个任务，所有任务都会被执行
            for (int i = 1; i <= 5; i++) {
                final int taskId = i;
                long startTime = System.currentTimeMillis();

                executor.execute(() -> executeTask(taskId));
                submittedTaskCount.incrementAndGet();

                long submitTime = System.currentTimeMillis() - startTime;
                if (submitTime > 500) {
                    logger.info("  🐌 任务 {} 提交耗时 {}ms (在调用者线程执行)", taskId, submitTime);
                } else {
                    logger.info("  ✅ 任务 {} 提交成功", taskId);
                }

                // 稍微延迟
                Thread.sleep(100);
            }

            // 等待所有任务执行完成
            Thread.sleep(2000);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("演示被中断", e);
        } finally {
            executor.shutdown();
        }

        printSummary("CallerRunsPolicy");

        logger.info("💡 总结: CallerRunsPolicy 不会丢弃任务，但会降低提交速度 (背压机制)");
        logger.info("");
    }

    /**
     * DiscardPolicy 演示 - 静默丢弃策略
     *
     * <p>行为：当队列满时，静默丢弃新任务，不抛异常
     *
     * <p>适用场景：
     * <ul>
     *   <li>日志收集 (允许丢失部分日志)</li>
     *   <li>监控数据采集 (允许丢失部分数据点)</li>
     *   <li>非关键任务</li>
     * </ul>
     *
     * <p>风险：任务丢失无感知，需要有监控和告警
     */
    public static void demonstrateDiscardPolicy() {
        logger.info("=== DiscardPolicy 演示 - 静默丢弃策略 ===");

        // 重置计数器
        resetCounters();

        // 创建线程池: 核心1个，最大2个，队列容量2
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, 2, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(2),
                new ThreadPoolExecutor.DiscardPolicy()  // 静默丢弃
        );

        try {
            // 提交 5 个任务，部分任务会被静默丢弃
            for (int i = 1; i <= 5; i++) {
                final int taskId = i;
                executor.execute(() -> executeTask(taskId));
                submittedTaskCount.incrementAndGet();
                logger.info("  ➡️ 任务 {} 已提交 (可能被丢弃)", taskId);

                // 稍微延迟
                Thread.sleep(100);
            }

            // 等待所有任务执行完成
            Thread.sleep(2000);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("演示被中断", e);
        } finally {
            executor.shutdown();
        }

        printSummary("DiscardPolicy");

        logger.info("💡 总结: DiscardPolicy 静默丢弃任务，调用方无法感知，需要有监控");
        logger.info("⚠️  警告: 任务丢失数 = 提交数 - 执行数 = {} - {} = {}",
                submittedTaskCount.get(), executedTaskCount.get(),
                submittedTaskCount.get() - executedTaskCount.get());
        logger.info("");
    }

    /**
     * DiscardOldestPolicy 演示 - 丢弃最老任务策略
     *
     * <p>行为：当队列满时，丢弃队列头部最老的任务，然后尝试重新提交新任务
     *
     * <p>适用场景：
     * <ul>
     *   <li>实时数据处理 (最新数据更重要)</li>
     *   <li>股票行情推送 (旧行情可以丢弃)</li>
     *   <li>实时监控告警 (最新告警更重要)</li>
     * </ul>
     *
     * <p>风险：可能丢弃重要任务，需要确认业务逻辑允许
     */
    public static void demonstrateDiscardOldestPolicy() {
        logger.info("=== DiscardOldestPolicy 演示 - 丢弃最老任务策略 ===");

        // 重置计数器
        resetCounters();

        // 创建线程池: 核心1个，最大2个，队列容量2
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, 2, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(2),
                new ThreadPoolExecutor.DiscardOldestPolicy()  // 丢弃最老的
        );

        try {
            // 提交 5 个任务，部分老任务会被丢弃
            for (int i = 1; i <= 5; i++) {
                final int taskId = i;
                executor.execute(() -> executeTask(taskId));
                submittedTaskCount.incrementAndGet();
                logger.info("  ➡️ 任务 {} 已提交 (可能导致旧任务被丢弃)", taskId);

                // 稍微延迟
                Thread.sleep(100);
            }

            // 等待所有任务执行完成
            Thread.sleep(2000);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("演示被中断", e);
        } finally {
            executor.shutdown();
        }

        printSummary("DiscardOldestPolicy");

        logger.info("💡 总结: DiscardOldestPolicy 保证最新任务优先执行，但会丢弃旧任务");
        logger.info("⚠️  警告: 任务丢失数 = 提交数 - 执行数 = {} - {} = {}",
                submittedTaskCount.get(), executedTaskCount.get(),
                submittedTaskCount.get() - executedTaskCount.get());
        logger.info("");
    }

    /**
     * 执行任务 (模拟耗时操作)
     *
     * @param taskId 任务 ID
     */
    private static void executeTask(int taskId) {
        try {
            logger.debug("    🔄 任务 {} 开始执行 (线程: {})", taskId, Thread.currentThread().getName());

            // 模拟任务执行 (500ms)
            Thread.sleep(500);

            executedTaskCount.incrementAndGet();
            logger.debug("    ✅ 任务 {} 执行完成", taskId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("任务 {} 被中断", taskId, e);
        }
    }

    /**
     * 重置计数器
     */
    private static void resetCounters() {
        submittedTaskCount.set(0);
        executedTaskCount.set(0);
        rejectedTaskCount.set(0);
    }

    /**
     * 打印汇总信息
     *
     * @param policyName 策略名称
     */
    private static void printSummary(String policyName) {
        logger.info("");
        logger.info("📊 {} 统计:", policyName);
        logger.info("  提交任务数: {}", submittedTaskCount.get());
        logger.info("  执行任务数: {}", executedTaskCount.get());
        logger.info("  拒绝任务数: {}", rejectedTaskCount.get());
        logger.info("  丢失任务数: {}", submittedTaskCount.get() - executedTaskCount.get() - rejectedTaskCount.get());
        logger.info("");
    }

    /**
     * 对比演示 - 并排展示 4 种策略的行为差异
     */
    public static void compareAllPolicies() {
        logger.info("");
        logger.info("╔══════════════════════════════════════════════════════════════════╗");
        logger.info("║                    拒绝策略对比汇总表                              ║");
        logger.info("╠══════════════════════════════════════════════════════════════════╣");
        logger.info("║ 策略               | 行为         | 适用场景       | 风险          ║");
        logger.info("╠══════════════════════════════════════════════════════════════════╣");
        logger.info("║ AbortPolicy        | 抛出异常     | IO密集型       | 需处理异常    ║");
        logger.info("║ CallerRunsPolicy   | 调用者执行   | CPU/混合型     | 降低提交速度  ║");
        logger.info("║ DiscardPolicy      | 静默丢弃     | 非关键任务     | 任务丢失      ║");
        logger.info("║ DiscardOldestPolicy| 丢弃最老任务 | 实时数据处理   | 可能丢失重要  ║");
        logger.info("╚══════════════════════════════════════════════════════════════════╝");
        logger.info("");

        logger.info("📖 选择建议:");
        logger.info("  1. 关键业务任务 → AbortPolicy (感知过载)");
        logger.info("  2. Web 应用 → CallerRunsPolicy (背压机制)");
        logger.info("  3. 日志/监控 → DiscardPolicy (允许丢失)");
        logger.info("  4. 实时推送 → DiscardOldestPolicy (最新优先)");
        logger.info("");
    }

    /**
     * 主函数 - 自启动演示
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        logger.info("╔════════════════════════════════════════════════════════════════╗");
        logger.info("║          Lab-03: 拒绝策略对比演示                                ║");
        logger.info("║          Rejection Policy Comparison Demo                      ║");
        logger.info("╚════════════════════════════════════════════════════════════════╝");
        logger.info("");

        try {
            // 1. AbortPolicy 演示
            demonstrateAbortPolicy();
            Thread.sleep(1000);

            // 2. CallerRunsPolicy 演示
            demonstrateCallerRunsPolicy();
            Thread.sleep(1000);

            // 3. DiscardPolicy 演示
            demonstrateDiscardPolicy();
            Thread.sleep(1000);

            // 4. DiscardOldestPolicy 演示
            demonstrateDiscardOldestPolicy();
            Thread.sleep(1000);

            // 5. 对比汇总
            compareAllPolicies();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("演示被中断", e);
        }

        logger.info("╔════════════════════════════════════════════════════════════════╗");
        logger.info("║                    演示完成                                      ║");
        logger.info("╚════════════════════════════════════════════════════════════════╝");
    }
}
