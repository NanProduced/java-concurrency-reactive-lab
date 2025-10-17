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

**更新触发**: Lab 完成时扫描新发现的坑

### TEMPLATES_REGISTRY.md
代码模板库清单 - 每个模板记录：
- 位置: `.claude/templates/TemplateX.java`
- 功能: 模板的用途
- 适用 Lab: 适用范围
- 复用率: 后续使用统计

**更新触发**: 沉淀新模板时

### DECISION_TREES_LOG.md
决策树库 - 记录决策场景：
- 背压策略选择
- GC 选择建议
- 线程池参数计算
- 异步方案对比

**更新触发**: 发现新的决策模式时

### SCRIPTS_TOOLKIT.md
自动化脚本库 - 脚本清单：
- 脚本名: `build/scripts/script.sh`
- 功能: 脚本的用途
- 参数: 标准参数说明
- 输出: 输出格式

**更新触发**: 创建新脚本时

### BEST_PRACTICES_COMPENDIUM.md
最佳实践集合 - 每个实践包括：
- 名称: 实践名称
- 场景: 适用场景
- 代码示例: 示范代码
- 效果评估: 性能/质量提升

**更新触发**: 发现新的最佳实践时

---

## 2. CE 自动化流程

### Lab 完成时 (write_memory)
```
1. 提取新坑 → PITFALLS_KNOWLEDGE.md
2. 注册新模板 → TEMPLATES_REGISTRY.md
3. 记录新决策 → DECISION_TREES_LOG.md
4. 注册新脚本 → SCRIPTS_TOOLKIT.md
5. 记录新实践 → BEST_PRACTICES_COMPENDIUM.md
```

### Lab 启动时 (read_memory)
```
1. 加载已知坑 → 避免重复
2. 加载模板清单 → 决定复用方案
3. 加载决策树 → 快速决策
4. 加载脚本工具 → 了解自动化方案
5. 加载最佳实践 → 指导设计
```

---

## 3. CE 评估指标

### 复用率
后续 lab 中使用前期沉淀的模板/决策/脚本的比例
- 目标: ≥ 70%

### 开发速度
(第 1-3 lab 平均用时) vs (第 11-14 lab 平均用时)
- 目标: 后续快 40% 以上

### 文档完整度
每个 lab 的文档分数 (README + Javadoc + 注释)
- 目标: ≥ 90 分

### 知识沉淀量
每个 lab 贡献的新模板、新坑、新脚本数
- 目标: 14 个 lab → ≥ 30 个模板 + 50+ 个坑

---

## 4. 使用规范

### 何时升级为模板
1. 完成一个 lab 后，总结其中的"标准做法"
2. 如果该做法在多个地方适用 → 升级为模板
3. 下个 lab 启动前从模板库加载

### 何时创建新脚本
1. 发现重复的手工操作 → 自动化为脚本
2. 脚本应该是"一条命令完成"的操作
3. 脚本应该有清晰的帮助信息 (--help)

### 何时记录决策树
1. 发现新的技术决策模式
2. 有多个选项需要对比的场景
3. 决策有明确的判断标准

---

## 5. Memory 内容示例

### PITFALLS 格式
```
## 坑 ID: PITFALL-XXX
**Lab**: lab-XX-name
**发现日期**: 2024-XX-XX

### 现象
用户遇到的具体表现

### 根因分析
深层原因分析

### 修复方案
解决办法

### 代码位置
relevant/file/path.java:123
```

### TEMPLATES 格式
```
| 模板名 | 位置 | 功能 | 适用 Lab | 复用率 |
|--------|------|------|---------|--------|
| ThreadPoolConfig | .claude/templates/... | 线程池配置 | lab-03,04,07,10 | 40% |
```

---

## 6. Memory 生命周期

```
Lab 完成
  ↓
提取经验 (分析代码、测试、文档)
  ↓
write_memory 到 5 库 (自动化)
  ↓
Memory 积累
  ↓
下个 Lab 启动
  ↓
read_memory 加载相关知识
  ↓
复用模板、避免已知坑、快速决策
  ↓
循环...
```

---

**这是 Serena Memory 管理的知识库，由 AI 自动维护**

最后更新: 2024-10-17

