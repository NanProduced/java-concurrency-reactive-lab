package nan.tech.lab10.testing;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.test.publisher.TestPublisher;

import java.time.Duration;

/**
 * StepVerifier 测试框架演示
 *
 * <p><b>教学目标</b>：
 * <ul>
 *   <li>理解 StepVerifier 的作用和使用方式</li>
 *   <li>掌握基础验证方法（expectNext/expectComplete）</li>
 *   <li>掌握高级验证方法（expectError/verifyThenAssertThat）</li>
 *   <li>理解虚拟时间测试的概念</li>
 *   <li>掌握背压测试的实现</li>
 * </ul>
 *
 * <p><b>核心概念</b>：
 * <pre>
 * StepVerifier 是 Reactor 提供的用于验证反应式流的测试工具。
 *
 * 工作原理：
 * 1. 创建一个反应式流（Publisher）
 * 2. 通过 StepVerifier.create() 或 .expectXxx() 定义期望
 * 3. 通过 .verify() 或 .verifyThenAssertThat() 执行验证
 * 4. 比对实际值和期望值，验证成功或失败
 *
 * 验证方法分类：
 *
 * 基础验证：
 *   expectNext(T)              - 验证下一个元素
 *   expectNextCount(long)      - 验证后续N个元素存在（不关心具体值）
 *   expectComplete()           - 验证流完成
 *   expectError(Class<E>)      - 验证错误
 *   expectErrorMessage(String) - 验证错误消息
 *
 * 高级验证：
 *   assertNext(Consumer<T>)    - 断言下一个元素
 *   consumeNextWith(Consumer)  - 消费下一个元素
 *   expectRecordedMatches(...) - 匹配记录的值
 *
 * 执行方式：
 *   verify()                   - 同步验证，阻塞直到完成或超时
 *   verifyThenAssertThat()    - 验证后额外断言
 *
 * 虚拟时间：
 *   withVirtualTime()         - 使用虚拟时间，加速延迟流
 *   thenAwait(Duration)       - 虚拟时间等待
 *
 * 背压测试：
 *   thenRequest(long)         - 请求元素数
 *   consumeRecordedWith()     - 消费记录的元素
 * </pre>
 *
 * @author 学习者
 * @since Lab-10 Phase 1
 */
public class StepVerifierDemo {

    private static final Logger logger = LoggerFactory.getLogger(StepVerifierDemo.class);

    /**
     * 演示 1：基础验证 - 验证元素和完成
     *
     * <p><b>特点</b>：
     * <ul>
     *   <li>验证流发送的所有元素</li>
     *   <li>验证流是否正确完成</li>
     *   <li>使用 expectNext() 验证每个元素</li>
     * </ul>
     */
    @Test
    public void demo1_BasicExpectNext() {
        logger.info("=== Demo 1: 基础验证 - expectNext ===");

        Flux.just(1, 2, 3)
            .doOnNext(x -> logger.info("  emitted: {}", x))
            .as(flux -> StepVerifier.create(flux)
                .expectNext(1)
                .expectNext(2)
                .expectNext(3)
                .expectComplete()
                .verify()
            );

        logger.info("✅ 验证通过: 元素正确，流正确完成");
        logger.info("");
    }

    /**
     * 演示 2：验证元素计数
     *
     * <p><b>特点</b>：
     * <ul>
     *   <li>只验证元素数量，不关心具体值</li>
     *   <li>适合大数据量验证</li>
     * </ul>
     */
    @Test
    public void demo2_ExpectNextCount() {
        logger.info("=== Demo 2: 验证元素计数 ===");

        Flux.range(1, 100)
            .doOnNext(x -> {
                if (x <= 3 || x >= 98) {
                    logger.info("  emitted: {}", x);
                }
            })
            .as(flux -> StepVerifier.create(flux)
                .expectNextCount(100)  // 验证恰好100个元素
                .expectComplete()
                .verify()
            );

        logger.info("✅ 验证通过: 元素计数正确");
        logger.info("");
    }

    /**
     * 演示 3：验证错误
     *
     * <p><b>特点</b>：
     * <ul>
     *   <li>验证流发送正确的错误</li>
     *   <li>可以验证错误类型和消息</li>
     * </ul>
     */
    @Test
    public void demo3_ExpectError() {
        logger.info("=== Demo 3: 验证错误 ===");

        Flux.<Integer>error(new IllegalArgumentException("Invalid input"))
            .doOnError(e -> logger.info("  error: {}", e.getMessage()))
            .as(flux -> StepVerifier.create(flux)
                .expectError(IllegalArgumentException.class)
                .verify()
            );

        logger.info("✅ 验证通过: 错误类型正确");
        logger.info("");
    }

    /**
     * 演示 4：验证错误消息
     *
     * <p><b>特点</b>：
     * <ul>
     *   <li>验证错误的具体消息</li>
     *   <li>更精确的错误验证</li>
     * </ul>
     */
    @Test
    public void demo4_ExpectErrorMessage() {
        logger.info("=== Demo 4: 验证错误消息 ===");

        Flux.<Integer>error(new IllegalArgumentException("User not found"))
            .as(flux -> StepVerifier.create(flux)
                .expectErrorMessage("User not found")
                .verify()
            );

        logger.info("✅ 验证通过: 错误消息正确");
        logger.info("");
    }

    /**
     * 演示 5：assertNext - 断言元素
     *
     * <p><b>特点</b>：
     * <ul>
     *   <li>对元素进行自定义断言</li>
     *   <li>可以验证元素的属性</li>
     * </ul>
     */
    @Test
    public void demo5_AssertNext() {
        logger.info("=== Demo 5: 断言元素 ===");

        Flux.range(1, 5)
            .map(x -> x * 2)
            .as(flux -> StepVerifier.create(flux)
                .assertNext(x -> {
                    logger.info("  checking: {}", x);
                    assert x == 2 : "first element should be 2";
                })
                .assertNext(x -> {
                    assert x == 4 : "second element should be 4";
                })
                .expectNextCount(3)  // 3, 4, 5
                .expectComplete()
                .verify()
            );

        logger.info("✅ 验证通过: 元素断言正确");
        logger.info("");
    }

    /**
     * 演示 6：verifyThenAssertThat - 验证后返回断言对象
     *
     * <p><b>特点</b>：
     * <ul>
     *   <li>验证流后，返回Assertions对象用于额外检查</li>
     *   <li>可以对验证结果进行进一步的操作</li>
     * </ul>
     */
    @Test
    public void demo6_VerifyThenAssertThat() {
        logger.info("=== Demo 6: 验证后返回Assertions对象 ===");

        StepVerifier.Assertions result = Flux.range(1, 3)
            .as(flux -> StepVerifier.create(flux)
                .expectNext(1, 2, 3)
                .expectComplete()
                .verifyThenAssertThat()
            );

        // verifyThenAssertThat()返回Assertions对象，可用于额外检查
        logger.info("  Assertions对象类型: {}", result.getClass().getSimpleName());

        logger.info("✅ 验证通过: 可以进行验证后的操作");
        logger.info("");
    }

    /**
     * 演示 7：虚拟时间测试
     *
     * <p><b>特点</b>：
     * <ul>
     *   <li>加速延迟流的测试</li>
     *   <li>不实际等待，而是模拟时间流逝</li>
     *   <li>适合测试有延迟的流</li>
     * </ul>
     */
    @Test
    public void demo7_VirtualTime() {
        logger.info("=== Demo 7: 虚拟时间测试 ===");

        StepVerifier.withVirtualTime(() ->
                Flux.interval(Duration.ofSeconds(1))
                    .take(3)
                    .doOnNext(x -> logger.info("  virtual tick: {}", x))
            )
            .expectSubscription()
            .expectNoEvent(Duration.ofSeconds(1))      // 等待1秒
            .expectNext(0L)                            // 第一个元素
            .thenAwait(Duration.ofSeconds(1))          // 继续等待1秒
            .expectNext(1L)                            // 第二个元素
            .thenAwait(Duration.ofSeconds(1))          // 继续等待1秒
            .expectNext(2L)                            // 第三个元素
            .expectComplete()
            .verify();

        logger.info("✅ 虚拟时间测试通过: 延迟流测试成功");
        logger.info("");
    }

    /**
     * 演示 8：背压测试
     *
     * <p><b>特点</b>：
     * <ul>
     *   <li>验证发布者是否尊重背压</li>
     *   <li>控制订阅者的请求</li>
     *   <li>验证发送的元素是否超过请求</li>
     * </ul>
     */
    @Test
    public void demo8_BackpressureTest() {
        logger.info("=== Demo 8: 背压测试 ===");

        Flux.range(1, 10)
            .doOnNext(x -> logger.info("  emitted: {}", x))
            .as(flux -> StepVerifier.create(
                flux,
                1  // 初始需求数为1
            )
            .expectNext(1)              // 接收第一个
            .thenRequest(2)             // 请求2个
            .expectNext(2, 3)           // 接收2个
            .thenRequest(5)             // 请求5个
            .expectNextCount(5)         // 接收5个
            .thenRequest(2)             // 请求2个
            .expectNextCount(2)         // 接收最后2个
            .expectComplete()
            .verify()
            );

        logger.info("✅ 背压测试通过: 发布者尊重背压");
        logger.info("");
    }

    /**
     * 演示 9：Mono 验证
     *
     * <p><b>特点</b>：
     * <ul>
     *   <li>验证 Mono（最多1个元素）</li>
     *   <li>简化的验证方式</li>
     * </ul>
     */
    @Test
    public void demo9_MonoVerification() {
        logger.info("=== Demo 9: Mono 验证 ===");

        Mono.just(42)
            .doOnNext(x -> logger.info("  mono value: {}", x))
            .as(mono -> StepVerifier.create(mono)
                .expectNext(42)
                .expectComplete()
                .verify()
            );

        logger.info("✅ Mono 验证通过");
        logger.info("");
    }

    /**
     * 演示 10：自定义流的验证
     *
     * <p><b>特点</b>：
     * <ul>
     *   <li>使用 TestPublisher 创建自定义流</li>
     *   <li>精确控制流的行为</li>
     * </ul>
     */
    @Test
    public void demo10_TestPublisher() {
        logger.info("=== Demo 10: TestPublisher 验证 ===");

        TestPublisher<String> testPublisher = TestPublisher.create();

        StepVerifier.create(testPublisher)
            .expectSubscription()
            .then(() -> {
                logger.info("  sending: hello");
                testPublisher.next("hello");
            })
            .expectNext("hello")
            .then(() -> {
                logger.info("  sending: world");
                testPublisher.next("world");
            })
            .expectNext("world")
            .then(() -> {
                logger.info("  completing");
                testPublisher.complete();
            })
            .expectComplete()
            .verify();

        logger.info("✅ TestPublisher 验证通过");
        logger.info("");
    }

    /**
     * 演示 11：消费元素
     *
     * <p><b>特点</b>：
     * <ul>
     *   <li>使用 consumeNextWith 消费每个元素</li>
     *   <li>对元素执行自定义操作</li>
     * </ul>
     */
    @Test
    public void demo11_ConsumeNext() {
        logger.info("=== Demo 11: 消费元素 ===");

        Flux.range(1, 3)
            .map(x -> x * 10)
            .as(flux -> StepVerifier.create(flux)
                .consumeNextWith(x -> {
                    logger.info("  consumed: {}", x);
                    assert x > 0 : "value should be positive";
                })
                .consumeNextWith(x -> logger.info("  consumed: {}", x))
                .consumeNextWith(x -> logger.info("  consumed: {}", x))
                .expectComplete()
                .verify()
            );

        logger.info("✅ 元素消费验证通过");
        logger.info("");
    }

    /**
     * 演示 12：超时控制
     *
     * <p><b>特点</b>：
     * <ul>
     *   <li>验证时的超时控制</li>
     *   <li>防止验证无限等待</li>
     * </ul>
     */
    @Test
    public void demo12_Timeout() {
        logger.info("=== Demo 12: 超时控制 ===");

        Flux.range(1, 5)
            .doOnNext(x -> logger.info("  emitted: {}", x))
            .as(flux -> StepVerifier.create(flux)
                .expectNextCount(5)
                .expectComplete()
                .verify(Duration.ofSeconds(1))  // 1秒超时
            );

        logger.info("✅ 超时验证通过: 流在1秒内完成");
        logger.info("");
    }
}
