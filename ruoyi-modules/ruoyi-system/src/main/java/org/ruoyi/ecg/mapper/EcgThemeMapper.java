package org.ruoyi.ecg.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.ruoyi.ecg.domain.EcgTheme;
import org.ruoyi.ecg.domain.EcgThemeState;
import org.ruoyi.ecg.domain.EcgThemeEvent;
import org.ruoyi.ecg.domain.EcgThemeSource;
import org.ruoyi.ecg.domain.EcgPatientCase;
import org.ruoyi.ecg.domain.EcgAlarmSetup;

/**
 * 教学方案Mapper接口
 *
 * @author ruoyi
 */
public interface EcgThemeMapper {

    // =============================================
    // 主表查询
    // =============================================

    /**
     * 查询完整教学方案（包含所有子表数据）
     */
    EcgTheme selectThemeById(String themeId);

    /**
     * 查询教学方案主表信息（不包含子表）
     */
    EcgTheme selectThemeBasicById(String themeId);

    /**
     * 查询教学方案列表（带条件）
     */
    List<EcgTheme> selectThemeList(EcgTheme theme);

    /**
     * 查询所有教学方案列表（无条件）
     */
    List<EcgTheme> selectAllThemeList();

    /**
     * 查询所有教学方案（无分页，下拉选择用）
     */
    List<EcgTheme> selectAllTheme(@Param("theme") EcgTheme theme);

    /**
     * 查询所有教学方案简略信息（仅ID和名称）
     */
    List<EcgTheme> selectSimpleTheme();

    /**
     * 查询教学方案列表（分页）
     */
    List<EcgTheme> selectThemeListPage(@Param("theme") EcgTheme theme,
                                        @Param("offset") int offset,
                                        @Param("pageSize") int pageSize);

    /**
     * 统计教学方案总数
     */
    int countTheme(EcgTheme theme);

    /**
     * 根据ID统计教学方案数量
     */
    int countThemeById(String themeId);

    /**
     * 根据名称统计教学方案数量
     */
    int countThemeByThemeName(String themeName);

    /**
     * 根据名称统计教学方案数量（排除指定ID）
     */
    int countThemeByThemeNameExclude(@Param("themeName") String themeName,
                                      @Param("excludeThemeId") String excludeThemeId);

    // =============================================
    // 主表操作
    // =============================================

    /**
     * 新增教学方案
     */
    int insertTheme(EcgTheme theme);

    /**
     * 更新教学方案
     */
    int updateTheme(EcgTheme theme);

    /**
     * 删除教学方案
     */
    int deleteThemeById(String themeId);

    // =============================================
    // 状态子表操作
    // =============================================

    /**
     * 根据主题ID查询状态列表
     */
    List<EcgThemeState> selectStatesByThemeId(String themeId);

    /**
     * 新增状态
     */
    int insertState(EcgThemeState state);

    /**
     * 根据主题ID删除状态
     */
    int deleteStatesByThemeId(String themeId);

    // =============================================
    // 事件子表操作
    // =============================================

    /**
     * 根据主题ID查询事件列表
     */
    List<EcgThemeEvent> selectEventsByThemeId(String themeId);

    /**
     * 新增事件
     */
    int insertEvent(EcgThemeEvent event);

    /**
     * 根据主题ID删除事件
     */
    int deleteEventsByThemeId(String themeId);

    // =============================================
    // 资源子表操作
    // =============================================

    /**
     * 根据主题ID查询资源列表
     */
    List<EcgThemeSource> selectSourcesByThemeId(String themeId);

    /**
     * 新增资源
     */
    int insertSource(EcgThemeSource source);

    /**
     * 根据主题ID删除资源
     */
    int deleteSourcesByThemeId(String themeId);

    // =============================================
    // 病例子表操作
    // =============================================

    /**
     * 根据主题ID查询病例列表
     */
    List<EcgPatientCase> selectPatientCasesByThemeId(String themeId);

    /**
     * 批量新增病例关联
     */
    int batchInsertPatientCases(@Param("list") List<EcgPatientCase> patientCaseList);

    /**
     * 根据主题ID删除所有病例关联
     */
    int deletePatientCasesByThemeId(String themeId);

    /**
     * 根据病例ID和主题ID删除单条关联
     */
    int deletePatientCaseById(@Param("patientCaseId") String patientCaseId, @Param("themeId") String themeId);

    // =============================================
    // 报警设置子表操作
    // =============================================

    /**
     * 根据主题ID查询报警设置
     */
    EcgAlarmSetup selectAlarmSetupByThemeId(String themeId);

    /**
     * 新增报警设置
     */
    int insertAlarmSetup(EcgAlarmSetup alarmSetup);

    /**
     * 根据主题ID删除报警设置
     */
    int deleteAlarmSetupByThemeId(String themeId);


    /**
     * 查询所有教学方案资源
     * @return 资源列表
     */
    List<EcgThemeSource> selectAllThemeSources();
}
