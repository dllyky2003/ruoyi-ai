# ACLS 模拟系统 - Skill 技能体系完整说明

**版本**: v2.0  
**更新日期**: 2026-09-08  
**适用范围**: ACLS Megacode 院前急救培训模拟系统


## 一、概述

### 1.1 什么是 Skill？

Skill（技能）是 ACLS 模拟系统中**执行单元的最小粒度**。每个 Skill 代表一个可以被 NPC（虚拟团队成员）执行的原子操作或判读行为。

### 1.2 设计原则

| 原则 | 说明 |
| :--- | :--- |
| **配置优先** | 能用 YAML 配置实现的，绝不写 Java 代码 |
| **职责绑定** | 每个 Skill 绑定到具体的 NPC 职责（Duty） |
| **零硬编码** | 所有变量名通过配置注入，不出现硬编码字符串 |
| **可观测** | 每个 Skill 执行后都有明确的变量变化和语音反馈 |


## 二、技能类型总览

| 技能类型 | 用途 | 是否写 Java | 适用场景 |
| :--- | :--- | :--- | :--- |
| `query` | 查询变量并语音播报 | ❌ | 检查结果、报告生命体征 |
| `standard` | 执行固定 VR 动作并记录 | ❌ | 贴电极、开启设备、转运 |
| `generic_assert` | 判读数据并写入结果 | ❌ | QRS 判读、稳定性评估 |
| `generic` | 通用动作 + 多变量输出 | ❌ | 复杂标准操作 |
| `complex` | 复杂业务逻辑 | ✅ | 药物推注、电复律 |


## 三、技能类型详解

### 3.1 QuerySkill（查询技能）

**用途**：从 `CaseContext` 读取变量值，通过 TTS 语音播报，**不修改任何变量**。

**适用场景**：生命体征报告、病史回顾、检查结果查询。

#### 配置字段

| 字段 | 类型 | 必填 | 说明 |
| :--- | :--- | :--- | :--- |
| `id` | string | ✅ | 技能唯一标识 |
| `duty` | enum | ✅ | 执行职责 |
| `type` | string | ✅ | 固定值 `query` |
| `targetVar` | string | ✅ | 要读取的变量名 |
| `responseTemplate` | string | ✅ | 语音模板，`{value}` 会被替换 |
| `valueMap` | map | ❌ | 枚举值 → 中文显示名映射 |

#### 配置示例

```yaml
- id: check_pulse
  duty: ASSESS
  type: query
  params:
    targetVar: phys.heartRate
    responseTemplate: "颈动脉搏动可触及，心率 {value} 次/分。"

- id: check_perrl
  duty: ASSESS
  type: query
  params:
    targetVar: phys.perrl
    responseTemplate: "瞳孔对光反射 {value}。"
    valueMap:
      brisk: "灵敏"
      sluggish: "迟钝"
      fixed: "固定"
      unequal: "不等大"