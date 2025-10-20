package nan.tech.lab07.basics;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Channel 生命周期演示
 *
 * <p><b>教学目标</b>：
 * <ul>
 *   <li>理解 Channel 的完整生命周期（8 个阶段）</li>
 *   <li>掌握生命周期方法的调用顺序</li>
 *   <li>学习在不同阶段执行初始化和清理操作</li>
 *   <li>观察异常处理机制（exceptionCaught）</li>
 * </ul>
 *
 * <p><b>生命周期流程</b>：
 * <pre>
 * 1. handlerAdded        ──► Handler 被添加到 Pipeline
 *    │
 * 2. channelRegistered   ──► Channel 注册到 EventLoop
 *    │
 * 3. channelActive       ──► Channel 激活（连接建立）
 *    │
 * 4. channelRead         ──► 读取数据（可能多次调用）
 *    │
 * 5. channelReadComplete ──► 本次读取完成
 *    │
 * 6. channelInactive     ──► Channel 关闭
 *    │
 * 7. channelUnregistered ──► Channel 从 EventLoop 注销
 *    │
 * 8. handlerRemoved      ──► Handler 从 Pipeline 移除
 * </pre>
 *
 * <p><b>运行方式</b>：
 * <pre>
 * mvn exec:java -Dexec.mainClass="nan.tech.lab07.basics.ChannelLifecycleDemo"
 *
 * # 在另一个终端测试连接：
 * telnet localhost 8081
 * </pre>
 *
 * @author nan.tech
 * @since Lab-07
 */
public class ChannelLifecycleDemo {

    private static final Logger logger = LoggerFactory.getLogger(ChannelLifecycleDemo.class);

    /** 服务器端口 */
    private static final int PORT = 8081;

    /**
     * 生命周期追踪 Handler
     *
     * <p><b>功能</b>：
     * <ul>
     *   <li>记录每个生命周期方法的调用时机</li>
     *   <li>打印详细的日志，方便观察</li>
     *   <li>演示资源初始化和清理的最佳时机</li>
     * </ul>
     *
     * <p><b>最佳实践</b>：
     * <ul>
     *   <li><b>handlerAdded</b>：初始化 Handler 资源（如：创建缓冲区）</li>
     *   <li><b>channelActive</b>：发送握手消息、启动心跳检测</li>
     *   <li><b>channelInactive</b>：清理连接相关资源</li>
     *   <li><b>handlerRemoved</b>：清理 Handler 资源</li>
     * </ul>
     */
    static class LifecycleHandler extends ChannelInboundHandlerAdapter {

        private final String name;

        public LifecycleHandler(String name) {
            this.name = name;
        }

        /**
         * 步骤 1：Handler 被添加到 Pipeline
         *
         * <p><b>触发时机</b>：调用 {@code pipeline.addLast(handler)} 时
         *
         * <p><b>典型用途</b>：
         * <ul>
         *   <li>初始化 Handler 级别的资源（如：创建缓冲区、统计计数器）</li>
         *   <li>注册 JMX MBean 进行监控</li>
         * </ul>
         */
        @Override
        public void handlerAdded(ChannelHandlerContext ctx) {
            logger.info("[{}] 1. handlerAdded - Handler 被添加到 Pipeline", name);
        }

        /**
         * 步骤 2：Channel 注册到 EventLoop
         *
         * <p><b>触发时机</b>：Channel 绑定到 EventLoop 后
         *
         * <p><b>典型用途</b>：
         * <ul>
         *   <li>记录 Channel 与 EventLoop 的绑定关系</li>
         *   <li>初始化 Channel 级别的资源</li>
         * </ul>
         */
        @Override
        public void channelRegistered(ChannelHandlerContext ctx) {
            logger.info("[{}] 2. channelRegistered - Channel 注册到 EventLoop: {}",
                name, ctx.channel().eventLoop().toString());
            ctx.fireChannelRegistered(); // 传播事件
        }

        /**
         * 步骤 3：Channel 激活（连接建立）
         *
         * <p><b>触发时机</b>：
         * <ul>
         *   <li>服务端：接受客户端连接后</li>
         *   <li>客户端：成功连接到服务端后</li>
         * </ul>
         *
         * <p><b>典型用途</b>：
         * <ul>
         *   <li>发送握手消息（如：HTTP 请求、WebSocket 握手）</li>
         *   <li>启动心跳检测</li>
         *   <li>初始化业务状态</li>
         * </ul>
         */
        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            logger.info("[{}] 3. channelActive - Channel 激活，远程地址: {}",
                name, ctx.channel().remoteAddress());

            // 示例：发送欢迎消息
            ByteBuf welcomeMsg = Unpooled.copiedBuffer(
                "Welcome to Channel Lifecycle Demo!\n", CharsetUtil.UTF_8);
            ctx.writeAndFlush(welcomeMsg);

            ctx.fireChannelActive(); // 传播事件
        }

        /**
         * 步骤 4：读取数据
         *
         * <p><b>触发时机</b>：从 Socket 读取到数据后
         *
         * <p><b>注意事项</b>：
         * <ul>
         *   <li>该方法可能被多次调用（一次 select() 可能读取多个数据包）</li>
         *   <li>必须释放 ByteBuf，否则会内存泄漏</li>
         * </ul>
         *
         * <p><b>典型用途</b>：
         * <ul>
         *   <li>解码数据（如：字节流 → 业务对象）</li>
         *   <li>处理业务逻辑</li>
         * </ul>
         */
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ByteBuf in = (ByteBuf) msg;
            try {
                String received = in.toString(CharsetUtil.UTF_8);
                logger.info("[{}] 4. channelRead - 收到数据: {}", name, received.trim());

                // 回显数据
                ByteBuf echo = Unpooled.copiedBuffer("Echo: " + received, CharsetUtil.UTF_8);
                ctx.write(echo);

            } finally {
                // 释放 ByteBuf（重要！）
                in.release();
            }
        }

        /**
         * 步骤 5：本次读取完成
         *
         * <p><b>触发时机</b>：本次 select() 的所有 channelRead() 调用完成后
         *
         * <p><b>典型用途</b>：
         * <ul>
         *   <li>刷新输出缓冲区（flush）</li>
         *   <li>批量处理累积的数据</li>
         * </ul>
         */
        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) {
            logger.info("[{}] 5. channelReadComplete - 本次读取完成，刷新缓冲区", name);
            ctx.flush(); // 刷新输出缓冲区
        }

        /**
         * 步骤 6：Channel 关闭
         *
         * <p><b>触发时机</b>：
         * <ul>
         *   <li>客户端主动断开连接</li>
         *   <li>服务端调用 {@code ctx.close()}</li>
         *   <li>网络故障导致连接中断</li>
         * </ul>
         *
         * <p><b>典型用途</b>：
         * <ul>
         *   <li>清理连接相关资源（如：关闭数据库连接、释放会话）</li>
         *   <li>记录连接统计信息</li>
         * </ul>
         */
        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            logger.info("[{}] 6. channelInactive - Channel 关闭", name);
            ctx.fireChannelInactive(); // 传播事件
        }

        /**
         * 步骤 7：Channel 从 EventLoop 注销
         *
         * <p><b>触发时机</b>：Channel 从 EventLoop 的 Selector 上注销后
         *
         * <p><b>典型用途</b>：
         * <ul>
         *   <li>清理 Channel 级别的资源</li>
         *   <li>移除监控指标</li>
         * </ul>
         */
        @Override
        public void channelUnregistered(ChannelHandlerContext ctx) {
            logger.info("[{}] 7. channelUnregistered - Channel 从 EventLoop 注销", name);
            ctx.fireChannelUnregistered(); // 传播事件
        }

        /**
         * 步骤 8：Handler 从 Pipeline 移除
         *
         * <p><b>触发时机</b>：
         * <ul>
         *   <li>调用 {@code pipeline.remove(handler)} 时</li>
         *   <li>Channel 关闭时，所有 Handler 会被自动移除</li>
         * </ul>
         *
         * <p><b>典型用途</b>：
         * <ul>
         *   <li>清理 Handler 级别的资源（与 handlerAdded 对应）</li>
         *   <li>取消注册的 JMX MBean</li>
         * </ul>
         */
        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) {
            logger.info("[{}] 8. handlerRemoved - Handler 从 Pipeline 移除", name);
        }

        /**
         * 异常处理
         *
         * <p><b>触发时机</b>：
         * <ul>
         *   <li>Handler 中抛出未捕获的异常</li>
         *   <li>上游 Handler 调用 {@code ctx.fireExceptionCaught(cause)}</li>
         * </ul>
         *
         * <p><b>注意事项</b>：
         * <ul>
         *   <li>异常总是向<b>尾部（Tail）</b>传播</li>
         *   <li>如果不处理，最终会被 TailHandler 打印日志并关闭连接</li>
         * </ul>
         *
         * <p><b>典型用途</b>：
         * <ul>
         *   <li>记录异常日志</li>
         *   <li>发送错误响应</li>
         *   <li>关闭连接</li>
         * </ul>
         */
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            logger.error("[{}] exceptionCaught - 捕获异常: {}", name, cause.getMessage(), cause);
            ctx.close(); // 关闭连接
        }
    }

    /**
     * 启动演示服务器
     *
     * <p><b>流程</b>：
     * <ol>
     *   <li>创建 BossGroup 和 WorkerGroup</li>
     *   <li>配置 ServerBootstrap</li>
     *   <li>绑定端口并启动</li>
     *   <li>等待用户连接（观察生命周期日志）</li>
     * </ol>
     *
     * @param latch 用于等待服务器启动完成
     */
    public static void startServer(CountDownLatch latch) {
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();

                        // 添加生命周期追踪 Handler
                        pipeline.addLast("lifecycle", new LifecycleHandler("LifecycleTracker"));
                    }
                });

            // 绑定端口
            ChannelFuture f = b.bind(PORT).sync();
            logger.info("========================================");
            logger.info("  服务器启动成功，监听端口: {}", PORT);
            logger.info("  请使用 telnet localhost {} 测试", PORT);
            logger.info("========================================");

            latch.countDown(); // 通知主线程服务器已启动

            // 等待服务器关闭
            f.channel().closeFuture().sync();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("服务器被中断", e);
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
            logger.info("服务器已关闭");
        }
    }

    /**
     * 主程序入口
     *
     * <p><b>使用说明</b>：
     * <ol>
     *   <li>运行本程序，启动演示服务器</li>
     *   <li>在另一个终端执行：{@code telnet localhost 8081}</li>
     *   <li>输入任意文本并回车，观察服务器日志</li>
     *   <li>输入 {@code Ctrl+]} 然后 {@code quit} 断开连接，观察关闭流程</li>
     * </ol>
     *
     * <p><b>预期输出</b>：
     * <pre>
     * [LifecycleTracker] 1. handlerAdded - Handler 被添加到 Pipeline
     * [LifecycleTracker] 2. channelRegistered - Channel 注册到 EventLoop
     * [LifecycleTracker] 3. channelActive - Channel 激活
     * [LifecycleTracker] 4. channelRead - 收到数据: Hello
     * [LifecycleTracker] 5. channelReadComplete - 本次读取完成
     * [LifecycleTracker] 6. channelInactive - Channel 关闭
     * [LifecycleTracker] 7. channelUnregistered - Channel 从 EventLoop 注销
     * [LifecycleTracker] 8. handlerRemoved - Handler 从 Pipeline 移除
     * </pre>
     *
     * @param args 命令行参数（未使用）
     */
    public static void main(String[] args) {
        logger.info("========================================");
        logger.info("    Netty Channel 生命周期演示");
        logger.info("========================================");

        CountDownLatch latch = new CountDownLatch(1);

        // 在新线程中启动服务器
        Thread serverThread = new Thread(() -> startServer(latch), "ServerThread");
        serverThread.start();

        try {
            // 等待服务器启动完成
            latch.await(5, TimeUnit.SECONDS);

            logger.info("\n服务器已启动，等待客户端连接...");
            logger.info("按 Ctrl+C 停止服务器\n");

            // 等待服务器线程结束
            serverThread.join();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("主线程被中断", e);
        }
    }
}
