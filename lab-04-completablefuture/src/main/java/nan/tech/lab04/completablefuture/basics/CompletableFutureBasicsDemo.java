package nan.tech.lab04.completablefuture.basics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;

/**
 * CompletableFuture 基础链式调用演示。
 *
 * <p><b>@教学目标</b>
 * <ul>
 *   <li>理解 CompletableFuture 的基本用法和链式调用模式</li>
 *   <li>掌握同步方法 vs 异步方法的区别（thenApply vs thenApplyAsync）</li>
 *   <li>学习串行组合（thenCompose）和并行组合（thenCombine）</li>
 *   <li>理解自定义线程池 vs 默认 ForkJoinPool 的差异</li>
 * </ul>
 *
 * <p><b>@核心概念</b>
 * <ul>
 *   <li><b>thenApply</b>: 转换结果，同步执行（在上一步的线程中）</li>
 *   <li><b>thenApplyAsync</b>: 转换结果，异步执行（在线程池中）</li>
 *   <li><b>thenCompose</b>: 串行组合（扁平化，避免嵌套 CompletableFuture）</li>
 *   <li><b>thenCombine</b>: 并行组合（两个独立任务的结果合并）</li>
 * </ul>
 *
 * <p><b>@陷阱</b>
 * <ul>
 *   <li><b>默认线程池的局限</b>: CompletableFuture 默认使用 ForkJoinPool.commonPool()，
 *       线程数 = CPU核数-1，不适合 IO 密集型任务</li>
 *   <li><b>同步方法的线程复用</b>: thenApply 在上一步的线程执行，可能导致线程阻塞</li>
 *   <li><b>异步方法的线程切换开销</b>: thenApplyAsync 每次都切换线程，有一定性能开销</li>
 * </ul>
 *
 * <p><b>@对比设计</b>
 * <ul>
 *   <li>❌ WITHOUT: 同步阻塞调用</li>
 *   <li>✅ WITH: CompletableFuture 异步链式调用</li>
 * </ul>
 *
 * <p><b>@参考</b>
 * <ul>
 *   <li>Java 8 in Action: CompletableFuture 完整指南</li>
 *   <li>CompletableFuture JavaDoc</li>
 * </ul>
 *
 * @author Nan
 * @since 1.0.0
 */
public class CompletableFutureBasicsDemo {

    private static final Logger log = LoggerFactory.getLogger(CompletableFutureBasicsDemo.class);

    // 自定义线程池（用于对比）
    private static final ExecutorService customExecutor = new ThreadPoolExecutor(
            4,                          // 核心线程数
            8,                          // 最大线程数
            60L, TimeUnit.SECONDS,      // 空闲线程存活时间
            new LinkedBlockingQueue<>(100),  // 工作队列
            new ThreadFactory() {
                private int count = 0;
                @Override
                public Thread newThread(Runnable r) {
                    return new Thread(r, "custom-cf-" + count++);
                }
            }
    );

    public static void main(String[] args) throws Exception {
        log.info("=== CompletableFuture 基础链式调用演示 ===\n");

        // 对比 1: 同步 vs 异步
        demo1_SyncVsAsync();

        // 对比 2: thenApply vs thenApplyAsync
        demo2_ApplyVsApplyAsync();

        // 对比 3: 串行组合 (thenCompose)
        demo3_ThenCompose();

        // 对比 4: 并行组合 (thenCombine)
        demo4_ThenCombine();

        // 对比 5: 自定义线程池 vs 默认线程池
        demo5_CustomExecutor();

        // 优雅关闭线程池
        customExecutor.shutdown();
        customExecutor.awaitTermination(5, TimeUnit.SECONDS);

        log.info("\n=== 演示完成 ===");
    }

    /**
     * 演示 1: 同步阻塞调用 vs CompletableFuture 异步调用。
     *
     * <p><b>@对比</b>
     * <ul>
     *   <li>❌ WITHOUT: 同步阻塞，总耗时 = 各步骤耗时之和</li>
     *   <li>✅ WITH: 异步非阻塞，可以在等待期间做其他事情</li>
     * </ul>
     */
    private static void demo1_SyncVsAsync() throws Exception {
        log.info("\n--- 演示 1: 同步 vs 异步 ---");

        // ❌ WITHOUT: 同步阻塞调用
        long start1 = System.currentTimeMillis();
        String result1 = fetchUserSync(1);
        String processed1 = processDataSync(result1);
        long end1 = System.currentTimeMillis();
        log.info("❌ 同步调用结果: {}, 耗时: {}ms", processed1, (end1 - start1));

        // ✅ WITH: CompletableFuture 异步调用
        long start2 = System.currentTimeMillis();
        CompletableFuture<String> future = CompletableFuture
                .supplyAsync(() -> fetchUserSync(1))  // 异步获取用户数据
                .thenApply(data -> processDataSync(data));  // 异步处理数据

        // 在等待期间可以做其他事情
        log.info("✅ 异步调用已提交，主线程可以继续执行其他任务...");
        Thread.sleep(50);  // 模拟做其他事情

        String result2 = future.get();  // 阻塞等待结果
        long end2 = System.currentTimeMillis();
        log.info("✅ 异步调用结果: {}, 总耗时: {}ms", result2, (end2 - start2));
    }

    /**
     * 演示 2: thenApply（同步）vs thenApplyAsync（异步）。
     *
     * <p><b>@对比</b>
     * <ul>
     *   <li><b>thenApply</b>: 在上一步的线程中执行（线程复用，无切换开销）</li>
     *   <li><b>thenApplyAsync</b>: 在 ForkJoinPool 中执行（线程切换，有开销）</li>
     * </ul>
     *
     * <p><b>@选择建议</b>
     * <ul>
     *   <li>轻量级转换（如字符串拼接）→ 使用 thenApply</li>
     *   <li>耗时操作（如IO、计算）→ 使用 thenApplyAsync</li>
     * </ul>
     */
    private static void demo2_ApplyVsApplyAsync() throws Exception {
        log.info("\n--- 演示 2: thenApply vs thenApplyAsync ---");

        // thenApply: 同步执行（在 ForkJoinPool-worker 线程）
        CompletableFuture<String> future1 = CompletableFuture
                .supplyAsync(() -> {
                    log.info("  [thenApply] 步骤1: 线程 = {}", Thread.currentThread().getName());
                    return "Hello";
                })
                .thenApply(s -> {
                    log.info("  [thenApply] 步骤2: 线程 = {}", Thread.currentThread().getName());
                    return s + " World";
                })
                .thenApply(s -> {
                    log.info("  [thenApply] 步骤3: 线程 = {}", Thread.currentThread().getName());
                    return s + "!";
                });
        log.info("thenApply 结果: {}", future1.get());

        Thread.sleep(100);  // 分隔输出

        // thenApplyAsync: 异步执行（每步都切换线程）
        CompletableFuture<String> future2 = CompletableFuture
                .supplyAsync(() -> {
                    log.info("  [thenApplyAsync] 步骤1: 线程 = {}", Thread.currentThread().getName());
                    return "Hello";
                })
                .thenApplyAsync(s -> {
                    log.info("  [thenApplyAsync] 步骤2: 线程 = {}", Thread.currentThread().getName());
                    return s + " World";
                })
                .thenApplyAsync(s -> {
                    log.info("  [thenApplyAsync] 步骤3: 线程 = {}", Thread.currentThread().getName());
                    return s + "!";
                });
        log.info("thenApplyAsync 结果: {}", future2.get());
    }

    /**
     * 演示 3: thenCompose（串行组合，扁平化嵌套）。
     *
     * <p><b>@教学</b>
     * <p>thenCompose 用于串行组合两个 CompletableFuture，避免嵌套。
     *
     * <p><b>@对比</b>
     * <ul>
     *   <li>❌ WITHOUT: 嵌套 CompletableFuture，难以阅读</li>
     *   <li>✅ WITH: thenCompose 扁平化，代码清晰</li>
     * </ul>
     *
     * <p><b>@类比</b>
     * <p>类似 Stream 的 flatMap，将嵌套结构扁平化。
     */
    private static void demo3_ThenCompose() throws Exception {
        log.info("\n--- 演示 3: thenCompose（串行组合） ---");

        // ❌ WITHOUT: 嵌套 CompletableFuture（不推荐）
        CompletableFuture<CompletableFuture<String>> nestedFuture = CompletableFuture
                .supplyAsync(() -> {
                    log.info("  [嵌套] 步骤1: 获取用户ID");
                    return 100;
                })
                .thenApply(userId -> {
                    log.info("  [嵌套] 步骤2: 根据用户ID异步获取用户名");
                    return CompletableFuture.supplyAsync(() -> "User-" + userId);
                });
        // 需要两次 get() 才能获取最终结果
        String result1 = nestedFuture.get().get();
        log.info("❌ 嵌套方式结果: {}", result1);

        Thread.sleep(100);

        // ✅ WITH: thenCompose 扁平化（推荐）
        CompletableFuture<String> flatFuture = CompletableFuture
                .supplyAsync(() -> {
                    log.info("  [扁平化] 步骤1: 获取用户ID");
                    return 100;
                })
                .thenCompose(userId -> {
                    log.info("  [扁平化] 步骤2: 根据用户ID异步获取用户名");
                    return CompletableFuture.supplyAsync(() -> "User-" + userId);
                });
        String result2 = flatFuture.get();
        log.info("✅ thenCompose 结果: {}", result2);
    }

    /**
     * 演示 4: thenCombine（并行组合，两个独立任务）。
     *
     * <p><b>@教学</b>
     * <p>thenCombine 用于并行执行两个独立的 CompletableFuture，然后合并结果。
     *
     * <p><b>@对比</b>
     * <ul>
     *   <li>❌ WITHOUT: 串行执行两个任务，总耗时 = 任务1 + 任务2</li>
     *   <li>✅ WITH: 并行执行两个任务，总耗时 ≈ max(任务1, 任务2)</li>
     * </ul>
     *
     * <p><b>@性能提升</b>
     * <p>假设任务1耗时100ms，任务2耗时150ms：
     * <ul>
     *   <li>串行: 100ms + 150ms = 250ms</li>
     *   <li>并行: max(100ms, 150ms) = 150ms（提升 40%）</li>
     * </ul>
     */
    private static void demo4_ThenCombine() throws Exception {
        log.info("\n--- 演示 4: thenCombine（并行组合） ---");

        // ❌ WITHOUT: 串行执行
        long start1 = System.currentTimeMillis();
        String user = fetchUserSync(1);
        String order = fetchOrderSync(100);
        String combined1 = user + " -> " + order;
        long end1 = System.currentTimeMillis();
        log.info("❌ 串行执行: 结果 = {}, 耗时 = {}ms", combined1, (end1 - start1));

        Thread.sleep(100);

        // ✅ WITH: 并行执行 + 合并
        long start2 = System.currentTimeMillis();
        CompletableFuture<String> userFuture = CompletableFuture.supplyAsync(() -> fetchUserSync(1));
        CompletableFuture<String> orderFuture = CompletableFuture.supplyAsync(() -> fetchOrderSync(100));

        CompletableFuture<String> combinedFuture = userFuture.thenCombine(orderFuture, (u, o) -> {
            log.info("  [合并] 线程 = {}", Thread.currentThread().getName());
            return u + " -> " + o;
        });

        String combined2 = combinedFuture.get();
        long end2 = System.currentTimeMillis();
        log.info("✅ 并行执行: 结果 = {}, 耗时 = {}ms", combined2, (end2 - start2));
    }

    /**
     * 演示 5: 自定义线程池 vs 默认 ForkJoinPool。
     *
     * <p><b>@教学</b>
     * <p>CompletableFuture 默认使用 ForkJoinPool.commonPool()：
     * <ul>
     *   <li>线程数 = CPU核数 - 1</li>
     *   <li>适合 CPU 密集型任务</li>
     *   <li>不适合 IO 密集型任务（线程数不足）</li>
     * </ul>
     *
     * <p><b>@建议</b>
     * <ul>
     *   <li>CPU 密集型 → 使用默认 ForkJoinPool</li>
     *   <li>IO 密集型 → 使用自定义 ExecutorService</li>
     * </ul>
     */
    private static void demo5_CustomExecutor() throws Exception {
        log.info("\n--- 演示 5: 自定义线程池 vs 默认线程池 ---");

        // 使用默认 ForkJoinPool
        CompletableFuture<String> future1 = CompletableFuture
                .supplyAsync(() -> {
                    log.info("  [默认线程池] 线程 = {}", Thread.currentThread().getName());
                    return "Default Pool";
                });
        log.info("默认线程池结果: {}", future1.get());

        Thread.sleep(100);

        // 使用自定义线程池
        CompletableFuture<String> future2 = CompletableFuture
                .supplyAsync(() -> {
                    log.info("  [自定义线程池] 线程 = {}", Thread.currentThread().getName());
                    return "Custom Pool";
                }, customExecutor);  // 指定自定义线程池
        log.info("自定义线程池结果: {}", future2.get());
    }

    // ======================== 辅助方法 ========================

    /**
     * 模拟同步获取用户数据（耗时100ms）。
     */
    private static String fetchUserSync(int userId) {
        try {
            Thread.sleep(100);  // 模拟网络延迟
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "User-" + userId;
    }

    /**
     * 模拟同步获取订单数据（耗时150ms）。
     */
    private static String fetchOrderSync(int orderId) {
        try {
            Thread.sleep(150);  // 模拟网络延迟
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "Order-" + orderId;
    }

    /**
     * 模拟同步处理数据（耗时50ms）。
     */
    private static String processDataSync(String data) {
        try {
            Thread.sleep(50);  // 模拟数据处理
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "[Processed] " + data;
    }
}
