package com.ruoyi.acls.tutor.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 形成性评价状态。存放过程证据、错误记录与得分快照。
 *
 * <p>阶段 3 增补：评分中断（{@link AssessmentInterruption}）与不可评估项标记，
 * 对应快照 P0-9 / D4：中断不扣分，但受影响项不可评估，不计入通过判定。</p>
 *
 * <p>阶段 3 丙-2 增补：beat 选择证据链（{@link BeatSelectionRecord}）。
 * 只做留痕，不参与评分、不触发中断、不影响 version 比较。</p>
 */
@Data
public class AssessmentState {

    private Map<String, Boolean> criticalActions = new LinkedHashMap<>();
    private List<String> delays = new ArrayList<>();
    private List<String> errors = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
    private List<String> evidenceLinks = new ArrayList<>();
    private ScoreSnapshot scoreSnapshot = new ScoreSnapshot();

    // ============================================================
    // 阶段 3 新增：评分中断与不可评估项
    // ============================================================

    /**
     * 评估中断记录列表。按时间顺序追加；同一会话内可有多次中断。
     * 与 errors / warnings 分离：中断不代表学习者错误，而是评估窗口被显式打断。
     */
    private List<AssessmentInterruption> interruptions = new ArrayList<>();

    /**
     * 全局不可评估项集合。key 为评估项 ID（assessmentTag / criticalAction / ruleId 等），
     * value 为原因（例如 "INSTRUCTOR_OVERRIDE" / "CLOCK_PAUSED"）。
     * <p>由 interruptions 聚合得来，冗余存放便于快速判定；写入路径统一走
     * {@link #markUnevaluable(String, String)}。</p>
     */
    private Map<String, String> unevaluableItems = new LinkedHashMap<>();

    // ============================================================
    // 阶段 3 丙-2 新增：beat 选择证据链
    // ============================================================

    /**
     * beat 选择证据链。按处理顺序追加，同一会话内可有多条。
     *
     * <p><b>语义边界</b>：
     * <ul>
     *   <li>只记录"选了哪个 beat / 有无冲突 / 为什么"，不参与评分；</li>
     *   <li>不触发评分中断、不影响 {@link #unevaluableItems}、不驱动状态迁移（P0-8）；</li>
     *   <li>不参与 {@code version} 比较；本类无 copy()，序列化由外部决定。</li>
     * </ul>
     * </p>
     *
     * <p>懒初始化：为兼容既有反序列化路径，此字段可能为 null，
     * 一律通过 {@link #addBeatSelection(BeatSelectionRecord)} /
     * {@link #getBeatSelections()} 访问，不直接读取。</p>
     */
    private List<BeatSelectionRecord> beatSelections;

    // ============================================================
    // 阶段 3 便捷方法
    // ============================================================

    /**
     * 追加一条评估中断。
     * <p>若中断携带 {@link AssessmentInterruption#getUnevaluableItems()}，
     * 同步聚合进 {@link #unevaluableItems}。</p>
     */
    public void addInterruption(AssessmentInterruption interruption) {
        if (interruption == null) {
            return;
        }
        if (interruptions == null) {
            interruptions = new ArrayList<>();
        }
        interruptions.add(interruption);
        if (interruption.getUnevaluableItems() != null) {
            interruption.getUnevaluableItems().forEach(this::markUnevaluable);
        }
    }

    /**
     * 标记某个评估项为不可评估。
     *
     * @param itemId 评估项 ID（assessmentTag / criticalAction / ruleId 等）
     * @param reason 原因，例如 "INSTRUCTOR_OVERRIDE" / "CLOCK_PAUSED"
     */
    public void markUnevaluable(String itemId, String reason) {
        if (itemId == null || itemId.isBlank()) {
            return;
        }
        if (unevaluableItems == null) {
            unevaluableItems = new LinkedHashMap<>();
        }
        // 首次写入的原因保留；重复标记不覆盖，避免丢失最早触发的中断原因。
        unevaluableItems.putIfAbsent(itemId, reason != null ? reason : "UNKNOWN");

        // 同步到 ScoreSnapshot，保证评分阶段无需二次聚合。
        if (scoreSnapshot != null) {
            List<String> list = scoreSnapshot.getUnevaluableItems();
            if (list == null) {
                list = new ArrayList<>();
                scoreSnapshot.setUnevaluableItems(list);
            }
            if (!list.contains(itemId)) {
                list.add(itemId);
            }
        }
    }

    /** 判断某评估项是否不可评估。 */
    public boolean isUnevaluable(String itemId) {
        return itemId != null
            && unevaluableItems != null
            && unevaluableItems.containsKey(itemId);
    }

    /** 返回全部不可评估项 ID（去重、稳定顺序）。 */
    public Set<String> unevaluableItemIds() {
        return unevaluableItems != null
            ? new LinkedHashSet<>(unevaluableItems.keySet())
            : new LinkedHashSet<>();
    }

    /** 当前是否处于未结束的中断中。 */
    public boolean hasOngoingInterruption() {
        if (interruptions == null || interruptions.isEmpty()) {
            return false;
        }
        return interruptions.stream().anyMatch(AssessmentInterruption::isOngoing);
    }

    // ============================================================
    // 阶段 3 丙-2 便捷方法：beat 选择证据链
    // ============================================================

    /**
     * 追加一条 beat 选择记录。
     *
     * <p>只做留痕：不触发中断、不改评分、不动 {@link #unevaluableItems}。
     * {@code record == null} 时静默忽略，保持与 {@link #addInterruption} 一致的防御风格。</p>
     */
    public void addBeatSelection(BeatSelectionRecord record) {
        if (record == null) {
            return;
        }
        if (this.beatSelections == null) {
            this.beatSelections = new ArrayList<>();
        }
        this.beatSelections.add(record);
    }

    /**
     * 返回 beat 选择记录（只读语义）。
     *
     * <p>未记录时返回空列表而非 null；返回副本，调用方修改不影响内部状态。</p>
     */
    public List<BeatSelectionRecord> getBeatSelections() {
        return beatSelections != null
            ? new ArrayList<>(beatSelections)
            : new ArrayList<>();
    }

    // ============================================================
    // 得分快照
    // ============================================================

    /**
     * 得分快照（不与总分压缩，分维度展示）。
     * <p>阶段 3 增补 {@link #unevaluableItems}：不可评估项不计入扣分，
     * 通过判定时需跳过这些项。</p>
     */
    @Data
    public static class ScoreSnapshot {
        private int processScore;
        private int safetyScore;
        private int decisionScore;

        /**
         * 不可评估项 ID 列表。与 {@link AssessmentState#unevaluableItems} 同步聚合，
         * 便于在报告/评分阶段直接读取。
         */
        private List<String> unevaluableItems = new ArrayList<>();
    }

    // ============================================================
    // 阶段 3 丙-2 新增：beat 选择记录
    // ============================================================

    /**
     * 单次 beat 选择的证据记录。
     *
     * <p>由 {@code AssessmentEngine.recordBeatSelection} 从
     * {@code ScenarioBeatEvaluator.SelectionResult} 转换而来。
     * 记录"选择结果"而非"学习行为"，因此不参与评分、不计入错误。</p>
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class BeatSelectionRecord {
        /** 选中的 beat ID；无选中（如冲突、无匹配）为 null。 */
        private String beatId;

        /** 选中 beat 的 case_outcome_ref；无选中为 null。 */
        private String outcomeRef;

        /** 是否检测到同 group + 同 priority + 不同 outcome 的冲突。 */
        private boolean conflictDetected;

        /** 冲突涉及的其它 beat ID 列表；无冲突为空列表。 */
        private List<String> conflictBeatIds;

        /** evaluator 返回的原因串，例如 "ok" / "no beats matched" / "conflict: ..."。 */
        private String reason;

        /** 记录时间。使用墙上时间，与评分时钟（simulated / assessment real）无关。 */
        private LocalDateTime recordedAt;
    }
}
