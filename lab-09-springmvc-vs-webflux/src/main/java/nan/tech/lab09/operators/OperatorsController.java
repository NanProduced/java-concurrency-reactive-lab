package nan.tech.lab09.operators;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * 响应式流操作符演示
 *
 * <h2>教学目标</h2>
 * <ul>
 *     <li>掌握 map() - 元素转换</li>
 *     <li>掌握 flatMap() - 异步操作和流扁平化</li>
 *     <li>掌握 merge() - 流合并</li>
 *     <li>掌握 zip() - 流组合与同步</li>
 *     <li>理解背压控制和缓冲策略</li>
 * </ul>
 *
 * <h2>核心概念</h2>
 *
 * <h3>1. map() - 同步转换</h3>
 * <pre>
 * 用途: 对每个元素进行同步转换
 * 特点: 一对一映射，保持顺序
 *
 * 示例:
 *   Flux.just(1, 2, 3)
 *     .map(n -> n * 2)  // 结果: 2, 4, 6
 * </pre>
 *
 * <h3>2. flatMap() - 异步转换</h3>
 * <pre>
 * 用途: 对每个元素进行异步操作，返回新的流
 * 特点: 一对多映射，自动扁平化嵌套流
 *
 * 示例:
 *   Flux.just("Alice", "Bob")
 *     .flatMap(name ->
 *       userRepository.findByName(name)  // 返回 Mono<User>
 *     )  // 结果: User 流扁平化
 * </pre>
 *
 * <h3>3. merge() - 流合并</h3>
 * <pre>
 * 用途: 将多个流合并为一个流
 * 特点: 交错发射元素，不保证顺序
 * 背压: 下游背压会影响所有上游源
 *
 * 示例:
 *   Flux.merge(
 *     Flux.just(1, 2),
 *     Flux.just(3, 4)
 *   )  // 结果: 1, 3, 2, 4 (可能乱序)
 * </pre>
 *
 * <h3>4. zip() - 流组合</h3>
 * <pre>
 * 用途: 将多个流的元素配对
 * 特点: 等待所有流有值时一起发射，保证同步
 * 背压: 由最慢的流控制
 *
 * 示例:
 *   Flux.zip(
 *     Flux.just("A", "B"),
 *     Flux.just(1, 2)
 *   )  // 结果: (A,1), (B,2)
 * </pre>
 *
 * <h3>5. 背压 (Backpressure)</h3>
 * <pre>
 * 定义: 下游消费者通过背压告诉上游生产者"我现在只能处理 N 个元素"
 *
 * 四种策略:
 * 1. BUFFER - 缓存：生产者继续生产，元素被缓存
 * 2. DROP - 丢弃：缓存满时，丢弃新元素
 * 3. LATEST - 最新：缓存满时，保留最新元素
 * 4. ERROR - 错误：缓存满时，抛出错误
 * </pre>
 *
 * @author Claude Code + Lab-09 团队
 * @version 1.0.0
 * @since 2025-10-23
 */
@Slf4j
@RestController
@RequestMapping("/operators")
public class OperatorsController {

    /**
     * Demo 1: map() - 同步元素转换
     *
     * 使用场景: 数据格式转换、计算转换
     *
     * @return 转换后的数字流
     */
    @GetMapping("/map")
    public Flux<Integer> demoMap() {
        log.info("📌 [map] 演示：同步元素转换");

        return Flux.just(1, 2, 3, 4, 5)
                .doOnNext(n -> log.info("  原始值: {}", n))
                .map(n -> {
                    int result = n * n;
                    log.info("  map 转换: {} → {} (平方)", n, result);
                    return result;
                })
                .doOnNext(n -> log.info("  转换后: {}", n));
    }

    /**
     * Demo 2: flatMap() - 异步流扁平化
     *
     * 使用场景: 数据库查询、API 调用、异步操作
     *
     * @return 扁平化后的用户名流
     */
    @GetMapping("/flatmap")
    public Flux<String> demoFlatMap() {
        log.info("📌 [flatMap] 演示：异步流扁平化");

        return Flux.just("user1", "user2", "user3")
                .doOnNext(id -> log.info("  处理用户 ID: {}", id))
                .flatMap(id -> {
                    // 模拟异步数据库查询：每个用户 ID 返回多个关联数据
                    return Mono.just(id)
                            .delayElement(Duration.ofMillis(100))
                            .flatMapMany(userId -> {
                                List<String> userData = Arrays.asList(
                                        userId + "-profile",
                                        userId + "-settings",
                                        userId + "-preferences"
                                );
                                return Flux.fromIterable(userData)
                                        .doOnNext(d -> log.info("    查询结果: {}", d));
                            });
                })
                .doOnNext(data -> log.info("  最终结果: {}", data));
    }

    /**
     * Demo 3: merge() - 流交错合并
     *
     * 使用场景: 合并多个数据源、事件流合并
     *
     * @return 合并后的整数流
     */
    @GetMapping("/merge")
    public Flux<String> demoMerge() {
        log.info("📌 [merge] 演示：流交错合并");

        Flux<String> fast = Flux.just("Fast-1", "Fast-2", "Fast-3")
                .delayElements(Duration.ofMillis(100))
                .doOnNext(v -> log.info("  fast 流: {}", v));

        Flux<String> slow = Flux.just("Slow-1", "Slow-2", "Slow-3")
                .delayElements(Duration.ofMillis(200))
                .doOnNext(v -> log.info("  slow 流: {}", v));

        return Flux.merge(fast, slow)
                .doOnNext(v -> log.info("  合并结果: {}", v));
    }

    /**
     * Demo 4: zip() - 流元素配对与同步
     *
     * 使用场景: 将多个流的元素关联、聚合来自多个源的数据
     *
     * @return 配对后的字符串流
     */
    @GetMapping("/zip")
    public Flux<String> demoZip() {
        log.info("📌 [zip] 演示：流元素配对");

        Flux<String> colors = Flux.just("Red", "Green", "Blue")
                .delayElements(Duration.ofMillis(150))
                .doOnNext(c -> log.info("  color 流: {}", c));

        Flux<Integer> numbers = Flux.just(1, 2, 3)
                .delayElements(Duration.ofMillis(100))
                .doOnNext(n -> log.info("  number 流: {}", n));

        return Flux.zip(colors, numbers)
                .map(tuple -> tuple.getT1() + "-" + tuple.getT2())
                .doOnNext(pair -> log.info("  配对结果: {}", pair));
    }

    /**
     * Demo 5: 背压控制 - Buffer 策略
     *
     * 使用场景: 快速生产者 + 慢速消费者，缓存数据
     *
     * @return 带背压控制的流
     */
    @GetMapping("/backpressure-buffer")
    public Flux<Integer> demoBackpressureBuffer() {
        log.info("📌 [背压] 演示：Buffer 缓冲策略");

        return Flux.range(1, 20)
                .doOnNext(n -> log.info("  生产: {}", n))
                .buffer(5)  // 缓冲 5 个元素
                .flatMap(batch -> {
                    log.info("  消费批次: {}", batch);
                    return Flux.fromIterable(batch)
                            .delayElements(Duration.ofMillis(200))
                            .doOnNext(n -> log.info("    处理: {}", n));
                });
    }

    /**
     * Demo 6: 背压控制 - OnBackpressureLatest 策略
     *
     * 使用场景: 实时数据流（传感器、事件）,只关心最新值
     *
     * @return 带最新值策略的流
     */
    @GetMapping("/backpressure-latest")
    public Flux<Integer> demoBackpressureLatest() {
        log.info("📌 [背压] 演示：OnBackpressureLatest 策略");

        return Flux.interval(Duration.ofMillis(50))
                .take(20)
                .map(Long::intValue)
                .doOnNext(n -> log.info("  生产: {}", n))
                .onBackpressureLatest()  // 只保留最新值
                .delayElements(Duration.ofMillis(200))
                .doOnNext(n -> log.info("  消费: {}", n));
    }

    /**
     * Demo 7: flatMap 与背压的交互
     *
     * 关键点: flatMap 的并发控制
     *
     * @param concurrent 并发数 (default: 2)
     * @return 控制并发的流
     */
    @GetMapping("/flatmap-concurrent")
    public Flux<String> demoFlatMapConcurrent(
            @RequestParam(defaultValue = "2") int concurrent) {
        log.info("📌 [flatMap] 演示：并发控制 (并发数={})", concurrent);

        return Flux.range(1, 10)
                .doOnNext(n -> log.info("  处理请求: {}", n))
                .flatMap(n -> {
                    // 模拟异步 API 调用
                    return Mono.just(n)
                            .delayElement(Duration.ofMillis(100 + n * 50))
                            .map(id -> "响应-" + id)
                            .doOnNext(r -> log.info("    API 响应: {}", r));
                }, concurrent)  // 限制并发数
                .doOnNext(r -> log.info("  最终: {}", r));
    }

    /**
     * Demo 8: 复合操作 - 链式流处理
     *
     * 展示多个操作符的组合使用
     *
     * @return 多步处理后的结果流
     */
    @GetMapping("/combined")
    public Flux<String> demoCombined() {
        log.info("📌 [组合] 演示：多个操作符链式处理");

        return Flux.just("Alice", "Bob", "Charlie", "Diana")
                .doOnNext(name -> log.info("  1. 原始名称: {}", name))
                .filter(name -> name.length() > 3)  // 过滤
                .doOnNext(name -> log.info("  2. 过滤后: {}", name))
                .map(String::toUpperCase)  // 转换
                .doOnNext(name -> log.info("  3. 大写转换: {}", name))
                .flatMap(name -> {
                    // 模拟每个名字关联多个数据
                    return Flux.just(
                            name + "-email",
                            name + "-phone"
                    );
                })
                .doOnNext(data -> log.info("  4. 扁平化: {}", data));
    }

}
