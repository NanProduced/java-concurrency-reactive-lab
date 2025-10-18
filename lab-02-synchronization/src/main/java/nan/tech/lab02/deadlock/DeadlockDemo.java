package nan.tech.lab02.deadlock;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 死锁演示与避免策略
 *
 * <p>【教学目标】</p>
 * <ol>
 *   <li>理解死锁的 4 个必要条件</li>
 *   <li>掌握 4 种常见的死锁场景</li>
 *   <li>学会诊断和检测死锁</li>
 *   <li>掌握避免死锁的策略</li>
 * </ol>
 *
 * <p>【死锁的 4 个必要条件】</p>
 * <pre>
 * 1. 互斥条件：资源不能被共享，只能由一个线程使用
 * 2. 持有并等待：线程持有资源的同时，等待获取其他资源
 * 3. 不可抢占：资源不能被强制剥夺，只能主动释放
 * 4. 循环等待：存在资源的循环等待链
 *
 * 破坏任一条件即可避免死锁！
 * </pre>
 *
 * <p>【死锁场景分类】</p>
 * <pre>
 * ┌──────────────────────────────────────┐
 * │ 场景 1：循环等待（经典死锁）          │
 * │   A 持有锁1，等待锁2                 │
 * │   B 持有锁2，等待锁1                 │
 * │   → A ←                              │
 * │   ↓   ↑                              │
 * │   B ←                                │
 * ├──────────────────────────────────────┤
 * │ 场景 2：资源竞争（多个锁）            │
 * │   多个线程以不同顺序获取多个锁        │
 * ├──────────────────────────────────────┤
 * │ 场景 3：顺序死锁（隐藏的循环）        │
 * │   转账: A→B 和 B→A 同时发生         │
 * ├──────────────────────────────────────┤
 * │ 场景 4：动态死锁（运行时产生）        │
 * │   根据条件动态获取锁，顺序不一致      │
 * └──────────────────────────────────────┘
 * </pre>
 *
 * <p>【避免策略】</p>
 * <ul>
 *   <li>固定锁顺序：所有线程按相同顺序获取锁</li>
 *   <li>尝试获取锁：使用 tryLock() 避免无限等待</li>
 *   <li>超时机制：设置获取锁的超时时间</li>
 *   <li>死锁检测：使用 JConsole/JStack 检测</li>
 * </ul>
 *
 * @author nan
 * @since 2025-10-17
 */
public class DeadlockDemo {

    private static final Logger logger = LoggerFactory.getLogger(DeadlockDemo.class);

    public static void main(String[] args) throws InterruptedException {
        logger.info("=== 死锁演示开始 ===\n");

        // 场景 1：经典循环等待死锁
        logger.info(">>> 场景 1：经典循环等待死锁（2秒后发生）");
        // demonstrateClassicDeadlock(); // 注释掉避免真的死锁

        logger.info(">>> 场景 1：死锁已被注释避免演示阻塞");
        logger.info("说明: 两个线程互相等待对方持有的锁");
        logger.info("线程 A: 持有 lock1，等待 lock2");
        logger.info("线程 B: 持有 lock2，等待 lock1\n");

        // 场景 2：正确的锁顺序（避免死锁）
        logger.info(">>> 场景 2：正确的锁顺序（避免死锁）");
        demonstrateCorrectLockOrder();

        // 场景 3：使用 tryLock 避免死锁
        logger.info("\n>>> 场景 3：使用 tryLock 避免死锁");
        demonstrateTryLockAvoidance();

        // 场景 4：转账死锁（顺序死锁）
        logger.info("\n>>> 场景 4：转账场景的死锁与解决");
        demonstrateTransferDeadlock();

        logger.info("\n=== 死锁演示结束 ===");
        logger.info("\n💡 提示：使用 jstack <pid> 可以检测死锁");
        logger.info("💡 提示：使用 JConsole 可以可视化检测死锁");
    }

    /**
     * 场景 1：经典循环等待死锁
     * ⚠️ 这会导致真正的死锁，仅用于教学演示
     */
    private static void demonstrateClassicDeadlock() throws InterruptedException {
        final Object lock1 = new Object();
        final Object lock2 = new Object();

        // 线程 A：先获取 lock1，再获取 lock2
        Thread threadA = new Thread(() -> {
            synchronized (lock1) {
                logger.info("线程 A：获得 lock1，尝试获取 lock2...");
                try {
                    Thread.sleep(100); // 确保另一个线程也获取到第一个锁
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                synchronized (lock2) {
                    logger.info("线程 A：获得 lock2");
                }
            }
        }, "Thread-A");

        // 线程 B：先获取 lock2，再获取 lock1
        Thread threadB = new Thread(() -> {
            synchronized (lock2) {
                logger.info("线程 B：获得 lock2，尝试获取 lock1...");
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                synchronized (lock1) {
                    logger.info("线程 B：获得 lock1");
                }
            }
        }, "Thread-B");

        threadA.start();
        threadB.start();

        Thread.sleep(2000);
        logger.warn("⚠️ 死锁已发生！线程 A 和 B 互相等待");

        // 不 join，避免主线程也阻塞
    }

    /**
     * 场景 2：正确的锁顺序（避免死锁）
     * 所有线程按相同顺序获取锁
     */
    private static void demonstrateCorrectLockOrder() throws InterruptedException {
        final Object lock1 = new Object();
        final Object lock2 = new Object();

        // 线程 A：按顺序获取锁
        Thread threadA = new Thread(() -> {
            synchronized (lock1) { // 先 lock1
                logger.info("线程 A：获得 lock1");
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                synchronized (lock2) { // 后 lock2
                    logger.info("线程 A：获得 lock2");
                }
            }
            logger.info("✅ 线程 A：完成");
        }, "Thread-A");

        // 线程 B：也按相同顺序获取锁
        Thread threadB = new Thread(() -> {
            synchronized (lock1) { // 先 lock1（同样顺序）
                logger.info("线程 B：获得 lock1");
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                synchronized (lock2) { // 后 lock2
                    logger.info("线程 B：获得 lock2");
                }
            }
            logger.info("✅ 线程 B：完成");
        }, "Thread-B");

        threadA.start();
        threadB.start();

        threadA.join();
        threadB.join();

        logger.info("✅ 结论：固定锁顺序可以避免死锁");
    }

    /**
     * 场景 3：使用 tryLock 避免死锁
     * 尝试获取锁失败时，释放已持有的锁并重试
     */
    private static void demonstrateTryLockAvoidance() throws InterruptedException {
        final Lock lock1 = new ReentrantLock();
        final Lock lock2 = new ReentrantLock();

        // 线程 A
        Thread threadA = new Thread(() -> {
            while (true) {
                if (lock1.tryLock()) {
                    try {
                        logger.info("线程 A：获得 lock1");
                        Thread.sleep(100);

                        if (lock2.tryLock()) {
                            try {
                                logger.info("线程 A：获得 lock2");
                                logger.info("✅ 线程 A：完成任务");
                                return; // 成功，退出
                            } finally {
                                lock2.unlock();
                            }
                        } else {
                            logger.warn("线程 A：无法获取 lock2，释放 lock1 并重试");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    } finally {
                        lock1.unlock();
                    }
                }

                // 重试前短暂休息
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, "Thread-A");

        // 线程 B（反向顺序）
        Thread threadB = new Thread(() -> {
            while (true) {
                if (lock2.tryLock()) {
                    try {
                        logger.info("线程 B：获得 lock2");
                        Thread.sleep(100);

                        if (lock1.tryLock()) {
                            try {
                                logger.info("线程 B：获得 lock1");
                                logger.info("✅ 线程 B：完成任务");
                                return; // 成功，退出
                            } finally {
                                lock1.unlock();
                            }
                        } else {
                            logger.warn("线程 B：无法获取 lock1，释放 lock2 并重试");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    } finally {
                        lock2.unlock();
                    }
                }

                // 重试前短暂休息
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, "Thread-B");

        threadA.start();
        threadB.start();

        threadA.join();
        threadB.join();

        logger.info("✅ 结论：tryLock() 可以避免死锁，但可能需要多次重试");
    }

    /**
     * 场景 4：转账死锁与解决方案
     * 展示转账场景中的死锁和如何通过锁排序避免
     */
    private static void demonstrateTransferDeadlock() throws InterruptedException {
        Account accountA = new Account("A", 1000);
        Account accountB = new Account("B", 1000);

        logger.info("初始状态: A={}, B={}\n", accountA.getBalance(), accountB.getBalance());

        // 线程 1：A 转账给 B
        Thread t1 = new Thread(() -> {
            accountA.transferTo(accountB, 100);
        }, "Transfer-A-to-B");

        // 线程 2：B 转账给 A（反向）
        Thread t2 = new Thread(() -> {
            accountB.transferTo(accountA, 200);
        }, "Transfer-B-to-A");

        t1.start();
        t2.start();

        t1.join();
        t2.join();

        logger.info("\n最终状态: A={}, B={}", accountA.getBalance(), accountB.getBalance());
        logger.info("✅ 结论：使用对象哈希值排序锁，避免转账死锁");
    }

    /**
     * 银行账户类（使用锁排序避免死锁）
     */
    static class Account {
        private final String name;
        @Getter
        private int balance;
        private final Lock lock = new ReentrantLock();

        public Account(String name, int balance) {
            this.name = name;
            this.balance = balance;
        }

        /**
         * 转账方法（使用全局锁顺序避免死锁）
         */
        public void transferTo(Account target, int amount) {
            // 关键：按对象哈希值排序，确保锁的获取顺序一致
            Account first = this.hashCode() < target.hashCode() ? this : target;
            Account second = this == first ? target : this;

            first.lock.lock();
            try {
                logger.info("{} 获得第一把锁", first.name);
                Thread.sleep(100); // 模拟处理时间

                second.lock.lock();
                try {
                    logger.info("{} 获得第二把锁", second.name);

                    // 执行转账
                    if (this.balance >= amount) {
                        this.balance -= amount;
                        target.balance += amount;
                        logger.info("✅ 转账成功：{} → {}, 金额: {}",
                                this.name, target.name, amount);
                    } else {
                        logger.warn("⚠️ 余额不足：{} 无法转账 {}", this.name, amount);
                    }
                } finally {
                    second.lock.unlock();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                first.lock.unlock();
            }
        }
    }

    /**
     * 死锁检测工具方法
     * 可用于实际项目中定期检测死锁
     */
    static class DeadlockDetector {
        /**
         * 检测死锁
         * @return 是否存在死锁
         */
        public static boolean detectDeadlock() {
            java.lang.management.ThreadMXBean bean =
                    java.lang.management.ManagementFactory.getThreadMXBean();

            long[] threadIds = bean.findDeadlockedThreads();
            if (threadIds != null && threadIds.length > 0) {
                logger.error("检测到死锁！涉及线程数: {}", threadIds.length);
                for (long threadId : threadIds) {
                    java.lang.management.ThreadInfo info =
                            bean.getThreadInfo(threadId);
                    logger.error("死锁线程: {} (ID={}), 状态: {}",
                            info.getThreadName(),
                            info.getThreadId(),
                            info.getThreadState());
                }
                return true;
            }
            return false;
        }
    }
}
