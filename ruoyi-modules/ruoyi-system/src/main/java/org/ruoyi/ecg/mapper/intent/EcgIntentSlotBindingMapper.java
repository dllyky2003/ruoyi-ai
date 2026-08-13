package org.ruoyi.ecg.mapper.intent;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import org.ruoyi.ecg.domain.intent.EcgIntentSlotBinding;

import java.util.List;

/**
 * 意图-槽位绑定 Mapper 接口
 *
 * @author ecg-system
 */
@Mapper
public interface EcgIntentSlotBindingMapper {

    // ==================== 查询方法 ====================

    /**
     * 查询意图-槽位绑定列表（支持分页）
     *
     * @param binding 查询条件
     * @return 绑定集合
     */
    public List<EcgIntentSlotBinding> selectEcgIntentSlotBindingList(EcgIntentSlotBinding binding);

    /**
     * 根据意图ID查询绑定的槽位列表
     *
     * @param intentId 意图编码
     * @return 绑定集合
     */
    public List<EcgIntentSlotBinding> selectBindingsByIntentId(@Param("intentId") String intentId);

    /**
     * 根据槽位名称查询引用该槽位的绑定列表
     *
     * @param slotName 槽位名称
     * @return 绑定集合
     */
    public List<EcgIntentSlotBinding> selectBindingsBySlotName(@Param("slotName") String slotName);

    /**
     * 根据主键ID查询绑定
     *
     * @param id 主键
     * @return 绑定
     */
    public EcgIntentSlotBinding selectEcgIntentSlotBindingById(@Param("id") Long id);

    // ==================== 插入方法 ====================

    /**
     * 新增意图-槽位绑定
     *
     * @param binding 绑定
     * @return 结果
     */
    public int insertEcgIntentSlotBinding(EcgIntentSlotBinding binding);

    /**
     * 批量新增意图-槽位绑定
     *
     * @param bindings 绑定列表
     * @return 结果
     */
    public int insertEcgIntentSlotBindingBatch(@Param("list") List<EcgIntentSlotBinding> bindings);

    // ==================== 更新方法 ====================

    /**
     * 修改意图-槽位绑定
     *
     * @param binding 绑定
     * @return 结果
     */
    public int updateEcgIntentSlotBinding(EcgIntentSlotBinding binding);

    // ==================== 删除方法 ====================

    /**
     * 根据主键删除绑定
     *
     * @param id 主键
     * @return 结果
     */
    public int deleteEcgIntentSlotBindingById(@Param("id") Long id);

    /**
     * 根据意图ID删除所有绑定
     *
     * @param intentId 意图编码
     * @return 结果
     */
    public int deleteBindingsByIntentId(@Param("intentId") String intentId);

    /**
     * 批量删除绑定
     *
     * @param ids 主键数组
     * @return 结果
     */
    public int deleteEcgIntentSlotBindingByIds(@Param("array") Long[] ids);
}
