package nan.tech.lab09;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 性能基线收集器
 *
 * <h2>功能</h2>
 * <ul>
 *     <li>收集测试运行期间的性能指标</li>
 *     <li>计算基线数据（平均值、最小值、最大值、p95/p99）</li>
 *     <li>生成性能报告（CSV 和 JSON 格式）</li>
 * </ul>
 *
 * <h2>使用方式</h2>
 * <pre>
 * PerformanceBaselineCollector collector = PerformanceBaselineCollector.getInstance();
 *
 * // 记录一次操作
 * collector.record("flux-simple", 45);  // 45ms
 * collector.record("flux-simple", 48);  // 48ms
 * collector.record("flux-simple", 52);  // 52ms
 *
 * // 生成报告
 * collector.generateReport();  // 输出到 target/performance-baseline.csv
 * </pre>
 *
 * @author Claude Code + Lab-09 团队
 * @version 1.0.0
 * @since 2025-10-23
 */
@Slf4j
public class PerformanceBaselineCollector {

    private static final PerformanceBaselineCollector INSTANCE = new PerformanceBaselineCollector();

    private final Map<String, List<Long>> metrics = new ConcurrentHashMap<>();
    private final Date startTime = new Date();

    private PerformanceBaselineCollector() {
        // Private constructor for singleton
    }

    /**
     * 获取单例实例
     */
    public static PerformanceBaselineCollector getInstance() {
        return INSTANCE;
    }

    /**
     * 记录单次性能数据
     *
     * @param testName 测试名称 (如 "flux-simple")
     * @param responseTimeMs 响应时间（毫秒）
     */
    public void record(String testName, long responseTimeMs) {
        metrics.computeIfAbsent(testName, k -> new ArrayList<>())
                .add(responseTimeMs);
    }

    /**
     * 获取统计信息
     *
     * @param testName 测试名称
     * @return 统计数据对象
     */
    public Statistics getStatistics(String testName) {
        List<Long> values = metrics.getOrDefault(testName, new ArrayList<>());
        if (values.isEmpty()) {
            return new Statistics(testName, 0, 0, 0, 0, 0, 0);
        }

        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);

        long sum = values.stream().mapToLong(Long::longValue).sum();
        long avg = sum / values.size();
        long min = sorted.get(0);
        long max = sorted.get(sorted.size() - 1);
        long p95 = sorted.get((int) (sorted.size() * 0.95));
        long p99 = sorted.get((int) (sorted.size() * 0.99));

        return new Statistics(testName, avg, min, max, p95, p99, values.size());
    }

    /**
     * 生成性能报告 (CSV 格式)
     * 文件路径: target/performance-baseline.csv
     */
    public void generateReport() {
        log.info("📊 生成性能基线报告...");

        try {
            Path reportPath = Paths.get("target/performance-baseline.csv");
            Files.createDirectories(reportPath.getParent());

            StringBuilder csv = new StringBuilder();
            csv.append("测试名称,平均响应时间(ms),最小值(ms),最大值(ms),P95(ms),P99(ms),样本数\n");

            List<String> testNames = new ArrayList<>(metrics.keySet());
            Collections.sort(testNames);

            for (String testName : testNames) {
                Statistics stats = getStatistics(testName);
                csv.append(String.format("%s,%d,%d,%d,%d,%d,%d\n",
                        testName,
                        stats.avg,
                        stats.min,
                        stats.max,
                        stats.p95,
                        stats.p99,
                        stats.sampleCount
                ));

                log.info("  {} - 平均: {}ms, 最小: {}ms, 最大: {}ms, P95: {}ms, P99: {}ms",
                        testName, stats.avg, stats.min, stats.max, stats.p95, stats.p99);
            }

            Files.write(reportPath, csv.toString().getBytes());
            log.info("✅ 性能报告已保存: {}", reportPath.toAbsolutePath());
        } catch (IOException e) {
            log.error("❌ 生成性能报告失败", e);
        }
    }

    /**
     * 打印性能总结
     */
    public void printSummary() {
        log.info("\n" +
                "╔════════════════════════════════════════════════════════════╗\n" +
                "║            📊 Lab-09 Phase 1 性能基线总结                  ║\n" +
                "╚════════════════════════════════════════════════════════════╝");

        List<String> testNames = new ArrayList<>(metrics.keySet());
        Collections.sort(testNames);

        log.info("┌─ 测试统计");
        for (String testName : testNames) {
            Statistics stats = getStatistics(testName);
            log.info("│");
            log.info("│  {}", testName);
            log.info("│    平均响应时间: {}ms", stats.avg);
            log.info("│    最小值: {}ms | 最大值: {}ms", stats.min, stats.max);
            log.info("│    P95: {}ms | P99: {}ms", stats.p95, stats.p99);
            log.info("│    样本数: {}", stats.sampleCount);
        }
        log.info("└─ 报告已保存到: target/performance-baseline.csv");

        Date endTime = new Date();
        long duration = endTime.getTime() - startTime.getTime();
        log.info("\n💡 总运行时间: {}ms (~{} 秒)", duration, duration / 1000);
    }

    /**
     * 清空所有数据
     */
    public void reset() {
        metrics.clear();
    }

    /**
     * 性能统计数据类
     */
    @Data
    public static class Statistics {
        private String testName;
        private long avg;
        private long min;
        private long max;
        private long p95;
        private long p99;
        private long sampleCount;

        public Statistics(String testName, long avg, long min, long max, long p95, long p99, long sampleCount) {
            this.testName = testName;
            this.avg = avg;
            this.min = min;
            this.max = max;
            this.p95 = p95;
            this.p99 = p99;
            this.sampleCount = sampleCount;
        }
    }

}
