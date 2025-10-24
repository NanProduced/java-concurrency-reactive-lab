package nan.tech.lab07.echo;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Netty Echo Client - 负载测试客户端
 *
 * <p><b>功能</b>：
 * <ul>
 *   <li>支持多并发连接测试</li>
 *   <li>统计性能指标（TPS、延迟、成功率）</li>
 *   <li>可配置测试参数（连接数、请求数、消息大小）</li>
 * </ul>
 *
 * <p><b>性能指标</b>：
 * <ul>
 *   <li><b>TPS</b>：每秒处理的请求数</li>
 *   <li><b>平均延迟</b>：单次请求的平均响应时间</li>
 *   <li><b>成功率</b>：成功请求占总请求的百分比</li>
 * </ul>
 *
 * <p><b>运行方式</b>：
 * <pre>
 * # 启动服务器（在另一个终端）
 * mvn exec:java -Dexec.mainClass="nan.tech.lab07.echo.NettyEchoServer"
 *
 * # 运行客户端（默认参数：10 连接，每连接 1000 请求）
 * mvn exec:java -Dexec.mainClass="nan.tech.lab07.echo.NettyEchoClient"
 *
 * # 自定义参数
 * mvn exec:java -Dexec.mainClass="nan.tech.lab07.echo.NettyEchoClient" \
 *   -Dexec.args="100 10000"  # 100 连接，每连接 10000 请求
 * </pre>
 *
 * @author nan.tech
 * @since Lab-07
 */
public class NettyEchoClient {

    private static final Logger logger = LoggerFactory.getLogger(NettyEchoClient.class);

    /** 服务器地址 */
    private static final String HOST = "localhost";
    /** 服务器端口 */
    private static final int PORT = 8080;

    /** 并发连接数 */
    private final int connections;
    /** 每个连接发送的请求数 */
    private final int requestsPerConnection;
    /** 测试消息 */
    private final String testMessage;

    /** 总请求数 */
    private final long totalRequests;
    /** 成功计数器 */
    private final AtomicLong successCount = new AtomicLong(0);
    /** 失败计数器 */
    private final AtomicLong failureCount = new AtomicLong(0);
    /** 总延迟（纳秒） */
    private final AtomicLong totalLatencyNanos = new AtomicLong(0);

    /** 测试开始时间 */
    private long startTime;
    /** 测试结束时间 */
    private long endTime;

    /**
     * 构造函数
     *
     * @param connections 并发连接数
     * @param requestsPerConnection 每个连接发送的请求数
     */
    public NettyEchoClient(int connections, int requestsPerConnection) {
        this.connections = connections;
        this.requestsPerConnection = requestsPerConnection;
        this.totalRequests = (long) connections * requestsPerConnection;
        this.testMessage = "Hello Netty Echo Server!";
    }

    /**
     * Echo Client Handler
     *
     * <p><b>功能</b>：
     * <ul>
     *   <li>连接建立后发送测试消息</li>
     *   <li>接收响应并统计延迟</li>
     *   <li>达到请求数后关闭连接</li>
     * </ul>
     */
    class EchoClientHandler extends ChannelInboundHandlerAdapter {

        private final CountDownLatch latch;
        private int sentCount = 0;
        private int receivedCount = 0;
        private long requestStartTime;

        public EchoClientHandler(CountDownLatch latch) {
            this.latch = latch;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            // 连接建立，开始发送请求
            sendNextRequest(ctx);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ByteBuf in = (ByteBuf) msg;
            try {
                // 计算延迟
                long latency = System.nanoTime() - requestStartTime;
                totalLatencyNanos.addAndGet(latency);

                // 验证响应
                String response = in.toString(CharsetUtil.UTF_8);
                if (response.equals(testMessage)) {
                    successCount.incrementAndGet();
                } else {
                    failureCount.incrementAndGet();
                    logger.warn("响应不匹配: expected={}, actual={}", testMessage, response);
                }

                receivedCount++;

                // 继续发送下一个请求
                if (sentCount < requestsPerConnection) {
                    sendNextRequest(ctx);
                } else {
                    // 所有请求发送完毕，关闭连接
                    ctx.close();
                    latch.countDown();
                }

            } finally {
                in.release();
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            logger.error("客户端异常", cause);
            failureCount.addAndGet(requestsPerConnection - sentCount);
            ctx.close();
            latch.countDown();
        }

        /**
         * 发送下一个请求
         */
        private void sendNextRequest(ChannelHandlerContext ctx) {
            if (sentCount < requestsPerConnection) {
                requestStartTime = System.nanoTime();
                ByteBuf msg = Unpooled.copiedBuffer(testMessage, CharsetUtil.UTF_8);
                ctx.writeAndFlush(msg);
                sentCount++;
            }
        }
    }

    /**
     * 执行负载测试
     *
     * <p><b>流程</b>：
     * <ol>
     *   <li>创建 EventLoopGroup</li>
     *   <li>并发建立多个连接</li>
     *   <li>每个连接发送指定数量的请求</li>
     *   <li>等待所有连接完成</li>
     *   <li>统计并输出性能报告</li>
     * </ol>
     *
     * @throws InterruptedException 如果测试被中断
     */
    public void runTest() throws InterruptedException {
        EventLoopGroup group = new NioEventLoopGroup();
        CountDownLatch latch = new CountDownLatch(connections);

        try {
            logger.info("========================================");
            logger.info("  Netty Echo Client 负载测试");
            logger.info("========================================");
            logger.info("并发连接数: {}", connections);
            logger.info("每连接请求数: {}", requestsPerConnection);
            logger.info("总请求数: {}", totalRequests);
            logger.info("测试消息: {}", testMessage);
            logger.info("========================================\n");

            startTime = System.currentTimeMillis();

            // 并发建立多个连接
            for (int i = 0; i < connections; i++) {
                Bootstrap b = new Bootstrap();
                b.group(group)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new EchoClientHandler(latch));
                        }
                    });

                // 异步连接服务器
                b.connect(HOST, PORT).addListener((ChannelFutureListener) future -> {
                    if (!future.isSuccess()) {
                        logger.error("连接失败: {}:{}", HOST, PORT, future.cause());
                        failureCount.addAndGet(requestsPerConnection);
                        latch.countDown();
                    }
                });

                // 控制连接速率，避免过快
                if (i > 0 && i % 100 == 0) {
                    Thread.sleep(100);
                }
            }

            // 等待所有连接完成
            logger.info("等待测试完成...\n");
            latch.await();

            endTime = System.currentTimeMillis();

            // 输出性能报告
            printReport();

        } finally {
            group.shutdownGracefully();
        }
    }

    /**
     * 打印性能报告
     */
    private void printReport() {
        long duration = endTime - startTime;
        double durationSeconds = duration / 1000.0;

        double tps = totalRequests / durationSeconds;
        double avgLatencyMs = totalLatencyNanos.get() / (double) successCount.get() / 1_000_000;
        double successRate = (successCount.get() * 100.0) / totalRequests;

        logger.info("\n========================================");
        logger.info("         性能测试报告");
        logger.info("========================================");
        logger.info("总请求数: {}", totalRequests);
        logger.info("成功请求: {}", successCount.get());
        logger.info("失败请求: {}", failureCount.get());
        logger.info("成功率: {}%", String.format("%.2f", successRate));
        logger.info("========================================");
        logger.info("总耗时: {} 秒", String.format("%.2f", durationSeconds));
        logger.info("吞吐量 (TPS): {} req/s", String.format("%.2f", tps));
        logger.info("平均延迟: {} ms", String.format("%.3f", avgLatencyMs));
        logger.info("========================================");

        // 对比 Lab-06
        logger.info("\n与 Lab-06 对比：");
        logger.info("  Lab-06 TPS: ~50,000 req/s");
        logger.info("  Lab-07 TPS: {} req/s (提升 {}%)",
            String.format("%.2f", tps), String.format("%.1f", (tps - 50000) / 50000 * 100));
        logger.info("========================================\n");
    }

    /**
     * 主程序入口
     *
     * @param args 命令行参数
     *             args[0]: 并发连接数（默认 10）
     *             args[1]: 每连接请求数（默认 1000）
     */
    public static void main(String[] args) {
        int connections = 10;
        int requestsPerConnection = 1000;

        if (args.length >= 1) {
            connections = Integer.parseInt(args[0]);
        }
        if (args.length >= 2) {
            requestsPerConnection = Integer.parseInt(args[1]);
        }

        try {
            NettyEchoClient client = new NettyEchoClient(connections, requestsPerConnection);
            client.runTest();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("测试被中断", e);
            System.exit(1);
        }
    }
}
