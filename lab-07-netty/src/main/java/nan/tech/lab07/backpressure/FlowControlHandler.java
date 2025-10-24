package nan.tech.lab07.backpressure;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 流量控制 Handler - 可复用的背压与限流组件
 *
 * <p><b>核心功能</b>：
 * <ul>
 *   <li><b>并发控制</b>：限制同时处理的请求数（基于 Semaphore）</li>
 *   <li><b>QPS 限流</b>：限制每秒请求数（基于滑动窗口）</li>
 *   <li><b>背压集成</b>：与 Netty 水位线机制结合</li>
 *   <li><b>统计指标</b>：接受、拒绝、待处理请求数</li>
 * </ul>
 *
 * <p><b>架构设计</b>：
 * <pre>
 *                          FlowControlHandler
 * ┌────────────────────────────────────────────────────────┐
 * │                                                          │
 * │  1. 检查并发限制 (Semaphore)                            │
 * │       │                                                  │
 * │       ▼                                                  │
 * │  2. 检查 QPS 限制 (Token Bucket)                        │
 * │       │                                                  │
 * │       ▼                                                  │
 * │  3. 检查背压状态 (isWritable)                           │
 * │       │                                                  │
 * │       ▼                                                  │
 * │  4. 通过检查 → 传播事件到下一个 Handler                 │
 * │      │                                                   │
 * │      └─ 未通过 → 拒绝请求（发送 429 响应或丢弃）        │
 * │                                                          │
 * └────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <p><b>使用示例</b>：
 * <pre>
 * // 在 Pipeline 中添加流量控制
 * pipeline.addLast("flowControl", new FlowControlHandler(
 *     100,    // 最大并发请求数
 *     1000,   // QPS 限制
 *     FlowControlHandler.RejectStrategy.RESPONSE  // 拒绝策略
 * ));
 * </pre>
 *
 * @author nan.tech
 * @since Lab-07
 */
public class FlowControlHandler extends ChannelDuplexHandler {

    private static final Logger logger = LoggerFactory.getLogger(FlowControlHandler.class);

    /** 拒绝策略 */
    public enum RejectStrategy {
        /** 发送 429 响应 */
        RESPONSE,
        /** 丢弃请求（silent drop） */
        DROP,
        /** 等待（阻塞，不推荐） */
        WAIT
    }

    /** 最大并发请求数 */
    private final int maxConcurrency;

    /** QPS 限制 */
    private final int maxQps;

    /** 拒绝策略 */
    private final RejectStrategy rejectStrategy;

    /** 并发控制信号量 */
    private final Semaphore concurrencySemaphore;

    /** 统计指标 */
    private final AtomicLong acceptedCount = new AtomicLong(0);
    private final AtomicLong rejectedCount = new AtomicLong(0);
    private final AtomicLong currentPending = new AtomicLong(0);

    /** QPS 控制：滑动窗口 */
    private final AtomicLong[] qpsWindow = new AtomicLong[10]; // 10 个 100ms 窗口
    private volatile long lastWindowTimestamp = System.currentTimeMillis();

    /**
     * 构造函数
     *
     * @param maxConcurrency 最大并发请求数
     * @param maxQps 最大 QPS（0 表示不限制）
     * @param rejectStrategy 拒绝策略
     */
    public FlowControlHandler(int maxConcurrency, int maxQps, RejectStrategy rejectStrategy) {
        this.maxConcurrency = maxConcurrency;
        this.maxQps = maxQps;
        this.rejectStrategy = rejectStrategy;
        this.concurrencySemaphore = new Semaphore(maxConcurrency);

        // 初始化 QPS 窗口
        for (int i = 0; i < qpsWindow.length; i++) {
            qpsWindow[i] = new AtomicLong(0);
        }

        logger.info("流量控制 Handler 已初始化：maxConcurrency={}, maxQps={}, rejectStrategy={}",
            maxConcurrency, maxQps, rejectStrategy);
    }

    /**
     * 入站事件处理（读取请求）
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        // 1. 检查并发限制
        if (!concurrencySemaphore.tryAcquire()) {
            handleRejection(ctx, msg, "并发限制");
            return;
        }

        // 2. 检查 QPS 限制
        if (maxQps > 0 && !checkQpsLimit()) {
            concurrencySemaphore.release();
            handleRejection(ctx, msg, "QPS 限制");
            return;
        }

        // 3. 检查背压状态
        if (!ctx.channel().isWritable()) {
            concurrencySemaphore.release();
            handleRejection(ctx, msg, "背压触发");
            return;
        }

        // ✅ 通过所有检查，接受请求
        acceptedCount.incrementAndGet();
        currentPending.incrementAndGet();

        // 传播到下一个 Handler
        ctx.fireChannelRead(msg);
    }

    /**
     * 出站事件处理（写响应）
     */
    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        // 响应发送完成后，释放并发槽位
        promise.addListener(future -> {
            concurrencySemaphore.release();
            currentPending.decrementAndGet();
        });

        ctx.write(msg, promise);
    }

    /**
     * 处理拒绝请求
     *
     * @param ctx ChannelHandlerContext
     * @param msg 被拒绝的消息
     * @param reason 拒绝原因
     */
    private void handleRejection(ChannelHandlerContext ctx, Object msg, String reason) {
        rejectedCount.incrementAndGet();

        long rejected = rejectedCount.get();
        if (rejected % 100 == 0) {
            double rejectRate = rejected * 100.0 / (acceptedCount.get() + rejected);
            logger.warn("【流量控制】拒绝请求：原因={}, 总拒绝数={}, 当前待处理={}, 拒绝率={}%",
                reason, rejected, currentPending.get(),
                String.format("%.2f", rejectRate));
        }

        switch (rejectStrategy) {
            case RESPONSE:
                // 发送 429 Too Many Requests 响应
                String response = String.format("429 Too Many Requests - Rejected by %s\n", reason);
                ByteBuf buffer = Unpooled.copiedBuffer(response, CharsetUtil.UTF_8);
                ctx.writeAndFlush(buffer);
                break;

            case DROP:
                // 静默丢弃
                ReferenceCountUtil.release(msg);
                break;

            case WAIT:
                // 阻塞等待（不推荐，可能导致 EventLoop 阻塞）
                try {
                    if (concurrencySemaphore.tryAcquire(1, TimeUnit.SECONDS)) {
                        acceptedCount.incrementAndGet();
                        currentPending.incrementAndGet();
                        ctx.fireChannelRead(msg);
                    } else {
                        // 等待超时，仍然拒绝
                        ReferenceCountUtil.release(msg);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    ReferenceCountUtil.release(msg);
                }
                break;

            default:
                ReferenceCountUtil.release(msg);
        }
    }

    /**
     * 检查 QPS 限制（基于滑动窗口）
     *
     * <p><b>实现原理</b>：
     * <ul>
     *   <li>将 1 秒分为 10 个 100ms 窗口</li>
     *   <li>统计最近 1 秒内的请求数</li>
     *   <li>如果超过 maxQps，拒绝请求</li>
     * </ul>
     *
     * @return true 通过限制，false 超过限制
     */
    private boolean checkQpsLimit() {
        long now = System.currentTimeMillis();
        int currentWindowIndex = (int) ((now / 100) % qpsWindow.length);

        // 重置过期窗口
        if (now - lastWindowTimestamp >= 100) {
            qpsWindow[currentWindowIndex].set(0);
            lastWindowTimestamp = now;
        }

        // 增加当前窗口计数
        qpsWindow[currentWindowIndex].incrementAndGet();

        // 计算最近 1 秒的总请求数
        long totalRequests = 0;
        for (AtomicLong window : qpsWindow) {
            totalRequests += window.get();
        }

        return totalRequests <= maxQps;
    }

    /**
     * 监听可写状态变化
     */
    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) {
        if (ctx.channel().isWritable()) {
            logger.info("【流量控制】背压释放，恢复接受请求");
        } else {
            logger.warn("【流量控制】背压触发，暂停接受请求");
        }
        ctx.fireChannelWritabilityChanged();
    }

    /**
     * 连接关闭时打印统计信息
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        long total = acceptedCount.get() + rejectedCount.get();
        double rejectRate = rejectedCount.get() * 100.0 / total;
        logger.info("【流量控制】连接关闭 - 接受={}, 拒绝={}, 总计={}, 拒绝率={}%",
            acceptedCount.get(), rejectedCount.get(), total,
            String.format("%.2f", rejectRate));

        ctx.fireChannelInactive();
    }

    /**
     * 获取统计指标
     *
     * @return 统计信息字符串
     */
    public String getMetrics() {
        long total = acceptedCount.get() + rejectedCount.get();
        return String.format(
            "FlowControl Metrics - Accepted: %d, Rejected: %d, Pending: %d, Reject Rate: %.2f%%",
            acceptedCount.get(), rejectedCount.get(), currentPending.get(),
            (rejectedCount.get() * 100.0 / Math.max(total, 1))
        );
    }

    /**
     * 重置统计指标
     */
    public void resetMetrics() {
        acceptedCount.set(0);
        rejectedCount.set(0);
        currentPending.set(0);
        for (AtomicLong window : qpsWindow) {
            window.set(0);
        }
        logger.info("流量控制统计指标已重置");
    }
}
