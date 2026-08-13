package org.ruoyi.ecg.mapper.intent;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import org.ruoyi.ecg.domain.intent.EcgUtteranceVariant;

import java.util.List;

/**
 * 语料变体 Mapper 接口
 *
 * @author ecg-system
 */
@Mapper
public interface EcgUtteranceVariantMapper {

    // ==================== 查询方法 ====================

    /**
     * 查询语料变体列表（支持分页）
     *
     * @param variant 查询条件
     * @return 变体集合
     */
    public List<EcgUtteranceVariant> selectEcgUtteranceVariantList(EcgUtteranceVariant variant);

    /**
     * 根据语料主表ID查询变体列表
     *
     * @param utteranceId 语料主表ID
     * @return 变体集合
     */
    public List<EcgUtteranceVariant> selectVariantsByUtteranceId(@Param("utteranceId") Long utteranceId);

    /**
     * 根据主键ID查询变体
     *
     * @param id 主键
     * @return 变体
     */
    public EcgUtteranceVariant selectEcgUtteranceVariantById(@Param("id") Long id);

    // ==================== 插入方法 ====================

    /**
     * 新增语料变体
     *
     * @param variant 变体
     * @return 结果
     */
    public int insertEcgUtteranceVariant(EcgUtteranceVariant variant);

    /**
     * 批量新增语料变体
     *
     * @param variants 变体列表
     * @return 结果
     */
    public int insertEcgUtteranceVariantBatch(@Param("list") List<EcgUtteranceVariant> variants);

    // ==================== 更新方法 ====================

    /**
     * 修改语料变体
     *
     * @param variant 变体
     * @return 结果
     */
    public int updateEcgUtteranceVariant(EcgUtteranceVariant variant);

    // ==================== 删除方法 ====================

    /**
     * 删除语料变体
     *
     * @param id 主键
     * @return 结果
     */
    public int deleteEcgUtteranceVariantById(@Param("id") Long id);

    /**
     * 根据语料主表ID批量删除变体
     *
     * @param utteranceId 语料主表ID
     * @return 结果
     */
    public int deleteVariantsByUtteranceId(@Param("utteranceId") Long utteranceId);

    /**
     * 批量删除语料变体
     *
     * @param ids 主键数组
     * @return 结果
     */
    public int deleteEcgUtteranceVariantByIds(@Param("array") Long[] ids);
}
