package nan.tech.lab09.basic;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

/**
 * Mono 基础演示 - 0 个或 1 个元素的异步结果
 *
 * <h2>教学目标</h2>
 * <ul>
 *     <li>理解 Mono 的本质：单个异步结果的容器</li>
 *     <li>对比 Mono vs Flux 的使用场景</li>
 *     <li>掌握 Mono 的基本创建方式</li>
 *     <li>理解 Mono 的三种终止状态：成功、错误、空</li>
 * </ul>
 *
 * <h2>Mono 的本质</h2>
 * <pre>
 * Mono<T> 代表一个包含 0 个或 1 个元素的异步结果
 *
 * 关键点：
 *   ✅ 不会立即执行，只有订阅时才执行
 *   ✅ 最多发送一个元素 (onNext 最多调用一次)
 *   ✅ 可能产生错误 (onError 事件)
 *   ✅ 完成时发送信号 (onComplete 事件)
 *
 * 对比：
 *   Mono<T>  : 0 或 1 个元素       (单个结果，如 GET /users/1)
 *   Flux<T>  : 0、1、多个或无限   (多个结果，如 GET /users)
 *
 * Mono 的三种终止状态：
 *   1. 成功: onNext(value) → onComplete()
 *   2. 错误: onError(exception)
 *   3. 空  : 无 onNext → onComplete()
 * </pre>
 *
 * <h2>Demo 列表</h2>
 * <ul>
 *     <li>Demo 1: {@link #simpleMono()} - 最简单的 Mono 示例</li>
 *     <li>Demo 2: {@link #delayMono()} - 延迟返回结果</li>
 *     <li>Demo 3: {@link #fromCallableMono()} - 从 Callable 创建</li>
 *     <li>Demo 4: {@link #emptyMono()} - 空 Mono 演示</li>
 *     <li>Demo 5: {@link #errorMono()} - 错误 Mono 演示</li>
 * </ul>
 *
 * @author Claude Code + Lab-09 团队
 * @version 1.0.0
 * @since 2025-10-23
 */
@Slf4j
@RestController
@RequestMapping("/basic/mono")
public class MonoController {

    /**
     * 模拟用户数据库
     */
    private static final Map<Integer, String> USER_CACHE = new HashMap<>();

    static {
        USER_CACHE.put(1, "Alice");
        USER_CACHE.put(2, "Bob");
        USER_CACHE.put(3, "Charlie");
    }

    /**
     * Demo 1: 最简单的 Mono 示例 - Mono.just()
     *
     * <h3>学习要点</h3>
     * <ul>
     *     <li>✅ Mono.just() 从一个值创建 Mono</li>
     *     <li>✅ 访问 /basic/mono/simple 会立即返回单个值</li>
     *     <li>✅ doOnNext() 用于观察值，不改变值</li>
     *     <li>✅ Mono 不需要 Content-Type 为 TEXT_EVENT_STREAM</li>
     * </ul>
     *
     * <h3>执行流程</h3>
     * <pre>
     * 时刻 1: 请求到达 → Spring 调用此方法
     * 时刻 2: 返回 Mono<String> (还没执行！)
     * 时刻 3: Spring 订阅这个 Mono
     * 时刻 4: 立即执行
     *   onNext("Hello, Reactive World!") → 日志记录 → 返回给客户端
     *   onComplete() → HTTP 连接关闭
     * </pre>
     *
     * <h3>对比 vs Flux</h3>
     * <pre>
     * Mono.just("Hello")
     *   onNext("Hello") → onComplete()    [只调用 1 次 onNext]
     *
     * Flux.just("A", "B", "C")
     *   onNext("A") → onNext("B") → onNext("C") → onComplete()
     *   [调用 3 次 onNext]
     * </pre>
     *
     * @return Mono<String> 包含一个字符串的 Mono
     */
    @GetMapping("/simple")
    public Mono<String> simpleMono() {
        return Mono.just("Hello, Reactive World!")
                .doOnNext(msg -> {
                    // 📌 @教学: doOnNext 在值到达时调用，但 Mono 最多调用一次
                    log.info("[Mono.simple] 返回值: {}", msg);
                })
                .doOnComplete(() -> {
                    // 📌 @教学: doOnComplete 在 Mono 完成时调用一次
                    log.info("[Mono.simple] Mono 完成");
                });
    }

    /**
     * Demo 2: 延迟返回结果 - delayElement()
     *
     * <h3>学习要点</h3>
     * <ul>
     *     <li>✅ delayElement(Duration) 延迟 Mono 的结果返回</li>
     *     <li>✅ 演示了"非阻塞延迟"vs"阻塞延迟"的区别</li>
     *     <li>✅ 在延迟期间，该线程可以处理其他请求</li>
     * </ul>
     *
     * <h3>执行时间线</h3>
     * <pre>
     * 时刻 0ms   : 请求到达
     * 时刻 0ms   : 创建 Mono → delayElement(2秒)
     * 时刻 0-2s  : 等待中... 但线程不被阻塞！
     * 时刻 2000ms: 结果准备好 → onNext() → 返回给客户端
     * 时刻 2000ms: onComplete()
     *
     * ✅ 关键点: 2 秒的等待期间，EventLoop 线程可以处理其他请求
     * </pre>
     *
     * <h3>对比</h3>
     * <pre>
     * // ❌ 错误: Thread.sleep 会完全阻塞线程
     * Mono.just("result")
     *     .map(r -> {
     *         Thread.sleep(2000);  // 阻塞！该线程完全无法处理其他请求
     *         return r;
     *     })
     *     .subscribe();
     *
     * // ✅ 正确: delayElement 不阻塞，EventLoop 可以处理其他请求
     * Mono.just("result")
     *     .delayElement(Duration.ofSeconds(2))
     *     .subscribe();
     * </pre>
     *
     * @return Mono<String> 延迟 2 秒后返回的 Mono
     */
    @GetMapping("/delay")
    public Mono<String> delayMono() {
        return Mono.just("延迟 2 秒后的结果")
                .delayElement(Duration.ofSeconds(2))
                .doOnNext(msg -> {
                    // 📌 @教学: 这个日志会在 2 秒后打印
                    log.info("[Mono.delay] 2 秒后返回: {}", msg);
                });
    }

    /**
     * Demo 3: 从 Callable 创建 - Mono.fromCallable()
     *
     * <h3>学习要点</h3>
     * <ul>
     *     <li>✅ Mono.fromCallable() 从一个阻塞操作创建 Mono</li>
     *     <li>✅ Callable 被 Mono 延迟执行 (订阅时才执行)</li>
     *     <li>✅ 常用于包装阻塞的数据库查询或 API 调用</li>
     * </ul>
     *
     * <h3>实际应用场景</h3>
     * <pre>
     * // 场景: 从缓存或数据库查询用户
     * public Mono<User> getUserById(Long id) {
     *     return Mono.fromCallable(() -> {
     *         // 这里是阻塞的数据库查询
     *         return database.queryUser(id);
     *     })
     *     .subscribeOn(Schedulers.boundedElastic())  // 在 IO 线程池执行
     *     .timeout(Duration.ofSeconds(5))  // 5 秒超时
     *     .onErrorResume(ex -> Mono.just(User.EMPTY));  // 错误处理
     * }
     * </pre>
     *
     * @param userId 用户 ID
     * @return Mono<String> 用户名
     */
    @GetMapping("/from-callable/{userId}")
    public Mono<String> fromCallableMono(@PathVariable Integer userId) {
        return Mono.fromCallable(() -> {
            // 📌 @教学: 这个 Callable 在订阅时才会执行
            log.info("[Mono.fromCallable] 执行中，查询用户 ID: {}", userId);

            // 模拟数据库查询
            String userName = USER_CACHE.getOrDefault(userId, "用户不存在");
            log.info("[Mono.fromCallable] 查询结果: {}", userName);
            return userName;
        })
        .doOnNext(name -> {
            log.info("[Mono.fromCallable] 返回: {}", name);
        });
    }

    /**
     * Demo 4: 空 Mono - Mono.empty()
     *
     * <h3>学习要点</h3>
     * <ul>
     *     <li>✅ Mono.empty() 表示一个不包含值的 Mono</li>
     *     <li>✅ 它会直接调用 onComplete()，不会调用 onNext()</li>
     *     <li>✅ 常用于"无结果"或"删除成功"等场景</li>
     * </ul>
     *
     * <h3>执行流程</h3>
     * <pre>
     * Mono.empty()
     *   └─ onComplete() [直接完成，无 onNext]
     *
     * 对比：
     *   Mono.just("value")
     *     └─ onNext("value") → onComplete()
     *
     *   Mono.empty()
     *     └─ onComplete() [无 onNext]
     * </pre>
     *
     * <h3>实际应用</h3>
     * <pre>
     * // 例1: 删除用户后返回空 Mono
     * public Mono<Void> deleteUser(Long id) {
     *     return userRepository.deleteById(id)
     *         .then(Mono.empty());  // 删除完毕，返回空
     * }
     *
     * // 例2: 条件判断
     * public Mono<User> getUserIfExists(Long id) {
     *     User user = cache.get(id);
     *     return user != null ? Mono.just(user) : Mono.empty();
     * }
     * </pre>
     *
     * @return Mono<Void> 空 Mono
     */
    @GetMapping("/empty")
    public Mono<String> emptyMono() {
        return Mono.empty()
                .doOnNext(value -> {
                    // 📌 @教学: 这个代码永远不会执行，因为没有值
                    log.info("[Mono.empty] 这一行永远不会打印");
                })
                .doOnComplete(() -> {
                    // 📌 @教学: 这个代码会执行，表示 Mono 完成
                    log.info("[Mono.empty] Mono 完成 (无值)");
                })
                .defaultIfEmpty("默认值")  // 如果空，返回默认值
                .doOnNext(value -> {
                    log.info("[Mono.empty] 返回默认值: {}", value);
                });
    }

    /**
     * Demo 5: 错误 Mono - Mono.error()
     *
     * <h3>学习要点</h3>
     * <ul>
     *     <li>✅ Mono.error() 创建一个将产生错误的 Mono</li>
     *     <li>✅ 它会调用 onError()，不会调用 onNext() 或 onComplete()</li>
     *     <li>✅ onErrorResume() 或 onErrorReturn() 可以恢复</li>
     * </ul>
     *
     * <h3>三种错误处理方式</h3>
     * <pre>
     * 方式 1: onErrorResume - 返回替代 Mono
     *   错误 → onErrorResume 捕获 → 返回新 Mono
     *
     * 方式 2: onErrorReturn - 返回单个值
     *   错误 → onErrorReturn 捕获 → 返回固定值
     *
     * 方式 3: onErrorMap - 转换错误类型
     *   错误 → onErrorMap 转换 → 返回新错误
     * </pre>
     *
     * <h3>常见陷阱</h3>
     * <ul>
     *     <li>❌ 陷阱: 没有处理错误，错误被吞掉
     *         <br/>原因: 没有 onError 或 onErrorResume 处理
     *         <br/>✅ 解决: 总是使用 onErrorResume 或 onErrorReturn</li>
     * </ul>
     *
     * @return Mono<String> 错误 Mono，但会恢复
     */
    @GetMapping("/error")
    public Mono<String> errorMono() {
        return Mono.error(new RuntimeException("模拟错误: 网络连接超时"))
                .doOnError(error -> {
                    // 📌 @教学: doOnError 可以观察错误，但不改变它
                    log.error("[Mono.error] 捕获错误: {}", error.getMessage());
                })
                .onErrorResume(error -> {
                    // 📌 @教学: onErrorResume 可以恢复，返回替代 Mono
                    log.info("[Mono.error] 错误已恢复，返回缓存数据");
                    return Mono.just("缓存数据 (服务不可用)");
                })
                .doOnNext(value -> {
                    log.info("[Mono.error] 最终返回: {}", value);
                });
    }

    /**
     * Bonus Demo 6: 从 CompletableFuture 创建
     *
     * <h3>学习要点</h3>
     * <ul>
     *     <li>✅ Mono.fromFuture() 将 CompletableFuture 转换为 Mono</li>
     *     <li>✅ 用于集成旧的异步 API</li>
     * </ul>
     *
     * @return Mono<String> 从 CompletableFuture 创建的 Mono
     */
    @GetMapping("/from-future")
    public Mono<String> fromFutureMono() {
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(1000);  // 模拟异步操作
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "来自 CompletableFuture 的结果";
        });

        return Mono.fromFuture(future)
                .doOnNext(value -> log.info("[Mono.fromFuture] {}", value));
    }

    /**
     * Bonus Demo 7: 条件判断 - Mono with map and filter
     *
     * <h3>学习要点</h3>
     * <ul>
     *     <li>✅ map() 可以在 Mono 上进行转换</li>
     *     <li>✅ filter() 可以条件判断</li>
     * </ul>
     *
     * @param userId 用户 ID
     * @return Mono<String> 处理后的结果
     */
    @GetMapping("/map-filter/{userId}")
    public Mono<String> mapFilterMono(@PathVariable Integer userId) {
        return Mono.just(userId)
                .filter(id -> id > 0)
                .flatMap(id -> {
                    // 📌 @教学: flatMap 用于异步操作
                    String userName = USER_CACHE.get(id);
                    return userName != null
                            ? Mono.just(userName)
                            : Mono.error(new RuntimeException("用户不存在"));
                })
                .map(name -> "欢迎, " + name)
                .doOnNext(msg -> log.info("[Mono.mapFilter] {}", msg))
                .onErrorReturn("欢迎, 访客");
    }

}
