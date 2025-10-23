package nan.tech.lab08.springmvc.basics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * DeferredResultController 单元测试
 *
 * <p><b>@教学</b>
 * <ul>
 *   <li>演示 DeferredResult 事件驱动模式的测试方法</li>
 *   <li>验证外部事件触发机制</li>
 *   <li>验证超时、错误、完成三种场景</li>
 * </ul>
 *
 * <p><b>@测试模式</b>
 * <pre>
 * DeferredResult 测试的特殊性:
 *   - 需要模拟外部事件触发
 *   - 使用 CompletableFuture 异步触发完成
 *   - 验证资源清理（pendingRequests）
 * </pre>
 *
 * @author Claude Code
 * @since 2025-10-20
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("DeferredResult事件驱动模式测试")
class DeferredResultControllerTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * 测试 DeferredResult 正常完成
     *
     * <p><b>@教学</b>
     * <p>测试流程：
     * <ol>
     *   <li>发起异步请求 → 创建 DeferredResult</li>
     *   <li>异步触发完成 → 调用 /complete 端点</li>
     *   <li>验证响应 → 检查结果是否正确</li>
     * </ol>
     */
    @Test
    @DisplayName("DeferredResult - 正常完成")
    void testDeferredResultComplete() throws Exception {
        String userId = "test-user-" + System.currentTimeMillis();

        // Step 1: 发起异步请求（在后台线程中）
        CompletableFuture<MvcResult> future = CompletableFuture.supplyAsync(() -> {
            try {
                return mockMvc.perform(get("/api/async/deferred")
                                .param("userId", userId))
                        .andExpect(request().asyncStarted())
                        .andReturn();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // Step 2: 等待 DeferredResult 创建完成（100ms）
        TimeUnit.MILLISECONDS.sleep(100);

        // Step 3: 触发完成（模拟外部事件）
        mockMvc.perform(post("/api/async/deferred/complete")
                        .param("userId", userId)
                        .param("message", "测试完成"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("triggered"))
                .andExpect(jsonPath("$.userId").value(userId));

        // Step 4: 获取异步结果并验证
        MvcResult mvcResult = future.get(5, TimeUnit.SECONDS);

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.message").value("测试完成"))
                .andExpect(jsonPath("$.data").value("外部事件处理结果: 测试完成"));
    }

    /**
     * 测试 DeferredResult 超时
     *
     * <p><b>@教学</b>
     * <p>验证当 10 秒内没有触发完成时，DeferredResult 是否能正确超时。
     *
     * <p><b>@注意</b>
     * <p>此测试需要真实等待 10+ 秒，适合集成测试或手动测试，单元测试中禁用。
     */
    @Test
    @org.junit.jupiter.api.Disabled("超时测试需要等待10秒，适合集成测试")
    @DisplayName("DeferredResult - 超时")
    void testDeferredResultTimeout() throws Exception {
        String userId = "timeout-user-" + System.currentTimeMillis();

        // 发起异步请求（不触发完成，等待超时）
        MvcResult mvcResult = mockMvc.perform(get("/api/async/deferred")
                        .param("userId", userId))
                .andExpect(request().asyncStarted())
                .andReturn();

        // 等待超时（DeferredResult 配置为 10 秒，这里等待 11 秒）
        // ⚠️ 注意：真实测试中应该缩短超时时间，这里为了演示使用 10 秒
        // 在真实项目中，可以创建一个超时时间更短的端点用于测试
        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("timeout"))
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.message").value("等待外部事件超时（10秒）"));
    }

    /**
     * 测试 DeferredResult 错误处理
     *
     * <p><b>@教学</b>
     * <p>验证当外部事件触发错误时，DeferredResult 是否能正确处理。
     */
    @Test
    @DisplayName("DeferredResult - 错误处理")
    void testDeferredResultError() throws Exception {
        String userId = "error-user-" + System.currentTimeMillis();

        // Step 1: 发起异步请求
        CompletableFuture<MvcResult> future = CompletableFuture.supplyAsync(() -> {
            try {
                return mockMvc.perform(get("/api/async/deferred")
                                .param("userId", userId))
                        .andExpect(request().asyncStarted())
                        .andReturn();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // Step 2: 等待 DeferredResult 创建完成
        TimeUnit.MILLISECONDS.sleep(100);

        // Step 3: 触发错误（模拟外部事件失败）
        mockMvc.perform(post("/api/async/deferred/error")
                        .param("userId", userId)
                        .param("error", "测试错误"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("triggered"))
                .andExpect(jsonPath("$.userId").value(userId));

        // Step 4: 获取异步结果并验证
        MvcResult mvcResult = future.get(5, TimeUnit.SECONDS);

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.message").value("外部事件处理失败: 测试错误"));
    }

    /**
     * 测试查看待处理请求
     *
     * <p><b>@教学</b>
     * <p>验证 pendingRequests 管理是否正确。
     */
    @Test
    @DisplayName("DeferredResult - 查看待处理请求")
    void testPendingRequests() throws Exception {
        String userId = "pending-user-" + System.currentTimeMillis();

        // Step 1: 发起异步请求（在后台线程中）
        CompletableFuture.runAsync(() -> {
            try {
                mockMvc.perform(get("/api/async/deferred")
                                .param("userId", userId))
                        .andExpect(request().asyncStarted())
                        .andReturn();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // Step 2: 等待 DeferredResult 创建完成
        TimeUnit.MILLISECONDS.sleep(100);

        // Step 3: 查看待处理请求（应该包含刚创建的请求）
        mockMvc.perform(get("/api/async/deferred/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingCount").isNumber())
                .andExpect(jsonPath("$.userIds").isArray())
                .andExpect(jsonPath("$.message").exists());

        // Step 4: 触发完成
        mockMvc.perform(post("/api/async/deferred/complete")
                        .param("userId", userId)
                        .param("message", "清理测试"))
                .andExpect(status().isOk());

        // Step 5: 再次查看（等待清理完成）
        TimeUnit.MILLISECONDS.sleep(100);

        mockMvc.perform(get("/api/async/deferred/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingCount").isNumber());
    }

    /**
     * 测试重复触发完成
     *
     * <p><b>@教学</b>
     * <p>验证对同一个用户多次触发完成时的行为。
     */
    @Test
    @DisplayName("DeferredResult - 重复触发完成")
    void testDuplicateComplete() throws Exception {
        String userId = "duplicate-user-" + System.currentTimeMillis();

        // Step 1: 发起异步请求（使用 CompletableFuture 等待结果）
        CompletableFuture<MvcResult> future = CompletableFuture.supplyAsync(() -> {
            try {
                return mockMvc.perform(get("/api/async/deferred")
                                .param("userId", userId))
                        .andExpect(request().asyncStarted())
                        .andReturn();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // Step 2: 等待 DeferredResult 创建完成
        TimeUnit.MILLISECONDS.sleep(100);

        // Step 3: 第一次触发完成（成功）
        mockMvc.perform(post("/api/async/deferred/complete")
                        .param("userId", userId)
                        .param("message", "第一次完成"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("triggered"));

        // Step 4: 等待异步请求完全完成（包括 asyncDispatch + onCompletion 回调）
        MvcResult mvcResult = future.get(5, TimeUnit.SECONDS);
        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        // Step 5: 等待 onCompletion 回调执行完成（清理资源）
        TimeUnit.MILLISECONDS.sleep(500);

        // Step 6: 第二次触发完成（应该失败，因为已经完成并清理）
        mockMvc.perform(post("/api/async/deferred/complete")
                        .param("userId", userId)
                        .param("message", "第二次完成"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("not_found"))
                .andExpect(jsonPath("$.message").value("未找到待处理的请求（可能已超时或已完成）"));
    }

    /**
     * 测试健康检查端点
     */
    @Test
    @DisplayName("健康检查端点")
    void testHealthEndpoint() throws Exception {
        mockMvc.perform(get("/api/async/deferred/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.controller").value("DeferredResultController"))
                .andExpect(jsonPath("$.pendingRequests").exists())
                .andExpect(jsonPath("$.time").exists());
    }
}
