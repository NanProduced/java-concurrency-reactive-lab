package nan.tech.lab02.lock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Lock vs Synchronized 对比演示
 *
 * <p>【教学目标】</p>
 * <ol>
 *   <li>理解 synchronized 和 ReentrantLock 的异同</li>
 *   <li>掌握 ReentrantLock 的高级特性（可重入、公平锁、可中断、可超时）</li>
 *   <li>学会根据场景选择合适的同步机制</li>
 *   <li>理解性能权衡和使用场景</li>
 * </ol>
 *
 * <p>【核心对比】</p>
 * <pre>
 * ┌─────────────────┬──────────────────────┬──────────────────────┐
 * │     特性        │     synchronized     │    ReentrantLock     │
 * ├─────────────────┼──────────────────────┼──────────────────────┤
 * │ 使用便利性      │ ✅ 简洁，自动释放     │ ❌ 需手动释放         │
 * │ 可重入性        │ ✅ 支持               │ ✅ 支持              │
 * │ 公平/非公平     │ ❌ 仅非公平           │ ✅ 可选择            │
 * │ 可中断          │ ❌ 不支持             │ ✅ 支持              │
 * │ 可超时          │ ❌ 不支持             │ ✅ 支持              │
 * │ 条件变量        │ ❌ 单一 wait/notify   │ ✅ 多个 Condition    │
 * │ 性能（低竞争）  │ ✅ 稍快               │ ✅ 相当              │
 * │ 性能（高竞争）  │ ✅ 相当               │ ✅ 稍快              │
 * │ JVM 优化        │ ✅ 偏向锁、轻量级锁   │ ❌ 无特殊优化         │
 * └─────────────────┴──────────────────────┴──────────────────────┘
 * </pre>
 *
 * <p>【使用场景决策树】</p>
 * <pre>
 * 需要高级特性（可中断/超时/公平锁/多条件）？
 *   ├─ 是 → 使用 ReentrantLock
 *   └─ 否 → 简单场景？
 *       ├─ 是 → 使用 synchronized（推荐）
 *       └─ 否 → 性能敏感且高竞争？
 *           ├─ 是 → 测试对比后选择
 *           └─ 否 → 使用 synchronized（默认选择）
 * </pre>
 *
 * @author Nan
 * @since 2025-10-17
 */
public class LockVsSynchronizedDemo {

    private static final Logger logger = LoggerFactory.getLogger(LockVsSynchronizedDemo.class);

    /**
     * 主函数：演示 synchronized 和 ReentrantLock 的各种场景
     */
    public static void main(String[] args) throws InterruptedException {
        logger.info("=== Lock vs Synchronized 对比演示开始 ===\n");

        // 场景 1：基本功能对比
        logger.info(">>> 场景 1：基本计数器对比");
        demonstrateBasicComparison();

        // 场景 2：可重入性演示
        logger.info("\n>>> 场景 2：可重入性演示");
        demonstrateReentrant();

        // 场景 3：公平锁 vs 非公平锁
        logger.info("\n>>> 场景 3：公平锁 vs 非公平锁");
        demonstrateFairness();

        // 场景 4：可中断锁
        logger.info("\n>>> 场景 4：可中断锁演示");
        demonstrateInterruptible();

        // 场景 5：超时获取锁
        logger.info("\n>>> 场景 5：超时获取锁演示");
        demonstrateTryLock();

        logger.info("\n=== Lock vs Synchronized 对比演示结束 ===");
    }

    /**
     * 场景 1：基本功能对比
     * 演示最基本的计数器场景，对比两种实现方式
     */
    private static void demonstrateBasicComparison() throws InterruptedException {
        int threadCount = 50;
        int increments = 1000;

        // 1.1 使用 synchronized
        logger.info("1.1 使用 synchronized 的计数器:");
        SynchronizedCounter syncCounter = new SynchronizedCounter();
        runCounterTest(syncCounter, threadCount, increments);
        logger.info("  最终结果: {} (期望: {})", syncCounter.getCount(), threadCount * increments);

        // 1.2 使用 ReentrantLock
        logger.info("1.2 使用 ReentrantLock 的计数器:");
        LockCounter lockCounter = new LockCounter();
        runCounterTest(lockCounter, threadCount, increments);
        logger.info("  最终结果: {} (期望: {})", lockCounter.getCount(), threadCount * increments);

        logger.info("✅ 结论: 两种方式都能保证线程安全，结果一致");
    }

    /**
     * 场景 2：可重入性演示
     * 证明 synchronized 和 ReentrantLock 都支持可重入
     */
    private static void demonstrateReentrant() {
        // 2.1 synchronized 可重入
        logger.info("2.1 synchronized 可重入:");
        ReentrantDemo syncReentrant = new ReentrantDemo();
        syncReentrant.outerSynchronized();

        // 2.2 ReentrantLock 可重入
        logger.info("2.2 ReentrantLock 可重入:");
        ReentrantDemo lockReentrant = new ReentrantDemo();
        lockReentrant.outerLock();

        logger.info("✅ 结论: 两种方式都支持可重入，避免自己锁死自己");
    }

    /**
     * 场景 3：公平锁 vs 非公平锁
     * ReentrantLock 可选择公平/非公平，synchronized 只能非公平
     */
    private static void demonstrateFairness() throws InterruptedException {
        int threadCount = 10;

        // 3.1 非公平锁（默认）
        logger.info("3.1 非公平锁（性能更好，但可能饿死）:");
        ReentrantLock unfairLock = new ReentrantLock(false);
        runFairnessTest(unfairLock, threadCount);

        Thread.sleep(500); // 间隔

        // 3.2 公平锁
        logger.info("3.2 公平锁（保证公平，但性能稍差）:");
        ReentrantLock fairLock = new ReentrantLock(true);
        runFairnessTest(fairLock, threadCount);

        logger.info("✅ 结论: 非公平锁性能更好，公平锁防止线程饥饿");
        logger.info("   ⚠️  synchronized 只能是非公平锁");
    }

    /**
     * 场景 4：可中断锁
     * ReentrantLock 支持可中断，synchronized 不支持
     */
    private static void demonstrateInterruptible() throws InterruptedException {
        ReentrantLock lock = new ReentrantLock();

        // 线程 1：持有锁
        Thread holder = new Thread(() -> {
            lock.lock();
            try {
                logger.info("线程 1 获得锁，持有 3 秒...");
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                logger.warn("线程 1 被中断");
            } finally {
                lock.unlock();
            }
        }, "Holder-Thread");

        // 线程 2：尝试可中断获取锁
        Thread interruptible = new Thread(() -> {
            try {
                logger.info("线程 2 尝试获取锁（可中断）...");
                lock.lockInterruptibly(); // 可中断地获取锁
                try {
                    logger.info("线程 2 获得锁");
                } finally {
                    lock.unlock();
                }
            } catch (InterruptedException e) {
                logger.warn("线程 2 在等待锁时被中断 ✅");
            }
        }, "Interruptible-Thread");

        holder.start();
        Thread.sleep(100); // 确保线程 1 先获得锁
        interruptible.start();
        Thread.sleep(100); // 确保线程 2 开始等待

        // 中断线程 2
        logger.info("主线程中断线程 2...");
        interruptible.interrupt();

        holder.join();
        interruptible.join();

        logger.info("✅ 结论: ReentrantLock.lockInterruptibly() 可响应中断");
        logger.info("   ⚠️  synchronized 不支持中断响应，会一直等待");
    }

    /**
     * 场景 5：超时获取锁
     * ReentrantLock 支持超时，synchronized 不支持
     */
    private static void demonstrateTryLock() throws InterruptedException {
        ReentrantLock lock = new ReentrantLock();

        // 线程 1：持有锁
        Thread holder = new Thread(() -> {
            lock.lock();
            try {
                logger.info("线程 1 获得锁，持有 2 秒...");
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                logger.warn("线程 1 被中断");
            } finally {
                lock.unlock();
                logger.info("线程 1 释放锁");
            }
        }, "Holder-Thread");

        // 线程 2：尝试超时获取锁
        Thread tryLocker = new Thread(() -> {
            try {
                logger.info("线程 2 尝试获取锁（超时 500ms）...");
                boolean acquired = lock.tryLock(500, TimeUnit.MILLISECONDS);
                if (acquired) {
                    try {
                        logger.info("线程 2 获得锁");
                    } finally {
                        lock.unlock();
                    }
                } else {
                    logger.warn("线程 2 获取锁超时 ⏰");
                }
            } catch (InterruptedException e) {
                logger.warn("线程 2 被中断");
            }
        }, "TryLock-Thread");

        holder.start();
        Thread.sleep(100); // 确保线程 1 先获得锁
        tryLocker.start();

        holder.join();
        tryLocker.join();

        logger.info("✅ 结论: ReentrantLock.tryLock(timeout) 可避免无限期等待");
        logger.info("   ⚠️  synchronized 不支持超时，可能永久阻塞");
    }

    // ==================== 辅助方法和内部类 ====================

    /**
     * 运行计数器测试
     */
    private static void runCounterTest(Counter counter, int threadCount, int increments)
            throws InterruptedException {
        long startTime = System.nanoTime();
        Thread[] threads = new Thread[threadCount];

        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < increments; j++) {
                    counter.increment();
                }
            });
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        long duration = System.nanoTime() - startTime;
        logger.info("  耗时: {} ms", duration / 1_000_000);
    }

    /**
     * 公平性测试
     */
    private static void runFairnessTest(ReentrantLock lock, int threadCount)
            throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            new Thread(() -> {
                lock.lock();
                try {
                    logger.info("  线程-{} 获得锁", threadId);
                    Thread.sleep(50); // 模拟工作
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    lock.unlock();
                    latch.countDown();
                }
            }, "Thread-" + i).start();
        }

        latch.await();
    }

    // ==================== 计数器接口和实现 ====================

    /**
     * 计数器接口
     */
    interface Counter {
        void increment();
        int getCount();
    }

    /**
     * 使用 synchronized 的计数器
     */
    static class SynchronizedCounter implements Counter {
        private int count = 0;

        @Override
        public synchronized void increment() {
            count++;
        }

        @Override
        public synchronized int getCount() {
            return count;
        }
    }

    /**
     * 使用 ReentrantLock 的计数器
     */
    static class LockCounter implements Counter {
        private final Lock lock = new ReentrantLock();
        private int count = 0;

        @Override
        public void increment() {
            lock.lock();
            try {
                count++;
            } finally {
                lock.unlock(); // 必须在 finally 中释放锁
            }
        }

        @Override
        public int getCount() {
            lock.lock();
            try {
                return count;
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * 可重入性演示类
     */
    static class ReentrantDemo {
        private final Lock lock = new ReentrantLock();
        private int depth = 0;

        /**
         * synchronized 可重入演示
         */
        public synchronized void outerSynchronized() {
            logger.info("  进入 outerSynchronized");
            innerSynchronized();
            logger.info("  退出 outerSynchronized");
        }

        private synchronized void innerSynchronized() {
            logger.info("    进入 innerSynchronized（同一把锁，可重入）");
        }

        /**
         * ReentrantLock 可重入演示
         */
        public void outerLock() {
            lock.lock();
            try {
                depth++;
                logger.info("  进入 outerLock，深度: {}", depth);
                innerLock();
                logger.info("  退出 outerLock");
            } finally {
                depth--;
                lock.unlock();
            }
        }

        private void innerLock() {
            lock.lock(); // 同一线程可再次获取锁
            try {
                depth++;
                logger.info("    进入 innerLock，深度: {}（同一把锁，可重入）", depth);
            } finally {
                depth--;
                lock.unlock();
            }
        }
    }
}
