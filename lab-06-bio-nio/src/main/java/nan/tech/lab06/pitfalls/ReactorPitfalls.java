package nan.tech.lab06.pitfalls;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Reactor 模式常见陷阱演示
 *
 * <p><strong>教学目标</strong>：
 * <ul>
 *   <li>理解 Reactor 模式中的线程安全问题</li>
 *   <li>掌握跨 Reactor 通信的正确方式</li>
 *   <li>避免资源竞争和死锁</li>
 * </ul>
 *
 * <p><strong>核心知识点</strong>：
 * <pre>
 * Reactor 模式架构:
 *   Main Reactor (Boss)  ← 单线程处理 ACCEPT
 *        ↓ 分发
 *   Sub Reactors (Workers) ← 多线程处理 READ/WRITE
 *
 * 线程安全挑战:
 *   - Boss → Worker 通信（跨线程）
 *   - Worker 之间隔离（无共享）
 *   - Selector 唤醒机制（wakeup）
 * </pre>
 *
 * <p><strong>运行方式</strong>：
 * <pre>
 * mvn exec:java -Dexec.mainClass="nan.tech.lab06.pitfalls.ReactorPitfalls"
 * </pre>
 *
 * @author AI Assistant
 * @since 1.0.0
 */
public class ReactorPitfalls {

    // ==================== 陷阱 1: 跨线程直接 register ====================

    /**
     * ❌ 陷阱 1: 跨线程直接 register 导致死锁
     *
     * <p><strong>错误现象</strong>：
     * <pre>
     * 程序挂起，不响应请求
     * jstack 显示两个线程互相等待
     * </pre>
     *
     * <p><strong>根本原因</strong>：
     * <pre>
     * channel.register() 需要获取 Selector 的锁
     * 而 selector.select() 也持有这个锁
     * 导致死锁
     *
     * 死锁场景:
     *   Thread1 (Worker): selector.select()       ← 持有锁，阻塞等待事件
     *   Thread2 (Boss):   channel.register(...)   ← 等待获取锁
     *   → 死锁！
     * </pre>
     */
    public static void pitfall1_DirectCrossThreadRegister() {
        System.out.println("========================================");
        System.out.println("陷阱 1: 跨线程直接 register");
        System.out.println("========================================");

        System.out.println("❌ 错误代码示例:");
        System.out.println("   // Thread 1 (Boss Reactor)");
        System.out.println("   SocketChannel clientChannel = serverChannel.accept();");
        System.out.println("   clientChannel.register(workerSelector, OP_READ);");
        System.out.println("   // ❌ 跨线程直接注册，可能死锁");
        System.out.println();
        System.out.println("   // Thread 2 (Worker Reactor)");
        System.out.println("   selector.select(); // 持有 Selector 锁");
        System.out.println();
        System.out.println("问题:");
        System.out.println("   - register() 需要 Selector 内部锁");
        System.out.println("   - select() 持有同一把锁");
        System.out.println("   - 跨线程调用导致死锁");
        System.out.println();
    }

    /**
     * ✅ 正确方案: 使用队列 + wakeup()
     *
     * <p><strong>实现原理</strong>：
     * <pre>
     * 1. Boss 线程: 将 Channel 放入队列
     * 2. Boss 线程: 调用 wakeup() 唤醒 Worker
     * 3. Worker 线程: 从队列取出 Channel
     * 4. Worker 线程: 在自己的线程内 register
     *
     * 关键: register 操作在 Worker 自己的线程内执行
     * </pre>
     */
    public static void solution1_QueuePlusWakeup() {
        System.out.println("✅ 正确方案: 队列 + wakeup()");
        System.out.println("----------------------------------------");

        System.out.println("✅ 正确代码示例:");
        System.out.println("   // Boss Reactor");
        System.out.println("   SocketChannel channel = serverChannel.accept();");
        System.out.println("   pendingChannels.offer(channel); // ✅ 放入队列");
        System.out.println("   workerSelector.wakeup();        // ✅ 唤醒 Worker");
        System.out.println();
        System.out.println("   // Worker Reactor");
        System.out.println("   while (running) {");
        System.out.println("       // 处理待注册的 Channel");
        System.out.println("       SocketChannel ch;");
        System.out.println("       while ((ch = pendingChannels.poll()) != null) {");
        System.out.println("           ch.register(selector, OP_READ); // ✅ 同线程注册");
        System.out.println("       }");
        System.out.println("       selector.select(); // 继续事件循环");
        System.out.println("   }");
        System.out.println();
        System.out.println("原理:");
        System.out.println("   - 使用线程安全队列（ConcurrentLinkedQueue）");
        System.out.println("   - wakeup() 唤醒阻塞的 select()");
        System.out.println("   - Worker 在自己线程内 register（无锁竞争）");
        System.out.println();
    }

    // ==================== 陷阱 2: 负载均衡不均导致性能下降 ====================

    /**
     * ❌ 陷阱 2: 简单轮询导致负载不均
     *
     * <p><strong>错误现象</strong>：
     * <pre>
     * 部分 Worker 非常忙，部分 Worker 很闲
     * 整体 TPS 低于预期
     * </pre>
     *
     * <p><strong>根本原因</strong>：
     * <pre>
     * 简单的 round-robin 轮询不考虑 Worker 负载
     * 可能出现:
     *   Worker1: 100 个长连接（很忙）
     *   Worker2: 5 个短连接（很闲）
     *
     * 结果: Worker1 成为瓶颈
     * </pre>
     */
    public static void pitfall2_SimpleRoundRobin() {
        System.out.println("========================================");
        System.out.println("陷阱 2: 简单轮询导致负载不均");
        System.out.println("========================================");

        System.out.println("❌ 错误代码示例:");
        System.out.println("   int index = roundRobinCounter.getAndIncrement() % workerCount;");
        System.out.println("   workers[index].register(channel);");
        System.out.println("   // ❌ 不考虑 Worker 当前负载");
        System.out.println();
        System.out.println("问题:");
        System.out.println("   - 不考虑 Worker 当前连接数");
        System.out.println("   - 不考虑 Worker 当前负载");
        System.out.println("   - 可能导致负载不均");
        System.out.println();
    }

    /**
     * ✅ 正确方案: 基于负载的选择策略
     *
     * <p><strong>策略选择</strong>：
     * <ul>
     *   <li>最少连接数（Least Connections）</li>
     *   <li>最低负载（Least Load）</li>
     *   <li>加权轮询（Weighted Round Robin）</li>
     * </ul>
     */
    public static void solution2_LeastConnectionsStrategy() {
        System.out.println("✅ 正确方案: 最少连接数策略");
        System.out.println("----------------------------------------");

        System.out.println("✅ 正确代码示例:");
        System.out.println("   // 选择连接数最少的 Worker");
        System.out.println("   SubReactor leastBusyWorker = workers[0];");
        System.out.println("   int minConnections = workers[0].getConnectionCount();");
        System.out.println();
        System.out.println("   for (SubReactor worker : workers) {");
        System.out.println("       int connections = worker.getConnectionCount();");
        System.out.println("       if (connections < minConnections) {");
        System.out.println("           minConnections = connections;");
        System.out.println("           leastBusyWorker = worker;");
        System.out.println("       }");
        System.out.println("   }");
        System.out.println();
        System.out.println("   leastBusyWorker.register(channel);");
        System.out.println();
        System.out.println("原理:");
        System.out.println("   - 选择连接数最少的 Worker");
        System.out.println("   - 保持 Worker 之间负载均衡");
        System.out.println("   - 提升整体吞吐量");
        System.out.println();
    }

    // ==================== 陷阱 3: 阻塞操作在 Reactor 线程 ====================

    /**
     * ❌ 陷阱 3: 在 Reactor 线程执行阻塞操作
     *
     * <p><strong>错误现象</strong>：
     * <pre>
     * 请求延迟大幅增加
     * TPS 急剧下降
     * CPU 使用率很低
     * </pre>
     *
     * <p><strong>根本原因</strong>：
     * <pre>
     * Reactor 线程应该只做非阻塞 I/O
     * 任何阻塞操作都会阻塞整个事件循环
     *
     * 常见错误:
     *   - 数据库查询（JDBC 阻塞）
     *   - 文件读写（可能阻塞）
     *   - 复杂计算（CPU 密集）
     *   - 同步 RPC 调用
     * </pre>
     */
    public static void pitfall3_BlockingOperationInReactor() {
        System.out.println("========================================");
        System.out.println("陷阱 3: 阻塞操作在 Reactor 线程");
        System.out.println("========================================");

        System.out.println("❌ 错误代码示例:");
        System.out.println("   private void handleRead(SelectionKey key) {");
        System.out.println("       // 读取数据");
        System.out.println("       String request = readFromChannel(key);");
        System.out.println();
        System.out.println("       // ❌ 错误: 在 Reactor 线程执行数据库查询");
        System.out.println("       String result = database.query(request); // 阻塞!");
        System.out.println();
        System.out.println("       // 写入响应");
        System.out.println("       writeToChannel(key, result);");
        System.out.println("   }");
        System.out.println();
        System.out.println("问题:");
        System.out.println("   - 数据库查询可能耗时几十毫秒");
        System.out.println("   - Reactor 线程被阻塞");
        System.out.println("   - 其他连接无法处理");
        System.out.println("   - 整体 TPS 急剧下降");
        System.out.println();
    }

    /**
     * ✅ 正确方案: 业务逻辑交给线程池
     *
     * <p><strong>架构模式</strong>：
     * <pre>
     * Reactor 线程:   只做非阻塞 I/O
     *                 ↓
     * 业务线程池:     处理业务逻辑（可阻塞）
     *                 ↓
     * Reactor 线程:   写回响应
     * </pre>
     */
    public static void solution3_UseBusinessThreadPool() {
        System.out.println("✅ 正确方案: 业务逻辑交给线程池");
        System.out.println("----------------------------------------");

        System.out.println("✅ 正确代码示例:");
        System.out.println("   private void handleRead(SelectionKey key) {");
        System.out.println("       // Reactor 线程: 只读取数据");
        System.out.println("       String request = readFromChannel(key);");
        System.out.println();
        System.out.println("       // ✅ 提交到业务线程池");
        System.out.println("       businessThreadPool.submit(() -> {");
        System.out.println("           // 业务线程: 可以阻塞");
        System.out.println("           String result = database.query(request);");
        System.out.println();
        System.out.println("           // 业务线程: 提交回 Reactor");
        System.out.println("           submitWriteTask(key, result);");
        System.out.println("       });");
        System.out.println("   }");
        System.out.println();
        System.out.println("原理:");
        System.out.println("   - Reactor 线程专注于 I/O 多路复用");
        System.out.println("   - 业务线程池处理阻塞操作");
        System.out.println("   - 分离关注点，提升性能");
        System.out.println();
    }

    // ==================== 陷阱 4: 共享状态无同步 ====================

    /**
     * ❌ 陷阱 4: Worker 之间共享状态无同步
     *
     * <p><strong>错误现象</strong>：
     * <pre>
     * 数据不一致
     * 偶发性错误（难以复现）
     * 统计数据不准确
     * </pre>
     *
     * <p><strong>根本原因</strong>：
     * <pre>
     * 多个 Worker 线程同时访问共享变量
     * 没有使用同步机制
     *
     * 常见错误:
     *   - 共享计数器（非原子）
     *   - 共享 Map（非线程安全）
     *   - 共享状态对象
     * </pre>
     */
    public static void pitfall4_SharedStateWithoutSync() {
        System.out.println("========================================");
        System.out.println("陷阱 4: 共享状态无同步");
        System.out.println("========================================");

        System.out.println("❌ 错误代码示例:");
        System.out.println("   // 全局共享变量");
        System.out.println("   private int totalConnections = 0; // ❌ 非线程安全");
        System.out.println();
        System.out.println("   // Worker 线程");
        System.out.println("   private void handleAccept(SocketChannel channel) {");
        System.out.println("       totalConnections++; // ❌ 非原子操作");
        System.out.println("   }");
        System.out.println();
        System.out.println("问题:");
        System.out.println("   - totalConnections++ 不是原子操作");
        System.out.println("   - 多线程并发会丢失更新");
        System.out.println("   - 统计数据不准确");
        System.out.println();
    }

    /**
     * ✅ 正确方案: 使用原子类或锁
     *
     * <p><strong>方案选择</strong>：
     * <ul>
     *   <li>方案 1: AtomicInteger/AtomicLong（推荐）</li>
     *   <li>方案 2: synchronized 或 Lock</li>
     *   <li>方案 3: 无共享（每个 Worker 独立统计）</li>
     * </ul>
     */
    public static void solution4_UseAtomicOrLock() {
        System.out.println("✅ 正确方案: 使用原子类");
        System.out.println("----------------------------------------");

        System.out.println("✅ 正确代码示例:");
        System.out.println("   // 方案 1: 使用 AtomicInteger");
        System.out.println("   private final AtomicInteger totalConnections = new AtomicInteger(0);");
        System.out.println();
        System.out.println("   private void handleAccept(SocketChannel channel) {");
        System.out.println("       totalConnections.incrementAndGet(); // ✅ 原子操作");
        System.out.println("   }");
        System.out.println();
        System.out.println("   // 方案 2: 无共享（推荐）");
        System.out.println("   class SubReactor {");
        System.out.println("       private int localConnections = 0; // 每个 Worker 独立");
        System.out.println("   }");
        System.out.println();
        System.out.println("   // 获取总数时汇总");
        System.out.println("   int getTotalConnections() {");
        System.out.println("       return Arrays.stream(workers)");
        System.out.println("           .mapToInt(w -> w.localConnections)");
        System.out.println("           .sum();");
        System.out.println("   }");
        System.out.println();
        System.out.println("原理:");
        System.out.println("   - AtomicInteger 使用 CAS 保证原子性");
        System.out.println("   - 无共享模式避免竞争（更优）");
        System.out.println("   - 按需汇总，减少同步开销");
        System.out.println();
    }

    // ==================== Main 方法（演示入口）====================

    /**
     * 主方法：运行所有陷阱演示
     *
     * <p><strong>学习建议</strong>：
     * <ul>
     *   <li>理解 Reactor 模式的线程模型</li>
     *   <li>掌握跨线程通信的正确方式</li>
     *   <li>区分 I/O 操作和业务逻辑</li>
     *   <li>避免共享状态，减少同步</li>
     * </ul>
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        System.out.println("\n");
        System.out.println("╔════════════════════════════════════════════╗");
        System.out.println("║  Reactor 模式常见陷阱演示（反面教材）      ║");
        System.out.println("╚════════════════════════════════════════════╝");
        System.out.println();

        // 陷阱 1: 跨线程直接 register
        pitfall1_DirectCrossThreadRegister();
        solution1_QueuePlusWakeup();

        // 陷阱 2: 简单轮询导致负载不均
        pitfall2_SimpleRoundRobin();
        solution2_LeastConnectionsStrategy();

        // 陷阱 3: 阻塞操作在 Reactor 线程
        pitfall3_BlockingOperationInReactor();
        solution3_UseBusinessThreadPool();

        // 陷阱 4: 共享状态无同步
        pitfall4_SharedStateWithoutSync();
        solution4_UseAtomicOrLock();

        System.out.println("========================================");
        System.out.println("所有陷阱演示完成！");
        System.out.println("========================================");
        System.out.println("\n💡 学习要点:");
        System.out.println("  1. 跨线程注册用队列 + wakeup()");
        System.out.println("  2. 负载均衡考虑 Worker 当前负载");
        System.out.println("  3. Reactor 线程只做非阻塞 I/O");
        System.out.println("  4. 避免共享状态，优先无锁设计");
        System.out.println();
        System.out.println("📚 架构原则:");
        System.out.println("  - Reactor 模式: 关注点分离");
        System.out.println("    * Boss: 只处理 ACCEPT");
        System.out.println("    * Worker: 只处理 I/O");
        System.out.println("    * Business: 处理业务逻辑");
        System.out.println("  - 线程安全: 无共享 > 原子类 > 锁");
        System.out.println("  - 性能优化: 负载均衡 + 非阻塞 I/O");
        System.out.println();
    }
}
