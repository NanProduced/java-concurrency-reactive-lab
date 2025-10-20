package nan.tech.lab08.springmvc.basics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * CallableController 单元测试
 *
 * <p><b>@教学</b>
 * <ul>
 *   <li>演示 Spring MVC 异步测试的标准模式（两步法）</li>
 *   <li>对比同步测试 vs 异步测试的区别</li>
 *   <li>验证线程切换是否成功</li>
 * </ul>
 *
 * <p><b>@测试模式</b>
 * <pre>
 * 同步测试（一步）:
 *   mockMvc.perform(get("/api/sync"))
 *          .andExpect(status().isOk())
 *          .andExpect(jsonPath("$.message").value("成功"));
 *
 * 异步测试（两步）:
 *   Step 1: 发起请求 → 验证异步已启动 → 获取 MvcResult
 *   MvcResult mvcResult = mockMvc.perform(get("/api/async"))
 *                                .andExpect(request().asyncStarted())
 *                                .andReturn();
 *
 *   Step 2: 等待异步完成 → 重新分派 → 验证响应
 *   mockMvc.perform(asyncDispatch(mvcResult))
 *          .andExpect(status().isOk())
 *          .andExpect(jsonPath("$.message").value("成功"));
 * </pre>
 *
 * @author Claude Code
 * @since 2025-10-20
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Callable异步模式测试")
class CallableControllerTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * 测试同步端点（对比基准）
     *
     * <p><b>@教学</b>
     * <p>同步端点测试只需要一步，直接验证响应。
     */
    @Test
    @DisplayName("同步端点 - 阻塞请求线程")
    void testSyncEndpoint() throws Exception {
        mockMvc.perform(get("/api/async/sync")
                        .param("delay", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("同步阻塞"))
                .andExpect(jsonPath("$.requestThread").exists())
                .andExpect(jsonPath("$.businessThread").exists())
                .andExpect(jsonPath("$.delayMs").value(100))
                .andExpect(jsonPath("$.message").value("同步处理完成（阻塞请求线程 100ms）"));
    }

    /**
     * 测试 Callable 异步端点
     *
     * <p><b>@教学</b>
     * <p>异步端点测试需要两步：
     * <ol>
     *   <li>Step 1: 验证异步已启动（request().asyncStarted()）</li>
     *   <li>Step 2: 等待异步完成并验证响应（asyncDispatch）</li>
     * </ol>
     */
    @Test
    @DisplayName("Callable异步端点 - 正常完成")
    void testCallableEndpoint() throws Exception {
        // Step 1: 发起异步请求
        MvcResult mvcResult = mockMvc.perform(get("/api/async/callable")
                        .param("delay", "100"))
                .andExpect(request().asyncStarted())  // ✅ 验证异步已启动
                .andExpect(request().asyncResult(org.hamcrest.Matchers.notNullValue()))  // ✅ 验证异步结果不为空
                .andReturn();

        // Step 2: 等待异步完成并验证响应
        mockMvc.perform(asyncDispatch(mvcResult))  // ⚡ 重新分派到 Servlet 容器
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("Callable 异步"))
                .andExpect(jsonPath("$.requestThread").exists())
                .andExpect(jsonPath("$.businessThread").exists())
                .andExpect(jsonPath("$.threadSwitch").value(true))  // ✅ 验证线程切换
                .andExpect(jsonPath("$.delayMs").value(100))
                .andExpect(jsonPath("$.message").value("Callable 异步处理完成（请求线程已释放）"));
    }

    /**
     * 测试 Callable 异步端点 - 线程切换验证
     *
     * <p><b>@教学</b>
     * <p>验证请求线程和业务线程是否不同（证明线程切换成功）。
     */
    @Test
    @DisplayName("Callable异步端点 - 验证线程切换")
    void testCallableThreadSwitch() throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/api/async/callable")
                        .param("delay", "100"))
                .andExpect(request().asyncStarted())
                .andReturn();

        // 获取异步结果
        Object asyncResult = mvcResult.getAsyncResult();
        assertThat(asyncResult).isNotNull();

        // 如果返回的是 Map，验证线程切换
        if (asyncResult instanceof java.util.Map) {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> result = (java.util.Map<String, Object>) asyncResult;

            String requestThread = (String) result.get("requestThread");
            String businessThread = (String) result.get("businessThread");
            Boolean threadSwitch = (Boolean) result.get("threadSwitch");

            assertThat(requestThread).isNotNull();
            assertThat(businessThread).isNotNull();
            assertThat(threadSwitch).isTrue();  // ✅ 线程切换成功
            assertThat(requestThread).isNotEqualTo(businessThread);  // ✅ 请求线程 ≠ 业务线程
        }
    }

    /**
     * 测试 Callable 异步端点 - 错误处理
     *
     * <p><b>@教学</b>
     * <p>验证 Callable 中的异常是否能正确传播。
     */
    @Test
    @DisplayName("Callable异步端点 - 错误处理")
    void testCallableError() throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/api/async/callable/error")
                        .param("shouldFail", "true"))
                .andExpect(request().asyncStarted())
                .andReturn();

        // 验证异步结果包含异常
        Object asyncResult = mvcResult.getAsyncResult();

        // Spring MVC 会将异常包装在 asyncResult 中
        // 如果是异常，asyncResult 应该是 Exception 类型
        assertThat(asyncResult).isInstanceOf(Exception.class);
    }

    /**
     * 测试健康检查端点
     */
    @Test
    @DisplayName("健康检查端点")
    void testHealthEndpoint() throws Exception {
        mockMvc.perform(get("/api/async/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.module").value("Lab-08: Spring MVC Async"))
                .andExpect(jsonPath("$.time").exists());
    }
}
