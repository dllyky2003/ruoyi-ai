package org.ruoyi.ecg.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.core.utils.DateUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import org.ruoyi.ecg.domain.EcgAlarmSetup;
import org.ruoyi.ecg.domain.EcgPatientCase;
import org.ruoyi.ecg.domain.EcgTheme;
import org.ruoyi.ecg.domain.EcgThemeEvent;
import org.ruoyi.ecg.domain.EcgThemeSource;
import org.ruoyi.ecg.domain.EcgThemeState;
import org.ruoyi.ecg.mapper.EcgThemeMapper;
import org.ruoyi.ecg.service.IEcgThemeService;

import jakarta.annotation.Resource;

/**
 * 教学方案Service业务层处理
 *
 * @author ruoyi
 */
@Service
public class EcgThemeServiceImpl implements IEcgThemeService {

    @Resource
    private EcgThemeMapper themeMapper;

    // =============================================
    // 新增
    // =============================================

    /**
     * 新增完整教学方案
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertEcgTheme(EcgTheme theme) {
        validateTheme(theme);

        // 如果未设置ID，自动生成
        if (isBlank(theme.getThemeId())) {
            theme.setThemeId(generateThemeId());
        }

        // 检查ID是否已存在
        if (themeMapper.countThemeById(theme.getThemeId()) > 0) {
            throw new ServiceException("教学方案ID已存在");
        }

        // 检查名称是否已存在
        if (themeMapper.countThemeByThemeName(theme.getThemeName()) > 0) {
            throw new ServiceException("教学方案名称已存在");
        }

        // 设置创建时间和更新时间
      //  theme.setCreateTime(DateUtils.getNowDate());
//theme.setUpdateTime(DateUtils.getNowDate());

        // 插入主表
        int result = themeMapper.insertTheme(theme);
        checkResult(result, "教学方案主记录保存失败");

        // 插入子表
        insertStates(theme);
        insertEvents(theme);
        insertSources(theme);
        insertPatientCases(theme);
        insertAlarmSetup(theme);

        return result;
    }

    /**
     * 新增教学方案主表信息（不包含子表）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertEcgThemeBasic(EcgTheme theme) {
        validateTheme(theme);

        // 如果未设置ID，自动生成
        if (isBlank(theme.getThemeId())) {
            theme.setThemeId(generateThemeId());
        }

        // 检查ID是否已存在
        if (themeMapper.countThemeById(theme.getThemeId()) > 0) {
            throw new ServiceException("教学方案ID已存在");
        }

        // 检查名称是否已存在
        if (themeMapper.countThemeByThemeName(theme.getThemeName()) > 0) {
            throw new ServiceException("教学方案名称已存在");
        }

        // 设置创建时间和更新时间
      //  theme.setCreateTime(DateUtils.getNowDate());
       // theme.setUpdateTime(DateUtils.getNowDate());

        int result = themeMapper.insertTheme(theme);
        checkResult(result, "教学方案主记录保存失败");

        return result;
    }

    // =============================================
    // 删除
    // =============================================

    /**
     * 删除教学方案（物理删除，同时删除关联子表数据）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteEcgThemeById(String themeId) {
        requireId(themeId, "教学方案ID不能为空");

        // 检查是否存在
        if (themeMapper.countThemeById(themeId) == 0) {
            throw new ServiceException("教学方案不存在");
        }

        // 由于数据库设置了 ON DELETE CASCADE，删除主表会自动删除子表数据
        // 但为了兼容性，先手动删除子表
        themeMapper.deleteStatesByThemeId(themeId);
        themeMapper.deleteEventsByThemeId(themeId);
        themeMapper.deleteSourcesByThemeId(themeId);
        themeMapper.deletePatientCasesByThemeId(themeId);
        themeMapper.deleteAlarmSetupByThemeId(themeId);

        // 删除主表数据
        int result = themeMapper.deleteThemeById(themeId);
        checkResult(result, "教学方案删除失败");

        return result;
    }

    /**
     * 批量删除教学方案
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteEcgThemeByIds(String[] themeIds) {
        if (themeIds == null || themeIds.length == 0) {
            throw new ServiceException("请选择要删除的教学方案");
        }

        int total = 0;
        for (String themeId : themeIds) {
            total += deleteEcgThemeById(themeId);
        }
        return total;
    }

    // =============================================
    // 更新
    // =============================================

    /**
     * 更新完整教学方案（先删除所有子表数据，再重新插入）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateEcgTheme(EcgTheme theme) {
        if (theme == null) {
            throw new ServiceException("教学方案对象不能为空");
        }

        String themeId = theme.getThemeId();
        requireId(themeId, "教学方案ID不能为空");

        // 检查是否存在
        if (themeMapper.countThemeById(themeId) == 0) {
            throw new ServiceException("教学方案不存在");
        }

        // 校验名称是否与其他记录冲突
        if (!isBlank(theme.getThemeName())) {
            int count = themeMapper.countThemeByThemeNameExclude(theme.getThemeName(), themeId);
            if (count > 0) {
                throw new ServiceException("教学方案名称已存在");
            }
        }

        validateTheme(theme);

        // 设置更新时间
       // theme.setUpdateTime(DateUtils.getNowDate());

        // 更新主表
        int result = themeMapper.updateTheme(theme);
        checkResult(result, "教学方案主记录更新失败");

        // 删除旧的关联子表数据
        themeMapper.deleteStatesByThemeId(themeId);
        themeMapper.deleteEventsByThemeId(themeId);
        themeMapper.deleteSourcesByThemeId(themeId);
        themeMapper.deletePatientCasesByThemeId(themeId);
        themeMapper.deleteAlarmSetupByThemeId(themeId);

        // 重新插入新的关联子表数据
        insertStates(theme);
        insertEvents(theme);
        insertSources(theme);
        insertPatientCases(theme);
        insertAlarmSetup(theme);

        return result;
    }

    /**
     * 更新教学方案主表信息（不更新子表）
     */
    @Override
    public int updateEcgThemeBasic(EcgTheme theme) {
        if (theme == null) {
            throw new ServiceException("教学方案对象不能为空");
        }

        String themeId = theme.getThemeId();
        requireId(themeId, "教学方案ID不能为空");

        // 检查是否存在
        if (themeMapper.countThemeById(themeId) == 0) {
            throw new ServiceException("教学方案不存在");
        }

        // 校验名称是否与其他记录冲突
        if (!isBlank(theme.getThemeName())) {
            int count = themeMapper.countThemeByThemeNameExclude(theme.getThemeName(), themeId);
            if (count > 0) {
                throw new ServiceException("教学方案名称已存在");
            }
        }

        validateTheme(theme);

        // 设置更新时间
       // theme.setUpdateTime(DateUtils.getNowDate());

        int result = themeMapper.updateTheme(theme);
        checkResult(result, "教学方案更新失败");

        return result;
    }

    /**
     * 更新当前状态
     */
    @Override
    public int updateCurrentState(String themeId, String stateId) {
        requireId(themeId, "教学方案ID不能为空");
        requireId(stateId, "状态ID不能为空");

        // 检查主题是否存在
        if (themeMapper.countThemeById(themeId) == 0) {
            throw new ServiceException("教学方案不存在");
        }

        EcgTheme theme = new EcgTheme();
        theme.setThemeId(themeId);
        theme.setCurrentStateId(stateId);
       // theme.setUpdateTime(DateUtils.getNowDate());

        int result = themeMapper.updateTheme(theme);
        checkResult(result, "更新当前状态失败");

        return result;
    }

    /**
     * 更新当前事件
     */
    @Override
    public int updateCurrentEvent(String themeId, String eventId) {
        requireId(themeId, "教学方案ID不能为空");
        requireId(eventId, "事件ID不能为空");

        // 检查主题是否存在
        if (themeMapper.countThemeById(themeId) == 0) {
            throw new ServiceException("教学方案不存在");
        }

        EcgTheme theme = new EcgTheme();
        theme.setThemeId(themeId);
        theme.setCurrentEventId(eventId);
        //theme.setUpdateTime(DateUtils.getNowDate());

        int result = themeMapper.updateTheme(theme);
        checkResult(result, "更新当前事件失败");

        return result;
    }

    /**
     * 更新AES密钥
     */
    @Override
    public int updateAesKeys(String themeId, String aesKeys) {
        requireId(themeId, "教学方案ID不能为空");

        if (isBlank(aesKeys)) {
            throw new ServiceException("AES密钥不能为空");
        }

        // 检查主题是否存在
        if (themeMapper.countThemeById(themeId) == 0) {
            throw new ServiceException("教学方案不存在");
        }

        EcgTheme theme = new EcgTheme();
        theme.setThemeId(themeId);
        theme.setAesKeys(aesKeys);
       // theme.setUpdateTime(DateUtils.getNowDate());

        int result = themeMapper.updateTheme(theme);
        checkResult(result, "更新AES密钥失败");

        return result;
    }

    // =============================================
    // 查询 - 单条
    // =============================================

    /**
     * 根据ID查询完整教学方案（包含所有子表数据）
     */
    @Override
    public EcgTheme selectEcgThemeById(String themeId) {
        requireId(themeId, "教学方案ID不能为空");
        return themeMapper.selectThemeById(themeId);
    }

    /**
     * 根据ID查询教学方案主表信息（不包含子表）
     */
    @Override
    public EcgTheme selectEcgThemeBasicById(String themeId) {
        requireId(themeId, "教学方案ID不能为空");
        return themeMapper.selectThemeBasicById(themeId);
    }

    // =============================================
    // 查询 - 列表
    // =============================================

    /**
     * 获取所有教学方案简略信息列表（只包含主表字段，不包含子表）
     */
    @Override
    public List<EcgTheme> selectEcgThemeList() {
        return themeMapper.selectAllThemeList();
    }

    /**
     * 获取所有教学方案简略信息列表（支持条件筛选）
     */
    @Override
    public List<EcgTheme> selectEcgThemeList(EcgTheme theme) {
        return themeMapper.selectThemeList(theme);
    }

    /**
     * 获取所有教学方案简略信息列表（分页查询）
     */
    @Override
    public List<EcgTheme> selectEcgThemeListPage(EcgTheme theme, int pageNum, int pageSize) {
        // 计算偏移量
        int offset = (pageNum - 1) * pageSize;
        return themeMapper.selectThemeListPage(theme, offset, pageSize);
    }

    /**
     * 获取所有教学方案（无分页，下拉选择用）
     */
    @Override
    public List<EcgTheme> selectAllEcgTheme(EcgTheme theme) {
        return themeMapper.selectAllTheme(theme);
    }

    /**
     * 获取所有教学方案简略信息（仅ID和名称）
     */
    @Override
    public List<EcgTheme> selectSimpleEcgTheme() {
        return themeMapper.selectSimpleTheme();
    }

    // =============================================
    // 查询 - 统计
    // =============================================

    /**
     * 统计教学方案总数
     */
    @Override
    public int countTheme(EcgTheme theme) {
        return themeMapper.countTheme(theme);
    }

    /**
     * 检查教学方案名称是否已存在
     */
    @Override
    public boolean checkThemeNameExists(String themeName) {
        if (isBlank(themeName)) {
            return false;
        }
        return themeMapper.countThemeByThemeName(themeName) > 0;
    }

    /**
     * 检查教学方案名称是否已存在（排除指定ID）
     */
    @Override
    public boolean checkThemeNameExists(String themeName, String excludeThemeId) {
        if (isBlank(themeName)) {
            return false;
        }
        if (isBlank(excludeThemeId)) {
            return checkThemeNameExists(themeName);
        }
        return themeMapper.countThemeByThemeNameExclude(themeName, excludeThemeId) > 0;
    }

    /**
     * 检查教学方案ID是否已存在
     */
    @Override
    public boolean checkThemeIdExists(String themeId) {
        if (isBlank(themeId)) {
            return false;
        }
        return themeMapper.countThemeById(themeId) > 0;
    }

    // =============================================
    // 查询 - 子表
    // =============================================

    /**
     * 根据主题ID查询所有状态列表
     */
    @Override
    public List<EcgThemeState> selectStatesByThemeId(String themeId) {
        requireId(themeId, "主题ID不能为空");
        return themeMapper.selectStatesByThemeId(themeId);
    }

    /**
     * 根据主题ID查询所有事件列表
     */
    @Override
    public List<EcgThemeEvent> selectEventsByThemeId(String themeId) {
        requireId(themeId, "主题ID不能为空");
        return themeMapper.selectEventsByThemeId(themeId);
    }

    /**
     * 根据主题ID查询所有资源列表
     */
    @Override
    public List<EcgThemeSource> selectSourcesByThemeId(String themeId) {
        requireId(themeId, "主题ID不能为空");
        return themeMapper.selectSourcesByThemeId(themeId);
    }

    /**
     * 根据主题ID查询所有病例列表
     */
    @Override
    public List<EcgPatientCase> selectPatientCasesByThemeId(String themeId) {
        requireId(themeId, "主题ID不能为空");
        return themeMapper.selectPatientCasesByThemeId(themeId);
    }

    /**
     * 根据主题ID查询报警设置
     */
    @Override
    public EcgAlarmSetup selectAlarmSetupByThemeId(String themeId) {
        requireId(themeId, "主题ID不能为空");
        return themeMapper.selectAlarmSetupByThemeId(themeId);
    }

    // =============================================
    // 操作
    // =============================================

    /**
     * 复制教学方案
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public EcgTheme copyEcgTheme(String themeId) {
        requireId(themeId, "教学方案ID不能为空");

        // 查询原方案
        EcgTheme sourceTheme = themeMapper.selectThemeById(themeId);
        if (sourceTheme == null) {
            throw new ServiceException("源教学方案不存在");
        }

        // 创建新方案
        EcgTheme newTheme = new EcgTheme();

        // 生成新的ID
        newTheme.setThemeId(generateThemeId());

        // 复制主表字段
        newTheme.setThemeName(sourceTheme.getThemeName() + "_副本");
        newTheme.setMemo(sourceTheme.getMemo());
        newTheme.setCategory(sourceTheme.getCategory());
        newTheme.setAesKeys(sourceTheme.getAesKeys());
        newTheme.setThemeIntro(sourceTheme.getThemeIntro());
        newTheme.setPatientInfo(sourceTheme.getPatientInfo());
        newTheme.setCurrentStateId(sourceTheme.getCurrentStateId());
        newTheme.setCurrentEventId(sourceTheme.getCurrentEventId());

        // 设置创建时间和更新时间
      //  newTheme.setCreateTime(DateUtils.getNowDate());
      //  newTheme.setUpdateTime(DateUtils.getNowDate());

        // 检查名称是否已存在
        if (themeMapper.countThemeByThemeName(newTheme.getThemeName()) > 0) {
            // 如果名称已存在，在后面加时间戳
            newTheme.setThemeName(newTheme.getThemeName() + "_" + System.currentTimeMillis());
        }

        // 插入主表
        int result = themeMapper.insertTheme(newTheme);
        checkResult(result, "复制教学方案主记录失败");

        // 复制子表数据
        copyStates(sourceTheme.getStates(), newTheme.getThemeId());
        copyEvents(sourceTheme.getEvents(), newTheme.getThemeId());
        copySources(sourceTheme.getSources(), newTheme.getThemeId());
        copyPatientCases(sourceTheme.getPatientCases(), newTheme.getThemeId());
        copyAlarmSetup(sourceTheme.getAlarmSetup(), newTheme.getThemeId());

        return newTheme;
    }

    // =============================================
    // 私有辅助方法
    // =============================================

    /**
     * 生成主题ID
     */
    private String generateThemeId() {
        return "TH" + System.currentTimeMillis() + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    /**
     * 校验教学方案
     */
    private void validateTheme(EcgTheme theme) {
        if (theme == null) {
            throw new ServiceException("教学方案对象不能为空");
        }
        if (isBlank(theme.getThemeName())) {
            throw new ServiceException("教学方案名称不能为空");
        }
        if (theme.getThemeName().length() > 200) {
            throw new ServiceException("教学方案名称长度不能超过200个字符");
        }
        if (theme.getThemeIntro() != null && theme.getThemeIntro().length() > 65535) {
            throw new ServiceException("教学方案介绍内容过长");
        }
        if (theme.getPatientInfo() != null && theme.getPatientInfo().length() > 65535) {
            throw new ServiceException("病情描述内容过长");
        }
        if (theme.getMemo() != null && theme.getMemo().length() > 1000) {
            throw new ServiceException("教学方案说明长度不能超过1000个字符");
        }

    }

    /**
     * 保存状态
     */
    private void insertStates(EcgTheme theme) {
        List<EcgThemeState> states = theme.getStates();
        if (states == null || states.isEmpty()) {
            return;
        }

        for (EcgThemeState item : states) {
            if (item == null) {
                throw new ServiceException("状态对象不能为空");
            }
            requireId(item.getStateId(), "状态ID不能为空");

            // 如果状态名称未设置，使用默认值
            if (isBlank(item.getStateName())) {
                item.setStateName("未命名状态");
            }

            item.setThemeId(theme.getThemeId());

            int result = themeMapper.insertState(item);
            checkResult(result, "状态记录保存失败");
        }
    }

    /**
     * 保存事件
     */
    private void insertEvents(EcgTheme theme) {
        List<EcgThemeEvent> events = theme.getEvents();
        if (events == null || events.isEmpty()) {
            return;
        }

        for (EcgThemeEvent item : events) {
            if (item == null) {
                throw new ServiceException("事件对象不能为空");
            }
            requireId(item.getEventId(), "事件ID不能为空");

            item.setThemeId(theme.getThemeId());

            int result = themeMapper.insertEvent(item);
            checkResult(result, "事件记录保存失败");
        }
    }

    /**
     * 保存资源
     */
    private void insertSources(EcgTheme theme) {
        List<EcgThemeSource> sources = theme.getSources();
        if (sources == null || sources.isEmpty()) {
            return;
        }

        for (EcgThemeSource item : sources) {
            if (item == null) {
                throw new ServiceException("资源对象不能为空");
            }
            requireId(item.getSourceId(), "资源ID不能为空");

            item.setThemeId(theme.getThemeId());

            int result = themeMapper.insertSource(item);
            checkResult(result, "资源记录保存失败");
        }
    }

    /**
     * 保存病例
     */
    private void insertPatientCases(EcgTheme theme) {
        List<EcgPatientCase> patientCases = theme.getPatientCases();
        if (patientCases == null || patientCases.isEmpty()) {
            return;
        }

        // 校验并填充 themeId
        for (EcgPatientCase item : patientCases) {
            if (item == null) {
                throw new ServiceException("病例对象不能为空");
            }
            requireId(item.getPatientCaseId(), "病例ID不能为空");
            item.setThemeId(theme.getThemeId());
        }

        int expected = patientCases.size();
        int actual = themeMapper.batchInsertPatientCases(patientCases);

        // 精确比较：插入条数必须等于预期条数
        if (actual != expected) {
            throw new ServiceException("病例记录保存失败，预期插入 " + expected + " 条，实际插入 " + actual + " 条");
        }
    }

    /**
     * 保存报警配置
     */
    private void insertAlarmSetup(EcgTheme theme) {
        EcgAlarmSetup setup = theme.getAlarmSetup();
        if (setup == null) {
            return;
        }

        setup.setThemeId(theme.getThemeId());

        int result = themeMapper.insertAlarmSetup(setup);
        checkResult(result, "报警配置保存失败");
    }

    /**
     * 复制状态
     */
    private void copyStates(List<EcgThemeState> sourceStates, String newThemeId) {
        if (sourceStates == null || sourceStates.isEmpty()) {
            return;
        }

        for (EcgThemeState source : sourceStates) {
            EcgThemeState newState = new EcgThemeState();
            newState.setStateId(generateId("ST"));
            newState.setThemeId(newThemeId);
            newState.setStateName(source.getStateName());
            newState.setRhythmName(source.getRhythmName());
            newState.setHr(source.getHr());
            newState.setHrChangeTime(source.getHrChangeTime());
            newState.setHrChangeType(source.getHrChangeType());
            newState.setSpo2(source.getSpo2());
            newState.setSpo2ChangeTime(source.getSpo2ChangeTime());
            newState.setSpo2ChangeType(source.getSpo2ChangeType());
            newState.setCo2(source.getCo2());
            newState.setCo2ChangeTime(source.getCo2ChangeTime());
            newState.setCo2ChangeType(source.getCo2ChangeType());
            newState.setNbpLow(source.getNbpLow());
            newState.setNbpHigh(source.getNbpHigh());
            newState.setNbpChangeTime(source.getNbpChangeTime());
            newState.setNbpChangeType(source.getNbpChangeType());
            newState.setBodyTemperature(source.getBodyTemperature());
            newState.setBreathRate(source.getBreathRate());
            newState.setAutoPlayTime(source.getAutoPlayTime());
            newState.setSortNum(source.getSortNum());

            int result = themeMapper.insertState(newState);
            checkResult(result, "复制状态记录失败");
        }
    }

    /**
     * 复制事件
     */
    private void copyEvents(List<EcgThemeEvent> sourceEvents, String newThemeId) {
        if (sourceEvents == null || sourceEvents.isEmpty()) {
            return;
        }

        for (EcgThemeEvent source : sourceEvents) {
            EcgThemeEvent newEvent = new EcgThemeEvent();
            newEvent.setEventId(generateId("EV"));
            newEvent.setThemeId(newThemeId);
            newEvent.setEventName(source.getEventName());
            newEvent.setIconIndex(source.getIconIndex());
            newEvent.setSortNum(source.getSortNum());

            int result = themeMapper.insertEvent(newEvent);
            checkResult(result, "复制事件记录失败");
        }
    }

    /**
     * 复制资源
     */
    private void copySources(List<EcgThemeSource> sourceSources, String newThemeId) {
        if (sourceSources == null || sourceSources.isEmpty()) {
            return;
        }

        for (EcgThemeSource source : sourceSources) {
            EcgThemeSource newSource = new EcgThemeSource();
            newSource.setSourceId(generateId("SR"));
            newSource.setThemeId(newThemeId);
            newSource.setSourceBusinessId(source.getSourceBusinessId());
            newSource.setSourceName(source.getSourceName());
            newSource.setSourceUrl(source.getSourceUrl());
            newSource.setPositionNum(source.getPositionNum());
            newSource.setSourceType(source.getSourceType());
            newSource.setLocalPath(source.getLocalPath());

            int result = themeMapper.insertSource(newSource);
            checkResult(result, "复制资源记录失败");
        }
    }

    private void copyPatientCases(List<EcgPatientCase> sourceCases, String newThemeId) {
        if (sourceCases == null || sourceCases.isEmpty()) {
            return;
        }

        List<EcgPatientCase> newCases = new ArrayList<>();
        for (EcgPatientCase source : sourceCases) {
            // 校验源数据
            if (source == null) {
                throw new ServiceException("源病例对象不能为空");
            }
            requireId(source.getPatientCaseId(), "源病例ID不能为空");

            EcgPatientCase newCase = new EcgPatientCase();
            // 复用相同的患者ID（关联到同一个患者），关联到新方案
            newCase.setPatientCaseId(source.getPatientCaseId());
            newCase.setThemeId(newThemeId);
            newCases.add(newCase);
        }

        // 批量插入
        int result = themeMapper.batchInsertPatientCases(newCases);
        if (result != newCases.size()) {
            throw new ServiceException("复制病例记录失败，预期插入 " + newCases.size() + " 条，实际插入 " + result + " 条");
        }
    }

    /**
     * 复制报警配置
     */
    private void copyAlarmSetup(EcgAlarmSetup sourceSetup, String newThemeId) {
        if (sourceSetup == null) {
            return;
        }

        EcgAlarmSetup newSetup = new EcgAlarmSetup();
        newSetup.setThemeId(newThemeId);
        newSetup.setHrLow(sourceSetup.getHrLow());
        newSetup.setHrHigh(sourceSetup.getHrHigh());
        newSetup.setSpo2Low(sourceSetup.getSpo2Low());
        newSetup.setSpo2High(sourceSetup.getSpo2High());
        newSetup.setCo2Low(sourceSetup.getCo2Low());
        newSetup.setCo2High(sourceSetup.getCo2High());
        newSetup.setNibpLow(sourceSetup.getNibpLow());
        newSetup.setNibpHigh(sourceSetup.getNibpHigh());
        newSetup.setBodyTemAlarmLow(sourceSetup.getBodyTemAlarmLow());
        newSetup.setBodyTemAlarmHigh(sourceSetup.getBodyTemAlarmHigh());
        newSetup.setBreathRateLow(sourceSetup.getBreathRateLow());
        newSetup.setBreathRateHigh(sourceSetup.getBreathRateHigh());

        int result = themeMapper.insertAlarmSetup(newSetup);
        checkResult(result, "复制报警配置失败");
    }

    /**
     * 生成ID
     */
    private String generateId(String prefix) {
        return prefix + System.currentTimeMillis() + UUID.randomUUID().toString().replace("-", "").substring(0, 6);
    }

    /**
     * 校验ID不能为空
     */
    private void requireId(String value, String message) {
        if (isBlank(value)) {
            throw new ServiceException(message);
        }
    }

    /**
     * 判断字符串是否为空
     */
    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * 检查执行结果
     */
    private void checkResult(int result, String message) {
        if (result != 1) {
            throw new ServiceException(message);
        }
    }

    @Override
    public List<EcgThemeSource> selectAllThemeSources() {
        return themeMapper.selectAllThemeSources();
    }
}
