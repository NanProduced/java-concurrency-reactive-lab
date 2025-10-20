package nan.tech.lab07.basics;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * EventLoop 工作原理演示
 *
 * <p><b>教学目标</b>：
 * <ul>
 *   <li>理解 Boss vs Worker EventLoopGroup 的职责划分</li>
 *   <li>观察 EventLoop 的任务调度机制（普通任务、定时任务）</li>
 *   <li>验证 Channel 与 EventLoop 的绑定关系（N:1）</li>
 *   <li>监控 EventLoop 的线程状态和队列积压</li>
 * </ul>
 *
 * <p><b>对比 Lab-06</b>：
 * <ul>
 *   <li>Lab-06: 手动创建 Reactor 线程，手动实现负载均衡（Round-Robin）</li>
 *   <li>Lab-07: Netty 自动管理 EventLoop，内置高性能负载均衡算法</li>
 * </ul>
 *
 * <p><b>核心机制</b>：
 * <pre>
 * EventLoop = Thread + Selector + TaskQueue
 *
 * 事件循环流程：
 * 1. select()            ──► 等待 I/O 事件或任务到达
 * 2. processSelectedKeys() ──► 处理 I/O 事件
 * 3. runAllTasks()       ──► 执行用户任务和定时任务
 * 4. 回到步骤 1
 * </pre>
 *
 * <p><b>运行方式</b>：
 * <pre>
 * mvn exec:java -Dexec.mainClass="nan.tech.lab07.basics.EventLoopDemo"
 * </pre>
 *
 * @author nan.tech
 * @since Lab-07
 */
public class EventLoopDemo {

    private static final Logger logger = LoggerFactory.getLogger(EventLoopDemo.class);

    /**
     * 演示 1：Boss vs Worker 线程模型
     *
     * <p><b>架构图</b>：
     * <pre>
     *                客户端连接请求
     *                      │
     *                      ▼
     *          ┌──────────────────────┐
     *          │   ServerBootstrap    │
     *          └──────────────────────┘
     *                      │
     *       ┌──────────────┴──────────────┐
     *       ▼                             ▼
     * ┌───────────────┐          ┌───────────────┐
     * │  BossGroup    │          │  WorkerGroup  │
     * │  (1 EventLoop)│          │  (N EventLoop)│
     * └───────────────┘          └───────────────┘
     *       │                             │
     *       ▼                             ▼
     * Accept 新连接              处理已连接的 I/O
     *       │                             │
     *       └────────► 注册到 ───────────┘
     *                  WorkerGroup
     * </pre>
     *
     * <p><b>核心观察点</b>：
     * <ul>
     *   <li>BossGroup 只有 1 个 EventLoop，专门处理 Accept 事件</li>
     *   <li>WorkerGroup 有多个 EventLoop（默认 = CPU 核心数 * 2）</li>
     *   <li>新连接通过负载均衡算法分配给 WorkerGroup 的某个 EventLoop</li>
     * </ul>
     */
    public static void demoBossWorkerModel() {
        logger.info("=== 演示 1：Boss vs Worker 线程模型 ===");

        // 1. BossGroup：只需要 1 个 EventLoop（单端口监听）
        EventLoopGroup bossGroup = new NioEventLoopGroup(1,
            new DefaultThreadFactory("boss", true));

        // 2. WorkerGroup：默认使用 CPU 核心数 * 2
        int workerThreads = Runtime.getRuntime().availableProcessors() * 2;
        EventLoopGroup workerGroup = new NioEventLoopGroup(workerThreads,
            new DefaultThreadFactory("worker", true));

        logger.info("BossGroup 线程数: 1");
        logger.info("WorkerGroup 线程数: {}", workerThreads);

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        // 打印 Channel 绑定的 EventLoop 线程名
                        String threadName = ch.eventLoop().toString();
                        logger.info("新连接 {} 绑定到 EventLoop: {}",
                            ch.id().asShortText(), threadName);
                    }
                });

            // 绑定端口（不启动，仅演示配置）
            logger.info("EventLoop 配置完成（演示模式，未启动服务器）");

        } finally {
            // 优雅关闭
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
            logger.info("EventLoopGroup 已关闭");
        }
    }

    /**
     * 演示 2：任务调度机制
     *
     * <p><b>任务类型</b>：
     * <ol>
     *   <li><b>普通任务（Runnable）</b>：异步执行，放入 TaskQueue</li>
     *   <li><b>定时任务（ScheduledFuture）</b>：延迟执行或周期执行</li>
     * </ol>
     *
     * <p><b>执行顺序</b>：
     * <pre>
     * I/O 事件 → 普通任务 → 定时任务（如果到期）
     * </pre>
     *
     * <p><b>时间分配</b>：
     * <ul>
     *   <li>默认：I/O 处理 50%，任务处理 50%</li>
     *   <li>可通过 ioRatio 参数调整（如 ioRatio=70 表示 I/O 占 70%）</li>
     * </ul>
     */
    public static void demoTaskScheduling() throws InterruptedException {
        logger.info("\n=== 演示 2：任务调度机制 ===");

        EventLoopGroup group = new NioEventLoopGroup(1,
            new DefaultThreadFactory("scheduler", true));

        try {
            EventLoop eventLoop = group.next();

            // 1. 提交普通任务
            eventLoop.execute(() -> {
                logger.info("[普通任务] 在 EventLoop 线程中执行: {}",
                    Thread.currentThread().getName());
            });

            // 2. 提交延迟任务（延迟 2 秒）
            eventLoop.schedule(() -> {
                logger.info("[延迟任务] 延迟 2 秒后执行");
            }, 2, TimeUnit.SECONDS);

            // 3. 提交周期性任务（每隔 1 秒执行一次，共执行 3 次）
            final int[] counter = {0};
            eventLoop.scheduleAtFixedRate(() -> {
                counter[0]++;
                logger.info("[周期任务] 第 {} 次执行", counter[0]);

                if (counter[0] >= 3) {
                    logger.info("[周期任务] 达到 3 次，主动停止");
                    throw new RuntimeException("Intentional stop");
                }
            }, 1, 1, TimeUnit.SECONDS);

            // 等待任务执行完毕
            logger.info("等待任务执行...");
            Thread.sleep(5000);

        } finally {
            group.shutdownGracefully().sync();
            logger.info("EventLoopGroup 已关闭");
        }
    }

    /**
     * 演示 3：Channel 与 EventLoop 的绑定关系
     *
     * <p><b>核心原则</b>：
     * <pre>
     * Channel : EventLoop = N : 1
     * </pre>
     *
     * <p><b>好处</b>：
     * <ul>
     *   <li><b>无锁设计</b>：同一个 Channel 的事件串行执行，无需加锁</li>
     *   <li><b>高吞吐</b>：避免了线程上下文切换和锁竞争</li>
     * </ul>
     *
     * <p><b>坏处</b>：
     * <ul>
     *   <li><b>单点热点</b>：如果某个 EventLoop 上的连接特别活跃，会导致负载不均衡</li>
     *   <li><b>解决方案</b>：合理配置 WorkerGroup 的线程数（通常为 CPU 核心数 * 2）</li>
     * </ul>
     */
    public static void demoChannelBinding() {
        logger.info("\n=== 演示 3：Channel 与 EventLoop 的绑定关系 ===");

        EventLoopGroup group = new NioEventLoopGroup(2,
            new DefaultThreadFactory("binding", true));

        try {
            // 模拟 10 个 Channel 注册到 EventLoopGroup
            for (int i = 0; i < 10; i++) {
                final int channelId = i;
                EventLoop eventLoop = group.next(); // 通过负载均衡选择 EventLoop

                eventLoop.execute(() -> {
                    logger.info("Channel-{} 绑定到 EventLoop: {}",
                        channelId, Thread.currentThread().getName());
                });
            }

            // 验证负载均衡：每个 EventLoop 应该处理约 5 个 Channel
            Thread.sleep(1000);
            logger.info("负载均衡结果：每个 EventLoop 应该处理约 5 个 Channel");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("演示被中断", e);
        } finally {
            group.shutdownGracefully();
        }
    }

    /**
     * 演示 4：EventLoop 状态监控
     *
     * <p><b>监控指标</b>：
     * <ul>
     *   <li><b>待处理任务数</b>：TaskQueue 中的任务数量</li>
     *   <li><b>线程状态</b>：是否在 EventLoop 线程中</li>
     *   <li><b>关闭状态</b>：是否正在关闭或已关闭</li>
     * </ul>
     *
     * <p><b>告警阈值</b>：
     * <ul>
     *   <li>待处理任务数 > 1000：可能出现积压，需要调优</li>
     * </ul>
     */
    public static void demoEventLoopMonitoring() throws InterruptedException {
        logger.info("\n=== 演示 4：EventLoop 状态监控 ===");

        EventLoopGroup group = new NioEventLoopGroup(1,
            new DefaultThreadFactory("monitor", true));

        try {
            EventLoop eventLoop = group.next();

            // 1. 检查当前线程是否为 EventLoop 线程
            boolean inEventLoop = eventLoop.inEventLoop();
            logger.info("当前线程是否为 EventLoop 线程: {}", inEventLoop);

            // 2. 提交大量任务，模拟队列积压
            for (int i = 0; i < 100; i++) {
                final int taskId = i;
                eventLoop.execute(() -> {
                    // 模拟耗时操作
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    if (taskId % 20 == 0) {
                        logger.info("执行任务: {}", taskId);
                    }
                });
            }

            // 3. 在 EventLoop 线程中检查待处理任务数
            eventLoop.execute(() -> {
                logger.info("在 EventLoop 线程中执行，线程名: {}",
                    Thread.currentThread().getName());

                // 注意：NioEventLoop 没有直接暴露 pendingTasks() 方法
                // 实际生产环境需要通过 MBean 或自定义指标监控
                logger.info("任务正在执行中...");
            });

            // 4. 检查关闭状态
            logger.info("EventLoop 是否正在关闭: {}", eventLoop.isShuttingDown());
            logger.info("EventLoop 是否已关闭: {}", eventLoop.isShutdown());

            // 等待任务执行完毕
            Thread.sleep(3000);

        } finally {
            group.shutdownGracefully().sync();
            logger.info("EventLoop 已优雅关闭");
        }
    }

    /**
     * 主程序入口
     *
     * <p><b>演示流程</b>：
     * <ol>
     *   <li>Boss vs Worker 线程模型</li>
     *   <li>任务调度机制</li>
     *   <li>Channel 与 EventLoop 的绑定关系</li>
     *   <li>EventLoop 状态监控</li>
     * </ol>
     *
     * @param args 命令行参数（未使用）
     */
    public static void main(String[] args) {
        logger.info("========================================");
        logger.info("    Netty EventLoop 工作原理演示");
        logger.info("========================================");

        try {
            // 演示 1：Boss vs Worker 线程模型
            demoBossWorkerModel();

            // 演示 2：任务调度机制
            demoTaskScheduling();

            // 演示 3：Channel 与 EventLoop 的绑定关系
            demoChannelBinding();

            // 演示 4：EventLoop 状态监控
            demoEventLoopMonitoring();

            logger.info("\n========================================");
            logger.info("  所有演示完成！请查看日志输出");
            logger.info("========================================");

        } catch (Exception e) {
            logger.error("演示过程中发生异常", e);
            System.exit(1);
        }
    }
}
