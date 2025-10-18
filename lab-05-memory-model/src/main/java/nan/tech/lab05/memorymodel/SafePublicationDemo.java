package nan.tech.lab05.memorymodel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

/**
 * Lab-05: 安全发布模式对比演示。
 *
 * <p><b>@教学</b>
 * <ul>
 *   <li>展示对象逸出（Object Escape）的危害</li>
 *   <li>对比 5 种安全发布模式的正确性和适用场景</li>
 *   <li>演示不可变对象的发布特性</li>
 *   <li>提供生产环境的安全发布最佳实践</li>
 * </ul>
 *
 * <p><b>@陷阱</b>
 * <ul>
 *   <li><b>构造函数中的 this 逸出</b>: 在构造函数中将 this 引用传递给其他线程</li>
 *   <li><b>非 final 字段的可见性</b>: 其他线程可能看到字段的默认值</li>
 *   <li><b>错误的双重检查</b>: 发布对象时缺少同步保护</li>
 *   <li><b>集合的不安全发布</b>: 使用非线程安全的集合发布对象</li>
 * </ul>
 *
 * <p><b>@参考</b>
 * <ul>
 *   <li>Java Concurrency in Practice: Chapter 3.5 - Safe Publication</li>
 *   <li>Effective Java 第 3 版: Item 83 - 使用延迟初始化要小心</li>
 *   <li>JSR-133: Java Memory Model and Thread Specification</li>
 * </ul>
 *
 * @author Nan
 * @since 1.0.0
 */
public class SafePublicationDemo {

    private static final Logger log = LoggerFactory.getLogger(SafePublicationDemo.class);

    /**
     * 演示程序入口。
     * <p>
     * 依次展示 6 种发布模式：
     * <ol>
     *   <li>场景1: ❌ 不安全的发布（对象逸出）</li>
     *   <li>场景2: ❌ 构造函数中的 this 逸出</li>
     *   <li>场景3: ✅ 使用 volatile 安全发布</li>
     *   <li>场景4: ✅ 使用 synchronized 安全发布</li>
     *   <li>场景5: ✅ 使用线程安全容器安全发布</li>
     *   <li>场景6: ✅ 不可变对象的安全发布</li>
     * </ol>
     */
    public static void main(String[] args) throws InterruptedException {
        log.info("╔═══════════════════════════════════════════════════════════════╗");
        log.info("║       Lab-05: 安全发布模式对比演示                               ║");
        log.info("╚═══════════════════════════════════════════════════════════════╝");

        // 场景1: 不安全的发布
        demonstrateUnsafePublication();
        Thread.sleep(500);

        // 场景2: this 逸出
        demonstrateThisEscape();
        Thread.sleep(500);

        // 场景3: volatile 安全发布
        demonstrateVolatilePublication();
        Thread.sleep(500);

        // 场景4: synchronized 安全发布
        demonstrateSynchronizedPublication();
        Thread.sleep(500);

        // 场景5: 线程安全容器安全发布
        demonstrateConcurrentContainerPublication();
        Thread.sleep(500);

        // 场景6: 不可变对象的安全发布
        demonstrateImmutablePublication();
        Thread.sleep(500);

        // 最终总结
        printSummary();
    }

    // ==================== 场景1: 不安全的发布 ====================

    /**
     * 演示不安全的对象发布。
     *
     * <p><b>@教学</b>
     * <p>不安全发布的问题：
     * <ol>
     *   <li>其他线程可能看到对象的部分初始化状态</li>
     *   <li>非 final 字段的值可能是默认值（0, null, false）</li>
     *   <li>即使构造函数已完成，其他线程仍可能看到旧值</li>
     * </ol>
     *
     * <p><b>@根因</b>
     * <pre>
     * 线程 A（发布者）:                线程 B（读取者）:
     * 1. 分配内存
     * 2. 初始化字段 x = 10            1. if (holder != null)  // 可能为 true!
     * 3. 初始化字段 y = 20            2. int x = holder.x;    // 可能读到 0!
     * 4. holder = 新对象              3. int y = holder.y;    // 可能读到 0!
     *
     * 问题：步骤 2/3/4 可能被重排序，导致 holder 先被赋值
     * </pre>
     *
     * <p><b>@危害</b>
     * <ul>
     *   <li>数据不一致：字段值不符合业务逻辑</li>
     *   <li>NullPointerException：引用类型字段为 null</li>
     *   <li>安全隐患：安全相关字段（如权限标志）可能是默认值</li>
     * </ul>
     */
    private static void demonstrateUnsafePublication() throws InterruptedException {
        log.info("\n【场景1】❌ 不安全的对象发布");
        log.info("─────────────────────────────────────────────────");

        final UnsafePublisher publisher = new UnsafePublisher();
        final CountDownLatch latch = new CountDownLatch(1);

        // 读取线程：可能看到未完全初始化的对象
        Thread reader = new Thread(() -> {
            try {
                latch.await();  // 等待发布线程启动
                Thread.sleep(1);  // 给发布线程一点时间

                Holder holder = publisher.getHolder();
                if (holder != null) {
                    int x = holder.x;
                    int y = holder.y;
                    log.info("📖 读取到对象: x={}, y={}", x, y);

                    if (x == 0 || y == 0) {
                        log.warn("⚠️  危险！读到了未初始化的字段值！");
                        log.warn("💡 原因: 对象发布时缺少同步，存在可见性问题");
                    } else {
                        log.info("✅ 幸运地读到了完整的值（不保证每次都成功）");
                    }
                } else {
                    log.info("📖 holder 为 null，发布尚未完成");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "Reader-Unsafe");

        reader.start();

        // 发布线程
        latch.countDown();
        publisher.publish();
        log.info("📝 发布线程已完成对象发布");

        reader.join();
        log.warn("⚠️  警告: 这种发布方式在高并发下可能导致严重问题！");
    }

    /**
     * 不安全的对象发布器（反模式）。
     */
    static class UnsafePublisher {
        // ❌ 错误：缺少 volatile，无可见性保证
        private Holder holder;

        public void publish() {
            // ❌ 危险：对象赋值可能在字段初始化之前对其他线程可见
            holder = new Holder(10, 20);
        }

        public Holder getHolder() {
            return holder;
        }
    }

    /**
     * 可变对象（持有两个 int 字段）。
     */
    static class Holder {
        int x;  // ❌ 非 final，可能被其他线程看到默认值 0
        int y;  // ❌ 非 final，可能被其他线程看到默认值 0

        public Holder(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    // ==================== 场景2: this 逸出 ====================

    /**
     * 演示构造函数中的 this 逸出问题。
     *
     * <p><b>@教学</b>
     * <p>this 逸出的典型场景：
     * <ol>
     *   <li>在构造函数中启动新线程，并将 this 传递给它</li>
     *   <li>在构造函数中注册监听器，传递 this</li>
     *   <li>在构造函数中发布内部类实例（隐式持有 this）</li>
     * </ol>
     *
     * <p><b>@危害</b>
     * <ul>
     *   <li>其他线程可能在对象完全构造之前就访问它</li>
     *   <li>字段可能尚未初始化（即使在构造函数中已赋值）</li>
     *   <li>final 字段的可见性保证可能失效</li>
     * </ul>
     *
     * <p><b>@修复方案</b>
     * <ol>
     *   <li>使用工厂方法：在构造完成后再启动线程</li>
     *   <li>使用私有构造函数 + 静态工厂方法</li>
     *   <li>延迟注册：在构造完成后单独调用 register() 方法</li>
     * </ol>
     */
    private static void demonstrateThisEscape() throws InterruptedException {
        log.info("\n【场景2】❌ 构造函数中的 this 逸出");
        log.info("─────────────────────────────────────────────────");

        log.info("📝 创建 ThisEscapeExample 对象...");
        ThisEscapeExample example = new ThisEscapeExample();

        Thread.sleep(100);  // 等待后台线程执行

        log.warn("⚠️  危险：构造函数中启动线程并传递 this 引用");
        log.warn("💡 问题：后台线程可能在对象完全构造之前就访问它");
        log.warn("💡 修复：使用工厂方法，在构造完成后再启动线程");

        example.shutdown();
    }

    /**
     * this 逸出示例（反模式）。
     */
    static class ThisEscapeExample {
        private final int value;
        private volatile boolean running = true;

        public ThisEscapeExample() {
            value = 42;

            // ❌ 危险：在构造函数中启动线程并传递 this
            new Thread(() -> {
                // 这里可能在 value 赋值之前就访问 this.value
                while (running) {
                    int val = this.value;  // 可能读到 0!
                    if (val == 0) {
                        log.warn("⚠️  this 逸出问题！读到 value=0（未初始化）");
                    }
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }, "ThisEscape-Thread").start();

            // 注意：即使这里 value 已赋值为 42，
            // 由于 this 逸出，后台线程仍可能读到 0
        }

        public void shutdown() {
            running = false;
        }
    }

    // ==================== 场景3: volatile 安全发布 ====================

    /**
     * 使用 volatile 安全发布对象。
     *
     * <p><b>@教学</b>
     * <p>volatile 发布的保证：
     * <ol>
     *   <li><b>可见性</b>: volatile 写之前的所有操作对后续的 volatile 读可见</li>
     *   <li><b>有序性</b>: 禁止 volatile 写与之前的操作重排序</li>
     *   <li><b>传递性</b>: 通过 happens-before 链传递可见性</li>
     * </ol>
     *
     * <p><b>@Happens-Before 规则</b>
     * <pre>
     * 线程 A:                          线程 B:
     * 1. holder = new Holder(10, 20)  3. Holder h = holder;  // volatile 读
     * 2. (volatile 写屏障)              4. int x = h.x;        // 保证看到 10
     *
     * happens-before 链:
     *   Holder 构造 → volatile 写 holder → volatile 读 holder → 读取字段
     * </pre>
     *
     * <p><b>@适用场景</b>
     * <ul>
     *   <li>单次赋值的引用（如单例、配置对象）</li>
     *   <li>不可变对象或有效不可变对象</li>
     *   <li>读多写少的场景</li>
     * </ul>
     */
    private static void demonstrateVolatilePublication() throws InterruptedException {
        log.info("\n【场景3】✅ 使用 volatile 安全发布");
        log.info("─────────────────────────────────────────────────");

        final VolatilePublisher publisher = new VolatilePublisher();
        final CountDownLatch latch = new CountDownLatch(1);

        Thread reader = new Thread(() -> {
            try {
                latch.await();
                Thread.sleep(1);

                Holder holder = publisher.getHolder();
                if (holder != null) {
                    log.info("📖 读取到对象: x={}, y={}", holder.x, holder.y);
                    log.info("✅ volatile 保证了字段的完整可见性");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "Reader-Volatile");

        reader.start();
        latch.countDown();
        publisher.publish();
        log.info("📝 使用 volatile 发布对象");

        reader.join();
        log.info("💡 性能: volatile 读写开销小，适合单次赋值场景");
    }

    /**
     * 使用 volatile 的安全发布器。
     */
    static class VolatilePublisher {
        // ✅ 正确：volatile 保证对象安全发布
        private volatile Holder holder;

        public void publish() {
            // ✅ 安全：volatile 写保证 Holder 构造完成后才对其他线程可见
            holder = new Holder(10, 20);
        }

        public Holder getHolder() {
            return holder;  // volatile 读
        }
    }

    // ==================== 场景4: synchronized 安全发布 ====================

    /**
     * 使用 synchronized 安全发布对象。
     *
     * <p><b>@教学</b>
     * <p>synchronized 发布的保证：
     * <ol>
     *   <li><b>可见性</b>: 解锁操作将所有修改刷新到主内存</li>
     *   <li><b>原子性</b>: 保证发布操作的原子性</li>
     *   <li><b>有序性</b>: 禁止临界区内外的重排序</li>
     * </ol>
     *
     * <p><b>@适用场景</b>
     * <ul>
     *   <li>需要保护多个相关字段的一致性</li>
     *   <li>发布过程涉及复杂操作（如条件判断）</li>
     *   <li>需要同时保证可见性和原子性</li>
     * </ul>
     */
    private static void demonstrateSynchronizedPublication() throws InterruptedException {
        log.info("\n【场景4】✅ 使用 synchronized 安全发布");
        log.info("─────────────────────────────────────────────────");

        final SynchronizedPublisher publisher = new SynchronizedPublisher();
        final CountDownLatch latch = new CountDownLatch(1);

        Thread reader = new Thread(() -> {
            try {
                latch.await();
                Thread.sleep(1);

                Holder holder = publisher.getHolder();
                if (holder != null) {
                    log.info("📖 读取到对象: x={}, y={}", holder.x, holder.y);
                    log.info("✅ synchronized 保证了完整的同步语义");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "Reader-Synchronized");

        reader.start();
        latch.countDown();
        publisher.publish();
        log.info("📝 使用 synchronized 发布对象");

        reader.join();
        log.info("💡 性能: synchronized 有锁开销，但提供最强的同步保证");
    }

    /**
     * 使用 synchronized 的安全发布器。
     */
    static class SynchronizedPublisher {
        private Holder holder;  // 由 synchronized 保护

        public synchronized void publish() {
            // ✅ 安全：synchronized 保证对象安全发布
            holder = new Holder(10, 20);
        }

        public synchronized Holder getHolder() {
            return holder;
        }
    }

    // ==================== 场景5: 线程安全容器安全发布 ====================

    /**
     * 使用线程安全容器安全发布对象。
     *
     * <p><b>@教学</b>
     * <p>线程安全容器的保证：
     * <ol>
     *   <li>容器内部使用 volatile 或 synchronized</li>
     *   <li>put 操作建立 happens-before 关系</li>
     *   <li>get 操作看到 put 之前的所有修改</li>
     * </ol>
     *
     * <p><b>@常用容器</b>
     * <ul>
     *   <li>ConcurrentHashMap: 高性能并发 Map</li>
     *   <li>CopyOnWriteArrayList: 读多写少的 List</li>
     *   <li>ConcurrentLinkedQueue: 无界并发队列</li>
     *   <li>BlockingQueue: 阻塞队列</li>
     * </ul>
     *
     * <p><b>@适用场景</b>
     * <ul>
     *   <li>需要在容器中共享对象</li>
     *   <li>多个生产者和消费者场景</li>
     *   <li>需要高性能并发访问</li>
     * </ul>
     */
    private static void demonstrateConcurrentContainerPublication() throws InterruptedException {
        log.info("\n【场景5】✅ 使用线程安全容器安全发布");
        log.info("─────────────────────────────────────────────────");

        final ConcurrentHashMap<String, Holder> registry = new ConcurrentHashMap<>();
        final CountDownLatch latch = new CountDownLatch(1);

        Thread reader = new Thread(() -> {
            try {
                latch.await();
                Thread.sleep(1);

                Holder holder = registry.get("config");
                if (holder != null) {
                    log.info("📖 从 ConcurrentHashMap 读取到对象: x={}, y={}", holder.x, holder.y);
                    log.info("✅ ConcurrentHashMap 保证了安全发布");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "Reader-Concurrent");

        reader.start();
        latch.countDown();

        // 发布到线程安全容器
        registry.put("config", new Holder(10, 20));
        log.info("📝 将对象发布到 ConcurrentHashMap");

        reader.join();
        log.info("💡 性能: ConcurrentHashMap 高性能，适合共享注册表场景");
    }

    // ==================== 场景6: 不可变对象的安全发布 ====================

    /**
     * 演示不可变对象的安全发布。
     *
     * <p><b>@教学</b>
     * <p>不可变对象的特殊保证：
     * <ol>
     *   <li><b>final 字段的可见性</b>: final 字段在构造完成后对所有线程可见</li>
     *   <li><b>无需同步</b>: 不可变对象可以安全地自由共享</li>
     *   <li><b>线程安全</b>: 不可变对象天然线程安全</li>
     * </ol>
     *
     * <p><b>@Final 字段的保证</b>
     * <pre>
     * JMM 对 final 字段的特殊规则：
     *   1. 构造函数内对 final 字段的写入 happens-before 构造函数结束
     *   2. 构造函数结束 happens-before 将 this 引用赋值给其他变量
     *   3. 其他线程读取该引用 happens-before 读取 final 字段
     *
     * 结论：只要对象正确构造（无 this 逸出），final 字段总是可见的
     * </pre>
     *
     * <p><b>@不可变对象的条件</b>
     * <ol>
     *   <li>所有字段都是 final</li>
     *   <li>对象正确构造（无 this 逸出）</li>
     *   <li>字段引用的对象也是不可变的</li>
     * </ol>
     *
     * <p><b>@推荐指数</b>: ⭐⭐⭐⭐⭐ (最简单、最安全的发布方式)
     */
    private static void demonstrateImmutablePublication() throws InterruptedException {
        log.info("\n【场景6】✅ 不可变对象的安全发布");
        log.info("─────────────────────────────────────────────────");

        final ImmutablePublisher publisher = new ImmutablePublisher();
        final CountDownLatch latch = new CountDownLatch(1);

        Thread reader = new Thread(() -> {
            try {
                latch.await();
                Thread.sleep(1);

                ImmutableHolder holder = publisher.getHolder();
                if (holder != null) {
                    log.info("📖 读取到不可变对象: x={}, y={}", holder.getX(), holder.getY());
                    log.info("✅ final 字段保证了可见性，无需额外同步");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "Reader-Immutable");

        reader.start();
        latch.countDown();
        publisher.publish();
        log.info("📝 发布不可变对象（无需 volatile 或 synchronized）");

        reader.join();
        log.info("💡 性能: 零开销，final 字段的可见性由 JVM 保证");
        log.info("💡 推荐: 优先使用不可变对象，最简单最安全！");
    }

    /**
     * 发布不可变对象（无需同步）。
     */
    static class ImmutablePublisher {
        private ImmutableHolder holder;  // ✅ 不可变对象，无需 volatile

        public void publish() {
            // ✅ 安全：不可变对象可以安全发布
            holder = new ImmutableHolder(10, 20);
        }

        public ImmutableHolder getHolder() {
            return holder;
        }
    }

    /**
     * 不可变对象（所有字段都是 final）。
     */
    static class ImmutableHolder {
        private final int x;  // ✅ final 字段保证可见性
        private final int y;  // ✅ final 字段保证可见性

        public ImmutableHolder(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }
    }

    // ==================== 总结 ====================

    /**
     * 打印 6 种发布模式的对比总结。
     */
    private static void printSummary() {
        log.info("\n╔═══════════════════════════════════════════════════════════════╗");
        log.info("║  🎯 总结：6 种对象发布模式的对比                                  ║");
        log.info("╠═══════════════════════════════════════════════════════════════╣");
        log.info("║  发布方式          安全性  性能    复杂度  适用场景               ║");
        log.info("╠═══════════════════════════════════════════════════════════════╣");
        log.info("║  ❌ 不安全发布      ×      最快    简单    ❌ 禁止使用           ║");
        log.info("║  ❌ this 逸出       ×      -      简单    ❌ 禁止使用           ║");
        log.info("║  ✅ volatile        ✓      快      中等    单次赋值             ║");
        log.info("║  ✅ synchronized    ✓      中等    中等    复合操作             ║");
        log.info("║  ✅ 线程安全容器     ✓      快      简单    共享注册表           ║");
        log.info("║  ✅ 不可变对象      ✓      最快    简单    ⭐⭐⭐⭐⭐ 首选      ║");
        log.info("╠═══════════════════════════════════════════════════════════════╣");
        log.info("║  💡 最佳实践：                                                   ║");
        log.info("║    1. 优先使用不可变对象（final 字段 + 无 this 逸出）             ║");
        log.info("║    2. 共享注册表使用线程安全容器（ConcurrentHashMap）             ║");
        log.info("║    3. 单例模式使用静态内部类或枚举                                ║");
        log.info("║    4. 避免在构造函数中启动线程或注册监听器                         ║");
        log.info("╚═══════════════════════════════════════════════════════════════╝");
    }
}
