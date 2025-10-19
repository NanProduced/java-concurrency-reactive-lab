package nan.tech.lab06.pitfalls;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * ByteBuffer 常见陷阱演示
 *
 * <p><strong>教学目标</strong>：
 * <ul>
 *   <li>通过"反面教材"加深对 ByteBuffer 的理解</li>
 *   <li>演示 5 个最常见的 ByteBuffer 错误使用场景</li>
 *   <li>提供正确的解决方案和原理解释</li>
 * </ul>
 *
 * <p><strong>核心知识点</strong>：
 * <pre>
 * ByteBuffer 三大核心指针:
 *   - position: 当前读/写位置
 *   - limit:    可读/写的边界
 *   - capacity: 容量（不可变）
 *
 * 状态转换:
 *   写模式 → flip()  → 读模式
 *   读模式 → clear() → 写模式
 *   读模式 → compact() → 写模式（保留未读数据）
 * </pre>
 *
 * <p><strong>运行方式</strong>：
 * <pre>
 * mvn exec:java -Dexec.mainClass="nan.tech.lab06.pitfalls.ByteBufferPitfalls"
 * </pre>
 *
 * @author AI Assistant
 * @since 1.0.0
 */
public class ByteBufferPitfalls {

    // ==================== 陷阱 1: 忘记 flip() ====================

    /**
     * ❌ 陷阱 1: 忘记 flip() 导致读取空数据
     *
     * <p><strong>错误现象</strong>：
     * <pre>
     * 写入数据后直接读取，得到空字符串或错误数据
     * </pre>
     *
     * <p><strong>根本原因</strong>：
     * <pre>
     * 写入后:  position = 5, limit = 1024
     * 直接读:  从 position=5 开始读，读到的是未初始化数据
     * </pre>
     */
    public static void pitfall1_ForgotFlip() {
        System.out.println("========================================");
        System.out.println("陷阱 1: 忘记 flip()");
        System.out.println("========================================");

        ByteBuffer buffer = ByteBuffer.allocate(1024);

        // 写入数据
        buffer.put("Hello".getBytes(StandardCharsets.UTF_8));
        System.out.println("写入后: position=" + buffer.position() + ", limit=" + buffer.limit());

        // ❌ 错误：直接读取
        String wrongResult = new String(buffer.array(), 0, buffer.position());
        System.out.println("❌ 错误读取: '" + wrongResult + "'");
        System.out.println("   问题: 虽然看起来正确，但这是直接访问底层数组，而不是通过 ByteBuffer API");

        // 如果尝试使用 remaining() 读取
        System.out.println("   buffer.remaining() = " + buffer.remaining());
        System.out.println("   → 期望读到 5 字节，实际 remaining=1019 (limit - position)");

        System.out.println();
    }

    /**
     * ✅ 正确方案: 使用 flip() 切换模式
     *
     * <p><strong>原理解释</strong>：
     * <pre>
     * flip() 的作用:
     *   1. limit = position     (设置可读边界)
     *   2. position = 0         (重置读位置)
     *   3. mark = -1            (清除标记)
     *
     * 效果:
     *   写入后: position=5, limit=1024
     *   flip(): position=0, limit=5
     *   现在可以从头读取 5 字节数据
     * </pre>
     */
    public static void solution1_UseFlip() {
        System.out.println("✅ 正确方案: 使用 flip()");
        System.out.println("----------------------------------------");

        ByteBuffer buffer = ByteBuffer.allocate(1024);

        // 写入数据
        buffer.put("Hello".getBytes(StandardCharsets.UTF_8));
        System.out.println("写入后: position=" + buffer.position() + ", limit=" + buffer.limit());

        // ✅ 正确：flip() 切换到读模式
        buffer.flip();
        System.out.println("flip后: position=" + buffer.position() + ", limit=" + buffer.limit());

        // 正确读取
        byte[] data = new byte[buffer.remaining()];
        buffer.get(data);
        String correctResult = new String(data, StandardCharsets.UTF_8);
        System.out.println("✅ 正确读取: '" + correctResult + "'");

        System.out.println();
    }

    // ==================== 陷阱 2: 重复读取不 rewind() ====================

    /**
     * ❌ 陷阱 2: 重复读取不 rewind() 导致第二次读取为空
     *
     * <p><strong>错误现象</strong>：
     * <pre>
     * 第一次读取成功，第二次读取得到空数据
     * </pre>
     *
     * <p><strong>根本原因</strong>：
     * <pre>
     * 第一次读取后: position = limit (已读完)
     * 第二次读取:    remaining() = 0 (无数据可读)
     * </pre>
     */
    public static void pitfall2_ForgotRewind() {
        System.out.println("========================================");
        System.out.println("陷阱 2: 重复读取不 rewind()");
        System.out.println("========================================");

        ByteBuffer buffer = ByteBuffer.allocate(1024);
        buffer.put("Hello".getBytes(StandardCharsets.UTF_8));
        buffer.flip();

        // 第一次读取
        byte[] firstRead = new byte[buffer.remaining()];
        buffer.get(firstRead);
        System.out.println("第一次读取: '" + new String(firstRead, StandardCharsets.UTF_8) + "'");
        System.out.println("读取后: position=" + buffer.position() + ", limit=" + buffer.limit());

        // ❌ 错误：直接第二次读取
        byte[] secondRead = new byte[buffer.remaining()];
        buffer.get(secondRead);
        System.out.println("❌ 第二次读取: '" + new String(secondRead, StandardCharsets.UTF_8) + "' (空!)");
        System.out.println("   问题: position=limit，无数据可读");

        System.out.println();
    }

    /**
     * ✅ 正确方案: 使用 rewind() 或 clear()
     *
     * <p><strong>方法选择</strong>：
     * <ul>
     *   <li>rewind(): 重置 position=0，保留 limit（用于重复读）</li>
     *   <li>clear():  重置 position=0, limit=capacity（用于重新写）</li>
     * </ul>
     */
    public static void solution2_UseRewind() {
        System.out.println("✅ 正确方案: 使用 rewind()");
        System.out.println("----------------------------------------");

        ByteBuffer buffer = ByteBuffer.allocate(1024);
        buffer.put("Hello".getBytes(StandardCharsets.UTF_8));
        buffer.flip();

        // 第一次读取
        byte[] firstRead = new byte[buffer.remaining()];
        buffer.get(firstRead);
        System.out.println("第一次读取: '" + new String(firstRead, StandardCharsets.UTF_8) + "'");

        // ✅ 正确：rewind() 重置读位置
        buffer.rewind();
        System.out.println("rewind后: position=" + buffer.position() + ", limit=" + buffer.limit());

        // 第二次读取
        byte[] secondRead = new byte[buffer.remaining()];
        buffer.get(secondRead);
        System.out.println("✅ 第二次读取: '" + new String(secondRead, StandardCharsets.UTF_8) + "'");

        System.out.println();
    }

    // ==================== 陷阱 3: 半包问题不使用 compact() ====================

    /**
     * ❌ 陷阱 3: 半包问题不使用 compact() 导致数据丢失
     *
     * <p><strong>错误现象</strong>：
     * <pre>
     * 网络接收数据时，第一次收到 "Hello"，第二次收到 "World"
     * 期望拼接成 "HelloWorld"，实际只得到 "World"
     * </pre>
     *
     * <p><strong>根本原因</strong>：
     * <pre>
     * 使用 clear() 会清空整个 buffer，包括未读完的数据
     * 应该使用 compact() 保留未读数据
     * </pre>
     */
    public static void pitfall3_UseClearInsteadOfCompact() {
        System.out.println("========================================");
        System.out.println("陷阱 3: 半包问题不使用 compact()");
        System.out.println("========================================");

        ByteBuffer buffer = ByteBuffer.allocate(1024);

        // 第一次接收数据: "Hello"
        buffer.put("Hello".getBytes(StandardCharsets.UTF_8));
        System.out.println("第一次写入: position=" + buffer.position());

        // ❌ 错误：使用 clear() 清空 buffer
        buffer.clear();
        System.out.println("clear后: position=" + buffer.position() + ", limit=" + buffer.limit());

        // 第二次接收数据: "World"
        buffer.put("World".getBytes(StandardCharsets.UTF_8));
        buffer.flip();

        // 读取数据
        byte[] data = new byte[buffer.remaining()];
        buffer.get(data);
        System.out.println("❌ 最终读取: '" + new String(data, StandardCharsets.UTF_8) + "'");
        System.out.println("   问题: 只得到 'World'，'Hello' 丢失了!");

        System.out.println();
    }

    /**
     * ✅ 正确方案: 使用 compact() 保留未读数据
     *
     * <p><strong>compact() 原理</strong>：
     * <pre>
     * 1. 将 position 到 limit 之间的数据移到 buffer 开头
     * 2. position = limit - position (移动后的写位置)
     * 3. limit = capacity
     *
     * 示例:
     *   初始状态:  [H][e][l][l][o][ ][ ]...
     *              pos=0, limit=5
     *   读取 2 字节: pos=2, limit=5
     *   compact():  [l][l][o][ ][ ][ ]...
     *              pos=3, limit=1024
     *   → 继续写入新数据
     * </pre>
     */
    public static void solution3_UseCompact() {
        System.out.println("✅ 正确方案: 使用 compact()");
        System.out.println("----------------------------------------");

        ByteBuffer buffer = ByteBuffer.allocate(1024);

        // 第一次接收数据: "Hello"
        buffer.put("Hello".getBytes(StandardCharsets.UTF_8));
        System.out.println("第一次写入: position=" + buffer.position());

        // ✅ 正确：使用 compact() 保留数据
        buffer.flip(); // 先切换到读模式
        buffer.compact(); // 保留未读数据，切换回写模式
        System.out.println("compact后: position=" + buffer.position() + ", limit=" + buffer.limit());

        // 第二次接收数据: "World"
        buffer.put("World".getBytes(StandardCharsets.UTF_8));
        buffer.flip();

        // 读取数据
        byte[] data = new byte[buffer.remaining()];
        buffer.get(data);
        System.out.println("✅ 最终读取: '" + new String(data, StandardCharsets.UTF_8) + "'");

        System.out.println();
    }

    // ==================== 陷阱 4: 直接访问 array() 超出 position ====================

    /**
     * ❌ 陷阱 4: 直接访问 array() 读取超出实际数据
     *
     * <p><strong>错误现象</strong>：
     * <pre>
     * 读取到未初始化的垃圾数据或空字节
     * </pre>
     *
     * <p><strong>根本原因</strong>：
     * <pre>
     * buffer.array() 返回整个底层数组
     * 必须结合 position/limit 限定读取范围
     * </pre>
     */
    public static void pitfall4_DirectArrayAccess() {
        System.out.println("========================================");
        System.out.println("陷阱 4: 直接访问 array() 超出范围");
        System.out.println("========================================");

        ByteBuffer buffer = ByteBuffer.allocate(1024);
        buffer.put("Hello".getBytes(StandardCharsets.UTF_8));

        // ❌ 错误：直接读取整个数组
        String wrongResult = new String(buffer.array(), StandardCharsets.UTF_8);
        System.out.println("❌ 错误读取长度: " + wrongResult.length() + " (期望 5)");
        System.out.println("   问题: 读取了整个 1024 字节数组，包含大量空字节");

        System.out.println();
    }

    /**
     * ✅ 正确方案: 使用 position/limit 限定范围
     */
    public static void solution4_UsePositionAndLimit() {
        System.out.println("✅ 正确方案: 使用 position/limit");
        System.out.println("----------------------------------------");

        ByteBuffer buffer = ByteBuffer.allocate(1024);
        buffer.put("Hello".getBytes(StandardCharsets.UTF_8));

        // ✅ 正确：限定读取范围
        String correctResult = new String(buffer.array(), 0, buffer.position(), StandardCharsets.UTF_8);
        System.out.println("✅ 正确读取: '" + correctResult + "' (长度: " + correctResult.length() + ")");

        System.out.println();
    }

    // ==================== 陷阱 5: 多线程共享 ByteBuffer ====================

    /**
     * ❌ 陷阱 5: 多线程共享 ByteBuffer 导致数据混乱
     *
     * <p><strong>错误现象</strong>：
     * <pre>
     * 数据读取错位、丢失或损坏
     * </pre>
     *
     * <p><strong>根本原因</strong>：
     * <pre>
     * ByteBuffer 不是线程安全的！
     *   - position/limit 是实例变量
     *   - 多线程同时 put()/get() 会互相干扰
     * </pre>
     */
    public static void pitfall5_SharedByteBuffer() {
        System.out.println("========================================");
        System.out.println("陷阱 5: 多线程共享 ByteBuffer");
        System.out.println("========================================");

        ByteBuffer sharedBuffer = ByteBuffer.allocate(1024);

        // ❌ 错误：多线程共享同一个 ByteBuffer
        Thread thread1 = new Thread(() -> {
            sharedBuffer.put("Thread1".getBytes(StandardCharsets.UTF_8));
        });

        Thread thread2 = new Thread(() -> {
            sharedBuffer.put("Thread2".getBytes(StandardCharsets.UTF_8));
        });

        thread1.start();
        thread2.start();

        try {
            thread1.join();
            thread2.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 读取数据
        sharedBuffer.flip();
        byte[] data = new byte[sharedBuffer.remaining()];
        sharedBuffer.get(data);
        String result = new String(data, StandardCharsets.UTF_8);
        System.out.println("❌ 读取结果: '" + result + "'");
        System.out.println("   问题: 可能出现数据混乱或 IndexOutOfBoundsException");

        System.out.println();
    }

    /**
     * ✅ 正确方案: 每个线程使用独立的 ByteBuffer
     *
     * <p><strong>方案选择</strong>：
     * <ul>
     *   <li>方案 1: 每个线程创建独立的 ByteBuffer</li>
     *   <li>方案 2: 使用 ThreadLocal&lt;ByteBuffer&gt;</li>
     *   <li>方案 3: 使用 duplicate() 创建独立视图（共享底层数组但独立指针）</li>
     * </ul>
     */
    public static void solution5_ThreadLocalByteBuffer() {
        System.out.println("✅ 正确方案: 使用 ThreadLocal");
        System.out.println("----------------------------------------");

        // ✅ 正确：每个线程独立的 ByteBuffer
        ThreadLocal<ByteBuffer> threadLocalBuffer = ThreadLocal.withInitial(() -> ByteBuffer.allocate(1024));

        Thread thread1 = new Thread(() -> {
            ByteBuffer buffer = threadLocalBuffer.get();
            buffer.put("Thread1".getBytes(StandardCharsets.UTF_8));
            buffer.flip();
            byte[] data = new byte[buffer.remaining()];
            buffer.get(data);
            System.out.println("Thread1 读取: '" + new String(data, StandardCharsets.UTF_8) + "'");
        });

        Thread thread2 = new Thread(() -> {
            ByteBuffer buffer = threadLocalBuffer.get();
            buffer.put("Thread2".getBytes(StandardCharsets.UTF_8));
            buffer.flip();
            byte[] data = new byte[buffer.remaining()];
            buffer.get(data);
            System.out.println("Thread2 读取: '" + new String(data, StandardCharsets.UTF_8) + "'");
        });

        thread1.start();
        thread2.start();

        try {
            thread1.join();
            thread2.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("✅ 两个线程独立操作，无数据混乱");
        System.out.println();
    }

    // ==================== Main 方法（演示入口）====================

    /**
     * 主方法：运行所有陷阱演示
     *
     * <p><strong>学习建议</strong>：
     * <ul>
     *   <li>先运行一遍观察错误现象</li>
     *   <li>阅读代码理解原理</li>
     *   <li>对比错误和正确方案</li>
     *   <li>自己手写一遍加深印象</li>
     * </ul>
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        System.out.println("\n");
        System.out.println("╔════════════════════════════════════════════╗");
        System.out.println("║  ByteBuffer 常见陷阱演示（反面教材）       ║");
        System.out.println("╚════════════════════════════════════════════╝");
        System.out.println();

        // 陷阱 1: 忘记 flip()
        pitfall1_ForgotFlip();
        solution1_UseFlip();

        // 陷阱 2: 重复读取不 rewind()
        pitfall2_ForgotRewind();
        solution2_UseRewind();

        // 陷阱 3: 半包问题不使用 compact()
        pitfall3_UseClearInsteadOfCompact();
        solution3_UseCompact();

        // 陷阱 4: 直接访问 array() 超出范围
        pitfall4_DirectArrayAccess();
        solution4_UsePositionAndLimit();

        // 陷阱 5: 多线程共享 ByteBuffer
        pitfall5_SharedByteBuffer();
        solution5_ThreadLocalByteBuffer();

        System.out.println("========================================");
        System.out.println("所有陷阱演示完成！");
        System.out.println("========================================");
        System.out.println("\n💡 学习要点:");
        System.out.println("  1. flip() 是写→读的必经之路");
        System.out.println("  2. rewind() 用于重复读，clear() 用于重新写");
        System.out.println("  3. compact() 保留未读数据（半包处理）");
        System.out.println("  4. array() 需要结合 position/limit 使用");
        System.out.println("  5. ByteBuffer 不是线程安全的");
        System.out.println();
    }
}
