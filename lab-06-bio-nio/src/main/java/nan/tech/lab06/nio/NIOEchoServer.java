package nan.tech.lab06.nio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * NIO (Non-blocking I/O) Echo Server 演示
 *
 * <p><strong>教学目标</strong>：
 * <ul>
 *   <li>理解非阻塞 I/O 的工作原理</li>
 *   <li>掌握 Selector 多路复用机制</li>
 *   <li>理解 ByteBuffer 的使用（flip/clear/compact）</li>
 *   <li>认识单线程处理大量并发连接的能力</li>
 * </ul>
 *
 * <p><strong>核心问题</strong>：
 * <ul>
 *   <li>为什么 NIO 可以用一个线程处理上万个连接？</li>
 *   <li>Selector.select() 如何知道哪些 Channel 就绪？</li>
 *   <li>ByteBuffer 为什么需要 flip() 操作？</li>
 *   <li>SelectionKey 的作用是什么？</li>
 * </ul>
 *
 * <p><strong>前置知识</strong>：
 * <ul>
 *   <li>阅读 {@code docs/prerequisites/IO_MODELS.md} 了解 I/O 多路复用模型</li>
 *   <li>理解操作系统的 select/epoll 机制</li>
 * </ul>
 *
 * <p><strong>架构模式</strong>：单 Reactor 模式
 * <pre>
 * ┌─────────────────────────────────────────┐
 * │         Selector (I/O 多路复用器)        │
 * │  ┌──────────────────────────────────┐  │
 * │  │  监听的 Channel 集合              │  │
 * │  │  - ServerSocketChannel (ACCEPT)  │  │
 * │  │  - SocketChannel-1 (READ/WRITE)  │  │
 * │  │  - SocketChannel-2 (READ/WRITE)  │  │
 * │  │  - ...                           │  │
 * │  └──────────────────────────────────┘  │
 * └─────────────────────────────────────────┘
 *            ↓
 *   事件循环线程（单线程）
 *   1. selector.select() 阻塞等待事件
 *   2. 遍历就绪的 SelectionKey
 *   3. 分发事件：ACCEPT / READ / WRITE
 * </pre>
 *
 * <p><strong>对比 BIO</strong>：
 * <table border="1">
 *   <tr><th>特性</th><th>BIO (Blocking I/O)</th><th>NIO (Non-blocking I/O)</th></tr>
 *   <tr><td>线程模型</td><td>每连接一线程</td><td>单线程处理所有连接</td></tr>
 *   <tr><td>阻塞行为</td><td>read/write 阻塞</td><td>read/write 非阻塞</td></tr>
 *   <tr><td>并发能力</td><td>受线程数限制（~1000）</td><td>受 FD 限制（~65535）</td></tr>
 *   <tr><td>资源消耗</td><td>线程栈（1MB/连接）</td><td>极低（单线程）</td></tr>
 *   <tr><td>适用场景</td><td>少量长连接</td><td>大量短连接、高并发</td></tr>
 * </table>
 *
 * @author AI Assistant
 * @since 1.0.0
 */
public class NIOEchoServer {

    private static final Logger log = LoggerFactory.getLogger(NIOEchoServer.class);

    /**
     * 默认服务器端口
     */
    private static final int DEFAULT_PORT = 8080;

    /**
     * ByteBuffer 默认大小（1KB）
     */
    private static final int BUFFER_SIZE = 1024;

    /**
     * 活跃连接计数器
     */
    private static final AtomicInteger activeConnections = new AtomicInteger(0);

    /**
     * 总处理请求计数器
     */
    private static final AtomicInteger totalRequests = new AtomicInteger(0);

    /**
     * Selector（I/O 多路复用器）
     */
    private Selector selector;

    /**
     * ServerSocketChannel（监听连接）
     */
    private ServerSocketChannel serverChannel;

    /**
     * 服务器是否运行中
     */
    private volatile boolean running = false;

    // ==================== 核心方法 ====================

    /**
     * 启动 NIO Echo Server（单 Reactor 模式）
     *
     * <p><strong>工作流程</strong>：
     * <pre>
     * 1. 创建 Selector
     * 2. 创建 ServerSocketChannel，配置为非阻塞模式
     * 3. 绑定端口，注册 ACCEPT 事件到 Selector
     * 4. 进入事件循环：
     *    a. selector.select() 阻塞等待事件
     *    b. 遍历就绪的 SelectionKey
     *    c. 根据事件类型分发：ACCEPT / READ / WRITE
     * </pre>
     *
     * <p><strong>⚠️ 核心优势</strong>：
     * <ul>
     *   <li><strong>单线程</strong>: 一个线程管理所有连接（降低上下文切换）</li>
     *   <li><strong>非阻塞</strong>: read/write 不阻塞，立即返回（提高 CPU 利用率）</li>
     *   <li><strong>事件驱动</strong>: 只处理就绪的 Channel（避免空轮询）</li>
     * </ul>
     *
     * <p><strong>⚠️ 注意事项</strong>：
     * <ul>
     *   <li><strong>ByteBuffer 复用</strong>: 每个连接维护独立的 ByteBuffer（避免数据混乱）</li>
     *   <li><strong>半包/粘包</strong>: 需要处理 TCP 粘包问题（本示例使用换行符分隔）</li>
     *   <li><strong>事件处理顺序</strong>: 先处理 ACCEPT，再处理 READ，最后处理 WRITE</li>
     * </ul>
     *
     * @param port 服务器监听端口
     * @throws IOException 如果 I/O 错误发生
     */
    public void start(int port) throws IOException {
        log.info("========================================");
        log.info("启动 NIO Echo Server - 端口: {}", port);
        log.info("========================================");
        log.info("✅ 单 Reactor 模式：一个线程处理所有连接");
        log.info("✅ 非阻塞 I/O：read/write 不阻塞，立即返回");

        // 步骤 1: 创建 Selector（I/O 多路复用器）
        // Selector 是 NIO 的核心，用于监听多个 Channel 的事件
        selector = Selector.open();

        // 步骤 2: 创建 ServerSocketChannel
        serverChannel = ServerSocketChannel.open();

        // ⚠️ 关键: 配置为非阻塞模式
        // 非阻塞模式下，accept() 不会阻塞，如果没有连接则立即返回 null
        serverChannel.configureBlocking(false);

        // 启用地址复用（避免 TIME_WAIT 状态导致端口不可用）
        serverChannel.setOption(java.net.StandardSocketOptions.SO_REUSEADDR, true);

        // 步骤 3: 绑定端口
        serverChannel.bind(new InetSocketAddress(port));

        // 步骤 4: 注册 ACCEPT 事件到 Selector
        // SelectionKey.OP_ACCEPT: 监听新连接事件
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        log.info("✅ 服务器启动成功，等待客户端连接...");
        log.info("📊 当前模式: 单线程 Reactor | 线程: {}", Thread.currentThread().getName());

        running = true;

        // 步骤 5: 进入事件循环（主循环）
        eventLoop();
    }

    /**
     * 事件循环（Reactor 模式核心）
     *
     * <p><strong>工作原理</strong>：
     * <pre>
     * while (running) {
     *     1. selector.select() 阻塞等待事件（有事件就绪时返回）
     *     2. 获取就绪的 SelectionKey 集合
     *     3. 遍历 SelectionKey，根据事件类型分发：
     *        - ACCEPT:  接受新连接
     *        - READ:    读取数据
     *        - WRITE:   写入数据
     *     4. 处理完毕后，移除 SelectionKey（避免重复处理）
     * }
     * </pre>
     *
     * <p><strong>⚠️ 阻塞点</strong>：
     * <ul>
     *   <li>{@code selector.select()}: 阻塞，直到至少一个 Channel 就绪</li>
     *   <li>如果没有任何 I/O 事件，线程会在这里等待（节省 CPU）</li>
     * </ul>
     *
     * @throws IOException 如果 I/O 错误发生
     */
    private void eventLoop() throws IOException {
        log.debug("进入事件循环...");

        while (running) {
            // ⚠️ 阻塞点: 等待至少一个 Channel 就绪
            // 返回值: 就绪的 Channel 数量
            int readyChannels = selector.select();

            if (readyChannels == 0) {
                // 没有就绪的 Channel（被 wakeup() 唤醒）
                continue;
            }

            // 获取就绪的 SelectionKey 集合
            Set<SelectionKey> selectedKeys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = selectedKeys.iterator();

            // 遍历就绪的 SelectionKey
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();

                // ⚠️ 关键: 处理完后必须移除，否则会重复处理
                iterator.remove();

                // 检查 SelectionKey 是否有效
                if (!key.isValid()) {
                    continue;
                }

                try {
                    // 根据事件类型分发
                    if (key.isAcceptable()) {
                        // 新连接事件
                        handleAccept(key);
                    } else if (key.isReadable()) {
                        // 可读事件
                        handleRead(key);
                    } else if (key.isWritable()) {
                        // 可写事件
                        handleWrite(key);
                    }
                } catch (IOException e) {
                    log.error("处理事件异常: {}", e.getMessage());
                    closeChannel(key);
                }
            }
        }

        log.info("事件循环结束");
    }

    /**
     * 处理 ACCEPT 事件（接受新连接）
     *
     * <p><strong>工作流程</strong>：
     * <pre>
     * 1. 从 SelectionKey 获取 ServerSocketChannel
     * 2. 调用 accept() 接受新连接（非阻塞，立即返回）
     * 3. 配置 SocketChannel 为非阻塞模式
     * 4. 注册 READ 事件到 Selector
     * 5. 为连接创建独立的 ByteBuffer（附加到 SelectionKey）
     * </pre>
     *
     * <p><strong>⚠️ 关键点</strong>：
     * <ul>
     *   <li><strong>非阻塞</strong>: accept() 不阻塞，如果没有连接返回 null</li>
     *   <li><strong>独立 Buffer</strong>: 每个连接维护独立的 ByteBuffer（避免数据混乱）</li>
     *   <li><strong>附加对象</strong>: 使用 key.attach() 将 Buffer 附加到 SelectionKey</li>
     * </ul>
     *
     * @param key SelectionKey（包含 ServerSocketChannel）
     * @throws IOException 如果 I/O 错误发生
     */
    private void handleAccept(SelectionKey key) throws IOException {
        // 获取 ServerSocketChannel
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();

        // 接受新连接（非阻塞，立即返回）
        SocketChannel clientChannel = serverChannel.accept();

        if (clientChannel == null) {
            // 没有新连接（理论上不会发生，因为 isAcceptable() 已判断）
            return;
        }

        // 获取客户端地址信息
        String clientInfo = clientChannel.getRemoteAddress().toString();
        log.info("📥 接受新连接: {}", clientInfo);

        // ⚠️ 关键: 配置为非阻塞模式
        clientChannel.configureBlocking(false);

        // 为每个连接创建独立的 ByteBuffer（1KB）
        // ⚠️ 重要: 每个连接必须有独立的 Buffer，否则数据会混乱
        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);

        // 注册 READ 事件到 Selector
        // attachment: 将 Buffer 附加到 SelectionKey，后续可通过 key.attachment() 获取
        clientChannel.register(selector, SelectionKey.OP_READ, buffer);

        // 更新统计
        int currentConnections = activeConnections.incrementAndGet();
        log.debug("当前活跃连接数: {}", currentConnections);
    }

    /**
     * 处理 READ 事件（读取客户端数据）
     *
     * <p><strong>工作流程</strong>：
     * <pre>
     * 1. 从 SelectionKey 获取 SocketChannel 和 ByteBuffer
     * 2. 调用 channel.read(buffer) 读取数据（非阻塞）
     * 3. 判断读取结果：
     *    - &gt; 0: 读取到数据
     *    - = 0: 没有数据（正常）
     *    - &lt; 0: 客户端关闭连接
     * 4. 处理数据（Echo 协议：原样返回）
     * 5. 切换到 WRITE 事件
     * </pre>
     *
     * <p><strong>⚠️ ByteBuffer 操作</strong>：
     * <pre>
     * 读取前: position=0, limit=capacity
     * ┌─────────────────────────────────┐
     * │ [0][1][2][3][4]...[capacity-1] │
     * └─────────────────────────────────┘
     *  ↑position              ↑limit
     *
     * 读取后: position=N, limit=capacity
     * ┌─────────────────────────────────┐
     * │ [H][e][l][l][o]...[capacity-1] │
     * └─────────────────────────────────┘
     *                  ↑position  ↑limit
     *
     * flip() 后: position=0, limit=N (准备读取)
     * ┌─────────────────────────────────┐
     * │ [H][e][l][l][o]...[capacity-1] │
     * └─────────────────────────────────┘
     *  ↑position      ↑limit
     * </pre>
     *
     * <p><strong>⚠️ 半包/粘包问题</strong>：
     * <ul>
     *   <li>本示例使用换行符 (\n) 分隔消息</li>
     *   <li>实际应用需要更复杂的协议（如长度前缀、分隔符）</li>
     * </ul>
     *
     * @param key SelectionKey（包含 SocketChannel 和 Buffer）
     * @throws IOException 如果 I/O 错误发生
     */
    private void handleRead(SelectionKey key) throws IOException {
        // 获取 SocketChannel
        SocketChannel channel = (SocketChannel) key.channel();
        String clientInfo = channel.getRemoteAddress().toString();

        // 获取附加的 ByteBuffer
        ByteBuffer buffer = (ByteBuffer) key.attachment();

        // ⚠️ 非阻塞读取: 立即返回，不等待数据
        // 返回值:
        //   > 0: 读取到的字节数
        //   = 0: 没有数据可读（正常）
        //   < 0: 客户端关闭连接（EOF）
        int bytesRead = channel.read(buffer);

        if (bytesRead < 0) {
            // 客户端关闭连接
            log.info("📤 客户端关闭连接: {}", clientInfo);
            closeChannel(key);
            return;
        }

        if (bytesRead == 0) {
            // 没有数据可读（正常情况）
            return;
        }

        log.debug("[{}] 读取 {} 字节", clientInfo, bytesRead);

        // ⚠️ 关键操作: flip() 切换到读模式
        // 作用: position=0, limit=当前position（准备读取刚写入的数据）
        buffer.flip();

        // 解码数据（UTF-8）
        String message = StandardCharsets.UTF_8.decode(buffer).toString();
        log.debug("[{}] 收到消息: {}", clientInfo, message.trim());

        // 统计
        totalRequests.incrementAndGet();

        // ⚠️ 关键操作: compact() 压缩 Buffer
        // 作用: 将未读数据移到 Buffer 开头，position 指向数据末尾
        // 为什么不用 clear()? 因为可能有半包数据（未读完整的消息）
        buffer.compact();

        // 准备响应数据（Echo: 原样返回）
        String response = "ECHO: " + message;
        ByteBuffer responseBuffer = ByteBuffer.wrap(response.getBytes(StandardCharsets.UTF_8));

        // 附加响应 Buffer 到 SelectionKey
        key.attach(responseBuffer);

        // 切换到 WRITE 事件（等待可写）
        key.interestOps(SelectionKey.OP_WRITE);
    }

    /**
     * 处理 WRITE 事件（向客户端写入数据）
     *
     * <p><strong>工作流程</strong>：
     * <pre>
     * 1. 从 SelectionKey 获取 SocketChannel 和响应 Buffer
     * 2. 调用 channel.write(buffer) 写入数据（非阻塞）
     * 3. 判断是否写完：
     *    - buffer.hasRemaining() == false: 写完，切换到 READ 事件
     *    - buffer.hasRemaining() == true:  未写完，继续等待 WRITE 事件
     * </pre>
     *
     * <p><strong>⚠️ 为什么可能写不完？</strong>：
     * <ul>
     *   <li><strong>发送缓冲区满</strong>: TCP 发送缓冲区有限（默认 ~64KB）</li>
     *   <li><strong>网络拥塞</strong>: 网络慢，数据发送不出去</li>
     *   <li><strong>非阻塞模式</strong>: write() 不等待，立即返回已写字节数</li>
     * </ul>
     *
     * @param key SelectionKey（包含 SocketChannel 和响应 Buffer）
     * @throws IOException 如果 I/O 错误发生
     */
    private void handleWrite(SelectionKey key) throws IOException {
        // 获取 SocketChannel
        SocketChannel channel = (SocketChannel) key.channel();
        String clientInfo = channel.getRemoteAddress().toString();

        // 获取附加的响应 Buffer
        ByteBuffer buffer = (ByteBuffer) key.attachment();

        // ⚠️ 非阻塞写入: 立即返回，不等待缓冲区有空间
        // 返回值: 写入的字节数（可能小于 buffer.remaining()）
        int bytesWritten = channel.write(buffer);

        log.debug("[{}] 写入 {} 字节", clientInfo, bytesWritten);

        // 检查是否写完
        if (!buffer.hasRemaining()) {
            // 写完，重新分配读 Buffer
            ByteBuffer readBuffer = ByteBuffer.allocate(BUFFER_SIZE);
            key.attach(readBuffer);

            // 切换回 READ 事件（继续接收数据）
            key.interestOps(SelectionKey.OP_READ);

            log.debug("[{}] 响应发送完毕，切换到 READ 模式", clientInfo);
        } else {
            // 未写完，继续等待 WRITE 事件
            log.debug("[{}] 响应未发送完毕，继续等待 WRITE 事件 | 剩余: {} 字节",
                clientInfo, buffer.remaining());
        }
    }

    /**
     * 关闭客户端连接
     *
     * <p><strong>关闭步骤</strong>：
     * <ol>
     *   <li>取消 SelectionKey（从 Selector 移除）</li>
     *   <li>关闭 SocketChannel</li>
     *   <li>更新统计信息</li>
     * </ol>
     *
     * @param key SelectionKey
     */
    private void closeChannel(SelectionKey key) {
        try {
            SocketChannel channel = (SocketChannel) key.channel();
            String clientInfo = channel.getRemoteAddress().toString();

            // 取消 SelectionKey（从 Selector 移除）
            key.cancel();

            // 关闭 Channel
            channel.close();

            // 更新统计
            int currentConnections = activeConnections.decrementAndGet();
            log.info("✅ 连接关闭: {} | 剩余活跃连接: {}", clientInfo, currentConnections);

        } catch (IOException e) {
            log.error("关闭连接异常: {}", e.getMessage());
        }
    }

    /**
     * 停止服务器
     *
     * <p><strong>关闭步骤</strong>：
     * <ol>
     *   <li>设置 running = false（停止事件循环）</li>
     *   <li>唤醒 Selector（如果正在阻塞）</li>
     *   <li>关闭所有连接</li>
     *   <li>关闭 ServerSocketChannel</li>
     *   <li>关闭 Selector</li>
     * </ol>
     */
    public void stop() {
        log.info("正在关闭服务器...");

        running = false;

        // 唤醒 Selector（如果正在阻塞）
        if (selector != null) {
            selector.wakeup();
        }

        try {
            // 关闭所有连接
            if (selector != null) {
                for (SelectionKey key : selector.keys()) {
                    if (key.channel() instanceof SocketChannel) {
                        closeChannel(key);
                    }
                }
            }

            // 关闭 ServerSocketChannel
            if (serverChannel != null) {
                serverChannel.close();
            }

            // 关闭 Selector
            if (selector != null) {
                selector.close();
            }

            log.info("✅ 服务器已关闭");

        } catch (IOException e) {
            log.error("关闭服务器异常: {}", e.getMessage());
        }
    }

    // ==================== 主方法（演示入口）====================

    /**
     * 演示入口
     *
     * <p><strong>使用方式</strong>：
     * <pre>
     * # 启动 NIO Echo Server
     * mvn exec:java -Dexec.mainClass="nan.tech.lab06.nio.NIOEchoServer"
     *
     * # 指定端口
     * mvn exec:java -Dexec.mainClass="nan.tech.lab06.nio.NIOEchoServer" -Dexec.args="9090"
     * </pre>
     *
     * <p><strong>测试客户端</strong>：
     * <pre>
     * # 使用 telnet 测试
     * telnet localhost 8080
     * > Hello NIO
     * ECHO: Hello NIO
     *
     * # 使用 netcat 测试
     * nc localhost 8080
     * Hello NIO
     * ECHO: Hello NIO
     * </pre>
     *
     * <p><strong>对比 BIO</strong>：
     * <pre>
     * # 启动 NIO Server
     * mvn exec:java -Dexec.mainClass="nan.tech.lab06.nio.NIOEchoServer"
     *
     * # 并发测试（1000 客户端）
     * mvn exec:java -Dexec.mainClass="nan.tech.lab06.bio.BIOEchoClient" \
     *   -Dexec.args="concurrent 1000 10"
     *
     * # 观察: NIO 单线程处理 1000 连接，CPU 占用低
     * # 对比: BIO 多线程模式，CPU 占用高（上下文切换）
     * </pre>
     *
     * @param args 命令行参数 [端口]
     * @throws IOException 如果 I/O 错误发生
     */
    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;

        NIOEchoServer server = new NIOEchoServer();

        // 注册 JVM 关闭钩子（优雅关闭）
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("收到关闭信号，正在优雅关闭服务器...");
            server.stop();
        }));

        // 启动服务器
        server.start(port);
    }
}
