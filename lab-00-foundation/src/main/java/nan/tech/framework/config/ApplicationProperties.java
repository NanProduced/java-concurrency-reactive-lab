package nan.tech.framework.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 应用程序属性配置。
 *
 * <p><b>@教学</b>
 * <ul>
 *   <li>使用 @ConfigurationProperties 从 application.yml 中读取配置</li>
 *   <li>前缀 "app" 对应 YAML 中的 app.* 配置项</li>
 *   <li>支持类型安全的配置注入</li>
 * </ul>
 *
 * <p><b>用法</b>
 * <pre>
 * app:
 *   name: Java Concurrency Lab
 *   version: 1.0.0
 * </pre>
 */
@ConfigurationProperties(prefix = "app")
public class ApplicationProperties {
    private String name = "Java Concurrency Lab";
    private String version = "1.0.0";

    /**
     * 获取应用程序名称。
     *
     * @return 应用名称
     */
    public String getName() {
        return name;
    }

    /**
     * 设置应用程序名称。
     *
     * @param name 应用名称
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * 获取应用程序版本。
     *
     * @return 应用版本
     */
    public String getVersion() {
        return version;
    }

    /**
     * 设置应用程序版本。
     *
     * @param version 应用版本
     */
    public void setVersion(String version) {
        this.version = version;
    }
}
