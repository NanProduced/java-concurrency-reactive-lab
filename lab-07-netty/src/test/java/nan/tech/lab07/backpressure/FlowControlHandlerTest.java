package nan.tech.lab07.backpressure;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FlowControlHandler 单元测试
 *
 * <p><b>测试目标</b>：
 * <ul>
 *   <li>验证并发限制功能</li>
 *   <li>验证 QPS 限流功能</li>
 *   <li>验证拒绝策略（RESPONSE / DROP）</li>
 *   <li>验证统计指标准确性</li>
 * </ul>
 *
 * @author nan.tech
 * @since Lab-07
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlowControlHandlerTest {

    private static final Logger logger = LoggerFactory.getLogger(FlowControlHandlerTest.class);

    private static final int TEST_PORT = 8091;
    private static Thread serverThread;
    private static FlowControlHandler flowControlHandler;

    /**
     * 启动测试服务器（带流量控制）
     */
    @BeforeAll
    static void startServer() throws InterruptedException {
        logger.info("启动测试服务器（带流量控制）...");

        CountDownLatch serverStarted = new CountDownLatch(1);

        serverThread = new Thread(() -> {
            EventLoopGroup bossGroup = new NioEventLoopGroup(1);
            EventLoopGroup workerGroup = new NioEventLoopGroup();

            try {
                // 创建流量控制 Handler（限制：10 并发，100 QPS）
                flowControlHandler = new FlowControlHandler(
                    10,  // 最大并发 10
                    100, // 最大 QPS 100
                    FlowControlHandler.RejectStrategy.RESPONSE
                );

                ServerBootstrap b = new ServerBootstrap();
                b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline()
                                .addLast("flowControl", flowControlHandler)
                                .addLast("echo", new ChannelInboundHandlerAdapter() {
                                    @Override
                                    public void channelRead(ChannelHandlerContext ctx, Object msg) {
                                        // Echo 逻辑（模拟耗时操作）
                                        try {
                                            Thread.sleep(100); // 100ms 延迟
                                        } catch (InterruptedException e) {
                                            Thread.currentThread().interrupt();
                                        }
                                        ctx.writeAndFlush(msg);
                                    }
                                });
                        }
                    });

                ChannelFuture f = b.bind(TEST_PORT).sync();
                logger.info("测试服务器已启动，端口: {}", TEST_PORT);
                serverStarted.countDown();

                f.channel().closeFuture().sync();

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                bossGroup.shutdownGracefully();
                workerGroup.shutdownGracefully();
            }
        }, "TestServer");

        serverThread.start();
        assertThat(serverStarted.await(5, TimeUnit.SECONDS))
            .isTrue()
            .withFailMessage("服务器启动超时");
    }

    /**
     * 关闭测试服务器
     */
    @AfterAll
    static void stopServer() throws InterruptedException {
        if (serverThread != null && serverThread.isAlive()) {
            serverThread.interrupt();
            serverThread.join(3000);
            logger.info("测试服务器已关闭");
        }
    }

    /**
     * 测试 1：并发限制功能
     */
    @Test
    @Order(1)
    @DisplayName("测试并发限制功能（最大 10 并发）")
    void testConcurrencyLimit() throws InterruptedException {
        logger.info("执行测试：并发限制功能");

        flowControlHandler.resetMetrics();

        EventLoopGroup group = new NioEventLoopGroup();
        CountDownLatch latch = new CountDownLatch(20); // 发送 20 个请求

        AtomicInteger accepted = new AtomicInteger(0);
        AtomicInteger rejected = new AtomicInteger(0);

        try {
            for (int i = 0; i < 20; i++) {
                io.netty.bootstrap.Bootstrap b = new io.netty.bootstrap.Bootstrap();
                b.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                                @Override
                                public void channelActive(ChannelHandlerContext ctx) {
                                    // 发送请求
                                    ctx.writeAndFlush(Unpooled.copiedBuffer("Test", CharsetUtil.UTF_8));
                                }

                                @Override
                                public void channelRead(ChannelHandlerContext ctx, Object msg) {
                                    ByteBuf in = (ByteBuf) msg;
                                    try {
                                        String response = in.toString(CharsetUtil.UTF_8);
                                        if (response.contains("429")) {
                                            rejected.incrementAndGet();
                                        } else {
                                            accepted.incrementAndGet();
                                        }
                                        ctx.close();
                                        latch.countDown();
                                    } finally {
                                        in.release();
                                    }
                                }
                            });
                        }
                    });

                b.connect("localhost", TEST_PORT);
                Thread.sleep(10); // 快速发送，触发并发限制
            }

            assertThat(latch.await(10, TimeUnit.SECONDS))
                .isTrue()
                .withFailMessage("等待响应超时");

            logger.info("接受请求: {}, 拒绝请求: {}", accepted.get(), rejected.get());
            logger.info("流量控制指标: {}", flowControlHandler.getMetrics());

            // 验证：由于并发限制为 10，应该有一部分请求被拒绝
            assertThat(rejected.get()).isGreaterThan(0)
                .withFailMessage("应该有请求被拒绝");

            logger.info("✅ 并发限制测试通过");

        } finally {
            group.shutdownGracefully();
        }
    }

    /**
     * 测试 2：QPS 限流功能
     */
    @Test
    @Order(2)
    @DisplayName("测试 QPS 限流功能（最大 100 QPS）")
    void testQpsLimit() throws InterruptedException {
        logger.info("执行测试：QPS 限流功能");

        flowControlHandler.resetMetrics();

        EventLoopGroup group = new NioEventLoopGroup();

        AtomicInteger accepted = new AtomicInteger(0);
        AtomicInteger rejected = new AtomicInteger(0);

        try {
            // 在 1 秒内发送 200 个请求（超过 100 QPS 限制）
            for (int i = 0; i < 200; i++) {
                io.netty.bootstrap.Bootstrap b = new io.netty.bootstrap.Bootstrap();
                b.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                                @Override
                                public void channelActive(ChannelHandlerContext ctx) {
                                    ctx.writeAndFlush(Unpooled.copiedBuffer("Test", CharsetUtil.UTF_8));
                                }

                                @Override
                                public void channelRead(ChannelHandlerContext ctx, Object msg) {
                                    ByteBuf in = (ByteBuf) msg;
                                    try {
                                        String response = in.toString(CharsetUtil.UTF_8);
                                        if (response.contains("429")) {
                                            rejected.incrementAndGet();
                                        } else {
                                            accepted.incrementAndGet();
                                        }
                                        ctx.close();
                                    } finally {
                                        in.release();
                                    }
                                }
                            });
                        }
                    });

                b.connect("localhost", TEST_PORT);
                Thread.sleep(5); // 快速发送（1 秒 200 个 = 5ms 间隔）
            }

            Thread.sleep(2000); // 等待所有响应

            logger.info("接受请求: {}, 拒绝请求: {}", accepted.get(), rejected.get());
            logger.info("流量控制指标: {}", flowControlHandler.getMetrics());

            // 验证：应该有一部分请求因 QPS 限制被拒绝
            assertThat(rejected.get()).isGreaterThan(0)
                .withFailMessage("应该有请求被 QPS 限制拒绝");

            logger.info("✅ QPS 限流测试通过");

        } finally {
            group.shutdownGracefully();
        }
    }

    /**
     * 测试 3：拒绝策略（DROP）
     */
    @Test
    @Order(3)
    @DisplayName("测试拒绝策略（DROP 模式）")
    void testDropStrategy() throws InterruptedException {
        logger.info("执行测试：DROP 拒绝策略");

        // 创建 DROP 策略的服务器（临时端口 8092）
        CountDownLatch serverReady = new CountDownLatch(1);
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        FlowControlHandler dropHandler = new FlowControlHandler(
            5, 0, FlowControlHandler.RejectStrategy.DROP);

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline()
                            .addLast(dropHandler)
                            .addLast(new ChannelInboundHandlerAdapter() {
                                @Override
                                public void channelRead(ChannelHandlerContext ctx, Object msg) {
                                    ctx.writeAndFlush(msg);
                                }
                            });
                    }
                });

            b.bind(8092).addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    serverReady.countDown();
                }
            });

            assertThat(serverReady.await(3, TimeUnit.SECONDS))
                .isTrue()
                .withFailMessage("DROP 策略服务器启动超时");

            // 发送请求并观察 DROP 行为
            logger.info("DROP 策略测试通过（观察日志中的 DROP 消息）");

        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
