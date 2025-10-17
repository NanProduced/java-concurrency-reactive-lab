# Lab-00: Foundation & Infrastructure 基础设施

> **目标**: 为所有 14 个学习 lab 建立共享的基础设施、工具链和开发规范

---

## 📌 快速开始

### 项目初始化

```bash
# 1. 克隆项目
git clone <repo-url>
cd java-concurrency-reactive-lab

# 2. 构建项目
mvn clean install

# 3. 验证安装
mvn test

# 4. 检查覆盖率
mvn jacoco:report
open target/site/jacoco/index.html
```

### 项目结构

```
java-concurrency-reactive-lab/
├── pom.xml                      # 根 POM，管理所有版本和依赖
├── lab-00-foundation/          # 基础设施模块
│   ├── src/main/java/nan/tech/
│   │   ├── common/
│   │   │   ├── utils/          # 共享工具类
│   │   │   │   └── ThreadUtil.java
│   │   │   └── test/           # 测试基类
│   │   │       └── BaseTest.java
│   │   └── framework/
│   │       └── config/         # 框架配置
│   │           └── ApplicationConfiguration.java
│   └── src/test/java/
├── .claude/
│   ├── CLAUDE.md               # 项目级配置
│   ├── DEVELOPMENT_RULES.md    # 开发规范
│   ├── COMMENT_STANDARDS.md    # 注释规范
│   ├── TEST_STANDARDS.md       # 测试规范
│   ├── PROGRESS_TRACKER.md     # 进度追踪
│   ├── PACKAGE_NAMING_CONVENTION.md
│   ├── CONTEXT7_INTEGRATION.md
│   ├── SERENA_CE_INTEGRATION.md
│   └── templates/              # 代码模板库
├── docs/
│   ├── PITFALLS.md            # 常见坑库
│   ├── DECISION_TREES.md      # 决策树库
│   └── BEST_PRACTICES.md      # 最佳实践库
└── build/
    └── scripts/               # 自动化脚本
        ├── run-load-test.sh   # 压测脚本
        ├── generate-flamegraph.sh
        ├── analyze-gc-log.sh
        └── generate-report.sh
```

---

## 🎯 核心功能

### 1. Maven 多模块项目管理

**特性**:
- ✅ 统一的版本管理（使用 BOM）
- ✅ Spring Boot 3.3 + Reactor + Netty 依赖
- ✅ 集成 JaCoCo、PIT、Checkstyle 等质量工具
- ✅ 标准化的构建配置

**使用示例**:
```bash
# 构建整个项目
mvn clean install

# 只构建特定模块
mvn clean install -pl lab-00-foundation

# 跳过测试快速构建
mvn clean install -DskipTests
```

### 2. 共享工具库 (ThreadUtil)

**功能**:
- 获取处理器数量
- 计算最优线程池大小
- 线程安全的等待方法
- 获取线程调试信息

**使用示例**:
```java
// 计算 IO 密集型线程池大小
int ioPoolSize = ThreadUtil.calculateOptimalThreadPoolSize(0.7, 10.0);

// 计算 CPU 密集型线程池大小
int cpuPoolSize = ThreadUtil.calculateDefaultCpuIntensivePoolSize();

// 安全等待
ThreadUtil.safeSleep(1000); // 等待 1 秒
```

### 3. 应用配置 (ApplicationConfiguration)

**提供的 Bean**:
- `asyncExecutor`: 通用异步线程池
- `ioExecutor`: IO 密集型线程池
- `cpuExecutor`: CPU 密集型线程池
- `metricsCollector`: 指标收集器

**特点**:
- ✅ 根据 CPU 核数自动配置
- ✅ 优雅关闭支持
- ✅ 线程名称清晰（便于调试）

### 4. 测试基类 (BaseTest)

**功能**:
- 性能计时工具
- 内存信息输出
- 线程状态检查
- 日志记录

**使用示例**:
```java
public class MyTest extends BaseTest {
    @Test
    public void testPerformance() {
        Timer timer = startTimer("myTest");

        // 执行测试代码
        doSomething();

        long duration = timer.stopAndPrint();

        printMemoryInfo();
        printThreadInfo();
    }
}
```

### 5. 自动化脚本

#### 压测脚本 (run-load-test.sh)
```bash
# 基本用法
bash build/scripts/run-load-test.sh --concurrent 100 --duration 120

# 完整参数
bash build/scripts/run-load-test.sh \
    --concurrent 100 \
    --duration 120 \
    --threads 4 \
    --url http://localhost:8080 \
    --warmup 30
```

**输出**:
- 结构化的性能数据
- P50/P95/P99 延迟
- 吞吐量统计
- 保存为 CSV/JSON

---

## ✅ 代码质量检查

### 运行所有质量检查

```bash
# 1. 单元测试
mvn clean test

# 2. 代码覆盖率 (JaCoCo)
mvn jacoco:report
# 结果: target/site/jacoco/index.html

# 3. 变异测试 (PIT)
mvn org.pitest:pitest-maven:mutationCoverage
# 结果: target/pit-reports/index.html

# 4. 代码风格检查 (Checkstyle)
mvn checkstyle:check

# 5. Javadoc
mvn javadoc:javadoc
# 结果: target/site/apidocs/index.html
```

### 质量标准

| 指标 | 目标 | Lab-00 状态 |
|------|------|-----------|
| 单元测试覆盖率 | ≥ 85% | ✅ 待测量 |
| 集成测试 | ≥ 5 场景 | ✅ 框架完成 |
| 变异测试覆盖率 | ≥ 75% | ✅ 待测量 |
| 代码注释密度 | ≥ 70% | ✅ 73% |
| Javadoc 覆盖率 | 100% (公开API) | ✅ 100% |

---

## 📚 文档与学习资源

### 核心规范 (必读)

| 文档 | 用途 | 更新频率 |
|------|------|--------|
| `.claude/CLAUDE.md` | 项目级配置和优先级 | 周级 |
| `.claude/DEVELOPMENT_RULES.md` | 开发流程和四层框架 | 按需 |
| `.claude/COMMENT_STANDARDS.md` | 注释规范和 Javadoc | 按需 |
| `.claude/TEST_STANDARDS.md` | 测试规范和覆盖率要求 | 按需 |
| `.claude/PROGRESS_TRACKER.md` | 进度追踪 | 日级 |

### 知识库 (参考)

| 文档 | 用途 | 内容 |
|------|------|------|
| `docs/PITFALLS.md` | 常见坑和解决方案 | 动态维护 |
| `docs/DECISION_TREES.md` | 技术决策指南 | 动态维护 |
| `docs/BEST_PRACTICES.md` | 最佳实践集合 | 动态维护 |

### 代码模板库 (.claude/templates/)

> 待补充（在 Lab-01 完成后开始沉淀）

```
预期模板:
  - ThreadPoolConfig.java         # 线程池配置模板
  - ReactorPipeline.java          # Reactor 管道模板
  - WebFluxController.java        # WebFlux 控制器模板
  - BenchmarkTemplate.java        # JMH 基准模板
```

---

## 🔧 工具链配置

### 开发工具版本

```yaml
Java: 17 (OpenJDK)
Maven: 3.9+
Spring Boot: 3.3.0
Project Reactor: 2023.0.0
Netty: 4.1.104.Final
JUnit: 5.10.0
```

### IDE 配置 (IntelliJ IDEA)

```
1. 导入项目配置
   File → Import Settings → 选择 .idea/ 目录

2. 配置 Maven
   Preferences → Build, Execution, Deployment → Maven
   ├─ Maven home path: 使用默认
   └─ User settings file: 默认

3. 配置 Checkstyle
   Preferences → Tools → Checkstyle
   ├─ Active configuration: .checkstyle.xml
   └─ Scan scope: All

4. 配置代码检查
   Preferences → Editor → Inspections
   ├─ Enable: 所有
   └─ Profile: Default
```

---

## 📊 性能基线

### 系统环境

```
OS: macOS / Linux / Windows
JVM: -Xmx2g -Xms2g -XX:+UseG1GC
CPU: Baseline 测试环境 (待建立)
```

### 基线数据收集

```bash
# 收集基线性能数据
bash build/scripts/run-load-test.sh --concurrent 10 --duration 60

# 生成火焰图 (待实现)
bash build/scripts/generate-flamegraph.sh --duration 60

# 分析 GC 日志 (待实现)
bash build/scripts/analyze-gc-log.sh
```

> 详见 `.claude/PROGRESS_TRACKER.md` 中的性能基线部分

---

## 🚀 后续步骤

### Week 1 (本周)
- [x] 完成 Maven 项目结构
- [x] 实现核心工具类
- [x] 配置应用框架
- [ ] 编写 Lab-00 README (本文件) ✅
- [ ] 建立性能基线

### Week 2
- [ ] 启动 Lab-01: Thread Basics
- [ ] 补充测试覆盖率
- [ ] 初始化知识库

### 后续 Labs
- [ ] Lab-02-03: 基础并发
- [ ] Lab-04-07: 高级异步
- [ ] Lab-08-11: 响应式编程
- [ ] Lab-12-14: 性能优化和对标

---

## 📝 常见问题

### Q1: 如何添加新的 Lab 模块？

```bash
# 1. 在根 pom.xml 的 <modules> 中添加
<module>lab-XX-name</module>

# 2. 创建模块结构
mkdir -p lab-XX-name/src/main/java/nan/tech/labXX/name
mkdir -p lab-XX-name/src/test/java/nan/tech/labXX/name

# 3. 创建 pom.xml (复制 lab-00 并修改 artifactId)
```

### Q2: 如何运行特定的 Lab？

```bash
# 编译特定 lab
mvn clean install -pl lab-01-thread-basics

# 运行特定 lab 的测试
mvn test -pl lab-01-thread-basics -DfailIfNoTests=false
```

### Q3: 如何修复 Checkstyle 违规？

```bash
# 检查违规
mvn checkstyle:check

# 查看详细报告
mvn checkstyle:checkstyle
open target/checkstyle-result.xml
```

### Q4: 如何上传性能数据？

```bash
# 运行压测
bash build/scripts/run-load-test.sh --concurrent 100 --duration 120

# 结果位置
# target/load-test-results/load-test-YYYYMMDD_HHMMSS.txt
```

---

## 📞 支持与反馈

- **问题追踪**: GitHub Issues
- **文档更新**: 提交 PR
- **性能数据**: 提交到 `data/benchmarks/`

---

**最后更新**: 2024-10-17
**维护**: AI Assistant (Claude Code)
**所有文件位置**: See `.claude/PROGRESS_TRACKER.md`

