package nan.tech.lab08.springmvc.timeout;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.context.request.async.WebAsyncTask;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.*;

/**
 * TimeoutController - 超时控制演示
 *
 * <p><b>@教学</b>
 * <ul>
 *   <li>演示 Callable 超时场景</li>
 *   <li>演示 DeferredResult 超时场景</li>
 *   <li>演示 WebAsyncTask 超时场景</li>
 *   <li>演示超时降级策略（快速失败、缓存数据、默认值）</li>
 * </ul>
 *
 * <p><b>@核心知识点</b>
 * <pre>
 * 三层超时架构：
 *   - Layer 1: Tomcat 全局超时 (application.yml)
 *   - Layer 2: AsyncContext 超时 (默认继承全局)
 *   - Layer 3: WebAsyncTask/DeferredResult 超时 (精确控制)
 *
 * 优先级：
 *   WebAsyncTask/DeferredResult > AsyncContext > Tomcat 全局
 * </pre>
 *
 * @author Claude Code
 * @since 2025-10-20
 */
@Slf4j
@RestController
@RequestMapping("/api/timeout")
public class TimeoutController {

    private final ExecutorService executor = Executors.newFixedThreadPool(10);

    // 模拟缓存 (用于降级策略)
    private final Map<String, Object> cache = new ConcurrentHashMap<>();

    /**
     * Callable 超时演示
     *
     * <p><b>@教学</b>
     * <p>Callable 超时特点：
     * <ul>
     *   <li>依赖全局超时配置 (application.yml: spring.mvc.async.request-timeout)</li>
     *   <li>无法自定义超时回调</li>
     *   <li>超时后抛出 AsyncRequestTimeoutException</li>
     *   <li>适合简单场景，不需要精细控制</li>
     * </ul>
     *
     * <p><b>@测试方式</b>
     * <pre>
     * 正常完成: curl "http://localhost:8080/api/timeout/callable?delay=2000"
     * 超时失败: curl "http://localhost:8080/api/timeout/callable?delay=35000"
     * </pre>
     *
     * @param delay 模拟延迟时间 (毫秒)
     * @return 异步结果
     */
    @GetMapping("/callable")
    public Callable<Map<String, Object>> callableTimeout(@RequestParam(defaultValue = "2000") int delay) {
        String requestThread = Thread.currentThread().getName();
        log.info("[Callable超时演示] 接收请求, delay={}ms, requestThread={}", delay, requestThread);

        return () -> {
            String businessThread = Thread.currentThread().getName();
            log.info("[Callable超时演示] 开始执行, businessThread={}", businessThread);

            try {
                // 模拟业务处理
                TimeUnit.MILLISECONDS.sleep(delay);

                log.info("[Callable超时演示] 执行完成");

                return Map.of(
                    "mode", "Callable",
                    "requestThread", requestThread,
                    "businessThread", businessThread,
                    "delayMs", delay,
                    "completedAt", LocalDateTime.now().toString(),
                    "status", "成功"
                );
            } catch (InterruptedException e) {
                log.error("[Callable超时演示] 被中断", e);
                Thread.currentThread().interrupt();
                throw new RuntimeException("Task interrupted", e);
            }
        };
    }

    /**
     * DeferredResult 超时演示
     *
     * <p><b>@教学</b>
     * <p>DeferredResult 超时特点：
     * <ul>
     *   <li>构造函数指定超时时间 (精确到毫秒)</li>
     *   <li>支持 onTimeout() 回调设置超时结果</li>
     *   <li>支持 onCompletion() 回调清理资源</li>
     *   <li>适合事件驱动场景 (外部触发完成)</li>
     * </ul>
     *
     * <p><b>@测试方式</b>
     * <pre>
     * 正常完成: curl "http://localhost:8080/api/timeout/deferred?delay=2000"
     * 超时失败: curl "http://localhost:8080/api/timeout/deferred?delay=12000"
     * </pre>
     *
     * @param delay 模拟延迟时间 (毫秒)
     * @return 异步结果
     */
    @GetMapping("/deferred")
    public DeferredResult<Map<String, Object>> deferredTimeout(@RequestParam(defaultValue = "2000") int delay) {
        String requestThread = Thread.currentThread().getName();
        log.info("[DeferredResult超时演示] 接收请求, delay={}ms, requestThread={}", delay, requestThread);

        // ✅ 设置 10 秒超时
        DeferredResult<Map<String, Object>> deferredResult = new DeferredResult<>(10_000L);

        // ✅ 超时回调：返回降级数据
        deferredResult.onTimeout(() -> {
            log.warn("[DeferredResult超时演示] 请求超时, delay={}ms", delay);

            deferredResult.setResult(Map.of(
                "mode", "DeferredResult",
                "requestThread", requestThread,
                "delayMs", delay,
                "timeout", "10000ms",
                "status", "超时",
                "message", "请求超时，已返回降级数据"
            ));
        });

        // ✅ 完成回调：清理资源
        deferredResult.onCompletion(() -> {
            log.info("[DeferredResult超时演示] 请求完成, delay={}ms", delay);
        });

        // ✅ 错误回调：异常处理
        deferredResult.onError((Throwable t) -> {
            log.error("[DeferredResult超时演示] 请求失败, delay={}ms", delay, t);

            deferredResult.setErrorResult(Map.of(
                "mode", "DeferredResult",
                "delayMs", delay,
                "status", "错误",
                "message", t.getMessage()
            ));
        });

        // 异步执行业务逻辑
        executor.submit(() -> {
            String businessThread = Thread.currentThread().getName();
            log.info("[DeferredResult超时演示] 开始执行, businessThread={}", businessThread);

            try {
                TimeUnit.MILLISECONDS.sleep(delay);

                // 如果还没超时，设置结果
                if (!deferredResult.isSetOrExpired()) {
                    log.info("[DeferredResult超时演示] 执行完成");

                    deferredResult.setResult(Map.of(
                        "mode", "DeferredResult",
                        "requestThread", requestThread,
                        "businessThread", businessThread,
                        "delayMs", delay,
                        "timeout", "10000ms",
                        "completedAt", LocalDateTime.now().toString(),
                        "status", "成功"
                    ));
                } else {
                    log.warn("[DeferredResult超时演示] 任务完成但已超时, delay={}ms", delay);
                }
            } catch (InterruptedException e) {
                log.error("[DeferredResult超时演示] 被中断", e);
                Thread.currentThread().interrupt();
            }
        });

        return deferredResult;
    }

    /**
     * WebAsyncTask 超时演示 (推荐方式)
     *
     * <p><b>@教学</b>
     * <p>WebAsyncTask 超时特点：
     * <ul>
     *   <li>构造函数指定超时时间</li>
     *   <li>支持 onTimeout() 回调返回降级数据</li>
     *   <li>支持 onCompletion() 回调清理资源</li>
     *   <li>支持 onError() 回调错误处理</li>
     *   <li>支持自定义线程池</li>
     *   <li>适合大多数异步场景 (推荐)</li>
     * </ul>
     *
     * <p><b>@测试方式</b>
     * <pre>
     * 正常完成: curl "http://localhost:8080/api/timeout/web-async-task?delay=2000"
     * 超时失败: curl "http://localhost:8080/api/timeout/web-async-task?delay=8000"
     * </pre>
     *
     * @param delay 模拟延迟时间 (毫秒)
     * @return 异步结果
     */
    @GetMapping("/web-async-task")
    public WebAsyncTask<Map<String, Object>> webAsyncTaskTimeout(@RequestParam(defaultValue = "2000") int delay) {
        String requestThread = Thread.currentThread().getName();
        log.info("[WebAsyncTask超时演示] 接收请求, delay={}ms, requestThread={}", delay, requestThread);

        // ✅ 创建 WebAsyncTask，设置 5 秒超时
        Callable<Map<String, Object>> callable = () -> {
            String businessThread = Thread.currentThread().getName();
            log.info("[WebAsyncTask超时演示] 开始执行, businessThread={}", businessThread);

            try {
                TimeUnit.MILLISECONDS.sleep(delay);

                log.info("[WebAsyncTask超时演示] 执行完成");

                return Map.of(
                    "mode", "WebAsyncTask",
                    "requestThread", requestThread,
                    "businessThread", businessThread,
                    "delayMs", delay,
                    "timeout", "5000ms",
                    "completedAt", LocalDateTime.now().toString(),
                    "status", "成功"
                );
            } catch (InterruptedException e) {
                log.error("[WebAsyncTask超时演示] 被中断", e);
                Thread.currentThread().interrupt();
                throw new RuntimeException("Task interrupted", e);
            }
        };

        WebAsyncTask<Map<String, Object>> task = new WebAsyncTask<>(5_000L, callable);

        // ✅ 超时回调：返回降级数据
        task.onTimeout(() -> {
            log.warn("[WebAsyncTask超时演示] 请求超时, delay={}ms", delay);

            return Map.of(
                "mode", "WebAsyncTask",
                "requestThread", requestThread,
                "delayMs", delay,
                "timeout", "5000ms",
                "status", "超时",
                "message", "任务执行超过 5 秒，已超时"
            );
        });

        // ✅ 完成回调：清理资源
        task.onCompletion(() -> {
            log.info("[WebAsyncTask超时演示] 请求完成, delay={}ms", delay);
        });

        // ✅ 错误回调：异常处理
        task.onError(() -> {
            log.error("[WebAsyncTask超时演示] 请求失败, delay={}ms", delay);

            return Map.of(
                "mode", "WebAsyncTask",
                "delayMs", delay,
                "status", "错误",
                "message", "任务执行失败"
            );
        });

        return task;
    }

    /**
     * 超时降级策略演示 - 快速失败
     *
     * <p><b>@教学</b>
     * <p>快速失败策略：
     * <ul>
     *   <li>适用场景: 非关键功能</li>
     *   <li>超时后: 直接返回错误信息</li>
     *   <li>优点: 简单直接，资源占用少</li>
     *   <li>缺点: 用户体验较差</li>
     * </ul>
     *
     * @param delay 模拟延迟时间 (毫秒)
     * @return 异步结果
     */
    @GetMapping("/strategy/fail-fast")
    public WebAsyncTask<Map<String, Object>> failFastStrategy(@RequestParam(defaultValue = "2000") int delay) {
        log.info("[快速失败策略] 接收请求, delay={}ms", delay);

        Callable<Map<String, Object>> callable = () -> {
            TimeUnit.MILLISECONDS.sleep(delay);
            return Map.of("status", "success", "data", "业务数据");
        };

        WebAsyncTask<Map<String, Object>> task = new WebAsyncTask<>(3_000L, callable);

        // ✅ 快速失败：直接告知用户
        task.onTimeout(() -> {
            log.warn("[快速失败策略] 请求超时");

            return Map.of(
                "status", "fail",
                "code", 408,
                "message", "功能暂时不可用，请稍后重试"
            );
        });

        return task;
    }

    /**
     * 超时降级策略演示 - 返回缓存数据
     *
     * <p><b>@教学</b>
     * <p>缓存降级策略：
     * <ul>
     *   <li>适用场景: 数据时效性要求不高</li>
     *   <li>超时后: 返回最近一次成功的结果</li>
     *   <li>优点: 用户体验较好，可用性高</li>
     *   <li>缺点: 数据可能过时</li>
     * </ul>
     *
     * @param key 数据键
     * @param delay 模拟延迟时间 (毫秒)
     * @return 异步结果
     */
    @GetMapping("/strategy/cached-data")
    public WebAsyncTask<Map<String, Object>> cachedDataStrategy(
            @RequestParam String key,
            @RequestParam(defaultValue = "2000") int delay) {

        log.info("[缓存降级策略] 接收请求, key={}, delay={}ms", key, delay);

        Callable<Map<String, Object>> callable = () -> {
            TimeUnit.MILLISECONDS.sleep(delay);

            Map<String, Object> freshData = Map.of(
                "status", "success",
                "data", "最新数据-" + LocalDateTime.now(),
                "fromCache", false
            );

            // ✅ 更新缓存
            cache.put(key, freshData);

            return freshData;
        };

        WebAsyncTask<Map<String, Object>> task = new WebAsyncTask<>(3_000L, callable);

        // ✅ 返回缓存数据
        task.onTimeout(() -> {
            log.warn("[缓存降级策略] 请求超时, 返回缓存数据");

            @SuppressWarnings("unchecked")
            Map<String, Object> cachedData = (Map<String, Object>) cache.get(key);

            if (cachedData != null) {
                return Map.of(
                    "status", "timeout",
                    "data", cachedData.get("data"),
                    "fromCache", true,
                    "message", "实时数据获取超时，返回缓存数据"
                );
            } else {
                return Map.of(
                    "status", "fail",
                    "message", "无缓存数据可用"
                );
            }
        });

        return task;
    }

    /**
     * 超时降级策略演示 - 返回默认值
     *
     * <p><b>@教学</b>
     * <p>默认值降级策略：
     * <ul>
     *   <li>适用场景: 核心功能，必须返回数据</li>
     *   <li>超时后: 返回预设的兜底数据</li>
     *   <li>优点: 系统可用性高，用户体验好</li>
     *   <li>缺点: 需要设计合理的默认值</li>
     * </ul>
     *
     * @param userId 用户ID
     * @param delay 模拟延迟时间 (毫秒)
     * @return 异步结果
     */
    @GetMapping("/strategy/default-value")
    public WebAsyncTask<Map<String, Object>> defaultValueStrategy(
            @RequestParam String userId,
            @RequestParam(defaultValue = "2000") int delay) {

        log.info("[默认值降级策略] 接收请求, userId={}, delay={}ms", userId, delay);

        Callable<Map<String, Object>> callable = () -> {
            TimeUnit.MILLISECONDS.sleep(delay);

            return Map.of(
                "userId", userId,
                "nickname", "真实昵称-" + userId,
                "avatar", "https://example.com/avatar/" + userId + ".png",
                "degraded", false
            );
        };

        WebAsyncTask<Map<String, Object>> task = new WebAsyncTask<>(3_000L, callable);

        // ✅ 返回默认值
        task.onTimeout(() -> {
            log.warn("[默认值降级策略] 请求超时, 返回默认值");

            return Map.of(
                "userId", userId,
                "nickname", "用户" + userId.substring(0, Math.min(4, userId.length())),
                "avatar", "/default-avatar.png",
                "degraded", true,
                "message", "数据加载超时，已返回默认值"
            );
        });

        return task;
    }

    /**
     * 健康检查端点
     *
     * @return 健康状态
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
            "status", "UP",
            "controller", "TimeoutController",
            "executor", Map.of(
                "type", "FixedThreadPool",
                "poolSize", 10
            ),
            "cacheSize", cache.size(),
            "time", LocalDateTime.now().toString()
        );
    }
}
