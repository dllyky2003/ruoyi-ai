package org.ruoyi.system.service;

import org.ruoyi.system.domain.vo.EmergencySessionEventLogVo;
import org.ruoyi.system.domain.vo.EmergencySessionScoreRecordVo;
import org.ruoyi.system.domain.vo.EmergencySessionStateSnapshotVo;
import org.ruoyi.system.domain.vo.EmergencyTrainingSessionVo;
import org.ruoyi.system.domain.bo.EmergencyTrainingSessionBo;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.ruoyi.common.mybatis.core.page.PageQuery;

import java.util.Collection;
import java.util.List;

/**
 * 训练会话Service接口
 *
 * @author ageerle
 * @date 2026-08-18
 */
public interface IEmergencyTrainingSessionService {

    /**
     * 查询训练会话
     *
     * @param id 主键
     * @return 训练会话
     */
    EmergencyTrainingSessionVo queryById(Long id);

    /**
     * 分页查询训练会话列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 训练会话分页列表
     */
    TableDataInfo<EmergencyTrainingSessionVo> queryPageList(EmergencyTrainingSessionBo bo, PageQuery pageQuery);

    /**
     * 查询符合条件的训练会话列表
     *
     * @param bo 查询条件
     * @return 训练会话列表
     */
    List<EmergencyTrainingSessionVo> queryList(EmergencyTrainingSessionBo bo);

    /**
     * 新增训练会话
     *
     * @param bo 训练会话
     * @return 是否新增成功
     */
    Boolean insertByBo(EmergencyTrainingSessionBo bo);

    /**
     * 修改训练会话
     *
     * @param bo 训练会话
     * @return 是否修改成功
     */
    Boolean updateByBo(EmergencyTrainingSessionBo bo);

    /**
     * 校验并批量删除训练会话信息
     *
     * @param ids     待删除的主键集合
     * @param isValid 是否进行有效性校验
     * @return 是否删除成功
     */
    Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);


    /**
     * 获取训练会话及其所有关联数据（事件日志、评分记录、状态快照）
     */
    EmergencyTrainingSessionVo getSessionWithAllData(Long id);

    /**
     * 获取会话的事件日志列表（按序列号升序）
     */
    List<EmergencySessionEventLogVo> getEventLogsBySessionId(Long sessionId);

    /**
     * 获取会话的评分记录列表（按ID升序）
     */
    List<EmergencySessionScoreRecordVo> getScoreRecordsBySessionId(Long sessionId);

    /**
     * 获取会话的状态快照列表（按序列号升序）
     */
    List<EmergencySessionStateSnapshotVo> getStateSnapshotsBySessionId(Long sessionId);
}
