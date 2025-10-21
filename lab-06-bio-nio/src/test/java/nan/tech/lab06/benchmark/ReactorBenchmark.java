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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Reactor 模式性能基准测试
 *
 * <p><strong>测试目标</strong>：
 * <ul>
 *   <li>验证主从 Reactor 模式的性能优势</li>
 *   <li>对比不同 Worker 数量的性能差异</li>
 *   <li>分析 Reactor 模式的扩展性</li>
 * </ul>
 *
 * <p><strong>架构对比</strong>：
 * <pre>
 * 单 Reactor (NIO):
 *   ┌───────────────┐
 *   │  Main Thread  │ ← ACCEPT + READ + WRITE
 *   └───────────────┘
 *   性能瓶颈: 单线程处理所有 I/O 事件
 *
 * 主从 Reactor (本测试):
 *   ┌─────────────┐
 *   │ Boss Thread │ ← 只处理 ACCEPT
 *   └─────────────┘
 *          ↓
 *   ┌─────────────┐
 *   │  Worker-1   │ ← 处理 READ + WRITE
 *   │  Worker-2   │
 *   │  Worker-N   │
 *   └─────────────┘
 *   性能提升: 多核 CPU 并行处理 I/O
 * </pre>
 *
 * <p><strong>运行方式</strong>：
 * <pre>
 * # 运行 Reactor benchmark
 * mvn test-compile exec:java -Dexec.mainClass="nan.tech.lab06.benchmark.ReactorBenchmark"
 * </pre>
 *
 * <p><strong>预期结果</strong>（参考值，4 核 CPU）：
 * <pre>
 * testReactorWith1Worker:  ~12000 ops/s (略优于单 Reactor)
 * testReactorWith2Workers: ~22000 ops/s (接近线性扩展)
 * testReactorWith4Workers: ~40000 ops/s (4x NIO 性能)
 * testReactorWith8Workers: ~45000 ops/s (接近 CPU 瓶颈)
 *
 * 性能分析:
 *   - 1-4 workers: 接近线性扩展（CPU 未饱和）
 *   - 4-8 workers: 收益递减（CPU 饱和）
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
public class ReactorBenchmark {

    // ==================== 服务器配置 ====================

    /**
     * Reactor 服务器端口（基础端口）
     */
    private static final int REACTOR_BASE_PORT = 20000;

    /**
     * ByteBuffer 大小
     */
    private static final int BUFFER_SIZE = 1024;

    /**
     * 测试的 Worker 数量配置
     */
    @Param({"1", "2", "4", "8"})
    public int workerCount;

    /**
     * Reactor 服务器实例
     */
    private ReactorServer reactorServer;

    // ==================== 测试生命周期 ====================

    /**
     * 每个参数组合启动一个新服务器
     */
    @Setup(Level.Iteration)
    public void setup() throws InterruptedException {
        int port = REACTOR_BASE_PORT + workerCount; // 避免端口冲突
        reactorServer = new ReactorServer(port, workerCount);
        reactorServer.start();

        // 等待服务器启动
        Thread.sleep(2000);
    }

    /**
     * 测试后关闭服务器
     */
    @TearDown(Level.Iteration)
    public void teardown() {
        if (reactorServer != null) {
            reactorServer.stop();
        }
    }

    // ==================== Benchmark 测试 ====================

    /**
     * Benchmark: Reactor 模式（参数化 Worker 数量）
     *
     * <p><strong>性能预期</strong>：
     * <ul>
     *   <li>1 Worker: 基线性能</li>
     *   <li>2 Workers: ~2x 性能</li>
     *   <li>4 Workers: ~4x 性能（接近线性扩展）</li>
     *   <li>8 Workers: 增长放缓（CPU 瓶颈）</li>
     * </ul>
     */
    @Benchmark
    public void testReactor() throws IOException {
        int port = REACTOR_BASE_PORT + workerCount;
        sendEchoRequest("localhost", port, "Hello-Reactor-" + workerCount);
    }

    // ==================== 辅助方法 ====================

    /**
     * 发送 Echo 请求（阻塞模式客户端）
     */
    private void sendEchoRequest(String host, int port, String message) throws IOException {
        try (SocketChannel channel = SocketChannel.open(new InetSocketAddress(host, port))) {
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

    // ==================== Reactor 服务器实现（简化版）====================

    /**
     * 主从 Reactor 服务器（简化版）
     */
    private static class ReactorServer {
        private final int port;
        private final int workerCount;
        private volatile boolean running = false;
        private Thread bossThread;
        private SubReactor[] subReactors;

        public ReactorServer(int port, int workerCount) {
            this.port = port;
            this.workerCount = workerCount;
        }

        /**
         * 启动服务器
         */
        public void start() {
            running = true;

            // 启动 Sub Reactors (Workers)
            subReactors = new SubReactor[workerCount];
            for (int i = 0; i < workerCount; i++) {
                subReactors[i] = new SubReactor("SubReactor-" + i);
                subReactors[i].start();
            }

            // 启动 Main Reactor (Boss)
            bossThread = new Thread(this::runBossReactor, "BossReactor");
            bossThread.setDaemon(true);
            bossThread.start();
        }

        public void stop() {
            running = false;
            if (bossThread != null) {
                bossThread.interrupt();
            }
            if (subReactors != null) {
                for (SubReactor reactor : subReactors) {
                    reactor.stopReactor();
                }
            }
        }

        /**
         * Boss Reactor（主 Reactor）
         * 职责：只处理 ACCEPT 事件
         */
        private void runBossReactor() {
            try (ServerSocketChannel serverChannel = ServerSocketChannel.open();
                 Selector selector = Selector.open()) {

                serverChannel.configureBlocking(false);
                serverChannel.bind(new InetSocketAddress(port));
                serverChannel.register(selector, SelectionKey.OP_ACCEPT);

                AtomicInteger roundRobinIndex = new AtomicInteger(0);

                while (running) {
                    int readyChannels = selector.select(1000);
                    if (readyChannels == 0) {
                        continue;
                    }

                    Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();
                    while (keyIterator.hasNext()) {
                        SelectionKey key = keyIterator.next();
                        keyIterator.remove();

                        if (key.isAcceptable()) {
                            ServerSocketChannel server = (ServerSocketChannel) key.channel();
                            SocketChannel clientChannel = server.accept();

                            if (clientChannel != null) {
                                // 轮询分配给 Sub Reactor
                                int index = roundRobinIndex.getAndIncrement() % workerCount;
                                subReactors[index].registerChannel(clientChannel);
                            }
                        }
                    }
                }
            } catch (IOException e) {
                // 忽略关闭异常
            }
        }
    }

    /**
     * Sub Reactor（从 Reactor）
     * 职责：处理 READ + WRITE 事件
     */
    private static class SubReactor extends Thread {
        private final Selector selector;
        private final BlockingQueue<SocketChannel> pendingChannels = new LinkedBlockingQueue<>();
        private volatile boolean running = true;

        public SubReactor(String name) {
            super(name);
            setDaemon(true);
            try {
                this.selector = Selector.open();
            } catch (IOException e) {
                throw new RuntimeException("Failed to create selector", e);
            }
        }

        /**
         * 注册新 Channel（Boss → Worker 通信）
         */
        public void registerChannel(SocketChannel channel) {
            pendingChannels.offer(channel);
            selector.wakeup(); // 唤醒 select()
        }

        public void stopReactor() {
            running = false;
            interrupt();
            try {
                selector.close();
            } catch (IOException e) {
                // 忽略
            }
        }

        @Override
        public void run() {
            while (running) {
                try {
                    // 注册新 Channel
                    SocketChannel channel;
                    while ((channel = pendingChannels.poll()) != null) {
                        channel.configureBlocking(false);
                        channel.register(selector, SelectionKey.OP_READ, ByteBuffer.allocate(BUFFER_SIZE));
                    }

                    // 处理 I/O 事件
                    int readyChannels = selector.select(1000);
                    if (readyChannels == 0) {
                        continue;
                    }

                    Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();
                    while (keyIterator.hasNext()) {
                        SelectionKey key = keyIterator.next();
                        keyIterator.remove();

                        try {
                            if (!key.isValid()) {
                                continue;
                            }

                            if (key.isReadable()) {
                                handleRead(key);
                            } else if (key.isWritable()) {
                                handleWrite(key);
                            }
                        } catch (IOException e) {
                            closeChannel(key);
                        }
                    }
                } catch (IOException e) {
                    // 忽略异常
                }
            }
        }

        /**
         * 处理 READ 事件
         */
        private void handleRead(SelectionKey key) throws IOException {
            SocketChannel channel = (SocketChannel) key.channel();
            ByteBuffer buffer = (ByteBuffer) key.attachment();

            int bytesRead = channel.read(buffer);
            if (bytesRead < 0) {
                closeChannel(key);
                return;
            }

            buffer.flip();
            String data = StandardCharsets.UTF_8.decode(buffer).toString();
            buffer.clear();

            if (data.contains("\n")) {
                String message = data.trim();
                String response = "ECHO: " + message + "\n";
                ByteBuffer writeBuffer = ByteBuffer.wrap(response.getBytes(StandardCharsets.UTF_8));
                key.attach(writeBuffer);
                key.interestOps(SelectionKey.OP_WRITE);
            }
        }

        /**
         * 处理 WRITE 事件
         */
        private void handleWrite(SelectionKey key) throws IOException {
            SocketChannel channel = (SocketChannel) key.channel();
            ByteBuffer buffer = (ByteBuffer) key.attachment();

            channel.write(buffer);

            if (!buffer.hasRemaining()) {
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
                // 忽略
            }
            key.cancel();
        }
    }

    // ==================== Main 方法（独立运行）====================

    /**
     * 独立运行入口
     *
     * <p><strong>使用方式</strong>：
     * <pre>
     * mvn test-compile exec:java -Dexec.mainClass="nan.tech.lab06.benchmark.ReactorBenchmark"
     * </pre>
     *
     * @param args 命令行参数
     * @throws RunnerException 如果 JMH 运行失败
     */
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
            .include(ReactorBenchmark.class.getSimpleName())
            .forks(1)
            .build();

        new Runner(opt).run();
    }
}
