package nan.tech.common.test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 所有测试类的基类，包含通用的测试工具和日志。
 *
 * <p><b>@教学</b>
 * <ul>
 *   <li>所有测试类的基类，提供通用的日志、断言工具</li>
 *   <li>提供性能测试的计时工具</li>
 *   <li>提供内存和线程信息的诊断工具</li>
 * </ul>
 */
public abstract class BaseTest {

    protected static final Logger log = LoggerFactory.getLogger(BaseTest.class);

    /**
     * 性能计时辅助类。
     *
     * <p><b>@教学</b>
     * <ul>
     *   <li>简单的性能计时辅助类</li>
     *   <li>用于测试中快速测量代码执行时间</li>
     * </ul>
     */
    protected static class Timer {
        private final long startTime;
        private final String name;

        public Timer(String name) {
            this.name = name;
            this.startTime = System.nanoTime();
        }

        /**
         * 停止计时并打印结果
         * @return 耗时（毫秒）
         */
        public long stopAndPrint() {
            long durationNanos = System.nanoTime() - startTime;
            long durationMillis = durationNanos / 1_000_000;
            log.info("[{}] 耗时: {} ms", name, durationMillis);
            return durationMillis;
        }

        /**
         * 停止计时但不打印
         * @return 耗时（毫秒）
         */
        public long stop() {
            long durationNanos = System.nanoTime() - startTime;
            return durationNanos / 1_000_000;
        }
    }

    /**
     * 创建计时器
     * @param name 计时器名称
     * @return Timer 实例
     */
    protected Timer startTimer(String name) {
        return new Timer(name);
    }

    /**
     * 打印内存信息
     */
    protected void printMemoryInfo() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / 1024 / 1024;
        long totalMemory = runtime.totalMemory() / 1024 / 1024;
        long freeMemory = runtime.freeMemory() / 1024 / 1024;
        long usedMemory = totalMemory - freeMemory;

        log.info("[内存统计] 最大: {} MB, 总数: {} MB, 已用: {} MB, 可用: {} MB",
                 maxMemory, totalMemory, usedMemory, freeMemory);
    }

    /**
     * 打印线程信息
     */
    protected void printThreadInfo() {
        ThreadGroup rootGroup = Thread.currentThread().getThreadGroup();
        int threadCount = rootGroup.activeCount();
        Thread[] threads = new Thread[threadCount];
        rootGroup.enumerate(threads);

        log.info("[线程统计] 活跃线程数: {}", threadCount);
    }
}
