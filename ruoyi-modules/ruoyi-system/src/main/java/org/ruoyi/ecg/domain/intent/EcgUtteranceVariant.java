package org.ruoyi.ecg.domain.intent;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 语料变体表 ecg_utterance_variant
 */
public class EcgUtteranceVariant implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 自增主键 */
    private Long id;

    /** 关联语料主表ID */
    private Long utteranceId;

    /** 变体表达 */
    private String variantText;

    /** 变体类型：synonym / abbreviation / slang / dialect / typo_correction */
    private String variantType;

    /** 匹配置信度权重 */
    private BigDecimal confidenceWeight;

    /** 是否启用：0禁用 1启用 */
    private Integer isActive;

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
    /** 关联的语料主表对象 */
    private EcgUtterancePattern utterancePattern;

    // ====== Getter / Setter ======
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUtteranceId() { return utteranceId; }
    public void setUtteranceId(Long utteranceId) { this.utteranceId = utteranceId; }

    public String getVariantText() { return variantText; }
    public void setVariantText(String variantText) { this.variantText = variantText; }

    public String getVariantType() { return variantType; }
    public void setVariantType(String variantType) { this.variantType = variantType; }

    public BigDecimal getConfidenceWeight() { return confidenceWeight; }
    public void setConfidenceWeight(BigDecimal confidenceWeight) { this.confidenceWeight = confidenceWeight; }

    public Integer getIsActive() { return isActive; }
    public void setIsActive(Integer isActive) { this.isActive = isActive; }

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

    public EcgUtterancePattern getUtterancePattern() { return utterancePattern; }
    public void setUtterancePattern(EcgUtterancePattern utterancePattern) { this.utterancePattern = utterancePattern; }
}
