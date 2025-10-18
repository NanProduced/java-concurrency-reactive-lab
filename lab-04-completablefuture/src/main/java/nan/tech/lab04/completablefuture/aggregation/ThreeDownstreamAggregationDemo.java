package nan.tech.lab04.completablefuture.aggregation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 三下游服务聚合演示（验收标准核心）。
 *
 * <p><b>@教学目标</b>
 * <ul>
 *   <li>掌握多个下游服务的并行调用和结果聚合</li>
 *   <li>理解 allOf 和 anyOf 的使用场景</li>
 *   <li>学习三种容错策略：全部成功、部分成功、快速失败</li>
 *   <li>掌握超时控制和异常恢复机制</li>
 * </ul>
 *
 * <p><b>@业务场景</b>
 * <p>电商首页需要聚合三个下游服务的数据：
 * <ul>
 *   <li><b>用户服务</b>: 获取用户基本信息（耗时 100ms）</li>
 *   <li><b>订单服务</b>: 获取用户最近订单（耗时 150ms）</li>
 *   <li><b>推荐服务</b>: 获取个性化推荐（耗时 200ms）</li>
 * </ul>
 *
 * <p><b>@核心方法</b>
 * <ul>
 *   <li><b>allOf</b>: 等待所有 CompletableFuture 完成（AND 语义）</li>
 *   <li><b>anyOf</b>: 等待任意一个 CompletableFuture 完成（OR 语义）</li>
 * </ul>
 *
 * <p><b>@容错策略</b>
 * <ul>
 *   <li><b>策略 1 - 全部成功</b>: 所有服务都成功才返回，任一失败则整体失败</li>
 *   <li><b>策略 2 - 部分成功</b>: 容忍部分服务失败，返回成功的结果</li>
 *   <li><b>策略 3 - 快速失败</b>: 任意服务失败立即返回错误，不等待其他服务</li>
 * </ul>
 *
 * <p><b>@陷阱</b>
 * <ul>
 *   <li><b>allOf 的返回值</b>: allOf 返回 CompletableFuture&lt;Void&gt;，需要手动获取每个 Future 的结果</li>
 *   <li><b>异常传播</b>: 一个 Future 异常不会影响其他 Future 的执行（除非使用 cancel）</li>
 *   <li><b>超时处理</b>: 需要显式设置超时，否则可能无限等待</li>
 * </ul>
 *
 * <p><b>@参考</b>
 * <ul>
 *   <li>Java 9+ CompletableFuture: orTimeout, completeOnTimeout</li>
 *   <li>微服务架构: 服务聚合与容错模式</li>
 * </ul>
 *
 * @author Nan
 * @since 1.0.0
 */
public class ThreeDownstreamAggregationDemo {

    private static final Logger log = LoggerFactory.getLogger(ThreeDownstreamAggregationDemo.class);

    public static void main(String[] args) {
        log.info("=== 三下游服务聚合演示 ===\n");

        // 演示 1: 串行调用 vs 并行调用（性能对比）
        demo1_SerialVsParallel();

        // 演示 2: allOf - 等待所有任务完成
        demo2_AllOf();

        // 演示 3: anyOf - 等待任意一个任务完成
        demo3_AnyOf();

        // 演示 4: 容错策略 1 - 全部成功
        demo4_AllOrNothing();

        // 演示 5: 容错策略 2 - 部分成功
        demo5_PartialSuccess();

        // 演示 6: 容错策略 3 - 快速失败
        demo6_FailFast();

        log.info("\n=== 演示完成 ===");
    }

    /**
     * 演示 1: 串行调用 vs 并行调用（性能对比）。
     *
     * <p><b>@性能分析</b>
     * <ul>
     *   <li>串行: 100ms + 150ms + 200ms = 450ms</li>
     *   <li>并行: max(100ms, 150ms, 200ms) = 200ms（提升 55%）</li>
     * </ul>
     */
    private static void demo1_SerialVsParallel() {
        log.info("\n--- 演示 1: 串行 vs 并行 ---");

        // ❌ 串行调用（阻塞）
        long start1 = System.currentTimeMillis();
        String user = callUserService(1);
        String order = callOrderService(1);
        String recommendation = callRecommendationService(1);
        long end1 = System.currentTimeMillis();
        log.info("❌ 串行调用: 用户={}, 订单={}, 推荐={}, 耗时={}ms",
                user, order, recommendation, (end1 - start1));

        // ✅ 并行调用（非阻塞）
        long start2 = System.currentTimeMillis();
        CompletableFuture<String> userFuture = CompletableFuture.supplyAsync(() -> callUserService(1));
        CompletableFuture<String> orderFuture = CompletableFuture.supplyAsync(() -> callOrderService(1));
        CompletableFuture<String> recFuture = CompletableFuture.supplyAsync(() -> callRecommendationService(1));

        // 等待所有任务完成
        CompletableFuture.allOf(userFuture, orderFuture, recFuture).join();

        long end2 = System.currentTimeMillis();
        log.info("✅ 并行调用: 用户={}, 订单={}, 推荐={}, 耗时={}ms",
                userFuture.join(), orderFuture.join(), recFuture.join(), (end2 - start2));
    }

    /**
     * 演示 2: allOf - 等待所有任务完成。
     *
     * <p><b>@教学</b>
     * <p>allOf 返回一个 CompletableFuture&lt;Void&gt;，表示所有任务都完成了。
     * 但它不会返回结果，需要手动从每个 Future 获取结果。
     *
     * <p><b>@使用场景</b>
     * <ul>
     *   <li>批量任务执行，需要等待全部完成</li>
     *   <li>数据聚合，所有数据源都必须成功</li>
     * </ul>
     */
    private static void demo2_AllOf() {
        log.info("\n--- 演示 2: allOf（等待所有任务完成） ---");

        // 创建三个异步任务
        CompletableFuture<String> future1 = CompletableFuture.supplyAsync(() -> {
            log.info("  [任务1] 开始执行...");
            sleep(100);
            log.info("  [任务1] 执行完成");
            return "Result-1";
        });

        CompletableFuture<String> future2 = CompletableFuture.supplyAsync(() -> {
            log.info("  [任务2] 开始执行...");
            sleep(150);
            log.info("  [任务2] 执行完成");
            return "Result-2";
        });

        CompletableFuture<String> future3 = CompletableFuture.supplyAsync(() -> {
            log.info("  [任务3] 开始执行...");
            sleep(200);
            log.info("  [任务3] 执行完成");
            return "Result-3";
        });

        // allOf: 等待所有任务完成
        CompletableFuture<Void> allOfFuture = CompletableFuture.allOf(future1, future2, future3);

        // 阻塞等待所有任务完成
        allOfFuture.join();
        log.info("✅ 所有任务已完成");

        // 手动获取每个任务的结果
        log.info("结果1: {}, 结果2: {}, 结果3: {}", future1.join(), future2.join(), future3.join());
    }

    /**
     * 演示 3: anyOf - 等待任意一个任务完成。
     *
     * <p><b>@教学</b>
     * <p>anyOf 返回第一个完成的 CompletableFuture 的结果（无论成功还是失败）。
     *
     * <p><b>@使用场景</b>
     * <ul>
     *   <li>多数据源查询，返回最快的结果</li>
     *   <li>降级策略，优先使用快速服务</li>
     *   <li>超时控制，与定时器配合使用</li>
     * </ul>
     */
    private static void demo3_AnyOf() {
        log.info("\n--- 演示 3: anyOf（等待任意一个任务完成） ---");

        // 模拟三个数据源，速度不同
        CompletableFuture<String> slowDb = CompletableFuture.supplyAsync(() -> {
            log.info("  [慢速数据库] 查询中...");
            sleep(300);
            return "慢速DB结果";
        });

        CompletableFuture<String> fastDb = CompletableFuture.supplyAsync(() -> {
            log.info("  [快速数据库] 查询中...");
            sleep(100);
            return "快速DB结果";
        });

        CompletableFuture<String> cache = CompletableFuture.supplyAsync(() -> {
            log.info("  [缓存] 查询中...");
            sleep(50);
            return "缓存结果";
        });

        // anyOf: 返回最快的结果
        CompletableFuture<Object> anyOfFuture = CompletableFuture.anyOf(slowDb, fastDb, cache);

        Object fastestResult = anyOfFuture.join();
        log.info("✅ 最快的结果: {}", fastestResult);

        // 注意：其他任务仍在后台执行，除非手动取消
    }

    /**
     * 演示 4: 容错策略 1 - 全部成功（All or Nothing）。
     *
     * <p><b>@策略说明</b>
     * <p>所有下游服务都成功才返回结果，任一服务失败则整体失败。
     *
     * <p><b>@适用场景</b>
     * <ul>
     *   <li>关键业务场景，数据完整性要求高</li>
     *   <li>事务性操作，部分成功无意义</li>
     * </ul>
     *
     * <p><b>@缺点</b>
     * <p>可用性低，任一服务故障导致整体不可用。
     */
    private static void demo4_AllOrNothing() {
        log.info("\n--- 演示 4: 容错策略 1 - 全部成功 ---");

        // 场景：所有服务正常
        try {
            CompletableFuture<String> userFuture = CompletableFuture.supplyAsync(() -> callUserService(1));
            CompletableFuture<String> orderFuture = CompletableFuture.supplyAsync(() -> callOrderService(1));
            CompletableFuture<String> recFuture = CompletableFuture.supplyAsync(() -> callRecommendationService(1));

            // allOf: 等待所有任务完成
            CompletableFuture<Void> allOfFuture = CompletableFuture.allOf(userFuture, orderFuture, recFuture);
            allOfFuture.join();

            // 所有任务成功，聚合结果
            String aggregatedResult = String.format("用户: %s, 订单: %s, 推荐: %s",
                    userFuture.join(), orderFuture.join(), recFuture.join());
            log.info("✅ [全部成功] 聚合结果: {}", aggregatedResult);

        } catch (Exception e) {
            log.error("❌ [全部成功策略] 有服务失败，整体失败: {}", e.getMessage());
        }

        // 场景：有服务失败
        try {
            CompletableFuture<String> userFuture = CompletableFuture.supplyAsync(() -> callUserService(1));
            CompletableFuture<String> orderFuture = CompletableFuture.supplyAsync(() -> {
                throw new RuntimeException("订单服务故障");  // 模拟失败
            });
            CompletableFuture<String> recFuture = CompletableFuture.supplyAsync(() -> callRecommendationService(1));

            CompletableFuture.allOf(userFuture, orderFuture, recFuture).join();

        } catch (Exception e) {
            log.error("❌ [全部成功策略] 订单服务失败，整体失败: {}", e.getCause().getMessage());
        }
    }

    /**
     * 演示 5: 容错策略 2 - 部分成功（Partial Success）。
     *
     * <p><b>@策略说明</b>
     * <p>容忍部分服务失败，返回成功的结果，失败的服务返回默认值。
     *
     * <p><b>@适用场景</b>
     * <ul>
     *   <li>展示类场景，部分数据缺失可接受</li>
     *   <li>推荐系统，个别推荐源失败不影响整体</li>
     * </ul>
     *
     * <p><b>@优点</b>
     * <p>可用性高，部分服务故障不影响整体功能。
     */
    private static void demo5_PartialSuccess() {
        log.info("\n--- 演示 5: 容错策略 2 - 部分成功 ---");

        // 三个服务并行调用，其中订单服务会失败
        CompletableFuture<String> userFuture = CompletableFuture
                .supplyAsync(() -> callUserService(1))
                .exceptionally(ex -> {
                    log.warn("用户服务失败，使用默认值: {}", ex.getMessage());
                    return "默认用户";
                });

        CompletableFuture<String> orderFuture = CompletableFuture
                .<String>supplyAsync(() -> {
                    throw new RuntimeException("订单服务故障");  // 模拟失败
                })
                .exceptionally(ex -> {
                    log.warn("订单服务失败，使用默认值: {}", ex.getMessage());
                    return "无订单";
                });

        CompletableFuture<String> recFuture = CompletableFuture
                .supplyAsync(() -> callRecommendationService(1))
                .exceptionally(ex -> {
                    log.warn("推荐服务失败，使用默认值: {}", ex.getMessage());
                    return "默认推荐";
                });

        // 等待所有任务完成（包括失败的）
        CompletableFuture.allOf(userFuture, orderFuture, recFuture).join();

        // 聚合结果（成功 + 默认值）
        String aggregatedResult = String.format("用户: %s, 订单: %s, 推荐: %s",
                userFuture.join(), orderFuture.join(), recFuture.join());
        log.info("✅ [部分成功] 聚合结果: {}", aggregatedResult);
    }

    /**
     * 演示 6: 容错策略 3 - 快速失败（Fail Fast）。
     *
     * <p><b>@策略说明</b>
     * <p>任意服务失败立即返回错误，不等待其他服务完成。
     *
     * <p><b>@适用场景</b>
     * <ul>
     *   <li>实时性要求高，失败需要快速感知</li>
     *   <li>降低资源消耗，避免无用等待</li>
     * </ul>
     *
     * <p><b>@实现方式</b>
     * <p>使用 anyOf + 异常检测，任一任务失败立即抛出异常。
     */
    private static void demo6_FailFast() {
        log.info("\n--- 演示 6: 容错策略 3 - 快速失败 ---");

        CompletableFuture<String> userFuture = CompletableFuture.supplyAsync(() -> {
            sleep(100);
            return callUserService(1);
        });

        CompletableFuture<String> orderFuture = CompletableFuture.supplyAsync(() -> {
            sleep(50);  // 比用户服务更快失败
            throw new RuntimeException("订单服务快速失败");
        });

        CompletableFuture<String> recFuture = CompletableFuture.supplyAsync(() -> {
            sleep(200);
            return callRecommendationService(1);
        });

        try {
            // 方法 1: 使用 anyOf 检测最快的结果（可能是异常）
            CompletableFuture<Object> anyOfFuture = CompletableFuture.anyOf(userFuture, orderFuture, recFuture);

            // 检查是否有异常
            anyOfFuture.whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("❌ [快速失败] 检测到服务失败: {}", ex.getMessage());
                }
            });

            // 也可以用 allOf，但会等待所有任务完成
            CompletableFuture.allOf(userFuture, orderFuture, recFuture).join();

        } catch (Exception e) {
            log.error("❌ [快速失败] 有服务失败，立即终止: {}", e.getCause().getMessage());

            // 取消其他正在执行的任务（节省资源）
            userFuture.cancel(true);
            recFuture.cancel(true);
            log.info("已取消其他正在执行的任务");
        }
    }

    // ======================== 下游服务模拟 ========================

    /**
     * 模拟用户服务调用（耗时 100ms）。
     */
    private static String callUserService(int userId) {
        log.info("  [用户服务] 查询用户 ID = {}", userId);
        sleep(100);
        return "User-" + userId;
    }

    /**
     * 模拟订单服务调用（耗时 150ms）。
     */
    private static String callOrderService(int userId) {
        log.info("  [订单服务] 查询用户 {} 的订单", userId);
        sleep(150);
        return "Order-" + userId;
    }

    /**
     * 模拟推荐服务调用（耗时 200ms）。
     */
    private static String callRecommendationService(int userId) {
        log.info("  [推荐服务] 生成用户 {} 的推荐", userId);
        sleep(200);
        return "Recommendation-" + userId;
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
