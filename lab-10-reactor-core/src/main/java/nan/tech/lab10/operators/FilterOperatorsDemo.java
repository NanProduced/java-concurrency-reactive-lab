package nan.tech.lab10.operators;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.time.Duration;

/**
 * 过滤操作符演示
 *
 * <p><b>教学目标</b>：
 * <ul>
 *   <li>掌握 filter 的条件过滤</li>
 *   <li>理解 distinct 的去重机制</li>
 *   <li>掌握 take/skip 的元素限制</li>
 *   <li>理解 first/last 的特殊处理</li>
 *   <li>掌握 timeout 的超时控制</li>
 * </ul>
 *
 * <p><b>核心概念</b>：
 * <pre>
 * 过滤操作符用于筛选、限制和控制流中的元素。
 *
 * 基本过滤：
 *   filter(T -> boolean)          - 条件过滤
 *   filterWhen(T -> Mono<boolean>) - 异步条件过滤
 *   distinct()                     - 去重（全局记忆）
 *   distinctUntilChanged()        - 去重（只去掉相邻重复）
 *
 * 元素限制：
 *   take(n)                       - 只取前 N 个
 *   skip(n)                       - 跳过前 N 个
 *   takeLast(n)                   - 只取最后 N 个
 *   skipLast(n)                   - 跳过最后 N 个
 *
 * 特殊选择：
 *   first()                       - 只取第一个
 *   last()                        - 只取最后一个
 *   elementAt(index)              - 取第 index 个
 *
 * 时间控制：
 *   timeout(Duration)             - 超时控制
 *   delayElement(Duration)        - 延迟元素
 * </pre>
 *
 * @author 学习者
 * @since Lab-10 Phase 1
 */
public class FilterOperatorsDemo {

    private static final Logger logger = LoggerFactory.getLogger(FilterOperatorsDemo.class);

    /**
     * 演示 1：filter() - 条件过滤
     *
     * <p><b>特点</b>：
     * <ul>
     *   <li>根据条件保留元素</li>
     *   <li>返回 true 则保留，false 则过滤掉</li>
     *   <li>可以链式调用多个 filter</li>
     * </ul>
     *
     * <p><b>执行流程</b>：
     * <pre>
     * 输入：1, 2, 3, 4, 5, 6
     * filter(x -> x % 2 == 0)
     * 输出：2, 4, 6
     * </pre>
     */
    public static void demo1_Filter() {
        logger.info("=== Demo 1: filter() - 条件过滤 ===");

        Flux.range(1, 10)
            .filter(x -> x % 2 == 0)        // 只保留偶数
            .subscribe(value -> logger.info("  even number: {}", value));

        logger.info("");
    }

    /**
     * 演示 2：filter() 链式调用 - 多条件过滤
     *
     * <p><b>特点</b>：
     * <ul>
     *   <li>可以链式调用多个 filter</li>
     *   <li>所有条件都满足才保留</li>
     * </ul>
     *
     * <p><b>执行流程</b>：
     * <pre>
     * 输入：1, 2, 3, 4, 5, 6, 7, 8, 9, 10
     * filter(x -> x % 2 == 0)       → 2, 4, 6, 8, 10
     * filter(x -> x > 4)            → 6, 8, 10
     * </pre>
     */
    public static void demo2_FilterChaining() {
        logger.info("=== Demo 2: filter() 链式调用 ===");

        Flux.range(1, 10)
            .filter(x -> x % 2 == 0)        // 第一个条件：偶数
            .filter(x -> x > 4)             // 第二个条件：大于4
            .subscribe(value -> logger.info("  result: {}", value));

        logger.info("");
    }

    /**
     * 演示 3：distinct() - 全局去重
     *
     * <p><b>特点</b>：
     * <ul>
     *   <li>记住所有出现过的元素，去掉重复</li>
     *   <li>需要内存来存储已见元素</li>
     *   <li>适合小数据集</li>
     * </ul>
     *
     * <p><b>执行流程</b>：
     * <pre>
     * 输入：1, 2, 2, 3, 2, 4, 3, 5
     * distinct()
     * 输出：1, 2, 3, 4, 5
     * </pre>
     */
    public static void demo3_Distinct() {
        logger.info("=== Demo 3: distinct() - 全局去重 ===");

        Flux.just(1, 2, 2, 3, 2, 4, 3, 5)
            .distinct()
            .subscribe(value -> logger.info("  distinct: {}", value));

        logger.info("");
    }

    /**
     * 演示 4：distinctUntilChanged() - 相邻去重
     *
     * <p><b>特点</b>：
     * <ul>
     *   <li>只去掉相邻的重复元素</li>
     *   <li>不需要记住所有元素，只看当前和前一个</li>
     *   <li>内存效率高</li>
     * </ul>
     *
     * <p><b>执行流程</b>：
     * <pre>
     * 输入：1, 2, 2, 3, 2, 4, 3, 3
     * distinctUntilChanged()
     * 输出：1, 2, 3, 2, 4, 3
     * （只去掉相邻的重复）
     * </pre>
     */
    public static void demo4_DistinctUntilChanged() {
        logger.info("=== Demo 4: distinctUntilChanged() - 相邻去重 ===");

        Flux.just(1, 2, 2, 3, 2, 4, 3, 3)
            .distinctUntilChanged()
            .subscribe(value -> logger.info("  result: {}", value));

        logger.info("");
    }

    /**
     * 演示 5：take(n) - 只取前 N 个
     *
     * <p><b>特点</b>：
     * <ul>
     *   <li>只取前 N 个元素</li>
     *   <li>立即取消订阅</li>
     *   <li>适合流很长但只需要部分数据</li>
     * </ul>
     *
     * <p><b>执行流程</b>：
     * <pre>
     * 输入：1, 2, 3, 4, 5, 6, 7, 8, 9, 10
     * take(5)
     * 输出：1, 2, 3, 4, 5
     * </pre>
     */
    public static void demo5_Take() {
        logger.info("=== Demo 5: take(n) - 只取前N个 ===");

        Flux.range(1, 10)
            .take(5)
            .subscribe(value -> logger.info("  taken: {}", value));

        logger.info("");
    }

    /**
     * 演示 6：skip(n) - 跳过前 N 个
     *
     * <p><b>特点</b>：
     * <ul>
     *   <li>跳过前 N 个元素</li>
     *   <li>常用于分页（跳过前 offset 个）</li>
     * </ul>
     *
     * <p><b>执行流程</b>：
     * <pre>
     * 输入：1, 2, 3, 4, 5, 6, 7, 8, 9, 10
     * skip(3)
     * 输出：4, 5, 6, 7, 8, 9, 10
     * </pre>
     */
    public static void demo6_Skip() {
        logger.info("=== Demo 6: skip(n) - 跳过前N个 ===");

        Flux.range(1, 10)
            .skip(3)
            .subscribe(value -> logger.info("  after skip: {}", value));

        logger.info("");
    }

    /**
     * 演示 7：take(n) + skip(n) - 分页
     *
     * <p><b>特点</b>：
     * <ul>
     *   <li>结合 skip 和 take 实现分页</li>
     *   <li>offset = skip(n), limit = take(n)</li>
     * </ul>
     *
     * <p><b>执行流程</b>：
     * <pre>
     * 输入：1-20
     * skip(5).take(5)    → 页码 2（跳过5个，取5个）
     * 输出：6, 7, 8, 9, 10
     * </pre>
     */
    public static void demo7_Pagination() {
        logger.info("=== Demo 7: skip() + take() - 分页 ===");

        Flux.range(1, 20)
            .skip(5)           // 跳过前 5 个
            .take(5)           // 取接下来的 5 个
            .subscribe(value -> logger.info("  page 2: {}", value));

        logger.info("");
    }

    /**
     * 演示 8：first() - 只取第一个
     *
     * <p><b>特点</b>：
     * <ul>
     *   <li>返回 Mono（只有一个元素）</li>
     *   <li>如果流为空，发送 NoSuchElementException</li>
     * </ul>
     */
    public static void demo8_First() {
        logger.info("=== Demo 8: first() - 只取第一个 ===");

        Flux.range(1, 10)
            .first()
            .subscribe(value -> logger.info("  first: {}", value));

        logger.info("");
    }

    /**
     * 演示 9：last() - 只取最后一个
     *
     * <p><b>特点</b>：
     * <ul>
     *   <li>返回 Mono（只有一个元素）</li>
     *   <li>需要等待所有元素发送完</li>
     *   <li>如果流为空，发送 NoSuchElementException</li>
     * </ul>
     */
    public static void demo9_Last() {
        logger.info("=== Demo 9: last() - 只取最后一个 ===");

        Flux.range(1, 10)
            .last()
            .subscribe(value -> logger.info("  last: {}", value));

        logger.info("");
    }

    /**
     * 演示 10：elementAt(index) - 取指定位置
     *
     * <p><b>特点</b>：
     * <ul>
     *   <li>取第 index 个元素（0-based）</li>
     *   <li>返回 Mono</li>
     *   <li>如果没有该位置，发送 IndexOutOfBoundsException</li>
     * </ul>
     */
    public static void demo10_ElementAt() {
        logger.info("=== Demo 10: elementAt(index) - 取指定位置 ===");

        Flux.range(1, 10)
            .elementAt(4)      // 取第 5 个（0-based）
            .subscribe(value -> logger.info("  element at 4: {}", value));

        logger.info("");
    }

    /**
     * main 方法：运行所有演示
     */
    public static void main(String[] args) {
        logger.info("╔════════════════════════════════════════════════════════════╗");
        logger.info("║     过滤操作符演示 - filter/distinct/take/skip/first/last   ║");
        logger.info("╚════════════════════════════════════════════════════════════╝");
        logger.info("");

        demo1_Filter();
        demo2_FilterChaining();
        demo3_Distinct();
        demo4_DistinctUntilChanged();
        demo5_Take();
        demo6_Skip();
        demo7_Pagination();
        demo8_First();
        demo9_Last();
        demo10_ElementAt();

        logger.info("");
        logger.info("╔════════════════════════════════════════════════════════════╗");
        logger.info("║                      所有演示完成                            ║");
        logger.info("╚════════════════════════════════════════════════════════════╝");
    }
}
