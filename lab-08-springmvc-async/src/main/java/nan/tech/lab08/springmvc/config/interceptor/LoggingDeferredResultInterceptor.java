package nan.tech.lab08.springmvc.config.interceptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.context.request.async.DeferredResultProcessingInterceptor;

/**
 * DeferredResult 异步请求处理拦截器
 *
 * <p><b>@教学</b>
 * <ul>
 *   <li>监控 DeferredResult 异步请求的完整生命周期</li>
 *   <li>记录请求开始、预处理、后处理、超时、错误、完成等事件</li>
 *   <li>用于调试和性能分析</li>
 * </ul>
 *
 * <p><b>@生命周期</b>
 * <pre>
 * 1. beforeConcurrentHandling: Controller 返回 DeferredResult 后，异步处理开始前
 * 2. preProcess: 异步线程设置结果前（DeferredResult.setResult 调用前）
 * 3. postProcess: 结果设置完成后，返回响应前
 * 4. handleTimeout: 异步处理超时时
 * 5. handleError: 异步处理异常时
 * 6. afterCompletion: 请求完成后（无论成功、失败、超时）
 * </pre>
 *
 * <p><b>@对比</b>
 * <p>与 CallableProcessingInterceptor 的区别：
 * <ul>
 *   <li>Callable: Spring 自动管理异步执行，拦截器跟踪执行过程</li>
 *   <li>DeferredResult: 开发者手动管理异步执行，拦截器跟踪结果设置</li>
 * </ul>
 *
 * @author Claude Code
 * @since 2025-10-20
 */
public class LoggingDeferredResultInterceptor implements DeferredResultProcessingInterceptor {

    private static final Logger log = LoggerFactory.getLogger(LoggingDeferredResultInterceptor.class);

    /**
     * 异步处理开始前
     *
     * <p><b>@教学</b>
     * <p>此时 Controller 已返回 DeferredResult，Servlet 容器线程即将被释放。
     *
     * @param request NativeWebRequest 请求对象
     * @param deferredResult DeferredResult 对象
     * @param <T> 返回值类型
     */
    @Override
    public <T> void beforeConcurrentHandling(NativeWebRequest request, DeferredResult<T> deferredResult) {
        log.debug("[DeferredResult拦截器] beforeConcurrentHandling - 异步处理即将开始: URI={}",
                request.getDescription(false));
    }

    /**
     * 结果设置前
     *
     * <p><b>@教学</b>
     * <p>当调用 deferredResult.setResult() 或 deferredResult.setErrorResult() 时触发。
     *
     * @param request NativeWebRequest 请求对象
     * @param deferredResult DeferredResult 对象
     * @param <T> 返回值类型
     */
    @Override
    public <T> void preProcess(NativeWebRequest request, DeferredResult<T> deferredResult) {
        log.debug("[DeferredResult拦截器] preProcess - 准备设置结果: 线程={}",
                Thread.currentThread().getName());
    }

    /**
     * 结果设置完成后
     *
     * <p><b>@教学</b>
     * <p>此时结果已设置，即将重新分派请求到 Servlet 容器。
     *
     * @param request NativeWebRequest 请求对象
     * @param deferredResult DeferredResult 对象
     * @param concurrentResult 设置的结果
     * @param <T> 返回值类型
     */
    @Override
    public <T> void postProcess(NativeWebRequest request, DeferredResult<T> deferredResult,
                                Object concurrentResult) {
        log.debug("[DeferredResult拦截器] postProcess - 结果设置完成: 结果={}", concurrentResult);
    }

    /**
     * 处理超时
     *
     * <p><b>@教学</b>
     * <p>当异步处理超过 DeferredResult 设置的超时时间时触发。
     *
     * @param request NativeWebRequest 请求对象
     * @param deferredResult DeferredResult 对象
     * @param <T> 返回值类型
     * @return true 继续调用其他拦截器，false 停止拦截器链
     */
    @Override
    public <T> boolean handleTimeout(NativeWebRequest request, DeferredResult<T> deferredResult) {
        log.warn("[DeferredResult拦截器] handleTimeout - 异步处理超时: URI={}",
                request.getDescription(false));
        return true; // 返回 true 继续调用其他拦截器
    }

    /**
     * 处理错误
     *
     * <p><b>@教学</b>
     * <p>当调用 deferredResult.setErrorResult() 时触发。
     *
     * @param request NativeWebRequest 请求对象
     * @param deferredResult DeferredResult 对象
     * @param t 异常对象
     * @param <T> 返回值类型
     * @return true 继续调用其他拦截器，false 停止拦截器链
     */
    @Override
    public <T> boolean handleError(NativeWebRequest request, DeferredResult<T> deferredResult, Throwable t) {
        log.error("[DeferredResult拦截器] handleError - 异步处理异常: URI={}, 异常={}",
                request.getDescription(false), t.getMessage(), t);
        return true;
    }

    /**
     * 请求完成后
     *
     * <p><b>@教学</b>
     * <p>无论成功、失败、超时，最终都会调用此方法。用于资源清理。
     *
     * @param request NativeWebRequest 请求对象
     * @param deferredResult DeferredResult 对象
     * @param <T> 返回值类型
     */
    @Override
    public <T> void afterCompletion(NativeWebRequest request, DeferredResult<T> deferredResult) {
        log.debug("[DeferredResult拦截器] afterCompletion - 请求完成: URI={}",
                request.getDescription(false));
    }
}
