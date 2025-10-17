package nan.tech.lab01.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Happens-Before 规则完整演示
 *
 * <h2>目标</h2>
 * 系统演示 Java 内存模型（JMM）中的 6 条核心 Happens-Before 规则，
 * 通过对比"有 HB 保证"和"无 HB 保证"的场景，深入理解内存可见性。
 *
 * <h2>Happens-Before 关系的意义</h2>
 * <ul>
 *   <li>定义了操作之间的偏序关系，保证内存可见性</li>
 *   <li>如果操作 A happens-before 操作 B，则 A 的结果对 B 可见</li>
 *   <li>不仅仅是时间上的先后，更重要的是可见性保证</li>
 * </ul>
 *
 * <h2>6 条核心规则</h2>
 * <ol>
 *   <li>Program Order Rule - 单线程内的顺序性</li>
 *   <li>Volatile Variable Rule - volatile 的写读可见性</li>
 *   <li>Synchronized Rule - 锁的释放获取可见性</li>
 *   <li>Thread Start Rule - 线程启动前后的可见性</li>
 *   <li>Thread Termination Rule - 线程终止的可见性</li>
 *   <li>Transitivity - HB 关系的传递性</li>
 * </ol>
 *
 * @author nan
 * @since 2025-01-17
 */
public class HappensBeforeDemo {

    private static final Logger log = LoggerFactory.getLogger(HappensBeforeDemo.class);

    // ==================== Rule 1: Program Order Rule ====================

    /**
     * 规则 1: Program Order Rule
     *
     * <h3>规则说明</h3>
     * 在单个线程内，按照程序代码顺序，前面的操作 happens-before 后面的操作。
     *
     * <h3>注意事项</h3>
     * <ul>
     *   <li>仅限单线程内部，不涉及跨线程可见性</li>
     *   <li>编译器和处理器可能重排序，但保证 as-if-serial 语义</li>
     *   <li>重排序不会改变单线程内的执行结果</li>
     * </ul>
     */
    static class ProgramOrderRuleDemo {
        private int a = 0;
        private int b = 0;

        /**
         * WITH HB: 单线程内的顺序执行
         *
         * <p>HB 关系链：
         * <pre>
         * a = 1  HB  b = 2  HB  int c = a + b  HB  assert c == 3
         * </pre>
         *
         * <p>即使可能发生指令重排序，但保证最终结果符合程序顺序语义。
         */
        public void withHappensBeforeSingleThread() {
            log.info("=== 规则 1: 程序顺序规则 (WITH HB) ===");

            // 操作 1: 写入 a
            a = 1;
            log.debug("步骤 1: a = 1");

            // 操作 2: 写入 b (HB 操作1)
            b = 2;
            log.debug("步骤 2: b = 2");

            // 操作 3: 读取 a 和 b (HB 操作1和2)
            int c = a + b;
            log.debug("步骤 3: c = a + b = {}", c);

            // 操作 4: 断言 (HB 操作3)
            assert c == 3 : "程序顺序保证 c == 3";
            log.info("✓ 断言通过: c = {}", c);
        }

        /**
         * WITHOUT HB: 跨线程场景下缺乏同步
         *
         * <p>问题：主线程对 a、b 的写入与子线程的读取之间没有 HB 关系，
         * 可能导致子线程读到旧值（0）。
         */
        public void withoutHappensBefore() throws InterruptedException {
            log.info("=== 规则 1: 程序顺序规则 (WITHOUT HB - 跨线程) ===");

            a = 0;
            b = 0;

            // 先启动读线程，未与主线程建立任何 HB 关系
            Thread reader = new Thread(() -> {
                try {
                    Thread.sleep(20); // 简单等待主线程可能的写入
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                int localA = a;
                int localB = b;
                log.warn("⚠ 读线程: a = {}, b = {} (缺乏 HB，可能仍为旧值)", localA, localB);
            });
            reader.start();

            // 主线程稍后写入，但未与读线程建立 HB，因此无法保证可见性
            Thread.sleep(5);
            a = 1;
            b = 2;
            log.debug("主线程: 已写入 a = 1, b = 2 (未与读线程同步)");

            reader.join();
        }
    }

    // ==================== Rule 2: Volatile Variable Rule ====================

    /**
     * 规则 2: Volatile Variable Rule
     *
     * <h3>规则说明</h3>
     * 对 volatile 变量的写操作 happens-before 后续对该变量的读操作。
     *
     * <h3>保证机制</h3>
     * <ul>
     *   <li>写 volatile 变量：禁止重排序到后面的操作之前</li>
     *   <li>读 volatile 变量：禁止重排序到前面的操作之后</li>
     *   <li>写 volatile 会立即刷新到主内存</li>
     *   <li>读 volatile 会从主内存读取最新值</li>
     * </ul>
     */
    static class VolatileRuleDemo {
        // WITH HB: 使用 volatile 保证可见性
        private volatile boolean volatileReady = false;
        private int volatileData = 0;

        // WITHOUT HB: 普通变量无可见性保证
        private boolean normalReady = false;
        private int normalData = 0;

        /**
         * WITH HB: volatile 保证的可见性
         *
         * <p>HB 关系链：
         * <pre>
         * Writer Thread:
         *   volatileData = 42  HB  volatileReady = true (volatile write)
         *
         * Reader Thread:
         *   (wait for volatileReady == true)  HB  read volatileData
         *
         * Cross-thread HB:
         *   volatileReady = true (write)  HB  volatileReady read (true)
         *
         * Result:
         *   volatileData = 42  HB  volatileReady = true  HB  read volatileReady  HB  read volatileData
         *   因此 Reader 一定能看到 volatileData = 42
         * </pre>
         */
        public void withHappensBeforeVolatile() throws InterruptedException {
            log.info("=== 规则 2: volatile 变量规则 (WITH HB) ===");

            // 写线程
            Thread writer = new Thread(() -> {
                // 1. 写普通变量
                volatileData = 42;
                log.debug("写线程: volatileData = 42");

                // 2. 写 volatile 变量（建立 HB 关系）
                volatileReady = true;
                log.debug("写线程: volatileReady = true (volatile 写)");
            }, "VolatileWriter");

            // 读线程
            Thread reader = new Thread(() -> {
                // 3. 读 volatile 变量（等待写入完成）
                while (!volatileReady) {
                    // 忙等待 volatileReady
                    Thread.yield();
                }
                log.debug("读线程: 检测到 volatileReady = true");

                // 4. 读普通变量（由于 HB 关系，保证能看到 42）
                int value = volatileData;
                log.info("✓ 读线程: volatileData = {} (由 volatile HB 保证)", value);
                assert value == 42 : "volatile HB 保证可见性";
            }, "VolatileReader");

            writer.start();
            reader.start();
            writer.join();
            reader.join();
        }

        /**
         * WITHOUT HB: 普通变量的可见性问题
         *
         * <p>问题：normalReady 和 normalData 都是普通变量，
         * Reader 线程可能：
         * <ul>
         *   <li>永远看不到 normalReady = true（无限循环）</li>
         *   <li>看到 normalReady = true，但 normalData 仍是 0</li>
         * </ul>
         */
        public void withoutHappensBefore() throws InterruptedException {
            log.info("=== 规则 2: volatile 变量规则 (WITHOUT HB) ===");

            // 写线程
            Thread writer = new Thread(() -> {
                normalData = 42;
                log.debug("写线程: normalData = 42 (无 volatile)");

                normalReady = true;
                log.debug("写线程: normalReady = true (无 volatile)");
            }, "NormalWriter");

            // 读线程，带超时防止无限等待
            Thread reader = new Thread(() -> {
                long start = System.currentTimeMillis();
                // 最多等待 100ms，防止无限等待
                while (!normalReady && (System.currentTimeMillis() - start < 100)) {
                    Thread.yield();
                }

                if (!normalReady) {
                    log.warn("⚠ 读线程: 100ms 后 normalReady 仍为 false (可见性问题)");
                } else {
                    int value = normalData;
                    log.warn("⚠ 读线程: normalData = {} (可能为 0，因为缺乏 HB 保证)", value);
                }
            }, "NormalReader");

            writer.start();
            reader.start();
            writer.join();
            reader.join();
        }
    }

    // ==================== Rule 3: Synchronized Rule ====================

    /**
     * 规则 3: Synchronized Rule (Monitor Lock Rule)
     *
     * <h3>规则说明</h3>
     * 对一个锁的解锁 happens-before 后续对同一个锁的加锁。
     *
     * <h3>保证机制</h3>
     * <ul>
     *   <li>释放锁时：所有共享变量刷新到主内存</li>
     *   <li>获取锁时：从主内存读取最新值</li>
     *   <li>禁止临界区内的操作重排序到临界区外</li>
     * </ul>
     */
    static class SynchronizedRuleDemo {
        private final Object lock = new Object();
        private int sharedData = 0;

        /**
         * WITH HB: synchronized 保证的可见性
         *
         * <p>HB 关系链：
         * <pre>
         * Writer Thread:
         *   lock.lock()  HB  sharedData = 100  HB  lock.unlock()
         *
         * Reader Thread:
         *   lock.lock()  HB  read sharedData  HB  lock.unlock()
         *
         * Monitor Lock HB:
         *   Writer unlock(lock)  HB  Reader lock(lock)
         *
         * Result:
         *   sharedData = 100  HB  unlock  HB  lock  HB  read sharedData
         *   因此 Reader 一定能看到 sharedData = 100
         * </pre>
         */
        public void withHappensBefore() throws InterruptedException {
            log.info("=== 规则 3: synchronized 规则 (WITH HB) ===");

            // 写线程
            Thread writer = new Thread(() -> {
                synchronized (lock) {
                    log.debug("写线程: 获得锁");
                    sharedData = 100;
                    log.debug("写线程: sharedData = 100");
                    log.debug("写线程: 释放锁");
                }
                // 释放锁时，sharedData 刷新到主内存
            }, "SyncWriter");

            // 读线程
            Thread reader = new Thread(() -> {
                try {
                    // 确保 Writer 先执行
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                synchronized (lock) {
                    // 获取锁时，从主内存读取最新值
                    log.debug("读线程: 获得锁");
                    int value = sharedData;
                    log.info("✓ 读线程: sharedData = {} (由 synchronized HB 保证)", value);
                    assert value == 100 : "synchronized HB 保证可见性";
                    log.debug("读线程: 释放锁");
                }
            }, "SyncReader");

            writer.start();
            reader.start();
            writer.join();
            reader.join();
        }

        /**
         * WITHOUT HB: 缺乏同步的可见性问题
         *
         * <p>问题：没有使用 synchronized，Reader 可能看到 sharedData = 0。
         */
        public void withoutHappensBefore() throws InterruptedException {
            log.info("=== 规则 3: synchronized 规则 (WITHOUT HB) ===");
            sharedData = 0; // 重置

            // 写线程（无同步）
            Thread writer = new Thread(() -> {
                sharedData = 100;
                log.debug("写线程: sharedData = 100 (无同步)");
            }, "UnsyncWriter");

            // 读线程（无同步）
            Thread reader = new Thread(() -> {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                int value = sharedData;
                log.warn("⚠ 读线程: sharedData = {} (可能为 0，因为缺乏同步)", value);
            }, "UnsyncReader");

            writer.start();
            reader.start();
            writer.join();
            reader.join();
        }
    }

    // ==================== Rule 4: Thread Start Rule ====================

    /**
     * 规则 4: Thread Start Rule
     *
     * <h3>规则说明</h3>
     * 主线程中调用 Thread.start() 之前的所有操作，
     * happens-before 新线程中的任何操作。
     *
     * <h3>保证机制</h3>
     * <ul>
     *   <li>start() 调用前的所有写操作对新线程可见</li>
     *   <li>新线程可以安全读取主线程的准备工作结果</li>
     * </ul>
     */
    static class ThreadStartRuleDemo {
        private int preparedData = 0;

        /**
         * WITH HB: Thread.start() 保证的可见性
         *
         * <p>HB 关系链：
         * <pre>
         * Main Thread:
         *   preparedData = 200  HB  thread.start()
         *
         * Child Thread:
         *   thread.start()  HB  read preparedData
         *
         * Result:
         *   preparedData = 200  HB  start()  HB  child thread reads
         *   因此子线程一定能看到 preparedData = 200
         * </pre>
         */
        public void withHappensBeforeThreadStart() throws InterruptedException {
            log.info("=== 规则 4: 线程启动规则 (WITH HB) ===");

            // 主线程准备数据
            preparedData = 200;
            log.debug("主线程: preparedData = 200");

            // 创建子线程
            Thread child = new Thread(() -> {
                // Thread.start() HB 这里的所有操作
                int value = preparedData;
                log.info("✓ 子线程: preparedData = {} (由 Thread.start() HB 保证)", value);
                assert value == 200 : "Thread.start() HB 保证可见性";
            }, "ChildThread");

            // 启动子线程（建立 HB 关系）
            log.debug("主线程: 调用 child.start()");
            child.start();
            child.join();
        }

        /**
         * 说明：Thread Start Rule 总是生效
         *
         * <p>这条规则是 JMM 的内置保证，无法演示"WITHOUT HB"场景，
         * 因为 start() 方法本身就建立了 HB 关系。
         */
        public void explanation() {
            log.info("=== 规则 4: 线程启动规则 (说明) ===");
            log.info("Thread.start() HB 是 JMM 的内置保证");
            log.info("start() 之前的所有写操作对新线程可见");
            log.info("无法为这条规则演示 'WITHOUT HB' 场景");
        }
    }

    // ==================== Rule 5: Thread Termination Rule ====================

    /**
     * 规则 5: Thread Termination Rule (Thread Join Rule)
     *
     * <h3>规则说明</h3>
     * 线程 T1 中的所有操作 happens-before 其他线程对 T1 的 join() 成功返回。
     *
     * <h3>保证机制</h3>
     * <ul>
     *   <li>join() 返回后，可以安全读取 T1 的所有结果</li>
     *   <li>T1 的所有写操作对 join() 后的操作可见</li>
     * </ul>
     */
    static class ThreadTerminationRuleDemo {
        private int result = 0;

        /**
         * WITH HB: Thread.join() 保证的可见性
         *
         * <p>HB 关系链：
         * <pre>
         * Worker Thread:
         *   result = 300  HB  thread terminates
         *
         * Main Thread:
         *   thread terminates  HB  worker.join() returns  HB  read result
         *
         * Result:
         *   result = 300  HB  join() returns  HB  main reads result
         *   因此主线程一定能看到 result = 300
         * </pre>
         */
        public void withHappensBeforeThreadJoin() throws InterruptedException {
            log.info("=== 规则 5: 线程终止规则 (WITH HB) ===");

            // 创建工作线程
            Thread worker = new Thread(() -> {
                log.debug("工作线程: 计算中...");
                result = 300;
                log.debug("工作线程: result = 300");
                log.debug("工作线程: 即将终止");
            }, "WorkerThread");

            // 启动工作线程
            worker.start();
            log.debug("主线程: 工作线程已启动");

            // 等待工作线程完成（建立 HB 关系）
            log.debug("主线程: 调用 worker.join()");
            worker.join();
            log.debug("主线程: worker.join() 返回");

            // join() 返回后，可以安全读取 result
            int value = result;
            log.info("✓ 主线程: result = {} (由 Thread.join() HB 保证)", value);
            assert value == 300 : "Thread.join() HB 保证可见性";
        }

        /**
         * WITHOUT HB: 不使用 join() 导致的可见性问题
         *
         * <p>问题：主线程不等待 Worker 完成，可能读到 result = 0。
         */
        public void withoutHappensBefore() throws InterruptedException {
            log.info("=== 规则 5: 线程终止规则 (WITHOUT HB) ===");
            result = 0; // 重置

            // 创建工作线程
            Thread worker = new Thread(() -> {
                try {
                    Thread.sleep(50); // 模拟耗时操作
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                result = 300;
                log.debug("工作线程: result = 300");
            }, "WorkerNoJoin");

            // 启动但不等待
            worker.start();
            log.debug("主线程: 工作线程已启动 (无 join)");

            // 立即读取（可能读到旧值）
            int value = result;
            log.warn("⚠ 主线程: result = {} (可能为 0，因为未调用 join())", value);

            // 清理
            worker.join();
        }
    }

    // ==================== Rule 6: Transitivity ====================

    /**
     * 规则 6: Transitivity (传递性)
     *
     * <h3>规则说明</h3>
     * 如果 A happens-before B，且 B happens-before C，
     * 则 A happens-before C。
     *
     * <h3>实际应用</h3>
     * <ul>
     *   <li>可以通过多个 HB 关系链建立跨线程可见性</li>
     *   <li>常用于复杂的多线程协调场景</li>
     * </ul>
     */
    static class TransitivityDemo {
        private volatile boolean step1Done = false;
        private volatile boolean step2Done = false;
        private int dataA = 0;
        private int dataB = 0;

        /**
         * WITH HB: 传递性保证的可见性
         *
         * <p>HB 关系链：
         * <pre>
         * Thread1:
         *   dataA = 1  HB  step1Done = true (volatile write)
         *
         * Thread2:
         *   (wait step1Done)  HB  dataB = 2  HB  step2Done = true (volatile write)
         *
         * Thread3:
         *   (wait step2Done)  HB  read dataA, dataB
         *
         * Transitivity:
         *   dataA = 1  HB  step1Done = true
         *              HB  (Thread2 reads step1Done)
         *              HB  dataB = 2
         *              HB  step2Done = true
         *              HB  (Thread3 reads step2Done)
         *              HB  read dataA, dataB
         *
         * Result:
         *   通过传递性，Thread3 能看到 dataA = 1 和 dataB = 2
         * </pre>
         */
        public void withHappensBeforeTransitivity() throws InterruptedException {
            log.info("=== 规则 6: 传递性 (WITH HB) ===");

            // 线程 1: 准备 dataA
            Thread t1 = new Thread(() -> {
                dataA = 1;
                log.debug("T1: dataA = 1");

                step1Done = true; // volatile 写
                log.debug("T1: step1Done = true (volatile)");
            }, "Thread1");

            // 线程 2: 等待 step1，准备 dataB
            Thread t2 = new Thread(() -> {
                // 等待 step1Done（建立 HB 关系）
                while (!step1Done) {
                    Thread.yield();
                }
                log.debug("T2: 检测到 step1Done = true");

                dataB = 2;
                log.debug("T2: dataB = 2");

                step2Done = true; // volatile 写
                log.debug("T2: step2Done = true (volatile)");
            }, "Thread2");

            // 线程 3: 等待 step2，读取 dataA 和 dataB
            Thread t3 = new Thread(() -> {
                // 等待 step2Done（建立 HB 关系）
                while (!step2Done) {
                    Thread.yield();
                }
                log.debug("T3: 检测到 step2Done = true");

                // 由于传递性，可以看到 dataA 和 dataB
                int valueA = dataA;
                int valueB = dataB;
                log.info("✓ T3: dataA = {}, dataB = {} (由传递性保证)", valueA, valueB);
                assert valueA == 1 && valueB == 2 : "传递性保证可见性";
            }, "Thread3");

            // 按顺序启动
            t1.start();
            t2.start();
            t3.start();

            t1.join();
            t2.join();
            t3.join();
        }

        /**
         * WITHOUT HB: 缺乏传递性保证
         *
         * <p>问题：如果去掉 volatile，传递性链条断裂，
         * Thread3 可能看不到 dataA 或 dataB 的最新值。
         */
        public void withoutHappensBefore() throws InterruptedException {
            log.info("=== 规则 6: 传递性 (WITHOUT HB) ===");
            dataA = 0;
            dataB = 0;
            boolean normalStep1 = false;
            boolean normalStep2 = false;

            // 使用普通变量（无 HB 保证）
            Thread t1 = new Thread(() -> {
                dataA = 1;
                // normalStep1 = true; // 无法在 lambda 中修改
                log.debug("T1: dataA = 1 (无 volatile)");
            }, "Thread1-Normal");

            Thread t2 = new Thread(() -> {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                dataB = 2;
                log.debug("T2: dataB = 2 (无 volatile)");
            }, "Thread2-Normal");

            Thread t3 = new Thread(() -> {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                int valueA = dataA;
                int valueB = dataB;
                log.warn("⚠ T3: dataA = {}, dataB = {} (可能为旧值，因为缺乏 HB)", valueA, valueB);
            }, "Thread3-Normal");

            t1.start();
            t2.start();
            t3.start();

            t1.join();
            t2.join();
            t3.join();
        }
    }

    // ==================== Main Entry Point ====================

    /**
     * 主演示方法
     *
     * <p>按顺序执行所有 6 条 HB 规则的演示，每条规则都包含：
     * <ul>
     *   <li>WITH HB: 展示正确的同步机制</li>
     *   <li>WITHOUT HB: 展示缺乏同步的问题</li>
     * </ul>
     *
     * @param args 命令行参数（未使用）
     */
    public static void main(String[] args) {
        log.info("========================================");
        log.info("Happens-Before 规则完整演示");
        log.info("========================================\n");

        try {
            // 规则 1: 程序顺序规则
            ProgramOrderRuleDemo rule1 = new ProgramOrderRuleDemo();
            rule1.withHappensBeforeSingleThread();
            log.info("");
            rule1.withoutHappensBefore();
            log.info("\n" + "=".repeat(60) + "\n");

            // 规则 2: volatile 变量规则
            VolatileRuleDemo rule2 = new VolatileRuleDemo();
            rule2.withHappensBeforeVolatile();
            log.info("");
            rule2.withoutHappensBefore();
            log.info("\n" + "=".repeat(60) + "\n");

            // 规则 3: synchronized 规则
            SynchronizedRuleDemo rule3 = new SynchronizedRuleDemo();
            rule3.withHappensBefore();
            log.info("");
            rule3.withoutHappensBefore();
            log.info("\n" + "=".repeat(60) + "\n");

            // 规则 4: 线程启动规则
            ThreadStartRuleDemo rule4 = new ThreadStartRuleDemo();
            rule4.withHappensBeforeThreadStart();
            log.info("");
            rule4.explanation();
            log.info("\n" + "=".repeat(60) + "\n");

            // 规则 5: 线程终止规则
            ThreadTerminationRuleDemo rule5 = new ThreadTerminationRuleDemo();
            rule5.withHappensBeforeThreadJoin();
            log.info("");
            rule5.withoutHappensBefore();
            log.info("\n" + "=".repeat(60) + "\n");

            // 规则 6: 传递性
            TransitivityDemo rule6 = new TransitivityDemo();
            rule6.withHappensBeforeTransitivity();
            log.info("");
            rule6.withoutHappensBefore();
            log.info("\n" + "=".repeat(60) + "\n");

            log.info("========================================");
            log.info("所有 Happens-Before 演示完成！");
            log.info("========================================");

        } catch (Exception e) {
            log.error("演示过程中发生错误", e);
        }
    }
}
