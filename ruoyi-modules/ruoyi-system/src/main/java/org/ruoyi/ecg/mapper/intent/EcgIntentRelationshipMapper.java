package org.ruoyi.ecg.mapper.intent;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import org.ruoyi.ecg.domain.intent.EcgIntentRelationship;

import java.util.List;

/**
 * 意图关系 Mapper 接口
 *
 * @author ecg-system
 */
@Mapper
public interface EcgIntentRelationshipMapper {

    // ==================== 查询方法 ====================

    /**
     * 查询意图关系列表（支持分页）
     *
     * @param relationship 查询条件
     * @return 意图关系集合
     */
    List<EcgIntentRelationship> selectEcgIntentRelationshipList(EcgIntentRelationship relationship);

    /**
     * 根据源意图ID查询关系列表
     *
     * @param sourceIntentId 源意图编码
     * @return 意图关系集合
     */
    List<EcgIntentRelationship> selectRelationshipsBySourceId(@Param("sourceIntentId") String sourceIntentId);

    /**
     * 根据主键ID查询意图关系
     *
     * @param id 主键
     * @return 意图关系
     */
    EcgIntentRelationship selectEcgIntentRelationshipById(@Param("id") Long id);

    /**
     * 检查意图关系是否已存在
     *
     * @param sourceIntentId 源意图编码
     * @param targetIntentId 目标意图编码
     * @param relationshipType 关系类型
     * @return 数量
     */
    int countRelationship(@Param("sourceIntentId") String sourceIntentId,
                          @Param("targetIntentId") String targetIntentId,
                          @Param("relationshipType") String relationshipType);

    // ==================== 插入方法 ====================

    /**
     * 新增意图关系
     *
     * @param relationship 意图关系
     * @return 结果
     */
    int insertEcgIntentRelationship(EcgIntentRelationship relationship);

    /**
     * 批量新增意图关系
     *
     * @param relationships 意图关系列表
     * @return 结果
     */
    int insertEcgIntentRelationshipBatch(@Param("list") List<EcgIntentRelationship> relationships);

    // ==================== 更新方法 ====================

    /**
     * 修改意图关系
     *
     * @param relationship 意图关系
     * @return 结果
     */
    int updateEcgIntentRelationship(EcgIntentRelationship relationship);

    // ==================== 删除方法 ====================

    /**
     * 删除意图关系
     *
     * @param id 主键
     * @return 结果
     */
    int deleteEcgIntentRelationshipById(@Param("id") Long id);

    /**
     * 根据源意图ID删除所有关系
     *
     * @param sourceIntentId 源意图编码
     * @return 结果
     */
    int deleteRelationshipsBySourceId(@Param("sourceIntentId") String sourceIntentId);

    /**
     * 批量删除意图关系
     *
     * @param ids 主键数组
     * @return 结果
     */
    int deleteEcgIntentRelationshipByIds(@Param("array") Long[] ids);
}
