package nan.tech.lab02.semaphore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Semaphore（信号量）演示
 *
 * <p>【教学目标】</p>
 * <ol>
 *   <li>理解信号量的工作原理（许可证机制）</li>
 *   <li>掌握资源池限流的实现方式</li>
 *   <li>理解公平策略 vs 非公平策略的区别</li>
 *   <li>学会使用信号量控制并发访问</li>
 * </ol>
 *
 * <p>【核心概念】</p>
 * <pre>
 * Semaphore = 停车场管理
 * ┌─────────────────────────────────┐
 * │  停车场（资源池）                │
 * │  ┌──┐ ┌──┐ ┌──┐ ← 3 个车位      │
 * │  │🚗│ │🚗│ │🚗│   （permits=3）  │
 * │  └──┘ └──┘ └──┘                │
 * │                                 │
 * │  等待区（阻塞队列）              │
 * │  🚗 🚗 🚗 ← 等待进入            │
 * └─────────────────────────────────┘
 *
 * acquire() → 获取许可（进入停车场）
 * release() → 释放许可（离开停车场）
 * </pre>
 *
 * <p>【使用场景】</p>
 * <ul>
 *   <li>数据库连接池限流（最多 N 个连接）</li>
 *   <li>API 限流（QPS 控制）</li>
 *   <li>资源池管理（线程池、对象池）</li>
 *   <li>流量控制（限制并发请求数）</li>
 * </ul>
 *
 * @author nan
 * @since 2025-10-17
 */
public class SemaphoreDemo {

    private static final Logger logger = LoggerFactory.getLogger(SemaphoreDemo.class);

    public static void main(String[] args) throws InterruptedException {
        logger.info("=== Semaphore 演示开始 ===\n");

        // 场景 1：资源池限流
        logger.info(">>> 场景 1：数据库连接池（最多 3 个连接）");
        demonstrateResourcePool();

        // 场景 2：公平 vs 非公平
        logger.info("\n>>> 场景 2：公平策略 vs 非公平策略");
        demonstrateFairness();

        // 场景 3：tryAcquire 超时
        logger.info("\n>>> 场景 3：tryAcquire 超时机制");
        demonstrateTryAcquire();

        logger.info("\n=== Semaphore 演示结束 ===");
    }

    /**
     * 场景 1：数据库连接池限流
     * 模拟最多 3 个并发连接，其他请求需要等待
     */
    private static void demonstrateResourcePool() throws InterruptedException {
        // 创建 3 个许可的信号量（模拟 3 个数据库连接）
        Semaphore dbPool = new Semaphore(3);
        logger.info("初始化数据库连接池，最大连接数: 3\n");

        // 启动 8 个线程竞争 3 个连接
        for (int i = 1; i <= 8; i++) {
            final int userId = i;
            new Thread(() -> {
                try {
                    logger.info("用户-{} 请求数据库连接...", userId);
                    dbPool.acquire(); // 获取许可
                    logger.info("✅ 用户-{} 获得连接，开始查询", userId);

                    // 模拟数据库操作
                    Thread.sleep(1000);

                    logger.info("✅ 用户-{} 完成查询，释放连接", userId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    dbPool.release(); // 释放许可
                }
            }, "User-" + i).start();

            Thread.sleep(100); // 错开启动时间
        }

        Thread.sleep(5000); // 等待所有线程完成
        logger.info("\n✅ 结论: Semaphore 成功限制并发数为 3");
    }

    /**
     * 场景 2：公平 vs 非公平策略
     */
    private static void demonstrateFairness() throws InterruptedException {
        // 2.1 非公平信号量（默认）
        logger.info("2.1 非公平信号量（性能更好）:");
        Semaphore unfairSemaphore = new Semaphore(1, false);
        testFairness(unfairSemaphore, "非公平");

        Thread.sleep(500);

        // 2.2 公平信号量
        logger.info("\n2.2 公平信号量（先到先得）:");
        Semaphore fairSemaphore = new Semaphore(1, true);
        testFairness(fairSemaphore, "公平");

        logger.info("\n✅ 结论: 非公平策略性能更好，公平策略防止饥饿");
    }

    /**
     * 公平性测试辅助方法
     */
    private static void testFairness(Semaphore semaphore, String type) throws InterruptedException {
        for (int i = 1; i <= 5; i++) {
            final int id = i;
            new Thread(() -> {
                try {
                    semaphore.acquire();
                    logger.info("  [{}] 线程-{} 获得许可", type, id);
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    semaphore.release();
                }
            }, "Thread-" + i).start();
        }
        Thread.sleep(500);
    }

    /**
     * 场景 3：tryAcquire 超时机制
     * 避免无限等待，设置超时时间
     */
    private static void demonstrateTryAcquire() throws InterruptedException {
        Semaphore semaphore = new Semaphore(1);

        // 线程 1：持有许可 2 秒
        Thread holder = new Thread(() -> {
            try {
                semaphore.acquire();
                logger.info("线程 1 获得许可，持有 2 秒");
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                semaphore.release();
                logger.info("线程 1 释放许可");
            }
        }, "Holder");

        // 线程 2：尝试获取许可（超时 500ms）
        Thread tryThread = new Thread(() -> {
            try {
                Thread.sleep(100); // 确保线程 1 先获得
                logger.info("线程 2 尝试获取许可（超时 500ms）...");
                boolean acquired = semaphore.tryAcquire(500, TimeUnit.MILLISECONDS);

                if (acquired) {
                    try {
                        logger.info("✅ 线程 2 成功获得许可");
                    } finally {
                        semaphore.release();
                    }
                } else {
                    logger.warn("⏰ 线程 2 获取超时，放弃等待");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "TryThread");

        holder.start();
        tryThread.start();

        holder.join();
        tryThread.join();

        logger.info("\n✅ 结论: tryAcquire 可避免无限期阻塞");
    }

    /**
     * 实际应用示例：API 限流器
     */
    static class ApiRateLimiter {
        private final Semaphore semaphore;
        private final int maxConcurrent;

        public ApiRateLimiter(int maxConcurrent) {
            this.maxConcurrent = maxConcurrent;
            this.semaphore = new Semaphore(maxConcurrent);
        }

        /**
         * 执行 API 调用（带限流）
         */
        public void callApi(String apiName, Runnable task) {
            try {
                // 尝试获取许可（超时 1 秒）
                if (semaphore.tryAcquire(1, TimeUnit.SECONDS)) {
                    try {
                        logger.info("执行 API: {}, 当前并发: {}/{}",
                                apiName,
                                maxConcurrent - semaphore.availablePermits(),
                                maxConcurrent);
                        task.run();
                    } finally {
                        semaphore.release();
                    }
                } else {
                    logger.warn("API 限流: {} 超过最大并发数 {}", apiName, maxConcurrent);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("API 调用被中断: {}", apiName);
            }
        }
    }
}
