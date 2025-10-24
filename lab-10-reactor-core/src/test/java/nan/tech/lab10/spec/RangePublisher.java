package nan.tech.lab10.spec;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reactive Streams规范实现：Publisher
 *
 * <p><b>教学目标</b>：
 * <ul>
 *   <li>理解Publisher接口的语义</li>
 *   <li>掌握subscribe(Subscriber)的正确实现</li>
 *   <li>理解发布者和订阅者的关系</li>
 *   <li>学会实现符合规范的Publisher</li>
 * </ul>
 *
 * <p><b>核心概念</b>：
 * <pre>
 * Publisher 是数据源，负责发送数据给Subscriber
 *
 * 典型流程：
 *   1. 订阅者调用 publisher.subscribe(subscriber)
 *   2. 发布者立即调用 subscriber.onSubscribe(subscription)
 *      - 传递Subscription给订阅者，用于控制数据流
 *   3. 订阅者调用 subscription.request(n) 请求n个元素
 *   4. 发布者根据request(n)调用:
 *      - subscriber.onNext(element)  发送元素
 *      - subscriber.onComplete()     流完成
 *      - subscriber.onError(error)   流出错
 * </pre>
 *
 * <p><b>Reactive Streams规范关键规则</b>：
 * <ul>
 *   <li>§2.1: subscribe(Subscriber)必须调用onSubscribe()</li>
 *   <li>§2.3: subscribe()可以被多次调用，每次创建独立的Subscription</li>
 *   <li>§2.4: subscribe()不应该抛出异常</li>
 * </ul>
 *
 * <p><b>使用场景</b>：
 * <pre>
 * // 示例：发送1到10的数字
 * Publisher<Integer> pub = new RangePublisher(1, 10);
 *
 * // 订阅者处理这些数字
 * pub.subscribe(new Subscriber<Integer>() {
 *     private Subscription sub;
 *
 *     @Override
 *     public void onSubscribe(Subscription s) {
 *         this.sub = s;
 *         s.request(5);  // 请求5个元素
 *     }
 *
 *     @Override
 *     public void onNext(Integer value) {
 *         System.out.println(value);
 *         // 可以继续请求更多元素
 *     }
 *
 *     @Override
 *     public void onError(Throwable t) {
 *         t.printStackTrace();
 *     }
 *
 *     @Override
 *     public void onComplete() {
 *         System.out.println("完成");
 *     }
 * });
 * </pre>
 *
 * @author 学习者
 * @since Lab-10 Phase 1
 */
public class RangePublisher implements Publisher<Integer> {

    private static final Logger logger = LoggerFactory.getLogger(RangePublisher.class);

    /**
     * 要发送的数字范围：[start, start+count)
     */
    private final int start;
    private final int count;

    /**
     * 创建一个新的RangePublisher
     *
     * <p><b>参数说明</b>：
     * <ul>
     *   <li>start: 起始数字（包含）</li>
     *   <li>count: 要发送的数字个数（即将发送start, start+1, ..., start+count-1）</li>
     * </ul>
     *
     * @param start 起始数字
     * @param count 要发送的个数
     * @throws IllegalArgumentException 如果count < 0
     */
    public RangePublisher(int start, int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count 不能为负数");
        }
        this.start = start;
        this.count = count;

        logger.info("[RangePublisher] 创建发布者: range=[{}, {}), 元素个数={}",
                start, start + count, count);
    }

    /**
     * 实现Publisher.subscribe(Subscriber)
     *
     * <p><b>规范要求</b> (Reactive Streams §2)：
     * <ul>
     *   <li>§2.1: 必须调用subscriber.onSubscribe(subscription)</li>
     *   <li>§2.3: 可以被多次调用，每次创建新的Subscription</li>
     *   <li>§2.4: 不应该抛出异常</li>
     * </ul>
     *
     * <p><b>实现要点</b>：
     * <ul>
     *   <li>创建RangeSubscription实例</li>
     *   <li>立即调用subscriber.onSubscribe()，传递Subscription</li>
     *   <li>不要在subscribe()方法中发送数据</li>
     *   <li>异常处理：捕获异常并传递给onError()</li>
     * </ul>
     *
     * @param subscriber 订阅者（不能为null）
     * @throws NullPointerException 如果subscriber为null
     */
    @Override
    public void subscribe(Subscriber<? super Integer> subscriber) {
        // 规范检查：subscriber不能为null
        if (subscriber == null) {
            throw new NullPointerException("subscriber 不能为null");
        }

        logger.info("[RangePublisher] 新订阅者加入");

        try {
            // 创建Subscription实例
            RangeSubscription subscription = new RangeSubscription(subscriber, start, count);

            // 规范§2.1：必须调用onSubscribe()
            // 这是发布者和订阅者建立联系的关键步骤
            subscriber.onSubscribe(subscription);

            logger.debug("[RangePublisher] 成功创建Subscription并传递给订阅者");

        } catch (Throwable e) {
            // 如果发生异常（通常不应该发生），不要再调用onSubscribe()
            // 而是尝试通知订阅者异常
            logger.error("[RangePublisher] subscribe()执行异常", e);
            try {
                subscriber.onError(e);
            } catch (Exception e2) {
                // 如果onError()也失败了，只能记录日志
                logger.error("[RangePublisher] 在异常处理中再次出错", e2);
            }
        }
    }

    /**
     * 获取起始数字（仅用于测试）
     *
     * @return 起始数字
     */
    public int getStart() {
        return start;
    }

    /**
     * 获取要发送的个数（仅用于测试）
     *
     * @return 元素个数
     */
    public int getCount() {
        return count;
    }
}
