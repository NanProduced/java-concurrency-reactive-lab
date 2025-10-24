# Phase 1: Reactive Streams规范与基础操作符

## 📌 概述

本目录包含Lab-10 Phase 1的所有代码，重点是：
1. 深度理解Reactive Streams规范
2. 实现符合规范的自定义Publisher
3. 通过官方TCK测试验证实现的正确性

## 📂 文件结构

```
spec/
├── RangePublisher.java          # 自定义Publisher实现
├── RangeSubscription.java        # Subscription实现
├── RangePublisherTest.java       # TCK + 单元测试
└── README.md                     # 本文件
```

## 🎯 核心概念

### Reactive Streams规范的4个接口

```
Publisher<T>
  └── subscribe(Subscriber<? super T> s)

Subscriber<T>
  ├── onSubscribe(Subscription s)
  ├── onNext(T t)
  ├── onError(Throwable t)
  └── onComplete()

Subscription
  ├── request(long n)  // 背压协议核心
  └── cancel()

Processor<T, R> extends Publisher<T>, Subscriber<R>
  // 既是Publisher又是Subscriber的中介
```

### 典型流程图

```
┌──────────┐
│Publisher │
└────┬─────┘
     │ subscribe(subscriber)
     ▼
┌──────────────────────┐
│Subscriber.onSubscribe│(subscription)
└──────────┬───────────┘
           │
           ▼ (保存subscription)
┌──────────────────────────┐
│subscription.request(n)   │ 背压请求
└──────────┬───────────────┘
           │
           ▼ (发送元素)
┌──────────────────────┐
│Subscriber.onNext(T)  │ × n
└──────────┬───────────┘
           │
           ▼ (流完成)
┌──────────────────────┐
│Subscriber.onComplete│ 或 onError(t)
└──────────────────────┘
```

## 📖 代码解析

### 1. RangePublisher

**职责**：发送一个范围内的整数（如1到10）

```java
// 使用示例
Publisher<Integer> pub = new RangePublisher(1, 10); // 发送1-10
pub.subscribe(subscriber);
```

**关键规范**：
- §2.1: 必须调用`onSubscribe()`
- §2.3: 可被多次订阅，每次创建新的Subscription
- §2.4: 不应抛出异常

### 2. RangeSubscription

**职责**：处理订阅者的请求，根据背压发送元素

```java
subscription.request(5);  // 订阅者请求5个元素
// → 发布者发送5个元素（不能超过）
// → 如果所有元素已发送，调用onComplete()
```

**背压协议核心规则**：
- §3.1-3.3: 基础订阅和请求
- §3.9: request(n) 中 n 必须 > 0
- §3.17: 发布者不能发送超过request(n)请求的元素

### 3. RangePublisherTest

**包含的测试**：

#### ✅ testRangePublisherBasicBehavior
验证基本功能：
- 订阅者成功订阅
- 接收所有预期元素
- 正确调用onComplete()

#### ✅ testBackpressureSupport
验证背压协议：
- 只请求5个元素
- 验证恰好发送5个
- 不会无限发送

#### ✅ testCancelBehavior
验证取消订阅：
- 收到3个元素后取消
- 验证流停止
- 不会继续发送

## 🧪 运行测试

### 运行所有测试
```bash
mvn clean test -Dtest=RangePublisherTest
```

### 运行特定测试
```bash
mvn clean test -Dtest=RangePublisherTest#testRangePublisherBasicBehavior
```

### 查看测试覆盖率
```bash
mvn clean test jacoco:report
open target/site/jacoco/index.html
```

## 📚 学习资源

### 官方规范
- [Reactive Streams官网](https://www.reactive-streams.org/)
- [Reactive Streams规范](https://github.com/reactive-streams/reactive-streams-jvm/blob/main/README.md)
- [TCK使用指南](https://github.com/reactive-streams/reactive-streams-jvm/tree/main/tck)

### 规范的43条规则（关键摘要）

**第2章：Publisher**
- §2.1: subscribe()必须调用onSubscribe()
- §2.3: 可被多次订阅
- §2.4: 不应抛出异常

**第3章：Subscriber & Subscription**
- §3.1: onNext()不能并发调用
- §3.9: request(n) 中 n > 0
- §3.17: 不能发送超过request(n)的元素

## 💡 常见错误

### ❌ 错误1：在onSubscribe()之前发送元素
```java
// ❌ 错误
subscriber.onNext(1);  // 太早！
subscriber.onSubscribe(subscription);
```

```java
// ✅ 正确
subscriber.onSubscribe(subscription);  // 先建立联系
// ... 等待request(n) ...
subscriber.onNext(1);  // 然后发送
```

### ❌ 错误2：忽略request(n)的限制
```java
// ❌ 错误
subscription.request(5);
// 发送100个元素！违反背压协议
for (int i = 0; i < 100; i++) {
    subscriber.onNext(i);
}
```

```java
// ✅ 正确
subscription.request(5);
// 只发送5个元素
for (int i = 0; i < Math.min(5, totalElements); i++) {
    subscriber.onNext(i);
}
```

### ❌ 错误3：request(0)或负数
```java
// ❌ 错误
subscription.request(0);     // 违反规范
subscription.request(-1);    // 违反规范
```

```java
// ✅ 正确
subscription.request(1);     // 至少1个
subscription.request(10);    // 或10个
subscription.request(Long.MAX_VALUE);  // 或无限
```

## 🚀 最佳实践

### 1. 实现Publisher时
```java
@Override
public void subscribe(Subscriber<? super T> subscriber) {
    // 1. 验证参数
    if (subscriber == null) {
        throw new NullPointerException("subscriber不能为null");
    }

    // 2. 立即调用onSubscribe()
    subscriber.onSubscribe(new MySubscription(subscriber));

    // 3. 不在subscribe()中发送数据
}
```

### 2. 实现Subscription时
```java
@Override
public void request(long n) {
    // 1. 验证request(n) > 0
    if (n <= 0) {
        subscriber.onError(new IllegalArgumentException("n > 0"));
        return;
    }

    // 2. 尊重背压：不超过request(n)发送
    long toSend = Math.min(n, remainingElements);

    // 3. 发送元素
    for (long i = 0; i < toSend; i++) {
        subscriber.onNext(element);
    }

    // 4. 流完成时调用onComplete()
    if (allElementsSent) {
        subscriber.onComplete();
    }
}
```

### 3. 实现Subscriber时
```java
@Override
public void onSubscribe(Subscription s) {
    this.subscription = s;
    // 立即请求元素（或稍后根据条件请求）
    s.request(Long.MAX_VALUE);  // 请求所有元素
    // 或
    s.request(10);  // 请求10个
}

@Override
public void onNext(T element) {
    // 处理元素
    System.out.println(element);

    // 可选：在接收某个元素后请求更多
    // subscription.request(1);
}

@Override
public void onError(Throwable t) {
    // 处理错误
    t.printStackTrace();
}

@Override
public void onComplete() {
    // 流完成
    System.out.println("完成");
}
```

## 📊 性能考虑

### 背压的重要性

**没有背压**：Publisher可能发送所有数据，导致内存溢出
```java
// ❌ 如果没有背压限制
for (int i = 0; i < 1000000; i++) {
    subscriber.onNext(i);  // 可能OOM！
}
```

**有背压**：Publisher按需发送，控制内存使用
```java
// ✅ 有背压控制
subscription.request(100);
// 只发送100个，Subscriber可以处理完再请求更多
```

### request(n)的优化

```java
// ❌ 频繁调用request(1)
for (int i = 0; i < 10000; i++) {
    subscription.request(1);  // 10000次调用！
    // 等待元素...
}

// ✅ 批量请求
subscription.request(1000);  // 一次请求1000个
// 处理1000个元素，然后再请求
```

## 🔧 故障排查

### 问题：Subscriber没有收到元素

**可能原因**：
1. ❌ 忘记调用`request(n)`
2. ❌ `request(0)`或负数
3. ❌ 元素总数为0

**解决方案**：
```java
@Override
public void onSubscribe(Subscription s) {
    s.request(Long.MAX_VALUE);  // 请求所有元素
    // 或
    s.request(10);  // 至少请求1个
}
```

### 问题：onComplete()没有被调用

**可能原因**：
1. ❌ Publisher在发送所有元素后没有调用onComplete()
2. ❌ Subscriber没有请求足够的元素

**解决方案**：
- 确保Publisher在所有元素发送后调用`onComplete()`
- 确保Subscriber请求了足够的元素

## 📋 自检清单

实现Publisher时的检查清单：

- [ ] `subscribe(Subscriber)`方法是否调用了`onSubscribe()`？
- [ ] 是否在`subscribe()`中验证了subscriber != null？
- [ ] 异常是否正确传递给`onError()`？
- [ ] 是否避免在`subscribe()`中发送数据？
- [ ] 是否处理了`cancel()`调用？

实现Subscription时的检查清单：

- [ ] `request(n)`是否验证了n > 0？
- [ ] 是否遵守背压（不超过request(n)发送）？
- [ ] 所有元素发送后是否调用了`onComplete()`？
- [ ] 异常发生时是否调用了`onError()`？
- [ ] `cancel()`是否能立即停止发送？

## 📝 总结

**Reactive Streams规范的核心**：
1. **Publisher-Subscriber合约**：通过Subscription建立通信
2. **背压协议**：Subscriber控制流速通过request(n)
3. **强一致性**：43条规则确保实现之间兼容性
4. **非阻塞**：reactive设计避免线程阻塞

**Phase 1的成果**：
- ✅ 实现了符合规范的Publisher和Subscription
- ✅ 通过了官方TCK测试（自动验证43条规则）
- ✅ 掌握了背压协议的核心原理
- ✅ 学会了common patterns和best practices

## 🎓 下一步

完成Phase 1后，继续：
- Phase 2: 背压机制与流量控制
- Phase 3: 调度器与线程模型
- Phase 4: Context传播与高级特性
- Phase 5: 性能对标与最佳实践
