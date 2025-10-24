package nan.tech.lab10.spec;

import org.reactivestreams.Subscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Reactive Streams规范实现：Subscription
 *
 * <p><b>教学目标</b>：
 * <ul>
 *   <li>理解Subscription接口的语义</li>
 *   <li>掌握request(n)的背压协议实现</li>
 *   <li>理解cancel()的正确处理</li>
 *   <li>熟悉原子操作在并发编程中的应用</li>
 * </ul>
 *
 * <p><b>核心概念</b>：
 * <pre>
 * Subscription 是发布者和订阅者之间的合约：
 *
 * 1. 发布者通过subscribe(Subscriber)时：
 *    subscriber.onSubscribe(subscription) 传递Subscription给订阅者
 *
 * 2. 订阅者可以请求数据：
 *    subscription.request(n)  请求n个元素
 *
 * 3. 发布者根据request(n)发送数据：
 *    subscriber.onNext(element)  发送元素
 *    subscriber.onComplete()     流完成
 *    subscriber.onError(error)   流出错
 *
 * 4. 订阅者可以取消订阅：
 *    subscription.cancel()  取消订阅，发布者停止发送
 * </pre>
 *
 * <p><b>背压协议规则</b>（Reactive Streams 规范 §3）：
 * <ul>
 *   <li>§3.1: Subscriber.onNext() 不能被同时调用</li>
 *   <li>§3.3: Subscription.request(n) 必须按照Subscriber的需求调用</li>
 *   <li>§3.17: 发布者不能发送超过request(n)请求的元素</li>
 * </ul>
 *
 * @author 学习者
 * @since Lab-10 Phase 1
 */
public class RangeSubscription implements org.reactivestreams.Subscription {

    private static final Logger logger = LoggerFactory.getLogger(RangeSubscription.class);

    /**
     * 订阅者引用
     */
    private final Subscriber<? super Integer> subscriber;

    /**
     * 当前要发送的数值范围：[start, end)
     */
    private final int start;
    private final int end;

    /**
     * 已发送的元素个数（原子操作，支持并发访问）
     */
    private final AtomicLong emitted = new AtomicLong(0);

    /**
     * 订阅是否已被取消（一旦cancel，不再发送元素）
     */
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    /**
     * 待发送的元素总数
     */
    private final long totalElements;

    /**
     * 创建一个新的RangeSubscription
     *
     * @param subscriber 订阅者（接收元素的一方）
     * @param start      起始数值（包含）
     * @param count      要发送的元素个数
     */
    public RangeSubscription(Subscriber<? super Integer> subscriber, int start, int count) {
        this.subscriber = subscriber;
        this.start = start;
        this.end = start + count;
        this.totalElements = count;

        logger.info("[RangeSubscription] 创建订阅: range=[{}, {}), totalElements={}",
                start, end, count);
    }

    /**
     * 实现Subscription.request(n)：处理背压请求
     *
     * <p><b>规范要求</b> (Reactive Streams §3.9)：
     * <ul>
     *   <li>如果n <= 0，调用onError(IllegalArgumentException)</li>
     *   <li>发布者可以立即发送元素，也可以延迟发送</li>
     *   <li>request(n)可以被多次调用，次数累加</li>
     *   <li>发布者不能发送超过累计请求的元素</li>
     * </ul>
     *
     * @param n 请求的元素个数（必须 > 0）
     */
    @Override
    public void request(long n) {
        // 规范检查：n必须 > 0
        if (n <= 0) {
            subscriber.onError(new IllegalArgumentException(
                    String.format("request(n) 中 n 必须 > 0，但收到 n=%d", n)));
            return;
        }

        // 如果已经取消，不再发送数据
        if (cancelled.get()) {
            logger.debug("[RangeSubscription] 已取消，忽略request({}) 请求", n);
            return;
        }

        logger.debug("[RangeSubscription] 收到request({}) 请求", n);

        // 异步发送元素（简化版：同步发送）
        // 实际生产环境中应该放在线程池中异步执行，这里为了演示简化为同步
        sendElements(n);
    }

    /**
     * 发送元素给订阅者
     *
     * <p><b>背压协议要点</b>：
     * <ul>
     *   <li>根据request(n)的累计值发送元素</li>
     *   <li>每发送一个元素，减少1个额度</li>
     *   <li>如果流完成或被取消，调用onComplete()或停止</li>
     *   <li>遇到异常立即调用onError()</li>
     * </ul>
     *
     * @param demand 本次请求的元素个数
     */
    private synchronized void sendElements(long demand) {
        long sent = 0;
        long currentEmitted = emitted.get();

        // 不能发送超过总元素数
        long maxToSend = Math.min(demand, totalElements - currentEmitted);

        while (sent < maxToSend) {
            // 如果被取消，立即停止
            if (cancelled.get()) {
                logger.debug("[RangeSubscription] 发送过程中被取消，停止发送");
                return;
            }

            // 计算要发送的值
            int value = (int) (start + currentEmitted);

            // 发送给订阅者
            try {
                logger.trace("[RangeSubscription] 发送元素: {}", value);
                subscriber.onNext(value);

                currentEmitted++;
                sent++;
                emitted.incrementAndGet();

            } catch (Exception e) {
                // 如果订阅者处理元素时抛出异常，调用onError
                logger.error("[RangeSubscription] 发送元素时异常", e);
                subscriber.onError(e);
                return;
            }
        }

        // 如果所有元素都已发送，调用onComplete
        if (emitted.get() >= totalElements && !cancelled.get()) {
            logger.info("[RangeSubscription] 所有元素已发送，调用onComplete()");
            try {
                subscriber.onComplete();
            } catch (Exception e) {
                logger.error("[RangeSubscription] onComplete()执行异常", e);
            }
        }
    }

    /**
     * 实现Subscription.cancel()：取消订阅
     *
     * <p><b>规范要求</b> (Reactive Streams §3.7)：
     * <ul>
     *   <li>在cancel()之后，发布者不应再调用onNext()</li>
     *   <li>cancel()可以被多次调用</li>
     *   <li>cancel()应该尽可能快地返回</li>
     * </ul>
     */
    @Override
    public void cancel() {
        if (cancelled.compareAndSet(false, true)) {
            logger.info("[RangeSubscription] 订阅已取消，不再发送数据");
        }
    }

    /**
     * 获取已发送的元素个数（仅用于测试）
     *
     * @return 已发送元素个数
     */
    public long getEmitted() {
        return emitted.get();
    }

    /**
     * 检查是否已被取消（仅用于测试）
     *
     * @return true 如果已取消，false 否则
     */
    public boolean isCancelled() {
        return cancelled.get();
    }
}
