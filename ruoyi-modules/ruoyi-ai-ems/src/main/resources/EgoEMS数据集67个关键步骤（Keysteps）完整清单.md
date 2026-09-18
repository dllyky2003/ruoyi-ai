# EgoEMS 数据集 67 个关键步骤（Keysteps）完整清单

## 一、数据集概述

**EgoEMS**（A High-Fidelity Multimodal Egocentric Dataset for Cognitive Assistance in Emergency Medical Services）是首个专为急救医疗服务（EMS）领域设计的高保真、多模态、多人称第一人称数据集。该数据集由弗吉尼亚大学研究团队发布，已被 **AAAI 2026** 接收。

### 数据集规模

- **233** 个模拟急救试验，共计 **20 小时** 视频数据
- **62** 名参与者，包括 **46** 名 EMS 专业人员
- **4** 种模态：视频（第一人称）+ 音频 + 智能手表 IMU + CPR 地面真实数据
- 覆盖 **3** 种场景：
    - 心脏骤停（76 试）
    - 疑似心脏病（23 试）
    - 中风（41 试）
- 共 **2,694** 个关键步骤标注实例

### 分类法构建依据

研究者分析了 **1500 万 +** EMS 记录，并结合 EMS 专家咨询，建立了 **3 大协议 → 9 项干预 → 67 项关键步骤** 的三级体系。该分类法基于美国国家急救医疗技术员注册局（NREMT）心理运动考试指南制定，确保与临床实践和国家级标准对齐。

---

## 二、9 项干预措施与 67 个关键步骤

### 1. ABCs（气道、呼吸、循环评估）

| 编号 | Keystep | 描述 |
| :---: | :--- | :--- |
| 1 | `approach_patient` | 接近患者并评估现场安全 |
| 2 | `check_responsiveness` | 检查患者对言语或物理刺激的反应 |
| 3 | `check_pulse` | 评估颈动脉或桡动脉搏动 |
| 4 | `check_breathing` | 通过观察、听、感受来检查呼吸 |

### 2. 患者病史采集（Patient History）

| 编号 | Keystep | 描述 |
| :---: | :--- | :--- |
| 5 | `review_medications` | 回顾当前用药 |
| 6 | `inquire_medication_anticoagulants` | 询问抗凝药物使用情况 |
| 7 | `inquire_hpi_and_pmh` | 询问现病史和既往史 |
| 8 | `inquire_substance_use` | 询问酒精或药物使用情况 |

### 3. 生命体征评估（Vital Sign Assessment）

| 编号 | Keystep | 描述 |
| :---: | :--- | :--- |
| 9 | `check_blood_pressure` | 测量血压 |
| 10 | `check_heart_rate` | 检查脉搏率 |
| 11 | `check_oxygen_saturation` | 测量血氧饱和度 |
| 12 | `check_respiratory_rate` | 计算呼吸频率 |
| 13 | `prepare_glucometer_and_strip` | 准备血糖仪和试纸 |
| 14 | `read_and_record_glucose_level` | 读取并记录血糖读数 |
| 15 | `check_perrl` | 检查瞳孔对光反射 |
| 16 | `check_skin_condition` | 评估皮肤颜色、温度和湿度 |
| 17 | `check_a&o` | 检查清醒程度和定向力 |

### 4. 卒中评估（Stroke Assessment）

| 编号 | Keystep | 描述 |
| :---: | :--- | :--- |
| 18 | `check_grip_strength` | 检查双侧握力是否对称 |
| 19 | `face_droop_check` | 让患者微笑以检查面部对称性 |
| 20 | `arm_drift_check` | 让患者举起双臂并观察是否下垂 |
| 21 | `speech_abnormality_check` | 让患者重复短语以评估语言能力 |
| 22 | `assess_balance_and_coordination` | 评估平衡和移动能力 |
| 23 | `document_lkw_time` | 记录最后一次正常知晓时间 |
| 24 | `check_vision_deficits` | 询问视力变化或丧失情况 |
| 25 | `evaluate_aphasia` | 评估失语症 |
| 26 | `assess_neglect_signs` | 检查单侧忽略体征 |

### 5. 12 导联心电图（12‑Lead ECG）

| 编号 | Keystep | 描述 |
| :---: | :--- | :--- |
| 27 | `explain_procedure` | 向患者解释心电图操作 |
| 28 | `shave_patient` | 剃除皮肤以利于电极接触 |
| 29 | `place_left_arm_lead` | 连接左臂电极 |
| 30 | `place_right_arm_lead` | 连接右臂电极 |
| 31 | `place_left_leg_lead` | 连接左腿电极 |
| 32 | `place_right_leg_lead` | 连接右腿电极 |
| 33 | `place_v1_lead` | 将 V1 置于第 4 肋间胸骨右缘 |
| 34 | `place_v2_lead` | 将 V2 置于第 4 肋间胸骨左缘 |
| 35 | `place_v3_lead` | 将 V3 置于 V2 和 V4 之间 |
| 36 | `place_v4_lead` | 将 V4 置于第 5 肋间锁骨中线 |
| 37 | `place_v5_lead` | 将 V5 置于腋前线 |
| 38 | `place_v6_lead` | 将 V6 置于腋中线 |
| 39 | `request_patient_to_not_move` | 要求患者在记录期间保持不动 |
| 40 | `turn_on_ecg` | 开启心电图机 |
| 41 | `connect_leads_to_ecg` | 将导联连接到电极和心电图机 |
| 42 | `ask_patient_age_sex` | 确认患者年龄和性别 |
| 43 | `obtain_ecg_recording` | 记录心电图 |
| 44 | `interpret_and_report` | 审阅并报告心电图结果 |

### 6. 心肺复苏（CPR）

| 编号 | Keystep | 描述 |
| :---: | :--- | :--- |
| 45 | `check_responsiveness` | 检查患者反应 |
| 46 | `check_pulse` | 检查脉搏 |
| 47 | `check_breathing` | 检查呼吸 |
| 48 | `chest_compressions` | 以正确的频率和深度进行胸外按压 |
| 49 | `assess_patient` | 重新评估患者状况 |

### 7. 除颤（Defibrillation）

| 编号 | Keystep | 描述 |
| :---: | :--- | :--- |
| 50 | `request_aed` | 尽早请求 AED |
| 51 | `request_assistance` | 呼叫旁观者或团队协助 |
| 52 | `turn_on_aed` | 开启 AED 并准备使用 |
| 53 | `attach_defib_pads` | 将电极片正确贴在裸露的胸部 |
| 54 | `clear_for_analysis` | 确保心律分析期间无人接触患者 |
| 55 | `clear_for_shock` | 确认所有人已离开后再电击 |
| 56 | `administer_shock_aed` | 如 AED 建议则实施电击 |

### 8. 通气（Ventilation）

| 编号 | Keystep | 描述 |
| :---: | :--- | :--- |
| 57 | `open_airway` | 使用气道技术（如下颌托举 / 仰头抬颏） |
| 58 | `place_bvm` | 放置并密封球囊面罩 |
| 59 | `ventilate_patient` | 以适当方式实施通气 |
| 60 | `suction_airway` | 使用吸引器清理气道 |
| 61 | `inset_NPA` | 插入鼻咽通气管以维持气道通畅 |

### 9. 转运（Transport）

| 编号 | Keystep | 描述 |
| :---: | :--- | :--- |
| 62 | `transport` | 将患者转移至救护车 |
| 63 | `load_patient_to_stretcher` | 将患者抬上担架 |
| 64 | `secure_patient_on_stretcher` | 用安全带固定患者 |
| 65 | `notify_hospital` | 通知接收医院 |
| 66 | `notify_hospital_of_stroke_alert` | 如有需要激活卒中警报 |
| 67 | `handoff_patient_to_hospital` | 交接时向医院报告 |

---

## 三、ACLS 模拟系统中的干预措施建模

高级心血管生命支持（ACLS）模拟系统需要对急救干预措施进行建模。根据 EgoEMS 数据集的分类体系及相关文献，ACLS 模拟系统需要建模的干预措施包括：

| 干预类别 | 说明 |
| :--- | :--- |
| 药物（Medications） | 如肾上腺素、阿托品、利多卡因等急救药物 |
| 按压（Compressions） | CPR 胸外按压，包括频率、深度等质量指标 |
| 氧流量（Oxygen Flow） | 给氧治疗 |
| 除颤（Defibrillation） | AED / 手动除颤器的使用 |
| 通气（Ventilation） | 球囊面罩通气、气道管理 |
| 12 导联心电图（12‑Lead ECG） | 心电图采集与判读 |
| 生命体征评估 | 血压、脉搏、血氧、呼吸等 |

这些干预措施在模拟器中与可测量的生理参数（心电图、生命体征）和生理状况（心律）相关联，共同构成动态急救模拟环境。

---

## 四、数据集的三个基准任务

EgoEMS 数据集提供了三个基准任务，用于推动 AI 认知协助系统的开发：

| 基准任务 | 描述 |
| :--- | :--- |
| **Keystep Classification（步骤分类）** | 从多模态输入中识别当前执行的关键步骤 |
| **Keystep Segmentation（在线分割）** | 检测每个关键步骤在时间轴上的起止时间 |
| **CPR Quality Estimation（CPR 质量估计）** | 评估心肺复苏的操作质量 |

---

## 五、标注方法

EgoEMS 数据集的 67 个关键步骤采用以下方法进行标注：

- **人工标注**：使用牛津大学 VGG 图像实验室的 Video Annotation Tool（VIA）进行手动标注
- **双重验证**：由不同标注员进行交叉验证以确保准确性
- **专家验证**：针对复杂关键步骤（如 12 导联 ECG 和卒中评估）咨询 EMS 专家进行额外验证
- **半自动音频标注流水线**：使用 Gemini‑2.5 进行零样本语音识别，词错误率（WER）仅为 0.31，相比纯手工标注节省约 90% 的时间。

---

## 六、数据来源说明

以上 67 个关键步骤中的 **第 1～26 步**（ABCs、患者病史、生命体征评估、卒中评估）来源于 EgoEMS 数据集论文解读；**第 27～67 步**（12 导联 ECG、CPR、除颤、通气、转运）来源于 EgoEMS arXiv 论文正文中的分类法表格。两部分合并后共计 67 个关键步骤，完整覆盖 EgoEMS 数据集定义的 9 项干预措施。

由于原始论文中部分步骤的编号未逐一标注，上述编号系根据干预措施顺序整理而成，具体步骤名称和描述均忠实于论文原文。