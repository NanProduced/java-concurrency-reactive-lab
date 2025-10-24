package nan.tech.lab09.basic;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * Flux 基础演示 - 0 个或多个元素的异步流
 *
 * <h2>教学目标</h2>
 * <ul>
 *     <li>理解 Flux 的本质：多个异步结果的容器</li>
 *     <li>对比 Flux vs Mono 的使用场景</li>
 *     <li>掌握 Flux 的基本创建方式</li>
 *     <li>体验流式处理和背压机制</li>
 * </ul>
 *
 * <h2>Flux 的本质</h2>
 * <pre>
 * Flux<T> 代表一个包含 0、1、多个或无限个元素的异步流
 *
 * 关键点：
 *   ✅ 不会立即执行，只有订阅时才执行
 *   ✅ 结果分次返回 (通过 onNext 事件)
 *   ✅ 可能产生错误 (onError 事件)
 *   ✅ 流结束时发送完成信号 (onComplete 事件)
 *
 * 对比：
 *   Mono<T>  : 0 或 1 个元素   (单个结果，如 GET /users/1)
 *   Flux<T>  : 0+ 个元素      (多个结果，如 GET /users 列表)
 * </pre>
 *
 * <h2>Demo 列表</h2>
 * <ul>
 *     <li>Demo 1: {@link #simpleFlux()} - 最简单的 Flux 示例</li>
 *     <li>Demo 2: {@link #rangeFlux()} - 按范围生成数字</li>
 *     <li>Demo 3: {@link #fromIterableFlux()} - 从列表转换为 Flux</li>
 *     <li>Demo 4: {@link #delayElementFlux()} - 延迟发送元素</li>
 *     <li>Demo 5: {@link #errorFlux()} - 错误处理演示</li>
 * </ul>
 *
 * @author Claude Code + Lab-09 团队
 * @version 1.0.0
 * @since 2025-10-23
 */
@Slf4j
@RestController
@RequestMapping("/basic/flux")
public class FluxController {

    /**
     * Demo 1: 最简单的 Flux 示例 - Flux.just()
     *
     * <h3>学习要点</h3>
     * <ul>
     *     <li>✅ Flux.just() 从多个值创建流</li>
     *     <li>✅ 访问 /basic/flux/simple 会立即返回流</li>
     *     <li>✅ doOnNext() 用于观察元素，不改变流内容</li>
     *     <li>✅ 浏览器或客户端通过 Server-Sent Events 接收数据</li>
     * </ul>
     *
     * <h3>执行流程</h3>
     * <pre>
     * 时刻 1: 请求到达 → Spring 调用此方法
     * 时刻 2: 返回 Flux<String> (还没执行！)
     * 时刻 3: Spring 订阅这个 Flux
     * 时刻 4: 立即发送 3 个字符串
     *   onNext("A") → 日志记录 → 发送给客户端
     *   onNext("B") → 日志记录 → 发送给客户端
     *   onNext("C") → 日志记录 → 发送给客户端
     * 时刻 5: onComplete() → 流结束
     * 时刻 6: HTTP 连接关闭
     * </pre>
     *
     * <h3>常见陷阱</h3>
     * <ul>
     *     <li>❌ 陷阱: 认为 Flux 创建时就执行了
     *         <br/>原因: Flux 是延迟执行的，只有订阅时才执行
     *         <br/>✅ 解决: 使用 subscribe() 或等待 Spring 自动订阅</li>
     *     <li>❌ 陷阱: 忽视 doOnNext() 的执行顺序
     *         <br/>原因: doOnNext() 是在 onNext 事件时执行的
     *         <br/>✅ 解决: 理解响应式流的事件驱动本质</li>
     * </ul>
     *
     * @return Flux<String> 包含三个字符串的流
     */
    @GetMapping("/simple")
    public Flux<String> simpleFlux() {
        return Flux.just("A", "B", "C")
                .doOnNext(item -> {
                    // 📌 @教学: doOnNext 是观察者模式，观察每个元素但不修改
                    log.info("[Flux.simple] 发送元素: {}", item);
                })
                .doOnComplete(() -> {
                    // 📌 @教学: doOnComplete 在所有元素发送完毕后调用一次
                    log.info("[Flux.simple] Flux 完成，所有元素已发送");
                });
    }

    /**
     * Demo 2: 按范围生成数字 - Flux.range()
     *
     * <h3>学习要点</h3>
     * <ul>
     *     <li>✅ Flux.range(start, count) 生成从 start 开始的 count 个数字</li>
     *     <li>✅ 演示了"有限流"vs"无限流"的区别</li>
     *     <li>✅ 可以用于生成序列、ID 等</li>
     * </ul>
     *
     * <h3>执行流程</h3>
     * <pre>
     * Flux.range(1, 5) 生成: 1, 2, 3, 4, 5
     *
     * 对比：
     *   Flux.range(1, 5)           → 有限流 (5 个元素后完成)
     *   Flux.interval(Duration)    → 无限流 (永远不完成)
     * </pre>
     *
     * @return Flux<Integer> 包含 1-5 的流
     */
    @GetMapping("/range")
    public Flux<Integer> rangeFlux() {
        return Flux.range(1, 5)
                .map(num -> {
                    // 📌 @教学: map 是响应式流的转换操作符
                    // 这里我们将数字 * 10 后发送
                    int result = num * 10;
                    log.info("[Flux.range] 转换: {} → {}", num, result);
                    return result;
                });
    }

    /**
     * Demo 3: 从列表转换为 Flux - Flux.fromIterable()
     *
     * <h3>学习要点</h3>
     * <ul>
     *     <li>✅ Flux.fromIterable() 将任何 Iterable (列表、集合) 转换为异步流</li>
     *     <li>✅ 演示了同步数据源如何异步化</li>
     *     <li>✅ 常见用途: 数据库查询结果转换为流</li>
     * </ul>
     *
     * <h3>实际应用</h3>
     * <pre>
     * // 在 Repository 中
     * public Flux<User> findAll() {
     *     // 假设 database.queryUsers() 返回 List<User>
     *     List<User> users = database.queryUsers();
     *     return Flux.fromIterable(users)  // 转换为异步流
     *         .delayElement(Duration.ofMillis(100));  // 添加背压控制
     * }
     * </pre>
     *
     * @return Flux<String> 从列表转换的流
     */
    @GetMapping("/from-list")
    public Flux<String> fromIterableFlux() {
        // 📌 @教学: 创建一个列表 (同步数据源)
        List<String> names = Arrays.asList("Alice", "Bob", "Charlie", "David");

        return Flux.fromIterable(names)
                .doOnNext(name -> log.info("[Flux.fromList] 处理用户: {}", name));
    }

    /**
     * Demo 4: 延迟发送元素 - delayElement()
     *
     * <h3>学习要点</h3>
     * <ul>
     *     <li>✅ delayElement(Duration) 延迟每个元素的发送</li>
     *     <li>✅ 演示了"非阻塞延迟"vs"阻塞延迟"的区别</li>
     *     <li>✅ 在延迟期间，该线程可以处理其他请求</li>
     * </ul>
     *
     * <h3>执行时间线</h3>
     * <pre>
     * 时刻 0ms   : onNext(1) → 延迟 500ms
     * 时刻 500ms : 发送 1
     * 时刻 500ms : onNext(2) → 延迟 500ms
     * 时刻 1000ms: 发送 2
     * 时刻 1000ms: onNext(3) → 延迟 500ms
     * 时刻 1500ms: 发送 3
     * 时刻 1500ms: onComplete()
     *
     * ✅ 关键点: 在延迟期间，EventLoop 线程不被阻塞
     *           可以处理其他请求，这就是非阻塞 I/O 的优势！
     * </pre>
     *
     * <h3>对比</h3>
     * <pre>
     * // ❌ 错误: Thread.sleep 会阻塞线程
     * Flux.range(1, 3)
     *     .map(i -> {
     *         Thread.sleep(500);  // 阻塞！
     *         return i;
     *     })
     *     .subscribe();
     *
     * // ✅ 正确: delayElement 不阻塞
     * Flux.range(1, 3)
     *     .delayElement(Duration.ofMillis(500))  // 非阻塞
     *     .subscribe();
     * </pre>
     *
     * @return Flux<Integer> 每 500ms 发送一个元素的流
     */
    @GetMapping("/delay")
    public Flux<Integer> delayElementFlux() {
        return Flux.range(1, 3)
                .delayElements(Duration.ofMillis(500))
                .doOnNext(num -> {
                    // 📌 @教学: 这里的日志输出会间隔 500ms
                    log.info("[Flux.delay] 发送元素 {} (间隔 500ms)", num);
                });
    }

    /**
     * Demo 5: 错误处理 - Flux.error() 和 onErrorResume()
     *
     * <h3>学习要点</h3>
     * <ul>
     *     <li>✅ Flux.error() 创建一个错误流</li>
     *     <li>✅ onError 是流中的第三种事件 (onNext, onError, onComplete)</li>
     *     <li>✅ onErrorResume() 可以捕获错误并发送替代值</li>
     *     <li>✅ 错误会中断流，不会再有 onNext 或 onComplete</li>
     * </ul>
     *
     * <h3>错误处理流程</h3>
     * <pre>
     * 场景 1: 无错误处理
     *   onNext(1) → onNext(2) → onError(RuntimeException) → [中断]
     *
     * 场景 2: 使用 onErrorResume
     *   onNext(1) → onNext(2) → 发生错误
     *   → onErrorResume 捕获错误
     *   → 返回替代流 (Flux.just("错误被恢复"))
     *   → onNext("错误被恢复") → onComplete()
     * </pre>
     *
     * <h3>常见陷阱</h3>
     * <ul>
     *     <li>❌ 陷阱: 不处理错误，错误被吞掉
     *         <br/>原因: 没有 onError 或 onErrorResume 处理
     *         <br/>✅ 解决: 始终使用 onErrorResume 或 onErrorReturn</li>
     *     <li>❌ 陷阱: 混淆 onError 和 onErrorResume
     *         <br/>原因: onError 仅记录，onErrorResume 才能恢复
     *         <br/>✅ 解决: 使用 onErrorResume 返回替代流</li>
     * </ul>
     *
     * @return Flux<String> 演示错误和恢复
     */
    @GetMapping("/error")
    public Flux<String> errorFlux() {
        return Flux.just("开始")
                .doOnNext(msg -> log.info("[Flux.error] {}", msg))
                .flatMap(msg -> {
                    // 📌 @教学: 这里模拟了一个错误的操作
                    // flatMap 允许我们在某个条件下返回一个错误流
                    return Flux.<String>error(new RuntimeException("模拟错误: 数据库连接失败"));
                })
                .onErrorResume(error -> {
                    // 📌 @教学: 捕获错误并发送替代值
                    log.error("[Flux.error] 捕获错误: {}", error.getMessage());
                    return Flux.just(
                            "错误已恢复",
                            "返回缓存数据",
                            "用户体验不受影响"
                    );
                })
                .doOnComplete(() -> {
                    log.info("[Flux.error] Flux 完成");
                });
    }

    /**
     * Bonus Demo 6: 服务器推送事件 (SSE) - 实时推送
     *
     * <h3>学习要点</h3>
     * <ul>
     *     <li>✅ 使用 Flux 实现 Server-Sent Events</li>
     *     <li>✅ 无限流的实时推送场景</li>
     *     <li>✅ MediaType.TEXT_EVENT_STREAM 是 SSE 的标准 MIME 类型</li>
     * </ul>
     *
     * <h3>使用方式</h3>
     * <pre>
     * // JavaScript 客户端
     * const eventSource = new EventSource("http://localhost:8080/basic/flux/stream");
     * eventSource.onmessage = (event) => {
     *     console.log("接收到消息:", event.data);
     * };
     * </pre>
     *
     * @return Flux<String> 无限推送消息的流
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamFlux() {
        return Flux.interval(Duration.ofSeconds(1))
                .map(sequence -> "消息 #" + sequence + " 发送于 " + System.currentTimeMillis())
                .doOnNext(msg -> log.info("[Flux.stream] {}", msg))
                .doOnCancel(() -> log.info("[Flux.stream] 客户端断开连接"));
    }

    /**
     * Bonus Demo 7: 无限数字流 - Flux.interval()
     *
     * <h3>学习要点</h3>
     * <ul>
     *     <li>✅ Flux.interval() 每隔一段时间发送一个递增的数字</li>
     *     <li>✅ 这是一个无限流，永远不会调用 onComplete()</li>
     *     <li>✅ 必须使用 take() 或 takeUntil() 限制元素数</li>
     * </ul>
     *
     * <h3>常见陷阱</h3>
     * <ul>
     *     <li>❌ 陷阱: Flux.interval() 会无限推送，导致客户端卡住
     *         <br/>原因: 没有办法中止无限流
     *         <br/>✅ 解决: 在客户端关闭连接或使用 take() 限制</li>
     * </ul>
     *
     * @return Flux<Long> 每秒发送一个递增数字
     */
    @GetMapping(value = "/interval", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> intervalFlux() {
        return Flux.interval(Duration.ofSeconds(1))
                .doOnNext(num -> log.info("[Flux.interval] 推送: {}", num))
                .map(num -> "第 " + num + " 次推送")
                .doOnCancel(() -> log.info("[Flux.interval] 客户端已断开"));
    }

}
