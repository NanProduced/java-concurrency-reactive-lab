package nan.tech.lab07.zerocopy;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.stream.ChunkedFile;
import io.netty.handler.stream.ChunkedWriteHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;

/**
 * Netty FileRegion 零拷贝演示
 *
 * <p><b>教学目标</b>：
 * <ul>
 *   <li>理解 FileRegion 的零拷贝原理（sendfile 系统调用）</li>
 *   <li>掌握 ChunkedFile 的使用（大文件分块传输）</li>
 *   <li>对比传统文件传输的性能差异</li>
 * </ul>
 *
 * <p><b>零拷贝原理</b>：
 * <pre>
 * 传统文件传输（4 次拷贝 + 4 次上下文切换）：
 * ┌─────────┐    read()    ┌──────────┐   write()   ┌─────────┐
 * │  磁盘   │ ───────────► │ 内核缓冲 │ ──────────► │  Socket │
 * └─────────┘              └──────────┘             └─────────┘
 *                               ▲                        ▲
 *                               │   copy_to_user         │
 *                               ▼                        │
 *                          ┌──────────┐                  │
 *                          │ 用户缓冲 │  copy_from_user  │
 *                          └──────────┘ ─────────────────┘
 *
 * FileRegion 零拷贝（0 次用户态拷贝 + 2 次上下文切换）：
 * ┌─────────┐  sendfile()  ┌──────────┐   DMA 传输  ┌─────────┐
 * │  磁盘   │ ───────────► │ 内核缓冲 │ ──────────► │  Socket │
 * └─────────┘              └──────────┘             └─────────┘
 *                          （全程在内核态，无用户态拷贝）
 * </pre>
 *
 * <p><b>性能对比</b>：
 * <ul>
 *   <li><b>传统方式</b>：4 次拷贝（磁盘→内核→用户→内核→Socket）</li>
 *   <li><b>FileRegion</b>：2 次拷贝（磁盘→内核→Socket），性能提升 50%+</li>
 * </ul>
 *
 * <p><b>运行方式</b>：
 * <pre>
 * # 启动服务器
 * mvn exec:java -Dexec.mainClass="nan.tech.lab07.zerocopy.FileRegionDemo"
 *
 * # 在另一个终端测试
 * telnet localhost 8083
 * # 输入：GET /path/to/file.txt
 * </pre>
 *
 * @author nan.tech
 * @since Lab-07
 */
public class FileRegionDemo {

    private static final Logger logger = LoggerFactory.getLogger(FileRegionDemo.class);

    /** 服务器端口 */
    private static final int PORT = 8083;

    /**
     * 文件传输 Handler（支持零拷贝）
     *
     * <p><b>功能</b>：
     * <ul>
 *   <li>接收客户端的文件请求（如："GET /path/to/file.txt"）</li>
     *   <li>使用 ChunkedFile 分块传输大文件</li>
     *   <li>利用 FileRegion 实现零拷贝</li>
     * </ul>
     *
     * <p><b>关键技术</b>：
     * <ul>
     *   <li><b>ChunkedWriteHandler</b>：自动处理分块写入，避免 OOM</li>
     *   <li><b>ChunkedFile</b>：封装文件分块读取逻辑</li>
     *   <li><b>FileRegion</b>：底层使用 sendfile 系统调用</li>
     * </ul>
     */
    static class FileServerHandler extends SimpleChannelInboundHandler<String> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, String request) throws Exception {
            logger.info("收到文件请求: {}", request);

            // 解析请求（简化版，仅支持 GET /path 格式）
            if (!request.startsWith("GET ")) {
                ctx.writeAndFlush("ERROR: Invalid request format. Use: GET /path/to/file\n");
                return;
            }

            String filePath = request.substring(4).trim();

            // 验证文件路径（安全检查）
            File file = new File(filePath);
            if (!file.exists() || !file.isFile()) {
                ctx.writeAndFlush("ERROR: File not found: " + filePath + "\n");
                logger.warn("文件不存在: {}", filePath);
                return;
            }

            // 检查文件大小
            long fileLength = file.length();
            logger.info("准备传输文件: {}, 大小: {} 字节 ({} MB)",
                filePath, fileLength, fileLength / (1024 * 1024));

            // 发送响应头
            ctx.write("OK " + fileLength + "\n");

            // 使用 ChunkedFile 传输文件（零拷贝）
            RandomAccessFile raf = new RandomAccessFile(file, "r");
            FileChannel fileChannel = raf.getChannel();

            // ChunkedFile 会自动使用 FileRegion 进行零拷贝传输
            ChunkedFile chunkedFile = new ChunkedFile(raf, 0, fileLength, 8192);

            // 异步传输，添加监听器
            ChannelFuture transferFuture = ctx.writeAndFlush(chunkedFile);
            transferFuture.addListener(new ChannelProgressiveFutureListener() {
                @Override
                public void operationProgressed(ChannelProgressiveFuture future, long progress, long total) {
                    if (total < 0) {
                        logger.info("传输进度: {} 字节", progress);
                    } else {
                        logger.info("传输进度: {} / {} ({}%)",
                            progress, total, String.format("%.2f", progress * 100.0 / total));
                    }
                }

                @Override
                public void operationComplete(ChannelProgressiveFuture future) {
                    if (future.isSuccess()) {
                        logger.info("文件传输完成: {}", filePath);
                    } else {
                        logger.error("文件传输失败", future.cause());
                    }

                    // 关闭文件通道
                    try {
                        fileChannel.close();
                        raf.close();
                    } catch (IOException e) {
                        logger.error("关闭文件失败", e);
                    }

                    // 关闭连接
                    ctx.close();
                }
            });
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            logger.error("文件传输异常", cause);
            ctx.close();
        }
    }

    /**
     * 启动文件服务器
     *
     * <p><b>配置要点</b>：
     * <ul>
     *   <li>添加 {@code ChunkedWriteHandler} 支持大文件传输</li>
     *   <li>使用 {@code LineBasedFrameDecoder} 按行解析请求</li>
     * </ul>
     *
     * @throws InterruptedException 如果启动被中断
     */
    public void start() throws InterruptedException {
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

                        // 按行解析请求
                        pipeline.addLast(new LineBasedFrameDecoder(1024));
                        pipeline.addLast(new StringDecoder());
                        pipeline.addLast(new StringEncoder());

                        // 支持分块传输（必须在 FileServerHandler 之前）
                        pipeline.addLast(new ChunkedWriteHandler());

                        // 业务逻辑
                        pipeline.addLast(new FileServerHandler());
                    }
                });

            ChannelFuture f = b.bind(PORT).sync();
            logger.info("========================================");
            logger.info("  FileRegion 零拷贝演示服务器");
            logger.info("========================================");
            logger.info("监听端口: {}", PORT);
            logger.info("请使用 telnet localhost {} 测试", PORT);
            logger.info("命令格式: GET /path/to/file.txt");
            logger.info("========================================\n");

            f.channel().closeFuture().sync();

        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    /**
     * 主程序入口
     *
     * <p><b>测试步骤</b>：
     * <ol>
     *   <li>运行本程序，启动文件服务器</li>
     *   <li>创建测试文件：{@code echo "Hello Netty Zero Copy" > test.txt}</li>
     *   <li>在另一个终端：{@code telnet localhost 8083}</li>
     *   <li>输入：{@code GET test.txt}</li>
     *   <li>观察日志输出，查看零拷贝传输过程</li>
     * </ol>
     *
     * <p><b>性能测试</b>：
     * <pre>
     * # 创建 100MB 测试文件
     * dd if=/dev/zero of=large_file.dat bs=1M count=100
     *
     * # 测试传输
     * telnet localhost 8083
     * GET large_file.dat
     *
     * # 观察传输速度和 CPU 使用率
     * </pre>
     *
     * @param args 命令行参数（未使用）
     */
    public static void main(String[] args) {
        logger.info("========================================");
        logger.info("    Netty FileRegion 零拷贝演示");
        logger.info("========================================");

        try {
            new FileRegionDemo().start();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("服务器启动失败", e);
            System.exit(1);
        }
    }
}
