package nan.tech.lab08.springmvc.config;

import nan.tech.lab08.springmvc.config.interceptor.LoggingCallableInterceptor;
import nan.tech.lab08.springmvc.config.interceptor.LoggingDeferredResultInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC 异步支持配置
 *
 * <p><b>@教学</b>
 * <ul>
 *   <li>配置全局异步超时时间：30 秒</li>
 *   <li>指定异步任务执行器：asyncTaskExecutor</li>
 *   <li>注册异步拦截器：监控和日志记录</li>
 * </ul>
 *
 * <p><b>@配置层次</b>
 * <pre>
 * 超时配置优先级（从高到低）:
 *   1. DeferredResult 构造器指定的超时
 *   2. WebAsyncTask 指定的超时
 *   3. 全局配置的默认超时（此处配置）
 * </pre>
 *
 * <p><b>@拦截器</b>
 * <ul>
 *   <li>LoggingCallableInterceptor: 记录 Callable 异步请求生命周期</li>
 *   <li>LoggingDeferredResultInterceptor: 记录 DeferredResult 异步请求生命周期</li>
 * </ul>
 *
 * @author Claude Code
 * @since 2025-10-20
 */
@Configuration
public class AsyncWebConfiguration implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AsyncWebConfiguration.class);

    @Autowired
    @Qualifier("asyncTaskExecutor")
    private ThreadPoolTaskExecutor asyncTaskExecutor;

    /**
     * 配置异步支持
     *
     * <p><b>@教学</b>
     * <ul>
     *   <li>setDefaultTimeout: 设置全局默认超时 30 秒</li>
     *   <li>setTaskExecutor: 指定 Callable/WebAsyncTask 使用的执行器</li>
     *   <li>registerCallableInterceptors: 注册 Callable 拦截器</li>
     *   <li>registerDeferredResultInterceptors: 注册 DeferredResult 拦截器</li>
     * </ul>
     *
     * <p><b>@注意</b>
     * <p>DeferredResult 不使用此执行器，它需要自己提交任务到线程池。
     *
     * @param configurer 异步支持配置器
     */
    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        log.info("配置 Spring MVC 异步支持");

        // 全局默认超时 30 秒
        configurer.setDefaultTimeout(30000);

        // 指定任务执行器
        configurer.setTaskExecutor(asyncTaskExecutor);

        // 注册 Callable 拦截器
        configurer.registerCallableInterceptors(new LoggingCallableInterceptor());

        // 注册 DeferredResult 拦截器
        configurer.registerDeferredResultInterceptors(new LoggingDeferredResultInterceptor());

        log.info("Spring MVC 异步支持配置完成: defaultTimeout=30000ms, executor={}",
                asyncTaskExecutor.getThreadNamePrefix());
    }
}
