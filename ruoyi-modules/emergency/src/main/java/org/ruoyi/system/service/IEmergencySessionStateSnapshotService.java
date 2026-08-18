package org.ruoyi.system.service;

import org.ruoyi.system.domain.vo.EmergencySessionStateSnapshotVo;
import org.ruoyi.system.domain.bo.EmergencySessionStateSnapshotBo;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.ruoyi.common.mybatis.core.page.PageQuery;

import java.util.Collection;
import java.util.List;

/**
 * 会话状态快照（追加式）Service接口
 *
 * @author ageerle
 * @date 2026-08-18
 */
public interface IEmergencySessionStateSnapshotService {

    /**
     * 查询会话状态快照（追加式）
     *
     * @param id 主键
     * @return 会话状态快照（追加式）
     */
    EmergencySessionStateSnapshotVo queryById(Long id);

    /**
     * 分页查询会话状态快照（追加式）列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 会话状态快照（追加式）分页列表
     */
    TableDataInfo<EmergencySessionStateSnapshotVo> queryPageList(EmergencySessionStateSnapshotBo bo, PageQuery pageQuery);

    /**
     * 查询符合条件的会话状态快照（追加式）列表
     *
     * @param bo 查询条件
     * @return 会话状态快照（追加式）列表
     */
    List<EmergencySessionStateSnapshotVo> queryList(EmergencySessionStateSnapshotBo bo);

    /**
     * 新增会话状态快照（追加式）
     *
     * @param bo 会话状态快照（追加式）
     * @return 是否新增成功
     */
    Boolean insertByBo(EmergencySessionStateSnapshotBo bo);

    /**
     * 修改会话状态快照（追加式）
     *
     * @param bo 会话状态快照（追加式）
     * @return 是否修改成功
     */
    Boolean updateByBo(EmergencySessionStateSnapshotBo bo);

    /**
     * 校验并批量删除会话状态快照（追加式）信息
     *
     * @param ids     待删除的主键集合
     * @param isValid 是否进行有效性校验
     * @return 是否删除成功
     */
    Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);
}
