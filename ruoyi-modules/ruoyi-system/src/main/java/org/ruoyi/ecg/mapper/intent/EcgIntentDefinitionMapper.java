package org.ruoyi.ecg.mapper.intent;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import org.ruoyi.ecg.domain.intent.EcgIntentDefinition;

import java.util.List;

/**
 * 意图定义 Mapper 接口
 *
 * @author ecg-system
 */
@Mapper
public interface EcgIntentDefinitionMapper {

    // ==================== 查询方法 ====================

    /**
     * 查询意图定义列表（支持分页）
     *
     * @param intent 查询条件
     * @return 意图定义集合
     */
    List<EcgIntentDefinition> selectEcgIntentDefinitionList(EcgIntentDefinition intent);

    /**
     * 获取所有意图（下拉选择用）
     *
     * @param category 分类过滤（可选）
     * @return 意图定义集合
     */
    List<EcgIntentDefinition> selectAllIntentOptions(@Param("category") String category);

    /**
     * 根据意图ID查询意图定义
     *
     * @param intentId 意图编码
     * @return 意图定义
     */
    EcgIntentDefinition selectEcgIntentDefinitionById(@Param("intentId") String intentId);

    // ==================== 插入方法 ====================

    /**
     * 新增意图定义
     *
     * @param intent 意图定义
     * @return 结果
     */
    int insertEcgIntentDefinition(EcgIntentDefinition intent);

    // ==================== 更新方法 ====================

    /**
     * 修改意图定义
     *
     * @param intent 意图定义
     * @return 结果
     */
    int updateEcgIntentDefinition(EcgIntentDefinition intent);

    /**
     * 批量更新意图状态
     *
     * @param intentIds 意图编码数组
     * @param isActive 状态值（0禁用 1启用）
     * @return 结果
     */
    int batchUpdateStatus(@Param("array") String[] intentIds, @Param("isActive") Integer isActive);

    // ==================== 删除方法（逻辑删除） ====================

    /**
     * 逻辑删除意图定义
     *
     * @param intentId 意图编码
     * @return 结果
     */
    int deleteEcgIntentDefinitionById(@Param("intentId") String intentId);

    /**
     * 批量逻辑删除意图定义
     *
     * @param intentIds 需要删除的意图编码数组
     * @return 结果
     */
    int deleteEcgIntentDefinitionByIds(@Param("array") String[] intentIds);
}
