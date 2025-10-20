package nan.tech.lab08.springmvc.basics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.WebAsyncTask;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * WebAsyncTask 异步演示 Controller
 *
 * <p><b>@教学</b>
 * <ul>
 *   <li>演示 WebAsyncTask 的自定义超时配置</li>
 *   <li>演示如何为不同任务指定不同的线程池</li>
 *   <li>对比 Callable vs WebAsyncTask 的差异</li>
 *   <li>演示超时处理的最佳实践</li>
 * </ul>
 *
 * <p><b>@核心概念</b>
 * <pre>
 * Callable 的局限性:
 *   - 超时时间: 使用全局配置 (spring.mvc.async.request-timeout)
 *   - 线程池: 使用默认的 AsyncTaskExecutor
 *   - 超时处理: 使用全局拦截器
 *   → 所有 Callable 任务共享相同配置
 *
 * WebAsyncTask 的优势:
 *   - 超时时间: 可以为每个任务单独配置
 *   - 线程池: 可以为每个任务指定特定的线程池
 *   - 超时处理: 可以为每个任务定制超时回调
 *   → 提供更细粒度的控制
 * </pre>
 *
 * <p><b>@适用场景</b>
 * <pre>
 * 使用 WebAsyncTask 的场景:
 *   - 不同任务需要不同的超时时间（例如：快速查询 5秒，报表生成 60秒）
 *   - 不同任务需要不同的线程池（例如：CPU密集型 vs IO密集型）
 *   - 需要自定义超时响应（例如：返回缓存数据而不是 503 错误）
 *   - 需要任务级别的监控和日志
 * </pre>
 *
 * <p><b>@快速测试</b>
 * <pre>
 * # 测试1: 正常完成（2秒任务，5秒超时）
 * curl "http://localhost:8080/api/async/web-async-task?delay=2000"
 *
 * # 测试2: 超时（8秒任务，5秒超时）
 * curl "http://localhost:8080/api/async/web-async-task?delay=8000"
 *
 * # 测试3: 自定义超时时间（8秒任务，10秒超时）
 * curl "http://localhost:8080/api/async/web-async-task/custom-timeout?delay=8000"
 *
 * # 测试4: 使用特定线程池（CPU密集型任务）
 * curl "http://localhost:8080/api/async/web-async-task/custom-executor?taskType=cpu"
 * </pre>
 *
 * @author Claude Code
 * @since 2025-10-20
 */
@RestController
@RequestMapping("/api/async")
public class WebAsyncTaskController {

    private static final Logger log = LoggerFactory.getLogger(WebAsyncTaskController.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final AsyncTaskExecutor asyncTaskExecutor;

    /**
     * 构造函数注入（推荐方式）
     *
     * <p><b>@教学</b>
     * <p>使用 @Qualifier 指定注入哪个 Bean（如果有多个同类型的 Bean）。
     *
     * @param asyncTaskExecutor 异步任务执行器
     */
    public WebAsyncTaskController(@Qualifier("asyncTaskExecutor") AsyncTaskExecutor asyncTaskExecutor) {
        this.asyncTaskExecutor = asyncTaskExecutor;
    }

    /**
     * WebAsyncTask 基础演示（使用默认超时）
     *
     * <p><b>@教学</b>
     * <p>WebAsyncTask 是对 Callable 的包装，提供更多配置选项。
     *
     * <p><b>@执行流程</b>
     * <pre>
     * 1. Tomcat 线程接收请求 → 调用此方法
     * 2. 创建 Callable 任务
     * 3. 包装为 WebAsyncTask(5秒超时)
     * 4. 返回 WebAsyncTask → Tomcat 线程立即释放
     * 5. AsyncTaskExecutor 执行 Callable
     * 6. 如果 5 秒内完成 → 返回结果
     * 7. 如果超过 5 秒 → 调用超时回调 → 返回超时响应
     * </pre>
     *
     * @param delay 延迟时间（毫秒），默认 2000ms
     * @return WebAsyncTask 异步任务
     */
    @GetMapping("/web-async-task")
    public WebAsyncTask<Map<String, Object>> webAsyncTaskEndpoint(
            @RequestParam(defaultValue = "2000") long delay) {

        String requestThread = Thread.currentThread().getName();
        String startTime = LocalDateTime.now().format(FORMATTER);

        log.info("[WebAsyncTask端点] 创建WebAsyncTask - 请求线程: {} - 开始时间: {} - 延迟: {}ms ⚡ 超时时间: 5秒",
                requestThread, startTime, delay);

        // 创建 Callable 任务
        Callable<Map<String, Object>> callable = () -> {
            String businessThread = Thread.currentThread().getName();
            String executeTime = LocalDateTime.now().format(FORMATTER);

            log.info("[WebAsyncTask端点] Callable开始执行 - 业务线程: {} - 执行时间: {} 🔄 延迟: {}ms",
                    businessThread, executeTime, delay);

            // 模拟耗时操作
            Thread.sleep(delay);

            String endTime = LocalDateTime.now().format(FORMATTER);
            log.info("[WebAsyncTask端点] Callable执行完成 - 业务线程: {} - 结束时间: {} ✅",
                    businessThread, endTime);

            Map<String, Object> response = new HashMap<>();
            response.put("mode", "WebAsyncTask");
            response.put("requestThread", requestThread);
            response.put("businessThread", businessThread);
            response.put("startTime", startTime);
            response.put("executeTime", executeTime);
            response.put("endTime", endTime);
            response.put("delayMs", delay);
            response.put("timeout", "5000ms");
            response.put("status", "成功");
            response.put("message", "WebAsyncTask 执行完成");

            return response;
        };

        // 创建 WebAsyncTask（5秒超时）
        WebAsyncTask<Map<String, Object>> webAsyncTask = new WebAsyncTask<>(5000L, callable);

        // 设置超时回调
        webAsyncTask.onTimeout(() -> {
            String timeoutThread = Thread.currentThread().getName();
            String timeoutTime = LocalDateTime.now().format(FORMATTER);

            log.warn("[WebAsyncTask端点] 任务超时 - 超时线程: {} - 超时时间: {} ⏱️ 超过5秒未完成",
                    timeoutThread, timeoutTime);

            Map<String, Object> timeoutResponse = new HashMap<>();
            timeoutResponse.put("mode", "WebAsyncTask");
            timeoutResponse.put("requestThread", requestThread);
            timeoutResponse.put("timeoutThread", timeoutThread);
            timeoutResponse.put("startTime", startTime);
            timeoutResponse.put("timeoutTime", timeoutTime);
            timeoutResponse.put("delayMs", delay);
            timeoutResponse.put("timeout", "5000ms");
            timeoutResponse.put("status", "超时");
            timeoutResponse.put("message", "任务执行超过5秒，已超时");

            return timeoutResponse;
        });

        // 设置完成回调
        webAsyncTask.onCompletion(() -> {
            String completionThread = Thread.currentThread().getName();
            String completionTime = LocalDateTime.now().format(FORMATTER);

            log.info("[WebAsyncTask端点] 任务完成 - 完成线程: {} - 完成时间: {} ✅ 无论成功或超时都会调用",
                    completionThread, completionTime);
        });

        return webAsyncTask;
    }

    /**
     * WebAsyncTask 自定义超时时间
     *
     * <p><b>@教学</b>
     * <p>演示如何为不同任务设置不同的超时时间。
     *
     * <p><b>@应用场景</b>
     * <pre>
     * 真实案例:
     *   - 快速查询: 5 秒超时（用户体验优先）
     *   - 报表生成: 60 秒超时（任务完成优先）
     *   - 数据导出: 120 秒超时（大数据处理）
     * </pre>
     *
     * @param delay 延迟时间（毫秒），默认 2000ms
     * @return WebAsyncTask 异步任务
     */
    @GetMapping("/web-async-task/custom-timeout")
    public WebAsyncTask<Map<String, Object>> customTimeoutEndpoint(
            @RequestParam(defaultValue = "2000") long delay) {

        String requestThread = Thread.currentThread().getName();
        String startTime = LocalDateTime.now().format(FORMATTER);

        // 根据任务类型动态设置超时时间
        long timeout = delay < 5000 ? 10000L : 30000L; // 短任务10秒，长任务30秒

        log.info("[自定义超时端点] 创建WebAsyncTask - 请求线程: {} - 开始时间: {} - 延迟: {}ms ⚡ 超时时间: {}ms",
                requestThread, startTime, delay, timeout);

        Callable<Map<String, Object>> callable = () -> {
            String businessThread = Thread.currentThread().getName();

            log.info("[自定义超时端点] Callable开始执行 - 业务线程: {} 🔄", businessThread);

            Thread.sleep(delay);

            Map<String, Object> response = new HashMap<>();
            response.put("mode", "WebAsyncTask (Custom Timeout)");
            response.put("requestThread", requestThread);
            response.put("businessThread", businessThread);
            response.put("startTime", startTime);
            response.put("endTime", LocalDateTime.now().format(FORMATTER));
            response.put("delayMs", delay);
            response.put("customTimeout", timeout + "ms");
            response.put("status", "成功");
            response.put("message", "自定义超时任务执行完成");

            return response;
        };

        WebAsyncTask<Map<String, Object>> webAsyncTask = new WebAsyncTask<>(timeout, callable);

        webAsyncTask.onTimeout(() -> {
            log.warn("[自定义超时端点] 任务超时 ⏱️ 超过{}ms未完成", timeout);

            Map<String, Object> timeoutResponse = new HashMap<>();
            timeoutResponse.put("mode", "WebAsyncTask (Custom Timeout)");
            timeoutResponse.put("status", "超时");
            timeoutResponse.put("customTimeout", timeout + "ms");
            timeoutResponse.put("message", "任务执行超过" + timeout + "ms，已超时");

            return timeoutResponse;
        });

        return webAsyncTask;
    }

    /**
     * WebAsyncTask 使用自定义线程池
     *
     * <p><b>@教学</b>
     * <p>演示如何为不同类型的任务指定不同的线程池。
     *
     * <p><b>@应用场景</b>
     * <pre>
     * 线程池隔离:
     *   - CPU密集型任务: 使用小线程池（核心数 * 1）
     *   - IO密集型任务: 使用大线程池（核心数 * 4）
     *   - 关键任务: 使用独立线程池（防止饥饿）
     * </pre>
     *
     * @param taskType 任务类型（cpu, io, critical）
     * @return WebAsyncTask 异步任务
     */
    @GetMapping("/web-async-task/custom-executor")
    public WebAsyncTask<Map<String, Object>> customExecutorEndpoint(
            @RequestParam(defaultValue = "io") String taskType) {

        String requestThread = Thread.currentThread().getName();
        String startTime = LocalDateTime.now().format(FORMATTER);

        log.info("[自定义线程池端点] 创建WebAsyncTask - 请求线程: {} - 任务类型: {} ⚡",
                requestThread, taskType);

        Callable<Map<String, Object>> callable = () -> {
            String businessThread = Thread.currentThread().getName();

            log.info("[自定义线程池端点] Callable开始执行 - 业务线程: {} - 任务类型: {} 🔄",
                    businessThread, taskType);

            // 模拟不同类型的任务
            if ("cpu".equals(taskType)) {
                // CPU密集型任务：计算
                long sum = 0;
                for (int i = 0; i < 10_000_000; i++) {
                    sum += i;
                }
                log.debug("[自定义线程池端点] CPU密集型任务完成 - 计算结果: {}", sum);
            } else if ("io".equals(taskType)) {
                // IO密集型任务：模拟网络请求
                Thread.sleep(2000);
                log.debug("[自定义线程池端点] IO密集型任务完成 - 模拟网络请求");
            } else {
                // 关键任务：快速处理
                Thread.sleep(500);
                log.debug("[自定义线程池端点] 关键任务完成");
            }

            Map<String, Object> response = new HashMap<>();
            response.put("mode", "WebAsyncTask (Custom Executor)");
            response.put("taskType", taskType);
            response.put("requestThread", requestThread);
            response.put("businessThread", businessThread);
            response.put("startTime", startTime);
            response.put("endTime", LocalDateTime.now().format(FORMATTER));
            response.put("status", "成功");
            response.put("message", "使用自定义线程池执行" + taskType + "任务");

            return response;
        };

        // 创建 WebAsyncTask（10秒超时，使用指定的线程池）
        WebAsyncTask<Map<String, Object>> webAsyncTask = new WebAsyncTask<>(10000L, asyncTaskExecutor, callable);

        webAsyncTask.onTimeout(() -> {
            log.warn("[自定义线程池端点] 任务超时 ⏱️ 任务类型: {}", taskType);

            Map<String, Object> timeoutResponse = new HashMap<>();
            timeoutResponse.put("mode", "WebAsyncTask (Custom Executor)");
            timeoutResponse.put("taskType", taskType);
            timeoutResponse.put("status", "超时");
            timeoutResponse.put("message", taskType + "任务执行超时");

            return timeoutResponse;
        });

        return webAsyncTask;
    }

    /**
     * 健康检查端点
     *
     * @return 健康状态
     */
    @GetMapping("/web-async-task/health")
    public Map<String, String> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("controller", "WebAsyncTaskController");
        response.put("executor", asyncTaskExecutor.getClass().getSimpleName());
        response.put("time", LocalDateTime.now().toString());
        return response;
    }
}
