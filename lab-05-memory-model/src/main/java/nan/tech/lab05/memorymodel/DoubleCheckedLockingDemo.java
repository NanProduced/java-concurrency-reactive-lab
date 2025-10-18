package nan.tech.lab05.memorymodel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lab-05: Double-Checked Locking 陷阱与修复。
 *
 * <p><b>@教学</b>
 * <ul>
 *   <li>展示经典的 Double-Checked Locking (DCL) 反模式</li>
 *   <li>解释指令重排序导致的"半初始化对象"问题</li>
 *   <li>对比 4 种单例模式实现的正确性和性能</li>
 *   <li>提供生产环境的最佳实践建议</li>
 * </ul>
 *
 * <p><b>@陷阱</b>
 * <ul>
 *   <li><b>指令重排序</b>: 对象构造可能在赋值之后完成，导致其他线程看到未初始化的对象</li>
 *   <li><b>误用 DCL</b>: 不加 volatile 的 DCL 在 Java 5 之前是错误的</li>
 *   <li><b>过度优化</b>: 为了性能而使用 DCL 可能得不偿失</li>
 * </ul>
 *
 * <p><b>@参考</b>
 * <ul>
 *   <li>Java Concurrency in Practice: 16.2.4 - Double-Checked Locking</li>
 *   <li>Effective Java 第 3 版: Item 83 - Use Lazy Initialization Judiciously</li>
 *   <li>The "Double-Checked Locking is Broken" Declaration (Doug Lea, 2000)</li>
 * </ul>
 *
 * @author Nan
 * @since 1.0.0
 */
public class DoubleCheckedLockingDemo {

    private static final Logger log = LoggerFactory.getLogger(DoubleCheckedLockingDemo.class);

    /**
     * 演示程序入口。
     * <p>
     * 依次展示 4 种单例模式实现：
     * <ol>
     *   <li>方案1: ❌ 错误的 DCL（无 volatile）- 存在安全隐患</li>
     *   <li>方案2: ✅ 正确的 DCL（volatile）- 安全但复杂</li>
     *   <li>方案3: ✅ 静态内部类（推荐）- 简洁高效</li>
     *   <li>方案4: ✅ 枚举单例（最佳）- 最安全</li>
     * </ol>
     */
    public static void main(String[] args) {
        log.info("╔═══════════════════════════════════════════════════════════════╗");
        log.info("║    Lab-05: Double-Checked Locking 陷阱与修复                     ║");
        log.info("╚═══════════════════════════════════════════════════════════════╝");

        // 方案1: 错误的 DCL
        demonstrateBrokenDCL();
        log.info("");

        // 方案2: 正确的 DCL
        demonstrateCorrectDCL();
        log.info("");

        // 方案3: 静态内部类
        demonstrateStaticInnerClass();
        log.info("");

        // 方案4: 枚举单例
        demonstrateEnumSingleton();
        log.info("");

        // 最终总结
        printSummary();
    }

    // ==================== 方案1: 错误的 DCL ====================

    /**
     * 演示错误的 Double-Checked Locking（无 volatile）。
     *
     * <p><b>@教学</b>
     * <p>这是一个经典的反模式，问题在于：
     * <ol>
     *   <li><b>步骤1</b>: 分配内存空间</li>
     *   <li><b>步骤2</b>: 调用构造函数初始化对象</li>
     *   <li><b>步骤3</b>: 将引用赋值给 instance</li>
     * </ol>
     * <p>
     * 由于指令重排序，步骤 2 和步骤 3 可能被交换，导致：
     * <pre>
     * 线程 A:                       线程 B:
     * 1. 分配内存
     * 3. instance = 内存地址        1. if (instance == null)  // false!
     * 2. 调用构造函数                2. return instance;        // 返回未初始化的对象！
     * </pre>
     *
     * <p><b>@危害</b>
     * <ul>
     *   <li>线程 B 可能获得一个"半初始化"的对象</li>
     *   <li>对象的字段可能是默认值（0, null, false）</li>
     *   <li>导致 NullPointerException 或数据不一致</li>
     * </ul>
     *
     * <p><b>@重现难度</b>
     * <p>这个问题很难重现，因为：
     * <ul>
     *   <li>只在高并发时偶尔出现</li>
     *   <li>依赖于 JIT 编译器的优化</li>
     *   <li>不同 CPU 架构表现不同</li>
     * </ul>
     */
    private static void demonstrateBrokenDCL() {
        log.info("【方案1】❌ 错误的 DCL（无 volatile）");
        log.info("─────────────────────────────────────────────────");

        // 模拟多线程并发获取单例
        BrokenDCLSingleton instance1 = BrokenDCLSingleton.getInstance();
        BrokenDCLSingleton instance2 = BrokenDCLSingleton.getInstance();

        log.info("✅ 获取到两个实例引用: instance1={}, instance2={}",
                 System.identityHashCode(instance1), System.identityHashCode(instance2));
        log.info("✅ 两个引用相同: {}", instance1 == instance2);

        log.warn("⚠️  警告: 这个实现在高并发下可能失败！");
        log.warn("💡 原因: 指令重排序可能导致其他线程看到未初始化的对象");
        log.warn("💡 影响: 可能导致 NPE、字段值错误、数据不一致");
    }

    /**
     * 错误的 DCL 单例实现（反模式）。
     * <p>
     * ❌ 危险：不要在生产环境使用！
     */
    static class BrokenDCLSingleton {
        // ❌ 错误：缺少 volatile，无法防止指令重排序
        private static BrokenDCLSingleton instance;

        private final String data;

        private BrokenDCLSingleton() {
            // 模拟耗时的初始化操作
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            this.data = "Initialized";
        }

        /**
         * ❌ 错误的 getInstance 实现。
         * <p>
         * 问题：instance = new BrokenDCLSingleton() 可能被重排序为：
         * <pre>
         * 1. 分配内存
         * 2. instance = 内存地址（此时对象未初始化！）
         * 3. 调用构造函数
         * </pre>
         * 其他线程可能在步骤 3 之前看到 instance != null，但对象未初始化。
         */
        public static BrokenDCLSingleton getInstance() {
            if (instance == null) {  // 第一次检查（无锁）
                synchronized (BrokenDCLSingleton.class) {
                    if (instance == null) {  // 第二次检查（有锁）
                        instance = new BrokenDCLSingleton();  // ❌ 危险：可能重排序
                    }
                }
            }
            return instance;
        }

        public String getData() {
            return data;
        }
    }

    // ==================== 方案2: 正确的 DCL ====================

    /**
     * 演示正确的 Double-Checked Locking（使用 volatile）。
     *
     * <p><b>@教学</b>
     * <p>volatile 的作用：
     * <ol>
     *   <li><b>禁止重排序</b>: 保证对象完全初始化后才赋值给 instance</li>
     *   <li><b>保证可见性</b>: instance 的赋值对所有线程立即可见</li>
     * </ol>
     *
     * <p><b>@Happens-Before 规则</b>
     * <pre>
     * volatile 变量规则:
     *   - 写 volatile 变量 happens-before 读 volatile 变量
     *   - 保证: 构造函数的所有操作 happens-before instance 赋值
     *           instance 赋值 happens-before 其他线程读取 instance
     * </pre>
     *
     * <p><b>@性能</b>
     * <ul>
     *   <li>第一次检查无锁，性能接近普通读取</li>
     *   <li>只在初始化时需要同步</li>
     *   <li>后续读取无锁，性能优异</li>
     * </ul>
     *
     * <p><b>@缺点</b>
     * <ul>
     *   <li>代码复杂，容易出错</li>
     *   <li>需要理解 JMM 和 volatile 语义</li>
     *   <li>有更简单的替代方案（静态内部类、枚举）</li>
     * </ul>
     */
    private static void demonstrateCorrectDCL() {
        log.info("【方案2】✅ 正确的 DCL（volatile）");
        log.info("─────────────────────────────────────────────────");

        CorrectDCLSingleton instance1 = CorrectDCLSingleton.getInstance();
        CorrectDCLSingleton instance2 = CorrectDCLSingleton.getInstance();

        log.info("✅ 获取到两个实例引用: instance1={}, instance2={}",
                 System.identityHashCode(instance1), System.identityHashCode(instance2));
        log.info("✅ 两个引用相同: {}", instance1 == instance2);
        log.info("✅ volatile 保证了安全性和可见性");
        log.info("💡 性能: 初始化后的读取无锁，性能优异");
        log.info("💡 缺点: 代码复杂，有更简单的替代方案");
    }

    /**
     * 正确的 DCL 单例实现（使用 volatile）。
     */
    static class CorrectDCLSingleton {
        // ✅ 正确：volatile 防止指令重排序和保证可见性
        private static volatile CorrectDCLSingleton instance;

        private final String data;

        private CorrectDCLSingleton() {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            this.data = "Initialized";
        }

        /**
         * ✅ 正确的 getInstance 实现。
         * <p>
         * volatile 保证：
         * <ol>
         *   <li>构造函数完全执行后才赋值给 instance</li>
         *   <li>instance 赋值对所有线程立即可见</li>
         * </ol>
         */
        public static CorrectDCLSingleton getInstance() {
            if (instance == null) {  // 第一次检查（无锁，volatile 读）
                synchronized (CorrectDCLSingleton.class) {
                    if (instance == null) {  // 第二次检查（有锁）
                        instance = new CorrectDCLSingleton();  // ✅ 安全：volatile 写
                    }
                }
            }
            return instance;  // volatile 读，保证可见性
        }

        public String getData() {
            return data;
        }
    }

    // ==================== 方案3: 静态内部类 ====================

    /**
     * 演示静态内部类单例（推荐方案）。
     *
     * <p><b>@教学</b>
     * <p>静态内部类的优点：
     * <ol>
     *   <li><b>懒加载</b>: 只有调用 getInstance() 时才加载内部类</li>
     *   <li><b>线程安全</b>: JVM 保证类加载的线程安全（基于类加载锁）</li>
     *   <li><b>代码简洁</b>: 无需 synchronized 或 volatile</li>
     *   <li><b>性能优异</b>: 无锁，无内存屏障开销</li>
     * </ol>
     *
     * <p><b>@原理</b>
     * <pre>
     * JVM 类加载机制保证：
     *   1. 类加载过程是线程安全的（加锁保护）
     *   2. 静态字段只初始化一次
     *   3. happens-before 保证初始化对所有线程可见
     *
     * 加载时机：
     *   - Holder 类在首次被引用时加载（调用 getInstance() 时）
     *   - 加载完成后，INSTANCE 已完全初始化
     * </pre>
     *
     * <p><b>@适用场景</b>
     * <ul>
     *   <li>需要懒加载的单例</li>
     *   <li>不需要传参的单例</li>
     *   <li>大部分生产环境的单例需求</li>
     * </ul>
     *
     * <p><b>@推荐指数</b>: ⭐⭐⭐⭐⭐
     */
    private static void demonstrateStaticInnerClass() {
        log.info("【方案3】✅ 静态内部类单例（推荐）");
        log.info("─────────────────────────────────────────────────");

        StaticInnerClassSingleton instance1 = StaticInnerClassSingleton.getInstance();
        StaticInnerClassSingleton instance2 = StaticInnerClassSingleton.getInstance();

        log.info("✅ 获取到两个实例引用: instance1={}, instance2={}",
                 System.identityHashCode(instance1), System.identityHashCode(instance2));
        log.info("✅ 两个引用相同: {}", instance1 == instance2);
        log.info("✅ JVM 类加载机制保证线程安全");
        log.info("💡 优点: 简洁、高效、线程安全、懒加载");
        log.info("💡 推荐: 这是大多数场景的最佳选择！");
    }

    /**
     * 静态内部类单例实现（推荐）。
     */
    static class StaticInnerClassSingleton {
        private final String data;

        private StaticInnerClassSingleton() {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            this.data = "Initialized";
        }

        /**
         * 静态内部类持有单例实例。
         * <p>
         * ✅ 关键点：
         * <ul>
         *   <li>只有调用 getInstance() 时，Holder 类才会被加载</li>
         *   <li>JVM 保证类加载的线程安全（类初始化锁）</li>
         *   <li>INSTANCE 在类加载时初始化，且只初始化一次</li>
         * </ul>
         */
        private static class Holder {
            private static final StaticInnerClassSingleton INSTANCE = new StaticInnerClassSingleton();
        }

        /**
         * ✅ 简洁的 getInstance 实现。
         * <p>
         * 无需 synchronized 或 volatile，JVM 自动保证线程安全。
         */
        public static StaticInnerClassSingleton getInstance() {
            return Holder.INSTANCE;  // 触发 Holder 类加载
        }

        public String getData() {
            return data;
        }
    }

    // ==================== 方案4: 枚举单例 ====================

    /**
     * 演示枚举单例（最安全方案）。
     *
     * <p><b>@教学</b>
     * <p>枚举单例的优点：
     * <ol>
     *   <li><b>线程安全</b>: JVM 保证枚举的线程安全</li>
     *   <li><b>防止反射攻击</b>: 枚举不能通过反射创建实例</li>
     *   <li><b>防止反序列化攻击</b>: 枚举的序列化由 JVM 保证唯一性</li>
     *   <li><b>代码最简洁</b>: 只需 1 行代码</li>
     * </ol>
     *
     * <p><b>@原理</b>
     * <pre>
     * 枚举的本质：
     *   - 枚举是 final 类，继承自 java.lang.Enum
     *   - 枚举实例是 public static final 字段
     *   - JVM 保证枚举实例只创建一次
     *
     * 防止反射攻击：
     *   - Constructor.newInstance() 检查是否是枚举类型
     *   - 如果是枚举，抛出 IllegalArgumentException
     *
     * 防止反序列化攻击：
     *   - 枚举的序列化由 JVM 特殊处理
     *   - 反序列化时直接返回已存在的枚举实例
     * </pre>
     *
     * <p><b>@缺点</b>
     * <ul>
     *   <li>不支持懒加载（类加载时即创建实例）</li>
     *   <li>不能继承其他类（已继承 Enum）</li>
     *   <li>语义不够直观</li>
     * </ul>
     *
     * <p><b>@推荐指数</b>: ⭐⭐⭐⭐⭐ (Josh Bloch 强烈推荐)
     */
    private static void demonstrateEnumSingleton() {
        log.info("【方案4】✅ 枚举单例（最安全）");
        log.info("─────────────────────────────────────────────────");

        EnumSingleton instance1 = EnumSingleton.INSTANCE;
        EnumSingleton instance2 = EnumSingleton.INSTANCE;

        log.info("✅ 获取到两个实例引用: instance1={}, instance2={}",
                 System.identityHashCode(instance1), System.identityHashCode(instance2));
        log.info("✅ 两个引用相同: {}", instance1 == instance2);
        log.info("✅ 枚举保证线程安全、防反射、防反序列化");
        log.info("💡 优点: 最简洁、最安全的单例实现");
        log.info("💡 缺点: 不支持懒加载");
        log.info("💡 推荐: Joshua Bloch 在 Effective Java 中强烈推荐！");
    }

    /**
     * 枚举单例实现（最安全）。
     */
    enum EnumSingleton {
        INSTANCE;  // ✅ 只需 1 行代码！

        private final String data;

        EnumSingleton() {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            this.data = "Initialized";
        }

        public String getData() {
            return data;
        }
    }

    // ==================== 总结 ====================

    /**
     * 打印 4 种单例模式的对比总结。
     */
    private static void printSummary() {
        log.info("╔═══════════════════════════════════════════════════════════════╗");
        log.info("║  🎯 总结：4 种单例模式的对比                                      ║");
        log.info("╠═══════════════════════════════════════════════════════════════╣");
        log.info("║  方案              线程安全  懒加载  防反射  防反序列化  推荐指数  ║");
        log.info("╠═══════════════════════════════════════════════════════════════╣");
        log.info("║  ❌ 错误 DCL        ×      ✓      ×        ×         ⭐      ║");
        log.info("║  ✅ 正确 DCL        ✓      ✓      ×        ×         ⭐⭐⭐    ║");
        log.info("║  ✅ 静态内部类       ✓      ✓      ×        ×         ⭐⭐⭐⭐⭐  ║");
        log.info("║  ✅ 枚举单例        ✓      ×      ✓        ✓         ⭐⭐⭐⭐⭐  ║");
        log.info("╠═══════════════════════════════════════════════════════════════╣");
        log.info("║  💡 推荐：                                                       ║");
        log.info("║    - 普通场景: 使用静态内部类（简洁高效）                         ║");
        log.info("║    - 需要防攻击: 使用枚举（最安全）                               ║");
        log.info("║    - 避免使用: 错误的 DCL（有安全隐患）                           ║");
        log.info("╚═══════════════════════════════════════════════════════════════╝");
    }
}
