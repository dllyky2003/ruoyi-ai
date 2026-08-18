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
import org.ruoyi.system.domain.bo.EmergencySessionEventLogBo;
import org.ruoyi.system.domain.vo.EmergencySessionEventLogVo;
import org.ruoyi.system.domain.EmergencySessionEventLog;
import org.ruoyi.system.mapper.EmergencySessionEventLogMapper;
import org.ruoyi.system.service.IEmergencySessionEventLogService;

import java.util.List;
import java.util.Map;
import java.util.Collection;

/**
 * 会话事件日志（追加式/可审计）Service业务层处理
 *
 * @author ageerle
 * @date 2026-08-18
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class EmergencySessionEventLogServiceImpl implements IEmergencySessionEventLogService {

    private final EmergencySessionEventLogMapper baseMapper;

    /**
     * 查询会话事件日志（追加式/可审计）
     *
     * @param id 主键
     * @return 会话事件日志（追加式/可审计）
     */
    @Override
    public EmergencySessionEventLogVo queryById(Long id){
        return baseMapper.selectVoById(id);
    }

    /**
     * 分页查询会话事件日志（追加式/可审计）列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 会话事件日志（追加式/可审计）分页列表
     */
    @Override
    public TableDataInfo<EmergencySessionEventLogVo> queryPageList(EmergencySessionEventLogBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<EmergencySessionEventLog> lqw = buildQueryWrapper(bo);
        Page<EmergencySessionEventLogVo> result = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(result);
    }

    /**
     * 查询符合条件的会话事件日志（追加式/可审计）列表
     *
     * @param bo 查询条件
     * @return 会话事件日志（追加式/可审计）列表
     */
    @Override
    public List<EmergencySessionEventLogVo> queryList(EmergencySessionEventLogBo bo) {
        LambdaQueryWrapper<EmergencySessionEventLog> lqw = buildQueryWrapper(bo);
        return baseMapper.selectVoList(lqw);
    }

    private LambdaQueryWrapper<EmergencySessionEventLog> buildQueryWrapper(EmergencySessionEventLogBo bo) {
        Map<String, Object> params = bo.getParams();
        LambdaQueryWrapper<EmergencySessionEventLog> lqw = Wrappers.lambdaQuery();
        lqw.orderByAsc(EmergencySessionEventLog::getId);
        lqw.eq(bo.getDeptId() != null, EmergencySessionEventLog::getDeptId, bo.getDeptId());
        lqw.eq(bo.getUserId() != null, EmergencySessionEventLog::getUserId, bo.getUserId());
        lqw.eq(bo.getOrderNum() != null, EmergencySessionEventLog::getOrderNum, bo.getOrderNum());
        lqw.eq(bo.getSessionId() != null, EmergencySessionEventLog::getSessionId, bo.getSessionId());
        lqw.eq(bo.getSequenceNo() != null, EmergencySessionEventLog::getSequenceNo, bo.getSequenceNo());
        lqw.eq(bo.getOccurredAt() != null, EmergencySessionEventLog::getOccurredAt, bo.getOccurredAt());
        lqw.eq(StringUtils.isNotBlank(bo.getEventType()), EmergencySessionEventLog::getEventType, bo.getEventType());
        lqw.eq(StringUtils.isNotBlank(bo.getSourceType()), EmergencySessionEventLog::getSourceType, bo.getSourceType());
        lqw.eq(StringUtils.isNotBlank(bo.getSourceId()), EmergencySessionEventLog::getSourceId, bo.getSourceId());
        lqw.eq(StringUtils.isNotBlank(bo.getPayloadJson()), EmergencySessionEventLog::getPayloadJson, bo.getPayloadJson());
        lqw.eq(StringUtils.isNotBlank(bo.getDecisionJson()), EmergencySessionEventLog::getDecisionJson, bo.getDecisionJson());
        lqw.eq(bo.getCreatedAt() != null, EmergencySessionEventLog::getCreatedAt, bo.getCreatedAt());
        return lqw;
    }

    /**
     * 新增会话事件日志（追加式/可审计）
     *
     * @param bo 会话事件日志（追加式/可审计）
     * @return 是否新增成功
     */
    @Override
    public Boolean insertByBo(EmergencySessionEventLogBo bo) {
        EmergencySessionEventLog add = MapstructUtils.convert(bo, EmergencySessionEventLog.class);
        validEntityBeforeSave(add);
        boolean flag = baseMapper.insert(add) > 0;
        if (flag) {
            bo.setId(add.getId());
        }
        return flag;
    }

    /**
     * 修改会话事件日志（追加式/可审计）
     *
     * @param bo 会话事件日志（追加式/可审计）
     * @return 是否修改成功
     */
    @Override
    public Boolean updateByBo(EmergencySessionEventLogBo bo) {
        EmergencySessionEventLog update = MapstructUtils.convert(bo, EmergencySessionEventLog.class);
        validEntityBeforeSave(update);
        return baseMapper.updateById(update) > 0;
    }

    /**
     * 保存前的数据校验
     */
    private void validEntityBeforeSave(EmergencySessionEventLog entity){
        //TODO 做一些数据校验,如唯一约束
    }

    /**
     * 校验并批量删除会话事件日志（追加式/可审计）信息
     *
     * @param ids     待删除的主键集合
     * @param isValid 是否进行有效性校验
     * @return 是否删除成功
     */
    @Override
    public Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        if(isValid){
            //TODO 做一些业务上的校验,判断是否需要校验
        }
        return baseMapper.deleteByIds(ids) > 0;
    }
}
