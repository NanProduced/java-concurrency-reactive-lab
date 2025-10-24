package nan.tech.lab10.creation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.Arrays;
import java.util.stream.Stream;

/**
 * Flux 创建操作符演示
 *
 * <p><b>教学目标</b>：
 * <ul>
 *   <li>理解 Flux 的本质（0 到 N 个元素的 Publisher）</li>
 *   <li>掌握 Flux 的各种创建方式</li>
 *   <li>理解不同创建方式的触发时机和用途</li>
 *   <li>区分冷流和热流的应用场景</li>
 * </ul>
 *
 * <p><b>核心概念</b>：
 * <pre>
 * Flux<T> 是 Reactor 中表示 0 到 N 个元素的 Publisher。
 *
 * 关键特性：
 * 1. 可以发送 0 到多个元素
 * 2. 冷流：每次订阅时都会重新执行创建逻辑
 * 3. 订阅时才会开始执行，不是立即执行
 * 4. 适合用于：数据流、批量数据、事件流
 *
 * 创建方式对比：
 * Flux.just(...)        - 包装多个已知值
 * Flux.fromIterable()   - 从 Iterable（List/Set/etc）创建
 * Flux.fromArray()      - 从数组创建
 * Flux.fromStream()     - 从 Stream 创建
 * Flux.range()          - 创建整数范围
 * Flux.empty()          - 创建空 Flux
 * Flux.error()          - 创建错误 Flux
 * Flux.never()          - 创建永不完成的 Flux
 * Flux.interval()       - 按时间间隔发送元素
 * Flux.create()         - 手动控制（高级用法）
 * Flux.generate()       - 基于状态机的生成
 * Flux.using()          - 资源管理
 * </pre>
 *
 * <p><b>Flux vs Mono</b>：
 * <pre>
 * Mono：最多1个元素
 *   Mono.just(value)          → onNext(value) + onComplete()
 *   Mono.empty()              → onComplete()
 *   Mono.error(exception)     → onError(exception)
 *
 * Flux：0 到 N 个元素
 *   Flux.just(1,2,3)          → onNext(1) + onNext(2) + onNext(3) + onComplete()
 *   Flux.empty()              → onComplete()
 *   Flux.error(exception)     → onError(exception)
 *   Flux.fromIterable([1,2,3])→ onNext(1) + onNext(2) + onNext(3) + onComplete()
 * </pre>
 *
 * @author 学习者
 * @since Lab-10 Phase 1
 */
public class FluxCreationDemo {

    private static final Logger logger = LoggerFactory.getLogger(FluxCreationDemo.class);

    /**
     * 演示 1：Flux.just() - 包装多个已知值
     *
     * <p><b>特点</b>：
     * <ul>
     *   <li>立即可用的多个值</li>
     *   <li>订阅时直接发送，无延迟</li>
     *   <li>冷流：每次订阅都发送相同的值</li>
     *   <li>常用于：固定数据集、常量列表</li>
     * </ul>
     *
     * <p><b>执行流程</b>：
     * <pre>
     * 订阅 → onNext(v1) → onNext(v2) → onNext(v3) → onComplete()
     * </pre>
     */
    public static void demo1_FluxJust() {
        logger.info("=== Demo 1: Flux.just() - 包装多个值 ===");

        Flux<Integer> flux = Flux.just(1, 2, 3, 4, 5);

        logger.info("subscription 1:");
        flux.subscribe(
            value -> logger.info("  received: {}", value),
            error -> logger.error("  error", error),
            () -> logger.info("  completed")
        );

        logger.info("");
    }

    /**
     * 演示 2：Flux.fromIterable() - 从集合创建
     *
     * <p><b>特点</b>：
     * <ul>
     *   <li>从 List、Set 等集合创建</li>
     *   <li>冷流：每次订阅都会迭代集合</li>
     *   <li>常用于：数据库查询结果、API 返回列表</li>
     * </ul>
     *
     * <p><b>执行流程</b>：
     * <pre>
     * 订阅 → 迭代 iterable → onNext(item) × N → onComplete()
     * </pre>
     */
    public static void demo2_FluxFromIterable() {
        logger.info("=== Demo 2: Flux.fromIterable() - 从集合创建 ===");

        var list = Arrays.asList("apple", "banana", "cherry", "date");
        Flux<String> flux = Flux.fromIterable(list);

        logger.info("subscription 1:");
        flux.subscribe(
            value -> logger.info("  received: {}", value),
            error -> logger.error("  error", error),
            () -> logger.info("  completed")
        );

        logger.info("");
    }

    /**
     * 演示 3：Flux.fromArray() - 从数组创建
     *
     * <p><b>特点</b>：
     * <ul>
     *   <li>从数组创建</li>
     *   <li>冷流：每次订阅都会迭代数组</li>
     *   <li>常用于：固定数组、初始化数据</li>
     * </ul>
     *
     * <p><b>执行流程</b>：
     * <pre>
     * 订阅 → 迭代数组 → onNext(item) × N → onComplete()
     * </pre>
     */
    public static void demo3_FluxFromArray() {
        logger.info("=== Demo 3: Flux.fromArray() - 从数组创建 ===");

        Integer[] array = {10, 20, 30, 40, 50};
        Flux<Integer> flux = Flux.fromArray(array);

        logger.info("subscription 1:");
        flux.subscribe(
            value -> logger.info("  received: {}", value),
            error -> logger.error("  error", error),
            () -> logger.info("  completed")
        );

        logger.info("");
    }

    /**
     * 演示 4：Flux.range() - 创建整数范围
     *
     * <p><b>特点</b>：
     * <ul>
     *   <li>创建从 start 到 start+count 的整数序列</li>
     *   <li>冷流：每次订阅都会生成序列</li>
     *   <li>常用于：循环、索引生成</li>
     * </ul>
     *
     * <p><b>执行流程</b>：
     * <pre>
     * 订阅 → onNext(start) → onNext(start+1) → ... → onNext(start+count-1) → onComplete()
     * </pre>
     */
    public static void demo4_FluxRange() {
        logger.info("=== Demo 4: Flux.range() - 整数范围 ===");

        // 从 5 开始，生成 10 个数字（5-14）
        Flux<Integer> flux = Flux.range(5, 10);

        logger.info("subscription 1:");
        flux.subscribe(
            value -> logger.info("  received: {}", value),
            error -> logger.error("  error", error),
            () -> logger.info("  completed")
        );

        logger.info("");
    }

    /**
     * 演示 5：Flux.empty() 和 Flux.error()
     *
     * <p><b>Flux.empty()</b>：
     * <ul>
     *   <li>0 个元素</li>
     *   <li>立即调用 onComplete()</li>
     * </ul>
     *
     * <p><b>Flux.error()</b>：
     * <ul>
     *   <li>立即触发错误</li>
     *   <li>不发送任何元素</li>
     * </ul>
     */
    public static void demo5_FluxEmptyAndError() {
        logger.info("=== Demo 5: Flux.empty() 和 Flux.error() ===");

        logger.info("Flux.empty() - 空流：");
        Flux<String> emptyFlux = Flux.empty();
        emptyFlux.subscribe(
            value -> logger.info("  received: {}", value),
            error -> logger.error("  error", error),
            () -> logger.info("  completed（无元素）")
        );

        logger.info("");
        logger.info("Flux.error() - 错误流：");
        Flux<String> errorFlux = Flux.error(new RuntimeException("Something went wrong!"));
        errorFlux.subscribe(
            value -> logger.info("  received: {}", value),
            error -> logger.error("  error: {}", error.getMessage()),
            () -> logger.info("  completed")
        );

        logger.info("");
    }

    /**
     * 演示 6：Flux.interval() - 按时间间隔发送元素
     *
     * <p><b>特点</b>：
     * <ul>
     *   <li>按固定间隔发送递增的长整数（0, 1, 2, ...）</li>
     *   <li>使用调度器（默认 Schedulers.parallel()）</li>
     *   <li>永不完成（无 onComplete()）</li>
     *   <li>常用于：定时任务、心跳检测</li>
     * </ul>
     *
     * <p><b>执行流程</b>：
     * <pre>
     * 订阅 → 等待间隔 → onNext(0) → 等待间隔 → onNext(1) → ...（无 onComplete）
     * </pre>
     */
    public static void demo6_FluxInterval() {
        logger.info("=== Demo 6: Flux.interval() - 按时间间隔 ===");

        // 每 1 秒发送一个数字，最多发送 5 个（使用 take 限制）
        Flux<Long> flux = Flux.interval(Duration.ofSeconds(1))
            .take(5);  // 只取前 5 个元素

        logger.info("订阅（每秒发送一个元素，共5个）:");
        flux.subscribe(
            value -> logger.info("  received: {}", value),
            error -> logger.error("  error", error),
            () -> logger.info("  completed")
        );

        logger.info("");
    }

    /**
     * 演示 7：Flux.create() - 手动控制流程
     *
     * <p><b>特点</b>：
     * <ul>
     *   <li>手动调用 sink.next(value)、sink.error()、sink.complete()</li>
     *   <li>冷流：每次订阅都会执行 consumer</li>
     *   <li>常用于：事件驱动、回调转反应式</li>
     * </ul>
     *
     * <p><b>执行流程</b>：
     * <pre>
     * 订阅 → 执行 consumer → sink.next() × N → sink.complete() → onComplete()
     * </pre>
     */
    public static void demo7_FluxCreate() {
        logger.info("=== Demo 7: Flux.create() - 手动控制 ===");

        Flux<Integer> flux = Flux.create(sink -> {
            logger.info("Flux.create() consumer 被执行");
            for (int i = 1; i <= 5; i++) {
                sink.next(i * 10);
            }
            sink.complete();
        });

        logger.info("subscription 1:");
        flux.subscribe(
            value -> logger.info("  received: {}", value),
            error -> logger.error("  error", error),
            () -> logger.info("  completed")
        );

        logger.info("");
    }

    /**
     * 演示 8：Flux.generate() - 基于状态机的生成
     *
     * <p><b>特点</b>：
     * <ul>
     *   <li>通过状态函数和生成函数创建流</li>
     *   <li>状态函数：定义初始状态和状态转移</li>
     *   <li>生成函数：根据状态发送元素</li>
     *   <li>适合用于：可预测的序列生成</li>
     * </ul>
     *
     * <p><b>典型模式</b>：
     * <pre>
     * Flux.generate(
     *   () -> initialState,              // state supplier
     *   (state, sink) -> {
     *       sink.next(value);            // 发送元素
     *       return newState;             // 更新状态
     *   }
     * )
     * </pre>
     *
     * <p><b>执行流程</b>：
     * <pre>
     * state = init() → generator(state, sink) → state' → generator(state', sink) → ...
     * </pre>
     */
    public static void demo8_FluxGenerate() {
        logger.info("=== Demo 8: Flux.generate() - 基于状态机 ===");

        // 生成整数序列：使用 generate() 方法
        // state 是一个计数器对象，每次递增
        var flux = Flux.generate(
            () -> 0,                        // 初始状态：从0开始
            (state, sink) -> {
                sink.next(state);           // 发送当前状态
                return state + 1;           // 返回下一个状态
            }
        ).take(8);  // 只取前 8 个

        logger.info("subscription 1（0-7的整数序列）:");
        flux.subscribe(
            value -> logger.info("  received: {}", value),
            error -> logger.error("  error", error),
            () -> logger.info("  completed")
        );

        logger.info("");
    }

    /**
     * 演示 9：Flux.fromStream() - 从 Stream 创建
     *
     * <p><b>特点</b>：
     * <ul>
     *   <li>从 Java Stream 创建</li>
     *   <li>冷流：每次订阅都会创建新的 Stream</li>
     *   <li>注意：Stream 只能使用一次，订阅完后会被关闭</li>
     * </ul>
     *
     * <p><b>执行流程</b>：
     * <pre>
     * 订阅 → 创建 Stream → 迭代 Stream → onNext(item) × N → onComplete()
     * </pre>
     */
    public static void demo9_FluxFromStream() {
        logger.info("=== Demo 9: Flux.fromStream() - 从Stream创建 ===");

        // 从 Iterable 创建（等效于 fromStream）
        var list = java.util.Arrays.asList(100, 200, 300, 400);
        Flux<Integer> flux = Flux.fromIterable(list);

        logger.info("subscription 1:");
        flux.subscribe(
            value -> logger.info("  received: {}", value),
            error -> logger.error("  error", error),
            () -> logger.info("  completed")
        );

        logger.info("");
    }

    /**
     * 演示 10：Flux.never() - 永不完成的流
     *
     * <p><b>特点</b>：
     * <ul>
     *   <li>永不发送元素，永不完成，永不错误</li>
     *   <li>常用于：测试、占位符</li>
     * </ul>
     *
     * <p><b>执行流程</b>：
     * <pre>
     * 订阅 → 什么都不做 → 永远等待
     * </pre>
     */
    public static void demo10_FluxNever() {
        logger.info("=== Demo 10: Flux.never() - 永不完成 ===");

        Flux<String> flux = Flux.never();

        logger.info("订阅（将无限等待）:");
        flux.subscribe(
            value -> logger.info("  received: {}", value),
            error -> logger.error("  error", error),
            () -> logger.info("  completed")
        );

        logger.info("（注意：never() 流永远不会调用 onComplete 或 onError）");
        logger.info("");
    }

    /**
     * 演示 11：Flux vs Mono - 性质对比
     */
    public static void demo11_FluxVsMono() {
        logger.info("=== Demo 11: Flux vs Mono - 对比演示 ===");

        logger.info("Flux（0-N个元素）:");
        Flux.just(1, 2, 3).subscribe(
            value -> logger.info("  Flux received: {}", value),
            error -> logger.error("  Flux error", error),
            () -> logger.info("  Flux completed")
        );

        logger.info("");
        logger.info("Mono（最多1个元素）:");
        reactor.core.publisher.Mono.just(42).subscribe(
            value -> logger.info("  Mono received: {}", value),
            error -> logger.error("  Mono error", error),
            () -> logger.info("  Mono completed")
        );

        logger.info("");
        logger.info("Mono.empty()（0个元素）:");
        reactor.core.publisher.Mono.empty().subscribe(
            value -> logger.info("  Mono received: {}", value),
            error -> logger.error("  Mono error", error),
            () -> logger.info("  Mono empty completed")
        );

        logger.info("");
    }

    /**
     * 演示 12：冷流特性 - 多次订阅
     */
    public static void demo12_ColdStreamMultipleSubscriptions() {
        logger.info("=== Demo 12: 冷流特性 - 多次订阅 ===");

        // 冷流：每次订阅都会重新执行
        final int[] callCount = {0};
        Flux<Integer> flux = Flux.create(sink -> {
            callCount[0]++;
            logger.info("create consumer 被执行（第 {} 次）", callCount[0]);
            sink.next(100 + callCount[0]);
            sink.complete();
        });

        logger.info("subscription 1:");
        flux.subscribe(value -> logger.info("  received: {}", value));

        logger.info("subscription 2:");
        flux.subscribe(value -> logger.info("  received (sub2): {}", value));

        logger.info("subscription 3:");
        flux.subscribe(value -> logger.info("  received (sub3): {}", value));

        logger.info("");
    }

    /**
     * main 方法：运行所有演示
     */
    public static void main(String[] args) throws InterruptedException {
        logger.info("╔════════════════════════════════════════════════════════════╗");
        logger.info("║         Flux 创建操作符演示 - 深入理解 Flux 的创建方式       ║");
        logger.info("╚════════════════════════════════════════════════════════════╝");
        logger.info("");

        // 演示 1-12
        demo1_FluxJust();
        demo2_FluxFromIterable();
        demo3_FluxFromArray();
        demo4_FluxRange();
        demo5_FluxEmptyAndError();
        demo6_FluxInterval();
        demo7_FluxCreate();
        demo8_FluxGenerate();
        demo9_FluxFromStream();
        demo10_FluxNever();
        demo11_FluxVsMono();
        demo12_ColdStreamMultipleSubscriptions();

        // demo6 使用了时间间隔，需要等待
        logger.info("等待异步操作完成（demo6_FluxInterval）...");
        Thread.sleep(6000);

        logger.info("");
        logger.info("╔════════════════════════════════════════════════════════════╗");
        logger.info("║                      所有演示完成                            ║");
        logger.info("╚════════════════════════════════════════════════════════════╝");
    }
}
