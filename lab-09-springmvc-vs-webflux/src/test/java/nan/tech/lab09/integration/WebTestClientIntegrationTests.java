package nan.tech.lab09.integration;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Phase 3 集成测试套件
 *
 * <h2>测试范围</h2>
 * <ul>
 *     <li>Phase 3.1: R2DBC 反应式数据库操作 (User CRUD)</li>
 *     <li>Phase 3.2: Redis 反应式缓存操作 (String/Hash/List)</li>
 *     <li>Phase 3.3: Kafka 反应式消息队列操作 (Producer模拟)</li>
 * </ul>
 *
 * <h2>测试策略</h2>
 * <pre>
 * 1. 函数式测试: 验证每个端点的正确性
 * 2. 端到端测试: 验证多个步骤的工作流
 * 3. 性能基线: 收集响应时间数据
 * </pre>
 *
 * @author Claude Code + Lab-09 团队
 * @version 1.0.0
 * @since 2025-10-24
 */
@Slf4j
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
public class WebTestClientIntegrationTests {

    @Autowired
    private WebTestClient webClient;

    private List<Long> responseTimes;

    @BeforeEach
    void setUp() {
        responseTimes = new ArrayList<>();
    }

    /**
     * Phase 3.1: R2DBC 反应式数据库演示测试
     */
    @Nested
    @DisplayName("Phase 3.1: R2DBC 反应式数据库测试")
    class R2DBCIntegrationTests {

        @Test
        @DisplayName("Demo 1: 创建新用户")
        void testCreateUser() {
            long startTime = System.currentTimeMillis();

            webClient
                    .post()
                    .uri("/integration/db/create")
                    .body(BodyInserters.fromValue("{\"username\":\"testUser\",\"email\":\"test@example.com\",\"age\":30}"))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class)
                    .consumeWith(response -> {
                        long elapsed = System.currentTimeMillis() - startTime;
                        responseTimes.add(elapsed);
                        log.info("✅ Demo 1 (创建用户): {} ms", elapsed);
                        assert response.getResponseBody() != null;
                    });
        }

        @Test
        @DisplayName("Demo 2: 查询所有用户")
        void testQueryAllUsers() {
            long startTime = System.currentTimeMillis();

            webClient
                    .get()
                    .uri("/integration/db/all")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBodyList(Object.class)
                    .consumeWith(response -> {
                        long elapsed = System.currentTimeMillis() - startTime;
                        responseTimes.add(elapsed);
                        log.info("✅ Demo 2 (查询所有用户): {} ms, 用户数: {}",
                                elapsed,
                                response.getResponseBody() != null ? response.getResponseBody().size() : 0);
                    });
        }

        @Test
        @DisplayName("Demo 3: 按 ID 查询用户")
        void testQueryUserById() {
            long startTime = System.currentTimeMillis();

            webClient
                    .get()
                    .uri("/integration/db/1")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class)
                    .consumeWith(response -> {
                        long elapsed = System.currentTimeMillis() - startTime;
                        responseTimes.add(elapsed);
                        log.info("✅ Demo 3 (按 ID 查询用户): {} ms", elapsed);
                    });
        }

        @Test
        @DisplayName("Demo 4: 年龄范围查询")
        void testQueryByAgeRange() {
            long startTime = System.currentTimeMillis();

            webClient
                    .get()
                    .uri("/integration/db/age-range?minAge=20&maxAge=40")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBodyList(Object.class)
                    .consumeWith(response -> {
                        long elapsed = System.currentTimeMillis() - startTime;
                        responseTimes.add(elapsed);
                        log.info("✅ Demo 4 (年龄范围查询): {} ms", elapsed);
                    });
        }

        @Test
        @DisplayName("Demo 7: 关键字搜索")
        void testKeywordSearch() {
            long startTime = System.currentTimeMillis();

            webClient
                    .get()
                    .uri("/integration/db/search?keyword=alice")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBodyList(Object.class)
                    .consumeWith(response -> {
                        long elapsed = System.currentTimeMillis() - startTime;
                        responseTimes.add(elapsed);
                        log.info("✅ Demo 7 (关键字搜索): {} ms", elapsed);
                    });
        }

        @Test
        @DisplayName("Demo 8: 统计用户数量")
        void testCountUsers() {
            long startTime = System.currentTimeMillis();

            webClient
                    .get()
                    .uri("/integration/db/count")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class)
                    .consumeWith(response -> {
                        long elapsed = System.currentTimeMillis() - startTime;
                        responseTimes.add(elapsed);
                        log.info("✅ Demo 8 (统计用户数量): {} ms, 结果: {}",
                                elapsed, response.getResponseBody());
                    });
        }
    }

    /**
     * Phase 3.2: Redis 反应式缓存演示测试
     */
    @Nested
    @DisplayName("Phase 3.2: Redis 反应式缓存测试")
    class RedisCacheIntegrationTests {

        @Test
        @DisplayName("Demo 1: 设置缓存字符串")
        void testSetString() {
            long startTime = System.currentTimeMillis();

            webClient
                    .post()
                    .uri("/integration/cache/string?key=user:1&value=Alice")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class)
                    .consumeWith(response -> {
                        long elapsed = System.currentTimeMillis() - startTime;
                        responseTimes.add(elapsed);
                        log.info("✅ Demo 1 (设置缓存字符串): {} ms", elapsed);
                    });
        }

        @Test
        @DisplayName("Demo 2: 获取缓存字符串")
        void testGetString() {
            long startTime = System.currentTimeMillis();

            webClient
                    .get()
                    .uri("/integration/cache/string?key=user:1")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class)
                    .consumeWith(response -> {
                        long elapsed = System.currentTimeMillis() - startTime;
                        responseTimes.add(elapsed);
                        log.info("✅ Demo 2 (获取缓存字符串): {} ms", elapsed);
                    });
        }

        @Test
        @DisplayName("Demo 3: 设置哈希")
        void testSetHash() {
            long startTime = System.currentTimeMillis();

            webClient
                    .post()
                    .uri("/integration/cache/hash?key=user:1&field=name&value=Alice")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class)
                    .consumeWith(response -> {
                        long elapsed = System.currentTimeMillis() - startTime;
                        responseTimes.add(elapsed);
                        log.info("✅ Demo 3 (设置哈希): {} ms", elapsed);
                    });
        }

        @Test
        @DisplayName("Demo 4: 获取所有哈希条目")
        void testGetHashAll() {
            long startTime = System.currentTimeMillis();

            webClient
                    .get()
                    .uri("/integration/cache/hash?key=user:1")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBodyList(Object.class)
                    .consumeWith(response -> {
                        long elapsed = System.currentTimeMillis() - startTime;
                        responseTimes.add(elapsed);
                        log.info("✅ Demo 4 (获取所有哈希条目): {} ms", elapsed);
                    });
        }

        @Test
        @DisplayName("Demo 5: 向列表添加项")
        void testListPush() {
            long startTime = System.currentTimeMillis();

            webClient
                    .post()
                    .uri("/integration/cache/list/push?key=queue&value=task1")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class)
                    .consumeWith(response -> {
                        long elapsed = System.currentTimeMillis() - startTime;
                        responseTimes.add(elapsed);
                        log.info("✅ Demo 5 (向列表添加项): {} ms", elapsed);
                    });
        }

        @Test
        @DisplayName("Demo 6: 获取列表范围")
        void testListRange() {
            long startTime = System.currentTimeMillis();

            webClient
                    .get()
                    .uri("/integration/cache/list/range?key=queue&start=0&end=-1")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBodyList(String.class)
                    .consumeWith(response -> {
                        long elapsed = System.currentTimeMillis() - startTime;
                        responseTimes.add(elapsed);
                        log.info("✅ Demo 6 (获取列表范围): {} ms", elapsed);
                    });
        }

        @Test
        @DisplayName("Demo 8: 获取 TTL")
        void testGetTTL() {
            long startTime = System.currentTimeMillis();

            webClient
                    .get()
                    .uri("/integration/cache/ttl?key=user:1")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class)
                    .consumeWith(response -> {
                        long elapsed = System.currentTimeMillis() - startTime;
                        responseTimes.add(elapsed);
                        log.info("✅ Demo 8 (获取 TTL): {} ms", elapsed);
                    });
        }
    }

    /**
     * Phase 3.3: Kafka 反应式消息队列演示测试
     */
    @Nested
    @DisplayName("Phase 3.3: Kafka 反应式消息队列测试")
    class KafkaMessagingIntegrationTests {

        @Test
        @DisplayName("Demo 1: 发送用户事件")
        void testSendUserEvent() {
            long startTime = System.currentTimeMillis();

            webClient
                    .post()
                    .uri("/integration/messaging/user-event?userId=user123&action=created")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class)
                    .consumeWith(response -> {
                        long elapsed = System.currentTimeMillis() - startTime;
                        responseTimes.add(elapsed);
                        log.info("✅ Demo 1 (发送用户事件): {} ms", elapsed);
                        assert response.getResponseBody() != null;
                    });
        }

        @Test
        @DisplayName("Demo 2: 发送订单事件")
        void testSendOrderEvent() {
            long startTime = System.currentTimeMillis();

            webClient
                    .post()
                    .uri("/integration/messaging/order-event?orderId=order123&status=paid&amount=99.99")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class)
                    .consumeWith(response -> {
                        long elapsed = System.currentTimeMillis() - startTime;
                        responseTimes.add(elapsed);
                        log.info("✅ Demo 2 (发送订单事件): {} ms", elapsed);
                    });
        }

        @Test
        @DisplayName("Demo 3: 发送批量消息")
        void testSendBatchMessages() {
            long startTime = System.currentTimeMillis();

            webClient
                    .post()
                    .uri("/integration/messaging/batch?topic=user-events&count=5")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class)
                    .consumeWith(response -> {
                        long elapsed = System.currentTimeMillis() - startTime;
                        responseTimes.add(elapsed);
                        log.info("✅ Demo 3 (发送批量消息): {} ms", elapsed);
                    });
        }

        @Test
        @DisplayName("Demo 4: 缓冲消息")
        void testBufferMessage() {
            long startTime = System.currentTimeMillis();

            webClient
                    .post()
                    .uri("/integration/messaging/buffer?messageId=msg1&content=Hello")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class)
                    .consumeWith(response -> {
                        long elapsed = System.currentTimeMillis() - startTime;
                        responseTimes.add(elapsed);
                        log.info("✅ Demo 4 (缓冲消息): {} ms", elapsed);
                    });
        }

        @Test
        @DisplayName("Demo 5: 查询缓冲区")
        void testGetBufferedMessages() {
            long startTime = System.currentTimeMillis();

            webClient
                    .get()
                    .uri("/integration/messaging/buffer")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Object.class)
                    .consumeWith(response -> {
                        long elapsed = System.currentTimeMillis() - startTime;
                        responseTimes.add(elapsed);
                        log.info("✅ Demo 5 (查询缓冲区): {} ms", elapsed);
                    });
        }

        @Test
        @DisplayName("Demo 6: 刷新缓冲区")
        void testFlushBuffer() {
            long startTime = System.currentTimeMillis();

            webClient
                    .post()
                    .uri("/integration/messaging/flush?topic=user-events")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class)
                    .consumeWith(response -> {
                        long elapsed = System.currentTimeMillis() - startTime;
                        responseTimes.add(elapsed);
                        log.info("✅ Demo 6 (刷新缓冲区): {} ms", elapsed);
                    });
        }

        @Test
        @DisplayName("Demo 7: 发送消息(支持重试)")
        void testSendWithRetry() {
            long startTime = System.currentTimeMillis();

            webClient
                    .post()
                    .uri("/integration/messaging/with-retry?topic=order-events&message=test")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class)
                    .consumeWith(response -> {
                        long elapsed = System.currentTimeMillis() - startTime;
                        responseTimes.add(elapsed);
                        log.info("✅ Demo 7 (发送消息-重试): {} ms", elapsed);
                    });
        }

        @Test
        @DisplayName("Demo 8: 获取消息统计")
        void testGetMessagingStats() {
            long startTime = System.currentTimeMillis();

            webClient
                    .get()
                    .uri("/integration/messaging/stats")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class)
                    .consumeWith(response -> {
                        long elapsed = System.currentTimeMillis() - startTime;
                        responseTimes.add(elapsed);
                        log.info("✅ Demo 8 (获取消息统计): {} ms\n{}", elapsed, response.getResponseBody());
                    });
        }
    }

    /**
     * 性能基线数据分析
     */
    @Nested
    @DisplayName("性能基线分析")
    class PerformanceBaseline {

        @Test
        @DisplayName("汇总性能指标")
        void summarizePerformanceMetrics() {
            if (responseTimes.isEmpty()) {
                log.info("⚠️ 未收集到响应时间数据");
                return;
            }

            long minTime = responseTimes.stream().mapToLong(Long::longValue).min().orElse(0);
            long maxTime = responseTimes.stream().mapToLong(Long::longValue).max().orElse(0);
            double avgTime = responseTimes.stream().mapToLong(Long::longValue).average().orElse(0.0);

            log.info("");
            log.info("╔════════════════════════════════════════════╗");
            log.info("║      Phase 3 性能基线汇总                  ║");
            log.info("╠════════════════════════════════════════════╣");
            log.info("║ 总请求数:        {} 个", responseTimes.size());
            log.info("║ 最小响应时间:    {} ms", minTime);
            log.info("║ 最大响应时间:    {} ms", maxTime);
            log.info("║ 平均响应时间:    {:.2f} ms", avgTime);
            log.info("╚════════════════════════════════════════════╝");
            log.info("");
        }
    }
}
