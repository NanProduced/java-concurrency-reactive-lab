package nan.tech.lab06.bio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * BIO Echo Server 测试客户端
 *
 * <p><strong>功能</strong>：
 * <ul>
 *   <li>单客户端模式：发送消息并验证响应</li>
 *   <li>并发测试模式：模拟多个并发客户端</li>
 *   <li>性能压测模式：测试 TPS 和延迟</li>
 * </ul>
 *
 * <p><strong>使用场景</strong>：
 * <ul>
 *   <li>验证单线程 BIO Server 的串行处理问题</li>
 *   <li>验证多线程 BIO Server 的并发能力</li>
 *   <li>对比不同模式的性能差异</li>
 * </ul>
 *
 * @author AI Assistant
 * @since 1.0.0
 */
public class BIOEchoClient {

    private static final Logger log = LoggerFactory.getLogger(BIOEchoClient.class);

    /**
     * 默认服务器地址
     */
    private static final String DEFAULT_HOST = "localhost";

    /**
     * 默认服务器端口
     */
    private static final int DEFAULT_PORT = 8080;

    /**
     * 成功请求计数器
     */
    private static final AtomicInteger successCount = new AtomicInteger(0);

    /**
     * 失败请求计数器
     */
    private static final AtomicInteger failureCount = new AtomicInteger(0);

    // ==================== 单客户端模式 ====================

    /**
     * 单客户端交互式模式
     *
     * <p><strong>功能</strong>：
     * <ul>
     *   <li>连接到服务器</li>
     *   <li>发送指定数量的消息</li>
     *   <li>验证响应</li>
     * </ul>
     *
     * @param host         服务器地址
     * @param port         服务器端口
     * @param messageCount 发送消息数量
     * @throws IOException 如果 I/O 错误发生
     */
    public static void runSingleClient(String host, int port, int messageCount) throws IOException {
        log.info("========================================");
        log.info("单客户端模式 - 连接: {}:{}", host, port);
        log.info("========================================");

        try (Socket socket = new Socket(host, port);
             InputStream inputStream = socket.getInputStream();
             OutputStream outputStream = socket.getOutputStream();
             BufferedReader in = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))) {

            log.info("✅ 连接成功");

            for (int i = 1; i <= messageCount; i++) {
                String message = "Message-" + i;

                // 发送消息
                out.write(message + "\n");
                out.flush();
                log.debug("→ 发送: {}", message);

                // 接收响应
                String response = in.readLine();
                log.debug("← 接收: {}", response);

                // 验证响应
                String expected = "ECHO: " + message;
                if (expected.equals(response)) {
                    successCount.incrementAndGet();
                } else {
                    log.error("❌ 响应不匹配 | 期望: {} | 实际: {}", expected, response);
                    failureCount.incrementAndGet();
                }
            }

            // 发送关闭命令
            out.write("QUIT\n");
            out.flush();

            log.info("✅ 测试完成 | 成功: {} | 失败: {}", successCount.get(), failureCount.get());
        }
    }

    // ==================== 并发测试模式 ====================

    /**
     * 并发客户端测试模式
     *
     * <p><strong>功能</strong>：
     * <ul>
     *   <li>启动多个并发客户端</li>
     *   <li>每个客户端发送指定数量的消息</li>
     *   <li>统计成功/失败率和平均延迟</li>
     * </ul>
     *
     * <p><strong>测试目的</strong>：
     * <ul>
     *   <li>验证单线程 Server 在并发情况下的表现（串行处理）</li>
     *   <li>验证多线程 Server 的并发能力</li>
     * </ul>
     *
     * @param host          服务器地址
     * @param port          服务器端口
     * @param clientCount   并发客户端数量
     * @param messagesPerClient 每个客户端发送的消息数
     * @throws InterruptedException 如果等待被中断
     */
    public static void runConcurrentClients(String host, int port, int clientCount, int messagesPerClient)
        throws InterruptedException {

        log.info("========================================");
        log.info("并发测试模式 - 连接: {}:{}", host, port);
        log.info("并发客户端数: {} | 每客户端消息数: {}", clientCount, messagesPerClient);
        log.info("========================================");

        ExecutorService executor = Executors.newFixedThreadPool(clientCount);
        CountDownLatch latch = new CountDownLatch(clientCount);

        long startTime = System.currentTimeMillis();

        // 启动并发客户端
        for (int i = 0; i < clientCount; i++) {
            final int clientId = i + 1;

            executor.submit(() -> {
                try {
                    runClientSession(host, port, clientId, messagesPerClient);
                } catch (Exception e) {
                    log.error("客户端 {} 异常: {}", clientId, e.getMessage());
                    failureCount.addAndGet(messagesPerClient);
                } finally {
                    latch.countDown();
                }
            });
        }

        // 等待所有客户端完成
        latch.await();
        long endTime = System.currentTimeMillis();

        // 统计结果
        long totalTime = endTime - startTime;
        int totalRequests = clientCount * messagesPerClient;
        int totalSuccess = successCount.get();
        int totalFailure = failureCount.get();

        log.info("========================================");
        log.info("测试结果");
        log.info("========================================");
        log.info("总耗时: {} ms", totalTime);
        log.info("总请求数: {}", totalRequests);
        log.info("成功: {} ({} %)", totalSuccess, totalSuccess * 100 / totalRequests);
        log.info("失败: {} ({} %)", totalFailure, totalFailure * 100 / totalRequests);
        log.info("TPS (Transaction Per Second): {} req/s", totalRequests * 1000 / totalTime);
        log.info("平均延迟: {} ms/req", totalTime / totalRequests);
        log.info("========================================");

        // 关闭线程池
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
    }

    /**
     * 单个客户端会话
     *
     * @param host              服务器地址
     * @param port              服务器端口
     * @param clientId          客户端 ID
     * @param messagesPerClient 发送消息数量
     * @throws IOException 如果 I/O 错误发生
     */
    private static void runClientSession(String host, int port, int clientId, int messagesPerClient)
        throws IOException {

        log.debug("[客户端 {}] 开始连接...", clientId);

        try (Socket socket = new Socket(host, port);
             InputStream inputStream = socket.getInputStream();
             OutputStream outputStream = socket.getOutputStream();
             BufferedReader in = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))) {

            log.debug("[客户端 {}] 连接成功", clientId);

            for (int i = 1; i <= messagesPerClient; i++) {
                String message = "Client-" + clientId + "-Message-" + i;

                // 发送消息
                out.write(message + "\n");
                out.flush();

                // 接收响应
                String response = in.readLine();

                // 验证响应
                String expected = "ECHO: " + message;
                if (expected.equals(response)) {
                    successCount.incrementAndGet();
                } else {
                    log.error("[客户端 {}] ❌ 响应不匹配 | 期望: {} | 实际: {}",
                        clientId, expected, response);
                    failureCount.incrementAndGet();
                }
            }

            // 发送关闭命令
            out.write("QUIT\n");
            out.flush();

            log.debug("[客户端 {}] 完成", clientId);
        }
    }

    // ==================== 主方法（演示入口）====================

    /**
     * 演示入口
     *
     * <p><strong>使用方式</strong>：
     * <pre>
     * # 单客户端模式（发送 10 条消息）
     * mvn exec:java -Dexec.mainClass="nan.tech.lab06.bio.BIOEchoClient" -Dexec.args="single 10"
     *
     * # 并发测试模式（10 个客户端，每个发送 20 条消息）
     * mvn exec:java -Dexec.mainClass="nan.tech.lab06.bio.BIOEchoClient" -Dexec.args="concurrent 10 20"
     *
     * # 压力测试（100 个客户端，每个发送 100 条消息）
     * mvn exec:java -Dexec.mainClass="nan.tech.lab06.bio.BIOEchoClient" -Dexec.args="concurrent 100 100"
     * </pre>
     *
     * <p><strong>对比测试</strong>：
     * <pre>
     * # 1. 启动单线程服务器
     * mvn exec:java -Dexec.mainClass="nan.tech.lab06.bio.BIOEchoServer" -Dexec.args="single"
     *
     * # 2. 测试并发（观察串行处理）
     * mvn exec:java -Dexec.mainClass="nan.tech.lab06.bio.BIOEchoClient" -Dexec.args="concurrent 10 10"
     *
     * # 3. 启动多线程服务器
     * mvn exec:java -Dexec.mainClass="nan.tech.lab06.bio.BIOEchoServer" -Dexec.args="multi"
     *
     * # 4. 再次测试并发（观察并发处理）
     * mvn exec:java -Dexec.mainClass="nan.tech.lab06.bio.BIOEchoClient" -Dexec.args="concurrent 10 10"
     * </pre>
     *
     * @param args 命令行参数 [模式] [参数...]
     *             单客户端: single [消息数]
     *             并发测试: concurrent [客户端数] [每客户端消息数]
     * @throws Exception 如果错误发生
     */
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("用法:");
            System.err.println("  单客户端:   java BIOEchoClient single [消息数]");
            System.err.println("  并发测试:   java BIOEchoClient concurrent [客户端数] [每客户端消息数]");
            System.exit(1);
        }

        String mode = args[0];
        String host = DEFAULT_HOST;
        int port = DEFAULT_PORT;

        switch (mode.toLowerCase()) {
            case "single":
                int messageCount = args.length > 1 ? Integer.parseInt(args[1]) : 10;
                runSingleClient(host, port, messageCount);
                break;

            case "concurrent":
                int clientCount = args.length > 1 ? Integer.parseInt(args[1]) : 10;
                int messagesPerClient = args.length > 2 ? Integer.parseInt(args[2]) : 10;
                runConcurrentClients(host, port, clientCount, messagesPerClient);
                break;

            default:
                System.err.println("未知模式: " + mode);
                System.err.println("支持的模式: single | concurrent");
                System.exit(1);
        }
    }
}
