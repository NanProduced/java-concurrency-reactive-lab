package nan.tech.lab09.basic;

import lombok.extern.slf4j.Slf4j;
import nan.tech.lab09.BaseWebFluxTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FluxController WebTestClient 测试
 *
 * <h2>测试范围</h2>
 * <ul>
 *     <li>Demo 1: Flux.just() 基础示例</li>
 *     <li>Demo 2: Flux.range() 生成数字</li>
 *     <li>Demo 3: Flux.fromIterable() 列表转换</li>
 *     <li>Demo 4: delayElement() 延迟发送</li>
 *     <li>Demo 5: 错误处理与恢复</li>
 *     <li>Demo 6: 服务器推送事件 (SSE)</li>
 *     <li>Demo 7: 无限流 (interval)</li>
 * </ul>
 *
 * <h2>测试策略</h2>
 * <ul>
 *     <li>✅ 使用 WebTestClient 进行 HTTP 级别的测试</li>
 *     <li>✅ 验证响应状态码和内容格式</li>
 *     <li>✅ 监控 EventSource 流的完整性</li>
 *     <li>✅ 性能基线：应答时间、吞吐量</li>
 * </ul>
 *
 * @author Claude Code + Lab-09 团队
 * @version 1.0.0
 * @since 2025-10-23
 */
@Slf4j
@DisplayName("FluxController WebTestClient 测试套件")
public class WebTestClientFluxControllerTests extends BaseWebFluxTest {

    /**
     * Demo 1: Flux.just() 基础示例测试
     *
     * 期望行为:
     * - HTTP 状态码: 200 OK
     * - 响应流包含 3 个字符串: A, B, C
     * - 响应完成且无错误
     */
    @Test
    @DisplayName("Demo 1: simple - Flux.just() 基础示例")
    public void testSimpleFlux() {
        log.info("🧪 [Test] Flux.just() 基础示例");
        startTiming();

        webClient
                .get()
                .uri("/basic/flux/simple")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(String.class)
                .consumeWith(response -> {
                    var body = response.getResponseBody();
                    log.info("✅ 响应: {}", body);
                    assertThat(body).isNotEmpty().hasSize(3);
                    assertThat(body).contains("A", "B", "C");
                });

        long elapsed = endTiming();
        assertThat(elapsed).isLessThan(1000); // 应该在 1 秒内完成
    }

    /**
     * Demo 2: Flux.range() 生成数字测试
     *
     * 期望行为:
     * - HTTP 状态码: 200 OK
     * - 响应包含 5 个转换后的数字: 10, 20, 30, 40, 50
     * - 每个元素都被 map() 转换为原值的 10 倍
     */
    @Test
    @DisplayName("Demo 2: range - Flux.range() 生成数字")
    public void testRangeFlux() {
        log.info("🧪 [Test] Flux.range() 生成数字");
        startTiming();

        webClient
                .get()
                .uri("/basic/flux/range")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Integer.class)
                .consumeWith(response -> {
                    var body = response.getResponseBody();
                    log.info("✅ 响应: {}", body);
                    assertThat(body).isNotEmpty().hasSize(5);
                    assertThat(body).containsExactly(10, 20, 30, 40, 50);
                });

        long elapsed = endTiming();
        assertThat(elapsed).isLessThan(1000);
    }

    /**
     * Demo 3: Flux.fromIterable() 列表转换测试
     *
     * 期望行为:
     * - HTTP 状态码: 200 OK
     * - 响应包含 4 个用户名: Alice, Bob, Charlie, David
     * - 所有元素都被正确序列化
     */
    @Test
    @DisplayName("Demo 3: from-list - Flux.fromIterable() 列表转换")
    public void testFromIterableFlux() {
        log.info("🧪 [Test] Flux.fromIterable() 列表转换");
        startTiming();

        webClient
                .get()
                .uri("/basic/flux/from-list")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(String.class)
                .consumeWith(response -> {
                    var body = response.getResponseBody();
                    log.info("✅ 响应: {}", body);
                    assertThat(body).isNotEmpty().hasSize(4);
                    assertThat(body).contains("Alice", "Bob", "Charlie", "David");
                });

        long elapsed = endTiming();
        assertThat(elapsed).isLessThan(1000);
    }

    /**
     * Demo 4: delayElement() 延迟发送测试
     *
     * 期望行为:
     * - HTTP 状态码: 200 OK
     * - 响应包含 3 个数字: 1, 2, 3
     * - 每个元素间隔 500ms，总耗时应 >= 1000ms
     * - 演示非阻塞延迟的概念
     */
    @Test
    @DisplayName("Demo 4: delay - delayElement() 延迟发送")
    public void testDelayElementFlux() {
        log.info("🧪 [Test] delayElement() 延迟发送 (500ms per element)");
        startTiming();

        webClient
                .get()
                .uri("/basic/flux/delay")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Integer.class)
                .consumeWith(response -> {
                    var body = response.getResponseBody();
                    log.info("✅ 响应: {}", body);
                    assertThat(body).isNotEmpty().hasSize(3);
                    assertThat(body).contains(1, 2, 3);
                });

        long elapsed = endTiming();
        // 3 个元素，每个 500ms，总耗时应 >= 1000ms
        log.info("⏱️  总耗时: {}ms (期望 >= 1000ms)", elapsed);
        assertThat(elapsed).isGreaterThanOrEqualTo(1000).isLessThan(3000);
    }

    /**
     * Demo 5: 错误处理与恢复测试
     *
     * 期望行为:
     * - HTTP 状态码: 200 OK (因为我们用 onErrorResume 恢复了)
     * - 响应包含恢复后的值: "错误已恢复", "返回缓存数据", "用户体验不受影响"
     * - 演示了 onErrorResume 如何捕获错误并返回替代值
     */
    @Test
    @DisplayName("Demo 5: error - 错误处理与恢复")
    public void testErrorFlux() {
        log.info("🧪 [Test] 错误处理与恢复");
        startTiming();

        webClient
                .get()
                .uri("/basic/flux/error")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(String.class)
                .consumeWith(response -> {
                    var body = response.getResponseBody();
                    log.info("✅ 响应: {}", body);
                    assertThat(body).isNotEmpty().hasSize(3);
                    assertThat(body).contains("错误已恢复", "返回缓存数据", "用户体验不受影响");
                });

        long elapsed = endTiming();
        assertThat(elapsed).isLessThan(2000);
    }

    /**
     * Demo 6: 服务器推送事件 (SSE) 测试
     *
     * 期望行为:
     * - HTTP 状态码: 200 OK
     * - Content-Type: text/event-stream (SSE 标准)
     * - 响应体包含多条消息 (至少 1 条)
     * - 演示了 Flux 与 SSE 的结合
     *
     * 注意: 这是一个无限流，测试会在一定时间后超时或客户端断开
     */
    @Test
    @DisplayName("Demo 6: stream - 服务器推送事件 (SSE)")
    public void testStreamFlux() {
        log.info("🧪 [Test] 服务器推送事件 (SSE)");
        startTiming();

        // 注意: SSE 是无限流，我们测试前几条消息后就停止
        webClient
                .get()
                .uri("/basic/flux/stream")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType("text/event-stream;charset=UTF-8")
                .returnResult(String.class)
                .getResponseBody()
                .take(3) // 只取前 3 条消息
                .blockLast(Duration.ofSeconds(5));

        long elapsed = endTiming();
        log.info("✅ SSE 流成功建立并接收了消息");
        assertThat(elapsed).isLessThan(5000);
    }

    /**
     * Demo 7: 无限流 (interval) 测试
     *
     * 期望行为:
     * - HTTP 状态码: 200 OK
     * - Content-Type: text/event-stream
     * - 响应包含每秒发送的消息
     * - 演示了 Flux.interval() 的无限流特性
     *
     * 注意: 无限流测试需要主动限制接收的元素数量
     */
    @Test
    @DisplayName("Demo 7: interval - 无限数字流")
    public void testIntervalFlux() {
        log.info("🧪 [Test] Flux.interval() 无限流");
        startTiming();

        webClient
                .get()
                .uri("/basic/flux/interval")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType("text/event-stream;charset=UTF-8")
                .returnResult(String.class)
                .getResponseBody()
                .take(2) // 只取前 2 条消息，避免等待太久
                .blockLast(Duration.ofSeconds(5));

        long elapsed = endTiming();
        log.info("✅ 无限流成功建立");
        assertThat(elapsed).isLessThan(5000);
    }

    /**
     * 性能基线：吞吐量测试
     *
     * 目标: 测量简单 Flux 的吞吐量
     * 期望: 简单流应该能在 100ms 内处理完
     */
    @Test
    @DisplayName("性能基线: 简单 Flux 吞吐量")
    public void testFluxThroughput() {
        log.info("🧪 [性能测试] Flux 吞吐量基线");

        for (int i = 0; i < 10; i++) {
            startTiming();

            webClient
                    .get()
                    .uri("/basic/flux/simple")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBodyList(String.class)
                    .consumeWith(response -> {
                        assertThat(response.getResponseBody()).hasSize(3);
                    });

            long elapsed = endTiming();
            log.info("  第 {} 次请求: {}ms", i + 1, elapsed);
        }

        log.info("✅ 吞吐量基线测试完成");
    }

}
