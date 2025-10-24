package nan.tech.lab10.spec;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.reactivestreams.tck.PublisherVerification;
import org.reactivestreams.tck.TestEnvironment;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

/**
 * RangePublisher TCK (Technology Compatibility Kit) 测试
 *
 * <p><b>教学目标</b>：
 * <ul>
 *   <li>理解TCK的作用和价值</li>
 *   <li>验证自定义Publisher是否符合Reactive Streams规范</li>
 *   <li>学会使用PublisherVerification进行规范验证</li>
 *   <li>理解TCK测试涵盖的43条规范规则</li>
 * </ul>
 *
 * <p><b>TCK是什么？</b>：
 * <pre>
 * TCK (Technology Compatibility Kit) 是Reactive Streams官方提供的测试工具包，
 * 用于验证Publisher/Subscriber/Subscription的实现是否符合规范。
 *
 * Reactive Streams规范定义了43条规则，这些规则确保不同实现之间的兼容性。
 * TCK通过多个测试用例自动验证这些规则。
 * </pre>
 *
 * <p><b>PublisherVerification的核心测试</b>：
 * <ul>
 *   <li>§3.1-3.7: 基础订阅和请求处理</li>
 *   <li>§3.8-3.10: 错误处理</li>
 *   <li>§3.11-3.17: 背压和流量控制</li>
 *   <li>§3.18-3.19: 并发和竞态条件</li>
 * </ul>
 *
 * <p><b>如何使用TCK</b>：
 * <ol>
 *   <li>继承 PublisherVerification<T></li>
 *   <li>实现抽象方法 createPublisher(long elements)</li>
 *   <li>运行单元测试，TCK会自动验证规范</li>
 * </ol>
 *
 * <p><b>TCK依赖</b>：
 * <pre>
 * &lt;dependency&gt;
 *     &lt;groupId&gt;org.reactivestreams&lt;/groupId&gt;
 *     &lt;artifactId&gt;reactive-streams-tck&lt;/artifactId&gt;
 *     &lt;version&gt;1.0.4&lt;/version&gt;
 *     &lt;scope&gt;test&lt;/scope&gt;
 * &lt;/dependency&gt;
 * </pre>
 *
 * <p><b>运行TCK测试</b>：
 * <pre>
 * # 运行本类的所有测试
 * mvn clean test -Dtest=RangePublisherTest
 *
 * # 运行特定的TCK测试（如背压测试）
 * mvn clean test -Dtest=RangePublisherTest#required_spec313_onNext_should_not_precede_previous_onNext_calls
 * </pre>
 *
 * @author 学习者
 * @since Lab-10 Phase 1
 */
public class RangePublisherTest extends PublisherVerification<Integer> {

    /**
     * TestEnvironment 配置TCK的行为
     * - timeout: 每个测试的最大执行时间
     * - 其他配置：参考PublisherVerification文档
     */
    private static final long TIMEOUT_MILLIS = 1000L;
    private static final long POLL_TIMEOUT_MILLIS = 100L;
    private static final int POLL_INTERVAL_MILLIS = 10;

    /**
     * 构造函数：初始化TestEnvironment
     *
     * <p>TestEnvironment参数说明：
     * <ul>
     *   <li>offeredToDeadlineInMillis: 从offer到deadline的时间（ms）</li>
     *   <li>pollTimeoutInMillis: 轮询超时时间（ms）</li>
     *   <li>pollIntervalInMillis: 轮询间隔（ms）</li>
     *   <li>skipPublisherTests: 是否跳过某些测试（不推荐）</li>
     * </ul>
     */
    public RangePublisherTest() {
        super(new TestEnvironment(
                TIMEOUT_MILLIS,        // offeredToDeadlineInMillis
                POLL_TIMEOUT_MILLIS,   // pollTimeoutInMillis
                POLL_INTERVAL_MILLIS   // pollIntervalInMillis
        ));
    }

    /**
     * 核心方法：创建Publisher实例
     *
     * <p><b>规范要求</b>：
     * <ul>
     *   <li>该方法被TCK多次调用，每次创建新的Publisher实例</li>
     *   <li>返回的Publisher必须能够发送恰好elements个元素</li>
     *   <li>如果elements=0，应该立即调用onComplete()</li>
     * </ul>
     *
     * @param elements 该Publisher应该发送的元素个数
     * @return 新创建的Publisher实例
     */
    @Override
    public Publisher<Integer> createPublisher(long elements) {
        // 验证elements的范围（TCK会传入0到Long.MAX_VALUE的值）
        if (elements < 0 || elements > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    String.format("elements必须在0到%d之间，但收到%d",
                            Integer.MAX_VALUE, elements));
        }

        return new RangePublisher(1, (int) elements);
    }

    /**
     * 可选方法：返回最大支持的元素数
     *
     * <p>TCK使用此方法确定测试的规模：
     * <ul>
     *   <li>如果返回Integer.MAX_VALUE，TCK会用大量数据测试</li>
     *   <li>如果返回较小值（如1000），TCK会进行小规模测试</li>
     * </ul>
     *
     * @return 该Publisher实现可以处理的最大元素数
     */
    @Override
    public long maxElementsFromPublisher() {
        // RangePublisher可以处理Integer.MAX_VALUE个元素
        return Integer.MAX_VALUE;
    }

    /**
     * 必须方法：创建一个失败的Publisher
     *
     * <p><b>规范要求</b>：
     * <ul>
     *   <li>该Publisher应该在onSubscribe()之后立即调用onError()</li>
     *   <li>onError()的异常应该是一个有意义的错误（而不是NullPointerException）</li>
     * </ul>
     *
     * <p>TCK使用此方法测试Publisher在发生错误时是否正确处理。
     *
     * @param elements 错误Publisher应该能够处理的元素数
     * @return 一个会立即触发onError()的Publisher
     */
    @Override
    public Publisher<Integer> createFailedPublisher() {
        return subscriber -> {
            try {
                subscriber.onSubscribe(new org.reactivestreams.Subscription() {
                    @Override
                    public void request(long n) {
                        // 失败的Publisher不处理请求
                    }

                    @Override
                    public void cancel() {
                        // 失败的Publisher不处理取消
                    }
                });
            } catch (Throwable t) {
                // 忽略
            }
            // 立即发送错误
            subscriber.onError(new RuntimeException("Publisher失败：测试故意触发的错误"));
        };
    }

    /**
     * 验证Publisher符合规范的单元测试
     *
     * <p><b>测试内容</b>：
     * 验证RangePublisher的基本功能：
     * <ul>
     *   <li>订阅者能否成功订阅</li>
     *   <li>是否接收到所有预期的元素</li>
     *   <li>是否正确调用了onComplete()</li>
     * </ul>
     */
    @Test
    public void testRangePublisherBasicBehavior() {
        // 创建一个发送1-5的发布者
        RangePublisher publisher = new RangePublisher(1, 5);

        // 跟踪接收到的元素
        java.util.List<Integer> received = new java.util.ArrayList<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        java.util.concurrent.CountDownLatch completed = new java.util.concurrent.CountDownLatch(1);

        // 订阅
        publisher.subscribe(new Subscriber<Integer>() {
            private Subscription subscription;

            @Override
            public void onSubscribe(Subscription s) {
                this.subscription = s;
                // 请求5个元素
                s.request(5);
            }

            @Override
            public void onNext(Integer value) {
                received.add(value);
            }

            @Override
            public void onError(Throwable t) {
                error.set(t);
                completed.countDown();
            }

            @Override
            public void onComplete() {
                completed.countDown();
            }
        });

        try {
            // 等待流完成
            completed.await(2, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 验证结果
        assert error.get() == null : "不应该发生错误: " + error.get();
        assert received.equals(java.util.Arrays.asList(1, 2, 3, 4, 5)) :
                "应该接收1-5的数字，但接收到: " + received;
    }

    /**
     * 验证背压支持
     *
     * <p><b>测试内容</b>：
     * 验证Publisher是否�受背压协议约束（只发送request(n)请求的元素）
     */
    @Test
    public void testBackpressureSupport() {
        // 创建一个发送1-100的发布者（但只请求5个）
        RangePublisher publisher = new RangePublisher(1, 100);

        java.util.List<Integer> received = new java.util.ArrayList<>();
        java.util.concurrent.CountDownLatch completed = new java.util.concurrent.CountDownLatch(1);

        publisher.subscribe(new Subscriber<Integer>() {
            @Override
            public void onSubscribe(Subscription s) {
                // 只请求5个元素（而不是全部100个）
                s.request(5);
            }

            @Override
            public void onNext(Integer value) {
                received.add(value);
            }

            @Override
            public void onError(Throwable t) {
                completed.countDown();
            }

            @Override
            public void onComplete() {
                // 这里不调用complete，因为我们没有请求所有元素
            }
        });

        try {
            // 等待所有请求的元素被发送
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 验证只接收了5个元素（不能超过request(n)请求的数量）
        assert received.size() == 5 : "应该只接收5个元素（遵守背压），但接收到: " + received.size();
        assert received.equals(java.util.Arrays.asList(1, 2, 3, 4, 5)) :
                "应该接收1-5，但接收到: " + received;
    }

    /**
     * 验证cancel()功能
     *
     * <p><b>测试内容</b>：
     * 验证取消订阅后，发布者不再发送元素：
     * <ul>
     *   <li>订阅者请求10个元素</li>
     *   <li>收到3个元素后取消</li>
     *   <li>验证只接收了3个元素</li>
     * </ul>
     */
    @Test
    public void testCancelBehavior() {
        RangePublisher publisher = new RangePublisher(1, 10);

        java.util.List<Integer> received = new java.util.ArrayList<>();
        java.util.concurrent.CountDownLatch cancelled = new java.util.concurrent.CountDownLatch(1);

        publisher.subscribe(new Subscriber<Integer>() {
            private Subscription subscription;
            private int count = 0;

            @Override
            public void onSubscribe(Subscription s) {
                this.subscription = s;
                s.request(10);
            }

            @Override
            public void onNext(Integer value) {
                received.add(value);
                count++;

                // 收到3个元素后取消
                if (count == 3) {
                    subscription.cancel();
                    cancelled.countDown();
                }
            }

            @Override
            public void onError(Throwable t) {
                cancelled.countDown();
            }

            @Override
            public void onComplete() {
                cancelled.countDown();
            }
        });

        try {
            cancelled.await(2, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 验证只接收了3个元素
        assert received.size() == 3 : "取消后应该只接收3个元素，但接收到: " + received.size();
        assert received.equals(java.util.Arrays.asList(1, 2, 3)) :
                "应该接收1-3，但接收到: " + received;
    }
}
