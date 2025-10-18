package nan.tech.lab04.completablefuture.context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * MDC (Mapped Diagnostic Context) 穿透演示。
 *
 * <p><b>@教学目标</b>
 * <ul>
 *   <li>理解 MDC 在异步调用中的丢失问题</li>
 *   <li>掌握手动传递 MDC 的方法</li>
 *   <li>学习封装 MDC 装饰器工具类</li>
 *   <li>实现完整的异步链路 MDC 传递</li>
 * </ul>
 *
 * <p><b>@核心概念</b>
 * <ul>
 *   <li><b>MDC</b>: SLF4J 提供的线程本地上下文，用于在日志中添加额外信息（如 TraceId）</li>
 *   <li><b>ThreadLocal</b>: MDC 基于 ThreadLocal 实现，线程切换时丢失</li>
 *   <li><b>上下文传递</b>: 在异步调用前捕获 MDC，在异步线程中恢复</li>
 * </ul>
 *
 * <p><b>@业务场景</b>
 * <ul>
 *   <li>分布式追踪：通过 TraceId 关联同一请求的所有日志</li>
 *   <li>用户标识：记录当前操作的用户ID</li>
 *   <li>请求标识：记录每个HTTP请求的唯一ID</li>
 * </ul>
 *
 * <p><b>@陷阱</b>
 * <ul>
 *   <li><b>MDC 丢失</b>: CompletableFuture 异步执行时，MDC 不会自动传递到新线程</li>
 *   <li><b>内存泄漏</b>: 使用线程池时，必须在任务结束后清理 MDC，否则线程复用时会读取旧值</li>
 *   <li><b>传递时机</b>: 必须在异步调用前捕获 MDC，调用后捕获为时已晚</li>
 * </ul>
 *
 * <p><b>@参考</b>
 * <ul>
 *   <li>SLF4J MDC Documentation</li>
 *   <li>Distributed Tracing: Context Propagation</li>
 * </ul>
 *
 * @author Nan
 * @since 1.0.0
 */
public class MDCPropagationDemo {

    private static final Logger log = LoggerFactory.getLogger(MDCPropagationDemo.class);

    // 自定义线程池（用于演示）
    private static final Executor executor = Executors.newFixedThreadPool(2, r -> {
        Thread thread = new Thread(r);
        thread.setName("mdc-demo-" + thread.getId());
        return thread;
    });

    public static void main(String[] args) {
        log.info("=== MDC 穿透演示 ===\n");

        // 演示 1: MDC 基本用法
        demo1_BasicMDC();

        // 演示 2: 问题演示 - 异步调用中 MDC 丢失
        demo2_MDCLossProblem();

        // 演示 3: 解决方案 - 手动传递 MDC
        demo3_ManualMDCPropagation();

        // 演示 4: 封装 MDC 装饰器
        demo4_MDCDecorator();

        // 演示 5: 完整的异步链路 MDC 传递
        demo5_FullChainPropagation();

        log.info("\n=== 演示完成 ===");
    }

    /**
     * 演示 1: MDC 基本用法。
     *
     * <p><b>@教学</b>
     * <p>MDC 允许在日志中添加额外的上下文信息，常用于：
     * <ul>
     *   <li>TraceId: 分布式追踪的唯一标识</li>
     *   <li>UserId: 当前用户的ID</li>
     *   <li>RequestId: 当前请求的唯一标识</li>
     * </ul>
     */
    private static void demo1_BasicMDC() {
        log.info("\n--- 演示 1: MDC 基本用法 ---");

        // 设置 MDC
        MDC.put("traceId", "trace-12345");
        MDC.put("userId", "user-100");

        // 日志会自动包含 MDC 信息（需要在 logback.xml 中配置 %X{traceId}）
        log.info("业务操作1: 查询用户信息");
        log.info("业务操作2: 更新用户数据");

        // 清理 MDC（重要！）
        MDC.clear();
        log.info("清理 MDC 后的日志");
    }

    /**
     * 演示 2: 问题演示 - 异步调用中 MDC 丢失。
     *
     * <p><b>@教学</b>
     * <p>MDC 基于 ThreadLocal，线程切换时会丢失。
     * CompletableFuture 异步执行时，无法读取到主线程的 MDC。
     *
     * <p><b>@问题</b>
     * <ul>
     *   <li>主线程设置 MDC</li>
     *   <li>异步线程读取 MDC → 为空！</li>
     * </ul>
     */
    private static void demo2_MDCLossProblem() {
        log.info("\n--- 演示 2: MDC 丢失问题 ---");

        // 主线程设置 MDC
        MDC.put("traceId", "trace-problem");
        MDC.put("userId", "user-200");
        log.info("[主线程] 设置 MDC: traceId={}, userId={}", MDC.get("traceId"), MDC.get("userId"));

        // 异步执行
        CompletableFuture.runAsync(() -> {
            // 异步线程无法读取到主线程的 MDC
            String traceId = MDC.get("traceId");
            String userId = MDC.get("userId");
            log.warn("[异步线程] MDC 丢失: traceId={}, userId={}", traceId, userId);
        }, executor).join();

        // 清理
        MDC.clear();
    }

    /**
     * 演示 3: 解决方案 - 手动传递 MDC。
     *
     * <p><b>@教学</b>
     * <p>在异步调用前捕获 MDC，在异步线程中恢复。
     *
     * <p><b>@步骤</b>
     * <ol>
     *   <li>主线程捕获 MDC: {@code Map<String, String> contextMap = MDC.getCopyOfContextMap()}</li>
     *   <li>异步线程恢复 MDC: {@code MDC.setContextMap(contextMap)}</li>
     *   <li>异步线程清理 MDC: {@code MDC.clear()}</li>
     * </ol>
     */
    private static void demo3_ManualMDCPropagation() {
        log.info("\n--- 演示 3: 手动传递 MDC ---");

        // 主线程设置 MDC
        MDC.put("traceId", "trace-manual");
        MDC.put("userId", "user-300");
        log.info("[主线程] 设置 MDC: traceId={}, userId={}", MDC.get("traceId"), MDC.get("userId"));

        // 捕获 MDC 上下文
        Map<String, String> contextMap = MDC.getCopyOfContextMap();

        // 异步执行
        CompletableFuture.runAsync(() -> {
            try {
                // 恢复 MDC
                if (contextMap != null) {
                    MDC.setContextMap(contextMap);
                }

                // 现在可以读取到 MDC
                String traceId = MDC.get("traceId");
                String userId = MDC.get("userId");
                log.info("[异步线程] MDC 恢复成功: traceId={}, userId={}", traceId, userId);

            } finally {
                // 清理 MDC（重要！避免线程复用时的污染）
                MDC.clear();
            }
        }, executor).join();

        // 清理主线程 MDC
        MDC.clear();
    }

    /**
     * 演示 4: 封装 MDC 装饰器。
     *
     * <p><b>@教学</b>
     * <p>将 MDC 传递逻辑封装成工具类，简化使用。
     *
     * <p><b>@优点</b>
     * <ul>
     *   <li>代码复用：避免每次手动捕获和恢复 MDC</li>
     *   <li>自动清理：确保 MDC 正确清理，避免内存泄漏</li>
     *   <li>可读性：业务代码更简洁</li>
     * </ul>
     */
    private static void demo4_MDCDecorator() {
        log.info("\n--- 演示 4: MDC 装饰器 ---");

        // 主线程设置 MDC
        MDC.put("traceId", "trace-decorator");
        MDC.put("userId", "user-400");
        log.info("[主线程] 设置 MDC: traceId={}, userId={}", MDC.get("traceId"), MDC.get("userId"));

        // 使用 MDC 装饰器（自动传递和清理）
        CompletableFuture<String> future = CompletableFuture.supplyAsync(
                withMDC(() -> {
                    log.info("[异步线程] 使用装饰器，MDC 自动传递: traceId={}, userId={}",
                            MDC.get("traceId"), MDC.get("userId"));
                    return "Success";
                }),
                executor
        );

        log.info("✅ 结果: {}", future.join());

        // 清理主线程 MDC
        MDC.clear();
    }

    /**
     * 演示 5: 完整的异步链路 MDC 传递。
     *
     * <p><b>@教学</b>
     * <p>在多级异步链式调用中，确保每一步都能读取到 MDC。
     *
     * <p><b>@挑战</b>
     * <ul>
     *   <li>链式调用的每一步可能在不同线程</li>
     *   <li>需要在每一步都恢复和传递 MDC</li>
     * </ul>
     */
    private static void demo5_FullChainPropagation() {
        log.info("\n--- 演示 5: 完整链路 MDC 传递 ---");

        // 主线程设置 MDC
        MDC.put("traceId", "trace-chain");
        MDC.put("userId", "user-500");
        log.info("[主线程] 开始链式调用: traceId={}, userId={}", MDC.get("traceId"), MDC.get("userId"));

        CompletableFuture<String> result = CompletableFuture
                // 步骤 1: 异步获取用户信息
                .supplyAsync(withMDC(() -> {
                    log.info("[步骤1] 获取用户信息: traceId={}, userId={}",
                            MDC.get("traceId"), MDC.get("userId"));
                    return "User-" + MDC.get("userId");
                }), executor)
                // 步骤 2: 异步获取订单
                .thenApplyAsync(withMDC(user -> {
                    log.info("[步骤2] 获取订单: 用户={}, traceId={}", user, MDC.get("traceId"));
                    return user + "-Order";
                }), executor)
                // 步骤 3: 异步聚合结果
                .thenApplyAsync(withMDC(order -> {
                    log.info("[步骤3] 聚合结果: 订单={}, traceId={}", order, MDC.get("traceId"));
                    return "[聚合] " + order;
                }), executor);

        log.info("✅ 最终结果: {}", result.join());

        // 清理主线程 MDC
        MDC.clear();
    }

    // ======================== MDC 装饰器工具 ========================

    /**
     * MDC 装饰器：为 Supplier 添加 MDC 传递能力。
     *
     * <p><b>@原理</b>
     * <ol>
     *   <li>在调用线程捕获 MDC</li>
     *   <li>在执行线程恢复 MDC</li>
     *   <li>执行业务逻辑</li>
     *   <li>清理 MDC</li>
     * </ol>
     *
     * @param supplier 原始 Supplier
     * @param <T> 返回值类型
     * @return 带 MDC 传递能力的 Supplier
     */
    private static <T> Supplier<T> withMDC(Supplier<T> supplier) {
        // 在调用线程捕获 MDC
        Map<String, String> contextMap = MDC.getCopyOfContextMap();

        return () -> {
            // 恢复 MDC
            if (contextMap != null) {
                MDC.setContextMap(contextMap);
            }

            try {
                // 执行业务逻辑
                return supplier.get();
            } finally {
                // 清理 MDC（重要！）
                MDC.clear();
            }
        };
    }

    /**
     * MDC 装饰器：为 Function 添加 MDC 传递能力。
     *
     * @param function 原始 Function
     * @param <T> 输入类型
     * @param <R> 返回类型
     * @return 带 MDC 传递能力的 Function
     */
    private static <T, R> java.util.function.Function<T, R> withMDC(java.util.function.Function<T, R> function) {
        // 在调用线程捕获 MDC
        Map<String, String> contextMap = MDC.getCopyOfContextMap();

        return input -> {
            // 恢复 MDC
            if (contextMap != null) {
                MDC.setContextMap(contextMap);
            }

            try {
                // 执行业务逻辑
                return function.apply(input);
            } finally {
                // 清理 MDC
                MDC.clear();
            }
        };
    }
}
