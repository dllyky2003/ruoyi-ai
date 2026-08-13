package org.ruoyi.ecg.domain.intent;

import java.io.Serializable;
import java.util.List;

/**
 * 语料主表 ecg_utterance_pattern
 */
public class EcgUtterancePattern implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 自增主键 */
    private Long id;

    /** 意图编码 */
    private String intentId;

    /** 话术原文 */
    private String utterance;

    /** 参数位置提示（JSON字符串） */
    private String slotHints;

    /** 语体风格：professional / colloquial / abbreviation / dialect / mixed */
    private String languageStyle;

    /** 难度等级：1-5 */
    private Integer difficultyLevel;

    /** 地域方言标注 */
    private String region;

    /** 是否启用：0禁用 1启用 */
    private Integer isActive;

    /** 历史命中次数 */
    private Integer hitCount;

    /** 创建者 */
    private String createBy;

    /** 创建时间 */
    private String createTime;

    /** 更新者 */
    private String updateBy;

    /** 更新时间 */
    private String updateTime;

    /** 备注 */
    private String remark;

    // ====== 非表字段 ======
    /** 关联的意图定义 */
    private EcgIntentDefinition intentDefinition;

    /** 语料变体列表 */
    private List<EcgUtteranceVariant> variants;

    /** 意图描述（便于前端展示） */
    private String intentDescription;

    // ====== Getter / Setter ======
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getIntentId() { return intentId; }
    public void setIntentId(String intentId) { this.intentId = intentId; }

    public String getUtterance() { return utterance; }
    public void setUtterance(String utterance) { this.utterance = utterance; }

    public String getSlotHints() { return slotHints; }
    public void setSlotHints(String slotHints) { this.slotHints = slotHints; }

    public String getLanguageStyle() { return languageStyle; }
    public void setLanguageStyle(String languageStyle) { this.languageStyle = languageStyle; }

    public Integer getDifficultyLevel() { return difficultyLevel; }
    public void setDifficultyLevel(Integer difficultyLevel) { this.difficultyLevel = difficultyLevel; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public Integer getIsActive() { return isActive; }
    public void setIsActive(Integer isActive) { this.isActive = isActive; }

    public Integer getHitCount() { return hitCount; }
    public void setHitCount(Integer hitCount) { this.hitCount = hitCount; }

    public String getCreateBy() { return createBy; }
    public void setCreateBy(String createBy) { this.createBy = createBy; }

    public String getCreateTime() { return createTime; }
    public void setCreateTime(String createTime) { this.createTime = createTime; }

    public String getUpdateBy() { return updateBy; }
    public void setUpdateBy(String updateBy) { this.updateBy = updateBy; }

    public String getUpdateTime() { return updateTime; }
    public void setUpdateTime(String updateTime) { this.updateTime = updateTime; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public EcgIntentDefinition getIntentDefinition() { return intentDefinition; }
    public void setIntentDefinition(EcgIntentDefinition intentDefinition) { this.intentDefinition = intentDefinition; }

    public List<EcgUtteranceVariant> getVariants() { return variants; }
    public void setVariants(List<EcgUtteranceVariant> variants) { this.variants = variants; }

    public String getIntentDescription() { return intentDescription; }
    public void setIntentDescription(String intentDescription) { this.intentDescription = intentDescription; }
}
