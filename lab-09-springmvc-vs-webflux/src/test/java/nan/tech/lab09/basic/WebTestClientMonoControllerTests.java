package nan.tech.lab09.basic;

import lombok.extern.slf4j.Slf4j;
import nan.tech.lab09.BaseWebFluxTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MonoController WebTestClient 测试
 *
 * <h2>测试范围</h2>
 * <ul>
 *     <li>Demo 1: Mono.just() 基础示例</li>
 *     <li>Demo 2: delayElement() 延迟发送</li>
 *     <li>Demo 3: Mono.fromCallable() 回调转换</li>
 *     <li>Demo 4: Mono.empty() 和 defaultIfEmpty()</li>
 *     <li>Demo 5: Mono.error() 错误处理</li>
 *     <li>Demo 6: 从 CompletableFuture 转换</li>
 *     <li>Demo 7: map 和 filter 组合</li>
 * </ul>
 *
 * <h2>测试策略</h2>
 * <ul>
 *     <li>✅ Mono 最多返回 1 个元素（或空，或错误）</li>
 *     <li>✅ 测试响应内容的完整性和正确性</li>
 *     <li>✅ 验证错误处理和恢复机制</li>
 *     <li>✅ 性能基线：单元素响应时间</li>
 * </ul>
 *
 * @author Claude Code + Lab-09 团队
 * @version 1.0.0
 * @since 2025-10-23
 */
@Slf4j
@DisplayName("MonoController WebTestClient 测试套件")
public class WebTestClientMonoControllerTests extends BaseWebFluxTest {

    /**
     * Demo 1: Mono.just() 基础示例测试
     *
     * 期望行为:
     * - HTTP 状态码: 200 OK
     * - 响应体包含单个值: "Hello WebFlux"
     * - 演示了 Mono 返回单个元素的特性
     */
    @Test
    @DisplayName("Demo 1: simple - Mono.just() 基础示例")
    public void testSimpleMono() {
        log.info("🧪 [Test] Mono.just() 基础示例");
        startTiming();

        webClient
                .get()
                .uri("/basic/mono/simple")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .consumeWith(response -> {
                    String body = response.getResponseBody();
                    log.info("✅ 响应: {}", body);
                    assertThat(body).isEqualTo("Hello WebFlux");
                });

        long elapsed = endTiming();
        assertThat(elapsed).isLessThan(1000);
    }

    /**
     * Demo 2: delayElement() 延迟发送测试
     *
     * 期望行为:
     * - HTTP 状态码: 200 OK
     * - 响应包含单个值: "缓慢的操作"
     * - 响应时间应 >= 2000ms (延迟 2 秒)
     * - 演示非阻塞延迟
     */
    @Test
    @DisplayName("Demo 2: delay - delayElement() 延迟发送")
    public void testDelayMono() {
        log.info("🧪 [Test] Mono.delayElement() 延迟 2 秒");
        startTiming();

        webClient
                .get()
                .uri("/basic/mono/delay")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .consumeWith(response -> {
                    String body = response.getResponseBody();
                    log.info("✅ 响应: {}", body);
                    assertThat(body).contains("缓慢的操作");
                });

        long elapsed = endTiming();
        log.info("⏱️  总耗时: {}ms (期望 >= 2000ms)", elapsed);
        assertThat(elapsed).isGreaterThanOrEqualTo(2000).isLessThan(5000);
    }

    /**
     * Demo 3: Mono.fromCallable() 回调转换测试
     *
     * 期望行为:
     * - HTTP 状态码: 200 OK
     * - 访问 /basic/mono/from-callable/123 返回用户信息
     * - 响应包含 userId 为 123 的模拟用户
     * - 演示了延迟执行 (defer 效果)
     */
    @Test
    @DisplayName("Demo 3: from-callable - Mono.fromCallable() 回调转换")
    public void testFromCallableMono() {
        log.info("🧪 [Test] Mono.fromCallable() 回调转换");
        startTiming();

        webClient
                .get()
                .uri("/basic/mono/from-callable/123")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .consumeWith(response -> {
                    String body = response.getResponseBody();
                    log.info("✅ 响应: {}", body);
                    assertThat(body)
                            .contains("userId: 123")
                            .contains("username");
                });

        long elapsed = endTiming();
        assertThat(elapsed).isLessThan(2000);
    }

    /**
     * Demo 4: Mono.empty() 和 defaultIfEmpty() 测试
     *
     * 期望行为:
     * - HTTP 状态码: 200 OK
     * - 访问 /basic/mono/empty 返回默认值
     * - 响应包含: "这是默认值，因为 Mono 为空"
     * - 演示了空值处理机制
     */
    @Test
    @DisplayName("Demo 4: empty - Mono.empty() 和 defaultIfEmpty()")
    public void testEmptyMono() {
        log.info("🧪 [Test] Mono.empty() 和 defaultIfEmpty()");
        startTiming();

        webClient
                .get()
                .uri("/basic/mono/empty")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .consumeWith(response -> {
                    String body = response.getResponseBody();
                    log.info("✅ 响应: {}", body);
                    assertThat(body).contains("默认值");
                });

        long elapsed = endTiming();
        assertThat(elapsed).isLessThan(1000);
    }

    /**
     * Demo 5: Mono.error() 错误处理测试
     *
     * 期望行为:
     * - HTTP 状态码: 200 OK (因为用 onErrorReturn 恢复了)
     * - 响应包含恢复后的值: "错误已恢复"
     * - 演示了 onErrorReturn 的使用
     */
    @Test
    @DisplayName("Demo 5: error - Mono.error() 错误处理")
    public void testErrorMono() {
        log.info("🧪 [Test] Mono.error() 错误处理");
        startTiming();

        webClient
                .get()
                .uri("/basic/mono/error")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .consumeWith(response -> {
                    String body = response.getResponseBody();
                    log.info("✅ 响应: {}", body);
                    assertThat(body).contains("错误已恢复");
                });

        long elapsed = endTiming();
        assertThat(elapsed).isLessThan(1000);
    }

    /**
     * Demo 6: 从 CompletableFuture 转换测试
     *
     * 期望行为:
     * - HTTP 状态码: 200 OK
     * - 响应包含成功处理的消息
     * - 演示了 Mono.fromFuture() 将异步 Future 转换为响应式 Mono
     */
    @Test
    @DisplayName("Demo 6: from-future - 从 CompletableFuture 转换")
    public void testFromFutureMono() {
        log.info("🧪 [Test] Mono.fromFuture() CompletableFuture 转换");
        startTiming();

        webClient
                .get()
                .uri("/basic/mono/from-future")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .consumeWith(response -> {
                    String body = response.getResponseBody();
                    log.info("✅ 响应: {}", body);
                    assertThat(body).isNotBlank();
                });

        long elapsed = endTiming();
        assertThat(elapsed).isLessThan(3000);
    }

    /**
     * Demo 7: map 和 filter 组合测试
     *
     * 期望行为:
     * - HTTP 状态码: 200 OK
     * - 访问 /basic/mono/map-filter/25 返回处理结果
     * - 如果条件满足，返回处理后的值
     * - 演示了条件判断和转换
     */
    @Test
    @DisplayName("Demo 7: map-filter - map 和 filter 组合")
    public void testMapFilterMono() {
        log.info("🧪 [Test] Mono.map() 和 filter() 组合");
        startTiming();

        webClient
                .get()
                .uri("/basic/mono/map-filter/25")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .consumeWith(response -> {
                    String body = response.getResponseBody();
                    log.info("✅ 响应: {}", body);
                    assertThat(body).isNotBlank();
                });

        long elapsed = endTiming();
        assertThat(elapsed).isLessThan(1000);
    }

    /**
     * 性能基线：单元素响应时间测试
     *
     * 目标: 测量单个 Mono 响应的基线性能
     * 期望: Mono 响应应该在 50ms 内完成
     */
    @Test
    @DisplayName("性能基线: 单元素 Mono 响应时间")
    public void testMonoResponseTime() {
        log.info("🧪 [性能测试] Mono 响应时间基线");

        long totalTime = 0;
        int iterations = 20;

        for (int i = 0; i < iterations; i++) {
            startTiming();

            webClient
                    .get()
                    .uri("/basic/mono/simple")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class)
                    .consumeWith(response -> {
                        assertThat(response.getResponseBody()).isEqualTo("Hello WebFlux");
                    });

            long elapsed = endTiming();
            totalTime += elapsed;
            log.info("  第 {} 次请求: {}ms", i + 1, elapsed);
        }

        long avgTime = totalTime / iterations;
        log.info("✅ 平均响应时间: {}ms", avgTime);
        assertThat(avgTime).isLessThan(100); // 平均应在 100ms 内
    }

    /**
     * 集成测试: 多个 Mono 顺序调用
     *
     * 目标: 验证多个 HTTP 请求的总耗时
     */
    @Test
    @DisplayName("集成测试: 多个 Mono 顺序调用")
    public void testSequentialMonoCalls() {
        log.info("🧪 [集成测试] 多个 Mono 顺序调用");
        startTiming();

        // 第一个调用
        webClient
                .get()
                .uri("/basic/mono/simple")
                .exchange()
                .expectStatus().isOk();

        // 第二个调用
        webClient
                .get()
                .uri("/basic/mono/from-callable/42")
                .exchange()
                .expectStatus().isOk();

        // 第三个调用
        webClient
                .get()
                .uri("/basic/mono/empty")
                .exchange()
                .expectStatus().isOk();

        long elapsed = endTiming();
        log.info("✅ 三个顺序 Mono 调用总耗时: {}ms", elapsed);
        assertThat(elapsed).isLessThan(5000);
    }

}
