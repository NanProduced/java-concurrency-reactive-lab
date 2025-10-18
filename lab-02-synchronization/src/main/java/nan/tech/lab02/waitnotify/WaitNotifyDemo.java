package nan.tech.lab02.waitnotify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.Queue;

/**
 * wait-notify 机制演示
 *
 * <p>【教学目标】</p>
 * <ol>
 *   <li>理解 wait/notify/notifyAll 的工作原理</li>
 *   <li>掌握经典的生产者-消费者模式</li>
 *   <li>理解为什么必须在 synchronized 块中使用</li>
 *   <li>学会避免虚假唤醒（spurious wakeup）</li>
 * </ol>
 *
 * <p>【核心原理】</p>
 * <pre>
 * wait/notify = 餐厅叫号系统
 * ┌─────────────────────────────────────┐
 * │  厨房（生产者）    取餐区（缓冲队列） │
 * │     👨‍🍳  ───→   🍔🍔🍔              │
 * │                   ↓                │
 * │  等候区（消费者）  notify()         │
 * │     👤👤  ←───  wait()             │
 * └─────────────────────────────────────┘
 *
 * 队列满 → 生产者 wait()
 * 队列空 → 消费者 wait()
 * 生产后 → notify() 唤醒消费者
 * 消费后 → notify() 唤醒生产者
 * </pre>
 *
 * <p>【关键要点】</p>
 * <ul>
 *   <li>wait() 必须在 synchronized 块中调用</li>
 *   <li>wait() 会释放锁，让其他线程执行</li>
 *   <li>notify() 唤醒一个等待线程</li>
 *   <li>notifyAll() 唤醒所有等待线程</li>
 *   <li>使用 while 循环检查条件（防止虚假唤醒）</li>
 * </ul>
 *
 * @author nan
 * @since 2025-10-17
 */
public class WaitNotifyDemo {

    private static final Logger logger = LoggerFactory.getLogger(WaitNotifyDemo.class);

    public static void main(String[] args) throws InterruptedException {
        logger.info("=== wait-notify 机制演示开始 ===\n");

        // 场景 1：基本的生产者-消费者
        logger.info(">>> 场景 1：生产者-消费者（队列容量 = 3）");
        demonstrateProducerConsumer();

        // 场景 2：多生产者多消费者
        logger.info("\n>>> 场景 2：多生产者多消费者（notifyAll 的必要性）");
        demonstrateMultipleProducersConsumers();

        logger.info("\n=== wait-notify 机制演示结束 ===");
    }

    /**
     * 场景 1：基本的生产者-消费者
     */
    private static void demonstrateProducerConsumer() throws InterruptedException {
        Buffer buffer = new Buffer(3); // 容量为 3 的缓冲区

        // 生产者线程
        Thread producer = new Thread(() -> {
            for (int i = 1; i <= 10; i++) {
                try {
                    buffer.produce(i);
                    Thread.sleep(200); // 生产速度
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "Producer");

        // 消费者线程
        Thread consumer = new Thread(() -> {
            for (int i = 1; i <= 10; i++) {
                try {
                    buffer.consume();
                    Thread.sleep(500); // 消费速度慢于生产
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "Consumer");

        producer.start();
        consumer.start();

        producer.join();
        consumer.join();

        logger.info("\n✅ 结论: wait-notify 成功协调生产者和消费者");
    }

    /**
     * 场景 2：多生产者多消费者
     * 展示 notifyAll() 的必要性
     */
    private static void demonstrateMultipleProducersConsumers() throws InterruptedException {
        Buffer buffer = new Buffer(5);

        // 启动 2 个生产者
        for (int i = 1; i <= 2; i++) {
            final int producerId = i;
            new Thread(() -> {
                for (int j = 1; j <= 5; j++) {
                    try {
                        buffer.produce(producerId * 10 + j);
                        Thread.sleep(300);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }, "Producer-" + i).start();
        }

        // 启动 3 个消费者
        for (int i = 1; i <= 3; i++) {
            new Thread(() -> {
                for (int j = 1; j <= 3; j++) {
                    try {
                        buffer.consume();
                        Thread.sleep(400);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }, "Consumer-" + i).start();
        }

        Thread.sleep(6000); // 等待所有线程完成
        logger.info("\n✅ 结论: notifyAll() 可唤醒所有等待线程，避免死锁");
    }

    /**
     * 有界缓冲区实现（经典生产者-消费者）
     */
    static class Buffer {
        private final Queue<Integer> queue = new LinkedList<>();
        private final int capacity;

        public Buffer(int capacity) {
            this.capacity = capacity;
        }

        /**
         * 生产数据
         * @param item 数据项
         */
        public synchronized void produce(int item) throws InterruptedException {
            // 关键：使用 while 而不是 if（防止虚假唤醒）
            while (queue.size() == capacity) {
                logger.warn("缓冲区已满({}/{}), 生产者 {} 等待...",
                        queue.size(), capacity, Thread.currentThread().getName());
                wait(); // 释放锁并等待
            }

            queue.offer(item);
            logger.info("✅ {} 生产: {}, 缓冲区: {}/{}",
                    Thread.currentThread().getName(), item, queue.size(), capacity);

            notifyAll(); // 唤醒所有等待的消费者
        }

        /**
         * 消费数据
         * @return 数据项
         */
        public synchronized int consume() throws InterruptedException {
            // 关键：使用 while 而不是 if（防止虚假唤醒）
            while (queue.isEmpty()) {
                logger.warn("缓冲区为空, 消费者 {} 等待...",
                        Thread.currentThread().getName());
                wait(); // 释放锁并等待
            }

            int item = queue.poll();
            logger.info("✅ {} 消费: {}, 缓冲区: {}/{}",
                    Thread.currentThread().getName(), item, queue.size(), capacity);

            notifyAll(); // 唤醒所有等待的生产者
            return item;
        }

        public synchronized int size() {
            return queue.size();
        }
    }

    /**
     * 错误示例：使用 if 而不是 while（会导致虚假唤醒问题）
     */
    static class BuggyBuffer {
        private final Queue<Integer> queue = new LinkedList<>();
        private final int capacity;

        public BuggyBuffer(int capacity) {
            this.capacity = capacity;
        }

        /**
         * ❌ 错误：使用 if 检查条件
         */
        public synchronized void produceWrong(int item) throws InterruptedException {
            if (queue.size() == capacity) { // ❌ 应该用 while
                wait();
            }
            queue.offer(item);
            notifyAll();
        }

        /**
         * ❌ 错误：使用 if 检查条件
         */
        public synchronized int consumeWrong() throws InterruptedException {
            if (queue.isEmpty()) { // ❌ 应该用 while
                wait();
            }
            int item = queue.poll();
            notifyAll();
            return item;
        }

        /**
         * ✅ 正确：使用 while 检查条件
         */
        public synchronized void produceCorrect(int item) throws InterruptedException {
            while (queue.size() == capacity) { // ✅ 使用 while
                wait();
            }
            queue.offer(item);
            notifyAll();
        }

        /**
         * ✅ 正确：使用 while 检查条件
         */
        public synchronized int consumeCorrect() throws InterruptedException {
            while (queue.isEmpty()) { // ✅ 使用 while
                wait();
            }
            int item = queue.poll();
            notifyAll();
            return item;
        }
    }

    /**
     * 实际应用：任务队列
     */
    static class TaskQueue {
        private final Queue<Runnable> tasks = new LinkedList<>();
        private final int capacity;
        private volatile boolean shutdown = false;

        public TaskQueue(int capacity) {
            this.capacity = capacity;
        }

        /**
         * 提交任务
         */
        public synchronized void submit(Runnable task) throws InterruptedException {
            while (tasks.size() == capacity && !shutdown) {
                wait();
            }

            if (shutdown) {
                throw new IllegalStateException("TaskQueue 已关闭");
            }

            tasks.offer(task);
            logger.info("任务提交，队列大小: {}", tasks.size());
            notifyAll();
        }

        /**
         * 获取任务
         */
        public synchronized Runnable take() throws InterruptedException {
            while (tasks.isEmpty() && !shutdown) {
                wait();
            }

            if (shutdown && tasks.isEmpty()) {
                return null; // 队列已空且已关闭
            }

            Runnable task = tasks.poll();
            notifyAll();
            return task;
        }

        /**
         * 关闭队列
         */
        public synchronized void shutdown() {
            shutdown = true;
            notifyAll(); // 唤醒所有等待的线程
        }
    }
}
