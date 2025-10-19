package nan.tech.lab07.backpressure;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Netty 背压（Backpressure）机制完整演示
 *
 * <p><b>教学目标</b>：
 * <ul>
 *   <li>理解高低水位线（High/Low Water Mark）机制</li>
 *   <li>掌握 {@code isWritable()} 的使用时机</li>
 *   <li>观察 {@code channelWritabilityChanged} 事件触发</li>
 *   <li>学习 4 种背压处理策略（等待、丢弃、降级、入队）</li>
 * </ul>
 *
 * <p><b>核心概念</b>：
 * <pre>
 *                             高水位线 (High Water Mark)
 *                             ────────────────────────
 *                             ▲
 *                             │ 缓冲区积压（不可写）
 *                             │
 * ────────────────────────────┼────────────────────────
 *                             │
 *                             │ 缓冲区正常（可写）
 *                             ▼
 *                             ────────────────────────
 *                             低水位线 (Low Water Mark)
 *
 * 状态转换：
 * 1. 可写状态（Writable）：缓冲区占用 < 低水位线
 * 2. 不可写状态（Unwritable）：缓冲区占用 ≥ 高水位线
 * 3. 恢复可写：缓冲区占用 < 低水位线（需要消费数据后才能恢复）
 * </pre>
 *
 * <p><b>运行方式</b>：
 * <pre>
 * mvn exec:java -Dexec.mainClass="nan.tech.lab07.backpressure.BackpressureDemo"
 *
 * # 在另一个终端测试：
 * telnet localhost 8082
 * # 快速输入大量数据（复制粘贴一大段文本）观察背压触发
 * </pre>
 *
 * @author nan.tech
 * @since Lab-07
 */
public class BackpressureDemo {

    private static final Logger logger = LoggerFactory.getLogger(BackpressureDemo.class);

    /** 演示端口 */
    private static final int PORT = 8082;

    /**
     * 策略 1：等待（Wait）策略
     *
     * <p><b>原理</b>：
     * <ul>
     *   <li>检测到背压时，停止读取上游数据 ({@code setAutoRead(false)})</li>
     *   <li>等待缓冲区可写后，恢复读取 ({@code setAutoRead(true)})</li>
     * </ul>
     *
     * <p><b>优点</b>：
     * <ul>
     *   <li>不丢失数据，保证可靠性</li>
     *   <li>自动调节流速，适应处理能力</li>
     * </ul>
     *
     * <p><b>缺点</b>：
     * <ul>
     *   <li>上游会被阻塞（TCP 接收窗口填满）</li>
     *   <li>端到端延迟增加</li>
     * </ul>
     *
     * <p><b>适用场景</b>：
     * <ul>
     *   <li>高可靠性要求（如：金融交易、订单处理）</li>
     *   <li>客户端可以容忍延迟</li>
     * </ul>
     */
    static class WaitStrategyHandler extends ChannelInboundHandlerAdapter {

        private final AtomicLong droppedCount = new AtomicLong(0);

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ByteBuf in = (ByteBuf) msg;

            try {
                if (ctx.channel().isWritable()) {
                    // ✅ 缓冲区正常，回显数据
                    logger.debug("可写状态，回显数据");
                    ctx.write(in.retain());
                } else {
                    // ❌ 缓冲区满，暂停读取（等待策略）
                    logger.warn("【等待策略】背压触发，暂停读取上游数据");
                    ctx.channel().config().setAutoRead(false);

                    // 注意：这里不释放消息，等待下次可写时重新发送
                    // 实际生产环境需要队列缓存
                }
            } finally {
                ReferenceCountUtil.release(in);
            }
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) {
            ctx.flush();
        }

        @Override
        public void channelWritabilityChanged(ChannelHandlerContext ctx) {
            if (ctx.channel().isWritable()) {
                // ✅ 缓冲区恢复可写，恢复读取
                logger.info("【等待策略】背压释放，恢复读取");
                ctx.channel().config().setAutoRead(true);
            } else {
                logger.warn("【等待策略】进入不可写状态");
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            logger.error("异常", cause);
            ctx.close();
        }
    }

    /**
     * 策略 2：丢弃（Drop）策略
     *
     * <p><b>原理</b>：
     * <ul>
     *   <li>检测到背压时，直接丢弃消息</li>
     *   <li>释放 ByteBuf 内存，避免 OOM</li>
     * </ul>
     *
     * <p><b>优点</b>：
     * <ul>
     *   <li>实现简单，内存安全</li>
     *   <li>不阻塞上游，保持低延迟</li>
     * </ul>
     *
     * <p><b>缺点</b>：
     * <ul>
     *   <li>数据丢失，不保证可靠性</li>
     *   <li>需要上层协议补偿（如：TCP 重传）</li>
     * </ul>
     *
     * <p><b>适用场景</b>：
     * <ul>
     *   <li>实时性优先（如：视频流、游戏、监控数据）</li>
     *   <li>允许丢失部分数据（如：日志采集）</li>
     * </ul>
     */
    static class DropStrategyHandler extends ChannelInboundHandlerAdapter {

        private final AtomicLong droppedCount = new AtomicLong(0);
        private final AtomicLong totalCount = new AtomicLong(0);

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ByteBuf in = (ByteBuf) msg;
            totalCount.incrementAndGet();

            try {
                if (ctx.channel().isWritable()) {
                    // ✅ 缓冲区正常，回显数据
                    ctx.write(in.retain());
                } else {
                    // ❌ 缓冲区满，丢弃消息（丢弃策略）
                    long dropped = droppedCount.incrementAndGet();
                    if (dropped % 100 == 0) {
                        logger.warn("【丢弃策略】背压触发，已丢弃 {} 条消息（总计 {} 条，丢弃率 {:.2f}%）",
                            dropped, totalCount.get(), (dropped * 100.0 / totalCount.get()));
                    }
                    // 消息已在 finally 块中释放
                }
            } finally {
                ReferenceCountUtil.release(in);
            }
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) {
            ctx.flush();
        }

        @Override
        public void channelWritabilityChanged(ChannelHandlerContext ctx) {
            if (ctx.channel().isWritable()) {
                logger.info("【丢弃策略】背压释放，恢复正常");
            } else {
                logger.warn("【丢弃策略】进入背压状态，开始丢弃消息");
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            logger.info("【丢弃策略】连接关闭 - 总计 {} 条消息，丢弃 {} 条（丢弃率 {:.2f}%）",
                totalCount.get(), droppedCount.get(),
                droppedCount.get() * 100.0 / totalCount.get());
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            logger.error("异常", cause);
            ctx.close();
        }
    }

    /**
     * 策略 3：降级（Degrade）策略
     *
     * <p><b>原理</b>：
     * <ul>
     *   <li>检测到背压时，发送降级响应（如："503 Service Unavailable"）</li>
     *   <li>拒绝服务，保护系统稳定性</li>
     * </ul>
     *
     * <p><b>优点</b>：
     * <ul>
     *   <li>主动告知客户端，用户体验好</li>
     *   <li>保护系统不崩溃</li>
     * </ul>
     *
     * <p><b>缺点</b>：
     * <ul>
     *   <li>需要客户端支持重试机制</li>
     *   <li>降级响应本身也会占用缓冲区</li>
     * </ul>
     *
     * <p><b>适用场景</b>：
     * <ul>
     *   <li>HTTP 服务（如：限流、熔断）</li>
     *   <li>需要主动告知客户端的场景</li>
     * </ul>
     */
    static class DegradeStrategyHandler extends ChannelInboundHandlerAdapter {

        private static final String DEGRADED_RESPONSE =
            "503 Service Unavailable - Server is overloaded\n";

        private final AtomicLong degradedCount = new AtomicLong(0);

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ByteBuf in = (ByteBuf) msg;

            try {
                if (ctx.channel().isWritable()) {
                    // ✅ 缓冲区正常，回显数据
                    ctx.write(in.retain());
                } else {
                    // ❌ 缓冲区满，发送降级响应
                    long degraded = degradedCount.incrementAndGet();
                    if (degraded % 50 == 0) {
                        logger.warn("【降级策略】背压触发，已降级 {} 次", degraded);
                    }

                    ByteBuf response = Unpooled.copiedBuffer(DEGRADED_RESPONSE, CharsetUtil.UTF_8);
                    ctx.writeAndFlush(response);
                }
            } finally {
                ReferenceCountUtil.release(in);
            }
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) {
            ctx.flush();
        }

        @Override
        public void channelWritabilityChanged(ChannelHandlerContext ctx) {
            if (ctx.channel().isWritable()) {
                logger.info("【降级策略】背压释放，恢复正常服务");
            } else {
                logger.warn("【降级策略】进入背压状态，开始降级服务");
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            logger.error("异常", cause);
            ctx.close();
        }
    }

    /**
     * 策略 4：入队（Enqueue）策略
     *
     * <p><b>原理</b>：
     * <ul>
     *   <li>检测到背压时，将消息放入本地队列</li>
     *   <li>缓冲区可写后，从队列取出并发送</li>
     * </ul>
     *
     * <p><b>优点</b>：
     * <ul>
     *   <li>不丢失数据，保证可靠性</li>
     *   <li>削峰填谷，平滑流量</li>
     * </ul>
     *
     * <p><b>缺点</b>：
     * <ul>
     *   <li>需要额外的内存存储队列</li>
     *   <li>队列满时仍需降级处理</li>
     *   <li>增加端到端延迟</li>
     * </ul>
     *
     * <p><b>适用场景</b>：
     * <ul>
     *   <li>高可靠性 + 可容忍延迟（如：消息队列）</li>
     *   <li>流量波动大的场景</li>
     * </ul>
     */
    static class EnqueueStrategyHandler extends ChannelInboundHandlerAdapter {

        /** 本地队列（限制大小，避免 OOM） */
        private final Queue<ByteBuf> pendingMessages = new LinkedBlockingQueue<>(1000);

        private final AtomicLong enqueuedCount = new AtomicLong(0);
        private final AtomicLong dequeuedCount = new AtomicLong(0);
        private final AtomicLong overflowCount = new AtomicLong(0);

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ByteBuf in = (ByteBuf) msg;

            if (ctx.channel().isWritable() && pendingMessages.isEmpty()) {
                // ✅ 缓冲区正常且队列为空，直接发送
                ctx.write(in.retain());
            } else {
                // ❌ 缓冲区满或队列有积压，入队
                if (pendingMessages.offer(in.retain())) {
                    long enqueued = enqueuedCount.incrementAndGet();
                    if (enqueued % 100 == 0) {
                        logger.warn("【入队策略】消息入队，当前队列深度: {}", pendingMessages.size());
                    }
                } else {
                    // 队列满，执行降级策略（丢弃或返回错误）
                    long overflow = overflowCount.incrementAndGet();
                    logger.error("【入队策略】队列溢出，已溢出 {} 条消息", overflow);
                }
            }

            ReferenceCountUtil.release(in);
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) {
            ctx.flush();
        }

        @Override
        public void channelWritabilityChanged(ChannelHandlerContext ctx) {
            if (ctx.channel().isWritable()) {
                // ✅ 缓冲区恢复可写，处理队列中的消息
                logger.info("【入队策略】背压释放，处理队列中的 {} 条消息", pendingMessages.size());
                flushPendingMessages(ctx);
            } else {
                logger.warn("【入队策略】进入背压状态，消息开始入队");
            }
        }

        /**
         * 刷新队列中的消息
         */
        private void flushPendingMessages(ChannelHandlerContext ctx) {
            while (ctx.channel().isWritable() && !pendingMessages.isEmpty()) {
                ByteBuf msg = pendingMessages.poll();
                if (msg != null) {
                    ctx.write(msg);
                    dequeuedCount.incrementAndGet();
                }
            }
            ctx.flush();

            if (!pendingMessages.isEmpty()) {
                logger.warn("【入队策略】队列未清空，剩余 {} 条消息", pendingMessages.size());
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            logger.info("【入队策略】连接关闭 - 入队 {} 条，出队 {} 条，溢出 {} 条",
                enqueuedCount.get(), dequeuedCount.get(), overflowCount.get());

            // 清理队列中的消息
            while (!pendingMessages.isEmpty()) {
                ByteBuf msg = pendingMessages.poll();
                if (msg != null) {
                    ReferenceCountUtil.release(msg);
                }
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            logger.error("异常", cause);
            ctx.close();
        }
    }

    /**
     * 启动演示服务器
     *
     * @param strategy 背压策略：wait / drop / degrade / enqueue
     * @throws InterruptedException 如果启动被中断
     */
    public static void startServer(String strategy) throws InterruptedException {
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK,
                    new WriteBufferWaterMark(8 * 1024, 16 * 1024)) // 8KB / 16KB（较小，容易触发背压）
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelHandler handler;
                        switch (strategy.toLowerCase()) {
                            case "wait":
                                handler = new WaitStrategyHandler();
                                break;
                            case "drop":
                                handler = new DropStrategyHandler();
                                break;
                            case "degrade":
                                handler = new DegradeStrategyHandler();
                                break;
                            case "enqueue":
                                handler = new EnqueueStrategyHandler();
                                break;
                            default:
                                throw new IllegalArgumentException("未知策略: " + strategy);
                        }
                        ch.pipeline().addLast(handler);
                    }
                });

            ChannelFuture f = b.bind(PORT).sync();
            logger.info("========================================");
            logger.info("  背压演示服务器已启动");
            logger.info("  端口: {}", PORT);
            logger.info("  策略: {}", strategy.toUpperCase());
            logger.info("  水位线: 8KB / 16KB");
            logger.info("========================================");
            logger.info("请使用 telnet localhost {} 测试", PORT);
            logger.info("快速输入大量数据（复制粘贴）观察背压触发\n");

            f.channel().closeFuture().sync();

        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    /**
     * 主程序入口
     *
     * <p><b>使用方式</b>：
     * <pre>
     * # 等待策略
     * mvn exec:java -Dexec.mainClass="nan.tech.lab07.backpressure.BackpressureDemo" \
     *   -Dexec.args="wait"
     *
     * # 丢弃策略
     * mvn exec:java -Dexec.mainClass="nan.tech.lab07.backpressure.BackpressureDemo" \
     *   -Dexec.args="drop"
     *
     * # 降级策略
     * mvn exec:java -Dexec.mainClass="nan.tech.lab07.backpressure.BackpressureDemo" \
     *   -Dexec.args="degrade"
     *
     * # 入队策略
     * mvn exec:java -Dexec.mainClass="nan.tech.lab07.backpressure.BackpressureDemo" \
     *   -Dexec.args="enqueue"
     * </pre>
     *
     * @param args args[0]: 背压策略（wait/drop/degrade/enqueue，默认 wait）
     */
    public static void main(String[] args) {
        String strategy = (args.length > 0) ? args[0] : "wait";

        logger.info("========================================");
        logger.info("    Netty 背压机制演示");
        logger.info("========================================");

        try {
            startServer(strategy);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("服务器被中断", e);
            System.exit(1);
        }
    }
}
