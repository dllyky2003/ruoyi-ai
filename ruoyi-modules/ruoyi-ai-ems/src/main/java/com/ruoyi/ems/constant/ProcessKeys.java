// ============================================================
// ⚠️ 自动生成代码 - 请勿手动编辑
// 源文件: _variables.json
// 生成时间: Mon Sep 14 12:10:45 CST 2026
// 修改后请执行 mvn generate-sources 重新生成
// ============================================================

package com.ruoyi.ems.constant;

/**
 * ProcessKeys
 * <p>包含 100 个变量常量
 * <p>使用示例: <code>String key = ProcessKeys.HEART_RATE;</code>
 */
public final class ProcessKeys {

    private ProcessKeys() {}

    /** 闭环确认是否已完成 */
    public static final String PROCESS_ACKNOWLEDGED = "process.acknowledged";
    /** 当前遵循的ACLS算法分支 */
    public static final String PROCESS_ALGORITHM_BRANCH = "process.algorithmBranch";
    /** 抗凝治疗情况是否已评估 */
    public static final String PROCESS_ANTICOAGULATION_ASSESSED = "process.anticoagulationAssessed";
    /** 表示学员已完成BLS初步评估（安全、反应、呼吸、脉搏检查） */
    public static final String PROCESS_BLS_SURVEY_COMPLETE = "process.blsSurveyComplete";
    /** 血压是否已测量 */
    public static final String PROCESS_BP_MEASURED = "process.bpMeasured";
    /** 当前案例类型编码 */
    public static final String PROCESS_CASE_TYPE = "process.caseType";
    /** 胸廓是否完全回弹 */
    public static final String PROCESS_CHEST_RECOIL = "process.chestRecoil";
    /** 胸外按压深度 */
    public static final String PROCESS_COMPRESSION_DEPTH = "process.compressionDepth";
    /** 平均按压深度 */
    public static final String PROCESS_COMPRESSION_DEPTH_AVG = "process.compressionDepthAvg";
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
    /** 12导联心电图是否已完成记录 */
    public static final String PROCESS_ECG_RECORDED = "process.ecgRecorded";
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
    /** 血流动力学恶化警报是否已触发 */
    public static final String PROCESS_HEMODYNAMIC_DETERIORATION_ALERT = "process.hemodynamicDeteriorationAlert";
    /** 血流动力学稳定性是否已评估 */
    public static final String PROCESS_HEMODYNAMICS_ASSESSED = "process.hemodynamicsAssessed";
    /** 病史是否已采集 */
    public static final String PROCESS_HISTORY_COLLECTED = "process.historyCollected";
    /** 到达医院的时间戳 */
    public static final String PROCESS_HOSPITAL_ARRIVAL_TIME = "process.hospitalArrivalTime";
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
    /** 主要操作是否已开始 */
    public static final String PROCESS_OPERATION_STARTED = "process.operationStarted";
    /** 当前操作执行状态 */
    public static final String PROCESS_OPERATION_STATUS = "process.operationStatus";
    /** 血氧饱和度是否已测量 */
    public static final String PROCESS_OXIMETRY_MEASURED = "process.oximetryMeasured";
    /** 患者是否处于稳定状态 */
    public static final String PROCESS_PATIENT_STABLE = "process.patientStable";
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
    /** 在当前状态停留的秒数 */
    public static final String PROCESS_STATE_DURATION = "process.stateDuration";
    /** 当前状态进入时间戳 */
    public static final String PROCESS_STATE_ENTER_TIME = "process.stateEnterTime";
    /** 策略是否已被覆盖 */
    public static final String PROCESS_STRATEGY_OVERRIDDEN = "process.strategyOverridden";
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
    /** 团队成员角色是否已分配 */
    public static final String PROCESS_TEAM_ROLES_ASSIGNED = "process.teamRolesAssigned";
    /** 团队决定终止复苏（通常因治疗无效、符合终止标准、家属放弃等）。设置为 true 时触发终止复苏流程。 */
    public static final String PROCESS_TERMINATE_RESUSCITATION = "process.terminateResuscitation";
    /** 终止复苏相关记录（死亡时间、停止原因、家属沟通、文书签署等）是否已全部填写完毕 */
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
    /** 转运准备是否已完成 */
    public static final String PROCESS_TRANSPORT_READY = "process.transportReady";
    /** 治疗效果是否已报告 */
    public static final String PROCESS_TREATMENT_RESULT_REPORTED = "process.treatmentResultReported";
    /** 非同步电击室颤警报是否已显示 */
    public static final String PROCESS_UNSYNCHRONIZED_SHOCK_VF_ALERT_SHOWN = "process.unsynchronizedShockVfAlertShown";
    /** 人工通气频率 */
    public static final String PROCESS_VENTILATION_RATE = "process.ventilationRate";
    /** WPW警报是否已显示 */
    public static final String PROCESS_WPW_ALERT_SHOWN = "process.wpwAlertShown";
    /** WPW警报是否已触发 */
    public static final String PROCESS_WPW_ALERT_TRIGGERED = "process.wpwAlertTriggered";
}
