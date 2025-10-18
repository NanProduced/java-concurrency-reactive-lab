package nan.tech.lab04.completablefuture.timeout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * CompletableFuture 超时与取消演示。
 *
 * <p><b>@教学目标</b>
 * <ul>
 *   <li>掌握 Java 9+ 的超时 API（orTimeout, completeOnTimeout）</li>
 *   <li>理解手动取消机制和状态查询</li>
 *   <li>学习超时后的资源清理</li>
 *   <li>掌握超时降级策略</li>
 * </ul>
 *
 * <p><b>@核心方法（Java 9+）</b>
 * <ul>
 *   <li><b>orTimeout(timeout, unit)</b>: 超时后抛出 TimeoutException</li>
 *   <li><b>completeOnTimeout(value, timeout, unit)</b>: 超时后返回默认值</li>
 *   <li><b>cancel(mayInterruptIfRunning)</b>: 手动取消任务</li>
 *   <li><b>isCancelled()</b>: 查询是否被取消</li>
 *   <li><b>isDone()</b>: 查询是否完成（成功/失败/取消）</li>
 *   <li><b>isCompletedExceptionally()</b>: 查询是否异常完成</li>
 * </ul>
 *
 * <p><b>@陷阱</b>
 * <ul>
 *   <li><b>取消不会中断线程</b>: cancel() 不会真正中断正在执行的任务</li>
 *   <li><b>资源泄漏</b>: 超时后任务可能仍在后台执行，需要手动清理资源</li>
 *   <li><b>超时时间设置</b>: 过短导致误杀，过长影响用户体验</li>
 * </ul>
 *
 * <p><b>@参考</b>
 * <ul>
 *   <li>Java 9 CompletableFuture Enhancements</li>
 *   <li>Reactive Programming: Timeout Strategies</li>
 * </ul>
 *
 * @author Nan
 * @since 1.0.0
 */
public class TimeoutCancellationDemo {

    private static final Logger log = LoggerFactory.getLogger(TimeoutCancellationDemo.class);

    public static void main(String[] args) throws Exception {
        log.info("=== CompletableFuture 超时与取消演示 ===\n");

        // 演示 1: orTimeout - 超时抛出异常
        demo1_OrTimeout();

        // 演示 2: completeOnTimeout - 超时返回默认值
        demo2_CompleteOnTimeout();

        // 演示 3: 手动取消任务
        demo3_ManualCancellation();

        // 演示 4: 状态查询
        demo4_StatusQueries();

        // 演示 5: 超时后的资源清理
        demo5_ResourceCleanup();

        // 演示 6: 超时降级策略
        demo6_TimeoutFallback();

        log.info("\n=== 演示完成 ===");
    }

    /**
     * 演示 1: orTimeout - 超时抛出异常。
     *
     * <p><b>@教学</b>
     * <p>orTimeout 在指定时间后抛出 TimeoutException，
     * 类似于 Future.get(timeout, unit)，但不会阻塞主线程。
     *
     * <p><b>@使用场景</b>
     * <ul>
     *   <li>严格的超时要求，超时即失败</li>
     *   <li>需要快速感知超时并进行告警</li>
     * </ul>
     */
    private static void demo1_OrTimeout() {
        log.info("\n--- 演示 1: orTimeout（超时抛出异常） ---");

        // 场景 1: 任务在超时前完成
        try {
            CompletableFuture<String> fastTask = CompletableFuture
                    .supplyAsync(() -> {
                        log.info("  [快速任务] 执行中...");
                        sleep(100);  // 100ms 完成
                        log.info("  [快速任务] 执行完成");
                        return "Fast Result";
                    })
                    .orTimeout(200, TimeUnit.MILLISECONDS);  // 超时 200ms

            log.info("✅ 快速任务结果: {}", fastTask.join());

        } catch (Exception e) {
            log.error("❌ 快速任务超时: {}", e.getMessage());
        }

        // 场景 2: 任务超时
        try {
            CompletableFuture<String> slowTask = CompletableFuture
                    .supplyAsync(() -> {
                        log.info("  [慢速任务] 执行中...");
                        sleep(300);  // 300ms 完成
                        log.info("  [慢速任务] 执行完成");
                        return "Slow Result";
                    })
                    .orTimeout(100, TimeUnit.MILLISECONDS);  // 超时 100ms

            log.info("结果: {}", slowTask.join());

        } catch (Exception e) {
            log.error("❌ 慢速任务超时: {} ({})", e.getClass().getSimpleName(), e.getCause().getMessage());
        }
    }

    /**
     * 演示 2: completeOnTimeout - 超时返回默认值。
     *
     * <p><b>@教学</b>
     * <p>completeOnTimeout 在超时后返回默认值，不抛出异常。
     * 类似于带超时的 exceptionally。
     *
     * <p><b>@使用场景</b>
     * <ul>
     *   <li>服务降级：超时后返回缓存或默认值</li>
     *   <li>用户体验优先：宁愿显示默认内容也不让用户等待</li>
     * </ul>
     *
     * <p><b>@对比</b>
     * <ul>
     *   <li><b>orTimeout</b>: 超时 → 抛出异常 → 需要 exceptionally 处理</li>
     *   <li><b>completeOnTimeout</b>: 超时 → 直接返回默认值</li>
     * </ul>
     */
    private static void demo2_CompleteOnTimeout() {
        log.info("\n--- 演示 2: completeOnTimeout（超时返回默认值） ---");

        // 任务超时，返回默认值
        CompletableFuture<String> taskWithFallback = CompletableFuture
                .supplyAsync(() -> {
                    log.info("  [降级任务] 执行中...");
                    sleep(300);  // 300ms 完成
                    log.info("  [降级任务] 执行完成");
                    return "Real Result";
                })
                .completeOnTimeout("Default Result", 100, TimeUnit.MILLISECONDS);  // 超时返回默认值

        String result = taskWithFallback.join();
        if ("Default Result".equals(result)) {
            log.warn("⚠️ 任务超时，使用默认值: {}", result);
        } else {
            log.info("✅ 任务正常完成: {}", result);
        }
    }

    /**
     * 演示 3: 手动取消任务。
     *
     * <p><b>@教学</b>
     * <p>cancel(mayInterruptIfRunning) 手动取消任务。
     * 注意：取消不会中断正在执行的任务，只是标记为取消状态。
     *
     * <p><b>@参数说明</b>
     * <ul>
     *   <li><b>true</b>: 尝试中断线程（CompletableFuture 忽略此参数）</li>
     *   <li><b>false</b>: 仅标记为取消</li>
     * </ul>
     *
     * <p><b>@使用场景</b>
     * <ul>
     *   <li>用户主动取消操作</li>
     *   <li>依赖任务失败，取消后续任务</li>
     * </ul>
     */
    private static void demo3_ManualCancellation() {
        log.info("\n--- 演示 3: 手动取消任务 ---");

        CompletableFuture<String> future = CompletableFuture
                .supplyAsync(() -> {
                    log.info("  [可取消任务] 开始执行...");
                    for (int i = 0; i < 10; i++) {
                        sleep(100);
                        log.info("  [可取消任务] 进度: {}%", (i + 1) * 10);

                        // 检查是否被取消（推荐做法）
                        if (Thread.currentThread().isInterrupted()) {
                            log.warn("  [可取消任务] 检测到取消信号，提前退出");
                            return "Cancelled Early";
                        }
                    }
                    return "Completed";
                });

        // 等待 300ms 后取消
        sleep(300);
        boolean cancelled = future.cancel(true);
        log.info("取消操作: {}", cancelled ? "成功" : "失败");

        try {
            String result = future.join();
            log.info("任务结果: {}", result);
        } catch (Exception e) {
            log.error("❌ 任务被取消: {}", e.getClass().getSimpleName());
        }
    }

    /**
     * 演示 4: 状态查询。
     *
     * <p><b>@教学</b>
     * <p>CompletableFuture 提供多个状态查询方法：
     * <ul>
     *   <li><b>isDone()</b>: 是否完成（包括成功、失败、取消）</li>
     *   <li><b>isCancelled()</b>: 是否被取消</li>
     *   <li><b>isCompletedExceptionally()</b>: 是否异常完成（包括取消）</li>
     * </ul>
     */
    private static void demo4_StatusQueries() {
        log.info("\n--- 演示 4: 状态查询 ---");

        // 状态 1: 正常完成
        CompletableFuture<String> normalFuture = CompletableFuture.completedFuture("Done");
        log.info("正常完成: isDone={}, isCancelled={}, isCompletedExceptionally={}",
                normalFuture.isDone(), normalFuture.isCancelled(), normalFuture.isCompletedExceptionally());

        // 状态 2: 异常完成
        CompletableFuture<String> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Error"));
        log.info("异常完成: isDone={}, isCancelled={}, isCompletedExceptionally={}",
                failedFuture.isDone(), failedFuture.isCancelled(), failedFuture.isCompletedExceptionally());

        // 状态 3: 取消
        CompletableFuture<String> cancelledFuture = new CompletableFuture<>();
        cancelledFuture.cancel(false);
        log.info("取消状态: isDone={}, isCancelled={}, isCompletedExceptionally={}",
                cancelledFuture.isDone(), cancelledFuture.isCancelled(), cancelledFuture.isCompletedExceptionally());
    }

    /**
     * 演示 5: 超时后的资源清理。
     *
     * <p><b>@教学</b>
     * <p>超时后，任务可能仍在后台执行，需要手动清理资源。
     *
     * <p><b>@最佳实践</b>
     * <ul>
     *   <li>使用 whenComplete 进行资源清理</li>
     *   <li>在任务中定期检查 isCancelled 状态</li>
     *   <li>使用 try-with-resources 管理外部资源</li>
     * </ul>
     */
    private static void demo5_ResourceCleanup() {
        log.info("\n--- 演示 5: 超时后的资源清理 ---");

        CompletableFuture<String> future = CompletableFuture
                .supplyAsync(() -> {
                    log.info("  [资源任务] 开始执行，占用资源");
                    sleep(300);  // 模拟长时间任务
                    log.info("  [资源任务] 执行完成，需要释放资源");
                    return "Result";
                })
                .orTimeout(100, TimeUnit.MILLISECONDS)
                .whenComplete((result, ex) -> {
                    // 无论成功、失败还是超时，都会执行清理
                    if (ex instanceof TimeoutException) {
                        log.warn("  [资源清理] 任务超时，清理资源");
                    } else if (ex != null) {
                        log.error("  [资源清理] 任务异常，清理资源: {}", ex.getMessage());
                    } else {
                        log.info("  [资源清理] 任务成功，清理资源");
                    }
                    // 这里执行实际的资源清理操作
                });

        try {
            future.join();
        } catch (Exception e) {
            log.error("❌ 任务异常: {}", e.getCause().getClass().getSimpleName());
        }

        // 等待资源清理完成
        sleep(400);
    }

    /**
     * 演示 6: 超时降级策略。
     *
     * <p><b>@教学</b>
     * <p>综合使用 orTimeout + exceptionally 实现超时降级。
     *
     * <p><b>@降级策略</b>
     * <ul>
     *   <li>策略 1: 返回缓存数据</li>
     *   <li>策略 2: 返回默认值</li>
     *   <li>策略 3: 切换到备用服务</li>
     * </ul>
     */
    private static void demo6_TimeoutFallback() {
        log.info("\n--- 演示 6: 超时降级策略 ---");

        CompletableFuture<String> future = CompletableFuture
                .supplyAsync(() -> {
                    log.info("  [主服务] 调用中...");
                    sleep(300);
                    return "Primary Result";
                })
                .orTimeout(100, TimeUnit.MILLISECONDS)
                .exceptionally(ex -> {
                    if (ex.getCause() instanceof TimeoutException) {
                        log.warn("  [降级] 主服务超时，使用缓存数据");
                        return "Cached Result";
                    } else {
                        log.error("  [降级] 主服务异常，使用默认值");
                        return "Default Result";
                    }
                });

        String result = future.join();
        log.info("✅ 最终结果: {}", result);
    }

    /**
     * 辅助方法：睡眠指定毫秒数。
     */
    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
