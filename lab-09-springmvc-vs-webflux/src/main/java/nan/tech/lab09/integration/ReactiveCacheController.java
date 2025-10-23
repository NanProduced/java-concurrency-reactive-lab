package nan.tech.lab09.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 反应式缓存集成演示 - Redis
 *
 * <h2>教学目标</h2>
 * <ul>
 *     <li>理解 Spring Data Redis Reactive 的异步操作</li>
 *     <li>掌握 String、Hash、List、Set 等 Redis 数据类型的反应式操作</li>
 *     <li>学习缓存的过期时间设置和管理</li>
 *     <li>对比同步 Redis 操作与反应式操作的优势</li>
 * </ul>
 *
 * <h2>核心概念</h2>
 *
 * <h3>Redis 数据类型</h3>
 * <pre>
 * String (字符串): 缓存用户信息、配置值
 *   key = "user:1", value = "{"name": "Alice", "age": 25}"
 *
 * Hash (哈希): 缓存对象属性
 *   key = "user:1", fields = {"name": "Alice", "age": "25", "email": "..."}
 *
 * List (列表): 消息队列、时间线
 *   key = "notifications:1", values = ["msg1", "msg2", "msg3"]
 *
 * Set (集合): 标签、去重、关系
 *   key = "tags:java", members = {"concurrent", "reactive", "performance"}
 * </pre>
 *
 * <h3>反应式 Redis 操作</h3>
 * <pre>
 * 同步 (阻塞):
 *   String value = redisTemplate.opsForValue().get("key");  // 线程阻塞
 *
 * 反应式 (非阻塞):
 *   Mono<String> value = reactiveTemplate.opsForValue().get("key");  // 异步
 *   value.subscribe(v -> log.info("值: {}", v));
 * </pre>
 *
 * @author Claude Code + Lab-09 团队
 * @version 1.0.0
 * @since 2025-10-23
 */
@Slf4j
@RestController
@RequestMapping("/integration/cache")
public class ReactiveCacheController {

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    public ReactiveCacheController(ReactiveRedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Demo 1: 字符串缓存 - 设置和获取
     *
     * <h3>演示内容</h3>
     * <ul>
     *     <li>存储简单的字符串值</li>
     *     <li>设置过期时间 (expire)</li>
     *     <li>异步读取和更新</li>
     * </ul>
     *
     * @param key   缓存键
     * @param value 缓存值
     * @return 设置结果
     */
    @PostMapping("/string")
    public Mono<Boolean> setStringCache(
            @RequestParam String key,
            @RequestParam String value) {
        log.info("💾 [Redis] 设置字符串缓存: {} = {}", key, value);

        return redisTemplate.opsForValue()
                .set(key, value, Duration.ofHours(1))  // 设置 1 小时过期
                .doOnNext(result -> log.info("  ✅ 缓存已设置, TTL: 1 小时"))
                .doOnError(err -> log.error("  ❌ 设置失败: {}", err.getMessage()));
    }

    /**
     * Demo 2: 字符串缓存 - 获取
     *
     * <h3>演示内容</h3>
     * <ul>
     *     <li>从 Redis 异步读取值</li>
     *     <li>处理缓存命中和缺失</li>
     *     <li>流式处理结果</li>
     * </ul>
     *
     * @param key 缓存键
     * @return 缓存值或空 Mono
     */
    @GetMapping("/string")
    public Mono<String> getStringCache(@RequestParam String key) {
        log.info("🔍 [Redis] 获取字符串缓存: {}", key);

        return redisTemplate.opsForValue()
                .get(key)
                .doOnNext(value -> log.info("  ✅ 缓存命中: {}", value))
                .switchIfEmpty(Mono.fromRunnable(() -> log.warn("  ⚠️  缓存未命中: {}", key))
                        .then(Mono.empty()))
                .doOnError(err -> log.error("  ❌ 获取失败: {}", err.getMessage()));
    }

    /**
     * Demo 3: 哈希缓存 - 存储对象字段
     *
     * <h3>演示内容</h3>
     * <ul>
     *     <li>使用 Hash 存储对象属性</li>
     *     <li>对比 String 存储完整 JSON</li>
     *     <li>支持单个字段更新</li>
     * </ul>
     *
     * @param key   缓存键
     * @param field 字段名
     * @param value 字段值
     * @return 操作结果
     */
    @PostMapping("/hash")
    public Mono<Boolean> setHashField(
            @RequestParam String key,
            @RequestParam String field,
            @RequestParam String value) {
        log.info("📦 [Redis] 设置 Hash 字段: {} -> {} = {}", key, field, value);

        return redisTemplate.opsForHash()
                .put(key, field, value)
                .doOnNext(result -> log.info("  ✅ 字段已设置"))
                .doOnError(err -> log.error("  ❌ 设置失败: {}", err.getMessage()));
    }

    /**
     * Demo 4: 哈希缓存 - 获取所有字段
     *
     * <h3>演示内容</h3>
     * <ul>
     *     <li>获取整个 Hash 对象</li>
     *     <li>返回 Flux<Map.Entry> 流式处理</li>
     * </ul>
     *
     * @param key 缓存键
     * @return Hash 中所有的字段-值对流
     */
    @GetMapping("/hash")
    public Flux<Map.Entry<Object, Object>> getHashAll(@RequestParam String key) {
        log.info("📚 [Redis] 获取 Hash 所有字段: {}", key);

        return redisTemplate.opsForHash()
                .entries(key)
                .doOnNext(entry -> log.info("  字段: {} = {}", entry.getKey(), entry.getValue()))
                .doOnError(err -> log.error("  ❌ 获取失败: {}", err.getMessage()));
    }

    /**
     * Demo 5: 列表缓存 - 推送和弹出
     *
     * <h3>演示内容</h3>
     * <ul>
     *     <li>使用 List 实现消息队列</li>
     *     <li>支持左推、右推、左弹、右弹操作</li>
     * </ul>
     *
     * @param key   列表键
     * @param value 要推送的值
     * @return 推送后的列表大小
     */
    @PostMapping("/list/push")
    public Mono<Long> pushToList(
            @RequestParam String key,
            @RequestParam String value) {
        log.info("📤 [Redis] 推送到列表: {} <- {}", key, value);

        return redisTemplate.opsForList()
                .rightPush(key, value)  // 从右侧推送
                .doOnNext(size -> log.info("  ✅ 列表大小: {}", size))
                .doOnError(err -> log.error("  ❌ 推送失败: {}", err.getMessage()));
    }

    /**
     * Demo 6: 列表缓存 - 读取范围
     *
     * <h3>演示内容</h3>
     * <ul>
     *     <li>获取列表中的一个范围</li>
     *     <li>返回 Flux 支持流式处理</li>
     * </ul>
     *
     * @param key 列表键
     * @return 列表中所有元素流
     */
    @GetMapping("/list/range")
    public Flux<String> getListRange(@RequestParam String key) {
        log.info("📥 [Redis] 读取列表范围: {}", key);

        return redisTemplate.opsForList()
                .range(key, 0, -1)  // 获取全部元素
                .doOnNext(value -> log.info("  元素: {}", value))
                .doOnError(err -> log.error("  ❌ 读取失败: {}", err.getMessage()));
    }

    /**
     * Demo 7: 缓存删除和过期管理
     *
     * <h3>演示内容</h3>
     * <ul>
     *     <li>删除缓存键</li>
     *     <li>获取 TTL（剩余存活时间）</li>
     *     <li>设置过期时间</li>
     * </ul>
     *
     * @param key 缓存键
     * @return 删除结果
     */
    @DeleteMapping("")
    public Mono<Boolean> deleteCache(@RequestParam String key) {
        log.info("🗑️  [Redis] 删除缓存: {}", key);

        return redisTemplate.delete(key)
                .map(count -> count > 0)
                .doOnNext(deleted -> log.info("  ✅ 缓存已删除: {}", deleted))
                .doOnError(err -> log.error("  ❌ 删除失败: {}", err.getMessage()));
    }

    /**
     * Demo 8: 获取 TTL（缓存剩余时间）
     *
     * <h3>演示内容</h3>
     * <ul>
     *     <li>获取缓存的剩余有效期</li>
     *     <li>返回 -1 表示永不过期，-2 表示键不存在</li>
     * </ul>
     *
     * @param key 缓存键
     * @return 剩余 TTL（秒）
     */
    @GetMapping("/ttl")
    public Mono<String> getTTL(@RequestParam String key) {
        log.info("⏰ [Redis] 获取 TTL: {}", key);

        return redisTemplate.getExpire(key)
                .map(duration -> {
                    if (duration.isNegative()) {
                        if (duration.getSeconds() == -1) {
                            return "缓存永不过期";
                        } else {
                            return "键不存在";
                        }
                    } else {
                        return "剩余 TTL: " + duration.getSeconds() + " 秒";
                    }
                })
                .doOnNext(result -> log.info("  ⏳ {}", result))
                .doOnError(err -> log.error("  ❌ 获取失败: {}", err.getMessage()));
    }

}
