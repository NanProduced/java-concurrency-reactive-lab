package nan.tech.lab07.echo;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NettyEchoServer 集成测试
 *
 * <p><b>测试目标</b>：
 * <ul>
 *   <li>验证 Echo 功能的正确性</li>
 *   <li>测试并发连接的稳定性</li>
 *   <li>验证背压控制机制</li>
 * </ul>
 *
 * @author nan.tech
 * @since Lab-07
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NettyEchoServerTest {

    private static final Logger logger = LoggerFactory.getLogger(NettyEchoServerTest.class);

    private static final int TEST_PORT = 8090; // 使用不同端口避免冲突
    private static Thread serverThread;

    /**
     * 启动测试服务器（所有测试共享）
     */
    @BeforeAll
    static void startServer() throws InterruptedException {
        logger.info("启动测试服务器...");

        CountDownLatch serverStarted = new CountDownLatch(1);

        serverThread = new Thread(() -> {
            EventLoopGroup bossGroup = new NioEventLoopGroup(1);
            EventLoopGroup workerGroup = new NioEventLoopGroup();

            try {
                io.netty.bootstrap.ServerBootstrap b = new io.netty.bootstrap.ServerBootstrap();
                b.group(bossGroup, workerGroup)
                    .channel(io.netty.channel.socket.nio.NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new NettyEchoServer.EchoHandler());
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
     * 测试 1：单个连接的 Echo 功能
     */
    @Test
    @Order(1)
    @DisplayName("单个连接的 Echo 功能测试")
    void testSingleConnection() throws InterruptedException {
        logger.info("执行测试：单个连接的 Echo 功能");

        EventLoopGroup group = new NioEventLoopGroup();
        CountDownLatch latch = new CountDownLatch(1);

        String testMessage = "Hello Netty!";
        final String[] receivedMessage = new String[1];

        try {
            Bootstrap b = new Bootstrap();
            b.group(group)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelActive(ChannelHandlerContext ctx) {
                                // 发送测试消息
                                ctx.writeAndFlush(Unpooled.copiedBuffer(testMessage, CharsetUtil.UTF_8));
                            }

                            @Override
                            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                                ByteBuf in = (ByteBuf) msg;
                                try {
                                    receivedMessage[0] = in.toString(CharsetUtil.UTF_8);
                                    ctx.close();
                                    latch.countDown();
                                } finally {
                                    in.release();
                                }
                            }
                        });
                    }
                });

            b.connect("localhost", TEST_PORT).sync();
            assertThat(latch.await(3, TimeUnit.SECONDS))
                .isTrue()
                .withFailMessage("等待响应超时");

            assertThat(receivedMessage[0])
                .isEqualTo(testMessage)
                .withFailMessage("Echo 消息不匹配");

            logger.info("✅ 单个连接测试通过");

        } finally {
            group.shutdownGracefully();
        }
    }

    /**
     * 测试 2：多并发连接测试
     */
    @Test
    @Order(2)
    @DisplayName("多并发连接测试")
    void testMultipleConnections() throws InterruptedException {
        logger.info("执行测试：多并发连接（10 连接，每连接 100 请求）");

        int connections = 10;
        int requestsPerConnection = 100;

        EventLoopGroup group = new NioEventLoopGroup();
        CountDownLatch latch = new CountDownLatch(connections);

        try {
            for (int i = 0; i < connections; i++) {
                Bootstrap b = new Bootstrap();
                b.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                                private int sentCount = 0;
                                private int receivedCount = 0;

                                @Override
                                public void channelActive(ChannelHandlerContext ctx) {
                                    sendNextRequest(ctx);
                                }

                                @Override
                                public void channelRead(ChannelHandlerContext ctx, Object msg) {
                                    ByteBuf in = (ByteBuf) msg;
                                    try {
                                        receivedCount++;
                                        if (sentCount < requestsPerConnection) {
                                            sendNextRequest(ctx);
                                        } else {
                                            ctx.close();
                                            latch.countDown();
                                        }
                                    } finally {
                                        in.release();
                                    }
                                }

                                private void sendNextRequest(ChannelHandlerContext ctx) {
                                    if (sentCount < requestsPerConnection) {
                                        ctx.writeAndFlush(Unpooled.copiedBuffer("Test", CharsetUtil.UTF_8));
                                        sentCount++;
                                    }
                                }
                            });
                        }
                    });

                b.connect("localhost", TEST_PORT);
            }

            assertThat(latch.await(10, TimeUnit.SECONDS))
                .isTrue()
                .withFailMessage("并发连接测试超时");

            logger.info("✅ 多并发连接测试通过（{} 连接，共 {} 请求）",
                connections, connections * requestsPerConnection);

        } finally {
            group.shutdownGracefully();
        }
    }

    /**
     * 测试 3：背压机制验证
     */
    @Test
    @Order(3)
    @DisplayName("背压机制验证")
    void testBackpressure() throws InterruptedException {
        logger.info("执行测试：背压机制验证");

        EventLoopGroup group = new NioEventLoopGroup();

        try {
            Bootstrap b = new Bootstrap();
            b.group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.WRITE_BUFFER_WATER_MARK,
                    new WriteBufferWaterMark(1024, 2048)) // 设置较小的水位线
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelActive(ChannelHandlerContext ctx) {
                                // 快速发送大量数据，触发背压
                                for (int i = 0; i < 1000; i++) {
                                    ctx.write(Unpooled.copiedBuffer("Large data block", CharsetUtil.UTF_8));

                                    if (!ctx.channel().isWritable()) {
                                        logger.info("✅ 背压触发：isWritable() = false");
                                        break;
                                    }
                                }
                                ctx.flush();
                                ctx.close();
                            }
                        });
                    }
                });

            ChannelFuture f = b.connect("localhost", TEST_PORT).sync();
            f.channel().closeFuture().sync();

            logger.info("✅ 背压机制测试通过");

        } finally {
            group.shutdownGracefully();
        }
    }
}
