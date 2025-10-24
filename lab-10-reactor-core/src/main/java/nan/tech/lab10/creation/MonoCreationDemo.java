package nan.tech.lab10.creation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * Mono 创建操作符演示
 *
 * <p><b>教学目标</b>：
 * <ul>
 *   <li>理解 Mono 的本质（最多发送1个元素的 Publisher）</li>
 *   <li>掌握 Mono 的各种创建方式</li>
 *   <li>理解冷流（Cold Stream）的概念</li>
 *   <li>区分不同创建方式的触发时机</li>
 * </ul>
 *
 * <p><b>核心概念</b>：
 * <pre>
 * Mono<T> 是 Reactor 中表示 0 或 1 个元素的 Publisher。
 *
 * 关键特性：
 * 1. 最多发送1个元素（element）或错误或完成（completion）
 * 2. 冷流：每次订阅时都会重新执行创建逻辑
 * 3. 订阅时才会开始执行，不是立即执行
 * 4. 适合用于：异步操作（async），单个结果（single result），可能的空值（optional）
 *
 * 创建方式对比：
 * Mono.just(T)        - 包装一个已知值（立即可用）
 * Mono.fromValue(T)   - 包装一个已知值（alias for just）
 * Mono.empty()        - 创建空 Mono（0个元素）
 * Mono.error(...)     - 创建错误 Mono（立即错误）
 * Mono.defer(...)     - 延迟创建（订阅时才执行）
 * Mono.fromCallable(...) - 包装 Callable（订阅时执行）
 * Mono.fromOptional(...) - 包装 Optional
 * Mono.fromSupplier(...) - 包装 Supplier（延迟执行）
 * Mono.delay(...)     - 延迟一段时间后发送值
 * Mono.create(...)    - 手动控制（高级用法）
 * </pre>
 *
 * <p><b>冷流 vs 热流</b>：
 * <pre>
 * 冷流（Cold Stream）：
 *   - 每次订阅时都会重新创建和执行
 *   - 示例：Mono.just(), Mono.fromCallable()
 *   - 优点：每个订阅者都能获得完整的流
 *   - 缺点：如果有大量订阅者，会重复执行多次逻辑
 *
 * 热流（Hot Stream）：
 *   - 独立于订阅者存在，所有订阅者共享同一个流
 *   - 示例：使用 share() 或 replay()
 *   - 优点：避免重复执行，节省资源
 *   - 缺点：晚到的订阅者会错过之前的元素
 * </pre>
 *
 * @author 学习者
 * @since Lab-10 Phase 1
 */
public class MonoCreationDemo {

    private static final Logger logger = LoggerFactory.getLogger(MonoCreationDemo.class);

    /**
     * 演示 1：Mono.just() - 包装已知值
     *
     * <p><b>特点</b>：
     * <ul>
     *   <li>立即可用的值</li>
     *   <li>订阅时直接发送，无延迟</li>
     *   <li>冷流：每次订阅都发送相同的值</li>
     *   <li>常用于：固定值、已计算结果</li>
     * </ul>
     *
     * <p><b>执行流程</b>：
     * <pre>
     * 订阅 → onNext("Hello") → onComplete()
     * </pre>
     */
    public static void demo1_MonoJust() {
        logger.info("=== Demo 1: Mono.just() - 包装已知值 ===");

        // 创建一个发送字符串 "Hello, Reactor" 的 Mono
        Mono<String> mono = Mono.just("Hello, Reactor");

        logger.info("subscription 1 - 第一个订阅者");
        mono.subscribe(
            value -> logger.info("received: {}", value),        // onNext
            error -> logger.error("error", error),              // onError
            () -> logger.info("completed")                      // onComplete
        );

        // 演示冷流：再次订阅会重新发送
        logger.info("subscription 2 - 第二个订阅者（冷流，会重新发送）");
        mono.subscribe(
            value -> logger.info("received (sub2): {}", value)
        );

        logger.info("");
    }

    /**
     * 演示 2：Mono.empty() - 创建空 Mono
     *
     * <p><b>特点</b>：
     * <ul>
     *   <li>0个元素</li>
     *   <li>立即调用 onComplete()</li>
     *   <li>常用于：条件分支、可选操作</li>
     * </ul>
     *
     * <p><b>执行流程</b>：
     * <pre>
     * 订阅 → onComplete()
     * （无 onNext 调用）
     * </pre>
     */
    public static void demo2_MonoEmpty() {
        logger.info("=== Demo 2: Mono.empty() - 创建空Mono ===");

        Mono<String> mono = Mono.empty();

        mono.subscribe(
            value -> logger.info("received: {}", value),
            error -> logger.error("error", error),
            () -> logger.info("completed（没有元素发送）")
        );

        logger.info("");
    }

    /**
     * 演示 3：Mono.error() - 创建错误 Mono
     *
     * <p><b>特点</b>：
     * <ul>
     *   <li>立即触发错误</li>
     *   <li>不发送任何元素</li>
     *   <li>常用于：错误处理、异常模拟</li>
     * </ul>
     *
     * <p><b>执行流程</b>：
     * <pre>
     * 订阅 → onError(Exception)
     * </pre>
     */
    public static void demo3_MonoError() {
        logger.info("=== Demo 3: Mono.error() - 创建错误Mono ===");

        Mono<String> mono = Mono.error(
            new IllegalArgumentException("Something went wrong!")
        );

        mono.subscribe(
            value -> logger.info("received: {}", value),
            error -> logger.error("error occurred: {}", error.getMessage()),
            () -> logger.info("completed")
        );

        logger.info("");
    }

    /**
     * 演示 4：Mono.fromCallable() - 包装 Callable（延迟执行）
     *
     * <p><b>特点</b>：
     * <ul>
     *   <li>冷流：订阅时才执行 Callable</li>
     *   <li>可以处理异常（Callable 的 Exception）</li>
     *   <li>常用于：异步操作、数据库查询、API 调用</li>
     * </ul>
     *
     * <p><b>执行流程</b>：
     * <pre>
     * 订阅 → 执行 Callable.call() → onNext(result) → onComplete()
     * </pre>
     *
     * <p><b>冷流演示</b>：
     * 每次订阅时 Callable 都会被调用一次
     */
    public static void demo4_MonoFromCallable() {
        logger.info("=== Demo 4: Mono.fromCallable() - 延迟执行 ===");

        // 创建一个 Callable，每次被调用时会执行里面的逻辑
        Mono<Integer> mono = Mono.fromCallable(() -> {
            logger.info("Callable.call() 被执行!");
            return 42;
        });

        logger.info("subscription 1:");
        mono.subscribe(value -> logger.info("received: {}", value));

        logger.info("subscription 2（冷流，Callable 会再次执行）:");
        mono.subscribe(value -> logger.info("received (sub2): {}", value));

        logger.info("");
    }

    /**
     * 演示 5：Mono.defer() - 延迟创建 Mono
     *
     * <p><b>特点</b>：
     * <ul>
     *   <li>冷流：订阅时才创建新的 Mono</li>
     *   <li>每次订阅都会创建新的 Mono 实例</li>
     *   <li>常用于：条件分支、动态 Mono 创建</li>
     * </ul>
     *
     * <p><b>执行流程</b>：
     * <pre>
     * 订阅 → 执行 supplier → 创建新 Mono → subscribe(new Mono)
     * </pre>
     *
     * <p><b>冷流演示</b>：
     * 每次订阅时都会创建新的 Mono，可能返回不同的值
     */
    public static void demo5_MonoDefer() {
        logger.info("=== Demo 5: Mono.defer() - 延迟创建Mono ===");

        // 使用一个计数器演示每次 defer 都会创建新的 Mono
        final int[] counter = {0};

        Mono<Integer> mono = Mono.defer(() -> {
            counter[0]++;
            logger.info("defer supplier 被执行，创建新的 Mono，count={}", counter[0]);
            return Mono.just(counter[0]);
        });

        logger.info("subscription 1:");
        mono.subscribe(value -> logger.info("received: {}", value));

        logger.info("subscription 2:");
        mono.subscribe(value -> logger.info("received (sub2): {}", value));

        logger.info("subscription 3:");
        mono.subscribe(value -> logger.info("received (sub3): {}", value));

        logger.info("");
    }

    /**
     * 演示 6：Mono.justOrEmpty() - 包装 Optional
     *
     * <p><b>特点</b>：
     * <ul>
     *   <li>Optional.of(value) → onNext(value) + onComplete()</li>
     *   <li>Optional.empty() → onComplete() （无 onNext）</li>
     *   <li>常用于：null safety、可选值</li>
     * </ul>
     *
     * <p><b>执行流程</b>：
     * <pre>
     * Optional.of(value) → onNext(value) → onComplete()
     * Optional.empty() → onComplete()
     * </pre>
     */
    public static void demo6_MonoFromOptional() {
        logger.info("=== Demo 6: Mono.justOrEmpty() - 包装Optional ===");

        // 情况1：Optional 有值
        logger.info("case 1: Optional.of(value)");
        Mono<String> monoWithValue = Mono.justOrEmpty(Optional.of("present value"));
        monoWithValue.subscribe(
            value -> logger.info("received: {}", value),
            error -> logger.error("error", error),
            () -> logger.info("completed")
        );

        // 情况2：Optional 为空
        logger.info("case 2: Optional.empty()");
        Mono<String> monoEmpty = Mono.justOrEmpty(Optional.empty());
        monoEmpty.subscribe(
            value -> logger.info("received: {}", value),
            error -> logger.error("error", error),
            () -> logger.info("completed（无元素）")
        );

        logger.info("");
    }

    /**
     * 演示 7：Mono.delay() - 延迟发送值
     *
     * <p><b>特点</b>：
     * <ul>
     *   <li>等待指定时间后发送元素</li>
     *   <li>使用调度器（默认 Schedulers.parallel()）</li>
     *   <li>常用于：模拟异步操作、超时处理</li>
     * </ul>
     *
     * <p><b>执行流程</b>：
     * <pre>
     * 订阅 → 等待 duration → onNext(value) → onComplete()
     * </pre>
     */
    public static void demo7_MonoDelay() {
        logger.info("=== Demo 7: Mono.delay() - 延迟发送值 ===");

        logger.info("创建延迟 Mono（延迟2秒）");
        long startTime = System.currentTimeMillis();

        Mono<Long> mono = Mono.delay(Duration.ofSeconds(2))
            .map(x -> System.currentTimeMillis() - startTime);

        mono.subscribe(
            elapsedMs -> logger.info("延迟后发送：耗时 {} ms", elapsedMs),
            error -> logger.error("error", error),
            () -> logger.info("completed")
        );

        // 注意：此处可能立即返回，因为异步操作在后台进行
        logger.info("订阅返回（异步操作在后台执行）");
        logger.info("");
    }

    /**
     * 演示 8：冷流 vs 热流 - 使用 share()
     *
     * <p><b>冷流示例</b>：
     * 每个订阅者都会触发一次数据生成
     *
     * <p><b>热流示例</b>：
     * 使用 share() 让所有订阅者共享同一个流
     */
    public static void demo8_ColdVsHotStream() {
        logger.info("=== Demo 8: 冷流 vs 热流 ===");

        // 冷流演示
        logger.info("冷流示例（未使用 share()）：");
        Mono<Integer> coldMono = Mono.fromCallable(() -> {
            logger.info("  Callable.call() 被执行（会执行2次）");
            return 100;
        });

        logger.info("  subscription 1:");
        coldMono.subscribe(v -> logger.info("    received: {}", v));

        logger.info("  subscription 2:");
        coldMono.subscribe(v -> logger.info("    received (sub2): {}", v));

        // 热流演示
        logger.info("");
        logger.info("热流示例（使用 share()）：");
        Mono<Integer> hotMono = Mono.fromCallable(() -> {
            logger.info("  Callable.call() 被执行（只执行1次）");
            return 200;
        }).share();

        logger.info("  subscription 1:");
        hotMono.subscribe(v -> logger.info("    received: {}", v));

        logger.info("  subscription 2（共享，Callable 不会再执行）:");
        hotMono.subscribe(v -> logger.info("    received (sub2): {}", v));

        logger.info("");
    }

    /**
     * 演示 9：Mono.create() - 手动控制流程（高级）
     *
     * <p><b>特点</b>：
     * <ul>
     *   <li>手动调用 sink.success(value) 或 sink.error() 或 sink.complete()</li>
     *   <li>冷流：每次订阅都会执行 consumer</li>
     *   <li>常用于：回调转反应式、事件驱动</li>
     * </ul>
     *
     * <p><b>执行流程</b>：
     * <pre>
     * 订阅 → 执行 consumer → sink.success(value) → onNext + onComplete()
     * </pre>
     */
    public static void demo9_MonoCreate() {
        logger.info("=== Demo 9: Mono.create() - 手动控制 ===");

        Mono<String> mono = Mono.create(sink -> {
            logger.info("Mono.create() consumer 被执行");
            try {
                // 模拟某个操作
                String result = "created value";
                sink.success(result);  // 发送成功值
            } catch (Exception e) {
                sink.error(e);  // 发送错误
            }
        });

        mono.subscribe(
            value -> logger.info("received: {}", value),
            error -> logger.error("error", error),
            () -> logger.info("completed")
        );

        logger.info("");
    }

    /**
     * 演示 10：从 null 安全地处理可能为 null 的值
     *
     * <p><b>常见模式</b>：
     * <pre>
     * 不安全：Mono.just(null) → NullPointerException
     * 安全：Mono.justOrEmpty(value) → 如果为 null 则返回 empty Mono
     * </pre>
     */
    public static void demo10_NullSafety() {
        logger.info("=== Demo 10: Null Safety ===");

        // 错误做法（会抛异常）
        logger.info("错误做法：Mono.just(null) - 会抛异常");
        try {
            Mono<String> mono = Mono.just(null);
            logger.info("不会执行到这里");
        } catch (NullPointerException e) {
            logger.info("捕获异常：{}", e.getMessage());
        }

        // 正确做法
        logger.info("");
        logger.info("正确做法：Mono.justOrEmpty(null)");
        Mono<String> mono = Mono.justOrEmpty(null);
        mono.subscribe(
            value -> logger.info("received: {}", value),
            error -> logger.error("error", error),
            () -> logger.info("completed（empty Mono）")
        );

        logger.info("");
    }

    /**
     * main 方法：运行所有演示
     */
    public static void main(String[] args) throws InterruptedException {
        logger.info("╔════════════════════════════════════════════════════════════╗");
        logger.info("║         Mono 创建操作符演示 - 深入理解 Mono 的创建方式        ║");
        logger.info("╚════════════════════════════════════════════════════════════╝");
        logger.info("");

        // 演示 1-10
        demo1_MonoJust();
        demo2_MonoEmpty();
        demo3_MonoError();
        demo4_MonoFromCallable();
        demo5_MonoDefer();
        demo6_MonoFromOptional();
        demo7_MonoDelay();
        demo8_ColdVsHotStream();
        demo9_MonoCreate();
        demo10_NullSafety();

        // demo7 使用了异步调度器，需要等待
        logger.info("等待异步操作完成（demo7_MonoDelay）...");
        Thread.sleep(3000);

        logger.info("");
        logger.info("╔════════════════════════════════════════════════════════════╗");
        logger.info("║                      所有演示完成                            ║");
        logger.info("╚════════════════════════════════════════════════════════════╝");
    }
}
