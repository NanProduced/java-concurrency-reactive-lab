package nan.tech.lab01.thread;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * volatile 关键字完整演示：对比式教学 (WITH vs WITHOUT volatile)
 *
 * 核心问题：为什么需要 volatile？
 *   ├─ 没有 volatile: 线程看不到其他线程的更新（可见性问题）
 *   ├─ 没有 volatile: 编译器/CPU 可能重排序，导致逻辑错误（有序性问题）
 *   └─ volatile 解决: 通过内存屏障保证可见性和有序性
 *
 * volatile 能提供：
 *   ✅ 可见性 (Visibility): 写操作立即对所有线程可见
 *   ✅ 有序性 (Ordering): 禁止重排序，通过内存屏障保证
 *   ❌ 不保证原子性 (No Atomicity): volatile i++ 仍然有竞态条件
 *
 * 学习路径：
 *   演示 1 & 2: 对比演示（没有 volatile vs WITH volatile）→ 理解可见性必要性
 *   演示 3 & 4: 对比演示（没有 volatile vs WITH volatile）→ 理解有序性必要性
 *   演示 5: volatile 不保证原子性 → 理解 volatile 的限制
 *   演示 6: Happens-Before 规则 → 理解 volatile 如何工作
 */
public class VolatileDemo {

    private static final Logger log = LoggerFactory.getLogger(VolatileDemo.class);

    // ============================================
    // 演示 1 & 2: 可见性 (WITHOUT volatile vs WITH volatile)
    // ============================================

    /**
     * 演示 1：❌ WITHOUT volatile - 可见性问题导致无限等待
     *
     * 问题：flag 是普通变量，Writer 的修改对 Reader 不可见
     * 结果：Reader 可能永远看不到 flag=true，导致无限循环
     * 原因：缺少内存屏障，编译器和 CPU 可能将读取缓存在寄存器中
     */
    private static boolean unsafeVisibilityFlag = false;  // ❌ 注意：没有 volatile

    public static void demonstrateVisibilityProblem() throws InterruptedException {
        log.info("════════════════════════════════════════");
        log.info("演示 1: ❌ WITHOUT volatile - 可见性问题");
        log.info("════════════════════════════════════════");

        unsafeVisibilityFlag = false;
        final long[] startTime = {System.currentTimeMillis()};
        final boolean[] readerFinished = {false};

        // Writer 线程：设置 flag=true
        Thread writer = new Thread(() -> {
            try {
                Thread.sleep(100);  // 延迟启动，确保 Reader 已进入循环
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            unsafeVisibilityFlag = true;
            log.info("[Writer] 已设置 unsafeVisibilityFlag=true (时间: 100ms)");
        }, "UnsafeWriter");

        // Reader 线程：等待 flag 变为 true
        Thread reader = new Thread(() -> {
            while (!unsafeVisibilityFlag) {
                // 注意：这个循环可能永远不会退出！
                // 原因：编译器可能将 unsafeVisibilityFlag 的读取缓存在寄存器中
            }
            long elapsed = System.currentTimeMillis() - startTime[0];
            readerFinished[0] = true;
            log.info("[Reader] 看到 unsafeVisibilityFlag=true (耗时: {}ms)", elapsed);
        }, "UnsafeReader");

        writer.start();
        reader.start();

        // 等待最多 3 秒，看 reader 是否能完成
        reader.join(3000);

        if (readerFinished[0]) {
            log.info("✓ 运气好，这次 Reader 看到了更新（但这不保证总是能看到！）");
            log.info("  原因：JVM 优化可能导致可见性问题");
        } else {
            log.warn("⚠️  Reader 在 3 秒内没有看到更新！");
            log.warn("  这就是没有 volatile 的问题：写操作对其他线程不可见");
            reader.interrupt();
            // 确保 reader 线程完全停止
            try {
                reader.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        writer.join();
        log.info("");
    }

    /**
     * 演示 2：✅ WITH volatile - 可见性问题解决
     *
     * 解决方案：将 flag 声明为 volatile
     * 结果：Reader 能立即看到 Writer 的修改
     * 原理：volatile 写操作后自动插入写屏障，volatile 读操作前插入读屏障
     *       这些屏障保证了内存的可见性和有序性
     */
    private static volatile boolean safeVisibilityFlag = false;  // ✅ WITH volatile

    public static void demonstrateVisibilitySolution() throws InterruptedException {
        log.info("════════════════════════════════════════");
        log.info("演示 2: ✅ WITH volatile - 可见性保证");
        log.info("════════════════════════════════════════");

        safeVisibilityFlag = false;
        long startTime = System.currentTimeMillis();

        // Writer 线程：设置 flag=true
        Thread writer = new Thread(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            safeVisibilityFlag = true;
            log.info("[Writer] 已设置 safeVisibilityFlag=true (时间: 100ms)");
        }, "SafeWriter");

        // Reader 线程：等待 flag 变为 true
        Thread reader = new Thread(() -> {
            while (!safeVisibilityFlag) {
                // 注意：volatile 保证了这个循环会在 Writer 更新后立即退出
                // 因为 volatile 读操作无法被缓存
            }
            long elapsed = System.currentTimeMillis() - startTime;
            log.info("[Reader] 看到 safeVisibilityFlag=true (耗时: {}ms)", elapsed);
        }, "SafeReader");

        writer.start();
        reader.start();

        writer.join();
        reader.join();

        log.info("✓ 对比演示：WITH volatile 保证了可见性");
        log.info("  规则：对 volatile 变量的写 Happens-Before 后续的读");
        log.info("");
    }

    // ============================================
    // 演示 3 & 4: 有序性 (WITHOUT volatile vs WITH volatile)
    // ============================================

    /**
     * 演示 3：❌ WITHOUT volatile - 有序性问题（重排序）
     *
     * 问题：编译器或 CPU 可能重排序语句
     * 场景：
     *   x = 1;       (操作 A)
     *   ready = true; (操作 B，普通变量)
     * 重排序后可能变成：
     *   ready = true;
     *   x = 1;
     * 结果：其他线程可能读到 ready=true 但 x 还是 0
     */
    private static int unsafeOrderingX = 0;
    private static boolean unsafeOrderingReady = false;  // ❌ 普通变量

    public static void demonstrateOrderingProblem() throws InterruptedException {
        log.info("════════════════════════════════════════");
        log.info("演示 3: ❌ WITHOUT volatile - 有序性问题（重排序）");
        log.info("════════════════════════════════════════");

        unsafeOrderingX = 0;
        unsafeOrderingReady = false;
        int violations = 0;
        int timeouts = 0;

        // 多次重复实验以增加发现重排序的概率
        // 注意：由于没有 volatile，读线程可能永远看不到 ready=true 的更新
        // 因此需要添加超时机制避免无限等待
        for (int round = 0; round < 1000; round++) {
            unsafeOrderingX = 0;
            unsafeOrderingReady = false;

            Thread t1 = new Thread(() -> {
                unsafeOrderingX = 1;      // 操作 A
                unsafeOrderingReady = true; // 操作 B - 可能被重排序到 A 之前！
            }, "UnsafeOrderingT1");

            final int[] detectedX = {-1};
            final boolean[] readerReady = {false};

            Thread t2 = new Thread(() -> {
                // 添加超时机制：如果 1ms 内还没看到 ready，就认为看不到了
                long deadline = System.currentTimeMillis() + 1;
                while (!unsafeOrderingReady && System.currentTimeMillis() < deadline) {
                    // 等待 ready 变为 true 或超时
                }
                readerReady[0] = unsafeOrderingReady;
                detectedX[0] = unsafeOrderingX;
            });

            t1.start();
            t2.start();

            // 等待线程完成，有超时保护
            try {
                t1.join(100);
                t2.join(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                // 中断当前线程后，也需要中断子线程
                t1.interrupt();
                t2.interrupt();
            }

            // 如果 Reader 在超时内看到了 ready=true，则进行验证
            // 否则认为这是一次无可见性的情况（这正常，因为没有 volatile）
            if (readerReady[0] && detectedX[0] != 1) {
                violations++;  // 发现了重排序问题
            }
        }

        if (violations > 0) {
            log.warn("⚠️  检测到 {} 次有序性问题（重排序）", violations);
            log.warn("  这说明没有 volatile 时，编译器/CPU 可能重排序");
        } else {
            log.info("ℹ️  本轮未检测到明显的重排序");
            log.info("  但 volatile 的缺失仍然会导致可见性和有序性问题");
            log.info("  在实际多核系统或特定编译优化下仍可能发生");
        }
        log.info("");
    }

    /**
     * 演示 4：✅ WITH volatile - 有序性保证（禁止重排序）
     *
     * 解决方案：将 ready 声明为 volatile
     * 结果：编译器和 CPU 不会重排序涉及 volatile 变量的操作
     * 原理：volatile 在读写时插入内存屏障，禁止指令重排序
     */
    private static volatile int safeOrderingX = 0;
    private static volatile boolean safeOrderingReady = false;  // ✅ WITH volatile

    public static void demonstrateOrderingSolution() throws InterruptedException {
        log.info("════════════════════════════════════════");
        log.info("演示 4: ✅ WITH volatile - 有序性保证");
        log.info("════════════════════════════════════════");

        safeOrderingX = 0;
        safeOrderingReady = false;
        int violations = 0;

        // 相同的多次实验
        for (int round = 0; round < 10000; round++) {
            safeOrderingX = 0;
            safeOrderingReady = false;

            Thread t1 = new Thread(() -> {
                safeOrderingX = 1;      // 操作 A
                safeOrderingReady = true; // 操作 B - volatile 写，不会被重排序到 A 之前
            });

            final int[] detectedX = {-1};
            Thread t2 = new Thread(() -> {
                while (!safeOrderingReady) {
                    // 等待
                }
                detectedX[0] = safeOrderingX;
            });

            t1.start();
            t2.start();
            t1.join();
            t2.join();

            if (detectedX[0] != 1) {
                violations++;
            }
        }

        if (violations == 0) {
            log.info("✓ 经过 10000 次实验，零次有序性违反");
            log.info("  规则：在 volatile 变量前的操作不会重排序到后面");
            log.info("  规则：在 volatile 变量后的操作不会重排序到前面");
        } else {
            log.warn("⚠️  检测到 {} 次违反（不应该发生）", violations);
        }
        log.info("");
    }

    // ============================================
    // 演示 5: volatile 不保证原子性
    // ============================================

    /**
     * 演示 5：volatile 不保证原子性
     *
     * 问题：volatile i++ 仍然不是原子操作
     * 分解：
     *   i++  →  (1) 读取 i 的值
     *           (2) 增加 1
     *           (3) 写回 i
     * 竞态条件：多个线程可能同时执行这 3 个步骤
     * 结果：最终的值会小于期望值
     */
    private static volatile long volatileCounter = 0;

    public static void demonstrateNonAtomicity() throws InterruptedException {
        log.info("════════════════════════════════════════");
        log.info("演示 5: volatile 不保证原子性");
        log.info("════════════════════════════════════════");

        volatileCounter = 0;
        int threadCount = 10;
        int incrementsPerThread = 1000;

        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < incrementsPerThread; j++) {
                    volatileCounter++;  // ❌ 这不是原子操作
                    // 分解为：读、加、写，可能发生数据丢失
                }
            });
        }

        long startTime = System.currentTimeMillis();
        for (Thread t : threads) {
            t.start();
        }

        for (Thread t : threads) {
            t.join();
        }
        long duration = System.currentTimeMillis() - startTime;

        long expected = (long) threadCount * incrementsPerThread;
        log.info("预期值: {}", expected);
        log.info("实际值: {}", volatileCounter);
        log.info("数据丢失: {}", expected - volatileCounter);
        log.info("耗时: {} ms", duration);

        log.info("✓ 演示成功：即使 volatile，++ 操作仍然发生竞态条件");
        log.info("  规则：volatile 只保证可见性和有序性，不保证原子性");
        log.info("  解决方案：使用 AtomicLong、synchronized 或其他同步机制");
        log.info("");
    }

    // ============================================
    // 演示 6: Happens-Before 规则
    // ============================================

    /**
     * 演示 6：volatile 如何通过 Happens-Before 规则工作
     *
     * 规则：对 volatile 变量的写操作 Happens-Before 该变量后续的读操作
     * 含义：
     *   (1) 线程 A 对 volatile 变量 v 的写
     *   (2) 线程 B 后续对 volatile 变量 v 的读
     * 那么 (1) 一定会在 (2) 之前执行，且 (1) 的所有修改对 (2) 可见
     */
    private static volatile int hbValue = 0;
    private static volatile boolean hbReady = false;

    public static void demonstrateHappensBeforeRule() throws InterruptedException {
        log.info("════════════════════════════════════════");
        log.info("演示 6: Happens-Before 规则");
        log.info("════════════════════════════════════════");

        hbValue = 0;
        hbReady = false;
        final int[] observedValue = {-1};

        Thread writer = new Thread(() -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            hbValue = 42;           // 操作 A
            hbReady = true;          // 操作 B (volatile 写)
            log.info("[Writer] 写入：value=42, ready=true");
        }, "HBWriter");

        Thread reader = new Thread(() -> {
            while (!hbReady) {       // volatile 读
                // 等待
            }
            observedValue[0] = hbValue;  // 读取 value
            log.info("[Reader] 读取：value={}", observedValue[0]);
        }, "HBReader");

        writer.start();
        reader.start();

        writer.join();
        reader.join();

        if (observedValue[0] == 42) {
            log.info("✓ Happens-Before 规则保证：");
            log.info("  操作 A (hbValue=42) Happens-Before 操作 B (hbReady=true)");
            log.info("  操作 B 的 volatile 读 Happens-Before 操作 C (hbValue 的读)");
            log.info("  因此操作 A Happens-Before 操作 C（传递性）");
            log.info("  所以 Reader 必然读到 value=42");
        } else {
            log.error("❌ 不应该发生：Reader 读到的值是 {}", observedValue[0]);
        }
        log.info("");
    }

    // ============================================
    // main：按顺序运行所有演示
    // ============================================

    public static void main(String[] args) throws InterruptedException {
        log.info("═════════════════════════════════════════════════════════════");
        log.info("Java volatile 关键字完整演示");
        log.info("教学目标：理解为什么需要 volatile，以及 volatile 能做什么/不能做什么");
        log.info("═════════════════════════════════════════════════════════════");
        log.info("");

        // 演示对比：可见性
        demonstrateVisibilityProblem();
        demonstrateVisibilitySolution();

        // 演示对比：有序性
        demonstrateOrderingProblem();
        demonstrateOrderingSolution();

        // 演示限制：原子性
        demonstrateNonAtomicity();

        // 演示原理：Happens-Before
        demonstrateHappensBeforeRule();

        log.info("═════════════════════════════════════════════════════════════");
        log.info("演示完成！");
        log.info("总结：");
        log.info("  ✅ volatile 能做：提供可见性和有序性");
        log.info("  ❌ volatile 不能做：保证原子性");
        log.info("  📖 原理：通过 Happens-Before 规则保证内存屏障效果");
        log.info("═════════════════════════════════════════════════════════════");

        // 确保所有线程完成，程序能正常退出
        System.exit(0);
    }
}
