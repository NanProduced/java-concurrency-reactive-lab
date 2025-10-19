package nan.tech.lab06.benchmark;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

/**
 * NIO (Non-blocking I/O) 性能基准测试
 *
 * <p><strong>测试目标</strong>：
 * <ul>
 *   <li>验证 Selector 多路复用的性能优势</li>
 *   <li>对比 NIO 与 BIO 的 TPS 差异</li>
 *   <li>分析单线程处理多连接的能力</li>
 * </ul>
 *
 * <p><strong>核心问题</strong>：
 * <pre>
 * ❓ 为什么单线程能处理大量并发？
 *
 * BIO 模式:
 *   - 一个线程处理一个连接
 *   - 阻塞在 read()/write() 上
 *   - 10000 并发 = 10000 线程 (不可行)
 *
 * NIO 模式:
 *   - 一个线程监听多个 Channel
 *   - Selector.select() 批量获取就绪事件
 *   - 事件驱动处理，无阻塞等待
 * </pre>
 *
 * <p><strong>运行方式</strong>：
 * <pre>
 * # 运行 NIO benchmark
 * mvn test-compile exec:java -Dexec.mainClass="nan.tech.lab06.benchmark.NIOBenchmark"
 * </pre>
 *
 * <p><strong>预期结果</strong>（参考值）：
 * <pre>
 * testNIOSelector: ~10000 ops/s (2x BIO 线程池性能)
 *
 * 性能优势来源:
 *   - 无线程创建开销
 *   - 无上下文切换开销
 *   - 无阻塞等待时间
 * </pre>
 *
 * @author AI Assistant
 * @since 1.0.0
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 2, time = 5)
@Measurement(iterations = 3, time = 10)
@Fork(1)
@Threads(10) // 模拟 10 个并发客户端
public class NIOBenchmark {

    // ==================== 服务器配置 ====================

    /**
     * NIO 服务器端口
     */
    private static final int NIO_PORT = 19090;

    /**
     * ByteBuffer 大小
     */
    private static final int BUFFER_SIZE = 1024;

    /**
     * 服务器线程（后台运行）
     */
    private Thread nioServerThread;

    /**
     * 服务器运行标志
     */
    private volatile boolean serverRunning = false;

    // ==================== 测试生命周期 ====================

    /**
     * 测试前启动 NIO 服务器
     */
    @Setup(Level.Trial)
    public void setup() throws InterruptedException {
        serverRunning = true;
        nioServerThread = new Thread(this::startNIOServer);
        nioServerThread.setDaemon(true);
        nioServerThread.start();

        // 等待服务器启动
        Thread.sleep(2000);
    }

    /**
     * 测试后关闭服务器
     */
    @TearDown(Level.Trial)
    public void teardown() {
        serverRunning = false;
        if (nioServerThread != null) {
            nioServerThread.interrupt();
        }
    }

    // ==================== Benchmark 测试 ====================

    /**
     * Benchmark: NIO Selector 多路复用服务器
     *
     * <p><strong>架构特点</strong>：
     * <pre>
     * ┌─────────────────────────────────┐
     * │    Selector (单线程)            │
     * │  ┌───────────────────────────┐  │
     * │  │  selector.select()        │  │  ← 阻塞等待事件
     * │  │  selectedKeys: [C1, C3]   │  │  ← 批量获取就绪 Channel
     * │  └───────────────────────────┘  │
     * └─────────────────────────────────┘
     *          ↓        ↓        ↓
     *       Channel1 Channel2 Channel3   ← 非阻塞 I/O
     * </pre>
     *
     * <p><strong>性能优势</strong>：
     * <ul>
     *   <li>单线程处理大量连接（C10K 问题解决方案）</li>
     *   <li>无线程创建/销毁开销</li>
     *   <li>无上下文切换开销</li>
     * </ul>
     */
    @Benchmark
    public void testNIOSelector() throws IOException {
        sendNIOEchoRequest("localhost", NIO_PORT, "Hello-NIO-Selector");
    }

    // ==================== 辅助方法 ====================

    /**
     * 发送 NIO Echo 请求（阻塞模式客户端）
     *
     * <p><strong>注意</strong>：
     * <ul>
     *   <li>客户端使用阻塞模式简化测试逻辑</li>
     *   <li>服务器使用非阻塞模式提升性能</li>
     * </ul>
     *
     * @param host    服务器地址
     * @param port    服务器端口
     * @param message 发送的消息
     * @throws IOException 如果 I/O 错误发生
     */
    private void sendNIOEchoRequest(String host, int port, String message) throws IOException {
        try (SocketChannel channel = SocketChannel.open(new InetSocketAddress(host, port))) {
            // 客户端使用阻塞模式
            channel.configureBlocking(true);

            ByteBuffer writeBuffer = ByteBuffer.allocate(BUFFER_SIZE);
            ByteBuffer readBuffer = ByteBuffer.allocate(BUFFER_SIZE);

            // 发送消息
            writeBuffer.put((message + "\n").getBytes(StandardCharsets.UTF_8));
            writeBuffer.flip();
            channel.write(writeBuffer);

            // 接收响应
            readBuffer.clear();
            int bytesRead = channel.read(readBuffer);
            if (bytesRead < 0) {
                throw new IOException("Server closed connection");
            }

            readBuffer.flip();
            String response = StandardCharsets.UTF_8.decode(readBuffer).toString().trim();

            // 验证响应
            String expected = "ECHO: " + message;
            if (!expected.equals(response)) {
                throw new AssertionError("Response mismatch: expected '" + expected + "', got '" + response + "'");
            }
        }
    }

    // ==================== NIO 服务器实现（简化版）====================

    /**
     * NIO Selector 服务器（简化版）
     *
     * <p><strong>事件驱动流程</strong>：
     * <pre>
     * 1. selector.select()       → 阻塞等待事件
     * 2. 遍历 selectedKeys        → 批量处理就绪事件
     * 3. 处理 ACCEPT 事件         → 注册新 Channel
     * 4. 处理 READ 事件           → 读取数据
     * 5. 处理 WRITE 事件          → 写入响应
     * </pre>
     */
    private void startNIOServer() {
        try (ServerSocketChannel serverChannel = ServerSocketChannel.open();
             Selector selector = Selector.open()) {

            // 配置非阻塞模式
            serverChannel.configureBlocking(false);
            serverChannel.bind(new InetSocketAddress(NIO_PORT));
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);

            // 事件循环
            while (serverRunning) {
                // 阻塞等待事件（超时 1 秒防止死锁）
                int readyChannels = selector.select(1000);
                if (readyChannels == 0) {
                    continue;
                }

                // 处理就绪事件
                Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();
                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();
                    keyIterator.remove();

                    try {
                        if (!key.isValid()) {
                            continue;
                        }

                        if (key.isAcceptable()) {
                            handleAccept(key, selector);
                        } else if (key.isReadable()) {
                            handleRead(key);
                        } else if (key.isWritable()) {
                            handleWrite(key);
                        }
                    } catch (IOException e) {
                        closeChannel(key);
                    }
                }
            }
        } catch (IOException e) {
            // 忽略服务器关闭异常
        }
    }

    /**
     * 处理 ACCEPT 事件（新连接）
     */
    private void handleAccept(SelectionKey key, Selector selector) throws IOException {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
        SocketChannel clientChannel = serverChannel.accept();

        if (clientChannel != null) {
            clientChannel.configureBlocking(false);
            clientChannel.register(selector, SelectionKey.OP_READ, ByteBuffer.allocate(BUFFER_SIZE));
        }
    }

    /**
     * 处理 READ 事件（读取数据）
     */
    private void handleRead(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        ByteBuffer buffer = (ByteBuffer) key.attachment();

        int bytesRead = channel.read(buffer);
        if (bytesRead < 0) {
            closeChannel(key);
            return;
        }

        // 检查是否有完整消息（以 \n 结尾）
        buffer.flip();
        String data = StandardCharsets.UTF_8.decode(buffer).toString();
        buffer.clear();

        if (data.contains("\n")) {
            // 准备响应
            String message = data.trim();
            String response = "ECHO: " + message + "\n";
            ByteBuffer writeBuffer = ByteBuffer.wrap(response.getBytes(StandardCharsets.UTF_8));
            key.attach(writeBuffer);

            // 切换到写模式
            key.interestOps(SelectionKey.OP_WRITE);
        }
    }

    /**
     * 处理 WRITE 事件（写入响应）
     */
    private void handleWrite(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        ByteBuffer buffer = (ByteBuffer) key.attachment();

        channel.write(buffer);

        if (!buffer.hasRemaining()) {
            // 写完后关闭连接（短连接模式）
            closeChannel(key);
        }
    }

    /**
     * 关闭 Channel
     */
    private void closeChannel(SelectionKey key) {
        try {
            key.channel().close();
        } catch (IOException e) {
            // 忽略关闭异常
        }
        key.cancel();
    }

    // ==================== Main 方法（独立运行）====================

    /**
     * 独立运行入口
     *
     * <p><strong>使用方式</strong>：
     * <pre>
     * mvn test-compile exec:java -Dexec.mainClass="nan.tech.lab06.benchmark.NIOBenchmark"
     * </pre>
     *
     * @param args 命令行参数
     * @throws RunnerException 如果 JMH 运行失败
     */
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
            .include(NIOBenchmark.class.getSimpleName())
            .forks(1)
            .build();

        new Runner(opt).run();
    }
}
