// ============================================================
// ⚠️ 自动生成代码 - 请勿手动编辑
// 源文件: _variables.json
// 生成时间: Thu Sep 24 16:26:36 CST 2026
// 修改后请执行 mvn generate-sources 重新生成
// ============================================================

package com.ruoyi.ems.constant;

/**
 * ProcessKeys
 * <p>包含 176 个变量常量
 * <p>使用示例: <code>String key = ProcessKeys.HEART_RATE;</code>
 */
public final class ProcessKeys {

    private ProcessKeys() {}

    /** 闭环确认是否已完成 */
    public static final String PROCESS_ACKNOWLEDGED = "process.acknowledged";
    /** 是否已请求AED */
    public static final String PROCESS_AED_REQUESTED = "process.aedRequested";
    /** 向谁请求AED */
    public static final String PROCESS_AED_REQUESTED_TO = "process.aedRequestedTo";
    /** 气道吸引出的分泌物 */
    public static final String PROCESS_AIRWAY_SECRETION = "process.airwaySecretion";
    /** 当前遵循的ACLS算法分支 */
    public static final String PROCESS_ALGORITHM_BRANCH = "process.algorithmBranch";
    /** 电击前所有人是否已离开 */
    public static final String PROCESS_ALL_CLEAR_FOR_SHOCK = "process.allClearForShock";
    /** 抗凝药物类型 */
    public static final String PROCESS_ANTICOAGULANT_DRUG = "process.anticoagulantDrug";
    /** 抗凝治疗情况是否已评估 */
    public static final String PROCESS_ANTICOAGULATION_ASSESSED = "process.anticoagulationAssessed";
    /** 识别到窄QRS不规则（房颤/房扑）时系统置位，提示学员评估抗凝 */
    public static final String PROCESS_ANTICOAGULATION_DECISION_TRIGGERED = "process.anticoagulationDecisionTriggered";
    /** 失语症评估结果 */
    public static final String PROCESS_APHASIA = "process.aphasia";
    /** 失语症严重程度 */
    public static final String PROCESS_APHASIA_SEVERITY = "process.aphasiaSeverity";
    /** 接近患者的具体动作 */
    public static final String PROCESS_APPROACH_ACTION = "process.approachAction";
    /** 手臂漂移检查结果 */
    public static final String PROCESS_ARM_DRIFT_CHECKED = "process.armDriftChecked";
    /** 手臂下垂观察时长 */
    public static final String PROCESS_ARM_DRIFT_DURATION_SEC = "process.armDriftDurationSec";
    /** 是否已请求协助 */
    public static final String PROCESS_ASSISTANCE_REQUESTED = "process.assistanceRequested";
    /** 请求协助对象类型 */
    public static final String PROCESS_ASSISTANCE_TYPE = "process.assistanceType";
    /** 平衡能力评估结果 */
    public static final String PROCESS_BALANCE_CHECKED = "process.balanceChecked";
    /** 表示学员已完成BLS初步评估 */
    public static final String PROCESS_BLS_SURVEY_COMPLETE = "process.blsSurveyComplete";
    /** 血压是否已测量 */
    public static final String PROCESS_BP_MEASURED = "process.bpMeasured";
    /** 呼吸评估使用的手法 */
    public static final String PROCESS_BREATHING_TECHNIQUE = "process.breathingTechnique";
    /** 是否已执行同步电复律（稳定但药物无效后的选择） */
    public static final String PROCESS_CARDIOVERSION_PERFORMED = "process.cardioversionPerformed";
    /** 是否在未给予镇静的情况下执行了同步电复律（用于评分/告警） */
    public static final String PROCESS_CARDIOVERSION_WITHOUT_SEDATION = "process.cardioversionWithoutSedation";
    /** 当前案例类型编码 */
    public static final String PROCESS_CASE_TYPE = "process.caseType";
    /** 胸部是否已暴露 */
    public static final String PROCESS_CHEST_EXPOSED = "process.chestExposed";
    /** 胸廓是否完全回弹 */
    public static final String PROCESS_CHEST_RECOIL = "process.chestRecoil";
    /** 分析心律前是否已清场 */
    public static final String PROCESS_CLEAR_FOR_ANALYSIS = "process.clearForAnalysis";
    /** 电击前是否已清场 */
    public static final String PROCESS_CLEAR_FOR_SHOCK = "process.clearForShock";
    /** 是否持续胸外按压不停 */
    public static final String PROCESS_COMPRESSION_CONTINUOUS = "process.compressionContinuous";
    /** 胸外按压深度 */
    public static final String PROCESS_COMPRESSION_DEPTH = "process.compressionDepth";
    /** 平均按压深度 */
    public static final String PROCESS_COMPRESSION_DEPTH_AVG = "process.compressionDepthAvg";
    /** 胸外按压是否已暂停 */
    public static final String PROCESS_COMPRESSION_PAUSED = "process.compressionPaused";
    /** 胸外按压频率 */
    public static final String PROCESS_COMPRESSION_RATE = "process.compressionRate";
    /** 平均按压频率 */
    public static final String PROCESS_COMPRESSION_RATE_AVG = "process.compressionRateAvg";
    /** 意识状态是否已检查 */
    public static final String PROCESS_CONSCIOUSNESS_CHECKED = "process.consciousnessChecked";
    /** 专家咨询是否已呼叫 */
    public static final String PROCESS_CONSULT_CALLED = "process.consultCalled";
    /** 禁忌操作拦截是否已触发 */
    public static final String PROCESS_CONTRAINDICATION_BLOCK_TRIGGERED = "process.contraindicationBlockTriggered";
    /** 操作禁忌症是否已检查 */
    public static final String PROCESS_CONTRAINDICATIONS_CHECKED = "process.contraindicationsChecked";
    /** 协调能力评估结果 */
    public static final String PROCESS_COORDINATION_CHECKED = "process.coordinationChecked";
    /** 已完成的CPR周期数 */
    public static final String PROCESS_CPR_CYCLES = "process.cprCycles";
    /** 按压时间占总复苏时间的比例 */
    public static final String PROCESS_CPR_FRACTION = "process.cprFraction";
    /** 按压中断累计时长 */
    public static final String PROCESS_CPR_INTERRUPTION_DURATION = "process.cprInterruptionDuration";
    /** 胸外按压中断的总次数 */
    public static final String PROCESS_CPR_INTERRUPTIONS = "process.cprInterruptions";
    /** 胸外按压开始时间戳 */
    public static final String PROCESS_CPR_START_TIME = "process.cprStartTime";
    /** 胸外按压结束时间戳 */
    public static final String PROCESS_CPR_STOP_TIME = "process.cprStopTime";
    /** 当前状态机节点 */
    public static final String PROCESS_CURRENT_STATE = "process.currentState";
    /** ROSC前的除颤电击次数 */
    public static final String PROCESS_DEFIB_ATTEMPTS_BEFORE_ROSC = "process.defibAttemptsBeforeROSC";
    /** 是否偏离标准流程 */
    public static final String PROCESS_DEVIATION_FLAG = "process.deviationFlag";
    /** 偏离标准流程的原因描述 */
    public static final String PROCESS_DEVIATION_REASON = "process.deviationReason";
    /** 药物诱发室颤警报是否已显示 */
    public static final String PROCESS_DRUG_INDUCED_VF_ALERT_SHOWN = "process.drugInducedVfAlertShown";
    /** ROSC前的给药次数 */
    public static final String PROCESS_DRUGS_BEFORE_ROSC = "process.drugsBeforeROSC";
    /** 心电图记录工件ID */
    public static final String PROCESS_ECG_ARTIFACT_ID = "process.ecgArtifactId";
    /** 患者是否知情同意 */
    public static final String PROCESS_ECG_CONSENT = "process.ecgConsent";
    /** 心电图操作是否已向患者解释 */
    public static final String PROCESS_ECG_EXPLAINED = "process.ecgExplained";
    /** 心电图判读结果 */
    public static final String PROCESS_ECG_INTERPRETATION = "process.ecgInterpretation";
    /** 12导联心电图是否已完成记录 */
    public static final String PROCESS_ECG_RECORDED = "process.ecgRecorded";
    /** 心电图是否已报告 */
    public static final String PROCESS_ECG_REPORTED = "process.ecgReported";
    /** 是否因血流动力学不稳定而执行紧急同步电复律 */
    public static final String PROCESS_EMERGENCY_CARDIOVERSION_PERFORMED = "process.emergencyCardioversionPerformed";
    /** 是否处于紧急处理模式 */
    public static final String PROCESS_EMERGENCY_MODE = "process.emergencyMode";
    /** 操作环境是否安全 */
    public static final String PROCESS_ENVIRONMENT_SAFE = "process.environmentSafe";
    /** 错误是否已纠正 */
    public static final String PROCESS_ERROR_FIXED = "process.errorFixed";
    /** 是否发生了错误 */
    public static final String PROCESS_ERROR_OCCURRED = "process.errorOccurred";
    /** 错误的具体类型 */
    public static final String PROCESS_ERROR_TYPE = "process.errorType";
    /** 预计到达医院时间 */
    public static final String PROCESS_ETA = "process.eta";
    /** 面部下垂检查结果 */
    public static final String PROCESS_FACE_DROOP_CHECKED = "process.faceDroopChecked";
    /** 家属情况是否已检查 */
    public static final String PROCESS_FAMILY_CHECKED = "process.familyChecked";
    /** 场景结束时的心律 */
    public static final String PROCESS_FINAL_RHYTHM = "process.finalRhythm";
    /** 首次接触患者的时间戳 */
    public static final String PROCESS_FIRST_CONTACT_TIME = "process.firstContactTime";
    /** 首次除颤电击的时间戳 */
    public static final String PROCESS_FIRST_DEFIB_TIME = "process.firstDefibTime";
    /** 首次推注药物的时间戳 */
    public static final String PROCESS_FIRST_DRUG_TIME = "process.firstDrugTime";
    /** 首次获取心电图的时间戳 */
    public static final String PROCESS_FIRST_ECGTIME = "process.firstECGTime";
    /** 双侧握力对称性评估 */
    public static final String PROCESS_GRIP_SYMMETRY_CHECKED = "process.gripSymmetryChecked";
    /** 患者交接是否已完成 */
    public static final String PROCESS_HANDOFF_DONE = "process.handoffDone";
    /** 患者交接形式 */
    public static final String PROCESS_HANDOFF_FORMAT = "process.handoffFormat";
    /** 血流动力学恶化警报是否已触发 */
    public static final String PROCESS_HEMODYNAMIC_DETERIORATION_ALERT = "process.hemodynamicDeteriorationAlert";
    /** 血流动力学稳定性是否已评估 */
    public static final String PROCESS_HEMODYNAMICS_ASSESSED = "process.hemodynamicsAssessed";
    /** 病史是否已采集 */
    public static final String PROCESS_HISTORY_COLLECTED = "process.historyCollected";
    /** 到达医院的时间戳 */
    public static final String PROCESS_HOSPITAL_ARRIVAL_TIME = "process.hospitalArrivalTime";
    /** 接收医院是否已通知 */
    public static final String PROCESS_HOSPITAL_NOTIFIED = "process.hospitalNotified";
    /** 现病史采集结果 */
    public static final String PROCESS_HPI = "process.hpi";
    /** 心率是否已测量 */
    public static final String PROCESS_HR_MEASURED = "process.hrMeasured";
    /** 首次记录的心律 */
    public static final String PROCESS_INITIAL_RHYTHM = "process.initialRhythm";
    /** 是否触发了不稳定标志 */
    public static final String PROCESS_INSTABILITY_TRIGGERED = "process.instabilityTriggered";
    /** 最后一次错误信息 */
    public static final String PROCESS_LAST_ERROR = "process.lastError";
    /** 上次冲管的时间戳 */
    public static final String PROCESS_LAST_FLUSH_TIME = "process.lastFlushTime";
    /** 上次干预的时间戳 */
    public static final String PROCESS_LAST_INTERVENTION_TIME = "process.lastInterventionTime";
    /** 上次电击的时间戳 */
    public static final String PROCESS_LAST_SHOCK_TIME = "process.lastShockTime";
    /** LKW 时间 */
    public static final String PROCESS_LKW_TIME = "process.lkwTime";
    /** LKW 目击者情况 */
    public static final String PROCESS_LKW_WITNESS = "process.lkwWitness";
    /** 用药询问上下文 */
    public static final String PROCESS_MEDICATION_CONTEXT = "process.medicationContext";
    /** 是否存在忽略体征 */
    public static final String PROCESS_NEGLECT_PRESENT = "process.neglectPresent";
    /** 忽略体征侧 */
    public static final String PROCESS_NEGLECT_SIGNS = "process.neglectSigns";
    /** 分析时无人接触患者 */
    public static final String PROCESS_NO_CONTACT_DURING_ANALYSIS = "process.noContactDuringAnalysis";
    /** 主要操作是否已开始 */
    public static final String PROCESS_OPERATION_STARTED = "process.operationStarted";
    /** 当前操作执行状态 */
    public static final String PROCESS_OPERATION_STATUS = "process.operationStatus";
    /** 当前执行的操作类型（vagal / cardioversion / drug_push 等） */
    public static final String PROCESS_OPERATION_TYPE = "process.operationType";
    /** 血氧饱和度是否已测量 */
    public static final String PROCESS_OXIMETRY_MEASURED = "process.oximetryMeasured";
    /** 记录期间患者活动情况 */
    public static final String PROCESS_PATIENT_MOVEMENT = "process.patientMovement";
    /** 向医院的患者报告内容 */
    public static final String PROCESS_PATIENT_REPORT = "process.patientReport";
    /** 患者是否已固定在担架上 */
    public static final String PROCESS_PATIENT_SECURED = "process.patientSecured";
    /** 患者是否处于稳定状态 */
    public static final String PROCESS_PATIENT_STABLE = "process.patientStable";
    /** 已要求患者在记录期间保持不动 */
    public static final String PROCESS_PATIENT_STILL = "process.patientStill";
    /** 患者是否处于不稳定状态 */
    public static final String PROCESS_PATIENT_UNSTABLE = "process.patientUnstable";
    /** PEA停搏警报是否已显示 */
    public static final String PROCESS_PEA_ASYSTOLE_ALERT_SHOWN = "process.peaAsystoleAlertShown";
    /** 最近一次电击后已过的秒数 */
    public static final String PROCESS_POST_SHOCK_ELAPSED = "process.postShockElapsed";
    /** 前一个状态机节点 */
    public static final String PROCESS_PREV_STATE = "process.prevState";
    /** QRS宽度是否已检查（稳定窄QRS场景） */
    public static final String PROCESS_QRS_WIDTH_CHECKED = "process.qrsWidthChecked";
    /** 患者是否已完成重新评估 */
    public static final String PROCESS_REASSESSED = "process.reassessed";
    /** 语言检查重复短语 */
    public static final String PROCESS_REPEATED_PHRASE = "process.repeatedPhrase";
    /** 子级返回父级的原因 */
    public static final String PROCESS_RETURN_TO_PARENT = "process.returnToParent";
    /** ROSC时的心律 */
    public static final String PROCESS_RHYTHM_AT_ROSC = "process.rhythmAtROSC";
    /** 心律解读是否已请求 */
    public static final String PROCESS_RHYTHM_INTERPRETATION_REQUESTED = "process.rhythmInterpretationRequested";
    /** 节律规则性是否已检查（稳定窄QRS场景） */
    public static final String PROCESS_RHYTHM_REGULARITY_CHECKED = "process.rhythmRegularityChecked";
    /** 确认ROSC的时间戳 */
    public static final String PROCESS_ROSC_TIME = "process.roscTime";
    /** 安全清场是否已完成 */
    public static final String PROCESS_SAFETY_CLEARED = "process.safetyCleared";
    /** 离开现场的时间戳 */
    public static final String PROCESS_SCENE_EXIT_TIME = "process.sceneExitTime";
    /** 是否已对镇静需求/禁忌进行评估 */
    public static final String PROCESS_SEDATION_ASSESSED = "process.sedationAssessed";
    /** 会话时钟状态 */
    public static final String PROCESS_SESSION_CLOCK_STATUS = "process.sessionClockStatus";
    /** 会话开始时间 */
    public static final String PROCESS_SESSION_START_TIME = "process.sessionStartTime";
    /** 除颤电击累计次数 */
    public static final String PROCESS_SHOCK_COUNT = "process.shockCount";
    /** 最近一次电击是否已完成 */
    public static final String PROCESS_SHOCK_DONE = "process.shockDone";
    /** 每次电击的能量设置序列 */
    public static final String PROCESS_SHOCK_ENERGY_SEQUENCE = "process.shockEnergySequence";
    /** 每次电击的结果序列 */
    public static final String PROCESS_SHOCK_OUTCOME_SEQUENCE = "process.shockOutcomeSequence";
    /** 每次电击的时间戳序列 */
    public static final String PROCESS_SHOCK_TIMING = "process.shockTiming";
    /** 语言异常检查结果 */
    public static final String PROCESS_SPEECH_CHECKED = "process.speechChecked";
    /** 在当前状态停留的秒数 */
    public static final String PROCESS_STATE_DURATION = "process.stateDuration";
    /** 当前状态进入时间戳 */
    public static final String PROCESS_STATE_ENTER_TIME = "process.stateEnterTime";
    /** 状态机步骤标记。仅由 transition effects 写入。 */
    public static final String PROCESS_STEP_CLEAR_ANNOUNCED = "process.stepClearAnnounced";
    /** 状态机步骤标记。仅由 transition effects 写入。 */
    public static final String PROCESS_STEP_DEFIB_CHARGED = "process.stepDefibCharged";
    /** 状态机步骤标记。仅由 transition effects 写入。 */
    public static final String PROCESS_STEP_ENERGY_SELECTED = "process.stepEnergySelected";
    /** 状态机步骤标记。仅由 transition effects 写入。 */
    public static final String PROCESS_STEP_SEDATION_GIVEN = "process.stepSedationGiven";
    /** 状态机步骤标记。仅由 transition effects 写入。用于让 F-102-PREP_CARDIOVERSION 的 Guard 保持事前语义。与 device.defibSyncMode 解耦。 */
    public static final String PROCESS_STEP_SYNC_MODE_REQUESTED = "process.stepSyncModeRequested";
    /** 反应评估使用的刺激方式 */
    public static final String PROCESS_STIMULUS_TYPE = "process.stimulusType";
    /** 固定带使用数量 */
    public static final String PROCESS_STRAPS_COUNT = "process.strapsCount";
    /** 策略是否已被覆盖 */
    public static final String PROCESS_STRATEGY_OVERRIDDEN = "process.strategyOverridden";
    /** 患者是否已上担架 */
    public static final String PROCESS_STRETCHER_LOADED = "process.stretcherLoaded";
    /** 卒中警报是否已激活 */
    public static final String PROCESS_STROKE_ALERT_ACTIVATED = "process.strokeAlertActivated";
    /** 卒中累及侧 */
    public static final String PROCESS_STROKE_SIDE = "process.strokeSide";
    /** 卒中团队是否已通知 */
    public static final String PROCESS_STROKE_TEAM_NOTIFIED = "process.strokeTeamNotified";
    /** 物质使用频率描述 */
    public static final String PROCESS_SUBSTANCE_FREQUENCY = "process.substanceFrequency";
    /** 酒精或药物使用情况 */
    public static final String PROCESS_SUBSTANCE_USE = "process.substanceUse";
    /** 吸引器清理是否已完成 */
    public static final String PROCESS_SUCTION_DONE = "process.suctionDone";
    /** 是否疑似酸中毒 */
    public static final String PROCESS_SUSPECTED_ACIDOSIS = "process.suspectedAcidosis";
    /** 是否疑似高钾血症 */
    public static final String PROCESS_SUSPECTED_HYPERKALEMIA = "process.suspectedHyperkalemia";
    /** 是否疑似低体温 */
    public static final String PROCESS_SUSPECTED_HYPOTHERMIA = "process.suspectedHypothermia";
    /** 是否疑似低血容量 */
    public static final String PROCESS_SUSPECTED_HYPOVOLEMIA = "process.suspectedHypovolemia";
    /** 是否疑似肺栓塞 */
    public static final String PROCESS_SUSPECTED_PE = "process.suspectedPe";
    /** 学员疑似的心律类型 */
    public static final String PROCESS_SUSPECTED_RHYTHM = "process.suspectedRhythm";
    /** 是否疑似心包填塞 */
    public static final String PROCESS_SUSPECTED_TAMPONADE = "process.suspectedTamponade";
    /** 是否疑似张力性气胸 */
    public static final String PROCESS_SUSPECTED_TENSION_PNEUMOTHORAX = "process.suspectedTensionPneumothorax";
    /** 是否疑似药物或毒物中毒 */
    public static final String PROCESS_SUSPECTED_TOXICITY = "process.suspectedToxicity";
    /** 从学员陈述中抽取的症状标准编码集合，供血流动力学稳定性判定与审计 */
    public static final String PROCESS_SYMPTOMS = "process.symptoms";
    /** 团队成员角色是否已分配 */
    public static final String PROCESS_TEAM_ROLES_ASSIGNED = "process.teamRolesAssigned";
    /** 团队决定终止复苏 */
    public static final String PROCESS_TERMINATE_RESUSCITATION = "process.terminateResuscitation";
    /** 终止复苏相关记录是否已全部填写完毕 */
    public static final String PROCESS_TERMINATION_DOCUMENTED = "process.terminationDocumented";
    /** 距离上次治疗干预的秒数 */
    public static final String PROCESS_TIME_ELAPSED_SINCE_LAST_INTERVENTION = "process.timeElapsedSinceLastIntervention";
    /** 关键时间点是否已记录 */
    public static final String PROCESS_TIME_RECORDED = "process.timeRecorded";
    /** 是否发生了超时 */
    public static final String PROCESS_TIMEOUT_OCCURRED = "process.timeoutOccurred";
    /** 超时是否已解决 */
    public static final String PROCESS_TIMEOUT_RESOLVED = "process.timeoutResolved";
    /** 总干预操作次数 */
    public static final String PROCESS_TOTAL_INTERVENTIONS = "process.totalInterventions";
    /** 患者搬运方式 */
    public static final String PROCESS_TRANSFER_METHOD = "process.transferMethod";
    /** 转运准备是否已完成 */
    public static final String PROCESS_TRANSPORT_READY = "process.transportReady";
    /** 转运是否已开始 */
    public static final String PROCESS_TRANSPORT_STARTED = "process.transportStarted";
    /** 转运目的地 */
    public static final String PROCESS_TRANSPORT_TARGET = "process.transportTarget";
    /** 治疗效果是否已报告 */
    public static final String PROCESS_TREATMENT_RESULT_REPORTED = "process.treatmentResultReported";
    /** 最近一次 Tutor 警告的 message key（如 feedback_adenosine_recheck_warn） */
    public static final String PROCESS_TUTOR_WARNING = "process.tutorWarning";
    /** 最近一次 Tutor 警告的时间戳（ms） */
    public static final String PROCESS_TUTOR_WARNING_AT = "process.tutorWarningAt";
    /** 非同步电击室颤警报是否已显示 */
    public static final String PROCESS_UNSYNCHRONIZED_SHOCK_VF_ALERT_SHOWN = "process.unsynchronizedShockVfAlertShown";
    /** 迷走神经刺激的转复效果 */
    public static final String PROCESS_VAGAL_EFFECT_OBSERVED = "process.vagalEffectObserved";
    /** Valsalva 或颈动脉窦按摩是否已执行（窄QRS规则首选） */
    public static final String PROCESS_VAGAL_MANEUVER_PERFORMED = "process.vagalManeuverPerformed";
    /** 患者是否跳过了迷走神经刺激直接用药 */
    public static final String PROCESS_VAGAL_SKIPPED_WARNING = "process.vagalSkippedWarning";
    /** 是否已完成通气 */
    public static final String PROCESS_VENTILATION_DONE = "process.ventilationDone";
    /** 人工通气频率 */
    public static final String PROCESS_VENTILATION_RATE = "process.ventilationRate";
    /** 视野缺损评估 */
    public static final String PROCESS_VISION_DEFICIT = "process.visionDeficit";
    /** 视野切割情况 */
    public static final String PROCESS_VISION_FIELD_CUT = "process.visionFieldCut";
    /** WPW警报是否已显示 */
    public static final String PROCESS_WPW_ALERT_SHOWN = "process.wpwAlertShown";
    /** WPW警报是否已触发 */
    public static final String PROCESS_WPW_ALERT_TRIGGERED = "process.wpwAlertTriggered";
}
