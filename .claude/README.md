# .claude 目录 - AI 开发指南总汇

> **精简化原则**: 避免冗余，仅保留必要文件
> **更新频率**: 按需更新
> **大小**: 保持 < 50KB

---

## 📚 核心文件说明

### CLAUDE.md ⭐ (启动必读)
- **用途**: 项目级配置和优先级设定
- **内容**: 工作流程、优先级、快速参考
- **更新**: 按需（一般不变）
- **大小**: ~10KB

### STANDARDS.md ⭐ (编码必读)
- **用途**: 统一的代码、测试、文档、包名规范
- **内容**: Javadoc 格式化、注释、测试、包名规范
- **使用场景**: 编码前参考、review 时检查
- **大小**: ~7KB

### PROGRESS_TRACKER.md ⭐ (日常必看)
- **用途**: 项目进度追踪（人类与AI共同维护）
- **内容**: 完成度、里程碑、待办事项
- **更新**: 每个 Lab 完成后同步
- **大小**: ~2KB

### CONTEXT7_INTEGRATION.md
- **用途**: 技术文档查询集成方案
- **内容**: 自动查询触发条件、查询流程
- **使用场景**: 遇到不确定的技术细节时
- **大小**: ~10KB

---

## 🗂️ 与其他目录的关系

```
.claude/                          (AI 配置与规范)
  ├─ CLAUDE.md                    ← 启动后首先阅读
  ├─ STANDARDS.md                 ← 编码时参考
  ├─ PROGRESS_TRACKER.md          ← 日常更新
  ├─ CONTEXT7_INTEGRATION.md
  └─ templates/                   ← 代码模板库

.serena/memories/                 (Serena Memory 知识库)
  └─ CE_COMPOUNDING_ENGINEERING.md ← 自动沉淀的知识

docs/                             (项目文档)
  ├─ PITFALLS.md                  ← 坑库
  ├─ DECISION_TREES.md            ← 决策树
  └─ BEST_PRACTICES.md            ← 最佳实践

build/scripts/                    (自动化脚本)
```

---

## ⚡ 快速启动流程

```bash
# 1. AI 启动时读取 (按顺序)
1. CLAUDE.md          # 了解项目配置
2. PROGRESS_TRACKER.md # 确认当前进度
3. STANDARDS.md       # 加载规范

# 2. 编码前
参考 STANDARDS.md 的对应规范

# 3. Lab 完成后
更新 PROGRESS_TRACKER.md 的进度

# 4. 知识沉淀 (Serena)
自动写入 .serena/memories/
```

---

## 💡 文件整合原则

**旧方式** ❌:
- COMMENT_STANDARDS.md, TEST_STANDARDS.md, DEVELOPMENT_RULES.md...
- 文件数多 → 上下文消耗大 → 启动慢

**新方式** ✅:
- STANDARDS.md (统一) + PROGRESS_TRACKER.md + CLAUDE.md
- 文件数少 → 上下文消耗小 → 启动快
- CE 知识 → .serena/memories/ (Serena 托管)

---

## 📊 .claude 大小监控

| 文件 | 大小 | 目标 | 状态 |
|------|------|------|------|
| CLAUDE.md | ~10KB | ≤ 15KB | ✅ |
| STANDARDS.md | ~7KB | ≤ 10KB | ✅ |
| PROGRESS_TRACKER.md | ~2KB | ≤ 5KB | ✅ |
| CONTEXT7_INTEGRATION.md | ~10KB | ≤ 15KB | ✅ |
| **总计** | **~29KB** | **< 50KB** | ✅ |

---

## 🚀 后续维护规则

1. **不创建新的分散文件** - 规范统一到 STANDARDS.md
2. **不存储暂时性文档** - 如总结、报告等
3. **不重复内容** - 通过链接引用
4. **CE 知识 → .serena/memories** - 由 Serena 自动管理

---

**最后更新**: 2024-10-17
**维护**: AI Assistant
**原则**: Simple is Beautiful ✨

