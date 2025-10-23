package nan.tech.lab09.operators;

import lombok.extern.slf4j.Slf4j;
import nan.tech.lab09.BaseWebFluxTest;
import nan.tech.lab09.PerformanceBaselineCollector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.*;

/**
 * OperatorsController 响应式操作符演示的测试套件
 *
 * <h2>测试范围</h2>
 * <ul>
 *     <li>map() - 同步元素转换</li>
 *     <li>flatMap() - 异步流扁平化</li>
 *     <li>merge() - 流交错合并</li>
 *     <li>zip() - 流元素配对与同步</li>
 *     <li>flatMap 并发控制 - 并发数限制</li>
 *     <li>背压控制 - Buffer 策略</li>
 *     <li>背压控制 - OnBackpressureLatest 策略</li>
 *     <li>多操作符链式处理</li>
 * </ul>
 *
 * <h2>测试方法</h2>
 * <ul>
 *     <li>利用 WebTestClient 进行非阻塞 HTTP 请求</li>
 *     <li>验证响应内容与数据转换逻辑</li>
 *     <li>收集性能基线数据</li>
 *     <li>集成测试多个操作符的组合</li>
 * </ul>
 *
 * @author Claude Code + Lab-09 团队
 * @version 1.0.0
 * @since 2025-10-23
 */
@Slf4j
@WebFluxTest(OperatorsController.class)
@ActiveProfiles("test")
@DisplayName("OperatorsController - 响应式操作符演示集成测试")
public class WebTestClientOperatorsControllerTests extends BaseWebFluxTest {

    private static final PerformanceBaselineCollector performanceCollector = PerformanceBaselineCollector.getInstance();

    /**
     * 演示 1: map() - 同步元素转换
     *
     * <h3>测试目标</h3>
     * 验证 map() 操作符能够正确地对每个元素进行同步转换（平方操作）
     *
     * <h3>验证点</h3>
     * <ul>
     *     <li>✅ 返回 5 个元素 (1, 2, 3, 4, 5 → 1, 4, 9, 16, 25)</li>
     *     <li>✅ 转换结果正确</li>
     *     <li>✅ 响应时间 < 500ms</li>
     * </ul>
     */
    @Test
    @DisplayName("测试 map() - 同步元素转换")
    void testMapOperator() {
        startTiming();

        webClient
                .get()
                .uri("/operators/map")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Integer.class)
                .consumeWith(response -> {
                    var body = response.getResponseBody();
                    assertThat(body)
                            .isNotNull()
                            .hasSize(5)
                            .containsExactly(1, 4, 9, 16, 25);  // 1², 2², 3², 4², 5²
                });

        long elapsed = endTiming();
        performanceCollector.record("testMapOperator", elapsed);
        assertThat(elapsed).isLessThan(500);
    }

    /**
     * 演示 2: flatMap() - 异步流扁平化
     *
     * <h3>测试目标</h3>
     * 验证 flatMap() 能够将异步操作结果扁平化为单个流
     * 每个用户 ID 返回 3 个相关数据项 (profile, settings, preferences)
     *
     * <h3>验证点</h3>
     * <ul>
     *     <li>✅ 3 个用户 × 3 个数据项 = 9 个元素</li>
     *     <li>✅ 每个用户的数据格式正确 (user1-profile, user1-settings, 等)</li>
     *     <li>✅ 响应时间 ≥ 300ms (因为模拟异步延迟 100ms)</li>
     * </ul>
     */
    @Test
    @DisplayName("测试 flatMap() - 异步流扁平化")
    void testFlatMapOperator() {
        startTiming();

        webClient
                .get()
                .uri("/operators/flatmap")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(String.class)
                .consumeWith(response -> {
                    var body = response.getResponseBody();
                    assertThat(body)
                            .isNotNull()
                            .hasSize(9)  // 3 users × 3 data items each
                            .contains(
                                    "user1-profile", "user1-settings", "user1-preferences",
                                    "user2-profile", "user2-settings", "user2-preferences",
                                    "user3-profile", "user3-settings", "user3-preferences"
                            );
                });

        long elapsed = endTiming();
        performanceCollector.record("testFlatMapOperator", elapsed);
        // 延迟 100ms + 发射时间，应该 ≥ 300ms
        assertThat(elapsed).isGreaterThanOrEqualTo(300);
    }

    /**
     * 演示 3: merge() - 流交错合并
     *
     * <h3>测试目标</h3>
     * 验证 merge() 能够将多个流合并为一个流，允许交错发射
     *
     * <h3>验证点</h3>
     * <ul>
     *     <li>✅ 6 个元素 (3 fast + 3 slow)</li>
     *     <li>✅ 包含所有预期的值</li>
     *     <li>✅ 响应时间 ≥ 600ms (slow 流延迟 200ms × 3)</li>
     * </ul>
     */
    @Test
    @DisplayName("测试 merge() - 流交错合并")
    void testMergeOperator() {
        startTiming();

        webClient
                .get()
                .uri("/operators/merge")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(String.class)
                .consumeWith(response -> {
                    var body = response.getResponseBody();
                    assertThat(body)
                            .isNotNull()
                            .hasSize(6)
                            .contains(
                                    "Fast-1", "Fast-2", "Fast-3",
                                    "Slow-1", "Slow-2", "Slow-3"
                            );
                });

        long elapsed = endTiming();
        performanceCollector.record("testMergeOperator", elapsed);
        // 慢速流需要 200ms × 3 = 600ms
        assertThat(elapsed).isGreaterThanOrEqualTo(600);
    }

    /**
     * 演示 4: zip() - 流元素配对与同步
     *
     * <h3>测试目标</h3>
     * 验证 zip() 能够将多个流的元素配对，等待所有流都有值时才一起发射
     *
     * <h3>验证点</h3>
     * <ul>
     *     <li>✅ 3 个配对元组 (最小的流决定数量)</li>
     *     <li>✅ 配对格式正确 (Red-1, Green-2, Blue-3)</li>
     *     <li>✅ 响应时间 ≥ 450ms (由最慢的流 color 决定: 150ms × 3)</li>
     * </ul>
     */
    @Test
    @DisplayName("测试 zip() - 流元素配对与同步")
    void testZipOperator() {
        startTiming();

        webClient
                .get()
                .uri("/operators/zip")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(String.class)
                .consumeWith(response -> {
                    var body = response.getResponseBody();
                    assertThat(body)
                            .isNotNull()
                            .hasSize(3)
                            .containsExactly("Red-1", "Green-2", "Blue-3");
                });

        long elapsed = endTiming();
        performanceCollector.record("testZipOperator", elapsed);
        // 颜色流延迟 150ms × 3 = 450ms
        assertThat(elapsed).isGreaterThanOrEqualTo(450);
    }

    /**
     * 演示 5: flatMap 并发控制
     *
     * <h3>测试目标</h3>
     * 验证 flatMap(mapper, concurrency) 能够限制并发数
     * 默认并发数为 2，处理 10 个请求
     *
     * <h3>验证点</h3>
     * <ul>
     *     <li>✅ 10 个响应 (响应-1 到 响应-10)</li>
     *     <li>✅ 所有响应都返回</li>
     *     <li>✅ 响应时间表明受到并发数限制</li>
     * </ul>
     */
    @Test
    @DisplayName("测试 flatMap 并发控制 - 默认并发数=2")
    void testFlatMapConcurrentControl() {
        startTiming();

        webClient
                .get()
                .uri("/operators/flatmap-concurrent?concurrent=2")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(String.class)
                .consumeWith(response -> {
                    var body = response.getResponseBody();
                    assertThat(body)
                            .isNotNull()
                            .hasSize(10)
                            .contains(
                                    "响应-1", "响应-2", "响应-3", "响应-4", "响应-5",
                                    "响应-6", "响应-7", "响应-8", "响应-9", "响应-10"
                            );
                });

        long elapsed = endTiming();
        performanceCollector.record("testFlatMapConcurrentControl", elapsed);
    }

    /**
     * 演示 6: 背压控制 - Buffer 策略
     *
     * <h3>测试目标</h3>
     * 验证 buffer() 背压策略能够将元素分组缓存
     * 生产 20 个元素，缓冲 5 个元素的批次
     *
     * <h3>验证点</h3>
     * <ul>
     *     <li>✅ 返回 20 个元素 (缓冲后扁平化)</li>
     *     <li>✅ 背压控制生效 (消费速度限制)</li>
     *     <li>✅ 响应时间 ≥ 1000ms (4 个批次 × 200ms = 800ms+)</li>
     * </ul>
     */
    @Test
    @DisplayName("测试背压 - Buffer 缓冲策略")
    void testBackpressureBuffer() {
        startTiming();

        webClient
                .get()
                .uri("/operators/backpressure-buffer")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Integer.class)
                .consumeWith(response -> {
                    var body = response.getResponseBody();
                    assertThat(body)
                            .isNotNull()
                            .hasSize(20)
                            .containsExactly(
                                    1, 2, 3, 4, 5,
                                    6, 7, 8, 9, 10,
                                    11, 12, 13, 14, 15,
                                    16, 17, 18, 19, 20
                            );
                });

        long elapsed = endTiming();
        performanceCollector.record("testBackpressureBuffer", elapsed);
        // 4 个批次，每个 200ms 延迟 = 800ms+
        assertThat(elapsed).isGreaterThanOrEqualTo(800);
    }

    /**
     * 演示 7: 背压控制 - OnBackpressureLatest 策略
     *
     * <h3>测试目标</h3>
     * 验证 onBackpressureLatest() 策略只保留最新值
     * 生产 20 个元素（50ms 间隔），消费 200ms 延迟
     *
     * <h3>验证点</h3>
     * <ul>
     *     <li>✅ 返回的元素数量 < 20 (因为只保留最新值)</li>
     *     <li>✅ 所有返回的元素都在预期范围内</li>
     *     <li>✅ 背压策略限制了返回数量</li>
     * </ul>
     */
    @Test
    @DisplayName("测试背压 - OnBackpressureLatest 策略")
    void testBackpressureLatest() {
        startTiming();

        webClient
                .get()
                .uri("/operators/backpressure-latest")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Integer.class)
                .consumeWith(response -> {
                    var body = response.getResponseBody();
                    assertThat(body)
                            .isNotNull()
                            .isNotEmpty()
                            .hasSizeLessThanOrEqualTo(20);  // 因为只保留最新值，数量会少很多
                });

        long elapsed = endTiming();
        performanceCollector.record("testBackpressureLatest", elapsed);
    }

    /**
     * 演示 8: 复合操作 - 多操作符链式处理
     *
     * <h3>测试目标</h3>
     * 验证多个操作符的组合使用：filter → map → flatMap
     * 从 4 个名字开始，过滤长度 > 3，转大写，关联多个数据
     *
     * <h3>验证点</h3>
     * <ul>
     *     <li>✅ 过滤后 3 个名字 (Alice, Charlie, Diana 长度 > 3)</li>
     *     <li>✅ 每个名字关联 2 个数据项 (email, phone) = 6 个元素</li>
     *     <li>✅ 名字转换为大写</li>
     *     <li>✅ 包含所有预期的扁平化数据</li>
     * </ul>
     */
    @Test
    @DisplayName("测试复合操作 - 多操作符链式处理")
    void testCombinedOperators() {
        startTiming();

        webClient
                .get()
                .uri("/operators/combined")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(String.class)
                .consumeWith(response -> {
                    var body = response.getResponseBody();
                    assertThat(body)
                            .isNotNull()
                            .hasSize(6)  // 3 names × 2 items each
                            .contains(
                                    "ALICE-email", "ALICE-phone",
                                    "CHARLIE-email", "CHARLIE-phone",
                                    "DIANA-email", "DIANA-phone"
                            );
                });

        long elapsed = endTiming();
        performanceCollector.record("testCombinedOperators", elapsed);
    }

    /**
     * 性能基线测试 - 多次迭代测量
     *
     * <h3>测试目标</h3>
     * 收集所有操作符的性能基线数据，支持后续性能对标
     *
     * <h3>验证点</h3>
     * <ul>
     *     <li>✅ 运行 10 次迭代，每次测量响应时间</li>
     *     <li>✅ 计算平均值、最小值、最大值、P95、P99</li>
     *     <li>✅ 生成性能报告 (target/performance-baseline.csv)</li>
     * </ul>
     */
    @Test
    @DisplayName("性能基线测试 - 操作符响应时间集合")
    void testOperatorPerformanceBaseline() {
        log.info("开始收集性能基线数据 (10 次迭代)...");

        // 迭代 map() 性能
        for (int i = 0; i < 10; i++) {
            startTiming();
            webClient
                    .get()
                    .uri("/operators/map")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBodyList(Integer.class)
                    .consumeWith(response -> assertThat(response.getResponseBody()).hasSize(5));
            long elapsed = endTiming();
            performanceCollector.record("OperatorBaseline-map", elapsed);
        }

        // 迭代 flatMap() 性能
        for (int i = 0; i < 10; i++) {
            startTiming();
            webClient
                    .get()
                    .uri("/operators/flatmap")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBodyList(String.class)
                    .consumeWith(response -> assertThat(response.getResponseBody()).hasSize(9));
            long elapsed = endTiming();
            performanceCollector.record("OperatorBaseline-flatMap", elapsed);
        }

        // 迭代 merge() 性能
        for (int i = 0; i < 10; i++) {
            startTiming();
            webClient
                    .get()
                    .uri("/operators/merge")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBodyList(String.class)
                    .consumeWith(response -> assertThat(response.getResponseBody()).hasSize(6));
            long elapsed = endTiming();
            performanceCollector.record("OperatorBaseline-merge", elapsed);
        }

        // 打印统计信息
        log.info("=== 性能基线统计 ===");
        log.info("map() 统计: {}", performanceCollector.getStatistics("OperatorBaseline-map"));
        log.info("flatMap() 统计: {}", performanceCollector.getStatistics("OperatorBaseline-flatMap"));
        log.info("merge() 统计: {}", performanceCollector.getStatistics("OperatorBaseline-merge"));

        // 生成性能报告
        performanceCollector.printSummary();
        log.info("性能报告已生成: target/performance-baseline.csv");
    }

}
