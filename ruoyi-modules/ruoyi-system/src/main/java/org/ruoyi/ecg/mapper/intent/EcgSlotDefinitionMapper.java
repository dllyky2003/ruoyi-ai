package org.ruoyi.ecg.mapper.intent;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import org.ruoyi.ecg.domain.intent.EcgSlotDefinition;

import java.util.List;

/**
 * 槽位定义 Mapper 接口
 *
 * @author ecg-system
 */
@Mapper
public interface EcgSlotDefinitionMapper {

    // ==================== 查询方法 ====================

    /**
     * 查询槽位定义列表（支持分页）
     *
     * @param slot 查询条件
     * @return 槽位定义集合
     */
    public List<EcgSlotDefinition> selectEcgSlotDefinitionList(EcgSlotDefinition slot);

    /**
     * 获取所有槽位（下拉选择用）
     *
     * @return 槽位定义集合
     */
    public List<EcgSlotDefinition> selectAllSlotOptions();

    /**
     * 根据槽位名称查询槽位定义
     *
     * @param slotName 槽位名称
     * @return 槽位定义
     */
    public EcgSlotDefinition selectEcgSlotDefinitionByName(@Param("slotName") String slotName);

    /**
     * 统计使用该槽位的意图数量
     *
     * @param slotName 槽位名称
     * @return 意图数量
     */
    public int countIntentBySlot(@Param("slotName") String slotName);

    /**
     * 查询使用该槽位的所有意图编码
     *
     * @param slotName 槽位名称
     * @return 意图编码列表
     */
    public List<String> selectIntentIdsBySlot(@Param("slotName") String slotName);

    // ==================== 插入方法 ====================

    /**
     * 新增槽位定义
     *
     * @param slot 槽位定义
     * @return 结果
     */
    public int insertEcgSlotDefinition(EcgSlotDefinition slot);

    // ==================== 更新方法 ====================

    /**
     * 修改槽位定义
     *
     * @param slot 槽位定义
     * @return 结果
     */
    public int updateEcgSlotDefinition(EcgSlotDefinition slot);

    // ==================== 删除方法 ====================

    /**
     * 删除槽位定义（物理删除，仅当没有被引用时允许）
     *
     * @param slotName 槽位名称
     * @return 结果
     */
    public int deleteEcgSlotDefinitionByName(@Param("slotName") String slotName);
}
