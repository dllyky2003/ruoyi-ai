package org.ruoyi.ecg.service;

import org.ruoyi.ecg.domain.EcgStateLibrary;
import java.util.List;

/**
 * ECG状态库Service接口
 *
 * @author ruoyi
 * @date 2026-08-06
 */
public interface IEcgStateLibraryService {

    // =============================================
    // 基础查询
    // =============================================

    /**
     * 查询ECG状态
     *
     * @param stateId 状态ID
     * @return ECG状态
     */
    public EcgStateLibrary selectEcgStateLibraryById(String stateId);

    /**
     * 查询ECG状态列表（支持条件筛选、排序、模糊搜索）
     *
     * @param ecgStateLibrary 查询条件
     * @return ECG状态集合
     */
    public List<EcgStateLibrary> selectEcgStateLibraryList(EcgStateLibrary ecgStateLibrary);

    /**
     * 查询所有ECG状态（下拉选择用）
     *
     * @return ECG状态集合
     */
    public List<EcgStateLibrary> selectAllEcgStateLibrary();

    /**
     * 查询所有启用的ECG状态（下拉选择用）
     *
     * @return ECG状态集合
     */
    public List<EcgStateLibrary> selectActiveEcgStateLibrary();

    /**
     * 查询ECG状态简略信息（仅ID、名称、别名）
     *
     * @return ECG状态简略集合
     */
    public List<EcgStateLibrary> selectSimpleEcgStateLibrary();

    /**
     * 根据分类查询ECG状态列表
     *
     * @param categoryCode 分类代码（BRADY/TACHY/ARREST/ROSC/OTHER）
     * @return ECG状态集合
     */
    public List<EcgStateLibrary> selectEcgStateLibraryByCategory(String categoryCode);

    /**
     * 查询所有分类列表（去重，下拉选择用）
     *
     * @return 分类代码列表
     */
    public List<String> selectCategoryList();

    // =============================================
    // 基础增删改
    // =============================================

    /**
     * 新增ECG状态
     *
     * @param ecgStateLibrary ECG状态
     * @return 结果
     */
    public int insertEcgStateLibrary(EcgStateLibrary ecgStateLibrary);

    /**
     * 修改ECG状态
     *
     * @param ecgStateLibrary ECG状态
     * @return 结果
     */
    public int updateEcgStateLibrary(EcgStateLibrary ecgStateLibrary);

    /**
     * 切换启用/停用状态
     *
     * @param stateId 状态ID
     * @param isActive 启用状态（0-停用，1-启用）
     * @return 结果
     */
    public int changeActiveStatus(String stateId, String isActive);

    /**
     * 逻辑删除ECG状态（del_flag置为2）
     *
     * @param stateId 状态ID
     * @return 结果
     */
    public int deleteEcgStateLibraryById(String stateId);

    /**
     * 批量逻辑删除ECG状态（del_flag置为2）
     *
     * @param stateIds 需要删除的状态ID数组
     * @return 结果
     */
    public int deleteEcgStateLibraryByIds(String[] stateIds);

    // =============================================
    // 物理删除（慎用）
    // =============================================

    /**
     * 物理删除ECG状态（慎用）
     *
     * @param stateId 状态ID
     * @return 结果
     */
    public int physicalDeleteEcgStateLibraryById(String stateId);

    /**
     * 批量物理删除ECG状态（慎用）
     *
     * @param stateIds 状态ID数组
     * @return 结果
     */
    public int physicalDeleteEcgStateLibraryByIds(String[] stateIds);

    // =============================================
    // 批量操作
    // =============================================

    /**
     * 批量调整排序号
     *
     * @param stateList 状态列表（包含stateId和sortNum）
     * @return 结果
     */
    public int batchUpdateSortNum(List<EcgStateLibrary> stateList);

    // =============================================
    // 唯一性校验
    // =============================================

    /**
     * 检查状态名称是否存在
     *
     * @param stateName 状态名称
     * @return true-存在，false-不存在
     */
    public boolean checkStateNameExists(String stateName);

    /**
     * 检查波形代码是否存在
     *
     * @param waveformCode 波形代码
     * @return true-存在，false-不存在
     */
    public boolean checkWaveformCodeExists(String waveformCode);

    // =============================================
    // 导入导出
    // =============================================

    /**
     * 批量导入ECG状态
     *
     * @param stateList 状态列表
     * @param isUpdateSupport 是否更新支持，如果已存在则更新
     * @param operName 操作人
     * @return 导入结果
     */
    public String importStateLibrary(List<EcgStateLibrary> stateList, boolean isUpdateSupport, String operName);
}
