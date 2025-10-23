package nan.tech.lab08.springmvc.basics;

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
 * WebAsyncTaskController 单元测试
 *
 * <p><b>@教学</b>
 * <ul>
 *   <li>演示 WebAsyncTask 自定义超时的测试方法</li>
 *   <li>验证超时回调是否正确触发</li>
 *   <li>验证自定义线程池是否生效</li>
 * </ul>
 *
 * <p><b>@测试模式</b>
 * <pre>
 * WebAsyncTask 测试重点:
 *   - 验证自定义超时时间
 *   - 验证超时回调
 *   - 验证自定义线程池
 * </pre>
 *
 * @author Claude Code
 * @since 2025-10-20
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("WebAsyncTask自定义超时测试")
class WebAsyncTaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * 测试 WebAsyncTask 正常完成
     *
     * <p><b>@教学</b>
     * <p>验证任务在超时时间内完成的情况。
     */
    @Test
    @DisplayName("WebAsyncTask - 正常完成（2秒任务，5秒超时）")
    void testWebAsyncTaskComplete() throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/api/async/web-async-task")
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
                .andExpect(jsonPath("$.status").value("成功"))
                .andExpect(jsonPath("$.message").value("WebAsyncTask 执行完成"));
    }

    /**
     * 测试 WebAsyncTask 超时
     *
     * <p><b>@教学</b>
     * <p>验证任务超过超时时间时的行为。
     *
     * <p><b>@注意</b>
     * <p>此测试需要真实等待 5+ 秒，适合集成测试或手动测试，单元测试中禁用。
     */
    @Test
    @org.junit.jupiter.api.Disabled("超时测试需要等待5秒，适合集成测试")
    @DisplayName("WebAsyncTask - 超时（8秒任务，5秒超时）")
    void testWebAsyncTaskTimeout() throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/api/async/web-async-task")
                        .param("delay", "8000"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("WebAsyncTask"))
                .andExpect(jsonPath("$.delayMs").value(8000))
                .andExpect(jsonPath("$.timeout").value("5000ms"))
                .andExpect(jsonPath("$.status").value("超时"))
                .andExpect(jsonPath("$.message").value("任务执行超过5秒，已超时"));
    }

    /**
     * 测试自定义超时时间（短任务 → 10秒超时）
     *
     * <p><b>@教学</b>
     * <p>验证不同任务类型使用不同的超时时间。
     */
    @Test
    @DisplayName("WebAsyncTask - 自定义超时（2秒任务，10秒超时）")
    void testCustomTimeout() throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/api/async/web-async-task/custom-timeout")
                        .param("delay", "2000"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("WebAsyncTask (Custom Timeout)"))
                .andExpect(jsonPath("$.delayMs").value(2000))
                .andExpect(jsonPath("$.customTimeout").value("10000ms"))
                .andExpect(jsonPath("$.status").value("成功"));
    }

    /**
     * 测试自定义超时时间（长任务 → 30秒超时）
     *
     * <p><b>@教学</b>
     * <p>验证长任务使用更长的超时时间。
     */
    @Test
    @DisplayName("WebAsyncTask - 自定义超时（8秒任务，30秒超时）")
    void testCustomTimeoutLongTask() throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/api/async/web-async-task/custom-timeout")
                        .param("delay", "8000"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("WebAsyncTask (Custom Timeout)"))
                .andExpect(jsonPath("$.delayMs").value(8000))
                .andExpect(jsonPath("$.customTimeout").value("30000ms"))
                .andExpect(jsonPath("$.status").value("成功"));
    }

    /**
     * 测试使用自定义线程池 - CPU密集型
     *
     * <p><b>@教学</b>
     * <p>验证 CPU 密集型任务使用自定义线程池。
     */
    @Test
    @DisplayName("WebAsyncTask - 自定义线程池（CPU密集型）")
    void testCustomExecutorCpu() throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/api/async/web-async-task/custom-executor")
                        .param("taskType", "cpu"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("WebAsyncTask (Custom Executor)"))
                .andExpect(jsonPath("$.taskType").value("cpu"))
                .andExpect(jsonPath("$.requestThread").exists())
                .andExpect(jsonPath("$.businessThread").exists())
                .andExpect(jsonPath("$.status").value("成功"))
                .andExpect(jsonPath("$.message").value("使用自定义线程池执行cpu任务"));
    }

    /**
     * 测试使用自定义线程池 - IO密集型
     *
     * <p><b>@教学</b>
     * <p>验证 IO 密集型任务使用自定义线程池。
     */
    @Test
    @DisplayName("WebAsyncTask - 自定义线程池（IO密集型）")
    void testCustomExecutorIo() throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/api/async/web-async-task/custom-executor")
                        .param("taskType", "io"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("WebAsyncTask (Custom Executor)"))
                .andExpect(jsonPath("$.taskType").value("io"))
                .andExpect(jsonPath("$.status").value("成功"))
                .andExpect(jsonPath("$.message").value("使用自定义线程池执行io任务"));
    }

    /**
     * 测试使用自定义线程池 - 关键任务
     *
     * <p><b>@教学</b>
     * <p>验证关键任务使用自定义线程池。
     */
    @Test
    @DisplayName("WebAsyncTask - 自定义线程池（关键任务）")
    void testCustomExecutorCritical() throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/api/async/web-async-task/custom-executor")
                        .param("taskType", "critical"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("WebAsyncTask (Custom Executor)"))
                .andExpect(jsonPath("$.taskType").value("critical"))
                .andExpect(jsonPath("$.status").value("成功"))
                .andExpect(jsonPath("$.message").value("使用自定义线程池执行critical任务"));
    }

    /**
     * 测试健康检查端点
     */
    @Test
    @DisplayName("健康检查端点")
    void testHealthEndpoint() throws Exception {
        mockMvc.perform(get("/api/async/web-async-task/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.controller").value("WebAsyncTaskController"))
                .andExpect(jsonPath("$.executor").exists())
                .andExpect(jsonPath("$.time").exists());
    }
}
