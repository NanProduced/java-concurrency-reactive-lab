package nan.tech.lab09.integration;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 用户仓储接口 - R2DBC 反应式仓储
 *
 * <h2>功能</h2>
 * <ul>
 *     <li>继承自 ReactiveCrudRepository，提供基础的 CRUD 操作</li>
 *     <li>支持自定义查询方法 (方法名查询、@Query 注解)</li>
 *     <li>所有方法都返回 Mono 或 Flux，支持异步操作</li>
 * </ul>
 *
 * <h2>R2DBC vs JPA 比较</h2>
 * <pre>
 * JPA (同步、阻塞):
 *   User user = userRepository.findById(1L);  // 阻塞等待结果
 *
 * R2DBC (异步、非阻塞):
 *   Mono<User> user = userRepository.findById(1L);  // 返回异步流
 *   user.subscribe(u -> log.info("得到用户: {}", u));  // 异步订阅
 * </pre>
 *
 * @author Claude Code + Lab-09 团队
 * @version 1.0.0
 * @since 2025-10-23
 */
@Repository
public interface UserRepository extends R2dbcRepository<User, Long> {

    /**
     * 按用户名查询用户（方法名查询）
     *
     * @param username 用户名
     * @return 匹配的用户 Mono
     */
    Mono<User> findByUsername(String username);

    /**
     * 查询所有年龄大于指定值的用户
     *
     * @param age 最小年龄
     * @return 匹配的用户流
     */
    Flux<User> findByAgeGreaterThan(Integer age);

    /**
     * 使用 @Query 注解的自定义查询
     * 查询所有包含特定关键字的用户（用户名或邮箱）
     *
     * @param keyword 搜索关键字
     * @return 匹配的用户流
     */
    @Query("""
            SELECT * FROM users
            WHERE username LIKE CONCAT('%', :keyword, '%')
               OR email LIKE CONCAT('%', :keyword, '%')
            ORDER BY id DESC
            """)
    Flux<User> searchByKeyword(String keyword);

    /**
     * 查询年龄在指定范围内的用户
     *
     * @param minAge 最小年龄
     * @param maxAge 最大年龄
     * @return 匹配的用户流
     */
    Flux<User> findByAgeBetween(Integer minAge, Integer maxAge);

    /**
     * 统计所有用户数量（演示聚合函数）
     *
     * @return 用户总数
     */
    @Query("SELECT COUNT(*) FROM users")
    Mono<Long> countAllUsers();

}
