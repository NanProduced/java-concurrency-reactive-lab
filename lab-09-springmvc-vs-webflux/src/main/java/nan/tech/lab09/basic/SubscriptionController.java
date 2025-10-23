package nan.tech.lab09.basic;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 生命周期和订阅演示 - 理解响应式流的执行模型
 *
 * <h2>教学目标</h2>
 * <ul>
 *     <li>理解"定义"和"订阅"的区别</li>
 *     <li>掌握 Mono/Flux 的三个关键事件：onNext、onError、onComplete</li>
 *     <li>理解"冷流"vs"热流"的概念</li>
 *     <li>体验响应式流的事件驱动本质</li>
 * </ul>
 *
 * <h2>核心概念</h2>
 *
 * <h3>定义 vs 订阅</h3>
 * <pre>
 * ❌ 错误的理解：
 *   Mono<T> mono = operation();  // 这时开始执行
 *
 * ✅ 正确的理解：
 *   Mono<T> mono = operation();  // 这时只是"定义"，不执行
 *   mono.subscribe();            // 这时才真正执行
 *
 * 类比：
 *   Mono = 一张"蓝图"
 *   subscribe = "按照蓝图施工"
 * </pre>
 *
 * <h3>三个关键事件</h3>
 * <pre>
 * onNext(T value)
 *   ├─ 数据到达时调用
 *   ├─ Mono 最多调用 1 次
 *   └─ Flux 可调用 0+ 次
 *
 * onError(Throwable error)
 *   ├─ 发生错误时调用
 *   ├─ 最多调用 1 次 (互斥于 onComplete)
 *   └─ 调用后，流中止，不再有其他事件
 *
 * onComplete()
 *   ├─ 流结束时调用
 *   ├─ 最多调用 1 次 (互斥于 onError)
 *   └─ 表示成功完成，没有更多数据
 * </pre>
 *
 * <h3>冷流 vs 热流</h3>
 * <pre>
 * 冷流 (Cold Stream):
 *   ├─ 每个订阅者都从头开始
 *   ├─ Mono 和 Flux 默认都是冷流
 *   └─ 例: Flux.range(1, 5) - 每个订阅者都会收到 1,2,3,4,5
 *
 * 热流 (Hot Stream):
 *   ├─ 订阅者只能收到订阅后的数据
 *   ├─ 如: Flux.interval() - 每个订阅者只能收到之后的数据
 *   └─ 需要使用 publish() 或 share() 转换
 * </pre>
 *
 * @author Claude Code + Lab-09 团队
 * @version 1.0.0
 * @since 2025-10-23
 */
@Slf4j
@RestController
@RequestMapping("/basic/subscription")
public class SubscriptionController {

    /**
     * Demo 1: 定义 vs 订阅 - 理解执行时机
     *
     * <h3>学习要点</h3>
     * <ul>
     *     <li>✅ Mono 定义时不执行，订阅时才执行</li>
     *     <li>✅ 这是"冷流"的特性</li>
     *     <li>✅ 可以定义一个 Mono 而不立即执行</li>
     * </ul>
     *
     * <h3>执行顺序演示</h3>
     * <pre>
     * 时刻 1: [定义] Mono.just("值") - 只创建流定义，不执行
     * 时刻 2: [订阅] .subscribe() - 才真正开始执行
     * 时刻 3: [执行] 计算、查询等操作发生
     * 时刻 4: [回调] onNext() / onError() / onComplete()
     * </pre>
     *
     * @return Mono<String> 演示定义和订阅的区别
     */
    @GetMapping("/definition-vs-subscription")
    public Mono<String> definitionVsSubscription() {
        log.info("[subscription] ==================== 定义 vs 订阅演示开始 ====================");

        // 📌 @教学: 时刻 1 - 定义阶段
        log.info("[subscription] [时刻 1] 定义 Mono - 此时不执行");
        Mono<String> mono = Mono.just("结果值")
                .doOnNext(value -> {
                    log.info("[subscription] [时刻 3] 执行 doOnNext: {}", value);
                })
                .doOnComplete(() -> {
                    log.info("[subscription] [时刻 4] 执行 onComplete");
                })
                .map(value -> {
                    log.info("[subscription] [时刻 3] 执行 map 转换");
                    return "转换后的 " + value;
                });

        log.info("[subscription] [时刻 2] 定义完毕，现在订阅 Mono");

        // 📌 @教学: 时刻 2 - 订阅阶段（此时才开始执行）
        return mono.doOnNext(finalValue -> {
            log.info("[subscription] [时刻 5] 最终返回: {}", finalValue);
        });
    }

    /**
     * Demo 2: onNext 的调用顺序 - 事件驱动流程
     *
     * <h3>学习要点</h3>
     * <ul>
     *     <li>✅ 理解响应式流中的事件顺序</li>
     *     <li>✅ 每个操作符都在特定事件发生时执行</li>
     *     <li>✅ 这是"链式反应"，不是顺序执行</li>
     * </ul>
     *
     * <h3>执行流程</h3>
     * <pre>
     * just(1)
     *   └─ doOnNext: 看到 1
     *      └─ map(n -> n*2): 转换为 2
     *         └─ doOnNext: 看到 2
     *            └─ map(n -> n+100): 转换为 102
     *               └─ doOnNext: 看到 102
     *                  └─ subscribe: 最终收到 102
     *
     * 时间线：
     *   时刻 0: onNext(1) 事件到达
     *   时刻 1: 执行第一个 doOnNext
     *   时刻 2: 执行第一个 map，转换为 2
     *   时刻 3: 执行第二个 doOnNext
     *   时刻 4: 执行第二个 map，转换为 102
     *   时刻 5: 执行第三个 doOnNext
     *   时刻 6: onComplete() 完成
     * </pre>
     *
     * @return Mono<Integer> 演示链式事件流
     */
    @GetMapping("/event-chain")
    public Mono<Integer> eventChain() {
        log.info("[subscription] ==================== 事件链演示开始 ====================");

        return Mono.just(1)
                .doOnNext(num -> {
                    log.info("[subscription] [事件 1] 看到原始值: {}", num);
                })
                .map(num -> {
                    int result = num * 2;
                    log.info("[subscription] [转换 1] {} * 2 = {}", num, result);
                    return result;
                })
                .doOnNext(num -> {
                    log.info("[subscription] [事件 2] 看到转换后值: {}", num);
                })
                .map(num -> {
                    int result = num + 100;
                    log.info("[subscription] [转换 2] {} + 100 = {}", num, result);
                    return result;
                })
                .doOnNext(num -> {
                    log.info("[subscription] [事件 3] 看到最终值: {}", num);
                })
                .doOnComplete(() -> {
                    log.info("[subscription] [完成] Mono 处理完毕");
                });
    }

    /**
     * Demo 3: Flux 的多个 onNext 事件
     *
     * <h3>学习要点</h3>
     * <ul>
     *     <li>✅ Flux 会多次调用 onNext（Mono 最多 1 次）</li>
     *     <li>✅ 每个元素都会触发整个链的执行</li>
     *     <li>✅ 理解"多次 onNext"vs"单次 onNext"的区别</li>
     * </ul>
     *
     * <h3>执行流程</h3>
     * <pre>
     * Flux.just("A", "B", "C")
     *   └─ 第一轮: onNext("A")
     *      └─ doOnNext: 看到 A
     *         └─ map: 转换为 A+
     *            └─ doOnNext: 看到 A+
     *
     *   └─ 第二轮: onNext("B")
     *      └─ doOnNext: 看到 B
     *         └─ map: 转换为 B+
     *            └─ doOnNext: 看到 B+
     *
     *   └─ 第三轮: onNext("C")
     *      └─ doOnNext: 看到 C
     *         └─ map: 转换为 C+
     *            └─ doOnNext: 看到 C+
     *
     *   └─ 最后: onComplete()
     * </pre>
     *
     * @return Flux<String> 演示多个 onNext 事件
     */
    @GetMapping("/multiple-events")
    public Flux<String> multipleEvents() {
        log.info("[subscription] ==================== 多个 onNext 事件演示开始 ====================");

        return Flux.just("A", "B", "C")
                .doOnNext(item -> {
                    log.info("[subscription] 看到元素: {}", item);
                })
                .map(item -> {
                    String result = item + "+";
                    log.info("[subscription] 转换: {} → {}", item, result);
                    return result;
                })
                .doOnNext(item -> {
                    log.info("[subscription] 转换后的元素: {}", item);
                })
                .doOnComplete(() -> {
                    log.info("[subscription] Flux 所有元素处理完毕");
                });
    }

    /**
     * Demo 4: 冷流演示 - 每个订阅都是独立的
     *
     * <h3>学习要点</h3>
     * <ul>
     *     <li>✅ Mono 和 Flux 默认是冷流</li>
     *     <li>✅ 每个订阅都从头开始执行</li>
     *     <li>✅ 如果有多个订阅，会执行多次</li>
     * </ul>
     *
     * <h3>冷流 vs 热流</h3>
     * <pre>
     * 冷流:
     *   Flux<Integer> cold = Flux.range(1, 3);
     *   cold.subscribe(a -> ...);  // 订阅 1: 1, 2, 3
     *   cold.subscribe(b -> ...);  // 订阅 2: 1, 2, 3 (重新开始)
     *
     * 热流:
     *   Flux<Integer> hot = Flux.interval(1s).share();
     *   hot.subscribe(a -> ...);   // 订阅 1: 只能收到之后的
     *   hot.subscribe(b -> ...);   // 订阅 2: 只能收到之后的
     * </pre>
     *
     * @return Mono<String> 演示冷流特性的说明
     */
    @GetMapping("/cold-stream")
    public Mono<String> coldStreamDemo() {
        log.info("[subscription] ==================== 冷流演示开始 ====================");

        // 创建一个计数器来演示多次执行
        AtomicInteger callCount = new AtomicInteger(0);

        // 创建冷流
        Mono<String> coldMono = Mono.defer(() -> {
            int call = callCount.incrementAndGet();
            log.info("[subscription] 第 {} 次调用", call);
            return Mono.just("结果 #" + call);
        });

        // 模拟多次订阅 (在这个 HTTP 请求中，我们虽然只返回一个 Mono，
        // 但可以展示冷流的特性)
        return coldMono
                .doOnNext(value -> {
                    log.info("[subscription] 第一个订阅收到: {}", value);
                })
                .map(value -> {
                    // 这里再创建一个订阅
                    String explanation = "冷流特性演示: ";
                    explanation += value + "\n";
                    explanation += "如果再次订阅，会从头开始执行（callCount 会增加）";
                    return explanation;
                });
    }

    /**
     * Demo 5: 异常处理 - 三种事件的互斥性
     *
     * <h3>学习要点</h3>
     * <ul>
     *     <li>✅ onNext、onError、onComplete 三者互斥</li>
     *     <li>✅ 一旦调用 onError，就不会再调用 onNext 或 onComplete</li>
     *     <li>✅ 一旦调用 onComplete，说明成功完成</li>
     * </ul>
     *
     * <h3>三种终止场景</h3>
     * <pre>
     * 场景 1 (成功):
     *   onNext(值) → onComplete()  ✅
     *
     * 场景 2 (错误):
     *   ... → onError(exception)  [中止，不会再有其他事件] ❌
     *
     * 场景 3 (空):
     *   [无 onNext] → onComplete()  ✅ (Mono.empty())
     * </pre>
     *
     * @return Mono<String> 演示错误处理
     */
    @GetMapping("/error-handling")
    public Mono<String> errorHandling() {
        log.info("[subscription] ==================== 错误处理演示开始 ====================");

        return Mono.just(10)
                .doOnNext(value -> {
                    log.info("[subscription] 看到值: {}", value);
                })
                .map(value -> {
                    if (value < 20) {
                        throw new RuntimeException("值太小: " + value);
                    }
                    return value;
                })
                .doOnNext(value -> {
                    // 📌 @教学: 如果上面抛出异常，这里不会执行
                    log.info("[subscription] 这一行不会执行，因为发生了错误");
                    return value;
                })
                .doOnError(error -> {
                    // 📌 @教学: 错误发生时，doOnError 会被调用
                    log.error("[subscription] 捕获错误: {}", error.getMessage());
                })
                .onErrorResume(error -> {
                    // 📌 @教学: 恢复错误，返回替代值
                    log.info("[subscription] 错误已恢复");
                    return Mono.just("错误恢复后的默认值");
                })
                .doOnComplete(() -> {
                    // 📌 @教学: 注意这里会执行，因为我们在 onErrorResume 中恢复了
                    log.info("[subscription] Mono 完成");
                });
    }

    /**
     * Demo 6: 超时处理 - 响应式流的超时机制
     *
     * <h3>学习要点</h3>
     * <ul>
     *     <li>✅ timeout() 设置超时时间</li>
     *     <li>✅ 超时会触发 onError</li>
     *     <li>✅ 可以与 onErrorResume 组合使用</li>
     * </ul>
     *
     * @return Mono<String> 演示超时处理
     */
    @GetMapping("/timeout")
    public Mono<String> timeoutDemo() {
        log.info("[subscription] ==================== 超时演示开始 ====================");

        return Mono.just("缓慢的操作")
                .delayElement(Duration.ofSeconds(3))  // 延迟 3 秒
                .doOnNext(value -> {
                    log.info("[subscription] 得到值: {}", value);
                })
                .timeout(Duration.ofSeconds(1))  // 但超时设置为 1 秒
                .doOnError(error -> {
                    log.error("[subscription] 超时错误: {}", error.getClass().getSimpleName());
                })
                .onErrorReturn("超时，返回默认值");
    }

}
