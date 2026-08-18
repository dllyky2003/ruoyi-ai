package org.ruoyi.system.service;

import org.ruoyi.system.domain.vo.EmergencySessionEventLogVo;
import org.ruoyi.system.domain.bo.EmergencySessionEventLogBo;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.ruoyi.common.mybatis.core.page.PageQuery;

import java.util.Collection;
import java.util.List;

/**
 * 会话事件日志（追加式/可审计）Service接口
 *
 * @author ageerle
 * @date 2026-08-18
 */
public interface IEmergencySessionEventLogService {

    /**
     * 查询会话事件日志（追加式/可审计）
     *
     * @param id 主键
     * @return 会话事件日志（追加式/可审计）
     */
    EmergencySessionEventLogVo queryById(Long id);

    /**
     * 分页查询会话事件日志（追加式/可审计）列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 会话事件日志（追加式/可审计）分页列表
     */
    TableDataInfo<EmergencySessionEventLogVo> queryPageList(EmergencySessionEventLogBo bo, PageQuery pageQuery);

    /**
     * 查询符合条件的会话事件日志（追加式/可审计）列表
     *
     * @param bo 查询条件
     * @return 会话事件日志（追加式/可审计）列表
     */
    List<EmergencySessionEventLogVo> queryList(EmergencySessionEventLogBo bo);

    /**
     * 新增会话事件日志（追加式/可审计）
     *
     * @param bo 会话事件日志（追加式/可审计）
     * @return 是否新增成功
     */
    Boolean insertByBo(EmergencySessionEventLogBo bo);

    /**
     * 修改会话事件日志（追加式/可审计）
     *
     * @param bo 会话事件日志（追加式/可审计）
     * @return 是否修改成功
     */
    Boolean updateByBo(EmergencySessionEventLogBo bo);

    /**
     * 校验并批量删除会话事件日志（追加式/可审计）信息
     *
     * @param ids     待删除的主键集合
     * @param isValid 是否进行有效性校验
     * @return 是否删除成功
     */
    Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);
}
