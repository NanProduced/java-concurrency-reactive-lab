package nan.tech.lab08.springmvc.basics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DeferredResult 异步演示 Controller
 *
 * <p><b>@教学</b>
 * <ul>
 *   <li>演示事件驱动的异步模式（最高灵活度）</li>
 *   <li>对比 Callable vs DeferredResult 的使用场景</li>
 *   <li>演示外部事件触发响应（模拟消息队列）</li>
 *   <li>完整的生命周期管理：onTimeout, onError, onCompletion</li>
 * </ul>
 *
 * <p><b>@核心概念</b>
 * <pre>
 * Callable 适用场景:
 *   - 简单异步任务（在线程池中执行）
 *   - Spring 自动管理执行
 *   - 任务逻辑在 Controller 中定义
 *   → 适合：数据库查询、外部 API 调用等
 *
 * DeferredResult 适用场景:
 *   - 事件驱动的异步任务（需要等待外部事件）
 *   - 开发者手动管理何时完成
 *   - 任务逻辑在 Controller 外部
 *   → 适合：消息队列、WebSocket、长轮询等
 * </pre>
 *
 * <p><b>@执行流程</b>
 * <pre>
 * 1. 客户端发起请求 → Tomcat 线程接收
 * 2. Controller 创建 DeferredResult → 立即返回（释放 Tomcat 线程）
 * 3. DeferredResult 存储到内存（等待外部事件）
 * 4. 外部事件触发（例如消息队列、WebSocket）
 * 5. 调用 deferredResult.setResult() → 重新分派到 Servlet 容器
 * 6. 渲染响应 → 返回客户端
 * </pre>
 *
 * <p><b>@应用场景</b>
 * <pre>
 * 真实案例:
 *   - 订单状态推送: 用户下单后，等待支付回调/物流更新
 *   - 聊天消息: 客户端长轮询，等待新消息到达
 *   - 异步任务查询: 提交任务后，轮询任务结果
 *   - WebSocket 替代: 在不支持 WebSocket 的环境中实现实时推送
 * </pre>
 *
 * <p><b>@快速测试</b>
 * <pre>
 * # 场景1: 正常完成
 * # 终端1: 发起异步请求（会阻塞等待）
 * curl "http://localhost:8080/api/async/deferred?userId=user123"
 *
 * # 终端2: 触发完成（3秒内）
 * curl -X POST "http://localhost:8080/api/async/deferred/complete?userId=user123&message=订单已支付"
 *
 * # 场景2: 超时
 * # 只执行终端1，等待 10 秒后超时
 *
 * # 场景3: 错误
 * # 终端1: 发起请求
 * curl "http://localhost:8080/api/async/deferred?userId=user456"
 * # 终端2: 触发错误
 * curl -X POST "http://localhost:8080/api/async/deferred/error?userId=user456&error=支付失败"
 *
 * # 查看待处理请求
 * curl "http://localhost:8080/api/async/deferred/pending"
 * </pre>
 *
 * @author Claude Code
 * @since 2025-10-20
 */
@RestController
@RequestMapping("/api/async")
public class DeferredResultController {

    private static final Logger log = LoggerFactory.getLogger(DeferredResultController.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    /**
     * 存储待处理的 DeferredResult（模拟消息队列的订阅者）
     *
     * <p><b>@教学</b>
     * <p>在真实场景中，这个 Map 可能是：
     * <ul>
     *   <li>Redis 缓存（分布式环境）</li>
     *   <li>消息队列的订阅者列表（Kafka, RabbitMQ）</li>
     *   <li>WebSocket 会话管理器</li>
     * </ul>
     *
     * <p><b>@陷阱</b>
     * <p>必须使用 ConcurrentHashMap 保证线程安全，因为：
     * <ul>
     *   <li>请求线程写入 DeferredResult</li>
     *   <li>事件线程读取并完成 DeferredResult</li>
     *   <li>清理线程删除已完成的 DeferredResult</li>
     * </ul>
     */
    private final ConcurrentHashMap<String, DeferredResult<Map<String, Object>>> pendingRequests = new ConcurrentHashMap<>();

    /**
     * 创建 DeferredResult 并等待外部事件（模拟长轮询）
     *
     * <p><b>@教学</b>
     * <p>此端点模拟客户端等待外部事件的场景（例如：等待支付回调）。
     *
     * <p><b>@执行流程</b>
     * <pre>
     * 1. Tomcat 线程接收请求 → 调用此方法
     * 2. 创建 DeferredResult(10秒超时) → 立即返回
     * 3. Tomcat 线程立即释放（可以处理其他请求）
     * 4. DeferredResult 存储到 pendingRequests 中
     * 5. 等待外部事件触发（10秒内）
     * 6. 如果超时 → onTimeout 回调 → 返回超时响应
     * </pre>
     *
     * <p><b>@线程模型</b>
     * <ul>
     *   <li>请求线程: http-nio-8080-exec-N（立即释放）</li>
     *   <li>事件线程: 外部事件的线程（例如：MQ消费者线程）</li>
     *   <li>超时线程: Servlet 容器的超时检查线程</li>
     * </ul>
     *
     * @param userId 用户ID（用于标识请求）
     * @return DeferredResult 异步结果
     */
    @GetMapping("/deferred")
    public DeferredResult<Map<String, Object>> deferredEndpoint(
            @RequestParam(required = true) String userId) {

        String requestThread = Thread.currentThread().getName();
        String startTime = LocalDateTime.now().format(FORMATTER);

        log.info("[DeferredResult端点] 创建异步请求 - 用户: {} - 请求线程: {} - 开始时间: {} ⚡ 请求线程即将释放",
                userId, requestThread, startTime);

        // 创建 DeferredResult（10秒超时）
        DeferredResult<Map<String, Object>> deferredResult = new DeferredResult<>(10000L);

        // ========== 回调1: onTimeout（超时时触发） ==========
        deferredResult.onTimeout(() -> {
            String timeoutThread = Thread.currentThread().getName();
            String timeoutTime = LocalDateTime.now().format(FORMATTER);

            log.warn("[DeferredResult端点] 请求超时 - 用户: {} - 超时线程: {} - 超时时间: {} ⏱️ 10秒内未收到事件",
                    userId, timeoutThread, timeoutTime);

            // 设置超时响应
            Map<String, Object> timeoutResponse = new HashMap<>();
            timeoutResponse.put("status", "timeout");
            timeoutResponse.put("userId", userId);
            timeoutResponse.put("message", "等待外部事件超时（10秒）");
            timeoutResponse.put("requestThread", requestThread);
            timeoutResponse.put("timeoutThread", timeoutThread);
            timeoutResponse.put("startTime", startTime);
            timeoutResponse.put("timeoutTime", timeoutTime);

            deferredResult.setResult(timeoutResponse);
        });

        // ========== 回调2: onError（异常时触发） ==========
        deferredResult.onError(throwable -> {
            String errorThread = Thread.currentThread().getName();
            String errorTime = LocalDateTime.now().format(FORMATTER);

            log.error("[DeferredResult端点] 请求异常 - 用户: {} - 异常线程: {} - 异常时间: {} ❌ 异常: {}",
                    userId, errorThread, errorTime, throwable.getMessage(), throwable);

            // 设置错误响应
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("userId", userId);
            errorResponse.put("message", "请求处理异常: " + throwable.getMessage());
            errorResponse.put("requestThread", requestThread);
            errorResponse.put("errorThread", errorThread);
            errorResponse.put("startTime", startTime);
            errorResponse.put("errorTime", errorTime);

            deferredResult.setErrorResult(errorResponse);
        });

        // ========== 回调3: onCompletion（完成时触发，无论成功/失败/超时） ==========
        deferredResult.onCompletion(() -> {
            String completionThread = Thread.currentThread().getName();
            String completionTime = LocalDateTime.now().format(FORMATTER);

            log.info("[DeferredResult端点] 请求完成 - 用户: {} - 完成线程: {} - 完成时间: {} ✅ 开始清理资源",
                    userId, completionThread, completionTime);

            // ⚠️ 清理资源：从 Map 中移除（防止内存泄漏）
            pendingRequests.remove(userId);

            log.debug("[DeferredResult端点] 资源清理完成 - 用户: {} - 剩余待处理请求数: {}",
                    userId, pendingRequests.size());
        });

        // 存储 DeferredResult 到内存（等待外部事件）
        pendingRequests.put(userId, deferredResult);

        log.info("[DeferredResult端点] DeferredResult已存储 - 用户: {} - 待处理请求数: {} 🔄 等待外部事件触发",
                userId, pendingRequests.size());

        return deferredResult;
    }

    /**
     * 触发 DeferredResult 完成（模拟外部事件）
     *
     * <p><b>@教学</b>
     * <p>此端点模拟外部事件触发（例如：支付回调、消息到达）。
     *
     * <p><b>@执行流程</b>
     * <pre>
     * 1. 外部系统调用此端点（例如：支付宝回调）
     * 2. 根据 userId 查找对应的 DeferredResult
     * 3. 调用 deferredResult.setResult() → 重新分派到 Servlet 容器
     * 4. 返回响应给客户端
     * </pre>
     *
     * <p><b>@应用场景</b>
     * <pre>
     * 真实案例:
     *   - 支付回调: 支付宝/微信支付异步通知
     *   - 消息推送: Kafka 消费者接收到新消息
     *   - 任务完成: 后台任务执行完成后通知
     * </pre>
     *
     * @param userId  用户ID
     * @param message 事件消息
     * @return 触发结果
     */
    @PostMapping("/deferred/complete")
    public Map<String, Object> completeDeferredResult(
            @RequestParam(required = true) String userId,
            @RequestParam(defaultValue = "外部事件已触发") String message) {

        String eventThread = Thread.currentThread().getName();
        String completeTime = LocalDateTime.now().format(FORMATTER);

        log.info("[DeferredResult完成] 外部事件触发 - 用户: {} - 事件线程: {} - 完成时间: {} 🎉 消息: {}",
                userId, eventThread, completeTime, message);

        // 查找待处理的 DeferredResult
        DeferredResult<Map<String, Object>> deferredResult = pendingRequests.get(userId);

        if (deferredResult == null) {
            log.warn("[DeferredResult完成] 未找到待处理请求 - 用户: {} ⚠️ 可能已超时或已完成", userId);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "not_found");
            response.put("userId", userId);
            response.put("message", "未找到待处理的请求（可能已超时或已完成）");
            return response;
        }

        // 设置结果（触发 DeferredResult 完成）
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("userId", userId);
        result.put("message", message);
        result.put("eventThread", eventThread);
        result.put("completeTime", completeTime);
        result.put("data", "外部事件处理结果: " + message);

        // ⚡ 关键步骤: 设置结果 → 重新分派到 Servlet 容器
        deferredResult.setResult(result);

        log.info("[DeferredResult完成] 结果已设置 - 用户: {} ✅ DeferredResult 将重新分派到容器", userId);

        // 返回确认响应（给触发事件的调用方）
        Map<String, Object> response = new HashMap<>();
        response.put("status", "triggered");
        response.put("userId", userId);
        response.put("message", "DeferredResult 已触发完成");
        response.put("eventThread", eventThread);
        response.put("completeTime", completeTime);

        return response;
    }

    /**
     * 触发 DeferredResult 错误（模拟外部事件失败）
     *
     * <p><b>@教学</b>
     * <p>此端点模拟外部事件处理失败（例如：支付失败、消息处理异常）。
     *
     * @param userId 用户ID
     * @param error  错误信息
     * @return 触发结果
     */
    @PostMapping("/deferred/error")
    public Map<String, Object> errorDeferredResult(
            @RequestParam(required = true) String userId,
            @RequestParam(defaultValue = "外部事件处理失败") String error) {

        String eventThread = Thread.currentThread().getName();
        String errorTime = LocalDateTime.now().format(FORMATTER);

        log.error("[DeferredResult错误] 外部事件失败 - 用户: {} - 事件线程: {} - 错误时间: {} ❌ 错误: {}",
                userId, eventThread, errorTime, error);

        DeferredResult<Map<String, Object>> deferredResult = pendingRequests.get(userId);

        if (deferredResult == null) {
            log.warn("[DeferredResult错误] 未找到待处理请求 - 用户: {} ⚠️ 可能已超时或已完成", userId);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "not_found");
            response.put("userId", userId);
            response.put("message", "未找到待处理的请求");
            return response;
        }

        // 设置错误结果
        Map<String, Object> errorResult = new HashMap<>();
        errorResult.put("status", "error");
        errorResult.put("userId", userId);
        errorResult.put("message", "外部事件处理失败: " + error);
        errorResult.put("eventThread", eventThread);
        errorResult.put("errorTime", errorTime);

        // ⚡ 关键步骤: 设置错误结果
        deferredResult.setErrorResult(errorResult);

        log.info("[DeferredResult错误] 错误结果已设置 - 用户: {} ❌ DeferredResult 将重新分派到容器", userId);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "triggered");
        response.put("userId", userId);
        response.put("message", "DeferredResult 已触发错误");
        response.put("eventThread", eventThread);
        response.put("errorTime", errorTime);

        return response;
    }

    /**
     * 查看待处理的请求列表
     *
     * <p><b>@教学</b>
     * <p>此端点用于监控当前有多少请求正在等待外部事件。
     *
     * @return 待处理请求列表
     */
    @GetMapping("/deferred/pending")
    public Map<String, Object> getPendingRequests() {
        log.debug("[DeferredResult监控] 查询待处理请求 - 数量: {}", pendingRequests.size());

        Map<String, Object> response = new HashMap<>();
        response.put("pendingCount", pendingRequests.size());
        response.put("userIds", pendingRequests.keySet());
        response.put("timestamp", LocalDateTime.now().format(FORMATTER));
        response.put("message", "当前有 " + pendingRequests.size() + " 个请求正在等待外部事件");

        return response;
    }

    /**
     * 健康检查端点
     *
     * @return 健康状态
     */
    @GetMapping("/deferred/health")
    public Map<String, String> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("controller", "DeferredResultController");
        response.put("pendingRequests", String.valueOf(pendingRequests.size()));
        response.put("time", LocalDateTime.now().toString());
        return response;
    }
}
