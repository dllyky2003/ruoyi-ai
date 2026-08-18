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
import org.ruoyi.system.domain.bo.EmergencySessionScoreRecordBo;
import org.ruoyi.system.domain.vo.EmergencySessionScoreRecordVo;
import org.ruoyi.system.domain.EmergencySessionScoreRecord;
import org.ruoyi.system.mapper.EmergencySessionScoreRecordMapper;
import org.ruoyi.system.service.IEmergencySessionScoreRecordService;

import java.util.List;
import java.util.Map;
import java.util.Collection;

/**
 * 会话评分记录（追加式）Service业务层处理
 *
 * @author ageerle
 * @date 2026-08-18
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class EmergencySessionScoreRecordServiceImpl implements IEmergencySessionScoreRecordService {

    private final EmergencySessionScoreRecordMapper baseMapper;

    /**
     * 查询会话评分记录（追加式）
     *
     * @param id 主键
     * @return 会话评分记录（追加式）
     */
    @Override
    public EmergencySessionScoreRecordVo queryById(Long id){
        return baseMapper.selectVoById(id);
    }

    /**
     * 分页查询会话评分记录（追加式）列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 会话评分记录（追加式）分页列表
     */
    @Override
    public TableDataInfo<EmergencySessionScoreRecordVo> queryPageList(EmergencySessionScoreRecordBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<EmergencySessionScoreRecord> lqw = buildQueryWrapper(bo);
        Page<EmergencySessionScoreRecordVo> result = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(result);
    }

    /**
     * 查询符合条件的会话评分记录（追加式）列表
     *
     * @param bo 查询条件
     * @return 会话评分记录（追加式）列表
     */
    @Override
    public List<EmergencySessionScoreRecordVo> queryList(EmergencySessionScoreRecordBo bo) {
        LambdaQueryWrapper<EmergencySessionScoreRecord> lqw = buildQueryWrapper(bo);
        return baseMapper.selectVoList(lqw);
    }

    private LambdaQueryWrapper<EmergencySessionScoreRecord> buildQueryWrapper(EmergencySessionScoreRecordBo bo) {
        Map<String, Object> params = bo.getParams();
        LambdaQueryWrapper<EmergencySessionScoreRecord> lqw = Wrappers.lambdaQuery();
        lqw.orderByAsc(EmergencySessionScoreRecord::getId);
        lqw.eq(bo.getDeptId() != null, EmergencySessionScoreRecord::getDeptId, bo.getDeptId());
        lqw.eq(bo.getUserId() != null, EmergencySessionScoreRecord::getUserId, bo.getUserId());
        lqw.eq(bo.getOrderNum() != null, EmergencySessionScoreRecord::getOrderNum, bo.getOrderNum());
        lqw.eq(bo.getSessionId() != null, EmergencySessionScoreRecord::getSessionId, bo.getSessionId());
        lqw.eq(StringUtils.isNotBlank(bo.getMetricCode()), EmergencySessionScoreRecord::getMetricCode, bo.getMetricCode());
        lqw.eq(bo.getScore() != null, EmergencySessionScoreRecord::getScore, bo.getScore());
        lqw.eq(bo.getMaxScore() != null, EmergencySessionScoreRecord::getMaxScore, bo.getMaxScore());
        lqw.eq(StringUtils.isNotBlank(bo.getResult()), EmergencySessionScoreRecord::getResult, bo.getResult());
        lqw.eq(StringUtils.isNotBlank(bo.getEvidenceJson()), EmergencySessionScoreRecord::getEvidenceJson, bo.getEvidenceJson());
        lqw.eq(StringUtils.isNotBlank(bo.getExplanation()), EmergencySessionScoreRecord::getExplanation, bo.getExplanation());
        lqw.eq(bo.getDeterminedAt() != null, EmergencySessionScoreRecord::getDeterminedAt, bo.getDeterminedAt());
        return lqw;
    }

    /**
     * 新增会话评分记录（追加式）
     *
     * @param bo 会话评分记录（追加式）
     * @return 是否新增成功
     */
    @Override
    public Boolean insertByBo(EmergencySessionScoreRecordBo bo) {
        EmergencySessionScoreRecord add = MapstructUtils.convert(bo, EmergencySessionScoreRecord.class);
        validEntityBeforeSave(add);
        boolean flag = baseMapper.insert(add) > 0;
        if (flag) {
            bo.setId(add.getId());
        }
        return flag;
    }

    /**
     * 修改会话评分记录（追加式）
     *
     * @param bo 会话评分记录（追加式）
     * @return 是否修改成功
     */
    @Override
    public Boolean updateByBo(EmergencySessionScoreRecordBo bo) {
        EmergencySessionScoreRecord update = MapstructUtils.convert(bo, EmergencySessionScoreRecord.class);
        validEntityBeforeSave(update);
        return baseMapper.updateById(update) > 0;
    }

    /**
     * 保存前的数据校验
     */
    private void validEntityBeforeSave(EmergencySessionScoreRecord entity){
        //TODO 做一些数据校验,如唯一约束
    }

    /**
     * 校验并批量删除会话评分记录（追加式）信息
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
