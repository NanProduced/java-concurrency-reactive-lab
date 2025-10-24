package nan.tech.lab09.integration;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 用户实体 - R2DBC 演示
 *
 * <h2>特点</h2>
 * <ul>
 *     <li>使用 @Table 注解标记为数据库表</li>
 *     <li>使用 @Id 注解标记主键</li>
 *     <li>支持 R2DBC Spring Data 仓储查询</li>
 * </ul>
 *
 * @author Claude Code + Lab-09 团队
 * @version 1.0.0
 * @since 2025-10-23
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("users")
public class User {

    /**
     * 用户 ID (主键, 自增)
     */
    @Id
    private Long id;

    /**
     * 用户名
     */
    private String username;

    /**
     * 邮箱地址
     */
    private String email;

    /**
     * 年龄
     */
    private Integer age;

    /**
     * 用户简介
     */
    private String bio;

    /**
     * 创建用户的便利构造器（不包含 ID，用于新建用户）
     *
     * @param username 用户名
     * @param email    邮箱
     * @param age      年龄
     * @param bio      简介
     */
    public User(String username, String email, Integer age, String bio) {
        this.username = username;
        this.email = email;
        this.age = age;
        this.bio = bio;
    }
}
