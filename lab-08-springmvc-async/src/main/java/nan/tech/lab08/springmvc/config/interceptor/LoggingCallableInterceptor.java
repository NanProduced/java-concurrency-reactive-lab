package nan.tech.lab08.springmvc.config.interceptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.async.CallableProcessingInterceptor;

import java.util.concurrent.Callable;

/**
 * Callable 异步请求处理拦截器
 *
 * <p><b>@教学</b>
 * <ul>
 *   <li>监控 Callable 异步请求的完整生命周期</li>
 *   <li>记录请求开始、预处理、后处理、超时、错误、完成等事件</li>
 *   <li>用于调试和性能分析</li>
 * </ul>
 *
 * <p><b>@生命周期</b>
 * <pre>
 * 1. beforeConcurrentHandling: Controller 返回 Callable 后，异步处理开始前
 * 2. preProcess: 异步线程开始执行 Callable 前
 * 3. postProcess: Callable 执行完成后，返回结果前
 * 4. handleTimeout: 异步处理超时时
 * 5. handleError: 异步处理异常时
 * 6. afterCompletion: 请求完成后（无论成功、失败、超时）
 * </pre>
 *
 * @author Claude Code
 * @since 2025-10-20
 */
public class LoggingCallableInterceptor implements CallableProcessingInterceptor {

    private static final Logger log = LoggerFactory.getLogger(LoggingCallableInterceptor.class);

    /**
     * 异步处理开始前
     *
     * <p><b>@教学</b>
     * <p>此时 Controller 已返回 Callable，Servlet 容器线程即将被释放。
     *
     * @param request NativeWebRequest 请求对象
     * @param task Callable 任务
     * @param <T> 返回值类型
     */
    @Override
    public <T> void beforeConcurrentHandling(NativeWebRequest request, Callable<T> task) {
        log.debug("[Callable拦截器] beforeConcurrentHandling - 异步处理即将开始: URI={}",
                request.getDescription(false));
    }

    /**
     * 异步线程开始执行前
     *
     * <p><b>@教学</b>
     * <p>此时已切换到异步线程池中的线程。
     *
     * @param request NativeWebRequest 请求对象
     * @param task Callable 任务
     * @param <T> 返回值类型
     */
    @Override
    public <T> void preProcess(NativeWebRequest request, Callable<T> task) {
        log.debug("[Callable拦截器] preProcess - 异步任务开始执行: 线程={}",
                Thread.currentThread().getName());
    }

    /**
     * Callable 执行完成后
     *
     * <p><b>@教学</b>
     * <p>此时 Callable 已返回结果，即将重新分派请求到 Servlet 容器。
     *
     * @param request NativeWebRequest 请求对象
     * @param task Callable 任务
     * @param concurrentResult Callable 返回的结果
     * @param <T> 返回值类型
     */
    @Override
    public <T> void postProcess(NativeWebRequest request, Callable<T> task, Object concurrentResult) {
        log.debug("[Callable拦截器] postProcess - 异步任务执行完成: 结果={}", concurrentResult);
    }

    /**
     * 处理超时
     *
     * <p><b>@教学</b>
     * <p>当异步处理超过配置的超时时间时触发。
     *
     * @param request NativeWebRequest 请求对象
     * @param task Callable 任务
     * @param <T> 返回值类型
     * @return 超时处理结果（可选）
     */
    @Override
    public <T> Object handleTimeout(NativeWebRequest request, Callable<T> task) {
        log.warn("[Callable拦截器] handleTimeout - 异步处理超时: URI={}",
                request.getDescription(false));
        return RESULT_NONE; // 返回 RESULT_NONE 表示继续调用其他拦截器
    }

    /**
     * 处理错误
     *
     * <p><b>@教学</b>
     * <p>当 Callable 执行过程中抛出异常时触发。
     *
     * @param request NativeWebRequest 请求对象
     * @param task Callable 任务
     * @param t 异常对象
     * @param <T> 返回值类型
     * @return 错误处理结果（可选）
     */
    @Override
    public <T> Object handleError(NativeWebRequest request, Callable<T> task, Throwable t) {
        log.error("[Callable拦截器] handleError - 异步处理异常: URI={}, 异常={}",
                request.getDescription(false), t.getMessage(), t);
        return RESULT_NONE;
    }

    /**
     * 请求完成后
     *
     * <p><b>@教学</b>
     * <p>无论成功、失败、超时，最终都会调用此方法。用于资源清理。
     *
     * @param request NativeWebRequest 请求对象
     * @param task Callable 任务
     * @param <T> 返回值类型
     */
    @Override
    public <T> void afterCompletion(NativeWebRequest request, Callable<T> task) {
        log.debug("[Callable拦截器] afterCompletion - 请求完成: URI={}",
                request.getDescription(false));
    }
}
