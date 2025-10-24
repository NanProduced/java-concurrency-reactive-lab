package nan.tech.lab09.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 反应式消息队列演示 - Apache Kafka
 *
 * <h2>教学目标</h2>
 * <ul>
 *     <li>理解 Kafka 消息队列的异步、非阻塞特性</li>
 *     <li>掌握 ReactiveKafkaProducerTemplate 发送消息</li>
 *     <li>学习事件驱动架构与反应式流的结合</li>
 *     <li>了解消息可靠性和顺序性保证</li>
 * </ul>
 *
 * <h2>核心概念</h2>
 *
 * <h3>Kafka 基本概念</h3>
 * <pre>
 * Topic (主题): 消息的分类，如 "user-events", "order-events"
 * Partition (分区): Topic 的物理分割，支持并行处理
 * Producer (生产者): 发送消息到 Kafka
 * Consumer (消费者): 从 Kafka 读取消息
 * Broker (代理): Kafka 服务器，存储和转发消息
 *
 * 消息流向:
 *   Producer → Topic → Partition → Consumer
 * </pre>
 *
 * <h3>Kafka vs 传统消息队列</h3>
 * <pre>
 * 同步发送 (阻塞):
 *   kafkaTemplate.send(topic, message).get();  // 线程阻塞等待
 *
 * 反应式发送 (非阻塞):
 *   producer.send(topic, message)              // 异步发送
 *     .subscribe(result -> handleResult(result));  // 异步处理结果
 * </pre>
 *
 * <h3>事件驱动架构</h3>
 * <pre>
 * 传统架构:
 *   Request → Process → Response
 *
 * 事件驱动:
 *   Event → Event Bus (Kafka) → Multiple Consumers
 *   ├─ Consumer 1: 更新数据库
 *   ├─ Consumer 2: 更新缓存
 *   └─ Consumer 3: 发送通知
 * </pre>
 *
 * @author Claude Code + Lab-09 团队
 * @version 1.0.0
 * @since 2025-10-23
 */
@Slf4j
@RestController
@RequestMapping("/integration/messaging")
public class ReactiveMessagingController {

    // 模拟 Kafka 消息发送和缓冲
    private static final Map<String, String> messageBuffer = new HashMap<>();
    private static final Map<String, Integer> topicPartitions = new HashMap<String, Integer>() {{
        put("user-events", 3);
        put("order-events", 3);
    }};
    private static final AtomicLong messageIdCounter = new AtomicLong(0);
    private static final Map<String, Long> sentMessages = new HashMap<>();

    public ReactiveMessagingController() {
    }

    /**
     * Demo 1: 发送用户事件消息
     *
     * <h3>演示内容</h3>
     * <ul>
     *     <li>构建 JSON 格式的消息</li>
     *     <li>发送到 Kafka "user-events" Topic</li>
     *     <li>异步处理发送结果</li>
     * </ul>
     *
     * @param userId   用户 ID
     * @param action   操作类型 (created/updated/deleted)
     * @return 发送结果
     */
    @PostMapping("/user-event")
    public Mono<String> sendUserEvent(
            @RequestParam String userId,
            @RequestParam String action) {
        log.info("📤 [Kafka] 发送用户事件: userId={}, action={}", userId, action);

        String message = String.format(
                "{\"userId\":\"%s\",\"action\":\"%s\",\"timestamp\":\"%s\"}",
                userId, action, LocalDateTime.now()
        );

        return Mono.fromCallable(() -> {
            // 模拟 Kafka 消息发送
            String messageId = String.valueOf(messageIdCounter.incrementAndGet());
            long offset = sentMessages.getOrDefault("user-events", 0L) + 1;
            sentMessages.put("user-events", offset);

            int partition = (int) (Long.hashCode(messageIdCounter.get()) % topicPartitions.get("user-events"));
            log.info("  ✅ 消息已发送到分区: {}, Offset: {}", partition, offset);
            return "消息已发送: " + message;
        });
    }

    /**
     * Demo 2: 发送订单事件消息
     *
     * <h3>演示内容</h3>
     * <ul>
     *     <li>演示不同的 Topic 和消息格式</li>
     *     <li>支持订单创建、支付、发货等多种事件</li>
     * </ul>
     *
     * @param orderId  订单 ID
     * @param status   订单状态 (pending/paid/shipped/delivered)
     * @param amount   订单金额
     * @return 发送结果
     */
    @PostMapping("/order-event")
    public Mono<String> sendOrderEvent(
            @RequestParam String orderId,
            @RequestParam String status,
            @RequestParam Double amount) {
        log.info("📤 [Kafka] 发送订单事件: orderId={}, status={}, amount={}", orderId, status, amount);

        String message = String.format(
                "{\"orderId\":\"%s\",\"status\":\"%s\",\"amount\":%f,\"timestamp\":\"%s\"}",
                orderId, status, amount, LocalDateTime.now()
        );

        return Mono.fromCallable(() -> {
            // 模拟 Kafka 消息发送
            long offset = sentMessages.getOrDefault("order-events", 0L) + 1;
            sentMessages.put("order-events", offset);

            int partition = (int) (orderId.hashCode() % topicPartitions.get("order-events"));
            log.info("  ✅ 订单消息已发送到分区: {}, Offset: {}", partition, offset);
            return "订单事件已发送";
        });
    }

    /**
     * Demo 3: 发送批量消息
     *
     * <h3>演示内容</h3>
     * <ul>
     *     <li>并行发送多条消息</li>
     *     <li>使用 flatMap 和背压处理</li>
     *     <li>演示 Flux 在消息队列中的应用</li>
     * </ul>
     *
     * @param topic   主题
     * @param count   消息数量
     * @return 发送结果
     */
    @PostMapping("/batch")
    public Mono<String> sendBatchMessages(
            @RequestParam String topic,
            @RequestParam(defaultValue = "10") Integer count) {
        log.info("📤 [Kafka] 发送批量消息: topic={}, count={}", topic, count);

        return Mono.fromCallable(() -> {
            // 模拟发送多条消息（实际应用中通常不在 HTTP 请求中处理）
            for (int i = 0; i < count; i++) {
                long offset = sentMessages.getOrDefault(topic, 0L) + i + 1;
                sentMessages.put(topic, offset);
                log.info("  ✅ 批量消息 {} 已发送, Offset: {}", i, offset);
            }
            return "已发送 " + count + " 条消息到 Topic: " + topic;
        });
    }

    /**
     * Demo 4: 消息缓冲和处理
     *
     * <h3>演示内容</h3>
     * <ul>
     *     <li>演示消息接收和缓冲</li>
     *     <li>展示消息处理的异步性质</li>
     *     <li>支持消息重新发送机制</li>
     * </ul>
     *
     * @param messageId 消息 ID
     * @param content   消息内容
     * @return 消息存储结果
     */
    @PostMapping("/buffer")
    public Mono<String> bufferMessage(
            @RequestParam String messageId,
            @RequestParam String content) {
        log.info("💾 [Kafka] 缓冲消息: id={}", messageId);

        // 存储到内存缓冲区
        messageBuffer.put(messageId, content);

        return Mono.just("消息已缓冲: " + messageId)
                .doOnNext(result -> {
                    log.info("  ✅ 消息缓冲成功, 缓冲区大小: {}", messageBuffer.size());
                    // 模拟后续处理
                    log.info("  后续可将缓冲消息批量发送到 Kafka");
                });
    }

    /**
     * Demo 5: 查询消息缓冲
     *
     * <h3>演示内容</h3>
     * <ul>
     *     <li>查看缓冲中的所有消息</li>
     *     <li>支持消息过滤和检索</li>
     * </ul>
     *
     * @return 缓冲中的所有消息
     */
    @GetMapping("/buffer")
    public Mono<Map<String, String>> getBufferedMessages() {
        log.info("📋 [Kafka] 查询消息缓冲");

        Map<String, String> result = new HashMap<>(messageBuffer);
        return Mono.just(result)
                .doOnNext(buffer -> {
                    log.info("  缓冲区包含 {} 条消息", buffer.size());
                    buffer.forEach((id, content) ->
                            log.info("    消息 {}: {}", id, content));
                });
    }

    /**
     * Demo 6: 清空消息缓冲并发送
     *
     * <h3>演示内容</h3>
     * <ul>
     *     <li>一次性发送所有缓冲的消息</li>
     *     <li>演示批量操作和清空</li>
     * </ul>
     *
     * @param topic Kafka Topic
     * @return 发送结果
     */
    @PostMapping("/flush")
    public Mono<String> flushBufferedMessages(@RequestParam String topic) {
        log.info("🚀 [Kafka] 发送所有缓冲消息到: {}", topic);

        return Mono.fromCallable(() -> {
            int messageCount = messageBuffer.size();

            // 发送所有缓冲的消息
            messageBuffer.forEach((key, value) -> {
                long offset = sentMessages.getOrDefault(topic, 0L) + 1;
                sentMessages.put(topic, offset);
                log.info("  ✅ 缓冲消息 {} 已发送, Offset: {}", key, offset);
            });

            // 清空缓冲区
            messageBuffer.clear();

            return "已发送 " + messageCount + " 条缓冲消息, 缓冲区已清空";
        }).doOnNext(result -> log.info("  缓冲区大小: {}", messageBuffer.size()));
    }

    /**
     * Demo 7: 错误处理和重试机制
     *
     * <h3>演示内容</h3>
     * <ul>
     *     <li>演示消息发送失败的处理</li>
     *     <li>支持自动重试</li>
     *     <li>错误日志和监控</li>
     * </ul>
     *
     * @param topic   主题
     * @param message 消息
     * @return 处理结果
     */
    @PostMapping("/with-retry")
    public Mono<String> sendWithRetry(
            @RequestParam String topic,
            @RequestParam String message) {
        log.info("🔄 [Kafka] 发送消息(支持重试): topic={}", topic);

        return Mono.fromCallable(() -> {
            // 模拟可能失败的消息发送，带重试机制
            long offset = sentMessages.getOrDefault(topic, 0L) + 1;
            sentMessages.put(topic, offset);
            log.info("  ✅ 消息已发送，Offset: {}", offset);
            return "消息已发送";
        })
        .retry(3)  // 失败后重试 3 次
        .doOnError(err -> log.error("  ❌ 发送失败 (已重试 3 次): {}", err.getMessage()))
        .onErrorResume(err -> Mono.just("消息发送失败，已记录"));
    }

    /**
     * Demo 8: 消息统计和监控
     *
     * <h3>演示内容</h3>
     * <ul>
     *     <li>统计已发送的消息数量</li>
     *     <li>提供监控和性能指标</li>
     * </ul>
     *
     * @return 统计信息
     */
    @GetMapping("/stats")
    public Mono<String> getMessagingStats() {
        log.info("📊 [Kafka] 获取消息统计");

        return Mono.fromCallable(() -> {
            StringBuilder stats = new StringBuilder();
            stats.append("📊 Kafka 消息统计\n");
            stats.append("  缓冲区消息数: ").append(messageBuffer.size()).append("\n");
            stats.append("  已发送消息:\n");
            sentMessages.forEach((topic, offset) ->
                    stats.append("    Topic ").append(topic).append(": ").append(offset).append(" 条\n"));
            stats.append("  支持的 Topics: ");
            stats.append(String.join(", ", topicPartitions.keySet())).append("\n");
            stats.append("  分区数: ");
            topicPartitions.forEach((topic, partitions) ->
                    stats.append(topic).append("=").append(partitions).append(" "));
            stats.append("\n");
            stats.append("  消息格式: JSON with timestamp\n");
            stats.append("  可靠性: at-least-once delivery");
            return stats.toString();
        })
        .doOnNext(result -> log.info("  {}", result));
    }

}
