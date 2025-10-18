package nan.tech.lab00.foundation.util;

import org.slf4j.MDC;

import java.util.Map;
import java.util.function.Function;
import java.util.function.Runnable;
import java.util.function.Supplier;

/**
 * MDC (Mapped Diagnostic Context) 工具类。
 *
 * <p>用于在异步任务中自动传播 MDC 上下文（如 traceId、userId）。
 * 基于装饰器模式，将普通的函数式接口包装为支持 MDC 传播的版本。
 *
 * <p><b>核心功能</b>:
 * <ul>
 *   <li>自动捕获调用线程的 MDC</li>
 *   <li>在执行线程恢复 MDC</li>
 *   <li>执行完成后自动清理 MDC（避免线程池污染）</li>
 * </ul>
 *
 * <p><b>使用场景</b>:
 * <ul>
 *   <li>CompletableFuture 异步任务</li>
 *   <li>线程池提交的任务</li>
 *   <li>响应式编程（Reactor）</li>
 * </ul>
 *
 * <p><b>使用示例</b>:
 * <pre>{@code
 * // 设置 MDC
 * MDC.put("traceId", "12345");
 * MDC.put("userId", "user-100");
 *
 * // 异步任务中使用 MDC 装饰器
 * CompletableFuture<String> future = CompletableFuture
 *     .supplyAsync(MDCUtil.withMDC(() -> {
 *         // 这里可以正常获取 MDC
 *         log.info("traceId={}", MDC.get("traceId"));
 *         return "result";
 *     }));
 * }</pre>
 *
 * <p><b>注意事项</b>:
 * <ul>
 *   <li>必须在异步调用前使用装饰器，调用后捕获为时已晚</li>
 *   <li>工具类会在 finally 块自动清理 MDC，无需手动处理</li>
 *   <li>适用于所有 ThreadLocal 场景（认证上下文、事务上下文等）</li>
 * </ul>
 *
 * @author Nan
 * @since 1.0.0
 * @see org.slf4j.MDC
 */
public final class MDCUtil {

    /**
     * 私有构造函数，禁止实例化。
     */
    private MDCUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * 装饰 Supplier，自动传播 MDC。
     *
     * <p><b>工作原理</b>:
     * <ol>
     *   <li>在调用线程捕获当前 MDC（getCopyOfContextMap）</li>
     *   <li>返回新的 Supplier，在执行时恢复 MDC</li>
     *   <li>执行原始 Supplier 的逻辑</li>
     *   <li>在 finally 块清理 MDC，避免线程池污染</li>
     * </ol>
     *
     * <p><b>使用示例</b>:
     * <pre>{@code
     * CompletableFuture<String> future = CompletableFuture
     *     .supplyAsync(MDCUtil.withMDC(() -> {
     *         return businessService.process();
     *     }));
     * }</pre>
     *
     * @param supplier 原始 Supplier
     * @param <T> 返回值类型
     * @return 带 MDC 传播能力的 Supplier
     */
    public static <T> Supplier<T> withMDC(Supplier<T> supplier) {
        // 在调用线程捕获 MDC
        Map<String, String> contextMap = MDC.getCopyOfContextMap();

        return () -> {
            // 在执行线程恢复 MDC
            if (contextMap != null) {
                MDC.setContextMap(contextMap);
            }
            try {
                // 执行原始逻辑
                return supplier.get();
            } finally {
                // 清理 MDC（重要！避免线程池污染）
                MDC.clear();
            }
        };
    }

    /**
     * 装饰 Runnable，自动传播 MDC。
     *
     * <p><b>使用示例</b>:
     * <pre>{@code
     * executor.submit(MDCUtil.withMDC(() -> {
     *     log.info("Processing task with traceId={}", MDC.get("traceId"));
     * }));
     * }</pre>
     *
     * @param runnable 原始 Runnable
     * @return 带 MDC 传播能力的 Runnable
     */
    public static Runnable withMDC(Runnable runnable) {
        // 在调用线程捕获 MDC
        Map<String, String> contextMap = MDC.getCopyOfContextMap();

        return () -> {
            // 在执行线程恢复 MDC
            if (contextMap != null) {
                MDC.setContextMap(contextMap);
            }
            try {
                // 执行原始逻辑
                runnable.run();
            } finally {
                // 清理 MDC
                MDC.clear();
            }
        };
    }

    /**
     * 装饰 Function，自动传播 MDC。
     *
     * <p><b>使用示例</b>:
     * <pre>{@code
     * CompletableFuture<String> future = CompletableFuture
     *     .supplyAsync(() -> "user-123")
     *     .thenApplyAsync(MDCUtil.withMDC(userId -> {
     *         log.info("Processing userId={}, traceId={}", userId, MDC.get("traceId"));
     *         return fetchUserData(userId);
     *     }));
     * }</pre>
     *
     * @param function 原始 Function
     * @param <T> 输入类型
     * @param <R> 返回类型
     * @return 带 MDC 传播能力的 Function
     */
    public static <T, R> Function<T, R> withMDC(Function<T, R> function) {
        // 在调用线程捕获 MDC
        Map<String, String> contextMap = MDC.getCopyOfContextMap();

        return input -> {
            // 在执行线程恢复 MDC
            if (contextMap != null) {
                MDC.setContextMap(contextMap);
            }
            try {
                // 执行原始逻辑
                return function.apply(input);
            } finally {
                // 清理 MDC
                MDC.clear();
            }
        };
    }
}
