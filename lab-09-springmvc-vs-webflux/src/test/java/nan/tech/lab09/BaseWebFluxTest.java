package nan.tech.lab09;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.reactive.server.WebTestClient.BodyContentSpec;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * WebFlux 测试基类
 *
 * <h2>特点</h2>
 * <ul>
 *     <li>提供 WebTestClient 实例用于 HTTP 断言</li>
 *     <li>支持异步、非阻塞的响应式测试</li>
 *     <li>包含性能计时工具</li>
 * </ul>
 *
 * <h2>使用方式</h2>
 * <pre>
 * @SpringBootTest
 * @AutoConfigureWebTestClient
 * public class MyControllerTest extends BaseWebFluxTest {
 *     @Test
 *     public void testMyEndpoint() {
 *         webClient
 *             .get().uri("/api/endpoint")
 *             .exchange()
 *             .expectStatus().isOk()
 *             .expectBody(String.class)
 *             .consumeWith(response -> {
 *                 assertThat(response.getResponseBody()).contains("expected");
 *             });
 *     }
 * }
 * </pre>
 *
 * @author Claude Code + Lab-09 团队
 * @version 1.0.0
 * @since 2025-10-23
 */
@Slf4j
@SpringBootTest
@AutoConfigureWebTestClient
public abstract class BaseWebFluxTest {

    /**
     * WebTestClient 实例
     * 用于发送 HTTP 请求并进行响应式断言
     */
    @Autowired
    protected WebTestClient webClient;

    /**
     * 性能计时器：用于测量响应时间
     */
    private AtomicLong startTime;

    @BeforeEach
    public void setUp() {
        this.startTime = new AtomicLong(0);
        log.info("========== Test started ==========");
    }

    /**
     * 开始计时
     */
    protected void startTiming() {
        this.startTime.set(System.currentTimeMillis());
    }

    /**
     * 结束计时并返回耗时（毫秒）
     *
     * @return 耗时，单位：毫秒
     */
    protected long endTiming() {
        long elapsed = System.currentTimeMillis() - startTime.get();
        log.info("⏱️  Response time: {}ms", elapsed);
        return elapsed;
    }

    /**
     * 获取请求超时时间配置
     *
     * @return Duration 超时时间
     */
    protected Duration getTimeout() {
        return Duration.ofSeconds(5);
    }

    /**
     * 设置 WebTestClient 的响应超时
     * 用于处理可能会延迟的响应流
     *
     * @return 配置后的 WebTestClient
     */
    protected WebTestClient getConfiguredWebClient() {
        return webClient.mutate()
                .responseTimeout(getTimeout())
                .build();
    }

}
