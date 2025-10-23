package nan.tech.lab09;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Lab-09: Spring WebFlux - 响应式编程与异步HTTP栈
 *
 * <h2>学习目标</h2>
 * <ul>
 *     <li>响应式编程基础 (Flux/Mono/背压)</li>
 *     <li>完整的非阻塞请求处理链路</li>
 *     <li>生产级集成方案 (R2DBC/Redis/Kafka)</li>
 *     <li>MVC Async vs WebFlux 性能对比</li>
 * </ul>
 *
 * <h2>项目结构</h2>
 * <pre>
 * lab-09-springmvc-vs-webflux/
 * ├── src/main/java/nan/tech/lab09/
 * │   ├── basic/              # Phase 1: Flux/Mono 基础示例
 * │   ├── advanced/           # Phase 2: 操作符 + 背压演示
 * │   ├── realworld/          # Phase 3: 生产集成 (R2DBC/Redis/Kafka)
 * │   ├── config/             # WebFlux 配置
 * │   └── Lab09Application.java
 * ├── src/test/java/nan/tech/lab09/
 * │   └── benchmark/          # Phase 4: 性能测试 (JMH)
 * ├── docs/
 * │   ├── 00_REACTOR_VS_REACTIVE_CONCEPTS.md    # 三个关键概念澄清
 * │   ├── 01_FLUX_MONO_FUNDAMENTALS.md          # Flux/Mono 基础
 * │   ├── 02_BACKPRESSURE_EXPLAINED.md          # 背压完全指南
 * │   ├── phase-1-basics.md
 * │   ├── phase-2-operators.md
 * │   └── phase-4-performance.md
 * └── README.md
 * </pre>
 *
 * <h2>快速开始</h2>
 * <pre>
 * # 构建项目
 * mvn clean install -DskipTests
 *
 * # 运行应用
 * mvn spring-boot:run
 *
 * # 访问演示
 * curl http://localhost:8080/basic/mono/simple
 * curl http://localhost:8080/basic/flux/simple
 * curl http://localhost:8080/basic/mono/delay
 * </pre>
 *
 * <h2>推荐学习顺序</h2>
 * <ol>
 *     <li><strong>前置知识 (15min)</strong>
 *         <ul>
 *             <li>阅读 {@code docs/00_REACTOR_VS_REACTIVE_CONCEPTS.md}</li>
 *             <li>理解 Reactor 模式 vs 响应式编程 vs 响应式系统的区别</li>
 *             <li>阅读 {@code docs/01_FLUX_MONO_FUNDAMENTALS.md}</li>
 *             <li>理解 Flux/Mono 的本质和生命周期</li>
 *         </ul>
 *     </li>
 *     <li><strong>Phase 1: Flux/Mono 基础 (30min)</strong>
 *         <ul>
 *             <li>运行 basic 包下的演示</li>
 *             <li>体验 Mono/Flux 的订阅和执行</li>
 *         </ul>
 *     </li>
 *     <li><strong>Phase 2: 操作符 + 背压 (45min)</strong>
 *         <ul>
 *             <li>学习 map/flatMap/merge/zip 操作符</li>
 *             <li>理解背压处理机制 ({@code docs/02_BACKPRESSURE_EXPLAINED.md})</li>
 *         </ul>
 *     </li>
 *     <li><strong>Phase 3: 生产集成 (1h)</strong>
 *         <ul>
 *             <li>学习 R2DBC 异步数据访问</li>
 *             <li>体验 Redis/Kafka 集成</li>
 *         </ul>
 *     </li>
 *     <li><strong>Phase 4: 性能对标 (30min)</strong>
 *         <ul>
 *             <li>查看性能对比数据</li>
 *             <li>理解何时选择 WebFlux</li>
 *         </ul>
 *     </li>
 * </ol>
 *
 * <h2>核心概念速查</h2>
 * <ul>
 *     <li><strong>Mono&lt;T&gt;</strong>: 0 个或 1 个元素的异步结果
 *         <ul>
 *             <li>用于: 单个查询、单个 API 调用</li>
 *             <li>例子: {@code Mono<User>, Mono<String>}</li>
 *         </ul>
 *     </li>
 *     <li><strong>Flux&lt;T&gt;</strong>: 0、1、多个或无限个元素的异步流
 *         <ul>
 *             <li>用于: 列表查询、事件流、SSE 推送</li>
 *             <li>例子: {@code Flux<User>, Flux<Event>}</li>
 *         </ul>
 *     </li>
 *     <li><strong>背压 (Backpressure)</strong>: 消费者控制生产者速度，避免 OOM
 *         <ul>
 *             <li>自动处理: Reactor 会自动检测和调整</li>
 *             <li>显式配置: buffer(), take(), onBackpressure*</li>
 *         </ul>
 *     </li>
 *     <li><strong>非阻塞 I/O</strong>: 数据库、API 调用都返回异步结果
 *         <ul>
 *             <li>R2DBC 而非 JDBC</li>
 *             <li>RestClient.get() 而非 RestTemplate.getForObject()</li>
 *         </ul>
 *     </li>
 * </ul>
 *
 * <h2>常见陷阱</h2>
 * <ul>
 *     <li>❌ 定义了 Mono/Flux 但没有订阅 → 不会执行</li>
 *     <li>❌ 只用一参数 subscribe(onNext) → 错误会被吞掉</li>
 *     <li>❌ 在 Mono.onNext() 中进行阻塞操作 → 阻塞 EventLoop</li>
 *     <li>❌ 混淆 Mono&lt;T&gt; 和 T 的类型 → 无法访问值</li>
 * </ul>
 *
 * <h2>Phase 1-4 交付物</h2>
 * <ul>
 *     <li><strong>Phase 1</strong>: 10+ 个基础演示 (FluxController, MonoController)</li>
 *     <li><strong>Phase 2</strong>: 4 个关键操作符 + 背压演示</li>
 *     <li><strong>Phase 3</strong>: R2DBC + Redis + Kafka 集成</li>
 *     <li><strong>Phase 4</strong>: 性能对标数据 + 决策指南</li>
 * </ul>
 *
 * @author Claude Code + Compounding Engineering System
 * @version 1.0.0
 * @since 2025-10-23
 */
@SpringBootApplication
@ComponentScan(basePackages = {
        "nan.tech.lab09",
        "nan.tech.lab00.util"  // 导入 Lab-00 的工具库
})
public class Lab09Application {

    /**
     * 应用程序入口点
     *
     * 启动后访问:
     * <ul>
     *     <li>http://localhost:8080/basic/mono/simple - 简单 Mono 示例</li>
     *     <li>http://localhost:8080/basic/flux/simple - 简单 Flux 示例</li>
     *     <li>http://localhost:8080/basic/mono/delay - 延迟 Mono 示例</li>
     * </ul>
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(Lab09Application.class, args);
        System.out.println("""

                ╔═══════════════════════════════════════════════╗
                ║  Lab-09: Spring WebFlux 启动成功               ║
                ║  http://localhost:8080                        ║
                ║                                               ║
                ║  推荐学习路径：                                ║
                ║  1. 阅读 docs/00_REACTOR_VS_REACTIVE_CONCEPTS.md
                ║  2. 阅读 docs/01_FLUX_MONO_FUNDAMENTALS.md    ║
                ║  3. 阅读 docs/02_BACKPRESSURE_EXPLAINED.md    ║
                ║  4. 运行 /basic 端点的演示                    ║
                ║  5. 学习 Phase 1-4 代码                       ║
                ║                                               ║
                ║  关键概念：                                    ║
                ║  • Mono<T>: 0 或 1 个元素的异步结果           ║
                ║  • Flux<T>: 0+ 个元素的异步流                ║
                ║  • 背压: 自动速度控制，避免 OOM               ║
                ║  • 非阻塞 I/O: 数据库、API 都异步调用         ║
                ╚═══════════════════════════════════════════════╝
                """);
    }
}
