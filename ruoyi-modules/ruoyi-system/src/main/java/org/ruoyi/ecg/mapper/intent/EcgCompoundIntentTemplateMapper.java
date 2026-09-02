package org.ruoyi.ecg.mapper.intent;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import org.ruoyi.ecg.domain.intent.EcgCompoundIntentTemplate;

import java.util.List;

/**
 * 复合指令模板 Mapper 接口
 *
 * @author ecg-system
 */
@Mapper
public interface EcgCompoundIntentTemplateMapper {

    // ==================== 查询方法 ====================

    /**
     * 查询复合指令模板列表（支持分页）
     *
     * @param template 查询条件
     * @return 复合模板集合
     */
    List<EcgCompoundIntentTemplate> selectEcgCompoundIntentTemplateList(EcgCompoundIntentTemplate template);

    /**
     * 根据模板ID查询复合指令模板
     *
     * @param templateId 模板编码
     * @return 复合模板
     */
    EcgCompoundIntentTemplate selectEcgCompoundIntentTemplateById(@Param("templateId") String templateId);

    /**
     * 根据主键ID查询复合指令模板
     *
     * @param id 主键
     * @return 复合模板
     */
    EcgCompoundIntentTemplate selectEcgCompoundIntentTemplateByPk(@Param("id") Long id);

    // ==================== 插入方法 ====================

    /**
     * 新增复合指令模板
     *
     * @param template 复合模板
     * @return 结果
     */
    int insertEcgCompoundIntentTemplate(EcgCompoundIntentTemplate template);

    // ==================== 更新方法 ====================

    /**
     * 修改复合指令模板
     *
     * @param template 复合模板
     * @return 结果
     */
    int updateEcgCompoundIntentTemplate(EcgCompoundIntentTemplate template);

    // ==================== 删除方法 ====================

    /**
     * 删除复合指令模板
     *
     * @param templateId 模板编码
     * @return 结果
     */
    int deleteEcgCompoundIntentTemplateById(@Param("templateId") String templateId);

    /**
     * 批量删除复合指令模板
     *
     * @param ids 主键数组
     * @return 结果
     */
    int deleteEcgCompoundIntentTemplateByIds(@Param("array") Long[] ids);
}
