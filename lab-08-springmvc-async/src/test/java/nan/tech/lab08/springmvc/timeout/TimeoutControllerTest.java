package nan.tech.lab08.springmvc.timeout;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * TimeoutController 单元测试
 *
 * <p><b>@教学</b>
 * <ul>
 *   <li>演示超时场景的测试方法</li>
 *   <li>验证三种超时模式 (Callable/DeferredResult/WebAsyncTask)</li>
 *   <li>验证三种降级策略 (快速失败/缓存数据/默认值)</li>
 * </ul>
 *
 * <p><b>@测试模式</b>
 * <pre>
 * 超时测试注意事项:
 *   - 正常完成测试: 延迟 < 超时时间
 *   - 超时测试: 延迟 > 超时时间 (需要真实等待，适合集成测试)
 *   - 使用 @Disabled 标记长时间测试，避免影响 CI/CD
 * </pre>
 *
 * @author Claude Code
 * @since 2025-10-20
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("超时控制测试")
class TimeoutControllerTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * 测试 Callable 正常完成 (延迟 < 超时)
     */
    @Test
    @DisplayName("Callable - 正常完成（2秒任务）")
    void testCallableComplete() throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/api/timeout/callable")
                        .param("delay", "2000"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("Callable"))
                .andExpect(jsonPath("$.requestThread").exists())
                .andExpect(jsonPath("$.businessThread").exists())
                .andExpect(jsonPath("$.delayMs").value(2000))
                .andExpect(jsonPath("$.status").value("成功"));
    }

    /**
     * 测试 Callable 超时 (延迟 > 全局超时)
     *
     * <p><b>@注意</b>
     * <p>此测试需要真实等待 30+ 秒 (全局超时配置)，适合集成测试或手动测试。
     */
    @Test
    @org.junit.jupiter.api.Disabled("超时测试需要等待 30+ 秒，适合集成测试")
    @DisplayName("Callable - 超时（35秒任务，30秒超时）")
    void testCallableTimeout() throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/api/timeout/callable")
                        .param("delay", "35000"))
                .andExpect(request().asyncStarted())
                .andReturn();

        // Callable 超时后会抛出 AsyncRequestTimeoutException
        // 由 Spring MVC 全局异常处理器处理
        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isServiceUnavailable());  // 503
    }

    /**
     * 测试 DeferredResult 正常完成
     */
    @Test
    @DisplayName("DeferredResult - 正常完成（2秒任务，10秒超时）")
    void testDeferredResultComplete() throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/api/timeout/deferred")
                        .param("delay", "2000"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("DeferredResult"))
                .andExpect(jsonPath("$.requestThread").exists())
                .andExpect(jsonPath("$.businessThread").exists())
                .andExpect(jsonPath("$.delayMs").value(2000))
                .andExpect(jsonPath("$.timeout").value("10000ms"))
                .andExpect(jsonPath("$.status").value("成功"));
    }

    /**
     * 测试 DeferredResult 超时
     *
     * <p><b>@注意</b>
     * <p>此测试需要真实等待 10+ 秒，适合集成测试或手动测试。
     */
    @Test
    @org.junit.jupiter.api.Disabled("超时测试需要等待 10 秒，适合集成测试")
    @DisplayName("DeferredResult - 超时（12秒任务，10秒超时）")
    void testDeferredResultTimeout() throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/api/timeout/deferred")
                        .param("delay", "12000"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("DeferredResult"))
                .andExpect(jsonPath("$.delayMs").value(12000))
                .andExpect(jsonPath("$.timeout").value("10000ms"))
                .andExpect(jsonPath("$.status").value("超时"))
                .andExpect(jsonPath("$.message").value("请求超时，已返回降级数据"));
    }

    /**
     * 测试 WebAsyncTask 正常完成
     */
    @Test
    @DisplayName("WebAsyncTask - 正常完成（2秒任务，5秒超时）")
    void testWebAsyncTaskComplete() throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/api/timeout/web-async-task")
                        .param("delay", "2000"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("WebAsyncTask"))
                .andExpect(jsonPath("$.requestThread").exists())
                .andExpect(jsonPath("$.businessThread").exists())
                .andExpect(jsonPath("$.delayMs").value(2000))
                .andExpect(jsonPath("$.timeout").value("5000ms"))
                .andExpect(jsonPath("$.status").value("成功"));
    }

    /**
     * 测试 WebAsyncTask 超时
     *
     * <p><b>@注意</b>
     * <p>此测试需要真实等待 5+ 秒，适合集成测试或手动测试。
     */
    @Test
    @org.junit.jupiter.api.Disabled("超时测试需要等待 5 秒，适合集成测试")
    @DisplayName("WebAsyncTask - 超时（8秒任务，5秒超时）")
    void testWebAsyncTaskTimeout() throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/api/timeout/web-async-task")
                        .param("delay", "8000"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("WebAsyncTask"))
                .andExpect(jsonPath("$.delayMs").value(8000))
                .andExpect(jsonPath("$.timeout").value("5000ms"))
                .andExpect(jsonPath("$.status").value("超时"))
                .andExpect(jsonPath("$.message").value("任务执行超过 5 秒，已超时"));
    }

    /**
     * 测试快速失败降级策略 - 正常完成
     */
    @Test
    @DisplayName("快速失败策略 - 正常完成（2秒任务，3秒超时）")
    void testFailFastStrategyComplete() throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/api/timeout/strategy/fail-fast")
                        .param("delay", "2000"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data").value("业务数据"));
    }

    /**
     * 测试快速失败降级策略 - 超时
     */
    @Test
    @org.junit.jupiter.api.Disabled("超时测试需要等待 3 秒，适合集成测试")
    @DisplayName("快速失败策略 - 超时（5秒任务，3秒超时）")
    void testFailFastStrategyTimeout() throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/api/timeout/strategy/fail-fast")
                        .param("delay", "5000"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("fail"))
                .andExpect(jsonPath("$.code").value(408))
                .andExpect(jsonPath("$.message").value("功能暂时不可用，请稍后重试"));
    }

    /**
     * 测试缓存降级策略 - 首次请求正常完成
     */
    @Test
    @DisplayName("缓存降级策略 - 首次正常完成（2秒任务，3秒超时）")
    void testCachedDataStrategyFirstComplete() throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/api/timeout/strategy/cached-data")
                        .param("key", "test-key-1")
                        .param("delay", "2000"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.fromCache").value(false))
                .andExpect(jsonPath("$.data").exists());
    }

    /**
     * 测试缓存降级策略 - 超时后返回缓存
     *
     * <p><b>@前置条件</b>
     * <p>需要先执行 testCachedDataStrategyFirstComplete() 填充缓存
     */
    @Test
    @org.junit.jupiter.api.Disabled("超时测试需要等待 3 秒，且依赖缓存数据")
    @DisplayName("缓存降级策略 - 超时返回缓存（5秒任务，3秒超时）")
    void testCachedDataStrategyTimeout() throws Exception {
        // 先填充缓存
        testCachedDataStrategyFirstComplete();

        // 再触发超时
        MvcResult mvcResult = mockMvc.perform(get("/api/timeout/strategy/cached-data")
                        .param("key", "test-key-1")
                        .param("delay", "5000"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("timeout"))
                .andExpect(jsonPath("$.fromCache").value(true))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.message").value("实时数据获取超时，返回缓存数据"));
    }

    /**
     * 测试缓存降级策略 - 无缓存时超时
     */
    @Test
    @org.junit.jupiter.api.Disabled("超时测试需要等待 3 秒，适合集成测试")
    @DisplayName("缓存降级策略 - 无缓存超时（5秒任务，3秒超时）")
    void testCachedDataStrategyTimeoutNoCache() throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/api/timeout/strategy/cached-data")
                        .param("key", "non-existent-key")
                        .param("delay", "5000"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("fail"))
                .andExpect(jsonPath("$.message").value("无缓存数据可用"));
    }

    /**
     * 测试默认值降级策略 - 正常完成
     */
    @Test
    @DisplayName("默认值降级策略 - 正常完成（2秒任务，3秒超时）")
    void testDefaultValueStrategyComplete() throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/api/timeout/strategy/default-value")
                        .param("userId", "user123")
                        .param("delay", "2000"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user123"))
                .andExpect(jsonPath("$.nickname").value("真实昵称-user123"))
                .andExpect(jsonPath("$.avatar").value("https://example.com/avatar/user123.png"))
                .andExpect(jsonPath("$.degraded").value(false));
    }

    /**
     * 测试默认值降级策略 - 超时返回默认值
     */
    @Test
    @org.junit.jupiter.api.Disabled("超时测试需要等待 3 秒，适合集成测试")
    @DisplayName("默认值降级策略 - 超时返回默认值（5秒任务，3秒超时）")
    void testDefaultValueStrategyTimeout() throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/api/timeout/strategy/default-value")
                        .param("userId", "user456")
                        .param("delay", "5000"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user456"))
                .andExpect(jsonPath("$.nickname").value("用户user"))
                .andExpect(jsonPath("$.avatar").value("/default-avatar.png"))
                .andExpect(jsonPath("$.degraded").value(true))
                .andExpect(jsonPath("$.message").value("数据加载超时，已返回默认值"));
    }

    /**
     * 测试健康检查端点
     */
    @Test
    @DisplayName("健康检查端点")
    void testHealthEndpoint() throws Exception {
        mockMvc.perform(get("/api/timeout/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.controller").value("TimeoutController"))
                .andExpect(jsonPath("$.executor").exists())
                .andExpect(jsonPath("$.time").exists());
    }
}
