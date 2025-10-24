# Phase 1: 基础操作符演示

## 📌 概述

本目录包含 Lab-10 Phase 1 的基础操作符演示，涵盖30+ 个常用操作符，重点是：
1. 深度理解转换、过滤、组合操作符的本质
2. 掌握不同操作符的用途和应用场景
3. 理解操作符之间的区别（如 flatMap vs concatMap vs switchMap）

## 📂 文件结构

```
operators/
├── TransformOperatorsDemo.java   # 转换操作符演示 (10个demo)
├── FilterOperatorsDemo.java      # 过滤操作符演示 (10个demo)
├── CombineOperatorsDemo.java     # 组合操作符演示 (10个demo)
└── README.md                     # 本文件
```

## 🎯 操作符分类

### 1️⃣ 转换操作符（Transform）

**定义**：改变流中元素的形状、类型或值的操作符。

#### 类型对比

| 操作符 | 输入输出 | 特点 | 用途 |
|--------|--------|------|------|
| `map(T -> R)` | 1→1 | 同步转换 | 元素值转换 |
| `flatMap(T -> Flux<R>)` | 1→N | 异步，并发 | 异步链式调用 |
| `concatMap(T -> Flux<R>)` | 1→N | 异步，顺序 | 异步链式，保证顺序 |
| `switchMap(T -> Flux<R>)` | 1→N | 异步，切换 | 最新优先，取消前面 |
| `scan(acc, (a,b)->a)` | N→N | 累积，发送中间 | 累加、累计 |
| `reduce(acc, (a,b)->a)` | N→1 | 累积，只发送最后 | 求和、求积等 |
| `cast(Class<R>)` | 1→1 | 类型转换 | 强制类型转换 |
| `then(Mono)` | 0→1 | 忽略元素 | 顺序执行 |
| `thenMany(Flux)` | 0→N | 忽略元素 | 顺序连接 |

#### 演示详解

**demo1: map() - 1对1转换**
```java
Flux.range(1, 5)
    .map(x -> x * 10)
    .subscribe(...);  // 输出：10, 20, 30, 40, 50
```

**demo2: map() 链式调用**
```java
Flux.range(1, 3)
    .map(x -> x * 2)      // 2, 4, 6
    .map(x -> x + 10)     // 12, 14, 16
    .map(x -> "val:" + x) // "val:12", "val:14", "val:16"
```

**demo3: flatMap() - 1对N异步转换**
```java
Flux.range(1, 3)
    .flatMap(x -> Flux.just(x*10, x*10+1, x*10+2))
    // 输出（顺序不定）：10,11,12,20,21,22,30,31,32
```

**demo4: concatMap() vs flatMap()**
```
flatMap：并发处理
  sub1 → [10,11,12]  ┐
  sub2 → [20,21,22]  ├─ 并发发送，顺序不定
  sub3 → [30,31,32]  ┘

concatMap：顺序处理
  sub1 → [10,11,12] 完成 →
  sub2 → [20,21,22] 完成 →
  sub3 → [30,31,32]
  输出顺序固定：10,11,12,20,21,22,30,31,32
```

**demo5: switchMap() - 最新优先**
```
新元素到来时，取消前面的流
应用场景：
  - 用户搜索框输入
  - 每次输入都搜索新的内容
  - 前面未完成的搜索被取消
```

**demo6: scan() - 累积，发送中间结果**
```java
Flux.range(1, 5)
    .scan(0, (acc, x) -> acc + x)
    // 输出：1, 3, 6, 10, 15（每步的累加和）
```

**demo7: reduce() - 累积，只发送最后结果**
```java
Flux.range(1, 5)
    .reduce(0, (acc, x) -> acc + x)
    // 输出：15（Mono，只有一个结果）
```

### 2️⃣ 过滤操作符（Filter）

**定义**：根据条件保留、限制或选择流中的元素。

#### 类型对比

| 操作符 | 功能 | 输出 | 用途 |
|--------|------|------|------|
| `filter(predicate)` | 条件过滤 | 满足条件的元素 | 筛选数据 |
| `distinct()` | 全局去重 | 不重复的元素 | 去重（大数据慎用） |
| `distinctUntilChanged()` | 相邻去重 | 不重复的相邻元素 | 去重（内存高效） |
| `take(n)` | 只取前N个 | 前N个元素 | 限制数量 |
| `skip(n)` | 跳过前N个 | 剩余元素 | 分页/偏移 |
| `first()` | 只取第一个 | 第一个元素(Mono) | 取单个 |
| `last()` | 只取最后一个 | 最后一个元素(Mono) | 取单个 |
| `elementAt(n)` | 取第n个 | 第n个元素(Mono) | 取指定位置 |
| `timeout(Duration)` | 超时控制 | 超时前的元素 | 超时控制 |

#### 演示详解

**demo1: filter() - 条件过滤**
```java
Flux.range(1, 10)
    .filter(x -> x % 2 == 0)  // 只保留偶数
    // 输出：2, 4, 6, 8, 10
```

**demo2: filter() 链式调用**
```java
Flux.range(1, 10)
    .filter(x -> x % 2 == 0)  // 偶数：2,4,6,8,10
    .filter(x -> x > 4)       // 大于4：6,8,10
```

**demo3: distinct() - 全局去重**
```java
Flux.just(1, 2, 2, 3, 2, 4, 3, 5)
    .distinct()
    // 输出：1, 2, 3, 4, 5
    // 注意：需要记住所有元素，内存占用高
```

**demo4: distinctUntilChanged() - 相邻去重**
```java
Flux.just(1, 2, 2, 3, 2, 4, 3, 3)
    .distinctUntilChanged()
    // 输出：1, 2, 3, 2, 4, 3
    // 注意：只记住前一个元素，内存占用低
```

**demo5-7: take/skip/pagination**
```
take(n) - 只取前n个
skip(n) - 跳过前n个
take(n).skip(m) - 分页：第 (m/n)+1 页，每页n个

示例：
  Flux.range(1, 20)
    .skip(5)    // 跳过1-5
    .take(5)    // 取6-10
    // 输出：6,7,8,9,10（第2页）
```

### 3️⃣ 组合操作符（Combine）

**定义**：将多个流组合为一个流。

#### 类型对比

| 操作符 | 订阅方式 | 顺序 | 用途 |
|--------|---------|------|------|
| `merge(...)` | 并发 | 不定 | 多源并发 |
| `concat(...)` | 顺序 | 固定 | 多源串联 |
| `zip(...)` | 并发 | 配对 | 等长配对 |
| `combineLatest(...)` | 并发 | 最新 | 多输入组合 |
| `withLatestFrom(...)` | 非对称 | 驱动流 | 主流+副流 |
| `startWith(...)` | 前置 | 固定 | 添加前置元素 |
| `defaultIfEmpty(...)` | 降级 | 单个 | 空流处理 |
| `switchIfEmpty(...)` | 降级 | 替换 | 流替换 |

#### 演示详解

**demo1: merge() - 并发组合**
```
Flux 1: 1, 2, 3
Flux 2: 10, 20, 30
merge(flux1, flux2)
输出（不定序）：1, 10, 2, 20, 3, 30

特点：
- 并发订阅两个Flux
- 元素交错发送
- 最快的决定速度
```

**demo2: concat() - 顺序组合**
```
Flux 1: 1, 2, 3
Flux 2: 10, 20, 30
concat(flux1, flux2)
输出（固定序）：1, 2, 3, 10, 20, 30

特点：
- 等待flux1完成
- 然后启动flux2
- 顺序保证
```

**demo3: zip() - 配对组合**
```
Flux A: 1, 2, 3
Flux B: a, b, c
zip(A, B)
输出：(1,a), (2,b), (3,c)

特点：
- 等待两个Flux都有新元素
- 形成元组
- 较慢的决定速度
```

**demo4: combineLatest() - 最新组合**
```
Timeline:
Flux A: ----1----2----3|
Flux B: ------a----b----c|

combineLatest(A, B)
时序：
  - 0ms: A产生1，但B还没有元素，等待
  - 60ms: B产生a，现在两个都有，发送(1,a)
  - 100ms: A产生2，发送(2,a)
  - 140ms: B产生b，发送(2,b)
  - 180ms: A产生3，发送(3,b)
  - 220ms: B产生c，发送(3,c)
  - 260ms: A完成，B完成，流结束

输出：(1,a), (2,a), (2,b), (3,b), (3,c)
```

**demo5: withLatestFrom() - 非对称组合**
```
Main: 1, 2, 3
Side: a, b, c
main.withLatestFrom(side)
输出：(1,a), (2,b), (3,c)

特点：
- Main流驱动，每个元素发送一次
- 使用Side的最新元素
- Side的更新不会触发输出
- 常用于：主流事件 + 副流上下文
```

**demo6: startWith() - 前置元素**
```java
Flux.just(2, 3, 4)
    .startWith(1)
    // 输出：1, 2, 3, 4
```

**demo7-8: defaultIfEmpty() / switchIfEmpty()**
```java
// 空流处理
Flux.empty()
    .defaultIfEmpty(99)  // 输出：99

// 流替换
Flux.empty()
    .switchIfEmpty(Flux.just(10, 20))  // 输出：10, 20
```

## 🧪 运行演示

### 运行 TransformOperatorsDemo
```bash
mvn compile exec:java \
  -Dexec.mainClass="nan.tech.lab10.operators.TransformOperatorsDemo" \
  -pl lab-10-reactor-core
```

### 运行 FilterOperatorsDemo
```bash
mvn compile exec:java \
  -Dexec.mainClass="nan.tech.lab10.operators.FilterOperatorsDemo" \
  -pl lab-10-reactor-core
```

### 运行 CombineOperatorsDemo
```bash
mvn compile exec:java \
  -Dexec.mainClass="nan.tech.lab10.operators.CombineOperatorsDemo" \
  -pl lab-10-reactor-core
```

## 📊 演示统计

| Demo类 | 演示数 | 覆盖操作符 |
|--------|--------|-----------|
| TransformOperatorsDemo | 10个 | map, flatMap, concatMap, switchMap, scan, reduce, cast, then, thenMany |
| FilterOperatorsDemo | 10个 | filter, distinct, distinctUntilChanged, take, skip, first, last, elementAt |
| CombineOperatorsDemo | 10个 | merge, concat, zip, combineLatest, withLatestFrom, startWith, defaultIfEmpty, switchIfEmpty, flatMapMany, using |
| **总计** | **30个** | **30+ 种操作符** |

## 💡 操作符选择指南

### 场景1：数据转换
**需求**：将数字流 1,2,3 转为 10,20,30

**方案**：使用 `map()`
```java
Flux.range(1, 3)
    .map(x -> x * 10)
    .subscribe(System.out::println);
```

### 场景2：异步链式调用
**需求**：查询用户，然后查询用户的订单

**方案**：使用 `flatMap()`
```java
getUserFlux()
    .flatMap(user -> getOrdersByUser(user.id))
    .subscribe(System.out::println);
```

**对比**：
- `flatMap`：并发查询，快
- `concatMap`：顺序查询，慢但有序
- `switchMap`：每个用户替换前一个，适合搜索框

### 场景3：数据过滤
**需求**：只保留大于10的数字

**方案**：使用 `filter()`
```java
Flux.range(1, 20)
    .filter(x -> x > 10)
    .subscribe(System.out::println);
```

### 场景4：分页
**需求**：第2页，每页10条

**方案**：使用 `skip().take()`
```java
items.skip(10)    // 跳过前10条
     .take(10)    // 取10条
     .subscribe(System.out::println);
```

### 场景5：多源合并
**需求**：同时获取内存缓存和数据库数据

**方案**：使用 `merge()`
```java
Flux.merge(
    getFromCache(),
    getFromDatabase()
).subscribe(System.out::println);
```

### 场景6：用户搜索输入
**需求**：用户每次输入都搜索，前面未完成的搜索取消

**方案**：使用 `switchMap()`
```java
userInput.textChanges()
    .switchMap(text -> search(text))
    .subscribe(System.out::println);
```

## 📚 学习资源

### 官方文档
- [Flux JavaDoc](https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html)
- [Mono JavaDoc](https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Mono.html)

### 相关资料
- [Reactor 操作符指南](https://projectreactor.io/docs/core/release/reference/#which.operator)

## 📋 自检清单

学完本模块，检查以下要点：

- [ ] 理解 map vs flatMap vs concatMap vs switchMap 的区别
- [ ] 掌握何时使用 scan 和 reduce
- [ ] 理解 filter 的单/多条件过滤
- [ ] 掌握 distinct vs distinctUntilChanged 的区别
- [ ] 理解分页的实现（skip+take）
- [ ] 理解 merge/concat/zip 的区别
- [ ] 掌握 combineLatest 和 withLatestFrom 的使用场景
- [ ] 理解 switchIfEmpty 和 defaultIfEmpty 的区别
- [ ] 知道何时使用 startWith
- [ ] 理解 using() 的资源管理机制

## 🎓 完成标准

**知识点掌握**：
- ✅ 转换操作符（9个）理解深度
- ✅ 过滤操作符（8个）理解深度
- ✅ 组合操作符（10个）理解深度
- ✅ 操作符选择能力

**实战能力**：
- ✅ 能独立选择合适操作符解决问题
- ✅ 理解操作符的性能特征
- ✅ 能优化操作符组合

---

**Status**: ✅ Phase 1B - 基础操作符演示（30个demo）
**Next**: Phase 1C - StepVerifier 测试框架
**Quality Target**: ≥90/100

---

*本文档生成于 2025-10-24*
*包含30+ 个可运行的演示程序*
