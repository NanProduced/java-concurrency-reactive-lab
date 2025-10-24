package nan.tech.lab09.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * 反应式数据库集成演示 - R2DBC
 *
 * <h2>教学目标</h2>
 * <ul>
 *     <li>理解 R2DBC 的异步、非阻塞数据库操作</li>
 *     <li>掌握 Mono/Flux 与数据库仓储的集成</li>
 *     <li>学习反应式数据库查询和操作流程</li>
 *     <li>对比 JPA (阻塞) vs R2DBC (非阻塞)</li>
 * </ul>
 *
 * <h2>核心概念</h2>
 *
 * <h3>R2DBC vs JPA</h3>
 * <pre>
 * JPA (阻塞模式):
 *   User user = repository.findById(id);     // 线程阻塞等待数据库
 *   处理 user;
 *   ↓
 *   缺点: 数据库操作多时，线程被阻塞，无法高效处理其他请求
 *
 * R2DBC (非阻塞模式):
 *   Mono<User> userMono = repository.findById(id);  // 返回异步操作
 *   userMono.subscribe(user -> 处理 user);          // 异步订阅结果
 *   ↓
 *   优点: 线程不阻塞，可继续处理其他请求，充分利用服务器资源
 * </pre>
 *
 * <h3>Mono vs Flux 在数据库操作中的应用</h3>
 * <pre>
 * Mono<User>:
 *   - 返回 0 或 1 条记录（findById、findOne）
 *   - 单条插入、更新、删除
 *
 * Flux<User>:
 *   - 返回多条记录（findAll、自定义查询）
 *   - 批量操作
 * </pre>
 *
 * @author Claude Code + Lab-09 团队
 * @version 1.0.0
 * @since 2025-10-23
 */
@Slf4j
@RestController
@RequestMapping("/integration/db")
public class ReactiveDbController {

    private final UserRepository userRepository;

    public ReactiveDbController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Demo 1: 创建用户 (INSERT)
     *
     * <h3>演示内容</h3>
     * <ul>
     *     <li>新建用户对象</li>
     *     <li>调用 save() 方法保存到数据库</li>
     *     <li>异步接收保存后的用户（包含生成的 ID）</li>
     * </ul>
     *
     * @param username 用户名
     * @param email    邮箱
     * @param age      年龄
     * @return 保存后的用户（包含自增 ID）
     */
    @PostMapping("/create")
    public Mono<User> createUser(
            @RequestParam String username,
            @RequestParam String email,
            @RequestParam Integer age) {
        log.info("📝 [R2DBC] 创建用户: {}", username);

        User newUser = new User(username, email, age, "新用户");

        return userRepository.save(newUser)
                .doOnNext(saved -> log.info("✅ 用户已保存，ID: {}", saved.getId()))
                .doOnError(err -> log.error("❌ 保存失败: {}", err.getMessage()));
    }

    /**
     * Demo 2: 查询所有用户 (SELECT ALL)
     *
     * <h3>演示内容</h3>
     * <ul>
     *     <li>调用 findAll() 返回 Flux<User></li>
     *     <li>流式处理每个用户</li>
     *     <li>支持背压和流控制</li>
     * </ul>
     *
     * @return 所有用户流
     */
    @GetMapping("/all")
    public Flux<User> getAllUsers() {
        log.info("📚 [R2DBC] 查询所有用户");

        return userRepository.findAll()
                .doOnNext(user -> log.info("  用户: {} ({})", user.getUsername(), user.getEmail()))
                .doOnComplete(() -> log.info("✅ 查询完成"))
                .doOnError(err -> log.error("❌ 查询失败: {}", err.getMessage()));
    }

    /**
     * Demo 3: 按 ID 查询用户 (SELECT BY ID)
     *
     * <h3>演示内容</h3>
     * <ul>
     *     <li>调用 findById() 返回 Mono<User></li>
     *     <li>处理用户存在或不存在的情况</li>
     *     <li>异步操作不阻塞线程</li>
     * </ul>
     *
     * @param id 用户 ID
     * @return 查询到的用户或空 Mono
     */
    @GetMapping("/{id}")
    public Mono<User> getUserById(@PathVariable Long id) {
        log.info("🔍 [R2DBC] 按 ID 查询用户: {}", id);

        return userRepository.findById(id)
                .doOnNext(user -> log.info("  找到用户: {} (年龄: {})", user.getUsername(), user.getAge()))
                .switchIfEmpty(Mono.fromRunnable(() -> log.warn("  用户不存在: {}", id))
                        .then(Mono.empty()))
                .doOnError(err -> log.error("❌ 查询失败: {}", err.getMessage()));
    }

    /**
     * Demo 4: 按年龄范围查询用户 (WHERE 条件查询)
     *
     * <h3>演示内容</h3>
     * <ul>
     *     <li>使用自定义查询方法 findByAgeBetween()</li>
     *     <li>返回 Flux 支持流式处理多个结果</li>
     *     <li>演示反应式流的背压机制</li>
     * </ul>
     *
     * @param minAge 最小年龄
     * @param maxAge 最大年龄
     * @return 年龄范围内的用户流
     */
    @GetMapping("/age-range")
    public Flux<User> getUsersByAgeRange(
            @RequestParam(defaultValue = "20") Integer minAge,
            @RequestParam(defaultValue = "40") Integer maxAge) {
        log.info("🎂 [R2DBC] 查询年龄范围: {} - {}", minAge, maxAge);

        return userRepository.findByAgeBetween(minAge, maxAge)
                .doOnNext(user -> log.info("  匹配: {} (年龄: {})", user.getUsername(), user.getAge()))
                .doOnComplete(() -> log.info("✅ 查询完成"));
    }

    /**
     * Demo 5: 更新用户
     *
     * <h3>演示内容</h3>
     * <ul>
     *     <li>先查询现有用户（findById）</li>
     *     <li>修改用户属性</li>
     *     <li>调用 save() 更新数据库</li>
     *     <li>链式操作展示反应式流的组合</li>
     * </ul>
     *
     * @param id  用户 ID
     * @param bio 新的用户简介
     * @return 更新后的用户
     */
    @PutMapping("/{id}/bio")
    public Mono<User> updateUserBio(
            @PathVariable Long id,
            @RequestParam String bio) {
        log.info("✏️  [R2DBC] 更新用户简介: {}", id);

        return userRepository.findById(id)
                .doOnNext(u -> log.info("  找到用户: {}", u.getUsername()))
                .map(user -> {
                    user.setBio(bio);  // 修改简介
                    return user;
                })
                .flatMap(userRepository::save)  // 保存更新
                .doOnNext(updated -> log.info("✅ 用户已更新: {}", updated.getUsername()))
                .doOnError(err -> log.error("❌ 更新失败: {}", err.getMessage()));
    }

    /**
     * Demo 6: 删除用户
     *
     * <h3>演示内容</h3>
     * <ul>
     *     <li>调用 deleteById() 删除用户</li>
     *     <li>返回 Mono<Void> 表示删除操作完成</li>
     *     <li>异步删除不阻塞线程</li>
     * </ul>
     *
     * @param id 用户 ID
     * @return 删除操作完成信号
     */
    @DeleteMapping("/{id}")
    public Mono<Void> deleteUser(@PathVariable Long id) {
        log.info("🗑️  [R2DBC] 删除用户: {}", id);

        return userRepository.deleteById(id)
                .doOnSuccess(v -> log.info("✅ 用户已删除: {}", id))
                .doOnError(err -> log.error("❌ 删除失败: {}", err.getMessage()));
    }

    /**
     * Demo 7: 搜索用户（自定义查询）
     *
     * <h3>演示内容</h3>
     * <ul>
     *     <li>使用 @Query 注解的自定义 SQL 查询</li>
     *     <li>演示模糊查询（LIKE）</li>
     *     <li>返回 Flux 处理多个搜索结果</li>
     * </ul>
     *
     * @param keyword 搜索关键字（用户名或邮箱）
     * @return 匹配的用户流
     */
    @GetMapping("/search")
    public Flux<User> searchUsers(@RequestParam String keyword) {
        log.info("🔎 [R2DBC] 搜索用户: {}", keyword);

        return userRepository.searchByKeyword(keyword)
                .doOnNext(user -> log.info("  匹配: {} <{}@...>", user.getUsername(), user.getEmail()))
                .doOnComplete(() -> log.info("✅ 搜索完成"));
    }

    /**
     * Demo 8: 统计用户数量（聚合查询）
     *
     * <h3>演示内容</h3>
     * <ul>
     *     <li>使用 COUNT(*) 聚合函数</li>
     *     <li>返回单个长整数结果</li>
     *     <li>演示 Mono 处理单一结果</li>
     * </ul>
     *
     * @return 用户总数
     */
    @GetMapping("/count")
    public Mono<Long> countUsers() {
        log.info("📊 [R2DBC] 统计用户总数");

        return userRepository.countAllUsers()
                .doOnNext(count -> log.info("✅ 用户总数: {}", count));
    }

}
