package nan.tech.lab10.operators;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 组合操作符演示
 *
 * <p><b>教学目标</b>：
 * <ul>
 *   <li>掌握 merge 的并发组合</li>
 *   <li>掌握 concat 的顺序组合</li>
 *   <li>掌握 zip 的元组组合</li>
 *   <li>理解 combineLatest 的最新组合</li>
 *   <li>掌握 withLatestFrom 的非对称组合</li>
 * </ul>
 *
 * <p><b>核心概念</b>：
 * <pre>
 * 组合操作符用于将多个流合并为一个流。
 *
 * 并发组合：
 *   merge(...)         - 并发组合，顺序不定
 *   mergeSequential(...) - 并发拉取，但结果顺序固定
 *
 * 顺序组合：
 *   concat(...)        - 串联组合，顺序保证
 *   concatDelayError(...) - 串联但不立即停止错误
 *
 * 元组组合：
 *   zip(...)           - 配对组合，等长
 *   zipWith(...)       - 与另一个流配对
 *
 * 最新组合：
 *   combineLatest(...) - 取最新元素组合
 *   withLatestFrom(...) - 非对称组合
 *   startWith(...)     - 前置元素
 *   defaultIfEmpty(...) - 默认值
 * </pre>
 *
 * <p><b>组合方式对比</b>：
 * <pre>
 * Flux A: 1----2----3|
 * Flux B: ----a----b----c|
 *
 * merge(A, B):
 *   输出：1----a----2----b----3----c|
 *   说明：并发交错，顺序不定
 *
 * concat(A, B):
 *   输出：1----2----3----a----b----c|
 *   说明：A完成后再执行B
 *
 * zip(A, B):
 *   输出：-----(1,a)---(2,b)|
 *   说明：等长配对，较慢的决定速度
 *
 * combineLatest(A, B):
 *   输出：-----(1,a)--(2,a)--(2,b)--(3,b)--(3,c)|
 *   说明：任意一个产生新值，使用另一个的最新值
 * </pre>
 *
 * @author 学习者
 * @since Lab-10 Phase 1
 */
public class CombineOperatorsDemo {

    private static final Logger logger = LoggerFactory.getLogger(CombineOperatorsDemo.class);

    /**
     * 演示 1：merge() - 并发组合
     *
     * <p><b>特点</b>：
     * <ul>
     *   <li>并发订阅多个流</li>
     *   <li>元素顺序不定（取决于实际发送时间）</li>
     *   <li>最快的决定速度</li>
     * </ul>
     *
     * <p><b>执行流程</b>：
     * <pre>
     * Flux 1: 1, 2, 3
     * Flux 2: 10, 20, 30
     * merge(flux1, flux2)
     * 输出（可能是）：1, 10, 2, 20, 3, 30
     * （顺序不确定，取决于发送时机）
     * </pre>
     */
    public static void demo1_Merge() {
        logger.info("=== Demo 1: merge() - 并发组合 ===");

        Flux<Integer> flux1 = Flux.just(1, 2, 3);
        Flux<Integer> flux2 = Flux.just(10, 20, 30);

        Flux.merge(flux1, flux2)
            .subscribe(value -> logger.info("  merged: {}", value));

        logger.info("");
    }

    /**
     * 演示 2：concat() - 顺序组合
     *
     * <p><b>特点</b>：
     * <ul>
     *   <li>串联多个流</li>
     *   <li>必须等待前一个流完成</li>
     *   <li>顺序固定</li>
     * </ul>
     *
     * <p><b>执行流程</b>：
     * <pre>
     * Flux 1: 1, 2, 3
     * Flux 2: 10, 20, 30
     * concat(flux1, flux2)
     * 输出：1, 2, 3, 10, 20, 30
     * </pre>
     */
    public static void demo2_Concat() {
        logger.info("=== Demo 2: concat() - 顺序组合 ===");

        Flux<Integer> flux1 = Flux.just(1, 2, 3);
        Flux<Integer> flux2 = Flux.just(10, 20, 30);

        Flux.concat(flux1, flux2)
            .subscribe(value -> logger.info("  concatenated: {}", value));

        logger.info("");
    }

    /**
     * 演示 3：zip() - 配对组合
     *
     * <p><b>特点</b>：
     * <ul>
     *   <li>等长配对，形成元组</li>
     *   <li>等待所有流都有新元素才发送</li>
     *   <li>较慢的流决定速度</li>
     * </ul>
     *
     * <p><b>执行流程</b>：
     * <pre>
     * Flux A: 1, 2, 3
     * Flux B: a, b, c
     * zip(A, B)
     * 输出：(1,a), (2,b), (3,c)
     * </pre>
     */
    public static void demo3_Zip() {
        logger.info("=== Demo 3: zip() - 配对组合 ===");

        Flux<Integer> flux1 = Flux.just(1, 2, 3);
        Flux<String> flux2 = Flux.just("a", "b", "c");

        Flux.zip(flux1, flux2)
            .subscribe(tuple -> logger.info("  zipped: {} - {}", tuple.getT1(), tuple.getT2()));

        logger.info("");
    }

    /**
     * 演示 4：combineLatest() - 最新组合
     *
     * <p><b>特点</b>：
     * <ul>
     *   <li>每当任意流产生新元素，使用所有流的最新值组合</li>
     *   <li>需要所有流都至少产生过一个元素</li>
     *   <li>适合多个输入影响输出的场景</li>
     * </ul>
     *
     * <p><b>执行流程</b>：
     * <pre>
     * Flux A: ----1----2----3|
     * Flux B: ------a----b----c|
     *
     * combineLatest(A, B)
     * 输出： ------(1,a)-(2,a)-(2,b)-(3,b)-(3,c)|
     * </pre>
     */
    public static void demo4_CombineLatest() {
        logger.info("=== Demo 4: combineLatest() - 最新组合 ===");

        Flux<Integer> flux1 = Flux.just(1, 2, 3);
        Flux<String> flux2 = Flux.just("a", "b", "c");

        Flux.combineLatest(flux1, flux2, (a, b) -> a + ":" + b)
            .subscribe(value -> logger.info("  combined: {}", value));

        logger.info("");
    }

    /**
     * 演示 5：withLatestFrom() - 非对称组合
     *
     * <p><b>特点</b>：
     * <ul>
     *   <li>主流的每个元素与副流的最新元素组合</li>
     *   <li>副流更新不会触发输出</li>
     *   <li>适合一个流驱动，另一个流提供上下文</li>
     * </ul>
     *
     * <p><b>执行流程</b>：
     * <pre>
     * Main: 1, 2, 3
     * Side: a, b, c
     * main.withLatestFrom(side)
     * 输出：(1,a), (2,b), (3,c)
     * （只看 main 的时机）
     * </pre>
     */
    public static void demo5_WithLatestFrom() {
        logger.info("=== Demo 5: withLatestFrom() - 非对称组合 ===");

        Flux<Integer> main = Flux.just(1, 2, 3);
        Flux<String> side = Flux.just("a", "b", "c");

        main.withLatestFrom(side, (m, s) -> m + ":" + s)
            .subscribe(value -> logger.info("  with latest: {}", value));

        logger.info("");
    }

    /**
     * 演示 6：startWith() - 前置元素
     *
     * <p><b>特点</b>：
     * <ul>
     *   <li>在流的开始前添加元素</li>
     *   <li>可以链式添加多个元素</li>
     * </ul>
     *
     * <p><b>执行流程</b>：
     * <pre>
     * Flux: 2, 3, 4
     * startWith(1)
     * 输出：1, 2, 3, 4
     * </pre>
     */
    public static void demo6_StartWith() {
        logger.info("=== Demo 6: startWith() - 前置元素 ===");

        Flux.just(2, 3, 4)
            .startWith(1)
            .startWith(0)
            .subscribe(value -> logger.info("  with start: {}", value));

        logger.info("");
    }

    /**
     * 演示 7：defaultIfEmpty() - 默认值
     *
     * <p><b>特点</b>：
     * <ul>
     *   <li>如果流为空，发送默认值</li>
     *   <li>常用于处理空流</li>
     * </ul>
     *
     * <p><b>执行流程</b>：
     * <pre>
     * Flux.empty()
     * defaultIfEmpty(0)
     * 输出：0
     * </pre>
     */
    public static void demo7_DefaultIfEmpty() {
        logger.info("=== Demo 7: defaultIfEmpty() - 默认值 ===");

        Flux.empty()
            .defaultIfEmpty(99)
            .subscribe(value -> logger.info("  default: {}", value));

        logger.info("");
    }

    /**
     * 演示 8：switchIfEmpty() - 切换流
     *
     * <p><b>特点</b>：
     * <ul>
     *   <li>如果流为空，切换到另一个流</li>
     *   <li>常用于降级处理</li>
     * </ul>
     *
     * <p><b>执行流程</b>：
     * <pre>
     * Flux.empty()
     * switchIfEmpty(Flux.just(1, 2, 3))
     * 输出：1, 2, 3
     * </pre>
     */
    public static void demo8_SwitchIfEmpty() {
        logger.info("=== Demo 8: switchIfEmpty() - 切换流 ===");

        Flux.empty()
            .switchIfEmpty(Flux.just(10, 20, 30))
            .subscribe(value -> logger.info("  switched: {}", value));

        logger.info("");
    }

    /**
     * 演示 9：flatMapMany() - Mono 转 Flux
     *
     * <p><b>特点</b>：
     * <ul>
     *   <li>将 Mono 的结果转换为 Flux</li>
     *   <li>常用于 API 返回单值但需要流式处理</li>
     * </ul>
     */
    public static void demo9_FlatMapMany() {
        logger.info("=== Demo 9: flatMapMany() - Mono转Flux ===");

        Mono.just("hello")
            .flatMapMany(s -> Flux.just(s, s + " world"))
            .subscribe(value -> logger.info("  flatMapped: {}", value));

        logger.info("");
    }

    /**
     * 演示 10：using() - 资源管理
     *
     * <p><b>特点</b>：
     * <ul>
     *   <li>自动管理资源的创建和释放</li>
     *   <li>确保即使发生异常也会释放资源</li>
     * </ul>
     *
     * <p><b>执行流程</b>：
     * <pre>
     * using(
     *   () -> createResource(),
     *   resource -> Flux.from(resource),
     *   resource -> releaseResource(resource)
     * )
     * </pre>
     */
    public static void demo10_Using() {
        logger.info("=== Demo 10: using() - 资源管理 ===");

        Flux.using(
            () -> {
                logger.info("  资源创建");
                return "resource";
            },
            resource -> Flux.just(1, 2, 3).map(x -> x + 10),
            resource -> logger.info("  资源释放")
        )
        .subscribe(value -> logger.info("  result: {}", value));

        logger.info("");
    }

    /**
     * main 方法：运行所有演示
     */
    public static void main(String[] args) {
        logger.info("╔════════════════════════════════════════════════════════════╗");
        logger.info("║   组合操作符演示 - merge/concat/zip/combineLatest/withLatest ║");
        logger.info("╚════════════════════════════════════════════════════════════╝");
        logger.info("");

        demo1_Merge();
        demo2_Concat();
        demo3_Zip();
        demo4_CombineLatest();
        demo5_WithLatestFrom();
        demo6_StartWith();
        demo7_DefaultIfEmpty();
        demo8_SwitchIfEmpty();
        demo9_FlatMapMany();
        demo10_Using();

        logger.info("");
        logger.info("╔════════════════════════════════════════════════════════════╗");
        logger.info("║                      所有演示完成                            ║");
        logger.info("╚════════════════════════════════════════════════════════════╝");
    }
}
