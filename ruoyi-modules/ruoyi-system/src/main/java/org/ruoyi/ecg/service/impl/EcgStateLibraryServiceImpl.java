package org.ruoyi.ecg.service.impl;

import cn.hutool.core.lang.UUID;
import org.ruoyi.common.core.utils.DateUtils;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.ecg.domain.EcgStateLibrary;
import org.ruoyi.ecg.mapper.EcgStateLibraryMapper;
import org.ruoyi.ecg.service.IEcgStateLibraryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * ECG状态库Service业务层处理
 *
 * @author ruoyi
 * @date 2026-08-06
 */
@Service
public class EcgStateLibraryServiceImpl implements IEcgStateLibraryService {

    private static final Logger logger = LoggerFactory.getLogger(EcgStateLibraryServiceImpl.class);
    @Autowired
    private EcgStateLibraryMapper ecgStateLibraryMapper;

    /**
     * 查询ECG状态
     *
     * @param stateId 状态ID
     * @return ECG状态
     */
    @Override
    public EcgStateLibrary selectEcgStateLibraryById(String stateId) {
        if (StringUtils.isEmpty(stateId)) {
            logger.warn("查询ECG状态失败：stateId为空");
            return null;
        }
        return ecgStateLibraryMapper.selectStateById(stateId);
    }

    /**
     * 查询ECG状态列表
     *
     * @param ecgStateLibrary 查询条件
     * @return ECG状态集合
     */
    @Override
    public List<EcgStateLibrary> selectEcgStateLibraryList(EcgStateLibrary ecgStateLibrary) {
        if (ecgStateLibrary == null) {
            ecgStateLibrary = new EcgStateLibrary();
        }
        return ecgStateLibraryMapper.selectStateList(ecgStateLibrary);
    }

    /**
     * 查询所有ECG状态（下拉选择用）
     *
     * @return ECG状态集合
     */
    @Override
    public List<EcgStateLibrary> selectAllEcgStateLibrary() {
        return ecgStateLibraryMapper.selectAllState();
    }

    /**
     * 查询所有启用的ECG状态（下拉选择用）
     *
     * @return ECG状态集合
     */
    @Override
    public List<EcgStateLibrary> selectActiveEcgStateLibrary() {
        return ecgStateLibraryMapper.selectActiveState();
    }

    /**
     * 查询ECG状态简略信息（仅ID、名称、别名）
     *
     * @return ECG状态简略集合
     */
    @Override
    public List<EcgStateLibrary> selectSimpleEcgStateLibrary() {
        return ecgStateLibraryMapper.selectSimpleState();
    }

    /**
     * 根据分类查询ECG状态列表
     *
     * @param categoryCode 分类代码（BRADY/TACHY/ARREST/ROSC/OTHER）
     * @return ECG状态集合
     */
    @Override
    public List<EcgStateLibrary> selectEcgStateLibraryByCategory(String categoryCode) {
        if (StringUtils.isEmpty(categoryCode)) {
            logger.warn("根据分类查询ECG状态失败：categoryCode为空");
            return null;
        }
        return ecgStateLibraryMapper.selectStateByCategory(categoryCode);
    }

    /**
     * 查询所有分类列表（去重，下拉选择用）
     *
     * @return 分类代码列表
     */
    @Override
    public List<String> selectCategoryList() {
        return ecgStateLibraryMapper.selectCategoryList();
    }

    /**
     * 新增ECG状态
     *
     * @param ecgStateLibrary ECG状态
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertEcgStateLibrary(EcgStateLibrary ecgStateLibrary) {
        if (ecgStateLibrary == null) {
            logger.warn("新增ECG状态失败：参数为空");
            return 0;
        }

        // 1. 生成UUID主键
        if (StringUtils.isEmpty(ecgStateLibrary.getStateId())) {
            ecgStateLibrary.setStateId(UUID.fastUUID().toString().replace("-", ""));
        }

        // 2. 校验状态名称唯一性
        if (StringUtils.isNotEmpty(ecgStateLibrary.getStateName())) {
            if (ecgStateLibraryMapper.countStateByStateName(ecgStateLibrary.getStateName()) > 0) {
                logger.warn("新增ECG状态失败：状态名称【{}】已存在", ecgStateLibrary.getStateName());
                throw new RuntimeException("状态名称【" + ecgStateLibrary.getStateName() + "】已存在，请重新输入");
            }
        }

        // 3. 校验波形代码唯一性
        if (StringUtils.isNotEmpty(ecgStateLibrary.getWaveformCode())) {
            if (ecgStateLibraryMapper.countStateByWaveformCode(ecgStateLibrary.getWaveformCode()) > 0) {
                logger.warn("新增ECG状态失败：波形代码【{}】已存在", ecgStateLibrary.getWaveformCode());
                throw new RuntimeException("波形代码【" + ecgStateLibrary.getWaveformCode() + "】已存在，请重新输入");
            }
        }

        // 4. 设置默认值
        if (StringUtils.isEmpty(ecgStateLibrary.getIsActive())) {
            ecgStateLibrary.setIsActive("1");
        }
        if (StringUtils.isEmpty(ecgStateLibrary.getDelFlag())) {
            ecgStateLibrary.setDelFlag("0");
        }

        // 5. 设置创建时间
       // ecgStateLibrary.setCreateTime(DateUtils.getNowDate());

        logger.info("新增ECG状态：{}（{}），分类：{}，波形：{}",
            ecgStateLibrary.getStateName(),
            ecgStateLibrary.getStateAlias(),
            ecgStateLibrary.getCategoryCode(),
            ecgStateLibrary.getWaveformDisplay());
        return ecgStateLibraryMapper.insertState(ecgStateLibrary);
    }

    /**
     * 修改ECG状态
     *
     * @param ecgStateLibrary ECG状态
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateEcgStateLibrary(EcgStateLibrary ecgStateLibrary) {
        if (ecgStateLibrary == null || StringUtils.isEmpty(ecgStateLibrary.getStateId())) {
            logger.warn("修改ECG状态失败：参数为空或stateId为空");
            return 0;
        }

        // 1. 校验状态是否存在
        if (ecgStateLibraryMapper.countStateById(ecgStateLibrary.getStateId()) == 0) {
            logger.warn("修改ECG状态失败：状态ID【{}】不存在", ecgStateLibrary.getStateId());
            throw new RuntimeException("状态ID【" + ecgStateLibrary.getStateId() + "】不存在，请检查");
        }

        // 2. 校验状态名称唯一性（排除自身）
        if (StringUtils.isNotEmpty(ecgStateLibrary.getStateName())) {
            if (ecgStateLibraryMapper.countStateByStateNameExclude(
                    ecgStateLibrary.getStateName(), ecgStateLibrary.getStateId()) > 0) {
                logger.warn("修改ECG状态失败：状态名称【{}】已被其他记录使用", ecgStateLibrary.getStateName());
                throw new RuntimeException("状态名称【" + ecgStateLibrary.getStateName() + "】已存在，请重新输入");
            }
        }

        // 3. 校验波形代码唯一性（排除自身）
        if (StringUtils.isNotEmpty(ecgStateLibrary.getWaveformCode())) {
            if (ecgStateLibraryMapper.countStateByWaveformCodeExclude(
                    ecgStateLibrary.getWaveformCode(), ecgStateLibrary.getStateId()) > 0) {
                logger.warn("修改ECG状态失败：波形代码【{}】已被其他记录使用", ecgStateLibrary.getWaveformCode());
                throw new RuntimeException("波形代码【" + ecgStateLibrary.getWaveformCode() + "】已存在，请重新输入");
            }
        }

        // 4. 设置更新时间
       // ecgStateLibrary.setUpdateTime(DateUtils.getNowDate());

        logger.info("修改ECG状态：ID={}, 名称={}",
            ecgStateLibrary.getStateId(),
            ecgStateLibrary.getStateName());
        return ecgStateLibraryMapper.updateState(ecgStateLibrary);
    }

    /**
     * 切换启用/停用状态
     *
     * @param stateId 状态ID
     * @param isActive 启用状态（0-停用，1-启用）
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int changeActiveStatus(String stateId, String isActive) {
        if (StringUtils.isEmpty(stateId)) {
            logger.warn("切换启用状态失败：stateId为空");
            return 0;
        }

        // 校验状态是否存在
        if (ecgStateLibraryMapper.countStateById(stateId) == 0) {
            logger.warn("切换启用状态失败：状态ID【{}】不存在", stateId);
            throw new RuntimeException("状态ID【" + stateId + "】不存在，请检查");
        }

        logger.info("切换ECG状态启用状态：ID={}, 状态={}", stateId, "1".equals(isActive) ? "启用" : "停用");
        return ecgStateLibraryMapper.changeActiveStatus(stateId, isActive);
    }

    /**
     * 逻辑删除ECG状态（del_flag置为2）
     *
     * @param stateId 状态ID
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteEcgStateLibraryById(String stateId) {
        if (StringUtils.isEmpty(stateId)) {
            logger.warn("逻辑删除ECG状态失败：stateId为空");
            return 0;
        }

        // 校验状态是否存在
        if (ecgStateLibraryMapper.countStateById(stateId) == 0) {
            logger.warn("逻辑删除ECG状态失败：状态ID【{}】不存在", stateId);
            throw new RuntimeException("状态ID【" + stateId + "】不存在，请检查");
        }

        logger.info("逻辑删除ECG状态：ID={}", stateId);
        return ecgStateLibraryMapper.logicDeleteStateById(stateId);
    }

    /**
     * 批量逻辑删除ECG状态（del_flag置为2）
     *
     * @param stateIds 需要删除的状态ID数组
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteEcgStateLibraryByIds(String[] stateIds) {
        if (stateIds == null || stateIds.length == 0) {
            logger.warn("批量逻辑删除ECG状态失败：stateIds为空");
            return 0;
        }

        // 校验所有状态是否存在
        for (String stateId : stateIds) {
            if (ecgStateLibraryMapper.countStateById(stateId) == 0) {
                logger.warn("批量逻辑删除ECG状态失败：状态ID【{}】不存在", stateId);
                throw new RuntimeException("状态ID【" + stateId + "】不存在，请检查");
            }
        }

        logger.info("批量逻辑删除ECG状态：{}条", stateIds.length);
        return ecgStateLibraryMapper.logicDeleteStateByIds(stateIds);
    }

    /**
     * 物理删除ECG状态（慎用）
     *
     * @param stateId 状态ID
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int physicalDeleteEcgStateLibraryById(String stateId) {
        if (StringUtils.isEmpty(stateId)) {
            logger.warn("物理删除ECG状态失败：stateId为空");
            return 0;
        }

        // 校验状态是否存在
        if (ecgStateLibraryMapper.countStateById(stateId) == 0) {
            logger.warn("物理删除ECG状态失败：状态ID【{}】不存在", stateId);
            throw new RuntimeException("状态ID【" + stateId + "】不存在，请检查");
        }

        logger.warn("物理删除ECG状态：ID={}，此操作不可恢复！", stateId);
        return ecgStateLibraryMapper.deleteStateById(stateId);
    }

    /**
     * 批量物理删除ECG状态（慎用）
     *
     * @param stateIds 状态ID数组
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int physicalDeleteEcgStateLibraryByIds(String[] stateIds) {
        if (stateIds == null || stateIds.length == 0) {
            logger.warn("批量物理删除ECG状态失败：stateIds为空");
            return 0;
        }

        // 校验所有状态是否存在
        for (String stateId : stateIds) {
            if (ecgStateLibraryMapper.countStateById(stateId) == 0) {
                logger.warn("批量物理删除ECG状态失败：状态ID【{}】不存在", stateId);
                throw new RuntimeException("状态ID【" + stateId + "】不存在，请检查");
            }
        }

        logger.warn("批量物理删除ECG状态：{}条，此操作不可恢复！", stateIds.length);
        return ecgStateLibraryMapper.deleteStateByIds(stateIds);
    }

    /**
     * 批量调整排序号
     *
     * @param stateList 状态列表（包含stateId和sortNum）
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchUpdateSortNum(List<EcgStateLibrary> stateList) {
        if (stateList == null || stateList.isEmpty()) {
            logger.warn("批量调整排序号失败：stateList为空");
            return 0;
        }

        // 校验所有状态是否存在
        for (EcgStateLibrary state : stateList) {
            if (StringUtils.isEmpty(state.getStateId())) {
                logger.warn("批量调整排序号失败：stateId为空");
                throw new RuntimeException("状态ID不能为空");
            }
            if (ecgStateLibraryMapper.countStateById(state.getStateId()) == 0) {
                logger.warn("批量调整排序号失败：状态ID【{}】不存在", state.getStateId());
                throw new RuntimeException("状态ID【" + state.getStateId() + "】不存在，请检查");
            }
        }

        logger.info("批量调整ECG状态排序号：{}条", stateList.size());
        return ecgStateLibraryMapper.batchUpdateSortNum(stateList);
    }

    /**
     * 检查状态名称是否存在
     *
     * @param stateName 状态名称
     * @return true-存在，false-不存在
     */
    @Override
    public boolean checkStateNameExists(String stateName) {
        if (StringUtils.isEmpty(stateName)) {
            return false;
        }
        return ecgStateLibraryMapper.countStateByStateName(stateName) > 0;
    }

    /**
     * 检查波形代码是否存在
     *
     * @param waveformCode 波形代码
     * @return true-存在，false-不存在
     */
    @Override
    public boolean checkWaveformCodeExists(String waveformCode) {
        if (StringUtils.isEmpty(waveformCode)) {
            return false;
        }
        return ecgStateLibraryMapper.countStateByWaveformCode(waveformCode) > 0;
    }

    /**
     * 批量导入ECG状态
     *
     * @param stateList 状态列表
     * @param isUpdateSupport 是否更新支持，如果已存在则更新
     * @param operName 操作人
     * @return 导入结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String importStateLibrary(List<EcgStateLibrary> stateList, boolean isUpdateSupport, String operName) {
        if (stateList == null || stateList.isEmpty()) {
            return "导入数据为空";
        }

        int successNum = 0;
        int failureNum = 0;
        StringBuilder successMsg = new StringBuilder();
        StringBuilder failureMsg = new StringBuilder();

        for (EcgStateLibrary state : stateList) {
            try {
                // 检查状态名称是否存在
                EcgStateLibrary existState = ecgStateLibraryMapper.selectStateById(state.getStateId());
                if (existState == null) {
                    // 检查是否按名称存在
                    // 新增

                    this.insertEcgStateLibrary(state);
                    successNum++;
                    successMsg.append("<br/>").append(successNum).append("、").append(state.getStateName()).append(" 导入成功");
                } else if (isUpdateSupport) {
                    // 更新

                    this.updateEcgStateLibrary(state);
                    successNum++;
                    successMsg.append("<br/>").append(successNum).append("、").append(state.getStateName()).append(" 更新成功");
                } else {
                    failureNum++;
                    failureMsg.append("<br/>").append(failureNum).append("、").append(state.getStateName()).append(" 已存在");
                }
            } catch (Exception e) {
                failureNum++;
                failureMsg.append("<br/>").append(failureNum).append("、").append(state.getStateName()).append(" 导入失败：").append(e.getMessage());
                logger.error("导入ECG状态失败：{}", state.getStateName(), e);
            }
        }

        String result = "导入完成！成功 " + successNum + " 条" + failureMsg;
        if (failureNum > 0) {
            result += "，失败 " + failureNum + " 条" + failureMsg;
        }
        return result;
    }

    /**
     * 生成状态编码（扩展方法）
     * 格式：STATE + yyyyMMdd + 4位流水号
     *
     * @return 状态编码
     */
    private String generateStateCode() {
        String dateStr = DateUtils.getDate().replace("-", "");
        // 实际使用时需要查询今日最大流水号
        String uuid = UUID.fastUUID().toString().replace("-", "").substring(0, 6);
        return "STATE" + dateStr + uuid;
    }
}
