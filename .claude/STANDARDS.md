# 开发规范总汇 (STANDARDS)

> **目标**: 统一的代码质量、测试、文档、包名规范
> **适用范围**: 所有 14 个 Lab 模块
> **更新频率**: 按需

---

## 1. 代码质量规范

### 1.1 线程安全

- ✅ 共享状态必须有同步机制 (Lock/Atomic/Concurrent*)
- ✅ 避免 check-then-act 竞态条件
- ✅ volatile 字段用法正确
- ✅ 无隐蔽的共享状态

### 1.2 异步与响应式

- ✅ CompletableFuture 链有异常处理
- ✅ Reactor 代码显式处理背压
- ✅ MDC/TraceId 在异步链路中传播
- ✅ 无阻塞操作混入响应式流

### 1.3 资源管理

- ✅ ExecutorService 有 shutdown 逻辑
- ✅ 连接正确关闭 (try-with-resource)
- ✅ ThreadLocal 有 remove()
- ✅ Flux/Mono 订阅有 dispose

---

## 2. Javadoc 格式化规范

### 2.1 基本原则

1. **首句独立** - 第一句作为概览
2. **段落分离** - 使用 `<p>` 分隔逻辑段落
3. **列表格式** - 多条信息用 `<ul><li>` 替代 `-` 缩进
4. **关键词加粗** - 参数名、配置项等用 `<b>` 包裹
5. **公式保护** - 数学公式用 `<pre>` 标签
6. **标准标签** - 使用 `@param`, `@return`, `@throws`

### 2.2 类级 Javadoc

```java
/**
 * 类的主要功能说明。
 *
 * <p><b>@教学</b>
 * <ul>
 *   <li>学习点 1</li>
 *   <li>学习点 2</li>
 * </ul>
 *
 * <p><b>@陷阱</b>
 * <ul>
 *   <li>常见错误 1</li>
 *   <li>常见错误 2</li>
 * </ul>
 */
public class MyClass {
}
```

### 2.3 方法级 Javadoc（简单）

```java
/**
 * 方法说明。
 *
 * @param param1 参数 1 说明
 * @return 返回值说明
 */
public void myMethod(String param1) {
}
```

### 2.4 方法级 Javadoc（复杂/公式）

```java
/**
 * 计算最优值。
 *
 * <p><b>@教学</b>
 * <p>使用公式:
 * <pre>
 * result = input * factor
 *
 * 其中:
 *   input = 输入值
 *   factor = 因子
 * </pre>
 *
 * <p><b>@参考</b>
 * <ul>
 *   <li>参考资料 1</li>
 *   <li>参考资料 2</li>
 * </ul>
 *
 * @param input 输入值
 * @param factor 因子
 * @return 计算结果
 */
public int calculate(int input, double factor) {
}
```

### 2.5 注释标签规范

| 标签 | 用途 | 示例 |
|------|------|------|
| `@教学` | 学习意义和知识点 | 展示如何正确使用... |
| `@陷阱` | 常见错误和注意事项 | 不要假设... |
| `@对标` | 性能对比和数据 | 对比方案 A/B... |
| `@参考` | 文档和出处 | Java Concurrency in Practice... |
| `@实现细节` | 实现的技术细节 | 内部使用... |

---

## 3. 代码注释规范

### 3.1 注释密度要求

- ✅ 代码注释密度 ≥ 70%
- ✅ 每个公开类必须有 Javadoc
- ✅ 每个公开方法必须有 Javadoc
- ✅ 关键行代码需要注释

### 3.2 注释内容指导

- ✅ 解释"为什么"而非"是什么"
- ✅ 复杂算法需要分步骤说明
- ✅ 配置参数需要解释含义
- ✅ 异常情况需要说明处理

---

## 4. 测试策略（2025-10-18 更新）

### 4.1 核心原则

**有意义的业务逻辑测试 > 盲目追求覆盖率**

### 4.2 测试决策标准

#### ✅ 必须编写测试的场景

1. **核心业务逻辑**
   - 复杂的算法实现
   - 关键业务流程
   - 数据转换和计算逻辑
   - 示例：线程池参数计算、背压策略实现

2. **并发安全性验证**
   - 线程安全的数据结构
   - 锁机制的正确性
   - 竞态条件验证
   - 示例：计数器正确性、资源池并发访问

3. **边界条件和异常处理**
   - 空值、边界值处理
   - 异常情况的容错
   - 资源释放的正确性

#### ✅ 推荐编写测试的场景

1. 工具类的关键方法
2. 配置解析和验证逻辑
3. 复杂的条件判断
4. 集成点的交互逻辑

#### ❌ 无需编写测试的场景

1. **简单的 POJO**
   - Getter/Setter 方法
   - 简单的构造函数
   - toString/equals/hashCode（Lombok 生成）

2. **教学演示代码**
   - Demo 类的 main 方法
   - 自启动演示程序
   - 交互式教学代码

3. **配置类**
   - Spring Boot Configuration
   - 简单的 Bean 定义

### 4.3 教学型项目特殊规则

本项目是教学型项目，与生产项目有不同的质量标准：

1. **自启动演示 > 单元测试**
   - 每个 Demo 必须提供可运行的 main 方法
   - 演示效果比测试覆盖率更重要

2. **注释密度 ≥ 70%**
   - 丰富的代码注释比单元测试更有教学价值
   - Javadoc 必须完整且易懂

3. **对比式教学设计**
   - WITH vs WITHOUT 对比演示
   - 正确 vs 错误的场景对比
   - 这些设计比单元测试更能说明问题

### 4.4 AI 决策权限

**AI 自主判断是否需要编写单元测试**，根据：
1. 代码复杂度（简单逻辑无需测试）
2. 业务价值（核心逻辑需要测试）
3. 并发风险（线程安全需要验证）
4. 教学价值（演示代码优先）

**不强制 JaCoCo 覆盖率指标**，但建议：
- 核心工具类：覆盖率 ≥ 80%
- 业务逻辑：覆盖率 ≥ 60%
- Demo 类：0% 也可接受

### 4.5 测试命名

| 类型 | 命名模式 | 示例 |
|------|---------|------|
| 单元测试 | `*Test.java` | `ThreadUtilTest.java` |
| 集成测试 | `*IntegrationTest.java` | `ApplicationConfigIntegrationTest.java` |
| 基准测试 | `*Benchmark.java` | `ThreadPoolBenchmark.java` |

### 4.6 测试类型指南

#### 单元测试（JUnit 5）
- 快速、隔离、可重复
- 使用 `@Test`, `@ParameterizedTest`
- Mock 外部依赖（Mockito）

#### 并发测试（Awaitility）
- 验证异步行为
- 使用 `CountDownLatch` 协调
- 等待条件满足

#### 集成测试（可选）
- 使用 `@SpringBootTest`
- Testcontainers（数据库、消息队列）
- 仅用于关键集成点

### 4.7 测试结构

```java
// Arrange - 准备
// Act - 执行
// Assert - 验证
```

### 4.8 示例对比

#### ❌ 错误：为覆盖率而测试

```java
@Test
void testGetCount() {
    Counter counter = new Counter();
    assertEquals(0, counter.getCount()); // 无意义的测试
}
```

#### ✅ 正确：有意义的业务逻辑测试

```java
@Test
void testConcurrentIncrement() throws InterruptedException {
    Counter counter = new Counter();
    int threads = 100;
    int increments = 1000;

    // 并发测试线程安全性
    CountDownLatch latch = new CountDownLatch(threads);
    for (int i = 0; i < threads; i++) {
        new Thread(() -> {
            for (int j = 0; j < increments; j++) {
                counter.increment();
            }
            latch.countDown();
        }).start();
    }

    latch.await();
    assertEquals(threads * increments, counter.getCount());
}
```

### 4.9 压测标准化参数

```
JVM: -Xmx2g -Xms2g -XX:+UseG1GC
并发度: [10, 50, 100, 500, 1000]
持续时间: 120s
预热: 30s
```

### 4.10 采集指标

- ✅ P50/P95/P99 延迟
- ✅ 吞吐量 (req/s)
- ✅ 错误率
- ✅ GC 信息
- ✅ 线程数
- ✅ 内存占用

---

## 5. 包名规范 (nan.tech.*)

### 5.1 顶级结构

```
nan.tech
├── lab01.threadbasics
├── lab02.locks
├── ...
├── lab14.shootout
├── common          # 跨模块共享
└── framework       # 框架基础设施
```

### 5.2 Lab 内部结构

```
nan.tech.labXX.name
├── basics          # 基础概念演示
├── correct         # ✅ 正确实现
├── incorrect       # ❌ 常见错误（教学用）
├── benchmark       # JMH 基准测试
├── utils           # 工具类（lab 专用）
└── config          # 配置类
```

### 5.3 命名规则

| 元素 | 规则 | 示例 |
|------|------|------|
| 包名 | 小写，下划线连接 | `lab01_thread_basics` |
| 类名 | 大驼峰 | `ThreadSafeCounter` |
| 测试类 | 后缀 `Test` | `ThreadSafeCounterTest` |
| 基准类 | 后缀 `Benchmark` | `ThreadPoolBenchmark` |

### 5.4 验证

```bash
# 检查包名规范
build/scripts/check-package-naming.sh

# 构建时验证
mvn checkstyle:check
```

---

## 6. 文档规范

### 6.1 README 模板

每个 Lab 的 README 必须包含：

- ✅ 学习目标 (3+ 个清晰的目标)
- ✅ 关键概念 (概念表)
- ✅ 快速开始 (可运行命令)
- ✅ 实验步骤 (实验 1/2/3 的详细步骤)
- ✅ 常见坑 & 修复 (≥ 3 个)
- ✅ 对标数据 (若有)
- ✅ 进度追踪 (百分比 + 待办)

### 6.2 文档完成度评分

```
代码质量: 40% (线程安全/异常处理/资源释放)
测试覆盖: 20% (覆盖率 + 场景完整度)
文档完整: 25% (README + Javadoc + 注释)
学习价值: 15% (教学注释 + 示例 + 解释)

目标: ≥ 90 分
```

---

## 7. 代码质量检查清单

### 快速检查

```
✅ 线程安全
  □ 共享状态有同步机制
  □ 没有 check-then-act 竞态
  □ volatile 用法正确

✅ 资源管理
  □ ExecutorService 有 shutdown
  □ 连接正确关闭
  □ ThreadLocal 有 remove()

✅ 注释完整性
  □ Javadoc 覆盖率 100% (公开 API)
  □ 代码注释密度 ≥ 70%
  □ 关键行有解释

✅ 测试质量（AI 自主决策）
  □ 核心业务逻辑有测试
  □ 并发安全性有验证
  □ 边界条件有覆盖
  □ 所有测试通过（如果有测试）
```

---

## 8. 常用命令

```bash
# 代码质量检查
mvn clean test jacoco:report          # 覆盖率
mvn checkstyle:check                  # 风格检查
mvn org.pitest:pitest-maven:mutationCoverage  # 变异测试

# 文档生成
mvn javadoc:javadoc

# 快速验证
mvn clean install -DskipTests
```

---

## 9. HTML 标签速查表

| 标签 | 用途 | 示例 |
|------|------|------|
| `<p>` | 段落分离 | `<p>新段落</p>` |
| `<b>` | 加粗 | `<b>重要</b>` |
| `<code>` | 代码 | `<code>myMethod()</code>` |
| `<pre>` | 预格式文本 | `<pre>N = A * B</pre>` |
| `<ul><li>` | 无序列表 | `<li>项目</li>` |
| `<ol><li>` | 有序列表 | `<li>步骤 1</li>` |

---

**最后更新**: 2025-10-18
**维护者**: AI Assistant
**应用范围**: 所有 Lab 模块

**更新记录**:
- 2025-10-18: 更新测试规范，反映教学型项目特殊性和 AI 自主决策权

