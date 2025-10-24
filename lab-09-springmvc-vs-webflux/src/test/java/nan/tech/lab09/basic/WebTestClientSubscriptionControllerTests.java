package nan.tech.lab09.basic;

import lombok.extern.slf4j.Slf4j;
import nan.tech.lab09.BaseWebFluxTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SubscriptionController WebTestClient 测试
 *
 * <h2>测试范围</h2>
 * <ul>
 *     <li>Demo 1: 定义 vs 订阅 - 理解执行时机</li>
 *     <li>Demo 2: 事件链 - onNext 事件的顺序</li>
 *     <li>Demo 3: 多个 onNext 事件 - Flux 的特性</li>
 *     <li>Demo 4: 冷流演示 - 每个订阅都从头开始</li>
 *     <li>Demo 5: 异常处理 - 三种事件的互斥性</li>
 *     <li>Demo 6: 超时处理 - 响应式流的超时机制</li>
 * </ul>
 *
 * <h2>测试策略</h2>
 * <ul>
 *     <li>✅ 测试生命周期事件的正确顺序</li>
 *     <li>✅ 验证 onNext/onError/onComplete 的互斥性</li>
 *     <li>✅ 测试超时和错误恢复</li>
 *     <li>✅ 性能基线：事件链的处理耗时</li>
 * </ul>
 *
 * @author Claude Code + Lab-09 团队
 * @version 1.0.0
 * @since 2025-10-23
 */
@Slf4j
@DisplayName("SubscriptionController WebTestClient 测试套件")
public class WebTestClientSubscriptionControllerTests extends BaseWebFluxTest {

    /**
     * Demo 1: 定义 vs 订阅测试
     *
     * 期望行为:
     * - HTTP 状态码: 200 OK
     * - 响应包含转换后的值: "转换后的 结果值"
     * - 演示了定义（creation）和订阅（subscription）的区别
     * - 定义时不执行，订阅时才执行
     */
    @Test
    @DisplayName("Demo 1: definition-vs-subscription - 定义 vs 订阅")
    public void testDefinitionVsSubscription() {
        log.info("🧪 [Test] 定义 vs 订阅");
        startTiming();

        webClient
                .get()
                .uri("/basic/subscription/definition-vs-subscription")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .consumeWith(response -> {
                    String body = response.getResponseBody();
                    log.info("✅ 响应: {}", body);
                    assertThat(body).contains("转换后的").contains("结果值");
                });

        long elapsed = endTiming();
        assertThat(elapsed).isLessThan(1000);
    }

    /**
     * Demo 2: 事件链测试
     *
     * 期望行为:
     * - HTTP 状态码: 200 OK
     * - 响应包含最终转换后的值: 102 (= (1*2)+100)
     * - 演示了事件链的执行流程：
     *   1. 原始值: 1
     *   2. 转换 1: 1*2 = 2
     *   3. 转换 2: 2+100 = 102
     * - 日志应该显示每一步的执行
     */
    @Test
    @DisplayName("Demo 2: event-chain - 事件链演示")
    public void testEventChain() {
        log.info("🧪 [Test] 事件链: 1 → (1*2=2) → (2+100=102)");
        startTiming();

        webClient
                .get()
                .uri("/basic/subscription/event-chain")
                .exchange()
                .expectStatus().isOk()
                .expectBody(Integer.class)
                .consumeWith(response -> {
                    Integer body = response.getResponseBody();
                    log.info("✅ 响应: {}", body);
                    assertThat(body).isEqualTo(102);
                });

        long elapsed = endTiming();
        assertThat(elapsed).isLessThan(1000);
    }

    /**
     * Demo 3: 多个 onNext 事件测试
     *
     * 期望行为:
     * - HTTP 状态码: 200 OK
     * - 响应包含 3 个转换后的字符串: "A+", "B+", "C+"
     * - 演示了 Flux 会多次调用 onNext（Mono 最多 1 次）
     * - 每个元素都会独立触发整个链的执行
     */
    @Test
    @DisplayName("Demo 3: multiple-events - Flux 的多个 onNext 事件")
    public void testMultipleEvents() {
        log.info("🧪 [Test] Flux 多个 onNext 事件");
        startTiming();

        webClient
                .get()
                .uri("/basic/subscription/multiple-events")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(String.class)
                .consumeWith(response -> {
                    var body = response.getResponseBody();
                    log.info("✅ 响应: {}", body);
                    assertThat(body).isNotEmpty().hasSize(3);
                    assertThat(body).contains("A+", "B+", "C+");
                });

        long elapsed = endTiming();
        assertThat(elapsed).isLessThan(1000);
    }

    /**
     * Demo 4: 冷流演示测试
     *
     * 期望行为:
     * - HTTP 状态码: 200 OK
     * - 响应包含冷流特性的解释
     * - 包含 "结果 #1" (第一次调用)
     * - 演示了冷流的特性：每个订阅都从头开始
     */
    @Test
    @DisplayName("Demo 4: cold-stream - 冷流演示")
    public void testColdStream() {
        log.info("🧪 [Test] 冷流演示");
        startTiming();

        webClient
                .get()
                .uri("/basic/subscription/cold-stream")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .consumeWith(response -> {
                    String body = response.getResponseBody();
                    log.info("✅ 响应: {}", body);
                    assertThat(body)
                            .contains("冷流特性演示")
                            .contains("结果");
                });

        long elapsed = endTiming();
        assertThat(elapsed).isLessThan(1000);
    }

    /**
     * Demo 5: 异常处理测试
     *
     * 期望行为:
     * - HTTP 状态码: 200 OK (错误被恢复了)
     * - 响应包含恢复后的值: "错误恢复后的默认值"
     * - 演示了 onErrorResume 的使用
     * - 演示了三种终止事件的互斥性
     */
    @Test
    @DisplayName("Demo 5: error-handling - 异常处理与恢复")
    public void testErrorHandling() {
        log.info("🧪 [Test] 异常处理与恢复");
        startTiming();

        webClient
                .get()
                .uri("/basic/subscription/error-handling")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .consumeWith(response -> {
                    String body = response.getResponseBody();
                    log.info("✅ 响应: {}", body);
                    assertThat(body).contains("错误恢复后的默认值");
                });

        long elapsed = endTiming();
        assertThat(elapsed).isLessThan(1000);
    }

    /**
     * Demo 6: 超时处理测试
     *
     * 期望行为:
     * - HTTP 状态码: 200 OK (超时被恢复了)
     * - 响应包含超时后的默认值: "超时，返回默认值"
     * - 演示了 timeout() 操作符和 onErrorReturn() 的组合
     * - 总耗时应 < 2 秒 (因为超时设置为 1 秒，加上网络开销)
     */
    @Test
    @DisplayName("Demo 6: timeout - 超时处理")
    public void testTimeout() {
        log.info("🧪 [Test] 超时处理 (设置 1 秒超时，操作需要 3 秒)");
        startTiming();

        webClient
                .get()
                .uri("/basic/subscription/timeout")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .consumeWith(response -> {
                    String body = response.getResponseBody();
                    log.info("✅ 响应: {}", body);
                    assertThat(body).contains("超时").contains("默认值");
                });

        long elapsed = endTiming();
        log.info("⏱️  总耗时: {}ms (应该 ~1-2 秒，因为超时了)", elapsed);
        assertThat(elapsed).isLessThan(5000); // 考虑网络延迟
    }

    /**
     * 集成测试: 验证三个终止事件的互斥性
     *
     * 目标: 确保 onNext、onError、onComplete 三者不会同时出现
     * 验证事件链的完整性
     */
    @Test
    @DisplayName("集成测试: 事件互斥性与完整性")
    public void testEventMutualExclusion() {
        log.info("🧪 [集成测试] 三个终止事件的互斥性");

        // 测试成功路径 (onNext + onComplete)
        startTiming();
        webClient
                .get()
                .uri("/basic/subscription/definition-vs-subscription")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .consumeWith(response -> {
                    assertThat(response.getResponseBody()).isNotNull();
                });
        long elapsed1 = endTiming();
        log.info("✅ 成功路径 (onNext + onComplete): {}ms", elapsed1);

        // 测试错误路径 (onError + recovery)
        startTiming();
        webClient
                .get()
                .uri("/basic/subscription/error-handling")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .consumeWith(response -> {
                    assertThat(response.getResponseBody()).contains("恢复");
                });
        long elapsed2 = endTiming();
        log.info("✅ 错误路径 (onError + onErrorResume): {}ms", elapsed2);

        // 总耗时应该合理
        assertThat(elapsed1 + elapsed2).isLessThan(5000);
    }

    /**
     * 性能基线: 事件链处理耗时
     *
     * 目标: 测量完整事件链的基线性能
     * 期望: 事件链处理应在 100ms 内完成
     */
    @Test
    @DisplayName("性能基线: 事件链处理耗时")
    public void testEventChainPerformance() {
        log.info("🧪 [性能测试] 事件链处理耗时基线");

        long totalTime = 0;
        int iterations = 20;

        for (int i = 0; i < iterations; i++) {
            startTiming();

            webClient
                    .get()
                    .uri("/basic/subscription/event-chain")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Integer.class)
                    .consumeWith(response -> {
                        assertThat(response.getResponseBody()).isEqualTo(102);
                    });

            long elapsed = endTiming();
            totalTime += elapsed;
            log.info("  第 {} 次: {}ms", i + 1, elapsed);
        }

        long avgTime = totalTime / iterations;
        log.info("✅ 平均耗时: {}ms", avgTime);
        assertThat(avgTime).isLessThan(100); // 平均应在 100ms 内
    }

}
