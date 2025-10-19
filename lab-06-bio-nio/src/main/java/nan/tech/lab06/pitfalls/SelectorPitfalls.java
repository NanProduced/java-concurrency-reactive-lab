package nan.tech.lab06.pitfalls;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Set;

/**
 * Selector 常见陷阱演示
 *
 * <p><strong>教学目标</strong>：
 * <ul>
 *   <li>演示 Selector 使用中的常见错误</li>
 *   <li>理解 SelectionKey 的正确管理方式</li>
 *   <li>掌握资源释放和内存泄漏防范</li>
 * </ul>
 *
 * <p><strong>核心知识点</strong>：
 * <pre>
 * Selector 关键概念:
 *   - Selector.select():    阻塞等待事件就绪
 *   - selectedKeys():       返回就绪事件集合
 *   - SelectionKey:         Channel 注册后的句柄
 *   - wakeup():             唤醒阻塞的 select()
 * </pre>
 *
 * <p><strong>运行方式</strong>：
 * <pre>
 * mvn exec:java -Dexec.mainClass="nan.tech.lab06.pitfalls.SelectorPitfalls"
 * </pre>
 *
 * @author AI Assistant
 * @since 1.0.0
 */
public class SelectorPitfalls {

    // ==================== 陷阱 1: 忘记从 selectedKeys 移除 ====================

    /**
     * ❌ 陷阱 1: 忘记从 selectedKeys 移除导致重复处理
     *
     * <p><strong>错误现象</strong>：
     * <pre>
     * 同一个事件被重复处理多次
     * 可能导致重复读取、重复写入
     * </pre>
     *
     * <p><strong>根本原因</strong>：
     * <pre>
     * selectedKeys() 返回的集合不会自动清空！
     * 需要手动调用 iterator.remove()
     *
     * 错误代码:
     *   for (SelectionKey key : selector.selectedKeys()) {
     *       handleKey(key); // ❌ 没有 remove，下次 select 时还在
     *   }
     *
     * 正确代码:
     *   Iterator<SelectionKey> it = selector.selectedKeys().iterator();
     *   while (it.hasNext()) {
     *       SelectionKey key = it.next();
     *       it.remove(); // ✅ 必须移除
     *       handleKey(key);
     *   }
     * </pre>
     */
    public static void pitfall1_ForgotToRemoveFromSelectedKeys() {
        System.out.println("========================================");
        System.out.println("陷阱 1: 忘记从 selectedKeys 移除");
        System.out.println("========================================");

        System.out.println("❌ 错误代码示例:");
        System.out.println("   for (SelectionKey key : selector.selectedKeys()) {");
        System.out.println("       handleKey(key); // 没有 remove");
        System.out.println("   }");
        System.out.println();
        System.out.println("问题:");
        System.out.println("   - SelectionKey 会一直存在于 selectedKeys 中");
        System.out.println("   - 下次 select() 返回时仍然包含这个 key");
        System.out.println("   - 导致重复处理同一事件");
        System.out.println();
    }

    /**
     * ✅ 正确方案: 使用 Iterator.remove()
     */
    public static void solution1_UseIteratorRemove() {
        System.out.println("✅ 正确方案: 使用 Iterator.remove()");
        System.out.println("----------------------------------------");

        System.out.println("✅ 正确代码示例:");
        System.out.println("   Iterator<SelectionKey> it = selector.selectedKeys().iterator();");
        System.out.println("   while (it.hasNext()) {");
        System.out.println("       SelectionKey key = it.next();");
        System.out.println("       it.remove(); // ✅ 必须移除");
        System.out.println("       handleKey(key);");
        System.out.println("   }");
        System.out.println();
        System.out.println("原理:");
        System.out.println("   - selectedKeys() 返回的是 Selector 内部集合的引用");
        System.out.println("   - Selector 不会自动清空这个集合");
        System.out.println("   - 必须手动 remove() 才能避免重复处理");
        System.out.println();
    }

    // ==================== 陷阱 2: 忘记检查 key.isValid() ====================

    /**
     * ❌ 陷阱 2: 忘记检查 key.isValid() 导致异常
     *
     * <p><strong>错误现象</strong>：
     * <pre>
     * CancelledKeyException
     * 或 Channel 已关闭异常
     * </pre>
     *
     * <p><strong>根本原因</strong>：
     * <pre>
     * SelectionKey 可能因为以下原因失效:
     *   1. Channel 被关闭
     *   2. 调用了 key.cancel()
     *   3. Selector 被关闭
     *
     * 失效的 key 调用 isReadable()/isWritable() 会抛异常
     * </pre>
     */
    public static void pitfall2_ForgotToCheckValid() {
        System.out.println("========================================");
        System.out.println("陷阱 2: 忘记检查 key.isValid()");
        System.out.println("========================================");

        System.out.println("❌ 错误代码示例:");
        System.out.println("   if (key.isReadable()) { // ❌ 没有先检查 valid");
        System.out.println("       handleRead(key);");
        System.out.println("   }");
        System.out.println();
        System.out.println("问题:");
        System.out.println("   - key 可能已经 cancelled");
        System.out.println("   - isReadable() 会抛出 CancelledKeyException");
        System.out.println("   - 导致整个事件循环中断");
        System.out.println();
    }

    /**
     * ✅ 正确方案: 先检查 isValid()
     */
    public static void solution2_CheckValidFirst() {
        System.out.println("✅ 正确方案: 先检查 isValid()");
        System.out.println("----------------------------------------");

        System.out.println("✅ 正确代码示例:");
        System.out.println("   if (!key.isValid()) { // ✅ 先检查 valid");
        System.out.println("       continue;");
        System.out.println("   }");
        System.out.println("   if (key.isReadable()) {");
        System.out.println("       handleRead(key);");
        System.out.println("   }");
        System.out.println();
        System.out.println("原理:");
        System.out.println("   - isValid() 返回 false 的情况:");
        System.out.println("     * Channel 已关闭");
        System.out.println("     * key.cancel() 已调用");
        System.out.println("     * Selector 已关闭");
        System.out.println("   - 必须在检查事件类型前先检查 valid");
        System.out.println();
    }

    // ==================== 陷阱 3: Channel 关闭不 cancel key ====================

    /**
     * ❌ 陷阱 3: Channel 关闭后不 cancel key 导致内存泄漏
     *
     * <p><strong>错误现象</strong>：
     * <pre>
     * Selector 中的 key 越来越多
     * 内存持续增长，最终 OOM
     * </pre>
     *
     * <p><strong>根本原因</strong>：
     * <pre>
     * Channel.close() 不会自动 cancel SelectionKey
     * 需要手动调用 key.cancel() 释放资源
     *
     * 内存泄漏路径:
     *   Selector → keys 集合 → SelectionKey → Channel → ByteBuffer
     * </pre>
     */
    public static void pitfall3_ForgotToCancelKey() {
        System.out.println("========================================");
        System.out.println("陷阱 3: Channel 关闭不 cancel key");
        System.out.println("========================================");

        System.out.println("❌ 错误代码示例:");
        System.out.println("   channel.close(); // ❌ 只关闭 Channel");
        System.out.println("   // 没有 key.cancel()");
        System.out.println();
        System.out.println("问题:");
        System.out.println("   - SelectionKey 仍然注册在 Selector 中");
        System.out.println("   - key 持有 Channel 引用");
        System.out.println("   - Channel 持有 ByteBuffer 引用");
        System.out.println("   - 导致内存泄漏");
        System.out.println();
    }

    /**
     * ✅ 正确方案: 关闭 Channel 前先 cancel key
     */
    public static void solution3_CancelKeyFirst() {
        System.out.println("✅ 正确方案: 关闭前先 cancel key");
        System.out.println("----------------------------------------");

        System.out.println("✅ 正确代码示例:");
        System.out.println("   private void closeChannel(SelectionKey key) {");
        System.out.println("       key.cancel(); // ✅ 先 cancel key");
        System.out.println("       try {");
        System.out.println("           key.channel().close(); // 再关闭 Channel");
        System.out.println("       } catch (IOException e) {");
        System.out.println("           // 忽略关闭异常");
        System.out.println("       }");
        System.out.println("   }");
        System.out.println();
        System.out.println("原理:");
        System.out.println("   - cancel() 从 Selector 中移除 key");
        System.out.println("   - close() 释放 Channel 资源");
        System.out.println("   - 顺序很重要: 先 cancel，再 close");
        System.out.println();
    }

    // ==================== 陷阱 4: wakeup 时机不当导致死锁 ====================

    /**
     * ❌ 陷阱 4: 注册新 Channel 不 wakeup 导致延迟
     *
     * <p><strong>错误现象</strong>：
     * <pre>
     * 新连接注册后长时间不被处理
     * 需要等到下一次 select() 超时才处理
     * </pre>
     *
     * <p><strong>根本原因</strong>：
     * <pre>
     * Selector 线程阻塞在 select() 上
     * 其他线程注册新 Channel 时，Selector 不知道
     * 需要调用 wakeup() 唤醒 Selector
     *
     * 典型场景:
     *   Thread1: selector.select()      ← 阻塞中
     *   Thread2: channel.register(...)  ← 注册新 Channel
     *   Thread1: (继续阻塞，不知道有新注册)
     * </pre>
     */
    public static void pitfall4_ForgotToWakeup() {
        System.out.println("========================================");
        System.out.println("陷阱 4: 注册新 Channel 不 wakeup");
        System.out.println("========================================");

        System.out.println("❌ 错误代码示例:");
        System.out.println("   // Thread 2 (Boss Reactor)");
        System.out.println("   SocketChannel channel = serverChannel.accept();");
        System.out.println("   channel.register(workerSelector, OP_READ);");
        System.out.println("   // ❌ 没有 wakeup，Worker 可能阻塞在 select()");
        System.out.println();
        System.out.println("问题:");
        System.out.println("   - Worker 线程阻塞在 select() 上");
        System.out.println("   - 新注册的 Channel 不会被立即处理");
        System.out.println("   - 需要等到 select() 超时才能处理");
        System.out.println();
    }

    /**
     * ✅ 正确方案: 注册后调用 wakeup()
     */
    public static void solution4_WakeupAfterRegister() {
        System.out.println("✅ 正确方案: 注册后调用 wakeup()");
        System.out.println("----------------------------------------");

        System.out.println("✅ 正确代码示例:");
        System.out.println("   // Thread 2 (Boss Reactor)");
        System.out.println("   SocketChannel channel = serverChannel.accept();");
        System.out.println("   pendingChannels.offer(channel); // 先放入队列");
        System.out.println("   workerSelector.wakeup(); // ✅ 唤醒 Worker");
        System.out.println();
        System.out.println("   // Thread 1 (Worker Reactor)");
        System.out.println("   while (running) {");
        System.out.println("       // 处理待注册的 Channel");
        System.out.println("       SocketChannel ch;");
        System.out.println("       while ((ch = pendingChannels.poll()) != null) {");
        System.out.println("           ch.register(selector, OP_READ);");
        System.out.println("       }");
        System.out.println("       selector.select(); // 被 wakeup() 唤醒");
        System.out.println("   }");
        System.out.println();
        System.out.println("原理:");
        System.out.println("   - wakeup() 会唤醒阻塞的 select()");
        System.out.println("   - 使用队列传递 Channel（线程安全）");
        System.out.println("   - 避免跨线程直接 register（可能死锁）");
        System.out.println();
    }

    // ==================== 陷阱 5: attachment 泄漏 ====================

    /**
     * ❌ 陷阱 5: attachment 使用不当导致内存泄漏
     *
     * <p><strong>错误现象</strong>：
     * <pre>
     * 内存持续增长
     * 堆转储显示大量 ByteBuffer 未释放
     * </pre>
     *
     * <p><strong>根本原因</strong>：
     * <pre>
     * SelectionKey 的 attachment 持有对象引用
     * 如果不清理，对象无法被 GC
     *
     * 典型错误:
     *   key.attach(ByteBuffer.allocate(1024)); // 创建 attachment
     *   // ... 使用 ...
     *   key.cancel(); // ❌ attachment 还在，buffer 泄漏
     * </pre>
     */
    public static void pitfall5_AttachmentLeak() {
        System.out.println("========================================");
        System.out.println("陷阱 5: attachment 导致内存泄漏");
        System.out.println("========================================");

        System.out.println("❌ 错误代码示例:");
        System.out.println("   key.attach(ByteBuffer.allocate(1024)); // 创建");
        System.out.println("   // ... 使用 ...");
        System.out.println("   key.cancel(); // ❌ attachment 未清理");
        System.out.println();
        System.out.println("问题:");
        System.out.println("   - key.attach() 创建强引用");
        System.out.println("   - key.cancel() 不会清理 attachment");
        System.out.println("   - ByteBuffer 无法被 GC 回收");
        System.out.println();
    }

    /**
     * ✅ 正确方案: 关闭前清理 attachment
     */
    public static void solution5_CleanupAttachment() {
        System.out.println("✅ 正确方案: 关闭前清理 attachment");
        System.out.println("----------------------------------------");

        System.out.println("✅ 正确代码示例:");
        System.out.println("   private void closeChannel(SelectionKey key) {");
        System.out.println("       key.attach(null); // ✅ 清理 attachment");
        System.out.println("       key.cancel();     // 取消注册");
        System.out.println("       try {");
        System.out.println("           key.channel().close(); // 关闭 Channel");
        System.out.println("       } catch (IOException e) {");
        System.out.println("           // 忽略");
        System.out.println("       }");
        System.out.println("   }");
        System.out.println();
        System.out.println("原理:");
        System.out.println("   - attach(null) 清除引用");
        System.out.println("   - ByteBuffer 可以被 GC 回收");
        System.out.println("   - 避免内存泄漏");
        System.out.println();
    }

    // ==================== Main 方法（演示入口）====================

    /**
     * 主方法：运行所有陷阱演示
     *
     * <p><strong>学习建议</strong>：
     * <ul>
     *   <li>理解每个陷阱的根本原因</li>
     *   <li>记住正确的使用模式</li>
     *   <li>在实际代码中应用这些最佳实践</li>
     * </ul>
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        System.out.println("\n");
        System.out.println("╔════════════════════════════════════════════╗");
        System.out.println("║  Selector 常见陷阱演示（反面教材）         ║");
        System.out.println("╚════════════════════════════════════════════╝");
        System.out.println();

        // 陷阱 1: 忘记从 selectedKeys 移除
        pitfall1_ForgotToRemoveFromSelectedKeys();
        solution1_UseIteratorRemove();

        // 陷阱 2: 忘记检查 key.isValid()
        pitfall2_ForgotToCheckValid();
        solution2_CheckValidFirst();

        // 陷阱 3: Channel 关闭不 cancel key
        pitfall3_ForgotToCancelKey();
        solution3_CancelKeyFirst();

        // 陷阱 4: 注册新 Channel 不 wakeup
        pitfall4_ForgotToWakeup();
        solution4_WakeupAfterRegister();

        // 陷阱 5: attachment 泄漏
        pitfall5_AttachmentLeak();
        solution5_CleanupAttachment();

        System.out.println("========================================");
        System.out.println("所有陷阱演示完成！");
        System.out.println("========================================");
        System.out.println("\n💡 学习要点:");
        System.out.println("  1. 必须用 Iterator.remove() 清理 selectedKeys");
        System.out.println("  2. 先检查 isValid()，再检查事件类型");
        System.out.println("  3. 关闭 Channel 前先 cancel key");
        System.out.println("  4. 跨线程注册 Channel 需要 wakeup()");
        System.out.println("  5. 清理 attachment 避免内存泄漏");
        System.out.println();
    }
}
