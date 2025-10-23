package nan.tech.lab08.springmvc.resilience;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * FaultToleranceController - 容错机制演示
 *
 * <p><b>@教学</b>
 * <ul>
 *   <li>演示 CircuitBreaker（断路器）保护机制</li>
 *   <li>演示 RateLimiter（限流器）流量控制</li>
 *   <li>演示 Retry（重试机制）故障恢复</li>
 *   <li>演示 Bulkhead（隔离舱）资源隔离</li>
 * </ul>
 *
 * <p><b>@核心概念</b>
 * <pre>
 * Resilience4j 四大核心功能：
 *
 * 1. CircuitBreaker（断路器）
 *    - CLOSED: 正常状态，请求正常通过
 *    - OPEN: 熔断状态，直接拒绝请求
 *    - HALF_OPEN: 半开状态，允许部分请求测试服务是否恢复
 *
 * 2. RateLimiter（限流器）
 *    - 控制单位时间内的请求数量
 *    - 防止系统过载
 *
 * 3. Retry（重试机制）
 *    - 自动重试失败的请求
 *    - 支持指数退避策略
 *
 * 4. Bulkhead（隔离舱）
 *    - 限制并发请求数量
 *    - 防止资源耗尽
 * </pre>
 *
 * @author Claude Code
 * @since 2025-10-20
 */
@Slf4j
@RestController
@RequestMapping("/api/resilience")
public class FaultToleranceController {

    private final AtomicInteger callCounter = new AtomicInteger(0);
    private final AtomicInteger retryCounter = new AtomicInteger(0);
    private final ExecutorService executor = Executors.newFixedThreadPool(20);

    /**
     * CircuitBreaker（断路器）演示
     *
     * <p><b>@教学</b>
     * <p>断路器工作原理：
     * <ol>
     *   <li>CLOSED 状态：正常处理请求，统计失败率</li>
     *   <li>失败率超过阈值 → OPEN 状态</li>
     *   <li>OPEN 状态：直接返回降级数据，不调用后端服务</li>
     *   <li>等待一段时间 → HALF_OPEN 状态</li>
     *   <li>HALF_OPEN 状态：允许部分请求测试服务是否恢复</li>
     *   <li>成功率达标 → CLOSED，否则 → OPEN</li>
     * </ol>
     *
     * <p><b>@测试方式</b>
     * <pre>
     * 正常请求: curl "http://localhost:8080/api/resilience/circuit-breaker?fail=false"
     * 触发熔断: curl "http://localhost:8080/api/resilience/circuit-breaker?fail=true" (多次调用)
     * </pre>
     *
     * @param fail 是否模拟失败
     * @return 异步结果
     */
    @GetMapping("/circuit-breaker")
    @CircuitBreaker(name = "externalService", fallbackMethod = "circuitBreakerFallback")
    public DeferredResult<Map<String, Object>> circuitBreakerDemo(@RequestParam(defaultValue = "false") boolean fail) throws Exception {
        int callNumber = callCounter.incrementAndGet();
        log.info("[CircuitBreaker演示] 接收请求, callNumber={}, fail={}", callNumber, fail);

        // ✅ 在方法本身抛出异常，让 CircuitBreaker 捕获
        if (fail) {
            log.error("[CircuitBreaker演示] 模拟服务失败, callNumber={}", callNumber);
            throw new RuntimeException("外部服务不可用");
        }

        DeferredResult<Map<String, Object>> deferredResult = new DeferredResult<>(5_000L);

        deferredResult.onTimeout(() -> {
            deferredResult.setResult(Map.of(
                "status", "timeout",
                "message", "请求超时"
            ));
        });

        executor.submit(() -> {
            try {
                // 模拟调用外部服务
                TimeUnit.MILLISECONDS.sleep(500);

                log.info("[CircuitBreaker演示] 服务调用成功, callNumber={}", callNumber);

                deferredResult.setResult(Map.of(
                    "status", "success",
                    "callNumber", callNumber,
                    "message", "外部服务调用成功",
                    "timestamp", LocalDateTime.now().toString()
                ));
            } catch (Exception e) {
                log.error("[CircuitBreaker演示] 异常, callNumber={}", callNumber, e);
                deferredResult.setErrorResult(e);
            }
        });

        return deferredResult;
    }

    /**
     * CircuitBreaker 降级方法
     *
     * <p><b>@教学</b>
     * <p>降级方法要求：
     * <ul>
     *   <li>方法签名必须与原方法相同</li>
     *   <li>增加一个 Throwable 类型的参数（可选）</li>
     *   <li>返回类型必须与原方法相同</li>
     * </ul>
     *
     * @param fail 是否模拟失败
     * @param t 异常信息
     * @return 降级结果
     */
    public DeferredResult<Map<String, Object>> circuitBreakerFallback(boolean fail, Throwable t) {
        log.warn("[CircuitBreaker降级] 执行降级逻辑, fail={}, error={}", fail, t.getMessage());

        DeferredResult<Map<String, Object>> deferredResult = new DeferredResult<>();

        deferredResult.setResult(Map.of(
            "status", "fallback",
            "message", "服务暂时不可用，已启用降级方案",
            "reason", t != null ? t.getMessage() : "未知错误",
            "timestamp", LocalDateTime.now().toString()
        ));

        return deferredResult;
    }

    /**
     * RateLimiter（限流器）演示
     *
     * <p><b>@教学</b>
     * <p>限流器工作原理：
     * <ul>
     *   <li>配置每个周期允许的最大请求数</li>
     *   <li>超过限制的请求被拒绝或等待</li>
     *   <li>防止系统过载</li>
     * </ul>
     *
     * <p><b>@测试方式</b>
     * <pre>
     * 正常请求: curl "http://localhost:8080/api/resilience/rate-limiter"
     * 触发限流: 快速连续发送多个请求 (超过配置的限流阈值)
     * </pre>
     *
     * @return 异步结果
     */
    @GetMapping("/rate-limiter")
    @RateLimiter(name = "apiRateLimiter", fallbackMethod = "rateLimiterFallback")
    public DeferredResult<Map<String, Object>> rateLimiterDemo() {
        int callNumber = callCounter.incrementAndGet();
        log.info("[RateLimiter演示] 接收请求, callNumber={}", callNumber);

        DeferredResult<Map<String, Object>> deferredResult = new DeferredResult<>(5_000L);

        deferredResult.onTimeout(() -> {
            deferredResult.setResult(Map.of(
                "status", "timeout",
                "message", "请求超时"
            ));
        });

        executor.submit(() -> {
            try {
                // 模拟业务处理
                TimeUnit.MILLISECONDS.sleep(100);

                log.info("[RateLimiter演示] 请求处理完成, callNumber={}", callNumber);

                deferredResult.setResult(Map.of(
                    "status", "success",
                    "callNumber", callNumber,
                    "message", "请求处理成功",
                    "timestamp", LocalDateTime.now().toString()
                ));
            } catch (Exception e) {
                log.error("[RateLimiter演示] 异常, callNumber={}", callNumber, e);
                deferredResult.setErrorResult(e);
            }
        });

        return deferredResult;
    }

    /**
     * RateLimiter 降级方法
     *
     * @param t 异常信息
     * @return 降级结果
     */
    public DeferredResult<Map<String, Object>> rateLimiterFallback(Throwable t) {
        log.warn("[RateLimiter降级] 触发限流, error={}", t.getMessage());

        DeferredResult<Map<String, Object>> deferredResult = new DeferredResult<>();

        deferredResult.setResult(Map.of(
            "status", "rate_limited",
            "message", "请求过于频繁，请稍后重试",
            "reason", "超过限流阈值",
            "timestamp", LocalDateTime.now().toString()
        ));

        return deferredResult;
    }

    /**
     * Retry（重试机制）演示
     *
     * <p><b>@教学</b>
     * <p>重试机制工作原理：
     * <ul>
     *   <li>自动重试失败的请求</li>
     *   <li>支持最大重试次数配置</li>
     *   <li>支持重试间隔配置</li>
     *   <li>支持指数退避策略</li>
     * </ul>
     *
     * <p><b>@测试方式</b>
     * <pre>
     * 正常请求: curl "http://localhost:8080/api/resilience/retry?failCount=0"
     * 触发重试: curl "http://localhost:8080/api/resilience/retry?failCount=2"
     * 重试失败: curl "http://localhost:8080/api/resilience/retry?failCount=5"
     * </pre>
     *
     * @param failCount 模拟失败次数
     * @return 异步结果
     */
    @GetMapping("/retry")
    @Retry(name = "externalService", fallbackMethod = "retryFallback")
    public DeferredResult<Map<String, Object>> retryDemo(@RequestParam(defaultValue = "0") int failCount) throws Exception {
        int callNumber = callCounter.incrementAndGet();
        int currentRetry = retryCounter.incrementAndGet();
        log.info("[Retry演示] 接收请求, callNumber={}, currentRetry={}, failCount={}", callNumber, currentRetry, failCount);

        // ✅ 在方法本身抛出异常，让 Retry 捕获
        // 模拟前 N 次失败
        if (currentRetry <= failCount) {
            log.warn("[Retry演示] 模拟第{}次失败, failCount={}", currentRetry, failCount);
            throw new RuntimeException("外部服务暂时不可用 (第" + currentRetry + "次调用)");
        }

        // 重置重试计数器
        retryCounter.set(0);

        DeferredResult<Map<String, Object>> deferredResult = new DeferredResult<>(10_000L);

        deferredResult.onTimeout(() -> {
            deferredResult.setResult(Map.of(
                "status", "timeout",
                "message", "请求超时"
            ));
        });

        executor.submit(() -> {
            try {
                // 模拟调用外部服务
                TimeUnit.MILLISECONDS.sleep(200);

                log.info("[Retry演示] 调用成功, callNumber={}, currentRetry={}", callNumber, currentRetry);

                deferredResult.setResult(Map.of(
                    "status", "success",
                    "callNumber", callNumber,
                    "retryCount", currentRetry - 1,
                    "message", "外部服务调用成功",
                    "timestamp", LocalDateTime.now().toString()
                ));
            } catch (Exception e) {
                log.error("[Retry演示] 异常, callNumber={}, currentRetry={}", callNumber, currentRetry, e);
                deferredResult.setErrorResult(e);
            }
        });

        return deferredResult;
    }

    /**
     * Retry 降级方法
     *
     * @param failCount 模拟失败次数
     * @param t 异常信息
     * @return 降级结果
     */
    public DeferredResult<Map<String, Object>> retryFallback(int failCount, Throwable t) {
        log.warn("[Retry降级] 重试失败, failCount={}, error={}", failCount, t.getMessage());

        // 重置重试计数器
        retryCounter.set(0);

        DeferredResult<Map<String, Object>> deferredResult = new DeferredResult<>();

        deferredResult.setResult(Map.of(
            "status", "retry_exhausted",
            "message", "重试次数已用尽，服务仍不可用",
            "failCount", failCount,
            "reason", t != null ? t.getMessage() : "未知错误",
            "timestamp", LocalDateTime.now().toString()
        ));

        return deferredResult;
    }

    /**
     * Bulkhead（隔离舱）演示
     *
     * <p><b>@教学</b>
     * <p>隔离舱工作原理：
     * <ul>
     *   <li>限制并发请求数量</li>
     *   <li>防止单个服务耗尽所有资源</li>
     *   <li>类似船舱隔离，一个舱进水不影响其他舱</li>
     * </ul>
     *
     * <p><b>@测试方式</b>
     * <pre>
     * 正常请求: curl "http://localhost:8080/api/resilience/bulkhead?delay=1000"
     * 触发隔离: 并发发送多个请求 (超过配置的并发数)
     * </pre>
     *
     * @param delay 模拟处理时间 (毫秒)
     * @return 异步结果
     */
    @GetMapping("/bulkhead")
    @Bulkhead(name = "externalService", fallbackMethod = "bulkheadFallback")
    public DeferredResult<Map<String, Object>> bulkheadDemo(@RequestParam(defaultValue = "1000") int delay) {
        int callNumber = callCounter.incrementAndGet();
        log.info("[Bulkhead演示] 接收请求, callNumber={}, delay={}ms", callNumber, delay);

        DeferredResult<Map<String, Object>> deferredResult = new DeferredResult<>(10_000L);

        deferredResult.onTimeout(() -> {
            deferredResult.setResult(Map.of(
                "status", "timeout",
                "message", "请求超时"
            ));
        });

        executor.submit(() -> {
            try {
                // 模拟业务处理
                TimeUnit.MILLISECONDS.sleep(delay);

                log.info("[Bulkhead演示] 处理完成, callNumber={}", callNumber);

                deferredResult.setResult(Map.of(
                    "status", "success",
                    "callNumber", callNumber,
                    "delayMs", delay,
                    "message", "请求处理成功",
                    "timestamp", LocalDateTime.now().toString()
                ));
            } catch (Exception e) {
                log.error("[Bulkhead演示] 异常, callNumber={}", callNumber, e);
                deferredResult.setErrorResult(e);
            }
        });

        return deferredResult;
    }

    /**
     * Bulkhead 降级方法
     *
     * @param delay 模拟处理时间
     * @param t 异常信息
     * @return 降级结果
     */
    public DeferredResult<Map<String, Object>> bulkheadFallback(int delay, Throwable t) {
        log.warn("[Bulkhead降级] 并发超限, delay={}ms, error={}", delay, t.getMessage());

        DeferredResult<Map<String, Object>> deferredResult = new DeferredResult<>();

        deferredResult.setResult(Map.of(
            "status", "bulkhead_full",
            "message", "系统繁忙，请稍后重试",
            "reason", "并发请求数已达上限",
            "timestamp", LocalDateTime.now().toString()
        ));

        return deferredResult;
    }

    /**
     * 组合模式演示 (CircuitBreaker + Retry + RateLimiter)
     *
     * <p><b>@教学</b>
     * <p>组合使用注意事项：
     * <ul>
     *   <li>执行顺序：RateLimiter → CircuitBreaker → Retry → 业务逻辑</li>
     *   <li>先限流，再断路，最后重试</li>
     *   <li>降级方法需要匹配所有注解</li>
     * </ul>
     *
     * @param fail 是否模拟失败
     * @return 异步结果
     */
    @GetMapping("/combined")
    @RateLimiter(name = "apiRateLimiter")
    @CircuitBreaker(name = "externalService")
    @Retry(name = "externalService", fallbackMethod = "combinedFallback")
    public DeferredResult<Map<String, Object>> combinedDemo(@RequestParam(defaultValue = "false") boolean fail) throws Exception {
        int callNumber = callCounter.incrementAndGet();
        log.info("[组合模式演示] 接收请求, callNumber={}, fail={}", callNumber, fail);

        // ✅ 在方法本身抛出异常，让 Resilience4j 注解捕获
        if (fail) {
            log.error("[组合模式演示] 模拟服务失败, callNumber={}", callNumber);
            throw new RuntimeException("模拟服务失败");
        }

        DeferredResult<Map<String, Object>> deferredResult = new DeferredResult<>(10_000L);

        executor.submit(() -> {
            try {
                TimeUnit.MILLISECONDS.sleep(300);

                deferredResult.setResult(Map.of(
                    "status", "success",
                    "callNumber", callNumber,
                    "message", "请求处理成功 (经过限流+断路+重试保护)",
                    "timestamp", LocalDateTime.now().toString()
                ));
            } catch (Exception e) {
                deferredResult.setErrorResult(e);
            }
        });

        return deferredResult;
    }

    /**
     * 组合模式降级方法
     */
    public DeferredResult<Map<String, Object>> combinedFallback(boolean fail, Throwable t) {
        log.warn("[组合模式降级] fail={}, error={}", fail, t.getMessage());

        DeferredResult<Map<String, Object>> deferredResult = new DeferredResult<>();

        deferredResult.setResult(Map.of(
            "status", "fallback",
            "message", "服务降级：限流+断路+重试保护已生效",
            "reason", t != null ? t.getMessage() : "未知错误",
            "timestamp", LocalDateTime.now().toString()
        ));

        return deferredResult;
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
            "controller", "FaultToleranceController",
            "totalCalls", callCounter.get(),
            "executor", Map.of(
                "type", "FixedThreadPool",
                "poolSize", 20
            ),
            "time", LocalDateTime.now().toString()
        );
    }
}
