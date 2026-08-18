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
import org.ruoyi.system.domain.bo.EmergencySessionStateSnapshotBo;
import org.ruoyi.system.domain.vo.EmergencySessionStateSnapshotVo;
import org.ruoyi.system.domain.EmergencySessionStateSnapshot;
import org.ruoyi.system.mapper.EmergencySessionStateSnapshotMapper;
import org.ruoyi.system.service.IEmergencySessionStateSnapshotService;

import java.util.List;
import java.util.Map;
import java.util.Collection;

/**
 * 会话状态快照（追加式）Service业务层处理
 *
 * @author ageerle
 * @date 2026-08-18
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class EmergencySessionStateSnapshotServiceImpl implements IEmergencySessionStateSnapshotService {

    private final EmergencySessionStateSnapshotMapper baseMapper;

    /**
     * 查询会话状态快照（追加式）
     *
     * @param id 主键
     * @return 会话状态快照（追加式）
     */
    @Override
    public EmergencySessionStateSnapshotVo queryById(Long id){
        return baseMapper.selectVoById(id);
    }

    /**
     * 分页查询会话状态快照（追加式）列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 会话状态快照（追加式）分页列表
     */
    @Override
    public TableDataInfo<EmergencySessionStateSnapshotVo> queryPageList(EmergencySessionStateSnapshotBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<EmergencySessionStateSnapshot> lqw = buildQueryWrapper(bo);
        Page<EmergencySessionStateSnapshotVo> result = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(result);
    }

    /**
     * 查询符合条件的会话状态快照（追加式）列表
     *
     * @param bo 查询条件
     * @return 会话状态快照（追加式）列表
     */
    @Override
    public List<EmergencySessionStateSnapshotVo> queryList(EmergencySessionStateSnapshotBo bo) {
        LambdaQueryWrapper<EmergencySessionStateSnapshot> lqw = buildQueryWrapper(bo);
        return baseMapper.selectVoList(lqw);
    }

    private LambdaQueryWrapper<EmergencySessionStateSnapshot> buildQueryWrapper(EmergencySessionStateSnapshotBo bo) {
        Map<String, Object> params = bo.getParams();
        LambdaQueryWrapper<EmergencySessionStateSnapshot> lqw = Wrappers.lambdaQuery();
        lqw.orderByAsc(EmergencySessionStateSnapshot::getId);
        lqw.eq(bo.getDeptId() != null, EmergencySessionStateSnapshot::getDeptId, bo.getDeptId());
        lqw.eq(bo.getUserId() != null, EmergencySessionStateSnapshot::getUserId, bo.getUserId());
        lqw.eq(bo.getOrderNum() != null, EmergencySessionStateSnapshot::getOrderNum, bo.getOrderNum());
        lqw.eq(bo.getSessionId() != null, EmergencySessionStateSnapshot::getSessionId, bo.getSessionId());
        lqw.eq(bo.getSequenceNo() != null, EmergencySessionStateSnapshot::getSequenceNo, bo.getSequenceNo());
        lqw.eq(bo.getOccurredAt() != null, EmergencySessionStateSnapshot::getOccurredAt, bo.getOccurredAt());
        lqw.eq(StringUtils.isNotBlank(bo.getStateJson()), EmergencySessionStateSnapshot::getStateJson, bo.getStateJson());
        lqw.eq(StringUtils.isNotBlank(bo.getStateHash()), EmergencySessionStateSnapshot::getStateHash, bo.getStateHash());
        return lqw;
    }

    /**
     * 新增会话状态快照（追加式）
     *
     * @param bo 会话状态快照（追加式）
     * @return 是否新增成功
     */
    @Override
    public Boolean insertByBo(EmergencySessionStateSnapshotBo bo) {
        EmergencySessionStateSnapshot add = MapstructUtils.convert(bo, EmergencySessionStateSnapshot.class);
        validEntityBeforeSave(add);
        boolean flag = baseMapper.insert(add) > 0;
        if (flag) {
            bo.setId(add.getId());
        }
        return flag;
    }

    /**
     * 修改会话状态快照（追加式）
     *
     * @param bo 会话状态快照（追加式）
     * @return 是否修改成功
     */
    @Override
    public Boolean updateByBo(EmergencySessionStateSnapshotBo bo) {
        EmergencySessionStateSnapshot update = MapstructUtils.convert(bo, EmergencySessionStateSnapshot.class);
        validEntityBeforeSave(update);
        return baseMapper.updateById(update) > 0;
    }

    /**
     * 保存前的数据校验
     */
    private void validEntityBeforeSave(EmergencySessionStateSnapshot entity){
        //TODO 做一些数据校验,如唯一约束
    }

    /**
     * 校验并批量删除会话状态快照（追加式）信息
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
