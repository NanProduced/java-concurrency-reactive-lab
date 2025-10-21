package nan.tech.lab06.bio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * BIO (Blocking I/O) Echo Server 演示
 *
 * <p><strong>教学目标</strong>：
 * <ul>
 *   <li>理解阻塞 I/O 的工作原理</li>
 *   <li>对比单线程 vs 多线程模型</li>
 *   <li>认识"每连接一线程"模型的资源问题</li>
 *   <li>掌握资源管理最佳实践（try-with-resources）</li>
 * </ul>
 *
 * <p><strong>核心问题</strong>：
 * <ul>
 *   <li>为什么 BIO 需要为每个连接创建一个线程？</li>
 *   <li>单线程 BIO Server 为什么无法处理并发连接？</li>
 *   <li>多线程 BIO Server 的性能瓶颈在哪里？</li>
 * </ul>
 *
 * <p><strong>前置知识</strong>：
 * 阅读 {@code docs/prerequisites/IO_MODELS.md} 了解阻塞 I/O 模型
 *
 * @author AI Assistant
 * @since 1.0.0
 */
public class BIOEchoServer {

    private static final Logger log = LoggerFactory.getLogger(BIOEchoServer.class);

    /**
     * 默认服务器端口
     */
    private static final int DEFAULT_PORT = 8080;

    /**
     * 活跃连接计数器（用于监控）
     */
    private static final AtomicInteger activeConnections = new AtomicInteger(0);

    /**
     * 总处理请求计数器
     */
    private static final AtomicInteger totalRequests = new AtomicInteger(0);

    // ==================== 单线程版本 ====================

    /**
     * <strong>版本 1: 单线程 BIO Echo Server（阻塞问题演示）</strong>
     *
     * <p><strong>工作原理</strong>：
     * <pre>
     * 1. ServerSocket.accept() 阻塞，等待客户端连接
     * 2. 接受连接后，处理客户端请求（读取 + 写入）
     * 3. 处理完毕后，关闭连接
     * 4. 返回步骤 1，继续等待下一个连接
     * </pre>
     *
     * <p><strong>❌ 核心问题</strong>：
     * <ul>
     *   <li><strong>串行处理</strong>: 同一时间只能处理一个客户端</li>
     *   <li><strong>阻塞等待</strong>: 在处理客户端 A 时，客户端 B 的连接请求会被阻塞</li>
     *   <li><strong>吞吐量低</strong>: TPS（Transaction Per Second）受限于单个请求的处理时间</li>
     * </ul>
     *
     * <p><strong>适用场景</strong>：
     * <ul>
     *   <li>并发连接数 &lt; 10</li>
     *   <li>请求处理时间极短（&lt; 10ms）</li>
     *   <li>学习演示、调试工具</li>
     * </ul>
     *
     * @param port 服务器监听端口
     * @throws IOException 如果 I/O 错误发生
     */
    public static void startSingleThreadServer(int port) throws IOException {
        log.info("========================================");
        log.info("启动单线程 BIO Echo Server - 端口: {}", port);
        log.info("========================================");
        log.warn("⚠️  单线程模式：同一时间只能处理一个客户端");
        log.warn("⚠️  并发客户端会被阻塞，等待前一个客户端处理完毕");

        // 创建 ServerSocket（监听指定端口）
        // try-with-resources 确保资源自动释放
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            // 启用地址复用（避免 TIME_WAIT 状态导致端口不可用）
            serverSocket.setReuseAddress(true);

            log.info("✅ 服务器启动成功，等待客户端连接...");

            // 主循环：持续接受客户端连接
            while (!Thread.currentThread().isInterrupted()) {
                // ⚠️ 阻塞点 1: accept() 阻塞，直到有客户端连接
                // 在此期间，主线程无法处理其他任务
                Socket clientSocket = serverSocket.accept();

                // 获取客户端地址信息
                String clientInfo = clientSocket.getInetAddress().getHostAddress() + ":" + clientSocket.getPort();
                log.info("📥 接受新连接: {}", clientInfo);

                // 更新统计信息
                int currentConnections = activeConnections.incrementAndGet();
                log.debug("当前活跃连接数: {}", currentConnections);

                // ⚠️ 串行处理：处理客户端请求（阻塞）
                // 在处理完当前客户端之前，无法接受新连接
                handleClient(clientSocket);

                // 处理完毕，更新统计
                activeConnections.decrementAndGet();
                log.info("✅ 连接关闭: {}", clientInfo);
            }
        }
    }

    // ==================== 多线程版本 ====================

    /**
     * <strong>版本 2: 多线程 BIO Echo Server（每连接一线程）</strong>
     *
     * <p><strong>工作原理</strong>：
     * <pre>
     * 1. 主线程: ServerSocket.accept() 阻塞，等待客户端连接
     * 2. 接受连接后，创建新线程处理客户端请求
     * 3. 工作线程: 读取 + 写入 + 关闭连接
     * 4. 主线程立即返回步骤 1，继续接受新连接
     * </pre>
     *
     * <p><strong>✅ 改进点</strong>：
     * <ul>
     *   <li><strong>并发处理</strong>: 可以同时处理多个客户端</li>
     *   <li><strong>高吞吐量</strong>: TPS 不再受单个请求时间限制</li>
     *   <li><strong>响应性好</strong>: 新连接不会被阻塞</li>
     * </ul>
     *
     * <p><strong>❌ 新问题</strong>：
     * <ul>
     *   <li><strong>线程开销</strong>: 每个连接创建一个线程（1MB 栈空间）</li>
     *   <li><strong>C10K 问题</strong>: 10000 连接 = 10000 线程 ≈ 10GB 内存</li>
     *   <li><strong>上下文切换</strong>: 大量线程导致 CPU 性能下降</li>
     *   <li><strong>资源耗尽</strong>: 达到系统线程上限后，新连接被拒绝</li>
     * </ul>
     *
     * <p><strong>适用场景</strong>：
     * <ul>
     *   <li>并发连接数 10 - 1000</li>
     *   <li>连接持续时间较短（&lt; 1 分钟）</li>
     *   <li>请求处理包含 I/O 操作（数据库查询、文件读写）</li>
     * </ul>
     *
     * @param port 服务器监听端口
     * @throws IOException 如果 I/O 错误发生
     */
    public static void startMultiThreadServer(int port) throws IOException {
        log.info("========================================");
        log.info("启动多线程 BIO Echo Server - 端口: {}", port);
        log.info("========================================");
        log.info("✅ 多线程模式：为每个连接创建一个新线程");
        log.warn("⚠️  高并发场景下，线程数可能爆炸（C10K 问题）");

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            serverSocket.setReuseAddress(true);

            log.info("✅ 服务器启动成功，等待客户端连接...");

            while (!Thread.currentThread().isInterrupted()) {
                // ⚠️ 阻塞点: accept() 阻塞（但不影响已建立的连接）
                Socket clientSocket = serverSocket.accept();

                String clientInfo = clientSocket.getInetAddress().getHostAddress() + ":" + clientSocket.getPort();
                log.info("📥 接受新连接: {}", clientInfo);

                int currentConnections = activeConnections.incrementAndGet();
                log.debug("当前活跃连接数: {} | 活跃线程数: {}", currentConnections, Thread.activeCount());

                // ✅ 并发处理：为每个连接创建新线程
                // 主线程立即返回，继续接受新连接
                Thread clientThread = new Thread(() -> {
                    try {
                        handleClient(clientSocket);
                    } finally {
                        activeConnections.decrementAndGet();
                        log.info("✅ 连接关闭: {} | 剩余活跃连接: {}", clientInfo, activeConnections.get());
                    }
                }, "Client-Handler-" + totalRequests.incrementAndGet());

                // 启动工作线程
                clientThread.start();

                // ⚠️ 注意: 没有等待线程结束，主线程立即返回继续 accept()
            }
        }
    }

    // ==================== 多线程版本（线程池优化）====================

    /**
     * <strong>版本 3: 线程池 BIO Echo Server（资源可控）</strong>
     *
     * <p><strong>工作原理</strong>：
     * <pre>
     * 1. 预创建固定数量的工作线程（线程池）
     * 2. 主线程: ServerSocket.accept() 阻塞，等待客户端连接
     * 3. 接受连接后，提交任务到线程池
     * 4. 工作线程: 从队列获取任务，处理客户端请求
     * </pre>
     *
     * <p><strong>✅ 改进点</strong>：
     * <ul>
     *   <li><strong>资源可控</strong>: 线程数固定，不会无限增长</li>
     *   <li><strong>线程复用</strong>: 减少线程创建/销毁开销</li>
     *   <li><strong>任务队列</strong>: 请求过多时排队，而非拒绝</li>
     * </ul>
     *
     * <p><strong>⚠️ 权衡</strong>：
     * <ul>
     *   <li><strong>队列堆积</strong>: 请求速度 &gt; 处理速度时，队列无限增长</li>
     *   <li><strong>延迟增加</strong>: 排队等待时间增加</li>
     *   <li><strong>仍然阻塞</strong>: 工作线程在 I/O 操作时阻塞</li>
     * </ul>
     *
     * <p><strong>适用场景</strong>：
     * <ul>
     *   <li>并发连接数 100 - 5000</li>
     *   <li>需要控制资源使用（内存、线程数）</li>
     *   <li>可以接受一定的请求排队</li>
     * </ul>
     *
     * @param port       服务器监听端口
     * @param threadPoolSize 线程池大小
     * @throws IOException 如果 I/O 错误发生
     */
    public static void startThreadPoolServer(int port, int threadPoolSize) throws IOException {
        log.info("========================================");
        log.info("启动线程池 BIO Echo Server - 端口: {} | 线程池大小: {}", port, threadPoolSize);
        log.info("========================================");
        log.info("✅ 线程池模式：固定 {} 个工作线程", threadPoolSize);
        log.info("✅ 请求过多时排队，而非创建新线程");

        // 创建固定大小的线程池
        ExecutorService threadPool = Executors.newFixedThreadPool(threadPoolSize, r -> {
            Thread t = new Thread(r);
            t.setName("PooledWorker-" + t.getId());
            return t;
        });

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            serverSocket.setReuseAddress(true);

            log.info("✅ 服务器启动成功，等待客户端连接...");

            while (!Thread.currentThread().isInterrupted()) {
                Socket clientSocket = serverSocket.accept();

                String clientInfo = clientSocket.getInetAddress().getHostAddress() + ":" + clientSocket.getPort();
                log.info("📥 接受新连接: {}", clientInfo);

                int currentConnections = activeConnections.incrementAndGet();
                log.debug("当前活跃连接数: {} | 线程池活跃线程: {} / {}",
                    currentConnections, Thread.activeCount() - 1, threadPoolSize);

                // 提交任务到线程池
                threadPool.submit(() -> {
                    try {
                        handleClient(clientSocket);
                    } finally {
                        activeConnections.decrementAndGet();
                        log.info("✅ 连接关闭: {} | 剩余活跃连接: {}", clientInfo, activeConnections.get());
                    }
                });
            }
        } finally {
            // 优雅关闭线程池
            shutdownThreadPool(threadPool);
        }
    }

    // ==================== 客户端请求处理 ====================

    /**
     * 处理单个客户端连接（Echo 协议实现）
     *
     * <p><strong>Echo 协议</strong>：
     * <ul>
     *   <li>读取客户端发送的消息</li>
     *   <li>原样返回（Echo）</li>
     *   <li>直到客户端关闭连接</li>
     * </ul>
     *
     * <p><strong>⚠️ 阻塞点</strong>：
     * <ul>
     *   <li>{@code in.readLine()}: 阻塞，直到客户端发送一行数据或关闭连接</li>
     *   <li>{@code out.write()}: 通常不阻塞（发送缓冲区有空间），但网络拥塞时可能阻塞</li>
     * </ul>
     *
     * <p><strong>资源管理</strong>：
     * <ul>
     *   <li>使用 {@code try-with-resources} 确保资源释放</li>
     *   <li>关闭顺序: OutputStream → InputStream → Socket</li>
     * </ul>
     *
     * @param socket 客户端连接 Socket
     */
    private static void handleClient(Socket socket) {
        String clientInfo = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();

        // ✅ 最佳实践: try-with-resources 自动关闭资源
        // 关闭顺序: BufferedReader → BufferedWriter → InputStream → OutputStream → Socket
        try (socket; // Socket 也实现了 AutoCloseable
             InputStream inputStream = socket.getInputStream();
             OutputStream outputStream = socket.getOutputStream();
             BufferedReader in = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))) {

            log.debug("[{}] 开始处理客户端请求", clientInfo);

            String line;
            int messageCount = 0;

            // ⚠️ 阻塞循环: 读取客户端消息，直到连接关闭
            while ((line = in.readLine()) != null) {
                messageCount++;
                log.debug("[{}] 收到消息 #{}: {}", clientInfo, messageCount, line);

                // Echo: 原样返回
                String response = "ECHO: " + line + "\n";
                out.write(response);
                out.flush(); // 立即发送（不等待缓冲区满）

                log.debug("[{}] 发送响应 #{}: {}", clientInfo, messageCount, response.trim());

                // 统计
                totalRequests.incrementAndGet();

                // 特殊命令: 客户端主动关闭
                if ("QUIT".equalsIgnoreCase(line.trim())) {
                    log.info("[{}] 客户端请求关闭连接", clientInfo);
                    break;
                }
            }

            log.info("[{}] 客户端连接关闭 | 处理消息数: {}", clientInfo, messageCount);

        } catch (IOException e) {
            // ❌ 常见错误: 客户端异常断开（RST）、网络超时
            log.warn("[{}] I/O 异常: {}", clientInfo, e.getMessage());
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 优雅关闭线程池
     *
     * <p><strong>关闭步骤</strong>：
     * <ol>
     *   <li>调用 {@code shutdown()}: 不再接受新任务，但继续执行已提交的任务</li>
     *   <li>等待最多 60 秒，让任务执行完毕</li>
     *   <li>如果超时，调用 {@code shutdownNow()}: 尝试中断所有任务</li>
     *   <li>再等待 60 秒，确保线程池完全关闭</li>
     * </ol>
     *
     * @param threadPool 要关闭的线程池
     */
    private static void shutdownThreadPool(ExecutorService threadPool) {
        log.info("正在关闭线程池...");

        // 第一步: 不再接受新任务
        threadPool.shutdown();

        try {
            // 第二步: 等待任务完成（最多 60 秒）
            if (!threadPool.awaitTermination(60, TimeUnit.SECONDS)) {
                log.warn("线程池未在 60 秒内关闭，尝试强制关闭...");

                // 第三步: 强制关闭（中断所有任务）
                threadPool.shutdownNow();

                // 第四步: 再等待 60 秒
                if (!threadPool.awaitTermination(60, TimeUnit.SECONDS)) {
                    log.error("线程池无法关闭");
                }
            }

            log.info("✅ 线程池已关闭");

        } catch (InterruptedException e) {
            log.error("线程池关闭被中断", e);
            threadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // ==================== 主方法（演示入口）====================

    /**
     * 演示入口（混合模式：支持命令行参数和交互式菜单）
     *
     * <p><strong>使用方式</strong>：
     *
     * <p><strong>方式 1: 交互式菜单（推荐在 IDE 中使用）</strong>
     * <pre>
     * # 无参数启动，会显示菜单选择
     * mvn exec:java -Dexec.mainClass="nan.tech.lab06.bio.BIOEchoServer"
     *
     * 输出:
     * =====================================
     * 🔧 Lab-06 BIO Echo Server 演示
     * =====================================
     * 1. 单线程 BIO Server (阻塞演示)
     * 2. 多线程 BIO Server (每连接一线程)
     * 3. 线程池 BIO Server (100 线程)
     * 4. 自定义线程池大小
     * 5. 退出
     *
     * 请选择 [1-5]:
     * </pre>
     *
     * <p><strong>方式 2: 命令行参数（适合脚本和自动化）</strong>
     * <pre>
     * # 启动单线程服务器
     * mvn exec:java -Dexec.mainClass="nan.tech.lab06.bio.BIOEchoServer" -Dexec.args="single"
     *
     * # 启动多线程服务器
     * mvn exec:java -Dexec.mainClass="nan.tech.lab06.bio.BIOEchoServer" -Dexec.args="multi"
     *
     * # 启动线程池服务器（指定线程数）
     * mvn exec:java -Dexec.mainClass="nan.tech.lab06.bio.BIOEchoServer" -Dexec.args="pool 100"
     * </pre>
     *
     * <p><strong>测试客户端</strong>：
     * <pre>
     * # 使用 telnet 测试
     * telnet localhost 8080
     * > Hello
     * ECHO: Hello
     * > QUIT
     * Connection closed.
     *
     * # 使用 netcat 测试
     * nc localhost 8080
     * Hello
     * ECHO: Hello
     * </pre>
     *
     * @param args 命令行参数（可选）[模式] [线程池大小]
     *             - 如果无参数：显示交互式菜单
     *             - 如果有参数：模式: single | multi | pool
     * @throws IOException 如果 I/O 错误发生
     */
    public static void main(String[] args) throws IOException {
        // 优先级 1: 有参数则直接使用参数（适合脚本）
        if (args.length > 0) {
            processCommandLineArgs(args);
        }
        // 优先级 2: 无参数则显示交互式菜单（适合 IDE）
        else {
            displayInteractiveMenu();
        }
    }

    /**
     * 处理命令行参数
     *
     * @param args 命令行参数
     * @throws IOException 如果 I/O 错误发生
     */
    private static void processCommandLineArgs(String[] args) throws IOException {
        String mode = args[0];
        int port = DEFAULT_PORT;

        switch (mode.toLowerCase()) {
            case "single":
                startSingleThreadServer(port);
                break;

            case "multi":
                startMultiThreadServer(port);
                break;

            case "pool":
                int threadPoolSize = args.length > 1 ? Integer.parseInt(args[1]) : 100;
                startThreadPoolServer(port, threadPoolSize);
                break;

            default:
                System.err.println("❌ 未知模式: " + mode);
                System.err.println("支持的模式: single | multi | pool");
                System.exit(1);
        }
    }

    /**
     * 显示交互式菜单（在 IDE 中运行无参数时调用）
     *
     * @throws IOException 如果 I/O 错误发生
     */
    private static void displayInteractiveMenu() throws IOException {
        System.out.println();
        System.out.println("=====================================");
        System.out.println("🔧 Lab-06 BIO Echo Server 演示");
        System.out.println("=====================================");
        System.out.println("1. 单线程 BIO Server (阻塞演示)");
        System.out.println("2. 多线程 BIO Server (每连接一线程)");
        System.out.println("3. 线程池 BIO Server (100 线程)");
        System.out.println("4. 自定义线程池大小");
        System.out.println("5. 退出");
        System.out.println("=====================================");
        System.out.print("\n请选择 [1-5]: ");

        try (Scanner scanner = new Scanner(System.in)) {
            String choice = scanner.nextLine().trim();

            int port = DEFAULT_PORT;

            switch (choice) {
                case "1":
                    startSingleThreadServer(port);
                    break;

                case "2":
                    startMultiThreadServer(port);
                    break;

                case "3":
                    startThreadPoolServer(port, 100);
                    break;

                case "4":
                    System.out.print("请输入线程池大小 [默认 100]: ");
                    String sizeInput = scanner.nextLine().trim();
                    int threadPoolSize = sizeInput.isEmpty() ? 100 : Integer.parseInt(sizeInput);
                    startThreadPoolServer(port, threadPoolSize);
                    break;

                case "5":
                    System.out.println("👋 再见!");
                    System.exit(0);
                    break;

                default:
                    System.err.println("❌ 无效选择，请输入 1-5");
                    displayInteractiveMenu();
            }
        } catch (NumberFormatException e) {
            System.err.println("❌ 输入错误: " + e.getMessage());
            displayInteractiveMenu();
        }
    }
}
