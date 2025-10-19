package nan.tech.lab06.zerocopy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * 零拷贝（Zero-Copy）技术演示
 *
 * <p><strong>教学目标</strong>：
 * <ul>
 *   <li>理解传统 I/O 的数据拷贝过程</li>
 *   <li>掌握零拷贝技术的原理</li>
 *   <li>对比传统 I/O vs 零拷贝的性能差异</li>
 *   <li>了解 sendfile/transferTo 的应用场景</li>
 * </ul>
 *
 * <p><strong>核心问题</strong>：
 * <ul>
 *   <li>为什么传统 I/O 需要 4 次数据拷贝？</li>
 *   <li>零拷贝如何减少数据拷贝次数？</li>
 *   <li>零拷贝的性能提升有多少？</li>
 *   <li>什么场景适合使用零拷贝？</li>
 * </ul>
 *
 * <p><strong>传统 I/O 数据流程</strong>（4 次拷贝 + 4 次上下文切换）：
 * <pre>
 * ┌─────────────────────────────────────────────────────────────────┐
 * │                    传统 I/O 文件传输                              │
 * └─────────────────────────────────────────────────────────────────┘
 *
 * 1. read(file, buffer)          2. write(socket, buffer)
 *    ┌──────────┐                   ┌──────────┐
 *    │   磁盘   │                   │   网卡   │
 *    └─────┬────┘                   └────▲─────┘
 *          │                             │
 *          │ ① DMA 拷贝                   │ ④ DMA 拷贝
 *          ▼                             │
 *    ┌──────────┐                   ┌────┴─────┐
 *    │内核缓冲区│                   │Socket缓冲│
 *    └─────┬────┘                   └────▲─────┘
 *          │                             │
 *          │ ② CPU 拷贝                   │ ③ CPU 拷贝
 *          ▼                             │
 *    ┌──────────┐                   ┌────┴─────┐
 *    │用户缓冲区│──────────────────→│用户缓冲区│
 *    └──────────┘     应用程序内存    └──────────┘
 *
 * 数据拷贝: ① DMA 拷贝 + ② CPU 拷贝 + ③ CPU 拷贝 + ④ DMA 拷贝 = 4 次
 * 上下文切换: 用户态 → 内核态 → 用户态 → 内核态 → 用户态 = 4 次
 * </pre>
 *
 * <p><strong>零拷贝数据流程</strong>（2-3 次拷贝 + 2 次上下文切换）：
 * <pre>
 * ┌─────────────────────────────────────────────────────────────────┐
 * │              零拷贝（sendfile/transferTo）                        │
 * └─────────────────────────────────────────────────────────────────┘
 *
 * transferTo(fileChannel, socketChannel)
 *    ┌──────────┐                   ┌──────────┐
 *    │   磁盘   │                   │   网卡   │
 *    └─────┬────┘                   └────▲─────┘
 *          │                             │
 *          │ ① DMA 拷贝                   │ ③ DMA 拷贝
 *          ▼                             │
 *    ┌──────────┐                   ┌────┴─────┐
 *    │内核缓冲区│──────────────────→│Socket缓冲│
 *    └──────────┘  ② CPU 拷贝/指针   └──────────┘
 *                  （部分硬件支持跳过）
 *
 * 内核空间操作（无需用户空间参与）
 *
 * 数据拷贝: ① DMA 拷贝 + ② CPU 拷贝（可选） + ③ DMA 拷贝 = 2-3 次
 * 上下文切换: 用户态 → 内核态 → 用户态 = 2 次
 *
 * ✅ 优势:
 *   - 减少 2 次 CPU 拷贝（50% 减少）
 *   - 减少 2 次上下文切换（50% 减少）
 *   - 不占用 CPU 资源（DMA 直接传输）
 *   - 减少内存占用（无需用户空间缓冲区）
 * </pre>
 *
 * <p><strong>应用场景</strong>：
 * <ul>
 *   <li><strong>适合</strong>: 大文件传输（视频、日志、备份）、静态文件服务器、代理服务器</li>
 *   <li><strong>不适合</strong>: 需要处理数据内容（加密、压缩、格式转换）</li>
 * </ul>
 *
 * @author AI Assistant
 * @since 1.0.0
 */
public class ZeroCopyDemo {

    private static final Logger log = LoggerFactory.getLogger(ZeroCopyDemo.class);

    /**
     * 默认端口
     */
    private static final int DEFAULT_PORT = 8888;

    /**
     * 测试文件大小（100MB）
     */
    private static final long TEST_FILE_SIZE = 100 * 1024 * 1024; // 100MB

    /**
     * 传统 I/O 缓冲区大小（8KB）
     */
    private static final int BUFFER_SIZE = 8192;

    // ==================== 服务器端 ====================

    /**
     * 传统 I/O 文件服务器（使用 BIO + read/write）
     *
     * <p><strong>工作流程</strong>：
     * <pre>
     * 1. 接受客户端连接
     * 2. 读取文件内容到用户空间缓冲区（byte[]）
     * 3. 将缓冲区内容写入 Socket
     * 4. 重复步骤 2-3，直到文件传输完毕
     * </pre>
     *
     * <p><strong>性能特征</strong>：
     * <ul>
     *   <li>数据拷贝: 4 次（磁盘 → 内核 → 用户 → 内核 → 网卡）</li>
     *   <li>上下文切换: 4 次（用户态 ↔ 内核态）</li>
     *   <li>CPU 占用: 高（参与数据拷贝）</li>
     *   <li>内存占用: 高（用户空间缓冲区）</li>
     * </ul>
     *
     * @param port     服务器端口
     * @param filePath 要传输的文件路径
     * @throws IOException 如果 I/O 错误发生
     */
    public static void startTraditionalServer(int port, String filePath) throws IOException {
        log.info("========================================");
        log.info("启动传统 I/O 文件服务器 - 端口: {}", port);
        log.info("========================================");
        log.info("文件: {} | 大小: {} MB", filePath, Files.size(Paths.get(filePath)) / 1024 / 1024);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            log.info("✅ 服务器启动成功，等待客户端连接...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                log.info("📥 接受新连接: {}", clientSocket.getRemoteSocketAddress());

                // 记录开始时间
                long startTime = System.currentTimeMillis();

                // 传输文件（传统 I/O）
                try (FileInputStream fis = new FileInputStream(filePath);
                     OutputStream os = clientSocket.getOutputStream()) {

                    byte[] buffer = new byte[BUFFER_SIZE]; // 用户空间缓冲区（8KB）
                    int bytesRead;
                    long totalBytes = 0;

                    // ⚠️ 传统 I/O: read() + write() 循环
                    // 每次循环涉及 4 次数据拷贝
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        os.write(buffer, 0, bytesRead);
                        totalBytes += bytesRead;
                    }

                    // 记录结束时间
                    long endTime = System.currentTimeMillis();
                    long duration = endTime - startTime;

                    log.info("✅ 文件传输完成 | 大小: {} MB | 耗时: {} ms | 速度: {} MB/s",
                        totalBytes / 1024 / 1024,
                        duration,
                        totalBytes / 1024 / 1024 / (duration / 1000.0));

                } finally {
                    clientSocket.close();
                }
            }
        }
    }

    /**
     * 零拷贝文件服务器（使用 NIO + FileChannel.transferTo）
     *
     * <p><strong>工作流程</strong>：
     * <pre>
     * 1. 接受客户端连接
     * 2. 调用 fileChannel.transferTo(socketChannel)
     * 3. 操作系统在内核空间完成数据传输（无需用户空间参与）
     * </pre>
     *
     * <p><strong>性能特征</strong>：
     * <ul>
     *   <li>数据拷贝: 2-3 次（磁盘 → 内核 → Socket 缓冲 → 网卡）</li>
     *   <li>上下文切换: 2 次（用户态 → 内核态 → 用户态）</li>
     *   <li>CPU 占用: 低（仅上下文切换）</li>
     *   <li>内存占用: 低（无需用户空间缓冲区）</li>
     * </ul>
     *
     * <p><strong>⚠️ 底层实现</strong>：
     * <ul>
     *   <li>Linux: sendfile() 系统调用</li>
     *   <li>Windows: TransmitFile() 系统调用</li>
     *   <li>部分硬件支持跳过 CPU 拷贝（DMA Gather）</li>
     * </ul>
     *
     * @param port     服务器端口
     * @param filePath 要传输的文件路径
     * @throws IOException 如果 I/O 错误发生
     */
    public static void startZeroCopyServer(int port, String filePath) throws IOException {
        log.info("========================================");
        log.info("启动零拷贝文件服务器 - 端口: {}", port);
        log.info("========================================");
        log.info("文件: {} | 大小: {} MB", filePath, Files.size(Paths.get(filePath)) / 1024 / 1024);

        try (ServerSocketChannel serverChannel = ServerSocketChannel.open()) {
            serverChannel.bind(new InetSocketAddress(port));
            log.info("✅ 服务器启动成功，等待客户端连接...");

            while (true) {
                SocketChannel clientChannel = serverChannel.accept();
                log.info("📥 接受新连接: {}", clientChannel.getRemoteAddress());

                // 记录开始时间
                long startTime = System.currentTimeMillis();

                // 零拷贝传输文件
                try (FileChannel fileChannel = FileChannel.open(Paths.get(filePath), StandardOpenOption.READ)) {

                    long fileSize = fileChannel.size();
                    long position = 0;
                    long transferred = 0;

                    // ⚠️ 零拷贝: transferTo() 一次调用完成
                    // 底层使用 sendfile() 系统调用，在内核空间完成数据传输
                    while (position < fileSize) {
                        transferred = fileChannel.transferTo(position, fileSize - position, clientChannel);
                        position += transferred;
                    }

                    // 记录结束时间
                    long endTime = System.currentTimeMillis();
                    long duration = endTime - startTime;

                    log.info("✅ 文件传输完成 | 大小: {} MB | 耗时: {} ms | 速度: {} MB/s",
                        fileSize / 1024 / 1024,
                        duration,
                        fileSize / 1024 / 1024 / (duration / 1000.0));

                } finally {
                    clientChannel.close();
                }
            }
        }
    }

    // ==================== 客户端 ====================

    /**
     * 文件接收客户端（用于性能测试）
     *
     * <p><strong>功能</strong>：
     * <ul>
     *   <li>连接到文件服务器</li>
     *   <li>接收文件内容</li>
     *   <li>统计传输速度</li>
     * </ul>
     *
     * @param host 服务器地址
     * @param port 服务器端口
     * @throws IOException 如果 I/O 错误发生
     */
    public static void receiveFile(String host, int port) throws IOException {
        log.info("========================================");
        log.info("连接文件服务器: {}:{}", host, port);
        log.info("========================================");

        try (Socket socket = new Socket(host, port);
             InputStream is = socket.getInputStream()) {

            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            long totalBytes = 0;
            long startTime = System.currentTimeMillis();

            // 接收文件内容（丢弃数据，仅测试速度）
            while ((bytesRead = is.read(buffer)) != -1) {
                totalBytes += bytesRead;
            }

            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            log.info("✅ 文件接收完成 | 大小: {} MB | 耗时: {} ms | 速度: {} MB/s",
                totalBytes / 1024 / 1024,
                duration,
                totalBytes / 1024 / 1024 / (duration / 1000.0));
        }
    }

    // ==================== 测试工具 ====================

    /**
     * 创建测试文件
     *
     * <p><strong>功能</strong>：
     * <ul>
     *   <li>生成指定大小的测试文件</li>
     *   <li>填充随机数据</li>
     * </ul>
     *
     * @param filePath 文件路径
     * @param fileSize 文件大小（字节）
     * @throws IOException 如果 I/O 错误发生
     */
    public static void createTestFile(String filePath, long fileSize) throws IOException {
        log.info("创建测试文件: {} | 大小: {} MB", filePath, fileSize / 1024 / 1024);

        Path path = Paths.get(filePath);

        // 删除旧文件
        Files.deleteIfExists(path);

        // 创建新文件并填充数据
        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            long written = 0;

            while (written < fileSize) {
                int toWrite = (int) Math.min(BUFFER_SIZE, fileSize - written);
                fos.write(buffer, 0, toWrite);
                written += toWrite;
            }
        }

        log.info("✅ 测试文件创建成功: {} MB", Files.size(path) / 1024 / 1024);
    }

    /**
     * 对比测试（传统 I/O vs 零拷贝）
     *
     * <p><strong>测试步骤</strong>：
     * <pre>
     * 1. 创建测试文件（100MB）
     * 2. 启动传统 I/O 服务器，测试传输速度
     * 3. 启动零拷贝服务器，测试传输速度
     * 4. 对比性能差异
     * </pre>
     *
     * @throws Exception 如果错误发生
     */
    public static void runBenchmark() throws Exception {
        String testFile = "test-file.dat";

        // 创建测试文件（100MB）
        createTestFile(testFile, TEST_FILE_SIZE);

        log.info("========================================");
        log.info("性能对比测试");
        log.info("========================================");

        // 测试传统 I/O
        log.info("1. 传统 I/O 测试...");
        Thread serverThread1 = new Thread(() -> {
            try {
                startTraditionalServer(DEFAULT_PORT, testFile);
            } catch (IOException e) {
                log.error("传统 I/O 服务器异常: {}", e.getMessage());
            }
        });
        serverThread1.start();

        Thread.sleep(1000); // 等待服务器启动
        receiveFile("localhost", DEFAULT_PORT);

        // 停止服务器（实际应用需要优雅关闭）
        serverThread1.interrupt();

        log.info("");
        log.info("2. 零拷贝测试...");
        Thread serverThread2 = new Thread(() -> {
            try {
                startZeroCopyServer(DEFAULT_PORT + 1, testFile);
            } catch (IOException e) {
                log.error("零拷贝服务器异常: {}", e.getMessage());
            }
        });
        serverThread2.start();

        Thread.sleep(1000); // 等待服务器启动
        receiveFile("localhost", DEFAULT_PORT + 1);

        // 停止服务器
        serverThread2.interrupt();

        log.info("========================================");
        log.info("性能对比测试完成");
        log.info("========================================");
        log.info("预期结果:");
        log.info("- 零拷贝速度 ≈ 传统 I/O 速度的 2-3 倍");
        log.info("- 零拷贝 CPU 占用更低");
        log.info("- 零拷贝内存占用更低");
    }

    // ==================== 主方法（演示入口）====================

    /**
     * 演示入口
     *
     * <p><strong>使用方式</strong>：
     * <pre>
     * # 创建测试文件
     * mvn exec:java -Dexec.mainClass="nan.tech.lab06.zerocopy.ZeroCopyDemo" \
     *   -Dexec.args="create test-file.dat"
     *
     * # 启动传统 I/O 服务器
     * mvn exec:java -Dexec.mainClass="nan.tech.lab06.zerocopy.ZeroCopyDemo" \
     *   -Dexec.args="traditional 8888 test-file.dat"
     *
     * # 启动零拷贝服务器
     * mvn exec:java -Dexec.mainClass="nan.tech.lab06.zerocopy.ZeroCopyDemo" \
     *   -Dexec.args="zerocopy 9999 test-file.dat"
     *
     * # 客户端接收文件
     * mvn exec:java -Dexec.mainClass="nan.tech.lab06.zerocopy.ZeroCopyDemo" \
     *   -Dexec.args="client localhost 8888"
     *
     * # 运行性能对比测试
     * mvn exec:java -Dexec.mainClass="nan.tech.lab06.zerocopy.ZeroCopyDemo" \
     *   -Dexec.args="benchmark"
     * </pre>
     *
     * <p><strong>性能对比示例</strong>：
     * <pre>
     * 传统 I/O:
     *   - 传输 100MB 文件: 500ms
     *   - 速度: ~200 MB/s
     *   - CPU 占用: 80%
     *
     * 零拷贝:
     *   - 传输 100MB 文件: 200ms
     *   - 速度: ~500 MB/s
     *   - CPU 占用: 20%
     *
     * 性能提升: 2.5 倍
     * </pre>
     *
     * @param args 命令行参数
     * @throws Exception 如果错误发生
     */
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("用法:");
            System.err.println("  创建测试文件:     java ZeroCopyDemo create <文件名>");
            System.err.println("  传统 I/O 服务器:  java ZeroCopyDemo traditional <端口> <文件名>");
            System.err.println("  零拷贝服务器:     java ZeroCopyDemo zerocopy <端口> <文件名>");
            System.err.println("  客户端:          java ZeroCopyDemo client <主机> <端口>");
            System.err.println("  性能对比测试:     java ZeroCopyDemo benchmark");
            System.exit(1);
        }

        String mode = args[0];

        switch (mode.toLowerCase()) {
            case "create":
                String fileName = args.length > 1 ? args[1] : "test-file.dat";
                createTestFile(fileName, TEST_FILE_SIZE);
                break;

            case "traditional":
                int port1 = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_PORT;
                String file1 = args.length > 2 ? args[2] : "test-file.dat";
                startTraditionalServer(port1, file1);
                break;

            case "zerocopy":
                int port2 = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_PORT;
                String file2 = args.length > 2 ? args[2] : "test-file.dat";
                startZeroCopyServer(port2, file2);
                break;

            case "client":
                String host = args.length > 1 ? args[1] : "localhost";
                int port3 = args.length > 2 ? Integer.parseInt(args[2]) : DEFAULT_PORT;
                receiveFile(host, port3);
                break;

            case "benchmark":
                runBenchmark();
                break;

            default:
                System.err.println("未知模式: " + mode);
                System.err.println("支持的模式: create | traditional | zerocopy | client | benchmark");
                System.exit(1);
        }
    }
}
