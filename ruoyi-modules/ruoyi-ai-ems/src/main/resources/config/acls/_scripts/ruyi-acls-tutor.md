# 📁 项目结构：main

**根路径**: `D:\CaveSystem\Demos\ruoyi-ai\ruoyi-modules\ruoyi-acls-tutor\src\main`

**生成时间**: 2026-09-23 17:21:32

├── 📁 **java**
│   └── 📁 **com**
│       └── 📁 **ruoyi**
│           └── 📁 **acls**
│               └── 📁 **tutor**
│                   ├── 📁 **config**
│                   │   └── 📄 **TutorAutoConfiguration.java**  (680.0 B, 2026-09-19 13:28:53)
│                   ├── 📁 **contract**
│                   │   ├── 📁 **models**
│                   │   └── 📁 **util**
│                   ├── 📁 **domain**
│                   │   ├── 📄 **AssessmentInterruption.java**  (4.7 KB, 2026-09-19 21:03:22)
│                   │   ├── 📄 **AssessmentState.java**  (9.0 KB, 2026-09-20 08:07:40)
│                   │   ├── 📄 **BranchClinicalState.java**  (878.0 B, 2026-09-19 19:48:55)
│                   │   ├── 📄 **CaseOutcomeBranch.java**  (1.1 KB, 2026-09-19 19:49:21)
│                   │   ├── 📄 **ClinicalRule.java**  (1.9 KB, 2026-09-19 19:51:02)
│                   │   ├── 📄 **ClinicalState.java**  (937.0 B, 2026-09-19 14:58:58)
│                   │   ├── 📄 **EffectSpecStatic.java**  (850.0 B, 2026-09-19 19:47:40)
│                   │   ├── 📄 **Evidence.java**  (907.0 B, 2026-09-19 19:49:09)
│                   │   ├── 📄 **FallbackPath.java**  (2.5 KB, 2026-09-19 20:40:30)
│                   │   ├── 📄 **InstructorOverride.java**  (3.0 KB, 2026-09-19 20:40:44)
│                   │   ├── 📄 **LearnerState.java**  (573.0 B, 2026-09-19 14:59:24)
│                   │   ├── 📄 **LessonPlan.java**  (2.4 KB, 2026-09-19 20:43:16)
│                   │   ├── 📄 **ProtocolState.java**  (602.0 B, 2026-09-19 14:59:12)
│                   │   ├── 📄 **Review.java**  (792.0 B, 2026-09-19 19:47:28)
│                   │   └── 📄 **ScenarioBeat.java**  (5.8 KB, 2026-09-19 20:38:50)
│                   ├── 📁 **dto**
│                   ├── 📁 **engine**
│                   │   ├── 📄 **AttemptEventProcessor.java**  (3.0 KB, 2026-09-20 08:03:16)
│                   │   └── 📄 **ScenarioBeatEvaluator.java**  (8.5 KB, 2026-09-19 20:54:17)
│                   ├── 📁 **feedback**
│                   │   └── 📄 **FeedbackMessageResolver.java**  (4.8 KB, 2026-09-19 16:25:51)
│                   ├── 📁 **integration**
│                   │   ├── 📄 **TutorAssessmentQueryService.java**  (2.3 KB, 2026-09-22 17:19:17)
│                   │   ├── 📄 **TutorAttemptEventSink.java**  (8.0 KB, 2026-09-22 17:27:47)
│                   │   ├── 📄 **TutorClinicalRuleService.java**  (9.2 KB, 2026-09-23 16:25:12)
│                   │   ├── 📄 **TutorStateTransitionHook.java**  (1.3 KB, 2026-09-19 14:54:12)
│                   │   └── 📄 **TutorTemporalEventSink.java**  (1.3 KB, 2026-09-19 14:54:12)
│                   ├── 📁 **manifest**
│                   │   ├── 📄 **AttemptManifest.java**  (3.3 KB, 2026-09-19 20:59:19)
│                   │   └── 📄 **ManifestFreezer.java**  (8.1 KB, 2026-09-21 17:01:50)
│                   ├── 📁 **repository**
│                   │   ├── 📄 **LessonPlanRepository.java**  (10.2 KB, 2026-09-19 20:43:33)
│                   │   └── 📄 **RuleRepository.java**  (8.5 KB, 2026-09-19 19:50:20)
│                   ├── 📁 **service**
│                   │   └── 📄 **AssessmentEngine.java**  (12.7 KB, 2026-09-21 17:01:51)
│                   └── 📁 **web**
└── 📁 **resources**
    └── 📁 **config**
        └── 📁 **acls**
            ├── 📁 **cases**
            │   ├── 📁 **stable_narrow**
            │   │   ├── 📄 **clinical_rules.yaml**  (2.6 KB, 2026-09-22 18:45:30)
            │   │   ├── 📄 **fallback_paths.yaml**  (467.0 B, 2026-09-20 13:24:42)
            │   │   ├── 📄 **instructor_overrides.yaml**  (459.0 B, 2026-09-20 13:24:54)
            │   │   ├── 📄 **lesson_plan.yaml**  (889.0 B, 2026-09-20 13:24:23)
            │   │   └── 📄 **scenario_beats.yaml**  (1.1 KB, 2026-09-22 17:03:15)
            │   └── 📁 **unstable_narrow_svt**
            │       ├── 📄 **clinical_rules.yaml**  (3.3 KB, 2026-09-23 16:25:38)
            │       ├── 📄 **fallback_paths.yaml**  (946.0 B, 2026-09-21 09:09:49)
            │       ├── 📄 **feedback_messages.yaml**  (868.0 B, 2026-09-21 09:28:15)
            │       ├── 📄 **instructor_overrides.yaml**  (886.0 B, 2026-09-21 09:10:03)
            │       ├── 📄 **lesson_plan.yaml**  (1.1 KB, 2026-09-21 09:09:20)
            │       └── 📄 **scenario_beats.yaml**  (1.6 KB, 2026-09-21 09:09:34)
            ├── 📁 **feedback**
            │   └── 📄 **feedback_messages.yaml**  (1.1 KB, 2026-09-19 16:25:34)
            └── 📁 **schema**
                ├── 📄 **_shared_schema.json**  (4.2 KB, 2026-09-19 19:34:50)
                ├── 📄 **assessment_rules_schema.json**  (2.5 KB, 2026-09-19 19:44:11)
                ├── 📄 **clinical_rules_schema.json**  (1.8 KB, 2026-09-19 19:37:43)
                ├── 📄 **directional_response_rules_schema.json**  (3.3 KB, 2026-09-19 19:45:06)
                ├── 📄 **evidence_manifest_schema.json**  (3.2 KB, 2026-09-19 19:44:31)
                ├── 📄 **fallback_paths_schema.json**  (2.5 KB, 2026-09-19 19:43:22)
                ├── 📄 **instructor_overrides_schema.json**  (4.5 KB, 2026-09-19 19:43:48)
                ├── 📄 **lesson_plan_schema.json**  (2.1 KB, 2026-09-19 19:42:57)
                ├── 📄 **presentation_mapping_schema.json**  (2.2 KB, 2026-09-19 19:45:45)
                └── 📄 **scenario_beats_schema.json**  (5.1 KB, 2026-09-19 19:42:17)
