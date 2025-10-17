package nan.tech.common.utils;

import java.util.concurrent.TimeUnit;

/**
 * 线程相关的实用工具方法集合，支持所有 lab 模块。
 *
 * <p><b>@教学</b>
 * <ul>
 *   <li>线程工具类的设计目的：提供跨 lab 共用的线程操作工具方法</li>
 *   <li>该类展示了如何安全地获取线程相关的系统信息</li>
 * </ul>
 *
 * <p><b>@陷阱</b>
 * <ul>
 *   <li>不要假设 availableProcessors() 在 Docker 中的行为与本机相同</li>
 *   <li>容器环境中可能需要显式指定 CPU 限制</li>
 * </ul>
 */
public final class ThreadUtil {

    private ThreadUtil() {
        // 工具类，不应被实例化
    }

    /**
     * 获取当前系统的可用处理器数量。
     *
     * <p><b>@教学</b>
     * <ul>
     *   <li>获取当前系统的可用处理器数量</li>
     *   <li>这是配置线程池 corePoolSize 的重要参考</li>
     * </ul>
     *
     * <p><b>@陷阱</b>
     * <ul>
     *   <li>返回值受 JVM 参数 -XX:ActiveProcessorCount 影响</li>
     *   <li>在容器环境中可能与实际可用 CPU 不一致</li>
     * </ul>
     *
     * @return 处理器数量，通常用于计算线程池大小
     */
    public static int getProcessorCount() {
        return Runtime.getRuntime().availableProcessors();
    }

    /**
     * 安全地等待，忽略 InterruptedException。
     *
     * <p><b>@教学</b>
     * <ul>
     *   <li>演示如何正确处理线程中断异常</li>
     *   <li>保存中断状态并在最后恢复</li>
     * </ul>
     *
     * @param millis 等待时间（毫秒）
     */
    public static void safeSleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            // 恢复中断状态
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 安全地等待，可配置时间单位
     *
     * @param duration 等待时间
     * @param unit 时间单位
     */
    public static void safeSleep(long duration, TimeUnit unit) {
        safeSleep(unit.toMillis(duration));
    }

    /**
     * 获取当前线程的调试信息。
     *
     * <p><b>@教学</b>
     * <ul>
     *   <li>获取当前线程的名称和 ID</li>
     *   <li>用于调试和日志记录</li>
     * </ul>
     *
     * @return 线程 ID:线程名称 格式的字符串
     */
    public static String getCurrentThreadInfo() {
        Thread current = Thread.currentThread();
        return String.format("%d:%s", current.getId(), current.getName());
    }

    /**
     * 计算最佳线程池大小（针对 IO 密集型任务）。
     *
     * <p><b>@教学</b>
     * <p>使用公式:
     * <pre>
     * N_threads = N_cpu * U_cpu * (1 + W/C)
     *
     * 其中:
     *   N_cpu = 处理器数量
     *   U_cpu = 目标 CPU 利用率 (0-1)
     *   W/C = 等待时间 / 计算时间
     * </pre>
     *
     * <p><b>@参考</b>
     * <ul>
     *   <li>Java Concurrency in Practice, Brian Goetz et al., Chapter 8</li>
     *   <li>公式来自对 IO 密集型任务的性能分析</li>
     * </ul>
     *
     * @param cpuUtilization 目标 CPU 利用率 (0.0 - 1.0)
     * @param waitComputeRatio 等待时间与计算时间的比率
     * @return 建议的线程池大小
     */
    public static int calculateOptimalThreadPoolSize(double cpuUtilization, double waitComputeRatio) {
        int processorCount = getProcessorCount();
        return (int) (processorCount * cpuUtilization * (1 + waitComputeRatio));
    }

    /**
     * 默认的 IO 密集型线程池大小计算
     * 假设 CPU 利用率 70%, 等待:计算 = 10:1
     *
     * @return 建议的线程池大小
     */
    public static int calculateDefaultIoIntensivePoolSize() {
        return calculateOptimalThreadPoolSize(0.7, 10.0);
    }

    /**
     * 默认的 CPU 密集型线程池大小计算
     * 假设 CPU 利用率 100%, 等待:计算 = 0:1
     *
     * @return 建议的线程池大小
     */
    public static int calculateDefaultCpuIntensivePoolSize() {
        return calculateOptimalThreadPoolSize(1.0, 0.0);
    }
}
