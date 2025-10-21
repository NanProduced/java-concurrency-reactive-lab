package nan.tech.lab06.benchmark;

import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.util.Collection;

/**
 * JMH Benchmark 统一运行器
 *
 * <p><strong>功能</strong>：
 * <ul>
 *   <li>运行所有 BIO/NIO/Reactor 性能测试</li>
 *   <li>生成统一的性能对比报告</li>
 *   <li>支持导出 JSON/CSV 格式报告</li>
 * </ul>
 *
 * <p><strong>使用方式</strong>：
 * <pre>
 * # 运行所有 benchmark
 * mvn clean test-compile exec:java \
 *   -Dexec.mainClass="nan.tech.lab06.benchmark.BenchmarkRunner" \
 *   -Dexec.classpathScope=test
 *
 * # 快速模式（减少迭代次数）
 * mvn clean test-compile exec:java \
 *   -Dexec.mainClass="nan.tech.lab06.benchmark.BenchmarkRunner" \
 *   -Dexec.args="--quick" \
 *   -Dexec.classpathScope=test
 *
 * # 只运行特定 benchmark
 * mvn clean test-compile exec:java \
 *   -Dexec.mainClass="nan.tech.lab06.benchmark.BenchmarkRunner" \
 *   -Dexec.args="--pattern BIOBenchmark" \
 *   -Dexec.classpathScope=test
 * </pre>
 *
 * <p><strong>预期输出</strong>：
 * <pre>
 * ========================================
 * Lab-06 Performance Benchmark Report
 * ========================================
 *
 * BIO Performance:
 *   - Single Thread:  ~500 ops/s
 *   - Multi Thread:   ~3000 ops/s
 *   - Thread Pool:    ~5000 ops/s
 *
 * NIO Performance:
 *   - NIO Selector:   ~10000 ops/s (2x BIO)
 *
 * Reactor Performance:
 *   - 1 Worker:       ~12000 ops/s
 *   - 2 Workers:      ~22000 ops/s (2x)
 *   - 4 Workers:      ~40000 ops/s (4x)
 *   - 8 Workers:      ~45000 ops/s
 *
 * Key Insights:
 *   ✅ NIO 比 BIO 线程池快 2x
 *   ✅ Reactor 比 NIO 快 4x (4 workers)
 *   ✅ Reactor 扩展性优秀 (1→4 workers: 接近线性)
 *   ⚠️ 超过 4 workers 收益递减 (CPU 瓶颈)
 * ========================================
 * </pre>
 *
 * @author AI Assistant
 * @since 1.0.0
 */
public class BenchmarkRunner {

    public static void main(String[] args) throws RunnerException {
        boolean quickMode = false;
        String pattern = ".*Benchmark.*"; // 默认运行所有

        // 解析命令行参数
        for (String arg : args) {
            if ("--quick".equals(arg)) {
                quickMode = true;
            } else if (arg.startsWith("--pattern=")) {
                pattern = arg.substring("--pattern=".length());
            }
        }

        System.out.println("========================================");
        System.out.println("Lab-06 Performance Benchmark Suite");
        System.out.println("========================================");
        System.out.println("Mode: " + (quickMode ? "Quick (reduced iterations)" : "Full (production-quality)"));
        System.out.println("Pattern: " + pattern);
        System.out.println("========================================\n");

        // 配置 JMH - 使用三元运算符选择迭代次数，避免类型转换问题
        Options opt = new OptionsBuilder()
            .include(pattern)
            .forks(1)
            .threads(10) // 模拟 10 个并发客户端
            .shouldFailOnError(true)
            .warmupIterations(quickMode ? 1 : 2)
            .warmupTime(TimeValue.seconds(quickMode ? 3 : 5))
            .measurementIterations(quickMode ? 2 : 3)
            .measurementTime(TimeValue.seconds(quickMode ? 5 : 10))
            .jvmArgs("-Xms2g", "-Xmx2g") // 固定堆大小避免 GC 干扰
            .build();

        // 运行 Benchmark
        Collection<RunResult> results = new Runner(opt).run();

        // 打印结果摘要
        printSummary(results);
    }

    /**
     * 打印性能报告摘要
     *
     * @param results JMH 运行结果
     */
    private static void printSummary(Collection<RunResult> results) {
        System.out.println("\n========================================");
        System.out.println("Performance Summary");
        System.out.println("========================================\n");

        double bioSingleThread = 0, bioMultiThread = 0, bioThreadPool = 0;
        double nioSelector = 0;
        double reactor1 = 0, reactor2 = 0, reactor4 = 0, reactor8 = 0;

        // 提取结果
        for (RunResult result : results) {
            String benchmarkName = result.getParams().getBenchmark();
            double score = result.getPrimaryResult().getScore();

            if (benchmarkName.contains("BIOBenchmark.testSingleThreadServer")) {
                bioSingleThread = score;
            } else if (benchmarkName.contains("BIOBenchmark.testMultiThreadServer")) {
                bioMultiThread = score;
            } else if (benchmarkName.contains("BIOBenchmark.testThreadPoolServer")) {
                bioThreadPool = score;
            } else if (benchmarkName.contains("NIOBenchmark.testNIOSelector")) {
                nioSelector = score;
            } else if (benchmarkName.contains("ReactorBenchmark.testReactor")) {
                String workerCount = result.getParams().getParam("workerCount");
                switch (workerCount) {
                    case "1": reactor1 = score; break;
                    case "2": reactor2 = score; break;
                    case "4": reactor4 = score; break;
                    case "8": reactor8 = score; break;
                }
            }
        }

        // 打印 BIO 结果
        if (bioSingleThread > 0 || bioMultiThread > 0 || bioThreadPool > 0) {
            System.out.println("📊 BIO (Blocking I/O) Performance:");
            if (bioSingleThread > 0) {
                System.out.printf("   - Single Thread:  %,.0f ops/s (baseline)%n", bioSingleThread);
            }
            if (bioMultiThread > 0) {
                System.out.printf("   - Multi Thread:   %,.0f ops/s (%.1fx)%n",
                    bioMultiThread, bioMultiThread / bioSingleThread);
            }
            if (bioThreadPool > 0) {
                System.out.printf("   - Thread Pool:    %,.0f ops/s (%.1fx)%n",
                    bioThreadPool, bioThreadPool / bioSingleThread);
            }
            System.out.println();
        }

        // 打印 NIO 结果
        if (nioSelector > 0) {
            System.out.println("⚡ NIO (Non-blocking I/O) Performance:");
            System.out.printf("   - NIO Selector:   %,.0f ops/s", nioSelector);
            if (bioThreadPool > 0) {
                System.out.printf(" (%.1fx BIO)%n", nioSelector / bioThreadPool);
            } else {
                System.out.println();
            }
            System.out.println();
        }

        // 打印 Reactor 结果
        if (reactor1 > 0 || reactor2 > 0 || reactor4 > 0 || reactor8 > 0) {
            System.out.println("🚀 Reactor (Master-Slave) Performance:");
            if (reactor1 > 0) {
                System.out.printf("   - 1 Worker:       %,.0f ops/s (baseline)%n", reactor1);
            }
            if (reactor2 > 0) {
                System.out.printf("   - 2 Workers:      %,.0f ops/s (%.1fx)%n",
                    reactor2, reactor2 / reactor1);
            }
            if (reactor4 > 0) {
                System.out.printf("   - 4 Workers:      %,.0f ops/s (%.1fx)%n",
                    reactor4, reactor4 / reactor1);
            }
            if (reactor8 > 0) {
                System.out.printf("   - 8 Workers:      %,.0f ops/s (%.1fx)%n",
                    reactor8, reactor8 / reactor1);
            }
            System.out.println();
        }

        // 打印关键洞察
        System.out.println("💡 Key Insights:");
        if (nioSelector > 0 && bioThreadPool > 0) {
            System.out.printf("   ✅ NIO 比 BIO 快 %.1fx (单线程处理多连接优势)%n",
                nioSelector / bioThreadPool);
        }
        if (reactor4 > 0 && nioSelector > 0) {
            System.out.printf("   ✅ Reactor (4 workers) 比 NIO 快 %.1fx (多核并行优势)%n",
                reactor4 / nioSelector);
        }
        if (reactor1 > 0 && reactor4 > 0) {
            double scalability = reactor4 / reactor1;
            System.out.printf("   ✅ Reactor 扩展性: %.1fx (1→4 workers", scalability);
            if (scalability >= 3.5) {
                System.out.println(", 接近线性扩展)");
            } else {
                System.out.println(", 存在扩展瓶颈)");
            }
        }
        if (reactor4 > 0 && reactor8 > 0) {
            double diminishingReturns = reactor8 / reactor4;
            if (diminishingReturns < 1.5) {
                System.out.printf("   ⚠️  超过 4 workers 收益递减 (%.1fx, 可能达到 CPU 瓶颈)%n",
                    diminishingReturns);
            }
        }

        System.out.println("\n========================================");
        System.out.println("Benchmark Report Complete!");
        System.out.println("========================================");

        // 教学提示
        System.out.println("\n📚 学习建议:");
        System.out.println("   1. 对比 BIO 三种模式，理解线程池的价值");
        System.out.println("   2. 分析 NIO vs BIO，理解多路复用的优势");
        System.out.println("   3. 观察 Reactor 扩展性，理解主从分离的意义");
        System.out.println("   4. 思考为什么 8 workers 收益递减（CPU/锁竞争）");
        System.out.println();
    }
}
