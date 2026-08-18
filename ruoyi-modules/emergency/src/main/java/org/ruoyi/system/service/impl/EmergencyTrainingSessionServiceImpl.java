package org.ruoyi.system.service.impl;

import org.ruoyi.common.core.utils.MapstructUtils;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.ruoyi.common.mybatis.core.page.PageQuery;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ruoyi.system.domain.bo.EmergencyTrainingSessionBo;
import org.ruoyi.system.domain.vo.EmergencyTrainingSessionVo;
import org.ruoyi.system.domain.vo.EmergencySessionEventLogVo;
import org.ruoyi.system.domain.vo.EmergencySessionScoreRecordVo;
import org.ruoyi.system.domain.vo.EmergencySessionStateSnapshotVo;
import org.ruoyi.system.domain.EmergencyTrainingSession;
import org.ruoyi.system.domain.EmergencySessionEventLog;
import org.ruoyi.system.domain.EmergencySessionScoreRecord;
import org.ruoyi.system.domain.EmergencySessionStateSnapshot;
import org.ruoyi.system.mapper.EmergencyTrainingSessionMapper;
import org.ruoyi.system.mapper.EmergencySessionEventLogMapper;
import org.ruoyi.system.mapper.EmergencySessionScoreRecordMapper;
import org.ruoyi.system.mapper.EmergencySessionStateSnapshotMapper;
import org.ruoyi.system.service.IEmergencyTrainingSessionService;

import java.util.List;
import java.util.Map;
import java.util.Collection;

/**
 * 训练会话Service业务层处理
 *
 * @author ageerle
 * @date 2026-08-18
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class EmergencyTrainingSessionServiceImpl implements IEmergencyTrainingSessionService {

    private final EmergencyTrainingSessionMapper baseMapper;

    /**
     * 注入所有关联子表 Mapper
     */
    private final EmergencySessionEventLogMapper eventLogMapper;
    private final EmergencySessionScoreRecordMapper scoreRecordMapper;
    private final EmergencySessionStateSnapshotMapper stateSnapshotMapper;

    /**
     * 查询训练会话
     *
     * @param id 主键
     * @return 训练会话
     */
    @Override
    @Transactional(readOnly = true)
    public EmergencyTrainingSessionVo queryById(Long id){
        return baseMapper.selectVoById(id);
    }

    /**
     * 分页查询训练会话列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 训练会话分页列表
     */
    @Override
    @Transactional(readOnly = true)
    public TableDataInfo<EmergencyTrainingSessionVo> queryPageList(EmergencyTrainingSessionBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<EmergencyTrainingSession> lqw = buildQueryWrapper(bo);
        Page<EmergencyTrainingSessionVo> result = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(result);
    }

    /**
     * 查询符合条件的训练会话列表
     *
     * @param bo 查询条件
     * @return 训练会话列表
     */
    @Override
    @Transactional(readOnly = true)
    public List<EmergencyTrainingSessionVo> queryList(EmergencyTrainingSessionBo bo) {
        LambdaQueryWrapper<EmergencyTrainingSession> lqw = buildQueryWrapper(bo);
        return baseMapper.selectVoList(lqw);
    }

    private LambdaQueryWrapper<EmergencyTrainingSession> buildQueryWrapper(EmergencyTrainingSessionBo bo) {
        Map<String, Object> params = bo.getParams();
        LambdaQueryWrapper<EmergencyTrainingSession> lqw = Wrappers.lambdaQuery();
        lqw.orderByAsc(EmergencyTrainingSession::getId);
        // 已删除 deptId、userId、orderNum（这些字段在实体类中已移除，属于基类字段）
        // lqw.eq(bo.getDeptId() != null, EmergencyTrainingSession::getDeptId, bo.getDeptId());
        // lqw.eq(bo.getUserId() != null, EmergencyTrainingSession::getUserId, bo.getUserId());
        // lqw.eq(bo.getOrderNum() != null, EmergencyTrainingSession::getOrderNum, bo.getOrderNum());
        lqw.eq(StringUtils.isNotBlank(bo.getSessionCode()), EmergencyTrainingSession::getSessionCode, bo.getSessionCode());
        lqw.eq(bo.getOrganizationId() != null, EmergencyTrainingSession::getOrganizationId, bo.getOrganizationId());
        lqw.eq(bo.getCaseVersionId() != null, EmergencyTrainingSession::getCaseVersionId, bo.getCaseVersionId());
        lqw.eq(StringUtils.isNotBlank(bo.getCaseDefinitionHash()), EmergencyTrainingSession::getCaseDefinitionHash, bo.getCaseDefinitionHash());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), EmergencyTrainingSession::getStatus, bo.getStatus());
        lqw.eq(bo.getStartedAt() != null, EmergencyTrainingSession::getStartedAt, bo.getStartedAt());
        lqw.eq(bo.getEndedAt() != null, EmergencyTrainingSession::getEndedAt, bo.getEndedAt());
        return lqw;
    }

    /**
     * 新增训练会话
     *
     * @param bo 训练会话
     * @return 是否新增成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean insertByBo(EmergencyTrainingSessionBo bo) {
        EmergencyTrainingSession add = MapstructUtils.convert(bo, EmergencyTrainingSession.class);
        validEntityBeforeSave(add);
        boolean flag = baseMapper.insert(add) > 0;
        if (flag) {
            bo.setId(add.getId());
        }
        return flag;
    }

    /**
     * 修改训练会话
     *
     * @param bo 训练会话
     * @return 是否修改成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateByBo(EmergencyTrainingSessionBo bo) {
        EmergencyTrainingSession update = MapstructUtils.convert(bo, EmergencyTrainingSession.class);
        validEntityBeforeSave(update);
        return baseMapper.updateById(update) > 0;
    }

    /**
     * 保存前的数据校验
     */
    private void validEntityBeforeSave(EmergencyTrainingSession entity){
        //TODO 做一些数据校验,如唯一约束
    }

    /**
     * 校验并批量删除训练会话信息
     *
     * @param ids     待删除的主键集合
     * @param isValid 是否进行有效性校验
     * @return 是否删除成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        if(isValid){
            //TODO 做一些业务上的校验,判断是否需要校验
        }
        return baseMapper.deleteByIds(ids) > 0;
    }

    // ==================== 父子表关联查询方法 ====================

    /**
     * 获取训练会话及其所有关联数据（事件日志、评分记录、状态快照）
     *
     * @param id 会话ID
     * @return 包含所有关联数据的 Vo
     */
    @Override
    @Transactional(readOnly = true)
    public EmergencyTrainingSessionVo getSessionWithAllData(Long id) {
        // 1. 查询主表
        EmergencyTrainingSessionVo vo = baseMapper.selectVoById(id);
        if (vo == null) {
            return null;
        }

        // 2. 查询所有子表数据
        vo.setEventLogs(getEventLogsBySessionId(id));
        vo.setScoreRecords(getScoreRecordsBySessionId(id));
        vo.setStateSnapshots(getStateSnapshotsBySessionId(id));

        return vo;
    }

    /**
     * 获取会话的事件日志列表（按序列号升序）
     *
     * @param sessionId 会话ID
     * @return 事件日志列表
     */
    @Override
    @Transactional(readOnly = true)
    public List<EmergencySessionEventLogVo> getEventLogsBySessionId(Long sessionId) {
        LambdaQueryWrapper<EmergencySessionEventLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EmergencySessionEventLog::getSessionId, sessionId)
            .orderByAsc(EmergencySessionEventLog::getSequenceNo);
        return eventLogMapper.selectVoList(wrapper);
    }

    /**
     * 获取会话的评分记录列表（按ID升序）
     *
     * @param sessionId 会话ID
     * @return 评分记录列表
     */
    @Override
    @Transactional(readOnly = true)
    public List<EmergencySessionScoreRecordVo> getScoreRecordsBySessionId(Long sessionId) {
        LambdaQueryWrapper<EmergencySessionScoreRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EmergencySessionScoreRecord::getSessionId, sessionId)
            .orderByAsc(EmergencySessionScoreRecord::getId);
        return scoreRecordMapper.selectVoList(wrapper);
    }

    /**
     * 获取会话的状态快照列表（按序列号升序）
     *
     * @param sessionId 会话ID
     * @return 状态快照列表
     */
    @Override
    @Transactional(readOnly = true)
    public List<EmergencySessionStateSnapshotVo> getStateSnapshotsBySessionId(Long sessionId) {
        LambdaQueryWrapper<EmergencySessionStateSnapshot> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EmergencySessionStateSnapshot::getSessionId, sessionId)
            .orderByAsc(EmergencySessionStateSnapshot::getSequenceNo);
        return stateSnapshotMapper.selectVoList(wrapper);
    }
}
