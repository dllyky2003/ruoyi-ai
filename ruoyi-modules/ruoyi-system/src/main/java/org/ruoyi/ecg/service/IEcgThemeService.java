package org.ruoyi.ecg.service;

import java.util.List;
import org.ruoyi.ecg.domain.EcgTheme;
import org.ruoyi.ecg.domain.EcgThemeState;
import org.ruoyi.ecg.domain.EcgThemeEvent;
import org.ruoyi.ecg.domain.EcgThemeSource;
import org.ruoyi.ecg.domain.EcgPatientCase;
import org.ruoyi.ecg.domain.EcgAlarmSetup;

/**
 * 教学方案Service接口
 *
 * @author ruoyi
 */
public interface IEcgThemeService {

    // =============================================
    // 新增
    // =============================================

    /**
     * 新增完整教学方案（包含所有子表数据）
     */
    int insertEcgTheme(EcgTheme theme);

    /**
     * 新增教学方案主表信息（不包含子表）
     */
    int insertEcgThemeBasic(EcgTheme theme);

    // =============================================
    // 删除
    // =============================================

    /**
     * 删除教学方案（级联删除所有子表数据）
     */
    int deleteEcgThemeById(String themeId);

    /**
     * 批量删除教学方案（级联删除所有子表数据）
     */
    int deleteEcgThemeByIds(String[] themeIds);

    // =============================================
    // 更新
    // =============================================

    /**
     * 更新完整教学方案（包含所有子表数据）
     */
    int updateEcgTheme(EcgTheme theme);

    /**
     * 更新教学方案主表信息（不更新子表）
     */
    int updateEcgThemeBasic(EcgTheme theme);

    /**
     * 更新当前状态
     */
    int updateCurrentState(String themeId, String stateId);

    /**
     * 更新当前事件
     */
    int updateCurrentEvent(String themeId, String eventId);

    /**
     * 更新AES密钥
     */
    int updateAesKeys(String themeId, String aesKeys);

    // =============================================
    // 查询 - 单条
    // =============================================

    /**
     * 根据ID查询完整教学方案（包含所有子表数据）
     */
    EcgTheme selectEcgThemeById(String themeId);

    /**
     * 根据ID查询教学方案主表信息（不包含子表）
     */
    EcgTheme selectEcgThemeBasicById(String themeId);

    // =============================================
    // 查询 - 列表
    // =============================================

    /**
     * 获取所有教学方案列表（无条件）
     */
    List<EcgTheme> selectEcgThemeList();

    /**
     * 查询教学方案列表（带条件）
     */
    List<EcgTheme> selectEcgThemeList(EcgTheme theme);

    /**
     * 获取所有教学方案列表（分页）
     */
    List<EcgTheme> selectEcgThemeListPage(EcgTheme theme, int pageNum, int pageSize);

    /**
     * 获取所有教学方案（无分页，下拉选择用）
     */
    List<EcgTheme> selectAllEcgTheme(EcgTheme theme);

    /**
     * 获取所有教学方案简略信息（仅ID和名称）
     */
    List<EcgTheme> selectSimpleEcgTheme();

    // =============================================
    // 查询 - 统计
    // =============================================

    /**
     * 统计教学方案总数
     */
    int countTheme(EcgTheme theme);

    /**
     * 检查教学方案名称是否存在
     */
    boolean checkThemeNameExists(String themeName);

    /**
     * 检查教学方案名称是否存在（排除指定ID）
     */
    boolean checkThemeNameExists(String themeName, String excludeThemeId);

    /**
     * 检查教学方案ID是否存在
     */
    boolean checkThemeIdExists(String themeId);

    // =============================================
    // 查询 - 子表
    // =============================================

    /**
     * 根据主题ID查询状态列表
     */
    List<EcgThemeState> selectStatesByThemeId(String themeId);

    /**
     * 根据主题ID查询事件列表
     */
    List<EcgThemeEvent> selectEventsByThemeId(String themeId);

    /**
     * 根据主题ID查询资源列表
     */
    List<EcgThemeSource> selectSourcesByThemeId(String themeId);

    /**
     * 根据主题ID查询病例列表
     */
    List<EcgPatientCase> selectPatientCasesByThemeId(String themeId);

    /**
     * 根据主题ID查询报警设置
     */
    EcgAlarmSetup selectAlarmSetupByThemeId(String themeId);

    // =============================================
    // 操作
    // =============================================

    /**
     * 复制教学方案
     */
    EcgTheme copyEcgTheme(String themeId);


    /**
     * 获取所有教学方案资源列表
     * @return 资源列表
     */
    List<EcgThemeSource> selectAllThemeSources();
}
