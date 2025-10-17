package nan.tech.lab01.atomicity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 原子操作完整演示：CAS 原理 + AtomicLong vs LongAdder 性能对比
 *
 * 核心问题：如何实现原子性而不用锁？
 *   ├─ synchronized: 通过锁实现，粒度粗，性能差
 *   ├─ volatile: 只提供可见性和有序性，不提供原子性
 *   └─ CAS: 通过原子指令实现无锁原子性（推荐）
 *
 * CAS (Compare-And-Swap) 原理：
 *   操作：atomicCompareAndSwap(期望值, 新值)
 *   步骤：(1) 读取当前值
 *         (2) 与期望值比较
 *         (3) 如果相等，写入新值
 *   特点：这三个步骤在硬件级别是原子的（一条 CPU 指令）
 *
 * AtomicLong vs LongAdder：
 *   AtomicLong: 单个 volatile long 变量 + CAS 操作
 *     ├─ 低竞争（≤4 核）: 性能更好（无额外分段开销）
 *     └─ 高竞争（≥8 核）: 性能差（CAS 重试多）
 *
 *   LongAdder: 分段累加（Striped）
 *     ├─ 低竞争（≤4 核）: 性能略差（有分段开销）
 *     └─ 高竞争（≥8 核）: 性能明显更好（多个分段可并发）
 *
 * 决策树：
 *   需要原子计数？
 *   ├─ 竞争很低（单线程或 2-4 线程）→ AtomicLong
 *   ├─ 竞争中等（4-8 线程）→ 都可以，AtomicLong 稍优
 *   ├─ 竞争很高（≥8 线程或热点）→ LongAdder
 *   └─ 需要频繁读取 → AtomicLong（LongAdder 的 sum() 遍历所有分段）
 *
 * 学习路径：
 *   演示 1: CAS 原理 → 理解无锁原子性基础
 *   演示 2 & 3: 低竞争 vs 高竞争 → 理解选择标准
 *   演示 4: AtomicLong 自旋重试 → 理解性能瓶颈
 *   演示 5: 决策树 → 实际应用指导
 */
public class AtomicVsLongAdder {

    private static final Logger log = LoggerFactory.getLogger(AtomicVsLongAdder.class);

    // ============================================
    // 演示 1: CAS 原理 - 手动实现一个简单的 CAS 计数器
    // ============================================

    /**
     * 演示 1：理解 CAS 原理
     *
     * CAS 的本质：
     *   expectedValue: 线程读到的当前值
     *   newValue: 要写入的新值
     *
     * 执行流程：
     *   (1) 比较：当前值是否等于 expectedValue
     *   (2) 如果相等：写入 newValue，返回 true
     *   (3) 如果不相等：不写入，返回 false（需要重试）
     *
     * 关键点：这个"比较并交换"的操作必须是原子的（硬件支持）
     */
    public static void demonstrateCASPrinciple() throws InterruptedException {
        log.info("════════════════════════════════════════");
        log.info("演示 1: CAS 原理（Compare-And-Swap）");
        log.info("════════════════════════════════════════");

        AtomicLong counter = new AtomicLong(0);

        log.info("初始值: {}", counter.get());
        log.info("");
        log.info("CAS 演示（单线程）:");

        // CAS 成功：期望值正确
        long current = counter.get();  // 读取当前值 = 0
        boolean success = counter.compareAndSet(current, 100);  // 尝试 CAS：期望 0，新值 100
        log.info("  操作 1: compareAndSet({}, 100) → {}", current, success);
        log.info("  结果: {} (成功，期望值正确)", counter.get());
        log.info("");

        // CAS 失败：期望值错误
        current = counter.get();  // 读取当前值 = 100
        success = counter.compareAndSet(50, 200);  // 尝试 CAS：期望 50，新值 200
        log.info("  操作 2: compareAndSet(50, 200) → {}", success);
        log.info("  结果: {} (失败，期望值不匹配，需要重试)", counter.get());
        log.info("");

        log.info("CAS 演示（多线程）:");
        counter.set(0);

        // 模拟两个线程同时做 CAS - 正确的场景
        // 核心原理：两个线程同时读到相同的值，但只有一个的 CAS 会成功
        final boolean[] successA = {false};
        final boolean[] successB = {false};
        // 使用两个 barrier 确保同步：
        // - readBarrier: 确保两个线程都读完值后再执行 CAS
        // - casBarrier: 确保两个线程都准备好执行 CAS
        final CyclicBarrier readBarrier = new CyclicBarrier(2);
        final CyclicBarrier casBarrier = new CyclicBarrier(2);

        Thread threadA = new Thread(() -> {
            try {
                long current1 = counter.get();  // Thread A 读到 0
                readBarrier.await();            // 同步：等待 B 也读取完

                // 现在两个线程都读到了值，准备执行 CAS
                casBarrier.await();             // 同步：一起执行 CAS

                // 执行 CAS（几乎同时）
                successA[0] = counter.compareAndSet(current1, 111);
                log.info("  [Thread A] 读到 {}，尝试 CAS(0→111) → {}", current1, successA[0]);
            } catch (Exception e) {
                Thread.currentThread().interrupt();
            }
        }, "CAS-A");

        Thread threadB = new Thread(() -> {
            try {
                long current2 = counter.get();  // Thread B 读到 0
                readBarrier.await();            // 同步：等待 A 也读取完

                // 现在两个线程都读到了值，准备执行 CAS
                casBarrier.await();             // 同步：一起执行 CAS

                // 执行 CAS（几乎同时）
                successB[0] = counter.compareAndSet(current2, 222);
                log.info("  [Thread B] 读到 {}，尝试 CAS(0→222) → {}", current2, successB[0]);
            } catch (Exception e) {
                Thread.currentThread().interrupt();
            }
        }, "CAS-B");

        threadA.start();
        threadB.start();
        threadA.join();
        threadB.join();

        log.info("");
        log.info("  结果: {} (只有一个线程的 CAS 成功)", counter.get());
        if (successA[0] && !successB[0]) {
            log.info("  → Thread A 成功，Thread B 失败（因为值已被 A 改为 111，不等于期望值 0）");
        } else if (!successA[0] && successB[0]) {
            log.info("  → Thread B 成功，Thread A 失败（因为值已被 B 改为 222，不等于期望值 0）");
        } else {
            log.info("  → (罕见竞争情况：两个都失败或其他)");
        }
        log.info("");
        log.info("  说明:");
        log.info("    ✅ 即使两个线程读到相同的值，只有先执行 CAS 的线程会成功");
        log.info("    ✅ 第二个线程的 CAS 失败（期望值 0，但当前值已被改为 111 或 222）");
        log.info("    ✅ 失败的线程需要重试：重新读取 → 比较新值 → 再次尝试 CAS");
        log.info("");

        log.info("✓ CAS 的优势：");
        log.info("  ✅ 无锁设计：无需申请/释放锁，性能更好");
        log.info("  ✅ 原子性：硬件级别保证，不会被打断");
        log.info("  ✅ 高并发：多线程可同时执行（失败的需重试）");
        log.info("");
    }

    // ============================================
    // 演示 2 & 3: 低竞争 vs 高竞争性能对比
    // ============================================

    /**
     * 演示 2：低竞争场景 - AtomicLong 优于 LongAdder
     *
     * 竞争度：中等（10 个线程，但每个线程操作 10 次就完成）
     * 结果：两者都很快，AtomicLong 因为没有分段开销而略优
     */
    public static void demonstrateLowContention() throws InterruptedException {
        log.info("════════════════════════════════════════");
        log.info("演示 2: 低竞争场景");
        log.info("════════════════════════════════════════");

        int threadCount = 10;
        int operationsPerThread = 100;  // 每个线程只做 100 次操作

        // 测试 AtomicLong
        AtomicLong atomicCounter = new AtomicLong(0);
        long atomicStart = System.nanoTime();

        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < operationsPerThread; j++) {
                    atomicCounter.incrementAndGet();
                }
            });
            threads[i].start();
        }
        for (Thread t : threads) {
            t.join();
        }

        long atomicDuration = (System.nanoTime() - atomicStart) / 1_000_000;  // 转换为 ms

        // 测试 LongAdder
        LongAdder adderCounter = new LongAdder();
        long adderStart = System.nanoTime();

        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < operationsPerThread; j++) {
                    adderCounter.increment();
                }
            });
            threads[i].start();
        }
        for (Thread t : threads) {
            t.join();
        }

        long adderDuration = (System.nanoTime() - adderStart) / 1_000_000;  // 转换为 ms

        long expected = (long) threadCount * operationsPerThread;
        log.info("总操作数: {}", expected);
        log.info("");
        log.info("AtomicLong 耗时: {} ms", atomicDuration);
        log.info("LongAdder  耗时: {} ms", adderDuration);
        log.info("");

        if (atomicDuration < adderDuration) {
            log.info("✓ AtomicLong 更快（{} ms）", adderDuration - atomicDuration);
            log.info("  原因：竞争低时，LongAdder 的分段开销占比更大");
        } else {
            log.info("✓ 性能相近，AtomicLong 略占优");
        }
        log.info("");
    }

    /**
     * 演示 3：高竞争场景 - LongAdder 优于 AtomicLong
     *
     * 竞争度：很高（64 个线程，每个线程做 100,000 次操作）
     * 结果：LongAdder 明显更快（分段锁分散了竞争）
     */
    public static void demonstrateHighContention() throws InterruptedException {
        log.info("════════════════════════════════════════");
        log.info("演示 3: 高竞争场景");
        log.info("════════════════════════════════════════");

        int threadCount = Runtime.getRuntime().availableProcessors() * 4;  // 核心数的 4 倍
        int operationsPerThread = 100_000;  // 每个线程做 100k 次操作

        // 测试 AtomicLong
        AtomicLong atomicCounter = new AtomicLong(0);
        long atomicStart = System.nanoTime();

        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < operationsPerThread; j++) {
                    atomicCounter.incrementAndGet();
                }
            });
            threads[i].start();
        }
        for (Thread t : threads) {
            t.join();
        }

        long atomicDuration = (System.nanoTime() - atomicStart) / 1_000_000;  // 转换为 ms

        // 测试 LongAdder
        LongAdder adderCounter = new LongAdder();
        long adderStart = System.nanoTime();

        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < operationsPerThread; j++) {
                    adderCounter.increment();
                }
            });
            threads[i].start();
        }
        for (Thread t : threads) {
            t.join();
        }

        long adderDuration = (System.nanoTime() - adderStart) / 1_000_000;  // 转换为 ms

        long expected = (long) threadCount * operationsPerThread;
        log.info("线程数: {}", threadCount);
        log.info("总操作数: {}", expected);
        log.info("");
        log.info("AtomicLong 耗时: {} ms", atomicDuration);
        log.info("LongAdder  耗时: {} ms", adderDuration);
        log.info("");

        if (atomicDuration > adderDuration) {
            double speedup = (double) atomicDuration / adderDuration;
            log.info("✓ LongAdder 更快（快 {:.1f}x）", speedup);
            log.info("  原因：高竞争时，LongAdder 的分段设计显著优于单原子变量");
            log.info("  分析：");
            log.info("    - AtomicLong: 单个值，所有线程竞争同一个 CAS，重试多");
            log.info("    - LongAdder: 多个分段（Cell），每个线程作用于不同分段，竞争低");
        } else {
            log.info("✓ 性能相近或 AtomicLong 略优");
        }
        log.info("");
    }

    // ============================================
    // 演示 4: AtomicLong 的 CAS 重试开销
    // ============================================

    /**
     * 演示 4：观察 AtomicLong 的 CAS 重试现象
     *
     * 在高竞争下，CAS 频繁失败，导致：
     *   ├─ CPU 忙循环（自旋重试）
     *   ├─ 缓存行争用（Cache Line Contention）
     *   └─ 总体吞吐量下降
     */
    public static void demonstrateCASRetry() throws InterruptedException {
        log.info("════════════════════════════════════════");
        log.info("演示 4: AtomicLong 的 CAS 重试开销");
        log.info("════════════════════════════════════════");

        // 高竞争场景
        int threadCount = 16;
        long operationsPerThread = 100_000;

        AtomicLong counter = new AtomicLong(0);
        long startTime = System.currentTimeMillis();

        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                long localCounter = 0;
                for (int j = 0; j < operationsPerThread; j++) {
                    counter.incrementAndGet();
                    localCounter++;
                }
            });
            threads[i].start();
        }

        for (Thread t : threads) {
            t.join();
        }

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        long totalOps = threadCount * operationsPerThread;
        long opsPerMs = totalOps / (duration > 0 ? duration : 1);

        log.info("总操作数: {}", totalOps);
        log.info("耗时: {} ms", duration);
        log.info("吞吐量: {} ops/ms", opsPerMs);
        log.info("");
        log.info("✓ 分析：");
        log.info("  - 吞吐量取决于 CAS 的成功率");
        log.info("  - 高竞争导致 CAS 失败率高，需要频繁重试");
        log.info("  - 每次重试都是一次浪费的 CPU 周期");
        log.info("  - 这就是 LongAdder 用分段来解决的问题");
        log.info("");
    }

    // ============================================
    // 演示 5: 决策树 - 如何选择
    // ============================================

    /**
     * 演示 5：AtomicLong vs LongAdder 决策指南
     */
    public static void demonstrateDecisionTree() {
        log.info("════════════════════════════════════════");
        log.info("演示 5: 选择指南 - 何时用 AtomicLong vs LongAdder");
        log.info("════════════════════════════════════════");
        log.info("");

        log.info("决策树：");
        log.info("");
        log.info("问题: 需要原子计数吗？");
        log.info("├─ 否 → 用普通变量（或使用 ThreadLocal 避免竞争）");
        log.info("└─ 是 →");
        log.info("   ├─ 竞争很低（单线程或主要是读）→ AtomicLong ✅");
        log.info("   │  └─ 特点：无分段开销，最快");
        log.info("   │");
        log.info("   ├─ 竞争中等（2-8 线程）→ AtomicLong 优先，LongAdder 也可");
        log.info("   │  └─ 特点：两者性能接近");
        log.info("   │");
        log.info("   ├─ 竞争高（≥8 线程或热点）→ LongAdder ✅✅");
        log.info("   │  └─ 特点：分段锁，吞吐量明显更高");
        log.info("   │");
        log.info("   └─ 需要频繁读取（read-heavy）→ AtomicLong");
        log.info("      └─ 特点：LongAdder.sum() 需要遍历所有分段，开销大");
        log.info("");

        log.info("实际应用场景：");
        log.info("");
        log.info("1. 计数器（write-heavy）");
        log.info("   ├─ 访问日志计数 → LongAdder（高竞争）");
        log.info("   ├─ 缓存命中计数 → LongAdder");
        log.info("   └─ 错误计数 → 竞争低 → AtomicLong");
        log.info("");
        log.info("2. 累加器（accumulator）");
        log.info("   ├─ 求和操作 → LongAdder");
        log.info("   ├─ 性能指标 → LongAdder");
        log.info("   └─ 跟踪值 → AtomicLong");
        log.info("");
        log.info("3. 反模式（不要做）");
        log.info("   ├─ ❌ 在高竞争场景下用 AtomicLong 而不用 LongAdder");
        log.info("   ├─ ❌ 频繁读取值用 LongAdder（sum() 开销大）");
        log.info("   └─ ❌ 用 AtomicLong/LongAdder 替代数据库或消息队列");
        log.info("");

        log.info("✓ 总结：");
        log.info("  高竞争? → LongAdder（吞吐量优先）");
        log.info("  频繁读? → AtomicLong（延迟优先）");
        log.info("  都没有? → 考虑根本不需要原子变量");
        log.info("");
    }

    // ============================================
    // main：按顺序运行所有演示
    // ============================================

    public static void main(String[] args) throws InterruptedException {
        log.info("═════════════════════════════════════════════════════════════");
        log.info("原子操作完整演示：CAS + AtomicLong vs LongAdder");
        log.info("教学目标：理解无锁原子性，以及选择合适的原子类");
        log.info("═════════════════════════════════════════════════════════════");
        log.info("");

        // 演示 1：CAS 原理
        demonstrateCASPrinciple();

        // 演示 2 & 3：性能对比
        demonstrateLowContention();
        demonstrateHighContention();

        // 演示 4：CAS 重试开销
        demonstrateCASRetry();

        // 演示 5：决策树
        demonstrateDecisionTree();

        log.info("═════════════════════════════════════════════════════════════");
        log.info("演示完成！");
        log.info("核心收获：");
        log.info("  ✅ CAS 是无锁原子性的基础");
        log.info("  ✅ AtomicLong 适合低竞争、高读取");
        log.info("  ✅ LongAdder 适合高竞争、高写入");
        log.info("  ✅ 选择要考虑实际竞争度和访问模式");
        log.info("═════════════════════════════════════════════════════════════");
    }
}
