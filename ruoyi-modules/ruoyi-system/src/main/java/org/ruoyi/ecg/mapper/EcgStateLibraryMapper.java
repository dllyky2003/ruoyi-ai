package org.ruoyi.ecg.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.ruoyi.ecg.domain.EcgStateLibrary;

/**
 * ECG状态库Mapper接口
 *
 * @author ruoyi

 */
public interface EcgStateLibraryMapper {

    // =============================================
    // 基础查询
    // =============================================

    /**
     * 根据ID查询ECG状态
     */
    EcgStateLibrary selectStateById(String stateId);

    /**
     * 查询ECG状态列表（带条件、排序）
     */
    List<EcgStateLibrary> selectStateList(EcgStateLibrary state);

    /**
     * 查询所有ECG状态（无条件，下拉选择用）
     */
    List<EcgStateLibrary> selectAllState();

    /**
     * 查询所有启用的ECG状态（下拉选择用）
     */
    List<EcgStateLibrary> selectActiveState();

    /**
     * 查询所有ECG状态简略信息（仅ID、名称、别名）
     */
    List<EcgStateLibrary> selectSimpleState();

    // =============================================
    // 分页查询
    // =============================================

    /**
     * 查询ECG状态列表（分页）
     */
    List<EcgStateLibrary> selectStateListPage(@Param("state") EcgStateLibrary state,
                                               @Param("offset") int offset,
                                               @Param("pageSize") int pageSize);

    /**
     * 统计ECG状态总数
     */
    int countState(EcgStateLibrary state);

    // =============================================
    // 唯一性校验
    // =============================================

    /**
     * 根据ID统计ECG状态数量
     */
    int countStateById(String stateId);

    /**
     * 根据状态名称统计数量（新增时校验唯一性）
     */
    int countStateByStateName(String stateName);

    /**
     * 根据状态名称统计数量（排除指定ID，修改时校验唯一性）
     */
    int countStateByStateNameExclude(@Param("stateName") String stateName,
                                      @Param("excludeStateId") String excludeStateId);

    /**
     * 根据波形代码统计数量（校验唯一性）
     */
    int countStateByWaveformCode(String waveformCode);

    /**
     * 根据波形代码统计数量（排除指定ID）
     */
    int countStateByWaveformCodeExclude(@Param("waveformCode") String waveformCode,
                                         @Param("excludeStateId") String excludeStateId);

    // =============================================
    // 按分类查询
    // =============================================

    /**
     * 根据分类代码查询状态列表
     */
    List<EcgStateLibrary> selectStateByCategory(String categoryCode);

    /**
     * 根据分类代码统计数量
     */
    int countStateByCategory(String categoryCode);

    /**
     * 查询所有分类列表（去重，供下拉选择）
     */
    List<String> selectCategoryList();

    // =============================================
    // 增删改操作
    // =============================================

    /**
     * 新增ECG状态
     */
    int insertState(EcgStateLibrary state);

    /**
     * 更新ECG状态
     */
    int updateState(EcgStateLibrary state);

    /**
     * 切换启用/停用状态
     */
    int changeActiveStatus(@Param("stateId") String stateId,
                            @Param("isActive") String isActive);

    /**
     * 物理删除ECG状态（慎用）
     */
    int deleteStateById(String stateId);

    /**
     * 批量物理删除（慎用）
     */
    int deleteStateByIds(String[] stateIds);

    /**
     * 逻辑删除ECG状态（del_flag置为2）
     */
    int logicDeleteStateById(String stateId);

    /**
     * 批量逻辑删除（del_flag置为2）
     */
    int logicDeleteStateByIds(String[] stateIds);

    // =============================================
    // 批量操作
    // =============================================

    /**
     * 批量插入状态（用于初始化数据）
     */
    int batchInsertState(@Param("list") List<EcgStateLibrary> stateList);

    /**
     * 批量更新排序号
     */
    int batchUpdateSortNum(@Param("list") List<EcgStateLibrary> stateList);
}
