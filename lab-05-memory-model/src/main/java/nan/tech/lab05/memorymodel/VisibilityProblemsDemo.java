package nan.tech.lab05.memorymodel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Lab-05: 可见性问题完整演示。
 *
 * <p><b>@教学</b>
 * <ul>
 *   <li>展示 Java 内存模型 (JMM) 中的可见性问题</li>
 *   <li>对比 4 种解决方案的优缺点和性能影响</li>
 *   <li>演示如何诊断和修复可见性问题</li>
 *   <li>提供实战中的最佳实践建议</li>
 * </ul>
 *
 * <p><b>@陷阱</b>
 * <ul>
 *   <li><b>缓存一致性失效</b>: CPU 缓存导致线程看不到其他线程的修改</li>
 *   <li><b>指令重排序</b>: 编译器和 CPU 的优化可能改变指令执行顺序</li>
 *   <li><b>误用 volatile</b>: volatile 只保证可见性和有序性，不保证原子性</li>
 *   <li><b>过度同步</b>: 不加区分地使用 synchronized 会降低性能</li>
 * </ul>
 *
 * <p><b>@参考</b>
 * <ul>
 *   <li>Java Language Specification: Chapter 17 - Threads and Locks</li>
 *   <li>Java Concurrency in Practice: Chapter 3 - Sharing Objects</li>
 *   <li>Doug Lea: JSR-133 (Java Memory Model) FAQ</li>
 * </ul>
 *
 * @author Nan
 * @since 1.0.0
 */
public class VisibilityProblemsDemo {

    private static final Logger log = LoggerFactory.getLogger(VisibilityProblemsDemo.class);

    /**
     * 演示程序入口。
     * <p>
     * 依次运行 4 个场景：
     * <ol>
     *   <li>场景1: ❌ 无保护的共享变量（可见性问题）</li>
     *   <li>场景2: ✅ 使用 volatile 修复（轻量级同步）</li>
     *   <li>场景3: ✅ 使用 synchronized 修复（完整同步）</li>
     *   <li>场景4: ✅ 使用 AtomicBoolean 修复（无锁算法）</li>
     * </ol>
     */
    public static void main(String[] args) throws InterruptedException {
        log.info("╔═══════════════════════════════════════════════════════════════╗");
        log.info("║       Lab-05: 可见性问题完整演示                                 ║");
        log.info("╚═══════════════════════════════════════════════════════════════╝");

        // 场景1: 演示可见性问题
        demonstrateVisibilityProblem();
        Thread.sleep(1000);

        // 场景2: volatile 修复
        demonstrateVolatileFix();
        Thread.sleep(1000);

        // 场景3: synchronized 修复
        demonstrateSynchronizedFix();
        Thread.sleep(1000);

        // 场景4: AtomicBoolean 修复
        demonstrateAtomicFix();
    }

    // ==================== 场景1: 可见性问题演示 ====================

    /**
     * 演示没有 volatile 的可见性问题。
     *
     * <p><b>@教学</b>
     * <p>这是一个经典的可见性问题案例：
     * <ul>
     *   <li>主线程修改 {@code stopFlag} 为 true</li>
     *   <li>工作线程持续检查 {@code stopFlag}，期望退出循环</li>
     *   <li><b>问题</b>: 工作线程可能永远看不到修改（无限循环）</li>
     * </ul>
     *
     * <p><b>@根因</b>
     * <ol>
     *   <li><b>CPU 缓存</b>: 工作线程的 CPU 缓存了 stopFlag 的旧值（false）</li>
     *   <li><b>编译器优化</b>: JIT 编译器可能认为 stopFlag 不会被修改，优化为常量</li>
     *   <li><b>缺少内存屏障</b>: 没有 happens-before 关系保证内存可见性</li>
     * </ol>
     *
     * <p><b>@诊断</b>
     * <p>可见性问题的典型症状：
     * <ul>
     *   <li>程序在单线程或低负载时正常</li>
     *   <li>在多线程高负载时出现卡死或无限循环</li>
     *   <li>添加日志或调试后问题消失（偶然引入了内存屏障）</li>
     * </ul>
     */
    private static void demonstrateVisibilityProblem() throws InterruptedException {
        log.info("\n【场景1】❌ 演示可见性问题（无 volatile）");
        log.info("─────────────────────────────────────────────────");

        // 共享变量：无任何同步保护
        final Holder holder = new Holder();

        // 启动工作线程：持续检查 stopFlag
        Thread worker = new Thread(() -> {
            log.info("⏳ 工作线程启动，等待 stopFlag 变为 true...");
            int iterations = 0;

            // 危险的循环：可能永远无法退出
            while (!holder.stopFlag) {
                iterations++;
                // 注意：这里没有任何同步操作，工作线程可能永远读取缓存的旧值
            }

            log.info("✅ 工作线程检测到 stopFlag=true，退出循环（迭代次数: {}）", iterations);
        }, "Worker-NoVolatile");

        worker.start();
        Thread.sleep(100);  // 让工作线程运行一段时间

        // 主线程修改 stopFlag
        log.info("🔄 主线程设置 stopFlag = true");
        holder.stopFlag = true;

        // 等待工作线程退出（设置超时，避免无限等待）
        worker.join(2000);

        // 检查工作线程是否成功退出
        if (worker.isAlive()) {
            log.warn("⚠️  工作线程仍在运行！可见性问题导致无限循环！");
            log.warn("💡 原因: 工作线程的 CPU 缓存了 stopFlag 的旧值 (false)");
            worker.interrupt();  // 强制中断
        } else {
            log.info("✅ 工作线程正常退出（可能幸运地看到了修改）");
            log.info("💡 注意: 这只是偶然情况，不能保证每次都成功！");
        }
    }

    /**
     * 辅助类：持有共享状态（无同步保护）。
     * <p>
     * 这是一个反模式示例，用于教学演示。
     */
    static class Holder {
        // ❌ 错误：没有 volatile，缺少可见性保证
        boolean stopFlag = false;
    }

    // ==================== 场景2: volatile 修复 ====================

    /**
     * 使用 volatile 修复可见性问题。
     *
     * <p><b>@教学</b>
     * <p>volatile 关键字的作用：
     * <ol>
     *   <li><b>可见性</b>: 写入立即刷新到主内存，读取直接从主内存读取</li>
     *   <li><b>有序性</b>: 禁止指令重排序（通过内存屏障实现）</li>
     *   <li><b>非原子性</b>: volatile 不保证复合操作的原子性</li>
     * </ol>
     *
     * <p><b>@实现细节</b>
     * <pre>
     * JVM 在 volatile 变量的读写操作前后插入内存屏障：
     *
     * 写操作:
     *   StoreStore 屏障   // 禁止前面的普通写与 volatile 写重排序
     *   写 volatile 变量
     *   StoreLoad 屏障    // 禁止 volatile 写与后面的 volatile 读/写重排序
     *
     * 读操作:
     *   LoadLoad 屏障     // 禁止后面的普通读与 volatile 读重排序
     *   读 volatile 变量
     *   LoadStore 屏障    // 禁止后面的普通写与 volatile 读重排序
     * </pre>
     *
     * <p><b>@性能影响</b>
     * <ul>
     *   <li>读操作: 接近普通字段（现代 CPU 缓存优化）</li>
     *   <li>写操作: 比普通字段慢（需要刷新缓存）</li>
     *   <li>整体性能: 比 synchronized 快（无锁开销）</li>
     * </ul>
     *
     * <p><b>@适用场景</b>
     * <ul>
     *   <li>状态标志位（如 stopFlag、initialized）</li>
     *   <li>单次赋值的引用（如单例模式的 instance）</li>
     *   <li>读多写少的场景</li>
     * </ul>
     */
    private static void demonstrateVolatileFix() throws InterruptedException {
        log.info("\n【场景2】✅ 使用 volatile 修复可见性问题");
        log.info("─────────────────────────────────────────────────");

        final VolatileHolder holder = new VolatileHolder();

        Thread worker = new Thread(() -> {
            log.info("⏳ 工作线程启动，等待 volatile stopFlag 变为 true...");
            int iterations = 0;

            // 安全的循环：volatile 保证可见性
            while (!holder.stopFlag) {
                iterations++;
            }

            log.info("✅ 工作线程检测到 stopFlag=true，退出循环（迭代次数: {}）", iterations);
        }, "Worker-Volatile");

        worker.start();
        Thread.sleep(100);

        log.info("🔄 主线程设置 volatile stopFlag = true（立即刷新到主内存）");
        holder.stopFlag = true;  // volatile 写：立即对其他线程可见

        worker.join(2000);

        if (worker.isAlive()) {
            log.error("❌ 工作线程仍在运行！volatile 未生效（JVM 实现问题）");
            worker.interrupt();
        } else {
            log.info("✅ 工作线程正常退出（volatile 保证可见性）");
            log.info("💡 性能: volatile 读写开销小，适合读多写少场景");
        }
    }

    /**
     * 使用 volatile 的正确示例。
     */
    static class VolatileHolder {
        // ✅ 正确：volatile 保证可见性和有序性
        volatile boolean stopFlag = false;
    }

    // ==================== 场景3: synchronized 修复 ====================

    /**
     * 使用 synchronized 修复可见性问题。
     *
     * <p><b>@教学</b>
     * <p>synchronized 关键字的作用：
     * <ol>
     *   <li><b>可见性</b>: 释放锁时刷新到主内存，获取锁时从主内存读取</li>
     *   <li><b>原子性</b>: 保证临界区内的操作不可分割</li>
     *   <li><b>有序性</b>: 禁止临界区内外的指令重排序</li>
     * </ol>
     *
     * <p><b>@Happens-Before 规则</b>
     * <pre>
     * 监视器锁规则 (Monitor Lock Rule):
     *   - 解锁操作 happens-before 后续的加锁操作
     *   - 保证: 线程 A 解锁前的所有操作对线程 B 加锁后可见
     * </pre>
     *
     * <p><b>@性能影响</b>
     * <ul>
     *   <li>线程竞争时需要阻塞和唤醒（上下文切换开销）</li>
     *   <li>锁升级过程：无锁 → 偏向锁 → 轻量级锁 → 重量级锁</li>
     *   <li>适度竞争下性能可接受（JVM 锁优化）</li>
     * </ul>
     *
     * <p><b>@适用场景</b>
     * <ul>
     *   <li>需要保证复合操作的原子性（如 check-then-act）</li>
     *   <li>临界区较大，涉及多个字段的修改</li>
     *   <li>写操作较多的场景</li>
     * </ul>
     */
    private static void demonstrateSynchronizedFix() throws InterruptedException {
        log.info("\n【场景3】✅ 使用 synchronized 修复可见性问题");
        log.info("─────────────────────────────────────────────────");

        final SynchronizedHolder holder = new SynchronizedHolder();

        Thread worker = new Thread(() -> {
            log.info("⏳ 工作线程启动，等待 synchronized stopFlag 变为 true...");
            int iterations = 0;

            // 安全的循环：synchronized 保证可见性
            while (!holder.getStopFlag()) {
                iterations++;
            }

            log.info("✅ 工作线程检测到 stopFlag=true，退出循环（迭代次数: {}）", iterations);
        }, "Worker-Synchronized");

        worker.start();
        Thread.sleep(100);

        log.info("🔄 主线程设置 synchronized stopFlag = true（解锁时刷新）");
        holder.setStopFlag(true);  // synchronized 写：解锁时刷新到主内存

        worker.join(2000);

        if (worker.isAlive()) {
            log.error("❌ 工作线程仍在运行！synchronized 未生效（JVM 实现问题）");
            worker.interrupt();
        } else {
            log.info("✅ 工作线程正常退出（synchronized 保证可见性和原子性）");
            log.info("💡 性能: synchronized 有锁开销，但提供了更强的同步保证");
        }
    }

    /**
     * 使用 synchronized 的正确示例。
     */
    static class SynchronizedHolder {
        private boolean stopFlag = false;  // 由 synchronized 保护

        // ✅ 正确：使用 synchronized 保护读操作
        public synchronized boolean getStopFlag() {
            return stopFlag;
        }

        // ✅ 正确：使用 synchronized 保护写操作
        public synchronized void setStopFlag(boolean value) {
            this.stopFlag = value;
        }
    }

    // ==================== 场景4: AtomicBoolean 修复 ====================

    /**
     * 使用 AtomicBoolean 修复可见性问题。
     *
     * <p><b>@教学</b>
     * <p>AtomicBoolean 的特点：
     * <ol>
     *   <li><b>无锁算法</b>: 基于 CAS (Compare-And-Swap) 实现</li>
     *   <li><b>可见性</b>: 内部使用 volatile，保证可见性</li>
     *   <li><b>原子性</b>: CAS 保证 compareAndSet 的原子性</li>
     * </ol>
     *
     * <p><b>@实现原理</b>
     * <pre>
     * AtomicBoolean 内部实现（简化版）:
     *
     * class AtomicBoolean {
     *     private volatile int value;  // 0=false, 1=true
     *
     *     public boolean compareAndSet(boolean expect, boolean update) {
     *         int e = expect ? 1 : 0;
     *         int u = update ? 1 : 0;
     *         return unsafe.compareAndSwapInt(this, valueOffset, e, u);
     *     }
     * }
     *
     * CAS 伪代码:
     *   if (内存值 == 期望值) {
     *       内存值 = 新值;
     *       return true;
     *   } else {
     *       return false;
     *   }
     * </pre>
     *
     * <p><b>@性能对比</b>
     * <ul>
     *   <li>比 synchronized 快（无锁，无上下文切换）</li>
     *   <li>比 volatile 略慢（CAS 循环开销）</li>
     *   <li>高并发下性能优秀（无阻塞）</li>
     * </ul>
     *
     * <p><b>@适用场景</b>
     * <ul>
     *   <li>需要原子性的布尔标志（如初始化状态）</li>
     *   <li>高并发的状态切换（如 started/stopped）</li>
     *   <li>需要 CAS 操作的场景（如自旋锁）</li>
     * </ul>
     */
    private static void demonstrateAtomicFix() throws InterruptedException {
        log.info("\n【场景4】✅ 使用 AtomicBoolean 修复可见性问题");
        log.info("─────────────────────────────────────────────────");

        final AtomicHolder holder = new AtomicHolder();

        Thread worker = new Thread(() -> {
            log.info("⏳ 工作线程启动，等待 AtomicBoolean stopFlag 变为 true...");
            int iterations = 0;

            // 安全的循环：AtomicBoolean 保证可见性
            while (!holder.stopFlag.get()) {
                iterations++;
            }

            log.info("✅ 工作线程检测到 stopFlag=true，退出循环（迭代次数: {}）", iterations);
        }, "Worker-Atomic");

        worker.start();
        Thread.sleep(100);

        log.info("🔄 主线程设置 AtomicBoolean stopFlag = true（CAS 操作）");
        holder.stopFlag.set(true);  // AtomicBoolean 写：volatile + CAS

        worker.join(2000);

        if (worker.isAlive()) {
            log.error("❌ 工作线程仍在运行！AtomicBoolean 未生效（JVM 实现问题）");
            worker.interrupt();
        } else {
            log.info("✅ 工作线程正常退出（AtomicBoolean 保证可见性和原子性）");
            log.info("💡 性能: AtomicBoolean 无锁高效，适合高并发场景");
        }

        log.info("\n╔═══════════════════════════════════════════════════════════════╗");
        log.info("║  🎯 总结：4 种解决方案的对比                                      ║");
        log.info("╠═══════════════════════════════════════════════════════════════╣");
        log.info("║  方案                可见性  原子性  有序性  性能      适用场景       ║");
        log.info("╠═══════════════════════════════════════════════════════════════╣");
        log.info("║  ❌ 无保护            ×      ×      ×      最快    ❌ 不适用      ║");
        log.info("║  ✅ volatile          ✓      ×      ✓      快      状态标志       ║");
        log.info("║  ✅ synchronized      ✓      ✓      ✓      中等    复合操作       ║");
        log.info("║  ✅ AtomicBoolean     ✓      ✓      ✓      快      高并发状态     ║");
        log.info("╚═══════════════════════════════════════════════════════════════╝");
    }

    /**
     * 使用 AtomicBoolean 的正确示例。
     */
    static class AtomicHolder {
        // ✅ 正确：AtomicBoolean 保证可见性和原子性
        final AtomicBoolean stopFlag = new AtomicBoolean(false);
    }
}
