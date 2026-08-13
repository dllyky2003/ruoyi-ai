package org.ruoyi.ecg.mapper.intent;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import org.ruoyi.ecg.domain.intent.EcgContextFiltering;

import java.util.List;

/**
 * 上下文过滤 Mapper 接口
 *
 * @author ecg-system
 */
@Mapper
public interface EcgContextFilteringMapper {

    // ==================== 查询方法 ====================

    /**
     * 查询上下文过滤配置列表（支持分页）
     *
     * @param context 查询条件
     * @return 上下文过滤集合
     */
    public List<EcgContextFiltering> selectEcgContextFilteringList(EcgContextFiltering context);

    /**
     * 根据阶段ID查询上下文过滤配置
     *
     * @param phaseId 阶段编码
     * @return 上下文过滤
     */
    public EcgContextFiltering selectEcgContextFilteringByPhaseId(@Param("phaseId") String phaseId);

    /**
     * 根据主键ID查询上下文过滤
     *
     * @param id 主键
     * @return 上下文过滤
     */
    public EcgContextFiltering selectEcgContextFilteringById(@Param("id") Long id);

    // ==================== 插入方法 ====================

    /**
     * 新增上下文过滤配置
     *
     * @param context 上下文过滤
     * @return 结果
     */
    public int insertEcgContextFiltering(EcgContextFiltering context);

    // ==================== 更新方法 ====================

    /**
     * 修改上下文过滤配置
     *
     * @param context 上下文过滤
     * @return 结果
     */
    public int updateEcgContextFiltering(EcgContextFiltering context);

    // ==================== 删除方法 ====================

    /**
     * 删除上下文过滤配置
     *
     * @param phaseId 阶段编码
     * @return 结果
     */
    public int deleteEcgContextFilteringByPhaseId(@Param("phaseId") String phaseId);

    /**
     * 批量删除上下文过滤配置
     *
     * @param ids 主键数组
     * @return 结果
     */
    public int deleteEcgContextFilteringByIds(@Param("array") Long[] ids);
}
