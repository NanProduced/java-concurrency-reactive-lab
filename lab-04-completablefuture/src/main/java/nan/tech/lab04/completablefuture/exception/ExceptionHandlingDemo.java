package nan.tech.lab04.completablefuture.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * CompletableFuture 异常处理链演示。
 *
 * <p><b>@教学目标</b>
 * <ul>
 *   <li>掌握 CompletableFuture 的三种异常处理方法</li>
 *   <li>理解异常在链式调用中的传播机制</li>
 *   <li>学习异常恢复和降级策略</li>
 *   <li>掌握 CompletionException 的包装和解包</li>
 * </ul>
 *
 * <p><b>@核心方法</b>
 * <ul>
 *   <li><b>exceptionally</b>: 异常恢复，返回默认值（类似 catch + return）</li>
 *   <li><b>handle</b>: 统一处理成功和失败（类似 finally + return）</li>
 *   <li><b>whenComplete</b>: 执行后置操作，不改变结果（类似 finally）</li>
 * </ul>
 *
 * <p><b>@异常传播规则</b>
 * <ul>
 *   <li>如果链中某一步抛出异常，后续的 thenApply/thenCompose 不会执行</li>
 *   <li>异常会被包装成 CompletionException 传播到链的末尾</li>
 *   <li>exceptionally 可以捕获并恢复异常，继续执行后续步骤</li>
 * </ul>
 *
 * <p><b>@陷阱</b>
 * <ul>
 *   <li><b>CompletionException 包装</b>: 原始异常被包装，需要用 getCause() 获取</li>
 *   <li><b>异常吞没</b>: whenComplete 如果不重新抛出异常，会吞没异常</li>
 *   <li><b>多层嵌套</b>: 多次包装可能导致异常难以定位</li>
 * </ul>
 *
 * <p><b>@参考</b>
 * <ul>
 *   <li>Java 8 in Action: CompletableFuture 异常处理</li>
 *   <li>Effective Java: 异常处理最佳实践</li>
 * </ul>
 *
 * @author Nan
 * @since 1.0.0
 */
public class ExceptionHandlingDemo {

    private static final Logger log = LoggerFactory.getLogger(ExceptionHandlingDemo.class);

    public static void main(String[] args) {
        log.info("=== CompletableFuture 异常处理链演示 ===\n");

        // 演示 1: exceptionally - 异常恢复
        demo1_Exceptionally();

        // 演示 2: handle - 统一处理成功和失败
        demo2_Handle();

        // 演示 3: whenComplete - 后置操作（不改变结果）
        demo3_WhenComplete();

        // 演示 4: 异常传播链
        demo4_ExceptionPropagation();

        // 演示 5: 多层异常处理
        demo5_MultiLevelExceptionHandling();

        // 演示 6: CompletionException 包装和解包
        demo6_CompletionException();

        log.info("\n=== 演示完成 ===");
    }

    /**
     * 演示 1: exceptionally - 异常恢复。
     *
     * <p><b>@教学</b>
     * <p>exceptionally 捕获异常并返回默认值，类似 try-catch 中的 catch 分支。
     *
     * <p><b>@使用场景</b>
     * <ul>
     *   <li>服务降级：调用失败时返回默认值</li>
     *   <li>容错处理：防止整个链路因单个步骤失败而中断</li>
     * </ul>
     */
    private static void demo1_Exceptionally() {
        log.info("\n--- 演示 1: exceptionally（异常恢复） ---");

        // 场景 1: 成功的情况（不触发 exceptionally）
        CompletableFuture<String> successFuture = CompletableFuture
                .supplyAsync(() -> {
                    log.info("  [成功场景] 正常执行");
                    return "Success";
                })
                .exceptionally(ex -> {
                    log.warn("  [成功场景] 异常恢复: {}", ex.getMessage());
                    return "Default Value";
                });

        log.info("✅ 成功场景结果: {}", successFuture.join());

        // 场景 2: 失败的情况（触发 exceptionally）
        CompletableFuture<String> failureFuture = CompletableFuture
                .<String>supplyAsync(() -> {
                    log.info("  [失败场景] 抛出异常");
                    throw new RuntimeException("业务异常");
                })
                .exceptionally(ex -> {
                    log.warn("  [失败场景] 异常恢复: {}", ex.getMessage());
                    return "Default Value";  // 返回默认值
                });

        log.info("✅ 失败场景结果: {}", failureFuture.join());
    }

    /**
     * 演示 2: handle - 统一处理成功和失败。
     *
     * <p><b>@教学</b>
     * <p>handle 接收两个参数：结果和异常，无论成功还是失败都会执行。
     *
     * <p><b>@对比</b>
     * <ul>
     *   <li><b>exceptionally</b>: 只处理异常</li>
     *   <li><b>handle</b>: 同时处理成功和异常</li>
     * </ul>
     *
     * <p><b>@使用场景</b>
     * <ul>
     *   <li>统一的返回格式封装（如 Result&lt;T&gt;）</li>
     *   <li>需要根据成功/失败做不同处理</li>
     * </ul>
     */
    private static void demo2_Handle() {
        log.info("\n--- 演示 2: handle（统一处理成功和失败） ---");

        // 成功的情况
        CompletableFuture<String> successFuture = CompletableFuture
                .supplyAsync(() -> {
                    log.info("  [handle-成功] 正常执行");
                    return "Success";
                })
                .handle((result, ex) -> {
                    if (ex != null) {
                        log.warn("  [handle-成功] 发生异常: {}", ex.getMessage());
                        return "Error: " + ex.getMessage();
                    } else {
                        log.info("  [handle-成功] 成功处理: {}", result);
                        return "Processed: " + result;
                    }
                });

        log.info("✅ handle 成功结果: {}", successFuture.join());

        // 失败的情况
        CompletableFuture<String> failureFuture = CompletableFuture
                .supplyAsync(() -> {
                    log.info("  [handle-失败] 抛出异常");
                    throw new RuntimeException("业务异常");
                })
                .handle((result, ex) -> {
                    if (ex != null) {
                        log.warn("  [handle-失败] 发生异常: {}", ex.getMessage());
                        return "Error: " + ex.getMessage();
                    } else {
                        log.info("  [handle-失败] 成功处理: {}", result);
                        return "Processed: " + result;
                    }
                });

        log.info("✅ handle 失败结果: {}", failureFuture.join());
    }

    /**
     * 演示 3: whenComplete - 后置操作（不改变结果）。
     *
     * <p><b>@教学</b>
     * <p>whenComplete 类似 finally，无论成功还是失败都执行，但不会改变结果。
     *
     * <p><b>@对比</b>
     * <ul>
     *   <li><b>handle</b>: 可以修改结果或异常</li>
     *   <li><b>whenComplete</b>: 不修改结果，只做记录、清理等操作</li>
     * </ul>
     *
     * <p><b>@使用场景</b>
     * <ul>
     *   <li>日志记录</li>
     *   <li>资源清理</li>
     *   <li>性能监控</li>
     * </ul>
     */
    private static void demo3_WhenComplete() {
        log.info("\n--- 演示 3: whenComplete（后置操作） ---");

        CompletableFuture<String> future = CompletableFuture
                .supplyAsync(() -> {
                    log.info("  [whenComplete] 执行业务逻辑");
                    return "Business Result";
                })
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("  [whenComplete] 记录异常: {}", ex.getMessage());
                    } else {
                        log.info("  [whenComplete] 记录成功: {}", result);
                    }
                    // 注意：这里不能修改 result 或抛出新异常
                });

        log.info("✅ whenComplete 结果: {}", future.join());
    }

    /**
     * 演示 4: 异常传播链。
     *
     * <p><b>@教学</b>
     * <p>如果链中某一步抛出异常，后续的 thenApply/thenCompose 不会执行，
     * 异常会直接传播到最近的异常处理方法（exceptionally/handle）。
     *
     * <p><b>@传播规则</b>
     * <ul>
     *   <li>步骤1异常 → 步骤2跳过 → 步骤3跳过 → exceptionally 捕获</li>
     *   <li>步骤2异常 → 步骤3跳过 → exceptionally 捕获</li>
     * </ul>
     */
    private static void demo4_ExceptionPropagation() {
        log.info("\n--- 演示 4: 异常传播链 ---");

        CompletableFuture<String> future = CompletableFuture
                .supplyAsync(() -> {
                    log.info("  [步骤1] 执行成功");
                    return "Step1";
                })
                .thenApply(s -> {
                    log.info("  [步骤2] 抛出异常");
                    throw new RuntimeException("步骤2异常");
                })
                .thenApply(s -> {
                    log.info("  [步骤3] 这里不会执行");
                    return s + "-Step3";
                })
                .exceptionally(ex -> {
                    log.warn("  [异常处理] 捕获异常: {}", ex.getMessage());
                    return "Recovered";
                });

        log.info("✅ 异常传播结果: {}", future.join());
    }

    /**
     * 演示 5: 多层异常处理。
     *
     * <p><b>@教学</b>
     * <p>可以在链中多次使用 exceptionally，每层处理不同类型的异常。
     *
     * <p><b>@模式</b>
     * <ul>
     *   <li>第一层：业务异常恢复</li>
     *   <li>第二层：系统异常降级</li>
     *   <li>第三层：兜底处理</li>
     * </ul>
     */
    private static void demo5_MultiLevelExceptionHandling() {
        log.info("\n--- 演示 5: 多层异常处理 ---");

        CompletableFuture<String> future = CompletableFuture
                .<String>supplyAsync(() -> {
                    log.info("  [业务层] 调用外部服务");
                    throw new IllegalArgumentException("参数错误");
                })
                .exceptionally(ex -> {
                    // 第一层：业务异常处理
                    if (ex instanceof IllegalArgumentException) {
                        log.warn("  [第一层] 业务异常恢复: {}", ex.getMessage());
                        return "Business Default";
                    }
                    throw new RuntimeException(ex);  // 继续传播
                })
                .thenApply(s -> {
                    log.info("  [转换层] 数据转换: {}", s);
                    return s.toUpperCase();
                })
                .exceptionally(ex -> {
                    // 第二层：系统异常处理
                    log.error("  [第二层] 系统异常降级: {}", ex.getMessage());
                    return "System Default";
                })
                .handle((result, ex) -> {
                    // 第三层：兜底处理
                    if (ex != null) {
                        log.error("  [第三层] 兜底处理: {}", ex.getMessage());
                        return "Final Fallback";
                    }
                    return result;
                });

        log.info("✅ 多层异常处理结果: {}", future.join());
    }

    /**
     * 演示 6: CompletionException 包装和解包。
     *
     * <p><b>@教学</b>
     * <p>CompletableFuture 会将原始异常包装成 CompletionException，
     * 需要使用 getCause() 获取原始异常。
     *
     * <p><b>@陷阱</b>
     * <ul>
     *   <li>直接使用 ex.getMessage() 可能获取不到原始异常信息</li>
     *   <li>多层嵌套时需要递归解包</li>
     * </ul>
     */
    private static void demo6_CompletionException() {
        log.info("\n--- 演示 6: CompletionException 包装和解包 ---");

        CompletableFuture<String> future = CompletableFuture
                .supplyAsync(() -> {
                    throw new IllegalStateException("原始业务异常");
                });

        try {
            future.join();
        } catch (CompletionException ex) {
            log.error("❌ 捕获 CompletionException: {}", ex.getClass().getSimpleName());
            log.error("❌ 包装异常消息: {}", ex.getMessage());

            // 解包获取原始异常
            Throwable cause = ex.getCause();
            log.error("✅ 原始异常类型: {}", cause.getClass().getSimpleName());
            log.error("✅ 原始异常消息: {}", cause.getMessage());
        }

        // 在 exceptionally 中直接获取原始异常
        CompletableFuture<String> unwrappedFuture = CompletableFuture
                .<String>supplyAsync(() -> {
                    throw new IllegalStateException("原始业务异常");
                })
                .exceptionally(ex -> {
                    // ex 已经是原始异常（Throwable），不是 CompletionException
                    log.info("✅ exceptionally 中的异常类型: {}", ex.getClass().getSimpleName());
                    log.info("✅ exceptionally 中的异常消息: {}", ex.getMessage());
                    return "Recovered";
                });

        log.info("✅ 异常解包结果: {}", unwrappedFuture.join());
    }
}
