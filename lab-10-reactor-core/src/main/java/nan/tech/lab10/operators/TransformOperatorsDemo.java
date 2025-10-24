package nan.tech.lab10.operators;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * 转换操作符演示
 *
 * <p><b>教学目标</b>：
 * <ul>
 *   <li>理解 map 的 1对1 转换</li>
 *   <li>掌握 flatMap 的异步链式调用</li>
 *   <li>区分 flatMap, concatMap, switchMap 的区别</li>
 *   <li>掌握 scan 的累积操作</li>
 *   <li>理解 cast 和 then 的用法</li>
 * </ul>
 *
 * <p><b>核心概念</b>：
 * <pre>
 * 转换操作符是改变流中元素的形状和类型的操作符。
 *
 * 关键分类：
 * 1对1转换：
 *   map(T -> R)           - 每个元素转换为另一个元素
 *   cast(Class<R>)        - 强制转换类型
 *
 * 1对N转换：
 *   flatMap(T -> Flux<R>) - 每个元素转换为多个元素（异步）
 *   concatMap(...)        - flatMap的顺序版本（保证顺序）
 *   switchMap(...)        - flatMap的切换版本（取消前面未完成的）
 *
 * 累积转换：
 *   scan(T, (acc, T) -> T) - 累积操作，发送中间结果
 *   reduce(T, (acc, T) -> T) - 累积操作，只发送最后结果
 *
 * 控制转换：
 *   then(Mono)            - 忽略元素，连接到下一个流
 *   thenMany(Flux)        - 忽略元素，连接到多元素流
 * </pre>
 *
 * <p><b>flatMap vs concatMap vs switchMap</b>：
 * <pre>
 * 输入：1, 2, 3
 * 每个元素转换为流：
 *   1 -> [10, 11, 12]（延迟100ms）
 *   2 -> [20, 21, 22]（延迟50ms）
 *   3 -> [30, 31, 32]（延迟25ms）
 *
 * flatMap（交错发送）：
 *   输出顺序不确定，可能是：10, 20, 30, 11, 21, 31, 12, 22, 32
 *   特点：并发处理，速度快，但顺序不保证
 *
 * concatMap（顺序发送）：
 *   输出顺序固定：10, 11, 12, 20, 21, 22, 30, 31, 32
 *   特点：串联处理，顺序保证，速度慢
 *
 * switchMap（取消前面）：
 *   当有新元素到来时，取消前面未完成的流
 *   输出可能是：30, 31, 32（只有最后一个完成）
 *   特点：最新优先，适合用户输入场景
 * </pre>
 *
 * @author 学习者
 * @since Lab-10 Phase 1
 */
public class TransformOperatorsDemo {

    private static final Logger logger = LoggerFactory.getLogger(TransformOperatorsDemo.class);

    /**
     * 演示 1：map() - 1对1的元素转换
     *
     * <p><b>特点</b>：
     * <ul>
     *   <li>同步转换，立即执行</li>
     *   <li>每个元素映射为另一个元素</li>
     *   <li>输入1个元素，输出1个元素</li>
     *   <li>保留原始流的时间特性</li>
     * </ul>
     *
     * <p><b>执行流程</b>：
     * <pre>
     * 输入：1, 2, 3, 4, 5
     * map(x -> x * 10)
     * 输出：10, 20, 30, 40, 50
     * </pre>
     */
    public static void demo1_Map() {
        logger.info("=== Demo 1: map() - 1对1转换 ===");

        Flux.range(1, 5)
            .map(x -> x * 10)
            .subscribe(value -> logger.info("  mapped: {}", value));

        logger.info("");
    }

    /**
     * 演示 2：map() 链式调用 - 多次转换
     *
     * <p><b>特点</b>：
     * <ul>
     *   <li>可以链式调用多个 map</li>
     *   <li>逐步转换数据</li>
     *   <li>每个 map 作用于上一个 map 的输出</li>
     * </ul>
     *
     * <p><b>执行流程</b>：
     * <pre>
     * 输入：1, 2, 3
     * map(x -> x * 2)     → 2, 4, 6
     * map(x -> x + 10)    → 12, 14, 16
     * map(x -> "value:" + x) → "value:12", "value:14", "value:16"
     * </pre>
     */
    public static void demo2_MapChaining() {
        logger.info("=== Demo 2: map() 链式调用 ===");

        Flux.range(1, 3)
            .map(x -> x * 2)          // 第一次转换
            .map(x -> x + 10)         // 第二次转换
            .map(x -> "value:" + x)   // 第三次转换
            .subscribe(value -> logger.info("  result: {}", value));

        logger.info("");
    }

    /**
     * 演示 3：flatMap() - 1对N的异步转换
     *
     * <p><b>特点</b>：
     * <ul>
     *   <li>异步转换，每个元素转换为 Flux/Mono</li>
     *   <li>输入1个元素，输出多个元素</li>
     *   <li>并发处理，顺序不保证</li>
     *   <li>常用于链式 API 调用</li>
     * </ul>
     *
     * <p><b>执行流程</b>：
     * <pre>
     * 输入：1, 2, 3
     * flatMap(x -> Flux.just(x*10, x*10+1, x*10+2))
     * 输出（顺序不定）：10, 11, 12, 20, 21, 22, 30, 31, 32
     * （可能交错）
     * </pre>
     */
    public static void demo3_FlatMap() {
        logger.info("=== Demo 3: flatMap() - 1对N异步转换 ===");

        Flux.range(1, 3)
            .flatMap(x ->
                Flux.just(x * 10, x * 10 + 1, x * 10 + 2)
            )
            .subscribe(value -> logger.info("  flatMapped: {}", value));

        logger.info("");
    }

    /**
     * 演示 4：concatMap() vs flatMap() - 顺序保证
     *
     * <p><b>区别</b>：
     * <ul>
     *   <li>flatMap：并发处理，顺序不保证</li>
     *   <li>concatMap：串联处理，顺序保证</li>
     * </ul>
     *
     * <p><b>执行流程</b>：
     * <pre>
     * 输入：1, 2, 3
     * concatMap(x -> Flux.just(x*10, x*10+1))
     * 输出（顺序固定）：10, 11, 20, 21, 30, 31
     * </pre>
     */
    public static void demo4_ConcatMapVsFlatMap() {
        logger.info("=== Demo 4: concatMap() vs flatMap() - 顺序对比 ===");

        logger.info("concatMap（顺序保证）:");
        Flux.range(1, 3)
            .concatMap(x ->
                Flux.just(x * 10, x * 10 + 1)
            )
            .subscribe(value -> logger.info("  concatMapped: {}", value));

        logger.info("");
    }

    /**
     * 演示 5：switchMap() - 取消前面未完成的流
     *
     * <p><b>特点</b>：
     * <ul>
     *   <li>当有新元素到来时，取消前面的流</li>
     *   <li>只处理最新的元素流</li>
     *   <li>适合用户输入场景（输入变化时取消前面搜索）</li>
     * </ul>
     *
     * <p><b>执行流程</b>：
     * <pre>
     * 输入：1, 2, 3（快速产生）
     * 每个元素转换为延迟流
     * switchMap会：
     *   - 收到1时，启动流1
     *   - 收到2时，取消流1，启动流2
     *   - 收到3时，取消流2，启动流3
     *   - 输出：只有流3的结果
     * </pre>
     */
    public static void demo5_SwitchMap() {
        logger.info("=== Demo 5: switchMap() - 最新优先 ===");

        Flux.range(1, 3)
            .switchMap(x ->
                // 每个元素延迟100ms后产生
                Mono.just(x * 100)
                    .delayElement(Duration.ofMillis(50))
            )
            .subscribe(value -> logger.info("  switched: {}", value));

        logger.info("");
    }

    /**
     * 演示 6：scan() - 累积操作，发送中间结果
     *
     * <p><b>特点</b>：
     * <ul>
     *   <li>类似于 reduce，但发送每一步的结果</li>
     *   <li>常用于累加、累积计算</li>
     *   <li>每个元素都会发送一次</li>
     * </ul>
     *
     * <p><b>执行流程</b>：
     * <pre>
     * 输入：1, 2, 3, 4, 5
     * scan(0, (acc, x) -> acc + x)
     * 输出：1, 3, 6, 10, 15
     * （每步的累加和）
     * </pre>
     */
    public static void demo6_Scan() {
        logger.info("=== Demo 6: scan() - 累积操作 ===");

        Flux.range(1, 5)
            .scan(0, (acc, x) -> acc + x)  // 初始值0，累加
            .subscribe(value -> logger.info("  accumulated: {}", value));

        logger.info("");
    }

    /**
     * 演示 7：reduce() - 累积操作，只发送最后结果
     *
     * <p><b>特点</b>：
     * <ul>
     *   <li>返回 Mono（只有一个结果）</li>
     *   <li>处理流中所有元素，只发送最终结果</li>
     *   <li>常用于聚合计算（求和、计数等）</li>
     * </ul>
     *
     * <p><b>执行流程</b>：
     * <pre>
     * 输入：1, 2, 3, 4, 5
     * reduce(0, (acc, x) -> acc + x)
     * 输出：15（只有最终结果）
     * </pre>
     */
    public static void demo7_Reduce() {
        logger.info("=== Demo 7: reduce() - 只发送最后结果 ===");

        Flux.range(1, 5)
            .reduce(0, (acc, x) -> acc + x)  // 返回 Mono<Integer>
            .subscribe(value -> logger.info("  total sum: {}", value));

        logger.info("");
    }

    /**
     * 演示 8：cast() - 类型转换
     *
     * <p><b>特点</b>：
     * <ul>
     *   <li>将流的类型转换为另一个类型</li>
     *   <li>如果转换失败，发送 onError</li>
     *   <li>常用于处理协变/逆变</li>
     * </ul>
     */
    public static void demo8_Cast() {
        logger.info("=== Demo 8: cast() - 类型转换 ===");

        Flux.just(1, 2, 3)
            .map(x -> (Number) x)           // 转为 Number
            .cast(Integer.class)            // 强制转为 Integer
            .subscribe(value -> logger.info("  casted: {}", value));

        logger.info("");
    }

    /**
     * 演示 9：then() - 忽略元素，连接到下一个流
     *
     * <p><b>特点</b>：
     * <ul>
     *   <li>忽略当前流的所有元素</li>
     *   <li>等待当前流完成后，执行下一个 Mono</li>
     *   <li>常用于顺序执行异步操作</li>
     * </ul>
     */
    public static void demo9_Then() {
        logger.info("=== Demo 9: then() - 顺序执行 ===");

        Flux.range(1, 3)
            .doOnNext(x -> logger.info("  processing: {}", x))
            .then(Mono.just("done"))         // 忽略元素，返回"done"
            .subscribe(value -> logger.info("  result: {}", value));

        logger.info("");
    }

    /**
     * 演示 10：thenMany() - 忽略元素，连接到多元素流
     *
     * <p><b>特点</b>：
     * <ul>
     *   <li>忽略当前流的所有元素</li>
     *   <li>等待当前流完成后，执行 Flux</li>
     *   <li>常用于按顺序执行多个流操作</li>
     * </ul>
     */
    public static void demo10_ThenMany() {
        logger.info("=== Demo 10: thenMany() - 顺序连接 ===");

        Flux.range(1, 2)
            .doOnNext(x -> logger.info("  first: {}", x))
            .thenMany(Flux.range(10, 3))    // 忽略前面的元素，执行新流
            .subscribe(value -> logger.info("  second: {}", value));

        logger.info("");
    }

    /**
     * main 方法：运行所有演示
     */
    public static void main(String[] args) throws InterruptedException {
        logger.info("╔════════════════════════════════════════════════════════════╗");
        logger.info("║       转换操作符演示 - map/flatMap/concatMap/switchMap      ║");
        logger.info("╚════════════════════════════════════════════════════════════╝");
        logger.info("");

        demo1_Map();
        demo2_MapChaining();
        demo3_FlatMap();
        demo4_ConcatMapVsFlatMap();
        demo5_SwitchMap();
        demo6_Scan();
        demo7_Reduce();
        demo8_Cast();
        demo9_Then();
        demo10_ThenMany();

        // demo5 使用了异步调度器，需要等待
        Thread.sleep(500);

        logger.info("");
        logger.info("╔════════════════════════════════════════════════════════════╗");
        logger.info("║                      所有演示完成                            ║");
        logger.info("╚════════════════════════════════════════════════════════════╝");
    }
}
