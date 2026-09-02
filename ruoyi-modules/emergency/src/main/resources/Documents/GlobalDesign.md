# 院前急救AI教考评培训系统 - 深化设计方案

> 基于ACLS 2025指南 | 面向120院前急救场景 | 多智能体协同架构


## 文档版本

| 版本 | 日期 | 作者 | 变更说明 |
|------|------|------|----------|
| v1.0 | 2026-08-23 | - | 初始版本，完成四维度深化设计 |


## 目录

- [一、系统概述](#一系统概述)
- [二、算法知识库（ACLS 2025指南）](#二算法知识库acls-2025指南)
- [三、系统分析师视角：需求工程与领域建模](#三系统分析师视角需求工程与领域建模)
- [四、架构师视角：系统架构深化设计](#四架构师视角系统架构深化设计)
- [五、AI算法工程师视角：算法深化设计](#五ai算法工程师视角算法深化设计)
- [六、ACLS培训导师视角：教学深化设计](#六acls培训导师视角教学深化设计)
- [七、质量属性与约束](#七质量属性与约束)
- [八、部署与演进路线](#八部署与演进路线)
- [九、总结](#九总结)


## 一、系统概述

### 1.1 项目目标

构建一套面向**120院前急救**的**AI教考评一体化培训系统**，实现：

- **教学**：实时语音引导，渐进式提示
- **考核**：基于语音识别的客观自动评分
- **评价**：多维度结构化教学反馈

### 1.2 核心业务流程
┌─────────────────────────────────────────────────────────────────────┐
│ 教考评闭环流程 │
│ │
│ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ │
│ │ 场景加载 │───▶│ 实时演练 │───▶│ 智能评估 │───▶│ 报告生成 │ │
│ │ (场景库) │ │ (语音交互)│ │ (AI评分) │ │ (反馈) │ │
│ └─────────┘ └─────────┘ └─────────┘ └─────────┘ │
│ │ │ │ │ │
│ ▼ ▼ ▼ ▼ │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ 数据沉淀 → 知识库迭代更新 │ │
│ └─────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────┘

text


## 二、算法知识库（ACLS 2025指南）

### 2.1 2025 AHA指南关键变更

| 变更项 | 2020指南 | 2025指南 | 系统影响 |
|--------|----------|----------|----------|
| 电复律起始能量 | 设备推荐 | **≥200J（双相波）** | 评分规则更新 |
| 房颤/房扑电击能量 | 未明确 | **200J** | 能量阈值校验 |
| 药物移除 | — | 移除**索他洛尔（Sotalol）** | 知识库删除 |
| 术语变更 | 室上性心动过速（SVT） | **窄QRS心动过速** | Prompt术语更新 |
| 宽QRS不规则警告 | 未强调 | 可能为多形性VT/TdP，需**除颤**（非同步） | 新增安全节点 |
| WPW综合征 | 部分禁忌 | 明确禁用腺苷、地高辛、CCB、β受体阻滞剂、IV胺碘酮 | 新增禁忌检测 |

### 2.2 成人伴有脉搏的心动过速算法（2025）

#### 触发条件
- 适用人群：成人患者
- 核心指征：**心率 > 150次/分钟**，且**存在可检测到的脉搏**
- 排除条件：无脉搏（启动心脏骤停算法）

#### 算法决策流程
┌─────────────────────────────────────────────────────┐
│ 触发条件：成人 + 有脉搏 + 心率 > 150 bpm │
└─────────────────────────────────────────────────────┘
↓
┌─────────────────────────────────────────────────────┐
│ Step 1-2: 评估 + 初始稳定 │
│ （气道/氧疗/监测/IV/12导联ECG） │
└─────────────────────────────────────────────────────┘
↓
┌─────────────────────────────────────────────────────┐
│ Step 3: 血流动力学稳定吗？ │
│ 不稳定体征：低血压/意识改变/休克/胸痛/急性心衰 │
└─────────────────────────────────────────────────────┘
↓ 不稳定 ↓ 稳定
┌─────────────────────┐ ┌─────────────────────────┐
│ Step 6: 同步电复律 │ │ Step 4: QRS宽度评估 │
│ （镇静+同步模式） │ │ 宽QRS ≥ 0.12秒 │
│ 能量 ≥ 200J │ │ 窄QRS < 0.12秒 │
└─────────────────────┘ └─────────────────────────┘
↓ ↓宽QRS ↓窄QRS
┌─────────────────────┐ ┌──────────────┐ ┌──────────────┐
│ Step 7: │ │ Step 8: │ │ Step 5: │
│ 升级/会诊/再评估 │ │ 宽QRS处理 │ │ 窄QRS处理 │
│ - 寻找病因 │ │ - 腺苷(仅 │ │ - 迷走神经 │
│ - 增加能量 │ │ 规律单形) │ │ 刺激 │
│ - 抗心律失常药 │ │ - 抗心律 │ │ - 腺苷6mg │
│ - 专家会诊 │ │ 失常药 │ │ - 腺苷12mg │
│ │ │ - 专家会诊 │ │ - β阻滞/CCB │
└─────────────────────┘ └──────────────┘ └──────────────┘

text

#### 关键药物知识库

| 药物 | 剂量 | 关键操作要点 | 2025更新 |
|------|------|-------------|----------|
| **腺苷** | 首次6mg快速IV推注+冲管；二次12mg | 半衰期极短，必须快速推注并立即冲管 | 无变化 |
| **胺碘酮** | 150mg IV输注(10min)；维持1mg/min×6h | 室速复发可重复 | 无变化 |
| **普鲁卡因胺** | 负荷20-50mg/min；维持1-4mg/min | 停用指征：心律失常抑制、低血压、QRS增宽>50%、总量达17mg/kg | 无变化 |
| **索他洛尔** | — | — | **已从指南移除** |

#### WPW综合征禁忌药物清单（2025新增）
明确禁用：
├── 腺苷（Adenosine）
├── 地高辛（Digoxin）
├── 钙通道阻滞剂（CCB）
├── β受体阻滞剂（Beta-blockers）
└── IV胺碘酮（IV Amiodarone）

text


## 三、系统分析师视角：需求工程与领域建模

### 3.1 核心业务需求

| 能力域 | 功能模块 | 优先级 |
|--------|----------|--------|
| **场景管理** | 场景库配置、难度分级、指南版本切换 | P0 |
| **实时演练** | 语音采集、ASR转写、实时提示、进度追踪 | P0 |
| **智能评估** | 操作抽取、路径判定、安全校验、多模融合 | P0 |
| **报告系统** | 结构化评分、教学评语、证据链溯源 | P0 |
| **知识管理** | 指南更新、案例沉淀、RAG向量库 | P1 |
| **用户管理** | 学员档案、成绩追踪、权限管理 | P1 |

### 3.2 领域模型
┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
│ Scenario │────▶│ Algorithm │────▶│ DecisionNode │
│ (考核场景) │ │ (算法流程) │ │ (决策节点) │
│ - id │ │ - id │ │ - id │
│ - name │ │ - name │ │ - type │
│ - difficulty │ │ - version │ │ - condition │
│ - algorithmId │ │ - nodes │ │ - trueBranch │
│ - guideVersion │ │ - edges │ │ - falseBranch │
└─────────────────┘ └─────────────────┘ └─────────────────┘
│ │ │
▼ ▼ ▼
┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
│ Session │────▶│ ActionTrace │────▶│ Event │
│ (演练会话) │ │ (操作轨迹) │ │ (操作事件) │
│ - id │ │ - id │ │ - seq │
│ - scenarioId │ │ - sessionId │ │ - timestamp │
│ - teamMembers │ │ - actionType │ │ - actor │
│ - startTime │ │ - detail │ │ - actionType │
│ - status │ │ - value │ │ - detail │
└─────────────────┘ │ - result │ │ - value │
│ └─────────────────┘ │ - result │
▼ └─────────────────┘
┌─────────────────┐ ┌─────────────────┐
│ Evaluation │────▶│ ScoreDimension│
│ (综合评价) │ │ (评分维度) │
│ - id │ │ - id │
│ - sessionId │ │ - name │
│ - totalScore │ │ - weight │
│ - passFail │ │ - maxScore │
│ - dimensions │ │ - rules │
│ - errors │ └─────────────────┘
│ - report │
└─────────────────┘

text

### 3.3 用例图
┌─────────────────────────────────────────────────────────────────┐
│ 院前急救AI教考评系统 │
│ │
│ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ │
│ │ 教官 │ │ 学员 │ │ 系统管理员 │ │
│ └──────┬──────┘ └──────┬──────┘ └──────┬──────┘ │
│ │ │ │ │
│ ▼ ▼ ▼ │
│ ┌─────────────────────────────────────────────────────┐ │
│ │ │ │
│ │ ┌─────────────────────────────────────────────┐ │ │
│ │ │ 场景管理 │ │ │
│ │ │ - 创建/编辑场景 - 配置算法路径 │ │ │
│ │ │ - 设置难度分级 - 指南版本切换 │ │ │
│ │ └─────────────────────────────────────────────┘ │ │
│ │ │ │
│ │ ┌─────────────────────────────────────────────┐ │ │
│ │ │ 演练执行 │ │ │
│ │ │ - 启动/结束演练 - 实时语音采集 │ │ │
│ │ │ - 进度追踪 - 实时提示（教学模式） │ │ │
│ │ └─────────────────────────────────────────────┘ │ │
│ │ │ │
│ │ ┌─────────────────────────────────────────────┐ │ │
│ │ │ 智能评估 │ │ │
│ │ │ - 自动评分 - 路径判定 │ │ │
│ │ │ - 安全校验 - 多模态融合 │ │ │
│ │ └─────────────────────────────────────────────┘ │ │
│ │ │ │
│ │ ┌─────────────────────────────────────────────┐ │ │
│ │ │ 报告与反馈 │ │ │
│ │ │ - 评分报告 - 教学评语 │ │ │
│ │ │ - 证据链展示 - 历史追踪 │ │ │
│ │ └─────────────────────────────────────────────┘ │ │
│ │ │ │
│ │ ┌─────────────────────────────────────────────┐ │ │
│ │ │ 知识管理 │ │ │
│ │ │ - 指南更新 - 案例沉淀 │ │ │
│ │ │ - RAG向量库管理 - 知识图谱维护 │ │ │
│ │ └─────────────────────────────────────────────┘ │ │
│ └─────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘

text


## 四、架构师视角：系统架构深化设计

### 4.1 六层架构模型
┌─────────────────────────────────────────────────────────────────────┐
│ 表现层（Presentation） │
│ 教官控制台 │ 学员终端 │ 实时监控大屏 │ 报告导出 │ WebRTC │
└─────────────────────────────────────────────────────────────────────┘
│
┌─────────────────────────────────────────────────────────────────────┐
│ API网关层（Gateway） │
│ Spring Cloud Gateway │ 认证鉴权（Spring Security） │ 限流熔断 │
└─────────────────────────────────────────────────────────────────────┘
│
┌─────────────────────────────────────────────────────────────────────┐
│ 业务编排层（Orchestration） │
│ 场景管理 │ 会话控制 │ 状态机引擎（Spring StateMachine）│ 报告服务 │
└─────────────────────────────────────────────────────────────────────┘
│
┌─────────────────────────────────────────────────────────────────────┐
│ AI能力层（AI Capabilities） │
│ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ │
│ │ASR引擎 │ │LLM抽取层 │ │多模态融合│ │RAG知识库 │ │
│ │(实时流) │ │(结构化) │ │(视频+音频)│ │(指南+案例)│ │
│ └──────────┘ └──────────┘ └──────────┘ └──────────┘ │
│ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ │
│ │多智能体 │ │动作识别 │ │意图推理 │ │评分引擎 │ │
│ │(6 Agents)│ │(视频) │ │(LLM) │ │(规则) │ │
│ └──────────┘ └──────────┘ └──────────┘ └──────────┘ │
└─────────────────────────────────────────────────────────────────────┘
│
┌─────────────────────────────────────────────────────────────────────┐
│ 数据层（Data） │
│ MySQL（业务）│ Redis（缓存/会话）│ Milvus（向量库）│ MinIO（音视频）│
└─────────────────────────────────────────────────────────────────────┘
│
┌─────────────────────────────────────────────────────────────────────┐
│ 基础设施层（Infrastructure） │
│ K8s/Docker │ 服务注册发现 │ 链路追踪 │ 日志聚合（ELK） │
└─────────────────────────────────────────────────────────────────────┘

text

### 4.2 RuoYi-AI模块划分

基于RuoYi-AI框架的Maven多模块结构：
ruoyi-modules/
├── ruoyi-ai-ems/ # 院前急救核心业务模块
│ ├── src/main/java/.../
│ │ ├── controller/ # REST API 接口
│ │ │ ├── ScenarioController
│ │ │ ├── SessionController
│ │ │ ├── EvaluationController
│ │ │ └── ReportController
│ │ ├── service/ # 业务服务层
│ │ │ ├── ScenarioService
│ │ │ ├── SessionService
│ │ │ ├── EvaluationService
│ │ │ └── ReportService
│ │ ├── engine/ # 核心算法引擎
│ │ │ ├── asr/ # 语音识别
│ │ │ │ ├── AsrService
│ │ │ │ └── AsrWebSocketHandler
│ │ │ ├── extraction/ # LLM结构化抽取
│ │ │ │ ├── ExtractionService
│ │ │ │ └── PromptTemplates
│ │ │ ├── fsm/ # ACLS状态机
│ │ │ │ ├── ACLSStateMachineConfig
│ │ │ │ ├── Guards
│ │ │ │ └── Actions
│ │ │ ├── scorer/ # 评分引擎
│ │ │ │ ├── ScoringEngine
│ │ │ │ └── WeightMatrix
│ │ │ ├── multiagent/ # 多智能体协同
│ │ │ │ ├── SupervisorAgent
│ │ │ │ └── SubAgents
│ │ │ └── report/ # 报告生成
│ │ │ ├── ReportGenerator
│ │ │ └── ISBARFormatter
│ │ ├── config/ # 模块配置
│ │ │ ├── WebSocketConfig
│ │ │ └── StateMachineConfig
│ │ └── domain/ # 实体类与DTO
│ │ ├── entity/
│ │ └── dto/
│ └── src/main/resources/
│ └── acls/ # ACLS算法配置（DSL）
│ ├── tachycardia.yaml
│ ├── bradycardia.yaml
│ └── cardiac_arrest.yaml

text

### 4.3 核心依赖配置

```xml
<dependencies>
    <!-- RuoYi-AI 核心依赖 -->
    <dependency>
        <groupId>com.ruoyi</groupId>
        <artifactId>ruoyi-common-ai</artifactId>
    </dependency>

    <!-- Spring Boot WebSocket (实时音频流) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-websocket</artifactId>
    </dependency>

    <!-- Spring StateMachine (ACLS流程控制) -->
    <dependency>
        <groupId>org.springframework.statemachine</groupId>
        <artifactId>spring-statemachine-starter</artifactId>
        <version>4.0.0</version>
    </dependency>

    <!-- Spring Cloud Gateway (API网关) -->
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-gateway</artifactId>
    </dependency>

    <!-- 语音识别SDK (阿里云Paraformer) -->
    <dependency>
        <groupId>com.alibaba.cloud</groupId>
        <artifactId>aliyun-speech-sdk</artifactId>
        <version>2.0.0</version>
    </dependency>

    <!-- 视频动作识别 (MediaPipe) -->
    <dependency>
        <groupId>com.google.mediapipe</groupId>
        <artifactId>mediapipe</artifactId>
        <version>0.10.7</version>
    </dependency>

    <!-- 向量数据库 (Milvus) -->
    <dependency>
        <groupId>io.milvus</groupId>
        <artifactId>milvus-sdk-java</artifactId>
        <version>2.4.0</version>
    </dependency>

    <!-- JSON处理 -->
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
    </dependency>
</dependencies>
4.4 状态机深化设计
ACLS顶层状态机
java
public enum ACLSStates {
    INITIAL_ASSESSMENT,    // 初始评估（心率>150，有脉搏）
    STABILIZATION,         // 稳定与监护
    STABILITY_CHECK,       // 血流动力学稳定性评估 【决策节点】
    UNSTABLE_PATH,         // 不稳定路径
    STABLE_PATH,           // 稳定路径
    QRS_EVALUATION,        // QRS宽度评估 【决策节点】
    NARROW_QRS_PATH,       // 窄QRS处理
    WIDE_QRS_PATH,         // 宽QRS处理
    SYNCHRONIZED_CARDIOVERSION, // 同步电复律 【安全节点】
    POST_TREATMENT,        // 治疗后管理
    COMPLETED
}

public enum ACLSEvents {
    RATE_CONFIRMED,        // 心率>150确认
    PULSE_CONFIRMED,       // 脉搏存在确认
    STABLE,                // 患者稳定
    UNSTABLE,              // 患者不稳定
    NARROW_QRS,            // QRS窄（<0.12s）
    WIDE_QRS,              // QRS宽（≥0.12s）
    REGULAR_RHYTHM,        // 规律心律
    IRREGULAR_RHYTHM,      // 不规则心律
    CARDIOVERSION_DONE,    // 电复律完成
    DRUG_ADMINISTERED,     // 药物给药完成
    EXPERT_CONSULTED,      // 专家会诊
    TREATMENT_COMPLETE     // 治疗完成
}
窄QRS子状态机（顺序执行）
java
public enum NarrowQRSSubStates {
    VAGAL_MANEUVER,        // 迷走神经刺激
    ADENOSINE_6MG,         // 腺苷6mg
    ADENOSINE_12MG,        // 腺苷12mg（如无效）
    BETA_BLOCKER_OR_CCB,   // β阻滞剂或钙通道阻滞剂
    EXPERT_CONSULT         // 专家会诊
}
条件守卫（Guards）
java
@Component
public class StabilityGuard implements Guard<ACLSStates, ACLSEvents> {
    @Override
    public boolean evaluate(StateContext<ACLSStates, ACLSEvents> context) {
        AssessmentResult result = context.getExtendedState()
            .get("assessmentResult", AssessmentResult.class);
        // 不稳定体征：低血压、意识改变、休克、胸痛、急性心衰
        return result.hasAnyUnstableSign();
    }
}

@Component
public class QRSWidthGuard implements Guard<ACLSStates, ACLSEvents> {
    @Override
    public boolean evaluate(StateContext<ACLSStates, ACLSEvents> context) {
        AssessmentResult result = context.getExtendedState()
            .get("assessmentResult", AssessmentResult.class);
        return result.getQrsWidth() >= 0.12;  // ≥0.12秒为宽QRS
    }
}

@Component
public class RhythmRegularityGuard implements Guard<ACLSStates, ACLSEvents> {
    @Override
    public boolean evaluate(StateContext<ACLSStates, ACLSEvents> context) {
        AssessmentResult result = context.getExtendedState()
            .get("assessmentResult", AssessmentResult.class);
        return result.isRegularRhythm();
    }
}
安全节点动作（Actions）
java
@Component
public class CardioversionAction implements Action<ACLSStates, ACLSEvents> {
    @Override
    public void execute(StateContext<ACLSStates, ACLSEvents> context) {
        // 1. 记录进入电复律节点
        // 2. 检查安全条件：是否提及"同步模式"
        // 3. 检查镇静措施
        // 4. 检查能量设置（2025指南：≥200J）
        // 5. 触发评分引擎的安全节点校验
        ExtendedState extState = context.getExtendedState();
        SafetyCheckResult safetyCheck = new SafetyCheckResult();
        
        // 同步模式校验
        boolean syncModeMentioned = extState.get("syncModeMentioned", Boolean.class);
        if (!syncModeMentioned) {
            safetyCheck.addViolation("SAFETY_001", "未口头确认同步模式");
        }
        
        // 镇静措施校验
        boolean sedationMentioned = extState.get("sedationMentioned", Boolean.class);
        if (!sedationMentioned) {
            safetyCheck.addViolation("SAFETY_002", "电复律前未提及镇静镇痛");
        }
        
        // 能量设置校验（2025指南：≥200J）
        Integer energySetting = extState.get("energySetting", Integer.class);
        if (energySetting == null || energySetting < 200) {
            safetyCheck.addViolation("SAFETY_003", 
                "能量设置未达2025指南推荐（≥200J），当前：" + energySetting + "J");
        }
        
        extState.set("safetyCheck", safetyCheck);
    }
}
4.5 状态机配置（YAML DSL）
yaml
# tachycardia.yaml - 心动过速算法流程配置
algorithm:
  id: acls-tachycardia-2025
  name: 成人伴有脉搏的心动过速
  version: 2025
  states:
    - id: INITIAL_ASSESSMENT
      type: assessment
      required_actions: [heart_rate_check, pulse_check]
      timeout: 30
    
    - id: STABILITY_CHECK
      type: decision
      guard: StabilityGuard
      true_branch: UNSTABLE_PATH
      false_branch: STABLE_PATH
      timeout: 20
    
    - id: UNSTABLE_PATH
      type: treatment
      required_actions: [synchronized_cardioversion, sedation]
      safety_checks: [sync_mode, energy_200j]
      next: POST_TREATMENT
    
    - id: STABLE_PATH
      type: decision
      guard: QRSWidthGuard
      true_branch: WIDE_QRS_PATH
      false_branch: NARROW_QRS_PATH
      timeout: 15
    
    - id: NARROW_QRS_PATH
      type: sub_state_machine
      config: narrow_qrs_sub.yaml
    
    - id: WIDE_QRS_PATH
      type: treatment
      warnings: [possible_vt_tdp]
      required_actions: [antiarrhythmic_drug]
      next: POST_TREATMENT
    
    - id: POST_TREATMENT
      type: assessment
      required_actions: [reassess, expert_consult]
      timeout: 30
4.6 多智能体协作架构（基于RuoYi-AI）
基于RuoYi-AI的多智能体协同能力，构建六个专业Agent：

Agent	职责	输入	输出
Assessment Agent	评估学员是否完成关键评估	ASR结构化文本	评估完成度评分
Decision Agent	判断学员是否走对算法分支	状态机当前状态	路径正确性评分
Safety Agent	监控关键安全节点	操作事件流	安全违规列表
Communication Agent	评估团队沟通质量	多人语音分离文本	沟通质量评分
Knowledge Agent	RAG检索指南知识	学员操作+指南向量库	证据链标注
Feedback Agent	汇总评分，生成反馈	所有Agent输出	结构化评价报告
java
@Configuration
public class MultiAgentConfig {
    
    @Bean
    public SupervisorAgent supervisorAgent() {
        return SupervisorAgent.builder()
            .subAgents(Arrays.asList(
                assessmentAgent(),
                decisionAgent(),
                safetyAgent(),
                communicationAgent(),
                knowledgeAgent(),
                feedbackAgent()
            ))
            .routingStrategy(RoutingStrategy.SEQUENTIAL)
            .build();
    }
    
    @Bean
    public AssessmentAgent assessmentAgent() {
        return AssessmentAgent.builder()
            .chatClient(chatClient)
            .systemPrompt(assessmentPrompt())
            .build();
    }
    
    // ... 其他Agent定义
}
五、AI算法工程师视角：算法深化设计
5.1 混合架构设计原则
核心原则：安全关键的通过/不通过判定100%由确定性代码执行，LLM仅用于语义理解和教育性解释。

text
                    ┌─────────────────────────────────────┐
                    │          ASR实时文本流              │
                    └─────────────────────────────────────┘
                                       │
                                       ▼
                    ┌─────────────────────────────────────┐
                    │      LLM语义抽取层（非判定）         │
                    │  - 操作事件提取（JSON结构化）         │
                    │  - ASR纠错（医疗同义词纠正）          │
                    │  - 隐含意图推理（"湿冷"→休克体征）    │
                    └─────────────────────────────────────┘
                                       │
                    ┌──────────────────┴──────────────────┐
                    ▼                                      ▼
     ┌─────────────────────────┐          ┌─────────────────────────┐
     │   确定性规则引擎（判定）   │          │   RAG知识检索（佐证）   │
     │  - 状态机路径匹配         │          │  - 指南条款检索         │
     │  - 安全节点校验           │          │  - 最佳实践匹配         │
     │  - 时序逻辑验证           │          │  - 证据链生成           │
     │  - 药物剂量校验           │          │                         │
     └─────────────────────────┘          └─────────────────────────┘
                    │                                      │
                    └──────────────────┬──────────────────┘
                                       ▼
                    ┌─────────────────────────────────────┐
                    │      评分聚合与报告生成              │
                    │  - 多维加权评分（确定性分数）        │
                    │  - LLM生成自然语言评语（非判定）     │
                    └─────────────────────────────────────┘
5.2 结构化抽取实现
数据模型定义
java
// 操作事件
public class ClinicalAction {
    private Integer seq;
    private Integer timestamp;        // 相对时间（秒）
    private String actor;              // 执行人
    private ActionType actionType;     // ASSESS/MONITOR/INTERVENTION/SAFETY/COMMUNICATION
    private String detail;
    private String value;
    private String result;
}

// 场景判断
public class ScenarioJudgment {
    private Boolean heartRateGt150;
    private Boolean pulsePresent;
    private List<String> unstableSigns;  // hypotension, AMS, chest_pain, shock
    private QrsWidth qrsWidth;           // NARROW/WIDE
    private RhythmRegularity rhythmRegularity; // REGULAR/IRREGULAR
}

// 团队表现
public class TeamPerformance {
    private List<ClinicalAction> actions;
    private ScenarioJudgment judgments;
    private String rawTranscript;
}
LLM抽取Service
java
@Service
public class ExtractionService {
    
    @Autowired
    private ChatClient chatClient;
    
    private final String EXTRACTION_PROMPT = """
        【系统角色】
        你是ACLS急救语音分析专家。从嘈杂的院前急救ASR文本中提取标准化操作事件。
        
        【ASR容错规则】
        1. 药物名称纠错："肝素"→"腺苷"（结合心率快、推注语境）
        2. 数字纠错："一百..."→推断为>150（结合"很快"、"摸不到"等描述）
        3. 忽略背景噪音：警报声、监护仪滴滴声
        
        【隐含意图推理】
        - "患者叫不醒，身上湿冷" → unstable_signs: ['altered_mental','shock']
        - "血压80/40" → 识别为低血压体征
        
        【输出格式】
        严格按JSON Schema输出，包含actions和judgments两部分。
        """;
    
    public TeamPerformance extract(String sessionId, String transcript) {
        try {
            return chatClient.prompt()
                .user(EXTRACTION_PROMPT + "\n\n语音文本：\n" + transcript)
                .call()
                .entity(TeamPerformance.class);
        } catch (Exception e) {
            // 降级策略：格式错误时的修正重试
            return fallbackExtractor.extract(transcript);
        }
    }
}
5.3 多模态融合算法
融合语音（ASR文本）、视频（动作识别）、监护仪数据（心电、血压）三类信号。

时序对齐与一致性校验
text
时间轴（秒）:  0─────5─────10─────15─────20─────25─────30
              │      │      │      │      │      │      │
语音事件:     [评估心率] [给药] [电复律] [再评估]
              │      │      │      │      │      │      │
视频动作:     [看监护] [推注] [拿电极] [看监护]
              │      │      │      │      │      │      │
监护数据:     HR160  HR160  HR160  HR90
              │      │      │      │      │      │      │
一致性校验:    ✓      ✓      ✓      ✓      ✓      ✓      ✓
冲突检测逻辑
冲突类型	检测规则	扣分
操作-叙述不一致	语音说"给了腺苷"但视频无推注动作	-5
评估-数据不一致	语音说"心率正常"但监护数据显示HR>150	-10
时序矛盾	语音说"先给药"但视频显示先电击	-8
5.4 评分算法：多维加权矩阵
评分维度	权重	判定逻辑	数据类型
临床决策准确性	35%	状态机路径匹配度（分支错误直接扣光）	确定性
关键安全节点	25%	同步模式、腺苷冲管、药物剂量、能量设置	确定性
操作时序性	20%	评估→决策→干预的顺序合规性	确定性
团队沟通与闭环	10%	ISBAR完整性、指令-确认闭环	LLM辅助
操作规范性	10%	与2025指南的吻合度	RAG+LLM
评分引擎实现
java
@Service
public class ScoringEngine {
    
    private static final int PASS_THRESHOLD = 70;
    private static final int MAX_SCORE = 100;
    
    public ScoreResult evaluate(StateMachine<ACLSStates, ACLSEvents> stateMachine,
                                 TeamPerformance performance) {
        ScoreResult result = new ScoreResult();
        result.setScore(MAX_SCORE);
        result.setCriticalErrors(new ArrayList<>());
        result.setWarnings(new ArrayList<>());
        
        // 1. 致命错误检测（直接扣光或严重扣分）
        detectFatalErrors(performance, result);
        
        // 2. 路径匹配度（35%权重）
        double pathScore = evaluatePathMatch(stateMachine, performance);
        result.addDimension("临床决策准确性", pathScore, 0.35);
        
        // 3. 安全节点校验（25%权重）
        double safetyScore = evaluateSafetyChecks(performance, result);
        result.addDimension("关键安全节点", safetyScore, 0.25);
        
        // 4. 时序逻辑校验（20%权重）
        double sequenceScore = evaluateSequence(performance, result);
        result.addDimension("操作时序性", sequenceScore, 0.20);
        
        // 5. 团队沟通（10%权重）
        double commScore = evaluateCommunication(performance, result);
        result.addDimension("团队沟通与闭环", commScore, 0.10);
        
        // 6. 指南规范性（10%权重）
        double guidelineScore = evaluateGuidelineCompliance(performance, result);
        result.addDimension("操作规范性", guidelineScore, 0.10);
        
        // 计算总分
        result.setTotalScore(result.calculateWeightedTotal());
        result.setPass(result.getTotalScore() >= PASS_THRESHOLD && 
                       result.getCriticalErrors().isEmpty());
        
        return result;
    }
    
    private void detectFatalErrors(TeamPerformance performance, ScoreResult result) {
        boolean isUnstable = !performance.getJudgments().getUnstableSigns().isEmpty();
        
        if (isUnstable) {
            boolean hasCardioversion = performance.getActions().stream()
                .anyMatch(a -> "synchronized_cardioversion".equals(a.getDetail()));
            
            if (!hasCardioversion) {
                result.addCriticalError("CRITICAL: 患者不稳定，未执行同步电复律");
                result.setScore(result.getScore() - 40);
            }
            
            // 不稳定患者严禁仅用腺苷
            boolean onlyAdenosine = performance.getActions().stream()
                .filter(a -> "adenosine".equals(a.getDetail()))
                .count() > 0 && !hasCardioversion;
            if (onlyAdenosine) {
                result.addCriticalError("CRITICAL: 不稳定患者严禁单独使用腺苷延迟电复律");
                result.setScore(result.getScore() - 20);
            }
        }
    }
    
    private double evaluateSafetyChecks(TeamPerformance performance, ScoreResult result) {
        double score = 100;
        
        // 同步模式检查
        boolean syncMentioned = performance.getActions().stream()
            .anyMatch(a -> "synchronized".equals(a.getDetail()) || 
                           "sync".equalsIgnoreCase(a.getValue()));
        if (performance.getActions().stream()
            .anyMatch(a -> "synchronized_cardioversion".equals(a.getDetail()))) {
            if (!syncMentioned) {
                score -= 30;
                result.addCriticalError("SAFETY: 电复律未明确设置为同步模式");
            }
        }
        
        // 腺苷冲管检查
        performance.getActions().stream()
            .filter(a -> "adenosine".equals(a.getDetail()))
            .forEach(a -> {
                boolean flushMentioned = performance.getActions().stream()
                    .anyMatch(f -> "flush".equals(f.getDetail()) && f.getSeq() > a.getSeq());
                if (!flushMentioned) {
                    score -= 10;
                    result.addWarning("腺苷给药后未提及生理盐水快速冲管");
                }
                // 剂量校验
                if (a.getValue() != null && !a.getValue().matches("6|12")) {
                    score -= 15;
                    result.addWarning("腺苷剂量异常：" + a.getValue() + "mg（应为6mg或12mg）");
                }
            });
        
        // 能量设置检查（2025指南：≥200J）
        performance.getActions().stream()
            .filter(a -> "synchronized_cardioversion".equals(a.getDetail()))
            .forEach(a -> {
                if (a.getValue() != null) {
                    try {
                        int energy = Integer.parseInt(a.getValue().replaceAll("[^0-9]", ""));
                        if (energy < 200) {
                            score -= 20;
                            result.addCriticalError("2025指南要求：电复律能量≥200J，当前：" + energy + "J");
                        }
                    } catch (NumberFormatException e) {
                        score -= 10;
                        result.addWarning("未明确电复律能量设置");
                    }
                }
            });
        
        return Math.max(0, score);
    }
}
5.5 RAG知识库实现
java
@Service
public class KnowledgeAgentService {
    
    @Autowired
    private MilvusClient milvusClient;
    
    @Autowired
    private ChatClient chatClient;
    
    /**
     * 检索指南条款，验证操作合规性
     */
    public List<GuidelineEvidence> retrieveGuidelines(String operation, 
                                                       String context) {
        // 1. 生成查询向量
        float[] queryVector = generateEmbedding(operation + " " + context);
        
        // 2. 向量检索
        SearchResp searchResp = milvusClient.search(
            SearchParam.newBuilder()
                .withCollectionName("acls_guidelines_2025")
                .withVectors(Collections.singletonList(queryVector))
                .withTopK(5)
                .build()
        );
        
        // 3. 返回相关条款
        return searchResp.getResults().stream()
            .map(this::toGuidelineEvidence)
            .collect(Collectors.toList());
    }
    
    /**
     * 知识增强的验证
     */
    public KnowledgeValidationResult validateWithKnowledge(
            ClinicalAction action, 
            List<GuidelineEvidence> evidences) {
        
        String prompt = """
            基于以下ACLS 2025指南条款，判断该操作是否合规：
            
            操作：%s
            详情：%s
            
            相关指南条款：
            %s
            
            请输出：合规/不合规/需进一步评估，并说明理由。
            """.formatted(action.getDetail(), action.getValue(), 
                         evidences.stream()
                             .map(e -> "- " + e.getClause() + ": " + e.getContent())
                             .collect(Collectors.joining("\n")));
        
        return chatClient.prompt()
            .user(prompt)
            .call()
            .entity(KnowledgeValidationResult.class);
    }
}
六、ACLS培训导师视角：教学深化设计
6.1 场景库设计
按难度分级构建标准化考核场景（基于AHA 2025指南）：

级别	场景类型	核心考核点	算法路径	预计时长
基础	稳定窄QRS心动过速	迷走神经刺激→腺苷6mg→12mg	Step 5	3-5min
基础	稳定宽QRS单形性VT	抗心律失常药（胺碘酮/普鲁卡因胺）	Step 8	3-5min
中级	不稳定心动过速（低血压）	识别不稳定→同步电复律→镇静	Step 3→Step 6	4-6min
中级	稳定窄QRS伴WPW	识别WPW→避免禁忌药物→专家会诊	Step 5→专家	4-6min
高级	宽QRS不规则（多形性VT）	识别→除颤（非同步）→专家会诊	Step 8警告	5-7min
专家	WPW综合征伴房颤	识别WPW→避免禁忌药物→同步电复律	Step 10警告	5-7min
6.2 场景配置示例（YAML）
yaml
# scenario_unstable_tachycardia.yaml
scenario:
  id: SCE-2025-002
  name: 不稳定窄QRS心动过速
  difficulty: INTERMEDIATE
  guide_version: AHA-2025
  
  initial_state:
    patient_info:
      age: 65
      gender: MALE
      chief_complaint: 心悸、气短
    vital_signs:
      heart_rate: 160
      rhythm: REGULAR_NARROW_QRS
      blood_pressure: 80/45
      spo2: 94
    physical_exam:
      consciousness: ALERT_BUT_WEAK
      skin: COLD_CLAMMY
  
  expected_path:
    - step: INITIAL_ASSESSMENT
      required_actions: [heart_rate_check, pulse_check]
      expected_time: 20
    
    - step: STABILITY_CHECK
      expected_judgment: UNSTABLE
      expected_signs: [hypotension]
    
    - step: UNSTABLE_PATH
      required_actions: [synchronized_cardioversion, sedation]
      safety_checks: [sync_mode_200j]
    
    - step: POST_TREATMENT
      required_actions: [reassess, expert_consult]
  
  scoring:
    pass_threshold: 70
    critical_errors: 
      - 未识别不稳定体征
      - 未执行同步电复律
      - 未设置同步模式
  
  hints:
    - level_1: "请评估患者血流动力学状态"
    - level_2: "检查是否存在低血压、意识改变、休克体征"
    - level_3: "患者不稳定，应立即准备同步电复律"
6.3 实时教学引导模式
在教学模式下，系统提供渐进式提示：

java
@Service
public class TeachingGuidanceService {
    
    private static final int LEVEL1_WAIT = 15;  // 15秒无操作
    private static final int LEVEL2_WAIT = 30;  // 30秒无操作
    private static final int LEVEL3_WAIT = 45;  // 45秒无操作
    
    private final Map<String, List<GuidanceHint>> hintMap;
    
    public GuidanceHint getHint(String sessionId, 
                                 ACLSStates currentState,
                                 long idleTime) {
        String scenarioId = getScenarioId(sessionId);
        List<GuidanceHint> hints = hintMap.get(scenarioId);
        
        if (idleTime > LEVEL3_WAIT) {
            return hints.stream()
                .filter(h -> h.getLevel() == 3 && h.getState() == currentState)
                .findFirst()
                .orElse(getDefaultHint(currentState, 3));
        } else if (idleTime > LEVEL2_WAIT) {
            return hints.stream()
                .filter(h -> h.getLevel() == 2 && h.getState() == currentState)
                .findFirst()
                .orElse(getDefaultHint(currentState, 2));
        } else if (idleTime > LEVEL1_WAIT) {
            return hints.stream()
                .filter(h -> h.getLevel() == 1 && h.getState() == currentState)
                .findFirst()
                .orElse(getDefaultHint(currentState, 1));
        }
        return null;
    }
    
    enum GuidanceLevel {
        LEVEL_1("温和提醒", "建议..."),
        LEVEL_2("明确指引", "请..."),
        LEVEL_3("直接建议", "应立即...");
    }
}
6.4 评价报告模板（ISBAR对齐）
text
╔═══════════════════════════════════════════════════════════════════════════════╗
║                    ACLS 心动过速算法考核评价报告                              ║
╠═══════════════════════════════════════════════════════════════════════════════╣
║  学员: 张伟 (ID: S2024001)          场景: 不稳定窄QRS心动过速                ║
║  指南版本: AHA 2025                  考核日期: 2026-08-23 14:30              ║
║  总用时: 4分32秒                     状态: 已完成                            ║
╠═══════════════════════════════════════════════════════════════════════════════╣
║  总分: 82/100                       判定: ✅ 通过    致命错误: 0            ║
╠═══════════════════════════════════════════════════════════════════════════════╣
║                                                                               ║
║  【Situation - 态势评估】                   得分: 18/20  (权重: 20%)          ║
║  ──────────────────────────────────────────────────────────────────────────   ║
║  ✅ 正确识别心率>150 (实际160bpm)                                            ║
║  ✅ 确认存在脉搏                                                              ║
║  ✅ 排除生理性心动过速原因                                                    ║
║  ⚠ 未在30秒内完成初始评估                                                    ║
║                                                                               ║
║  【Background - 背景信息】                   得分: 15/20  (权重: 15%)          ║
║  ──────────────────────────────────────────────────────────────────────────   ║
║  ✅ 建立静脉通路 (左前臂)                                                     ║
║  ✅ 12导联ECG (提示窄QRS, 规律, 约160bpm)                                    ║
║  ✅ 持续心电血压血氧监护                                                      ║
║  ✗ 未提及血氧饱和度监测数据                                                    ║
║                                                                               ║
║  【Assessment - 评估判断】                   得分: 28/30  (权重: 30%)          ║
║  ──────────────────────────────────────────────────────────────────────────   ║
║  ✅ 正确识别不稳定体征（低血压 80/40, 皮肤湿冷）                              ║
║  ✅ 正确选择同步电复律路径                                                    ║
║  ✅ 正确判断为窄QRS心动过速                                                    ║
║                                                                               ║
║  【Recommendation - 处置建议】               得分: 21/30  (权重: 30%)          ║
║  ──────────────────────────────────────────────────────────────────────────   ║
║  ✅ 执行同步电复律                                                            ║
║  ✅ 提及镇静措施（咪达唑仑 2mg IV）                                           ║
║  ✅ 电复律后再次评估（心率降至 90bpm）                                        ║
║  ⚠ 未口头确认"同步模式"设置！                     [安全警告 - 扣10分]        ║
║  ✗ 能量设置 150J 低于2025指南推荐≥200J            [安全警告 - 扣10分]        ║
║  ⚠ 电复律后未提及专家会诊                        [流程缺失 - 扣5分]          ║
║                                                                               ║
╠═══════════════════════════════════════════════════════════════════════════════╣
║  【导师评语】                                                                 ║
║  ──────────────────────────────────────────────────────────────────────────   ║
║  学员对不稳定心动过速的识别准确，决策路径选择正确，体现了良好的临床思维。    ║
║  操作流程总体流畅，但存在关键安全疏漏：                                       ║
║  1. 未口头确认"同步模式"——这可能导致误将电复律操作为除颤，造成严重后果       ║
║  2. 能量设置未达2025指南要求（≥200J），需更新设备认知                         ║
║                                                                               ║
║  【改进建议】                                                                 ║
║  1. 强化"同步电复律安全核查"模拟训练                                           ║
║  2. 复习2025指南电复律能量更新要点                                             ║
║  3. 练习完整ISBAR交接流程                                                     ║
║                                                                               ║
║  【推荐训练场景】                                                             ║
║  - 场景: 不稳定宽QRS心动过速 (难度: 高级)                                    ║
║  - 专项: 同步电复律安全核查流程                                              ║
╚═══════════════════════════════════════════════════════════════════════════════╝
七、质量属性与约束
质量属性	目标	实现策略
安全性	零容忍LLM幻觉导致的误判	混合架构：判定100%确定性代码
可解释性	每项扣分可溯源	证据链：时间戳+指南条款+原始文本
实时性	端到端延迟<3秒	ASR流式+LLM流式输出+状态机内存计算
准确性	与专家评分一致性≥85%	参照NEJM AI研究基线（89.7% agreement）
可扩展性	支持新指南版本	算法DSL化，指南变更加载新配置
合规性	符合医疗软件规范	操作审计日志、数据脱敏、权限管理
可用性	系统可用性≥99.5%	高可用部署、熔断降级、故障恢复
数据隐私	医疗数据保护	传输加密、存储加密、访问审计
八、部署与演进路线
8.1 Phase 1（MVP）- 单模态语音评估
项目	内容
周期	3个月
功能	ASR语音识别 + LLM结构化抽取 + 状态机评分
场景	基础场景（稳定窄QRS）
验收	完成端到端流程，评分准确率≥80%
8.2 Phase 2（成熟）- 多模态融合
项目	内容
周期	4个月
功能	视频动作识别 + 监护仪数据接入 + 多模态时序对齐
场景	全部4级场景
验收	多模态一致性校验，综合评分准确率≥85%
8.3 Phase 3（高级）- 多智能体协同
项目	内容
周期	3个月
功能	6 Agent协同评价 + RAG知识库 + 指南自动更新
场景	复杂病例 + 2025-2030指南适配
验收	多Agent一致性≥90%，知识库覆盖2025指南
8.4 Phase 4（智能化）- 自适应教学
项目	内容
周期	2个月
功能	个性化场景推荐 + 难度动态调整 + 学习路径规划
场景	全场景自适应
验收	学员进步率提升30%以上
8.5 部署架构
yaml
# Docker Compose 部署配置
version: '3.8'
services:
  # 应用服务
  ruoyi-ai-ems:
    image: ruoyi-ai-ems:latest
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - AI_API_KEY=${AI_API_KEY}
    depends_on:
      - mysql
      - redis
      - milvus
      - minio
  
  # 数据库
  mysql:
    image: mysql:8.0
    environment:
      - MYSQL_ROOT_PASSWORD=${MYSQL_PASSWORD}
    volumes:
      - mysql_data:/var/lib/mysql
  
  # 缓存
  redis:
    image: redis:7.0-alpine
    ports:
      - "6379:6379"
  
  # 向量数据库
  milvus:
    image: milvusdb/milvus:v2.4.0
    ports:
      - "19530:19530"
  
  # 对象存储（音视频）
  minio:
    image: minio/minio:latest
    ports:
      - "9000:9000"
      - "9001:9001"
    command: server /data --console-address ":9001"
九、总结
本设计方案从系统分析师、架构师、AI算法工程师、ACLS培训导师四个视角，对院前急救AI教考评培训系统进行了深化设计：

核心设计思想
用确定性代码守护安全底线，用LLM拓展语义理解的边界，用多模态融合还原真实临床场景，用多智能体实现专业分工评价。

关键亮点
混合架构：LLM负责语义理解，确定性代码负责安全判定，杜绝AI幻觉风险

状态机引擎：基于Spring StateMachine实现ACLS算法的确定性执行

多智能体协同：6个专业Agent分工协作，覆盖评估、决策、安全、沟通、知识、反馈

多模态融合：语音+视频+监护数据时序对齐，实现全方位行为分析

指南版本管理：支持2020→2025指南切换，知识库持续更新

教学评一体化：实时引导+客观评分+结构化报告，形成完整教学闭环

可解释性设计：每项扣分均可溯源到时间戳、指南条款和原始文本

文档结束

text

---

您现在可以**点击代码块右上角的“复制”按钮**（或直接选中全部内容后 `Ctrl+C`），一次性将整个文档粘贴到您的本地Markdown编辑器中。如果还有任何格式问题，欢迎继续提出。