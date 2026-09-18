# Skill 配置清单

**统计时间**: 2026-09-08
**配置目录**: `src/main/resources/config/skills/`


## 一、技能总数

| 层级 | 目录 | 文件数 | 技能数 |
| :--- | :--- | :--- | :--- |
| **L1：通用技能** | `common/` | 8 | 69 |
| **L2：家族共享** | `shared/` | 3 | 4 |
| **L3：案例专属** | `case_specific/` | 1 | 4 |
| **合计** | — | **12** | **77** |


## 二、L1 通用技能（`common/`）

### `01_abcs.yaml`（4个）

| 技能ID | 类型 | 职责 | 说明 |
| :--- | :--- | :--- | :--- |
| `approach_patient` | query | ASSESS | 现场安全评估 |
| `check_responsiveness` | query | ASSESS | 检查患者反应 |
| `check_pulse` | query | ASSESS | 检查脉搏 |
| `check_breathing` | query | ASSESS | 检查呼吸 |

### `02_vitals.yaml`（9个）

| 技能ID | 类型 | 职责 | 说明 |
| :--- | :--- | :--- | :--- |
| `check_blood_pressure` | query | ASSESS | 测量血压 |
| `check_heart_rate` | query | ASSESS | 检查心率 |
| `check_oxygen_saturation` | query | ASSESS | 测量血氧 |
| `check_respiratory_rate` | query | ASSESS | 检查呼吸频率 |
| `prepare_glucometer_and_strip` | standard | ASSESS | 准备血糖仪 |
| `read_and_record_glucose_level` | query | ASSESS | 读取血糖 |
| `check_perrl` | query | ASSESS | 检查瞳孔 |
| `check_skin_condition` | query | ASSESS | 评估皮肤 |
| `check_ao` | query | ASSESS | 检查意识定向力 |

### `03_airway.yaml`（5个）

| 技能ID | 类型 | 职责 | 说明 |
| :--- | :--- | :--- | :--- |
| `open_airway` | standard | AIRWAY | 开放气道 |
| `place_bvm` | standard | AIRWAY | 放置球囊面罩 |
| `ventilate_patient` | standard | AIRWAY | 实施通气 |
| `suction_airway` | standard | AIRWAY | 吸引气道 |
| `insert_npa` | standard | AIRWAY | 插入鼻咽通气管 |

### `04_cpr.yaml`（3个）

| 技能ID | 类型 | 职责 | 说明 |
| :--- | :--- | :--- | :--- |
| `cpr_compressions` | standard | CPR | 胸外按压 |
| `assess_patient_cpr` | query | ASSESS | CPR中评估 |
| `cpr_rotation` | standard | CPR | 按压员轮换 |

### `05_defibrillation.yaml`（7个）

| 技能ID | 类型 | 职责 | 说明 |
| :--- | :--- | :--- | :--- |
| `request_aed` | query | MONITOR_DEFIB | 请求AED |
| `request_assistance` | query | MONITOR_DEFIB | 呼叫协助 |
| `turn_on_aed` | standard | MONITOR_DEFIB | 开启AED |
| `attach_defib_pads` | generic | MONITOR_DEFIB | 贴除颤电极片 |
| `clear_for_analysis` | standard | MONITOR_DEFIB | 分析清场 |
| `clear_for_shock` | standard | MONITOR_DEFIB | 电击清场 |
| `administer_shock_aed` | complex | MONITOR_DEFIB | 实施电击 |

### `06_ecg.yaml`（18个）

| 技能ID | 类型 | 职责 | 说明 |
| :--- | :--- | :--- | :--- |
| `explain_ecg_procedure` | query | MONITOR_DEFIB | 解释ECG操作 |
| `shave_patient` | standard | MONITOR_DEFIB | 剃除皮肤 |
| `place_lead_la` | standard | MONITOR_DEFIB | 左臂导联 |
| `place_lead_ra` | standard | MONITOR_DEFIB | 右臂导联 |
| `place_lead_ll` | standard | MONITOR_DEFIB | 左腿导联 |
| `place_lead_rl` | standard | MONITOR_DEFIB | 右腿导联 |
| `place_lead_v1` | standard | MONITOR_DEFIB | V1导联 |
| `place_lead_v2` | standard | MONITOR_DEFIB | V2导联 |
| `place_lead_v3` | standard | MONITOR_DEFIB | V3导联 |
| `place_lead_v4` | standard | MONITOR_DEFIB | V4导联 |
| `place_lead_v5` | standard | MONITOR_DEFIB | V5导联 |
| `place_lead_v6` | standard | MONITOR_DEFIB | V6导联 |
| `request_patient_not_move` | standard | MONITOR_DEFIB | 要求患者不动 |
| `turn_on_ecg` | standard | MONITOR_DEFIB | 开启心电图机 |
| `connect_leads_to_ecg` | standard | MONITOR_DEFIB | 连接导联 |
| `ask_patient_age_sex` | query | MONITOR_DEFIB | 询问年龄性别 |
| `obtain_ecg_recording` | standard | MONITOR_DEFIB | 记录心电图 |
| `interpret_ecg` | query | MONITOR_DEFIB | 判读心电图 |

### `07_transport.yaml`（6个）

| 技能ID | 类型 | 职责 | 说明 |
| :--- | :--- | :--- | :--- |
| `transport_to_ambulance` | standard | ASSIST | 转运 |
| `load_to_stretcher` | standard | ASSIST | 抬上担架 |
| `secure_patient` | standard | ASSIST | 固定患者 |
| `notify_hospital` | query | RECORD | 通知医院 |
| `notify_stroke_alert` | query | RECORD | 卒中警报 |
| `handoff_patient` | query | RECORD | 交接患者 |

### `08_team.yaml`（17个）

| 技能ID | 类型 | 职责 | 说明 |
| :--- | :--- | :--- | :--- |
| `safety_clear` | standard | MONITOR_DEFIB | 安全清场 |
| `check_contraindications` | standard | MONITOR_DEFIB | 检查禁忌症 |
| `check_environment` | standard | ASSESS | 环境检查 |
| `assign_role` | query | TEAM_LEADER | 角色分配 |
| `confirm_ack` | query | ASSIST | 确认闭环 |
| `record_time` | query | RECORD | 记录时间 |
| `consult_expert` | query | TEAM_LEADER | 专家会诊 |
| `start_operation` | standard | TEAM_LEADER | 操作启动 |
| `check_family` | query | ASSESS | 家属确认 |
| `prepare_transport` | standard | TEAM_LEADER | 转运准备 |
| `reassess_after_intervention` | standard | ASSESS | 复评 |
| `collect_history` | standard | ASSESS | 采集病史 |
| `device_setup` | standard | MONITOR_DEFIB | 设备设置 |
| `connect_leads` | standard | MONITOR_DEFIB | 连接导联 |
| `establish_access` | standard | IV_MED | 建立静脉通路 |
| `defib_setup` | standard | MONITOR_DEFIB | 除颤准备 |
| `check_defib_ready` | standard | MONITOR_DEFIB | 除颤就绪检查 |


## 三、L2 家族共享技能（`shared/`）

### `tachycardia/drug_adenosine.yaml`（2个）

| 技能ID | 类型 | 职责 | 说明 |
| :--- | :--- | :--- | :--- |
| `drug_push_adenosine` | complex | IV_MED | 腺苷推注（6mg/12mg） |
| `flush_adenosine` | standard | IV_MED | 腺苷冲管 |

### `tachycardia/cardioversion.yaml`（1个）

| 技能ID | 类型 | 职责 | 说明 |
| :--- | :--- | :--- | :--- |
| `synchronized_cardioversion` | complex | MONITOR_DEFIB | 同步电复律 |

### `cardiac_arrest/drug_epinephrine.yaml`（1个）

| 技能ID | 类型 | 职责 | 说明 |
| :--- | :--- | :--- | :--- |
| `drug_push_epinephrine` | complex | IV_MED | 肾上腺素推注 |


## 四、L3 案例专属技能（`case_specific/stable_narrow/`）

### `specific.yaml`（4个）

| 技能ID | 类型 | 职责 | 说明 |
| :--- | :--- | :--- | :--- |
| `assert_qrs_width` | generic_assert | MONITOR_DEFIB | QRS宽度判读 |
| `assert_rhythm_regular` | generic_assert | MONITOR_DEFIB | 节律规则性判读 |
| `assert_hemodynamics` | generic_assert | ASSESS | 血流动力学判读 |
| `vagal_maneuver` | standard | ASSESS | 迷走神经刺激 |


## 五、技能ID完整清单（77个）

| 序号 | 技能ID | 层级 | 类型 | 职责 |
| :--- | :--- | :--- | :--- | :--- |
| 1 | `approach_patient` | L1 | query | ASSESS |
| 2 | `check_responsiveness` | L1 | query | ASSESS |
| 3 | `check_pulse` | L1 | query | ASSESS |
| 4 | `check_breathing` | L1 | query | ASSESS |
| 5 | `check_blood_pressure` | L1 | query | ASSESS |
| 6 | `check_heart_rate` | L1 | query | ASSESS |
| 7 | `check_oxygen_saturation` | L1 | query | ASSESS |
| 8 | `check_respiratory_rate` | L1 | query | ASSESS |
| 9 | `prepare_glucometer_and_strip` | L1 | standard | ASSESS |
| 10 | `read_and_record_glucose_level` | L1 | query | ASSESS |
| 11 | `check_perrl` | L1 | query | ASSESS |
| 12 | `check_skin_condition` | L1 | query | ASSESS |
| 13 | `check_ao` | L1 | query | ASSESS |
| 14 | `open_airway` | L1 | standard | AIRWAY |
| 15 | `place_bvm` | L1 | standard | AIRWAY |
| 16 | `ventilate_patient` | L1 | standard | AIRWAY |
| 17 | `suction_airway` | L1 | standard | AIRWAY |
| 18 | `insert_npa` | L1 | standard | AIRWAY |
| 19 | `cpr_compressions` | L1 | standard | CPR |
| 20 | `assess_patient_cpr` | L1 | query | ASSESS |
| 21 | `cpr_rotation` | L1 | standard | CPR |
| 22 | `request_aed` | L1 | query | MONITOR_DEFIB |
| 23 | `request_assistance` | L1 | query | MONITOR_DEFIB |
| 24 | `turn_on_aed` | L1 | standard | MONITOR_DEFIB |
| 25 | `attach_defib_pads` | L1 | generic | MONITOR_DEFIB |
| 26 | `clear_for_analysis` | L1 | standard | MONITOR_DEFIB |
| 27 | `clear_for_shock` | L1 | standard | MONITOR_DEFIB |
| 28 | `administer_shock_aed` | L1 | complex | MONITOR_DEFIB |
| 29 | `explain_ecg_procedure` | L1 | query | MONITOR_DEFIB |
| 30 | `shave_patient` | L1 | standard | MONITOR_DEFIB |
| 31 | `place_lead_la` | L1 | standard | MONITOR_DEFIB |
| 32 | `place_lead_ra` | L1 | standard | MONITOR_DEFIB |
| 33 | `place_lead_ll` | L1 | standard | MONITOR_DEFIB |
| 34 | `place_lead_rl` | L1 | standard | MONITOR_DEFIB |
| 35 | `place_lead_v1` | L1 | standard | MONITOR_DEFIB |
| 36 | `place_lead_v2` | L1 | standard | MONITOR_DEFIB |
| 37 | `place_lead_v3` | L1 | standard | MONITOR_DEFIB |
| 38 | `place_lead_v4` | L1 | standard | MONITOR_DEFIB |
| 39 | `place_lead_v5` | L1 | standard | MONITOR_DEFIB |
| 40 | `place_lead_v6` | L1 | standard | MONITOR_DEFIB |
| 41 | `request_patient_not_move` | L1 | standard | MONITOR_DEFIB |
| 42 | `turn_on_ecg` | L1 | standard | MONITOR_DEFIB |
| 43 | `connect_leads_to_ecg` | L1 | standard | MONITOR_DEFIB |
| 44 | `ask_patient_age_sex` | L1 | query | MONITOR_DEFIB |
| 45 | `obtain_ecg_recording` | L1 | standard | MONITOR_DEFIB |
| 46 | `interpret_ecg` | L1 | query | MONITOR_DEFIB |
| 47 | `transport_to_ambulance` | L1 | standard | ASSIST |
| 48 | `load_to_stretcher` | L1 | standard | ASSIST |
| 49 | `secure_patient` | L1 | standard | ASSIST |
| 50 | `notify_hospital` | L1 | query | RECORD |
| 51 | `notify_stroke_alert` | L1 | query | RECORD |
| 52 | `handoff_patient` | L1 | query | RECORD |
| 53 | `safety_clear` | L1 | standard | MONITOR_DEFIB |
| 54 | `check_contraindications` | L1 | standard | MONITOR_DEFIB |
| 55 | `check_environment` | L1 | standard | ASSESS |
| 56 | `assign_role` | L1 | query | TEAM_LEADER |
| 57 | `confirm_ack` | L1 | query | ASSIST |
| 58 | `record_time` | L1 | query | RECORD |
| 59 | `consult_expert` | L1 | query | TEAM_LEADER |
| 60 | `start_operation` | L1 | standard | TEAM_LEADER |
| 61 | `check_family` | L1 | query | ASSESS |
| 62 | `prepare_transport` | L1 | standard | TEAM_LEADER |
| 63 | `reassess_after_intervention` | L1 | standard | ASSESS |
| 64 | `collect_history` | L1 | standard | ASSESS |
| 65 | `device_setup` | L1 | standard | MONITOR_DEFIB |
| 66 | `connect_leads` | L1 | standard | MONITOR_DEFIB |
| 67 | `establish_access` | L1 | standard | IV_MED |
| 68 | `defib_setup` | L1 | standard | MONITOR_DEFIB |
| 69 | `check_defib_ready` | L1 | standard | MONITOR_DEFIB |
| 70 | `drug_push_adenosine` | L2 | complex | IV_MED |
| 71 | `flush_adenosine` | L2 | standard | IV_MED |
| 72 | `synchronized_cardioversion` | L2 | complex | MONITOR_DEFIB |
| 73 | `drug_push_epinephrine` | L2 | complex | IV_MED |
| 74 | `assert_qrs_width` | L3 | generic_assert | MONITOR_DEFIB |
| 75 | `assert_rhythm_regular` | L3 | generic_assert | MONITOR_DEFIB |
| 76 | `assert_hemodynamics` | L3 | generic_assert | ASSESS |
| 77 | `vagal_maneuver` | L3 | standard | ASSESS |


## 六、统计汇总

### 按类型分布

| 类型 | 数量 | 占比 |
| :--- | :--- | :--- |
| `query` | 22 | 28.6% |
| `standard` | 43 | 55.8% |
| `generic` | 1 | 1.3% |
| `generic_assert` | 3 | 3.9% |
| `complex` | 5 | 6.5% |
| `complex` + `standard` | 3 | 3.9% |

### 按职责分布

| 职责 | 数量 | 说明 |
| :--- | :--- | :--- |
| `ASSESS` | 22 | 评估/检查 |
| `MONITOR_DEFIB` | 38 | 监护/除颤/ECG |
| `AIRWAY` | 5 | 气道管理 |
| `CPR` | 2 | 心肺复苏 |
| `IV_MED` | 6 | 静脉给药 |
| `ASSIST` | 5 | 协助/转运 |
| `RECORD` | 5 | 记录 |
| `TEAM_LEADER` | 4 | 团队领导 |

### 按层级分布

| 层级 | 数量 | 占比 |
| :--- | :--- | :--- |
| **L1**（common） | 69 | 89.6% |
| **L2**（shared） | 4 | 5.2% |
| **L3**（case_specific） | 4 | 5.2% |


## 七、后续维护

| 操作 | 说明 |
| :--- | :--- |
| 新增案例 | 在 `case_specific/{caseType}/specific.yaml` 添加 3-6 个专属技能 |
| 新增家族 | 在 `shared/{family}/` 添加共享技能 |
| 新增通用 | 在 `common/` 对应文件添加 |
| 删除技能 | 需同时删除 `semantic-routing.yaml` 中的路由映射 |
