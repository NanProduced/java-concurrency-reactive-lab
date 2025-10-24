package nan.tech.lab07.backpressure;

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

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 高并发压力测试客户端
 *
 * <p><b>测试目标</b>：
 * <ul>
 *   <li>验证 1000+ 并发连接的稳定性</li>
 *   <li>长时间运行（> 1 小时）无内存泄漏</li>
 *   <li>观察背压触发和恢复机制</li>
 *   <li>统计性能指标（TPS、延迟、成功率、背压次数）</li>
 * </ul>
 *
 * <p><b>测试参数</b>：
 * <ul>
 *   <li><b>连接数</b>：可配置（默认 1000）</li>
 *   <li><b>持续时间</b>：可配置（默认 3600 秒 = 1 小时）</li>
 *   <li><b>请求速率</b>：每个连接的请求间隔（默认 100ms）</li>
 *   <li><b>消息大小</b>：可配置（默认 1KB）</li>
 * </ul>
 *
 * <p><b>监控指标</b>：
 * <ul>
 *   <li>总请求数 / 成功数 / 失败数</li>
 *   <li>TPS（每秒事务数）</li>
 *   <li>平均延迟 / P99 延迟</li>
 *   <li>背压触发次数</li>
 *   <li>内存使用量</li>
 * </ul>
 *
 * <p><b>运行方式</b>：
 * <pre>
 * # 启动服务器（在另一个终端）
 * mvn exec:java -Dexec.mainClass="nan.tech.lab07.echo.NettyEchoServer"
 *
 * # 运行压力测试（默认参数：1000 连接，1 小时）
 * mvn exec:java -Dexec.mainClass="nan.tech.lab07.backpressure.StressTestClient"
 *
 * # 自定义参数（500 连接，10 分钟）
 * mvn exec:java -Dexec.mainClass="nan.tech.lab07.backpressure.StressTestClient" \
 *   -Dexec.args="500 600"
 * </pre>
 *
 * @author nan.tech
 * @since Lab-07
 */
public class StressTestClient {

    private static final Logger logger = LoggerFactory.getLogger(StressTestClient.class);

    /** 服务器地址 */
    private static final String HOST = "localhost";
    /** 服务器端口 */
    private static final int PORT = 8080;

    /** 并发连接数 */
    private final int connections;
    /** 测试持续时间（秒） */
    private final int durationSeconds;
    /** 请求间隔（毫秒） */
    private final int requestIntervalMs;
    /** 消息大小（字节） */
    private final int messageSize;

    /** 测试消息 */
    private final String testMessage;

    /** 统计指标 */
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong successCount = new AtomicLong(0);
    private final AtomicLong failureCount = new AtomicLong(0);
    private final AtomicLong backpressureCount = new AtomicLong(0);
    private final AtomicLong totalLatencyNanos = new AtomicLong(0);

    /** 延迟统计（用于计算 P99） */
    private final ConcurrentLinkedQueue<Long> latencies = new ConcurrentLinkedQueue<>();

    /** 控制标志 */
    private final AtomicBoolean running = new AtomicBoolean(true);

    /** 测试开始时间 */
    private long startTime;

    /**
     * 构造函数
     *
     * @param connections 并发连接数
     * @param durationSeconds 测试持续时间（秒）
     * @param requestIntervalMs 请求间隔（毫秒）
     * @param messageSize 消息大小（字节）
     */
    public StressTestClient(int connections, int durationSeconds, int requestIntervalMs, int messageSize) {
        this.connections = connections;
        this.durationSeconds = durationSeconds;
        this.requestIntervalMs = requestIntervalMs;
        this.messageSize = messageSize;

        // 生成指定大小的测试消息
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < messageSize; i++) {
            sb.append((char) ('A' + (i % 26)));
        }
        this.testMessage = sb.toString();
    }

    /**
     * 压力测试客户端 Handler
     */
    class StressTestHandler extends ChannelInboundHandlerAdapter {

        private long requestStartTime;
        private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            // 连接建立，开始定时发送请求
            scheduler.scheduleAtFixedRate(() -> {
                if (running.get()) {
                    sendRequest(ctx);
                } else {
                    ctx.close();
                    scheduler.shutdown();
                }
            }, 0, requestIntervalMs, TimeUnit.MILLISECONDS);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ByteBuf in = (ByteBuf) msg;
            try {
                // 计算延迟
                long latency = System.nanoTime() - requestStartTime;
                totalLatencyNanos.addAndGet(latency);
                latencies.offer(latency);

                // 限制队列大小，避免内存溢出
                if (latencies.size() > 10000) {
                    latencies.poll();
                }

                // 验证响应
                String response = in.toString(CharsetUtil.UTF_8);
                if (response.equals(testMessage)) {
                    successCount.incrementAndGet();
                } else {
                    failureCount.incrementAndGet();
                }

            } finally {
                in.release();
            }
        }

        @Override
        public void channelWritabilityChanged(ChannelHandlerContext ctx) {
            if (!ctx.channel().isWritable()) {
                // 背压触发
                backpressureCount.incrementAndGet();
                logger.warn("背压触发：{}", ctx.channel().id().asShortText());
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            logger.error("连接异常：{}", ctx.channel().remoteAddress(), cause);
            failureCount.incrementAndGet();
            ctx.close();
        }

        /**
         * 发送请求
         */
        private void sendRequest(ChannelHandlerContext ctx) {
            if (ctx.channel().isActive() && ctx.channel().isWritable()) {
                requestStartTime = System.nanoTime();
                totalRequests.incrementAndGet();
                ByteBuf msg = Unpooled.copiedBuffer(testMessage, CharsetUtil.UTF_8);
                ctx.writeAndFlush(msg);
            } else {
                // 连接不可写，跳过本次请求
                backpressureCount.incrementAndGet();
            }
        }
    }

    /**
     * 执行压力测试
     */
    public void runTest() throws InterruptedException {
        EventLoopGroup group = new NioEventLoopGroup();

        try {
            logger.info("========================================");
            logger.info("  Netty 高并发压力测试");
            logger.info("========================================");
            logger.info("并发连接数: {}", connections);
            logger.info("测试持续时间: {} 秒 ({} 分钟)", durationSeconds, durationSeconds / 60);
            logger.info("请求间隔: {} ms", requestIntervalMs);
            logger.info("消息大小: {} 字节", messageSize);
            logger.info("========================================\n");

            startTime = System.currentTimeMillis();

            // 并发建立连接
            CountDownLatch connectLatch = new CountDownLatch(connections);
            for (int i = 0; i < connections; i++) {
                Bootstrap b = new Bootstrap();
                b.group(group)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new StressTestHandler());
                        }
                    });

                b.connect(HOST, PORT).addListener((ChannelFutureListener) future -> {
                    if (future.isSuccess()) {
                        connectLatch.countDown();
                    } else {
                        logger.error("连接失败：{}:{}", HOST, PORT);
                    }
                });

                // 控制连接速率，避免过快
                if (i > 0 && i % 100 == 0) {
                    Thread.sleep(100);
                    logger.info("已建立 {} 个连接", i);
                }
            }

            // 等待所有连接建立
            connectLatch.await(30, TimeUnit.SECONDS);
            logger.info("所有连接已建立，开始压力测试...\n");

            // 启动监控线程
            startMonitoring();

            // 等待测试时间结束
            TimeUnit.SECONDS.sleep(durationSeconds);

            // 停止测试
            running.set(false);
            logger.info("\n测试时间结束，正在关闭连接...");

            // 等待连接关闭
            TimeUnit.SECONDS.sleep(5);

            // 输出最终报告
            printFinalReport();

        } finally {
            group.shutdownGracefully();
        }
    }

    /**
     * 启动监控线程（每 10 秒输出一次统计）
     */
    private void startMonitoring() {
        ScheduledExecutorService monitor = Executors.newScheduledThreadPool(1);

        monitor.scheduleAtFixedRate(() -> {
            long elapsed = (System.currentTimeMillis() - startTime) / 1000;
            double tps = totalRequests.get() / (double) Math.max(elapsed, 1);
            double avgLatencyMs = totalLatencyNanos.get() / (double) Math.max(successCount.get(), 1) / 1_000_000;
            double successRate = (successCount.get() * 100.0) / Math.max(totalRequests.get(), 1);

            logger.info("【监控】已运行 {} 秒 | TPS: {} | 成功率: {}% | 平均延迟: {} ms | 背压次数: {}",
                elapsed, String.format("%.2f", tps), String.format("%.2f", successRate),
                String.format("%.3f", avgLatencyMs), backpressureCount.get());

            // 内存监控
            Runtime runtime = Runtime.getRuntime();
            long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
            logger.info("【内存】已用: {} MB", usedMemory);

        }, 10, 10, TimeUnit.SECONDS);

        // 测试结束后关闭监控
        ScheduledExecutorService finalMonitor = monitor;
        Executors.newScheduledThreadPool(1).schedule(() -> {
            finalMonitor.shutdown();
        }, durationSeconds, TimeUnit.SECONDS);
    }

    /**
     * 输出最终测试报告
     */
    private void printFinalReport() {
        long endTime = System.currentTimeMillis();
        double durationSec = (endTime - startTime) / 1000.0;

        double tps = totalRequests.get() / durationSec;
        double avgLatencyMs = totalLatencyNanos.get() / (double) Math.max(successCount.get(), 1) / 1_000_000;
        double p99LatencyMs = calculateP99Latency();
        double successRate = (successCount.get() * 100.0) / Math.max(totalRequests.get(), 1);

        logger.info("\n========================================");
        logger.info("         最终测试报告");
        logger.info("========================================");
        logger.info("测试持续时间: {} 秒 ({} 分钟)",
            String.format("%.2f", durationSec), String.format("%.2f", durationSec / 60));
        logger.info("并发连接数: {}", connections);
        logger.info("========================================");
        logger.info("总请求数: {}", totalRequests.get());
        logger.info("成功请求: {}", successCount.get());
        logger.info("失败请求: {}", failureCount.get());
        logger.info("成功率: {}%", String.format("%.2f", successRate));
        logger.info("========================================");
        logger.info("吞吐量 (TPS): {} req/s", String.format("%.2f", tps));
        logger.info("平均延迟: {} ms", String.format("%.3f", avgLatencyMs));
        logger.info("P99 延迟: {} ms", String.format("%.3f", p99LatencyMs));
        logger.info("========================================");
        logger.info("背压触发次数: {}", backpressureCount.get());
        logger.info("背压触发率: {}%",
            String.format("%.4f", backpressureCount.get() * 100.0 / Math.max(totalRequests.get(), 1)));
        logger.info("========================================");

        // 内存统计
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long maxMemory = runtime.maxMemory() / (1024 * 1024);
        logger.info("内存使用: {} MB / {} MB ({}%)",
            usedMemory, maxMemory, String.format("%.2f", usedMemory * 100.0 / maxMemory));
        logger.info("========================================\n");

        // 稳定性评估
        if (successRate >= 99.9 && backpressureCount.get() < totalRequests.get() * 0.01) {
            logger.info("✅ 稳定性测试通过：成功率 ≥ 99.9%，背压触发率 < 1%");
        } else {
            double backpressureRate = backpressureCount.get() * 100.0 / totalRequests.get();
            logger.warn("⚠️ 稳定性测试未通过：成功率 {}%，背压触发率 {}%",
                String.format("%.2f", successRate), String.format("%.4f", backpressureRate));
        }
    }

    /**
     * 计算 P99 延迟
     */
    private double calculateP99Latency() {
        if (latencies.isEmpty()) {
            return 0.0;
        }

        Long[] sorted = latencies.toArray(new Long[0]);
        java.util.Arrays.sort(sorted);

        int p99Index = (int) (sorted.length * 0.99);
        return sorted[p99Index] / 1_000_000.0; // 纳秒 → 毫秒
    }

    /**
     * 主程序入口
     *
     * @param args args[0]: 并发连接数（默认 1000）
     *             args[1]: 测试持续时间/秒（默认 3600）
     *             args[2]: 请求间隔/毫秒（默认 100）
     *             args[3]: 消息大小/字节（默认 1024）
     */
    public static void main(String[] args) {
        int connections = 1000;
        int durationSeconds = 3600; // 1 小时
        int requestIntervalMs = 100;
        int messageSize = 1024;

        if (args.length >= 1) {
            connections = Integer.parseInt(args[0]);
        }
        if (args.length >= 2) {
            durationSeconds = Integer.parseInt(args[1]);
        }
        if (args.length >= 3) {
            requestIntervalMs = Integer.parseInt(args[2]);
        }
        if (args.length >= 4) {
            messageSize = Integer.parseInt(args[3]);
        }

        try {
            StressTestClient client = new StressTestClient(
                connections, durationSeconds, requestIntervalMs, messageSize);
            client.runTest();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("测试被中断", e);
            System.exit(1);
        }
    }
}
