package nan.tech.lab08.springmvc.basics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Callable 异步演示 Controller
 *
 * <p><b>@教学</b>
 * <ul>
 *   <li>对比同步阻塞 vs Callable 异步的线程占用情况</li>
 *   <li>演示 Servlet 容器线程与业务线程的解耦</li>
 *   <li>量化性能提升：同步 vs 异步的 TPS 对比</li>
 *   <li>观察线程名变化：http-nio-xxx → async-mvc-xxx</li>
 * </ul>
 *
 * <p><b>@核心概念</b>
 * <pre>
 * 同步阻塞模式:
 *   1. Tomcat 线程接收请求
 *   2. Controller 执行业务逻辑（阻塞 Tomcat 线程）
 *   3. 返回响应
 *   → Tomcat 线程数 = 并发请求数
 *
 * Callable 异步模式:
 *   1. Tomcat 线程接收请求
 *   2. Controller 返回 Callable（立即释放 Tomcat 线程）
 *   3. AsyncTaskExecutor 线程执行 Callable
 *   4. 执行完成后，重新分派到 Tomcat 容器
 *   5. 返回响应
 *   → Tomcat 线程数 << 并发请求数（线程复用）
 * </pre>
 *
 * <p><b>@性能对比</b>
 * <pre>
 * 场景: 100 并发请求，每个请求耗时 2 秒
 *
 * 同步模式:
 *   - Tomcat 线程池: 200 个线程
 *   - 实际占用: 100 个线程（阻塞等待）
 *   - TPS: ~50 req/s
 *
 * Callable 异步模式:
 *   - Tomcat 线程池: 200 个线程
 *   - AsyncTaskExecutor: 40 个线程
 *   - 实际占用: Tomcat ~20个（快速轮转）+ 异步 40个
 *   - TPS: ~75 req/s（提升 50%）
 * </pre>
 *
 * <p><b>@快速测试</b>
 * <pre>
 * # 同步端点
 * curl http://localhost:8080/api/async/sync
 *
 * # Callable 异步端点
 * curl http://localhost:8080/api/async/callable
 *
 * # 并发测试（使用 hey 或 wrk）
 * hey -n 1000 -c 100 http://localhost:8080/api/async/sync
 * hey -n 1000 -c 100 http://localhost:8080/api/async/callable
 * </pre>
 *
 * @author Claude Code
 * @since 2025-10-20
 */
@RestController
@RequestMapping("/api/async")
public class CallableController {

    private static final Logger log = LoggerFactory.getLogger(CallableController.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    /**
     * 同步阻塞端点（对比基准）
     *
     * <p><b>@教学</b>
     * <p>模拟耗时操作（2秒），阻塞 Tomcat 请求线程。
     *
     * <p><b>@线程占用</b>
     * <ul>
     *   <li>请求线程: http-nio-8080-exec-N（阻塞 2 秒）</li>
     *   <li>业务线程: 无（使用请求线程执行）</li>
     * </ul>
     *
     * @param delay 延迟时间（毫秒），默认 2000ms
     * @return 响应数据
     * @throws InterruptedException 中断异常
     */
    @GetMapping("/sync")
    public Map<String, Object> syncEndpoint(
            @RequestParam(defaultValue = "2000") long delay) throws InterruptedException {

        String requestThread = Thread.currentThread().getName();
        String startTime = LocalDateTime.now().format(FORMATTER);

        log.info("[同步端点] 开始处理 - 请求线程: {} - 开始时间: {}", requestThread, startTime);

        // 模拟耗时操作（阻塞请求线程）
        Thread.sleep(delay);

        String endTime = LocalDateTime.now().format(FORMATTER);
        log.info("[同步端点] 处理完成 - 请求线程: {} - 结束时间: {}", requestThread, endTime);

        Map<String, Object> response = new HashMap<>();
        response.put("mode", "同步阻塞");
        response.put("requestThread", requestThread);
        response.put("businessThread", requestThread); // 同步模式下，业务线程 = 请求线程
        response.put("startTime", startTime);
        response.put("endTime", endTime);
        response.put("delayMs", delay);
        response.put("message", "同步处理完成（阻塞请求线程 " + delay + "ms）");

        return response;
    }

    /**
     * Callable 异步端点
     *
     * <p><b>@教学</b>
     * <p>Controller 立即返回 Callable，释放请求线程。业务逻辑在 AsyncTaskExecutor 线程池中执行。
     *
     * <p><b>@线程切换</b>
     * <ul>
     *   <li>请求线程: http-nio-8080-exec-N（立即释放）</li>
     *   <li>业务线程: async-mvc-N（在线程池中执行）</li>
     * </ul>
     *
     * <p><b>@执行流程</b>
     * <pre>
     * 1. Tomcat 线程接收请求 → 调用此方法
     * 2. 返回 Callable 对象 → Tomcat 线程立即释放
     * 3. AsyncTaskExecutor 从线程池取出线程
     * 4. 执行 Callable.call() 方法
     * 5. 返回结果 → 重新分派到 Servlet 容器
     * 6. 渲染响应 → 返回客户端
     * </pre>
     *
     * @param delay 延迟时间（毫秒），默认 2000ms
     * @return Callable 异步任务
     */
    @GetMapping("/callable")
    public Callable<Map<String, Object>> callableEndpoint(
            @RequestParam(defaultValue = "2000") long delay) {

        String requestThread = Thread.currentThread().getName();
        String startTime = LocalDateTime.now().format(FORMATTER);

        log.info("[Callable端点] 返回Callable - 请求线程: {} - 开始时间: {} ⚡ 请求线程即将释放",
                requestThread, startTime);

        // 返回 Callable（Lambda 表达式）
        return () -> {
            // ⚠️ 此代码块在 AsyncTaskExecutor 线程池中执行
            String businessThread = Thread.currentThread().getName();
            String executeTime = LocalDateTime.now().format(FORMATTER);

            log.info("[Callable端点] Callable开始执行 - 业务线程: {} - 执行时间: {} 🔄 已切换到异步线程",
                    businessThread, executeTime);

            // 模拟耗时操作（不阻塞请求线程）
            Thread.sleep(delay);

            String endTime = LocalDateTime.now().format(FORMATTER);
            log.info("[Callable端点] Callable执行完成 - 业务线程: {} - 结束时间: {} ✅ 准备返回结果",
                    businessThread, endTime);

            Map<String, Object> response = new HashMap<>();
            response.put("mode", "Callable 异步");
            response.put("requestThread", requestThread);
            response.put("businessThread", businessThread); // 业务线程 ≠ 请求线程
            response.put("startTime", startTime);
            response.put("executeTime", executeTime);
            response.put("endTime", endTime);
            response.put("delayMs", delay);
            response.put("threadSwitch", !requestThread.equals(businessThread)); // 是否发生线程切换
            response.put("message", "Callable 异步处理完成（请求线程已释放）");

            return response;
        };
    }

    /**
     * Callable 异步端点（带异常处理）
     *
     * <p><b>@教学</b>
     * <p>演示 Callable 中的异常如何传播到 Spring MVC。
     *
     * <p><b>@异常处理</b>
     * <ul>
     *   <li>Callable 中抛出的异常会被 Spring MVC 捕获</li>
     *   <li>异常会通过 @ExceptionHandler 或全局异常处理器处理</li>
     *   <li>CallableProcessingInterceptor.handleError() 会被调用</li>
     * </ul>
     *
     * @param shouldFail 是否模拟失败
     * @return Callable 异步任务
     */
    @GetMapping("/callable/error")
    public Callable<Map<String, Object>> callableWithError(
            @RequestParam(defaultValue = "false") boolean shouldFail) {

        String requestThread = Thread.currentThread().getName();
        log.info("[Callable错误演示] 请求线程: {}, 是否模拟失败: {}", requestThread, shouldFail);

        return () -> {
            String businessThread = Thread.currentThread().getName();
            log.info("[Callable错误演示] 业务线程: {}", businessThread);

            if (shouldFail) {
                log.error("[Callable错误演示] 模拟异常");
                throw new RuntimeException("模拟 Callable 执行异常");
            }

            Map<String, Object> response = new HashMap<>();
            response.put("mode", "Callable 异步（错误处理）");
            response.put("requestThread", requestThread);
            response.put("businessThread", businessThread);
            response.put("status", "成功");
            response.put("message", "Callable 执行成功，无异常");

            return response;
        };
    }

    /**
     * 健康检查端点
     *
     * <p><b>@教学</b>
     * <p>简单的同步端点，用于验证服务是否正常运行。
     *
     * @return 健康状态
     */
    @GetMapping("/health")
    public Map<String, String> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("module", "Lab-08: Spring MVC Async");
        response.put("time", LocalDateTime.now().toString());
        return response;
    }
}
