# Compounding Engineering (CE) 复利工程知识库

> **目标**: 使用 Serena Memory 记录与管理知识沉淀
> **存放位置**: `.serena/memories/` (Serena 托管)
> **自动化**: 每个 Lab 完成后自动沉淀

---

## 1. CE 五库体系

### PITFALLS_KNOWLEDGE.md
常见坑知识库 - 每个坑包含：
- 现象: 什么问题会出现
- 根因: 为什么会这样
- 修复: 如何解决
- 代码位置: 在哪个 Lab 中

**最新发现** (2025-10-21):
- **项目级通用陷阱**: Benchmark 类放在 src/main/java 导致 JAR 膨胀 + JMH API 类型转换错误
  - Lab-06/Lab-07 都踩过这个坑
  - 迁移操作已标准化
  - 常见编译错误（TimeValue vs TimeUnit）已文档化

**更新触发**: Lab 完成时扫描新发现的坑

### TEMPLATES_REGISTRY.md
代码模板库清单 - 每个模板记录：
- 位置: `.claude/templates/TemplateX.java`
- 功能: 模板的用途
- 适用 Lab: 适用范围
- 复用率: 后续使用统计

**可沉淀的新模板**:
- BenchmarkRunner 模板（JMH 配置最佳实践）
- 正确的 OptionsBuilder 链式调用模式

**更新触发**: 沉淀新模板时

### DECISION_TREES_LOG.md
决策树库 - 记录决策场景：
- 源码目录结构选择（main vs test）
- JMH 时间参数配置（TimeValue vs TimeUnit）
- 背压策略选择
- GC 选择建议
- 线程池参数计算
- 异步方案对比

**最新决策**:
- Benchmark/Test 类放置：100% 必须在 src/test/java（无例外）
- JMH 时间参数：始终使用 TimeValue，不要用 TimeUnit

**更新触发**: 发现新的决策模式时

### SCRIPTS_TOOLKIT.md
自动化脚本库 - 脚本清单：
- 脚本名: `build/scripts/script.sh`
- 功能: 脚本的用途
- 参数: 标准参数说明
- 输出: 输出格式

**可沉淀脚本**:
- `migrate-benchmark-to-test.sh` - 自动将 benchmark 类从 main 移到 test

**更新触发**: 创建新脚本时

### BEST_PRACTICES_COMPENDIUM.md
最佳实践集合 - 每个实践包括：
- 名称: 实践名称
- 场景: 适用场景
- 代码示例: 示范代码
- 效果评估: 性能/质量提升

**最新最佳实践**:
- **项目结构规范**: lab-XX/src/[main|test]/java/nan/tech/labXX/{core,utils,config,benchmark}
  - 生产 JAR 减重：benchmark 代码占用 0.5-3MB（取决于演示代码量）
  - 部署时依赖简化：无需打包 JMH
  
- **JMH 配置模式**: 三元运算符实现条件选择
  - 避免中间变量导致的类型转换问题
  - 一次性链式调用构建 Options

**更新触发**: 发现新的最佳实践时

---

## 2. CE 自动化流程

### Lab 完成时 (write_memory)
```
1. 提取新坑 → PITFALLS_KNOWLEDGE.md
   ✅ 2025-10-21: 项目级 benchmark 放置规范
   
2. 注册新模板 → TEMPLATES_REGISTRY.md
   ⏳ BenchmarkRunner.java（JMH 模板）
   ⏳ OptionsBuilder 正确用法模板
   
3. 记录新决策 → DECISION_TREES_LOG.md
   ✅ Benchmark 放置决策（main vs test）
   
4. 注册新脚本 → SCRIPTS_TOOLKIT.md
   ⏳ 自动化 benchmark 迁移脚本
   
5. 记录新实践 → BEST_PRACTICES_COMPENDIUM.md
   ✅ 项目结构规范
   ✅ JMH 配置最佳实践
```

### Lab 启动时 (read_memory)
```
1. 加载已知坑 → 避免重复
   - 检查是否有 benchmark 类在 main 中
   
2. 加载模板清单 → 决定复用方案
   - 如果需要性能测试，直接用 BenchmarkRunner 模板
   
3. 加载决策树 → 快速决策
   - 源码目录：确认 benchmark 在 test 中
   - JMH 时间参数：使用 TimeValue
   
4. 加载脚本工具 → 了解自动化方案
   - 无需手动移动文件
   
5. 加载最佳实践 → 指导设计
   - 参考项目结构规范
   - 参考 JMH 配置最佳实践
```

---

## 3. CE 评估指标

### 复用率
后续 lab 中使用前期沉淀的模板/决策/脚本的比例
- 目标: ≥ 70%
- **当前**: lab-06/lab-07 已应用 benchmark 放置规范 (100%)

### 开发速度
(第 1-3 lab 平均用时) vs (第 11-14 lab 平均用时)
- 目标: 后续快 40% 以上
- **当前**: benchmark 迁移 + 编译错误修复 < 10 分钟（已标准化）

### 文档完整度
每个 lab 的文档分数 (README + Javadoc + 注释)
- 目标: ≥ 90 分

### 知识沉淀量
每个 lab 贡献的新模板、新坑、新脚本数
- 目标: 14 个 lab → ≥ 30 个模板 + 50+ 个坑
- **当前**: 
  - 已沉淀坑: 1 个项目级通用陷阱
  - 待沉淀模板: 2 个（BenchmarkRunner + OptionsBuilder）
  - 待沉淀脚本: 1 个（benchmark 迁移自动化）

---

## 4. 使用规范

### 何时升级为模板
1. 完成一个 lab 后，总结其中的"标准做法"
2. 如果该做法在多个地方适用 → 升级为模板
3. 下个 lab 启动前从模板库加载

**案例**: BenchmarkRunner 已在 lab-06 和 lab-07 使用，符合升级条件

### 何时创建新脚本
1. 发现重复的手工操作 → 自动化为脚本
2. 脚本应该是"一条命令完成"的操作
3. 脚本应该有清晰的帮助信息 (--help)

**案例**: Benchmark 迁移操作（mkdir + mv + rmdir + mvn compile）应自动化

### 何时记录决策树
1. 发现新的技术决策模式
2. 有多个选项需要对比的场景
3. 决策有明确的判断标准

**案例**: Benchmark 放置决策（main vs test）有清晰的判断标准

---

## 5. Memory 内容示例

### PITFALLS 格式
```
## 坑 ID: PITFALL-PROJECT-001
**发现地点**: lab-06-bio-nio, lab-07-netty
**发现日期**: 2025-10-21

### 现象
生产 JAR 膨胀，包含测试代码；编译错误（类型转换问题）

### 根因分析
1. Benchmark 类放在 src/main 中
2. JMH API 链式调用返回 ChainedOptionsBuilder，类型不兼容
3. TimeUnit vs TimeValue 混淆

### 修复方案
1. 将 benchmark 移到 src/test
2. 使用 TimeValue 而非 TimeUnit
3. 一次性链式调用构建 Options

### 代码位置
- lab-06-bio-nio/src/test/java/nan/tech/lab06/benchmark/
- lab-07-netty/src/test/java/nan/tech/lab07/benchmark/
```

### TEMPLATES 格式
```
| 模板名 | 位置 | 功能 | 适用 Lab | 复用率 |
|--------|------|------|---------|--------|
| BenchmarkRunner | .claude/templates/BenchmarkRunner.java | JMH 配置 | lab-06,07,... | 正在升级 |
| OptionsBuilder | .claude/templates/JMHConfig.java | JMH Options 构建 | lab-06,07,... | 正在升级 |
```

---

## 6. 多 Lab 间的经验复用

### Lab-06 → Lab-07 验证
- ✅ Benchmark 放置规范：lab-07 自动应用，无错误
- ✅ 迁移流程：完全一致，代码复用 100%
- ✅ 编译错误修复：lab-07 从 lab-06 经验中学到，TimeValue 问题快速定位

### 预期：Lab-08 及以后
- 如果有 benchmark，自动应用规范（无需提醒）
- 如果需要 JMH 配置，直接从模板库复用
- 如果遇到类型错误，快速查阅已知问题库

---

## 7. Memory 生命周期

```
Lab 完成 (e.g., Lab-07)
  ↓
提取经验 (分析代码、测试、文档)
  - 识别：benchmark 放置规范（可复用）
  - 识别：JMH 编译错误修复（可复用）
  - 识别：迁移操作流程（可自动化）
  ↓
write_memory 到 5 库 (2025-10-21 已完成)
  - PITFALLS_KNOWLEDGE: ✅ 记录坑点
  - DECISION_TREES_LOG: ✅ 记录决策
  - TEMPLATES_REGISTRY: ⏳ 待升级为模板
  - SCRIPTS_TOOLKIT: ⏳ 待创建脚本
  - BEST_PRACTICES_COMPENDIUM: ✅ 记录最佳实践
  ↓
Memory 积累 (持续)
  - 当前库存: 1 坑 + 1 决策 + 2 实践
  ↓
Lab-08+ 启动时
  ↓
read_memory 加载相关知识
  - 自动应用 benchmark 规范
  - 自动选择 JMH 最佳实践
  - 快速避免已知坑点
  ↓
复用率评估 (预期 ≥ 70%)
  ↓
循环...
```

---

**这是 Serena Memory 管理的知识库，由 AI 自动维护**

最后更新: 2025-10-21
更新者: Claude Code
更新内容: 添加项目级 benchmark 放置规范的复利经验
