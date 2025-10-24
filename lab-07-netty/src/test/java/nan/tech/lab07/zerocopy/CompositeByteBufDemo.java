package nan.tech.lab07.zerocopy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CompositeByteBuf 零拷贝演示
 *
 * <p><b>教学目标</b>：
 * <ul>
 *   <li>理解 CompositeByteBuf 的零拷贝原理</li>
 *   <li>掌握 CompositeByteBuf 的使用方法</li>
 *   <li>对比传统拷贝方式的性能差异</li>
 * </ul>
 *
 * <p><b>零拷贝原理</b>：
 * <pre>
 * 传统方式（数据拷贝）：
 * ┌─────────┐   ┌─────────┐
 * │ Header  │   │  Body   │
 * └─────────┘   └─────────┘
 *      │             │
 *      └──── copy ───┤
 *                    ▼
 *            ┌──────────────┐
 *            │  New Buffer  │  ← 需要分配新内存并拷贝数据
 *            └──────────────┘
 *
 * CompositeByteBuf（零拷贝）：
 * ┌─────────┐   ┌─────────┐
 * │ Header  │   │  Body   │
 * └─────────┘   └─────────┘
 *      │             │
 *      └──── ref ────┤
 *                    ▼
 *          ┌──────────────────┐
 *          │ CompositeByteBuf │  ← 仅存储引用，无数据拷贝
 *          │  [Header, Body]  │
 *          └──────────────────┘
 * </pre>
 *
 * <p><b>性能对比</b>：
 * <ul>
 *   <li><b>传统方式</b>：O(n) 时间复杂度（需要拷贝 n 字节）</li>
 *   <li><b>CompositeByteBuf</b>：O(1) 时间复杂度（仅增加引用）</li>
 * </ul>
 *
 * <p><b>运行方式</b>：
 * <pre>
 * mvn exec:java -Dexec.mainClass="nan.tech.lab07.zerocopy.CompositeByteBufDemo"
 * </pre>
 *
 * @author nan.tech
 * @since Lab-07
 */
public class CompositeByteBufDemo {

    private static final Logger logger = LoggerFactory.getLogger(CompositeByteBufDemo.class);

    /**
     * 演示 1：传统方式（需要拷贝数据）
     *
     * <p><b>缺点</b>：
     * <ul>
     *   <li>需要分配新的内存空间</li>
     *   <li>需要拷贝所有数据（CPU 和内存开销）</li>
     *   <li>浪费内存（原始 ByteBuf 和合并后的 ByteBuf 同时存在）</li>
     * </ul>
     */
    public static void demoTraditionalCopy() {
        logger.info("=== 演示 1：传统方式（数据拷贝）===");

        // 模拟 HTTP 请求：Header + Body
        ByteBuf header = Unpooled.copiedBuffer("GET /index.html HTTP/1.1\r\n", CharsetUtil.UTF_8);
        ByteBuf body = Unpooled.copiedBuffer("Host: example.com\r\n\r\n", CharsetUtil.UTF_8);

        logger.info("Header 大小: {} 字节", header.readableBytes());
        logger.info("Body 大小: {} 字节", body.readableBytes());

        // 传统方式：创建新 ByteBuf 并拷贝数据
        long startTime = System.nanoTime();

        ByteBuf merged = Unpooled.buffer(header.readableBytes() + body.readableBytes());
        merged.writeBytes(header); // 拷贝 Header
        merged.writeBytes(body);   // 拷贝 Body

        long duration = System.nanoTime() - startTime;

        logger.info("合并后大小: {} 字节", merged.readableBytes());
        logger.info("耗时: {} 纳秒 ({} 微秒)", duration, duration / 1000);
        logger.info("内容: {}", merged.toString(CharsetUtil.UTF_8));

        // 释放资源
        header.release();
        body.release();
        merged.release();

        logger.info("");
    }

    /**
     * 演示 2：CompositeByteBuf（零拷贝）
     *
     * <p><b>优点</b>：
     * <ul>
     *   <li>不需要分配新内存（仅增加引用）</li>
     *   <li>不需要拷贝数据（O(1) 时间复杂度）</li>
     *   <li>节省内存（共享底层数据）</li>
     * </ul>
     *
     * <p><b>注意事项</b>：
     * <ul>
     *   <li>需要调用 {@code addComponents(true, ...)} 自动更新 writerIndex</li>
     *   <li>CompositeByteBuf 会增加组件的引用计数</li>
     *   <li>读取时可能需要多次系统调用（因为数据不连续）</li>
     * </ul>
     */
    public static void demoCompositeByteBuf() {
        logger.info("=== 演示 2：CompositeByteBuf（零拷贝）===");

        // 模拟 HTTP 请求：Header + Body
        ByteBuf header = Unpooled.copiedBuffer("GET /index.html HTTP/1.1\r\n", CharsetUtil.UTF_8);
        ByteBuf body = Unpooled.copiedBuffer("Host: example.com\r\n\r\n", CharsetUtil.UTF_8);

        logger.info("Header 大小: {} 字节", header.readableBytes());
        logger.info("Body 大小: {} 字节", body.readableBytes());

        // CompositeByteBuf：零拷贝合并
        long startTime = System.nanoTime();

        CompositeByteBuf composite = Unpooled.compositeBuffer();
        composite.addComponents(true, header, body); // true 表示自动更新 writerIndex

        long duration = System.nanoTime() - startTime;

        logger.info("合并后大小: {} 字节", composite.readableBytes());
        logger.info("组件数量: {}", composite.numComponents());
        logger.info("耗时: {} 纳秒 ({} 微秒)", duration, duration / 1000);
        logger.info("内容: {}", composite.toString(CharsetUtil.UTF_8));

        // 释放资源（CompositeByteBuf 会自动管理组件的引用计数）
        composite.release();

        logger.info("");
    }

    /**
     * 演示 3：性能对比（大数据量）
     *
     * <p><b>测试场景</b>：
     * <ul>
     *   <li>合并 10 个 ByteBuf，每个 1MB</li>
     *   <li>对比传统拷贝和 CompositeByteBuf 的性能</li>
     * </ul>
     *
     * <p><b>预期结果</b>：
     * <ul>
     *   <li>CompositeByteBuf 性能提升 100x+（数据量越大，优势越明显）</li>
     * </ul>
     */
    public static void demoBenchmark() {
        logger.info("=== 演示 3：性能对比（10 个 1MB ByteBuf）===");

        int componentCount = 10;
        int componentSize = 1024 * 1024; // 1 MB

        // 创建测试数据
        ByteBuf[] buffers = new ByteBuf[componentCount];
        for (int i = 0; i < componentCount; i++) {
            buffers[i] = Unpooled.buffer(componentSize);
            buffers[i].writeZero(componentSize); // 填充数据
        }

        // 方式 1：传统拷贝
        long startTime1 = System.nanoTime();
        ByteBuf merged1 = Unpooled.buffer(componentCount * componentSize);
        for (ByteBuf buf : buffers) {
            merged1.writeBytes(buf.duplicate()); // duplicate() 避免修改原 ByteBuf
        }
        long duration1 = System.nanoTime() - startTime1;

        logger.info("【传统拷贝】耗时: {} 微秒 ({} 毫秒)",
            duration1 / 1000, duration1 / 1_000_000);
        merged1.release();

        // 方式 2：CompositeByteBuf
        long startTime2 = System.nanoTime();
        CompositeByteBuf composite = Unpooled.compositeBuffer(componentCount);
        for (ByteBuf buf : buffers) {
            composite.addComponent(true, buf.retainedDuplicate());
        }
        long duration2 = System.nanoTime() - startTime2;

        logger.info("【CompositeByteBuf】耗时: {} 微秒 ({} 毫秒)",
            duration2 / 1000, duration2 / 1_000_000);
        logger.info("组件数量: {}", composite.numComponents());
        logger.info("总大小: {} MB", composite.readableBytes() / (1024 * 1024));

        // 性能提升
        double speedup = (double) duration1 / duration2;
        logger.info("性能提升: {}x", String.format("%.2f", speedup));

        // 释放资源
        composite.release();
        for (ByteBuf buf : buffers) {
            buf.release();
        }

        logger.info("");
    }

    /**
     * 演示 4：CompositeByteBuf 的高级用法
     *
     * <p><b>功能演示</b>：
     * <ul>
     *   <li>访问指定位置的组件</li>
     *   <li>遍历所有组件</li>
     *   <li>移除组件</li>
     *   <li>合并为单一 ByteBuf（consolidate）</li>
     * </ul>
     */
    public static void demoAdvancedUsage() {
        logger.info("=== 演示 4：CompositeByteBuf 高级用法 ===");

        // 创建 CompositeByteBuf
        CompositeByteBuf composite = Unpooled.compositeBuffer();

        ByteBuf buf1 = Unpooled.copiedBuffer("Part 1 ", CharsetUtil.UTF_8);
        ByteBuf buf2 = Unpooled.copiedBuffer("Part 2 ", CharsetUtil.UTF_8);
        ByteBuf buf3 = Unpooled.copiedBuffer("Part 3", CharsetUtil.UTF_8);

        composite.addComponents(true, buf1, buf2, buf3);

        logger.info("初始状态：");
        logger.info("  组件数量: {}", composite.numComponents());
        logger.info("  内容: {}", composite.toString(CharsetUtil.UTF_8));

        // 1. 访问指定组件
        logger.info("\n访问第 2 个组件：");
        ByteBuf component2 = composite.component(1); // 索引从 0 开始
        logger.info("  内容: {}", component2.toString(CharsetUtil.UTF_8));

        // 2. 遍历所有组件
        logger.info("\n遍历所有组件：");
        for (int i = 0; i < composite.numComponents(); i++) {
            ByteBuf component = composite.component(i);
            logger.info("  Component {}: {}", i, component.toString(CharsetUtil.UTF_8));
        }

        // 3. 移除组件
        logger.info("\n移除第 2 个组件：");
        composite.removeComponent(1);
        logger.info("  剩余组件数量: {}", composite.numComponents());
        logger.info("  内容: {}", composite.toString(CharsetUtil.UTF_8));

        // 4. 合并为单一 ByteBuf（consolidate）
        logger.info("\n合并为单一 ByteBuf：");
        composite.consolidate(); // 将所有组件合并为一个（涉及数据拷贝）
        logger.info("  组件数量: {}", composite.numComponents());
        logger.info("  内容: {}", composite.toString(CharsetUtil.UTF_8));

        // 释放资源
        composite.release();

        logger.info("");
    }

    /**
     * 主程序入口
     *
     * <p><b>演示流程</b>：
     * <ol>
     *   <li>传统拷贝方式（小数据量）</li>
     *   <li>CompositeByteBuf 方式（小数据量）</li>
     *   <li>性能对比（大数据量）</li>
     *   <li>高级用法</li>
     * </ol>
     *
     * @param args 命令行参数（未使用）
     */
    public static void main(String[] args) {
        logger.info("========================================");
        logger.info("  CompositeByteBuf 零拷贝演示");
        logger.info("========================================\n");

        try {
            // 演示 1：传统拷贝方式
            demoTraditionalCopy();

            // 演示 2：CompositeByteBuf
            demoCompositeByteBuf();

            // 演示 3：性能对比
            demoBenchmark();

            // 演示 4：高级用法
            demoAdvancedUsage();

            logger.info("========================================");
            logger.info("  所有演示完成！");
            logger.info("========================================");

        } catch (Exception e) {
            logger.error("演示过程中发生异常", e);
            System.exit(1);
        }
    }
}
