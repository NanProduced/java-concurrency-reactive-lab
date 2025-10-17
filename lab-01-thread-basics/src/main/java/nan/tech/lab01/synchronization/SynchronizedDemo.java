package nan.tech.lab01.synchronization;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * synchronized 同步机制完整演示：对比式教学 (WITH vs WITHOUT synchronized)
 *
 * 核心问题：为什么需要 synchronized？
 *   ├─ 没有 synchronized: 多线程共享变量导致数据竞争，结果错误
 *   ├─ 没有 synchronized: 可能发生死锁、饥饿等同步问题
 *   └─ synchronized 解决: 通过监视器锁保证原子性和可见性
 *
 * synchronized 能提供：
 *   ✅ 原子性 (Atomicity): 临界区内的操作不被打断
 *   ✅ 可见性 (Visibility): 解锁时对所有线程可见
 *   ✅ 有序性 (Ordering): 内存屏障保证操作顺序
 *   ⚠️  性能成本：锁竞争导致性能下降
 *
 * 学习路径：
 *   演示 1 & 2: 对比演示（无同步 vs WITH synchronized）→ 理解竞态条件
 *   演示 3: 多种 synchronized 形式 → 理解灵活使用
 *   演示 4 & 5: 死锁演示 → 理解死锁成因和解决
 *   演示 6: Happens-Before 规则 → 理解 synchronized 如何工作
 */
public class SynchronizedDemo {

    private static final Logger log = LoggerFactory.getLogger(SynchronizedDemo.class);

    // ============================================
    // 演示 1 & 2: 竞态条件 (WITHOUT vs WITH synchronized)
    // ============================================

    /**
     * 演示 1：❌ WITHOUT synchronized - 竞态条件导致数据丢失
     *
     * 问题：count 是普通字段，多个线程同时修改会导致数据丢失
     * 原因：count++ 分解为 (1) 读、(2) 加、(3) 写三个步骤
     *       多个线程可能在这三个步骤之间交织，导致某些增量被覆盖
     * 结果：最终的值会远小于期望值
     */
    private static class UnsafeCounter {
        private int count = 0;  // ❌ 普通字段，无同步

        public void increment() {
            count++;  // ❌ 数据竞争：三个步骤可能被打断
        }

        public int get() {
            return count;
        }
    }

    public static void demonstrateRaceCondition() throws InterruptedException {
        log.info("════════════════════════════════════════");
        log.info("演示 1: ❌ WITHOUT synchronized - 竞态条件");
        log.info("════════════════════════════════════════");

        UnsafeCounter counter = new UnsafeCounter();
        int threadCount = 10;
        int incrementsPerThread = 10000;

        Thread[] threads = new Thread[threadCount];
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < incrementsPerThread; j++) {
                    counter.increment();  // 竞态条件
                }
            });
        }

        for (Thread t : threads) {
            t.start();
        }

        for (Thread t : threads) {
            t.join();
        }

        long duration = System.currentTimeMillis() - startTime;
        int expected = threadCount * incrementsPerThread;
        int actual = counter.get();
        int lost = expected - actual;

        log.info("预期值: {}", expected);
        log.info("实际值: {}", actual);
        log.info("数据丢失: {} ({} %)", lost, (100.0 * lost / expected));
        log.info("耗时: {} ms", duration);

        log.info("⚠️  演示成功：证明了竞态条件导致数据丢失");
        log.info("  原因：count++ 的三个步骤可能被其他线程打断");
        log.info("");
    }

    /**
     * 演示 2：✅ WITH synchronized - 竞态条件被消除
     *
     * 解决方案：使用 synchronized 关键字保护临界区
     * 结果：每个线程执行 count++ 时都获得独占锁，结果正确
     * 原理：synchronized 通过监视器锁保证同一时间只有一个线程进入临界区
     */
    private static class SyncCounter {
        private int count = 0;

        public synchronized void increment() {
            count++;  // ✅ synchronized 保护：同一时间只有一个线程执行
        }

        public int get() {
            return count;
        }
    }

    public static void demonstrateRaceConditionSolution() throws InterruptedException {
        log.info("════════════════════════════════════════");
        log.info("演示 2: ✅ WITH synchronized - 竞态条件解决");
        log.info("════════════════════════════════════════");

        SyncCounter counter = new SyncCounter();
        int threadCount = 10;
        int incrementsPerThread = 10000;

        Thread[] threads = new Thread[threadCount];
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < incrementsPerThread; j++) {
                    counter.increment();
                }
            });
        }

        for (Thread t : threads) {
            t.start();
        }

        for (Thread t : threads) {
            t.join();
        }

        long duration = System.currentTimeMillis() - startTime;
        int expected = threadCount * incrementsPerThread;
        int actual = counter.get();

        log.info("预期值: {}", expected);
        log.info("实际值: {}", actual);
        log.info("数据丢失: {} (0 %)", expected - actual);
        log.info("耗时: {} ms", duration);

        log.info("✓ 演示成功：synchronized 保证了结果正确");
        log.info("  规则：获取锁 Happens-Before 释放锁");
        log.info("  规则：释放锁 Happens-Before 后续获取同一个锁");
        log.info("");
    }

    // ============================================
    // 演示 3: synchronized 的多种形式
    // ============================================

    /**
     * 演示 3：synchronized 的多种形式
     *
     * 形式 1：synchronized 方法
     *   - 加锁对象是 this（实例方法）或 class（静态方法）
     *   - 优点：简洁、易理解
     *   - 缺点：粒度粗，整个方法被保护
     *
     * 形式 2：synchronized 块
     *   - 加锁对象可以是任意对象
     *   - 优点：粒度细，只保护必要的代码段
     *   - 缺点：需要手动指定锁对象，代码略复杂
     *
     * 形式 3：监视器模式
     *   - 使用不同的锁对象保护不同的数据
     *   - 优点：支持细粒度锁，提高并发度
     *   - 缺点：需要小心设计以避免死锁
     */
    public static void demonstrateSynchronizationForms() throws InterruptedException {
        log.info("════════════════════════════════════════");
        log.info("演示 3: synchronized 的多种形式");
        log.info("════════════════════════════════════════");

        // 形式 1：synchronized 方法
        class Form1Counter {
            private int count = 0;

            public synchronized void increment() {
                count++;  // 形式 1：整个方法被锁保护
            }

            public synchronized int get() {
                return count;
            }
        }

        // 形式 2：synchronized 块
        class Form2Counter {
            private int count = 0;

            public void increment() {
                synchronized (this) {  // 形式 2：只有这个块被锁保护
                    count++;
                }
            }

            public int get() {
                synchronized (this) {
                    return count;
                }
            }
        }

        // 形式 3：监视器模式（细粒度锁）
        class Form3Counter {
            private int count1 = 0;
            private int count2 = 0;
            private final Object lock1 = new Object();
            private final Object lock2 = new Object();

            public void incrementCount1() {
                synchronized (lock1) {  // 形式 3：不同的锁保护不同的数据
                    count1++;
                }
            }

            public void incrementCount2() {
                synchronized (lock2) {  // 可以并发执行，因为用了不同的锁
                    count2++;
                }
            }

            public int getSum() {
                synchronized (lock1) {
                    synchronized (lock2) {
                        return count1 + count2;
                    }
                }
            }
        }

        log.info("✓ 三种形式的特点：");
        log.info("  形式 1（synchronized 方法）: 简洁，适合保护整个方法");
        log.info("  形式 2（synchronized 块）: 灵活，可以选择加锁粒度");
        log.info("  形式 3（监视器模式）: 精细，支持不同数据用不同锁");
        log.info("  选择：根据实际需求平衡简洁性和并发度");
        log.info("");
    }

    // ============================================
    // 演示 4 & 5: 死锁 (问题演示和解决方案)
    // ============================================

    /**
     * 演示 4：❌ 死锁问题
     *
     * 死锁成因：
     *   (1) 多个线程竞争多个锁资源
     *   (2) 锁的获取顺序不一致
     *   (3) 线程之间互相等待对方的锁
     *   (4) 没有超时或其他打破循环的机制
     *
     * 场景：
     *   线程 A：获得锁 L1，尝试获得锁 L2 → 等待 L2
     *   线程 B：获得锁 L2，尝试获得锁 L1 → 等待 L1
     *   结果：A 和 B 互相等待，形成死锁
     */
    public static void demonstrateDeadlock() throws InterruptedException {
        log.info("════════════════════════════════════════");
        log.info("演示 4: ❌ 死锁问题");
        log.info("════════════════════════════════════════");

        Object lock1 = new Object();
        Object lock2 = new Object();
        final boolean[] deadlockOccurred = {false};

        Thread threadA = new Thread(() -> {
            synchronized (lock1) {
                log.info("[Thread A] 获得 lock1");
                try {
                    Thread.sleep(50);  // 给 Thread B 机会获得 lock2
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                log.info("[Thread A] 尝试获得 lock2...");
                synchronized (lock2) {  // 这里可能永远等待
                    log.info("[Thread A] 获得 lock2");
                }
            }
        }, "DeadlockThreadA");

        Thread threadB = new Thread(() -> {
            synchronized (lock2) {
                log.info("[Thread B] 获得 lock2");
                try {
                    Thread.sleep(50);  // 给 Thread A 机会获得 lock1
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                log.info("[Thread B] 尝试获得 lock1...");
                synchronized (lock1) {  // 这里可能永远等待
                    log.info("[Thread B] 获得 lock1");
                }
            }
        }, "DeadlockThreadB");

        // 将演示线程标记为守护线程，避免死锁演示阻塞 JVM 退出。
        threadA.setDaemon(true);
        threadB.setDaemon(true);
        threadA.start();
        threadB.start();

        // 等待最多 2 秒，看是否发生死锁
        threadA.join(2000);
        threadB.join(2000);

        if (threadA.isAlive() || threadB.isAlive()) {
            log.warn("⚠️  检测到死锁！");
            log.warn("  Thread A 持有 lock1，等待 lock2");
            log.warn("  Thread B 持有 lock2，等待 lock1");
            log.warn("  两个线程互相等待，形成循环");
            deadlockOccurred[0] = true;

            // interrupt() 无法打破监视器死锁，这里通过日志提醒学习者。
            log.warn("  调用 interrupt() 无法打破监视器死锁，线程会一直阻塞。");
            log.warn("  通过将演示线程设为守护线程并在结尾调用 System.exit() 来确保程序可以退出。");
        } else {
            log.info("ℹ️  这次没有发生死锁（运气好）");
        }

        log.info("");
    }

    /**
     * 演示 5：✅ 死锁解决方案
     *
     * 解决方案：
     *   (1) 固定锁的获取顺序
     *   (2) 使用 tryLock() 超时机制
     *   (3) 使用 java.util.concurrent.locks 提供的高级工具
     *
     * 这里演示最简单的方案：固定锁获取顺序
     */
    public static void demonstrateDeadlockSolution() throws InterruptedException {
        log.info("════════════════════════════════════════");
        log.info("演示 5: ✅ 死锁解决方案（固定锁顺序）");
        log.info("════════════════════════════════════════");

        Object lock1 = new Object();
        Object lock2 = new Object();

        Thread threadA = new Thread(() -> {
            // 都按照相同的顺序获得锁：先 lock1，再 lock2
            synchronized (lock1) {
                log.info("[Thread A] 获得 lock1");
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                synchronized (lock2) {
                    log.info("[Thread A] 获得 lock2");
                }
            }
        }, "SolutionThreadA");

        Thread threadB = new Thread(() -> {
            // 同样的顺序：先 lock1，再 lock2
            synchronized (lock1) {
                log.info("[Thread B] 获得 lock1");
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                synchronized (lock2) {
                    log.info("[Thread B] 获得 lock2");
                }
            }
        }, "SolutionThreadB");

        threadA.start();
        threadB.start();

        threadA.join();
        threadB.join();

        log.info("✓ 演示成功：两个线程都正常完成");
        log.info("  关键：两个线程都按照相同的顺序获得锁（先 lock1，再 lock2）");
        log.info("  结果：即使竞争，也不会形成循环等待");
        log.info("  最佳实践：");
        log.info("    (1) 简化设计，尽量避免多个锁");
        log.info("    (2) 如果必须多个锁，固定获取顺序");
        log.info("    (3) 使用超时机制 (tryLock with timeout)");
        log.info("    (4) 考虑使用并发集合而不是手动同步");
        log.info("");
    }

    // ============================================
    // 演示 6: Happens-Before 规则
    // ============================================

    /**
     * 演示 6：synchronized 的 Happens-Before 规则
     *
     * 规则：
     *   (1) 获取锁 Happens-Before 解锁
     *   (2) 解锁 Happens-Before 后续获取同一个锁
     *   (3) 通过传递性，保证内存可见性
     *
     * 实际含义：
     *   线程 A 在临界区内的所有修改，对线程 B 中后续的代码可见
     */
    public static void demonstrateHappensBeforeRule() throws InterruptedException {
        log.info("════════════════════════════════════════");
        log.info("演示 6: synchronized 的 Happens-Before 规则");
        log.info("════════════════════════════════════════");

        class SharedData {
            private int x = 0;
            private int y = 0;
        }

        SharedData data = new SharedData();
        Object lock = new Object();
        final int[] observedX = {-1};
        final int[] observedY = {-1};
        // 用 barrier 确保顺序：Writer 先写入，Reader 再读取
        // 这样能清晰地演示 Happens-Before 规则的效果
        final boolean[] writerDone = {false};

        Thread writer = new Thread(() -> {
            try {
                Thread.sleep(50);  // 让 Reader 线程启动
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            synchronized (lock) {
                data.x = 42;       // 操作 A
                data.y = 100;      // 操作 B
                log.info("[Writer] 写入：x=42, y=100");
                writerDone[0] = true;  // 标记写入完成
            }  // 这里释放锁，建立 Happens-Before 关系
        }, "HBWriter");

        Thread reader = new Thread(() -> {
            // 等待 Writer 完成写入后再读取
            while (!writerDone[0]) {
                Thread.yield();
            }
            // 等待 Writer 真正释放锁（通过短暂延迟确保）
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            synchronized (lock) {  // 这里获得锁，建立 Happens-Before 关系
                observedX[0] = data.x;  // 读取 A
                observedY[0] = data.y;  // 读取 B
                log.info("[Reader] 读取：x={}, y={}", observedX[0], observedY[0]);
            }
        }, "HBReader");

        writer.start();
        reader.start();

        writer.join();
        reader.join();

        if (observedX[0] == 42 && observedY[0] == 100) {
            log.info("✓ Happens-Before 规则保证：");
            log.info("  解锁 Happens-Before 后续获取同一个锁");
            log.info("  因此 Writer 的所有修改对 Reader 可见");
            log.info("  这是 synchronized 提供的内存可见性保证");
        } else {
            log.error("❌ 不应该发生：Reader 读到的值是 x={}, y={}", observedX[0], observedY[0]);
        }
        log.info("");
    }

    // ============================================
    // main：按顺序运行所有演示
    // ============================================

    public static void main(String[] args) throws InterruptedException {
        log.info("═════════════════════════════════════════════════════════════");
        log.info("Java synchronized 关键字完整演示");
        log.info("教学目标：理解为什么需要 synchronized，以及如何安全地使用");
        log.info("═════════════════════════════════════════════════════════════");
        log.info("");

        // 演示对比：竞态条件
        demonstrateRaceCondition();
        demonstrateRaceConditionSolution();

        // 演示形式：多种 synchronized 使用方式
        demonstrateSynchronizationForms();

        // 演示风险：死锁
        demonstrateDeadlock();
        demonstrateDeadlockSolution();

        // 演示原理：Happens-Before
        demonstrateHappensBeforeRule();

        log.info("═════════════════════════════════════════════════════════════");
        log.info("演示完成！");
        log.info("总结：");
        log.info("  ✅ synchronized 能做：提供原子性、可见性和有序性");
        log.info("  ⚠️  代价：锁竞争导致性能下降");
        log.info("  ❌ 风险：不当使用会导致死锁");
        log.info("  📖 最佳实践：尽量使用并发工具而不是手动 synchronized");
        log.info("═════════════════════════════════════════════════════════════");
        System.exit(0);  // 确保程序正常退出
    }
}
