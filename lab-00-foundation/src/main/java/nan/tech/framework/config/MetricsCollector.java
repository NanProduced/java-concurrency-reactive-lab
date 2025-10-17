package nan.tech.framework.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 指标收集器的简单实现。
 *
 * <p><b>@教学</b>
 * <ul>
 *   <li>演示如何使用 Micrometer 收集自定义指标</li>
 *   <li>支持计数器（Counter）和计时器（Timer）两种指标类型</li>
 *   <li>与 Prometheus 可视化无缝集成</li>
 * </ul>
 *
 * <p><b>参考</b>
 * <ul>
 *   <li>Micrometer: 应用监控指标的统一抽象层</li>
 *   <li>Prometheus: 指标数据的采集和存储</li>
 * </ul>
 */
public class MetricsCollector {
    private static final Logger log = LoggerFactory.getLogger(MetricsCollector.class);
    private final MeterRegistry registry;

    /**
     * 构造指标收集器。
     *
     * @param registry Micrometer 指标注册表
     */
    public MetricsCollector(MeterRegistry registry) {
        this.registry = registry;
        initializeMetrics();
    }

    /**
     * 初始化指标收集器。
     */
    private void initializeMetrics() {
        log.info("指标收集器已初始化");
    }

    /**
     * 记录事件发生次数（计数器）。
     *
     * <p><b>示例</b>
     * <pre>
     * metricsCollector.incrementCounter("user.login.success");
     * </pre>
     *
     * @param name 指标名称
     */
    public void incrementCounter(String name) {
        registry.counter(name).increment();
    }

    /**
     * 记录数值（计时器）。
     *
     * @param name 指标名称
     * @param value 数值
     */
    public void recordValue(String name, double value) {
        registry.timer(name).record(() -> {});
    }
}
