package org.ruoyi.system.service;

import org.ruoyi.system.domain.vo.EmergencySessionScoreRecordVo;
import org.ruoyi.system.domain.bo.EmergencySessionScoreRecordBo;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.ruoyi.common.mybatis.core.page.PageQuery;

import java.util.Collection;
import java.util.List;

/**
 * 会话评分记录（追加式）Service接口
 *
 * @author ageerle
 * @date 2026-08-18
 */
public interface IEmergencySessionScoreRecordService {

    /**
     * 查询会话评分记录（追加式）
     *
     * @param id 主键
     * @return 会话评分记录（追加式）
     */
    EmergencySessionScoreRecordVo queryById(Long id);

    /**
     * 分页查询会话评分记录（追加式）列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 会话评分记录（追加式）分页列表
     */
    TableDataInfo<EmergencySessionScoreRecordVo> queryPageList(EmergencySessionScoreRecordBo bo, PageQuery pageQuery);

    /**
     * 查询符合条件的会话评分记录（追加式）列表
     *
     * @param bo 查询条件
     * @return 会话评分记录（追加式）列表
     */
    List<EmergencySessionScoreRecordVo> queryList(EmergencySessionScoreRecordBo bo);

    /**
     * 新增会话评分记录（追加式）
     *
     * @param bo 会话评分记录（追加式）
     * @return 是否新增成功
     */
    Boolean insertByBo(EmergencySessionScoreRecordBo bo);

    /**
     * 修改会话评分记录（追加式）
     *
     * @param bo 会话评分记录（追加式）
     * @return 是否修改成功
     */
    Boolean updateByBo(EmergencySessionScoreRecordBo bo);

    /**
     * 校验并批量删除会话评分记录（追加式）信息
     *
     * @param ids     待删除的主键集合
     * @param isValid 是否进行有效性校验
     * @return 是否删除成功
     */
    Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);
}
