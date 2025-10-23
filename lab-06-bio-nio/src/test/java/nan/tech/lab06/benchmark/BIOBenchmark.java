package nan.tech.lab06.benchmark;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * BIO (Blocking I/O) 性能基准测试
 *
 * <p><strong>测试目标</strong>：
 * <ul>
 *   <li>对比三种 BIO 服务器模式的 TPS 和延迟</li>
 *   <li>验证线程池模式的性能优势</li>
 *   <li>分析 BIO 的性能瓶颈</li>
 * </ul>
 *
 * <p><strong>测试场景</strong>：
 * <pre>
 * 场景 1: 单线程 BIO 服务器（baseline）
 * 场景 2: 多线程 BIO 服务器（one-thread-per-connection）
 * 场景 3: 线程池 BIO 服务器（resource-controlled）
 * </pre>
 *
 * <p><strong>运行方式</strong>：
 * <pre>
 * # 运行所有 BIO benchmark
 * mvn test-compile exec:java -Dexec.mainClass="nan.tech.lab06.benchmark.BIOBenchmark"
 *
 * # 运行单个测试
 * mvn test-compile exec:java -Dexec.mainClass="nan.tech.lab06.benchmark.BIOBenchmark" \
 *   -Dexec.args="BIOBenchmark.testThreadPoolServer"
 * </pre>
 *
 * <p><strong>预期结果</strong>（参考值，因硬件而异）：
 * <pre>
 * testSingleThreadServer: ~500 ops/s (单线程串行处理)
 * testMultiThreadServer:  ~3000 ops/s (受线程创建开销限制)
 * testThreadPoolServer:   ~5000 ops/s (线程复用优势)
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
public class BIOBenchmark {

    // ==================== 服务器配置 ====================

    /**
     * 单线程服务器端口
     */
    private static final int SINGLE_THREAD_PORT = 18080;

    /**
     * 多线程服务器端口
     */
    private static final int MULTI_THREAD_PORT = 18081;

    /**
     * 线程池服务器端口
     */
    private static final int THREAD_POOL_PORT = 18082;

    /**
     * 线程池大小
     */
    private static final int POOL_SIZE = 20;

    /**
     * 服务器线程（后台运行）
     */
    private Thread singleThreadServerThread;
    private Thread multiThreadServerThread;
    private Thread threadPoolServerThread;

    /**
     * 线程池（用于多线程和线程池服务器）
     */
    private ExecutorService multiThreadExecutor;
    private ExecutorService threadPoolExecutor;

    // ==================== 测试生命周期 ====================

    /**
     * 测试前启动所有服务器
     */
    @Setup(Level.Trial)
    public void setup() throws InterruptedException {
        // 1. 启动单线程服务器
        singleThreadServerThread = new Thread(() -> startSingleThreadServer(SINGLE_THREAD_PORT));
        singleThreadServerThread.setDaemon(true);
        singleThreadServerThread.start();

        // 2. 启动多线程服务器
        multiThreadExecutor = Executors.newCachedThreadPool();
        multiThreadServerThread = new Thread(() -> startMultiThreadServer(MULTI_THREAD_PORT, multiThreadExecutor));
        multiThreadServerThread.setDaemon(true);
        multiThreadServerThread.start();

        // 3. 启动线程池服务器
        threadPoolExecutor = Executors.newFixedThreadPool(POOL_SIZE);
        threadPoolServerThread = new Thread(() -> startThreadPoolServer(THREAD_POOL_PORT, threadPoolExecutor));
        threadPoolServerThread.setDaemon(true);
        threadPoolServerThread.start();

        // 等待服务器启动
        Thread.sleep(2000);
    }

    /**
     * 测试后关闭所有服务器
     */
    @TearDown(Level.Trial)
    public void teardown() {
        if (multiThreadExecutor != null) {
            multiThreadExecutor.shutdownNow();
        }
        if (threadPoolExecutor != null) {
            threadPoolExecutor.shutdownNow();
        }
    }

    // ==================== Benchmark 测试 ====================

    /**
     * Benchmark 1: 单线程 BIO 服务器
     *
     * <p><strong>特点</strong>：
     * <ul>
     *   <li>单线程串行处理所有请求</li>
     *   <li>性能最差但资源占用最低</li>
     *   <li>适合极低并发场景（< 10）</li>
     * </ul>
     */
    @Benchmark
    public void testSingleThreadServer() throws IOException {
        sendEchoRequest("localhost", SINGLE_THREAD_PORT, "Hello-Single-Thread");
    }

    /**
     * Benchmark 2: 多线程 BIO 服务器
     *
     * <p><strong>特点</strong>：
     * <ul>
     *   <li>每个连接创建一个新线程</li>
     *   <li>线程创建开销影响性能</li>
     *   <li>适合中等并发场景（< 1000）</li>
     * </ul>
     */
    @Benchmark
    public void testMultiThreadServer() throws IOException {
        sendEchoRequest("localhost", MULTI_THREAD_PORT, "Hello-Multi-Thread");
    }

    /**
     * Benchmark 3: 线程池 BIO 服务器
     *
     * <p><strong>特点</strong>：
     * <ul>
     *   <li>线程复用减少创建开销</li>
     *   <li>性能最佳但有资源上限</li>
     *   <li>适合高并发场景（< 10000）</li>
     * </ul>
     */
    @Benchmark
    public void testThreadPoolServer() throws IOException {
        sendEchoRequest("localhost", THREAD_POOL_PORT, "Hello-Thread-Pool");
    }

    // ==================== 辅助方法 ====================

    /**
     * 发送 Echo 请求并接收响应
     *
     * @param host    服务器地址
     * @param port    服务器端口
     * @param message 发送的消息
     * @throws IOException 如果 I/O 错误发生
     */
    private void sendEchoRequest(String host, int port, String message) throws IOException {
        try (Socket socket = new Socket(host, port);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {

            // 发送消息
            writer.println(message);

            // 接收响应
            String response = reader.readLine();

            // 验证响应
            if (!("ECHO: " + message).equals(response)) {
                throw new AssertionError("Response mismatch: expected 'ECHO: " + message + "', got '" + response + "'");
            }
        }
    }

    // ==================== 服务器实现（简化版）====================

    /**
     * 单线程 BIO 服务器（简化版）
     */
    private void startSingleThreadServer(int port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (!Thread.currentThread().isInterrupted()) {
                Socket clientSocket = serverSocket.accept();
                handleClient(clientSocket); // 串行处理
            }
        } catch (IOException e) {
            // 忽略关闭异常
        }
    }

    /**
     * 多线程 BIO 服务器（简化版）
     */
    private void startMultiThreadServer(int port, ExecutorService executor) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (!Thread.currentThread().isInterrupted()) {
                Socket clientSocket = serverSocket.accept();
                executor.submit(() -> handleClient(clientSocket)); // 新线程处理
            }
        } catch (IOException e) {
            // 忽略关闭异常
        }
    }

    /**
     * 线程池 BIO 服务器（简化版）
     */
    private void startThreadPoolServer(int port, ExecutorService executor) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (!Thread.currentThread().isInterrupted()) {
                Socket clientSocket = serverSocket.accept();
                executor.submit(() -> handleClient(clientSocket)); // 线程池处理
            }
        } catch (IOException e) {
            // 忽略关闭异常
        }
    }

    /**
     * 处理客户端连接（Echo 逻辑）
     */
    private void handleClient(Socket socket) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {

            String message = reader.readLine();
            if (message != null) {
                writer.println("ECHO: " + message);
            }
        } catch (IOException e) {
            // 忽略客户端异常
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                // 忽略关闭异常
            }
        }
    }

    // ==================== Main 方法（独立运行）====================

    /**
     * 独立运行入口
     *
     * <p><strong>使用方式</strong>：
     * <pre>
     * mvn test-compile exec:java -Dexec.mainClass="nan.tech.lab06.benchmark.BIOBenchmark"
     * </pre>
     *
     * @param args 命令行参数
     * @throws RunnerException 如果 JMH 运行失败
     */
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
            .include(BIOBenchmark.class.getSimpleName())
            .forks(1)
            .build();

        new Runner(opt).run();
    }
}
