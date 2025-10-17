# java-concurrency-reactive-lab 项目级 CLAUDE 配置

> **目标**：为 AI 驱动的 java-concurrency-reactive-lab 项目提供统一的开发规范、工作流、工具链配置

---

## 1. 项目基本信息

```yaml
project_name: java-concurrency-reactive-lab
project_type: Educational Learning Lab
language: Java 17
build_system: Maven 3.9+
frameworks:
  - Spring Boot 3.3.x
  - Project Reactor
  - Netty 4.1+
  - R2DBC

organization: Personal Learning
objectives:
  - 系统掌握 Java 线程与并发
  - 理解异步编程与响应式编程
  - 掌握性能分析与优化方法
  - 形成可复现的对标实验体系

estimated_duration: 8 weeks
quality_bar: Production-Grade Learning Content
```

---

## 2. AI 工作模式与优先级

### 2.1 开发优先级（按重要性排序）

```
P0 (必须)
  ├─ 代码质量: 线程安全 + 异常处理 + 资源释放
  ├─ 单元测试: 有意义的业务逻辑测试 (非为覆盖率而测试)
  ├─ demo 自启动: 清晰的演示场景 + Javadoc 注释 ≥ 70%
  ├─ 文档完整: README + Javadoc + 注释达标
  ├─ 进度跟踪: 实时更新 PROGRESS_TRACKER.md
  └─ 知识沉淀: 坑库 + 模板库 + 脚本库更新

P1 (重要)
  ├─ 可视化调试: UI 界面辅助 (JProfiler/Grafana)
  ├─ 对标数据: 完整的性能数据 + 火焰图
  ├─ 教学价值: 丰富的注释 + 示例 + 解释
  └─ 最佳实践: 在代码中示范 + 文档化

P2 (可选)
  ├─ 进阶拓展: Kafka/gRPC/虚拟线程等
  ├─ 自动化增强: CI/CD 流程 + 报告生成
  └─ 社区化: 文档美化 + 示例精选
```

### 2.2 核心工作流

```
启动开发 (start of day):
  1. 读取 .claude/PROGRESS_TRACKER.md
  2. 确认当前阶段与 blockers
  3. 加载相关模板库
  4. 准备待办清单

开发执行 (implementation):
  1. 按 DEVELOPMENT_RULES.md 第8.1节流程
  2. 代码实现 → 注释 → 测试 → 文档 → 质检
  3. 每步都要遵循对应规范

收工汇报 (end of day):
  1. 更新进度文档
  2. 记录已知问题 + blockers
  3. 沉淀新发现的经验
  4. 确认下日计划

定期复盘 (weekly):
  1. 统计周度成果
  2. 评估与计划的偏差
  3. 更新模板库与经验库
  4. 准备周度总结
```

---

## 3. 规范文件映射

```
├─ .claude/STANDARDS.md              [必读] 统一的代码、测试、文档、包名规范
│  └─ 使用场景: 编码前参考、review 时检查
│
├─ .claude/PROGRESS_TRACKER.md       [每日参考] 项目进度、里程碑、快速参考
│  └─ 更新频率: 每个 lab 完成后立即更新
│
├─ .claude/CONTEXT7_INTEGRATION.md   [参考] 技术文档查询集成方案
│
├─ .claude/templates/                [模板库] 代码模板 (复用)
│  └─ 由各 Lab 贡献，后续补充
│
└─ docs/PITFALLS.md                  [动态维护] 常见坑集合 (持续补充)
   docs/DECISION_TREES.md            [参考] 背压/GC/池参数决策指南
   docs/BEST_PRACTICES.md            [参考] 最佳实践集合
```

---

## 4. 开发规范速查

**查看 `.claude/STANDARDS.md` 获取完整规范** ⬅️

快速检查清单:
- 代码质量: 线程安全、异常处理、资源释放
- 注释规范: Javadoc 格式化、注释密度 ≥ 70%
- 单元测试: 有意义的业务逻辑测试（非为覆盖率而测试）
- demo 自启动: 提供 main() 方法支持教学演示
- 包名规范: `nan.tech.*` 体系
- 文档完整: README + Javadoc 达标

---

## 5. Compounding Engineering (CE) - 复利工程

**查看 `.serena/memories/CE_COMPOUNDING_ENGINEERING.md`** ⬅️

CE 五库体系 (由 Serena Memory 自动管理):
1. **PITFALLS_KNOWLEDGE.md** - 常见坑库
2. **TEMPLATES_REGISTRY.md** - 模板库清单
3. **DECISION_TREES_LOG.md** - 决策树库
4. **SCRIPTS_TOOLKIT.md** - 脚本库
5. **BEST_PRACTICES_COMPENDIUM.md** - 最佳实践库

自动化流程:
- Lab 完成时: 提取新经验 → write_memory
- Lab 启动时: 加载相关知识 → read_memory
- 目标复用率: ≥ 70% | 速度提升: 40%+

---

## 6. 工具链与环保

### 6.1 必需工具

```
开发工具:
  ✅ JDK 17+
  ✅ Maven 3.9+
  ✅ Git
  ✅ IDE (IntelliJ IDEA 推荐)

测试工具:
  ✅ JUnit 5
  ✅ AssertJ
  ✅ Awaitility (异步测试)
  ✅ Testcontainers (集成测试)
  ✅ PIT (变异测试)

性能工具:
  ✅ JMH (基准测试)
  ✅ async-profiler (火焰图)
  ✅ JFR (Java Flight Recorder)
  ✅ JProfiler / YourKit (可视化)

压测工具:
  ✅ hey / wrk / k6 (选一个)
  ✅ Apache JMeter (可选)

可观测工具:
  ✅ Prometheus (指标收集)
  ✅ Grafana (仪表板)
  ✅ Logback + MDC (日志)
  ✅ OpenTelemetry (可选)
```

### 6.2 快速启动命令

```bash
# 1. 环境验证
java -version
mvn -v
git --version

# 2. 项目构建
cd java-concurrency-reactive-lab
mvn clean install -DskipTests

# 3. 运行示例
cd lab-01-thread-basics
mvn spring-boot:run

# 4. 运行测试 (含覆盖率)
mvn clean test jacoco:report
open target/site/jacoco/index.html

# 5. 运行压测
build/scripts/run-load-test.sh --concurrent 100 --duration 120

# 6. 生成火焰图
build/scripts/generate-flamegraph.sh

# 7. 生成 Javadoc
mvn javadoc:javadoc
open target/site/apidocs/index.html
```

---

## 7. 调试与问题解决

### 7.1 常见问题快速参考

| 问题 | 查询位置 | 解决方案 |
|------|---------|---------|
| 不知道如何开始 | PROGRESS_TRACKER.md 5. 快速参考 | 按流程运行 |
| 代码线程安全问题 | PITFALLS.md "线程安全" | 参考已知解决 |
| 不知道咋写注释 | COMMENT_STANDARDS.md | 选择对应模板 |
| 压测数据异常 | docs/DIAGNOSIS_TOOLKIT.md | 用诊断工具 |
| 覆盖率不达标 | TEST_STANDARDS.md 4.2 | 补充测试 |
| 背压策略不知咋选 | docs/DECISION_TREES.md | 按决策树 |

### 7.2 性能诊断工具链

```
现象: 系统响应慢
诊断步骤:
  1. 用 GC 日志发现: 是否 Full GC 频繁 (GCEasy)
  2. 用 async-profiler 生成火焰图: 找 CPU 热点
  3. 用 JFR 看: 线程状态、IO 等待、Lock 竞争
  4. 用 jstack: 确认是否死锁/饥饿

现象: 内存持续增长
诊断步骤:
  1. jmap dump: jmap -dump:live,format=b,file=heap.bin $PID
  2. MAT 分析: 找泄漏根对象
  3. JFR allocation profiling: 找高频分配

现象: 线程数暴增
诊断步骤:
  1. jstack $PID | grep "tid" | wc -l
  2. 看最近的线程创建来源 (线程名)
  3. 检查线程池是否配置错误或有泄漏
```

---

## 8. 每日工作检查清单

### 启动前 (1-2 分钟)

```
□ 阅读 PROGRESS_TRACKER.md 当前状态
□ 确认当前阶段是什么 (准备/M1/M2/M3/M4)
□ 检查 blockers 和"已知问题"
□ 加载该 lab 的模板库
```

### 工作期间 (持续)

```
□ 每写完 100 行代码，检查一次注释密度
□ 每完成一个方法，运行相关测试
□ 每发现新坑，记录到"已知问题"
□ 每 1 小时检查一次进度，确保不偏离计划
```

### 收工前 (5-10 分钟)

```
□ 运行所有测试 (单元 + 集成 + 变异)
□ 检查覆盖率是否 ≥ 85%
□ 更新 README 中的进度追踪
□ 更新本 lab 的"已知问题"与"下一步"
□ 记录新发现的坑 → PITFALLS.md
□ 评估是否有新模板可沉淀 → templates/
```

---

## 9. 与用户协作的规范

### 9.1 进度同步点

```
周一: 本周规划
  - 目标: lab-XX 的具体产出清单

周中 (周三): 进度检查
  - 评估: 是否按计划进行
  - 调整: 是否需要改进方向

周五: 周度总结
  - 成果: 完成的内容与质量指标
  - 问题: 遇到的困难与解决方案
  - 计划: 下周的调整
```

### 9.2 质量评审

```
每个 M(里程碑) 完成后:
  1. 代码审查 (用检查清单)
  2. 文档审查 (README + Javadoc + 注释)
  3. 测试审查 (覆盖率 + 集成测试)
  4. 对标数据审查 (完整性与可靠性)
  5. 知识沉淀审查 (经验库更新)

质量分数计算:
  代码质量: 40% (线程安全/异常处理/资源释放)
  测试覆盖: 20% (覆盖率 + 场景完整度)
  文档完整: 25% (README + Javadoc + 注释)
  学习价值: 15% (教学注释 + 示例 + 解释)

  目标: ≥ 90 分
```

---

## 10. 快速命令参考

```bash
# 新启动一个 lab
cd lab-0X-name
cat README.md  # 理解需求
mvn clean test # 运行基础测试

# 代码质量检查
mvn clean test jacoco:report  # 覆盖率
mvn org.pitest:pitest-maven:mutationCoverage  # 变异测试

# 压测与性能分析
build/scripts/run-load-test.sh --concurrent 100 --duration 120
build/scripts/generate-flamegraph.sh  # 火焰图
build/scripts/generate-report.sh  # 生成报告

# 文档生成
mvn javadoc:javadoc

# 进度更新
# 手动编辑 PROGRESS_TRACKER.md

# 知识沉淀
# 编辑 docs/PITFALLS.md, docs/DECISION_TREES.md, docs/BEST_PRACTICES.md
```

---

## 11. 成功标准

### 项目完成标准

```
✅ 代码质量
  - 所有 14 个 lab 的覆盖率 ≥ 85%
  - 所有 lab 的线程安全检查通过
  - 所有 lab 的资源管理规范

✅ 文档完整
  - 每个 lab 的 README 完整度 ≥ 90%
  - Javadoc 覆盖率 100% (公开 API)
  - 代码注释密度 ≥ 70%

✅ 教学价值
  - 每个 lab 的丰富注释能够独立讲解
  - 至少 5 个"常见坑"案例有完整的演示代码
  - 对标实验产出有说服力的数据与分析

✅ 知识复用
  - 建立 ≥ 30 个可复用的代码模板
  - 沉淀 ≥ 50+ 个已知坑与解决方案
  - 创建 ≥ 10 个自动化脚本

✅ 项目管理
  - 进度追踪完整，无遗漏
  - 每个 lab 都有清晰的里程碑
  - 所有 M(里程碑) 都按期完成
```

---

**最后**：
- 这份 CLAUDE.md 是项目的"北极星"
- 遇到决策困境时，参考它的优先级和规范
- 定期 (每周) 审视与更新本文件

🚀 **Ready to start!**