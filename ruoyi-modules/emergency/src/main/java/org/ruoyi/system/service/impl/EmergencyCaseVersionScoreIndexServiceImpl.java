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
import org.ruoyi.system.domain.bo.EmergencyCaseVersionScoreIndexBo;
import org.ruoyi.system.domain.vo.EmergencyCaseVersionScoreIndexVo;
import org.ruoyi.system.domain.EmergencyCaseVersionScoreIndex;
import org.ruoyi.system.mapper.EmergencyCaseVersionScoreIndexMapper;
import org.ruoyi.system.service.IEmergencyCaseVersionScoreIndexService;

import java.util.List;
import java.util.Map;
import java.util.Collection;

/**
 * 病例版本评分项索引Service业务层处理
 *
 * @author ageerle
 * @date 2026-08-18
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class EmergencyCaseVersionScoreIndexServiceImpl implements IEmergencyCaseVersionScoreIndexService {

    private final EmergencyCaseVersionScoreIndexMapper baseMapper;

    /**
     * 查询病例版本评分项索引
     *
     * @param caseVersionId 主键
     * @return 病例版本评分项索引
     */
    @Override
    public EmergencyCaseVersionScoreIndexVo queryById(Long caseVersionId){
        return baseMapper.selectVoById(caseVersionId);
    }

    /**
     * 分页查询病例版本评分项索引列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 病例版本评分项索引分页列表
     */
    @Override
    public TableDataInfo<EmergencyCaseVersionScoreIndexVo> queryPageList(EmergencyCaseVersionScoreIndexBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<EmergencyCaseVersionScoreIndex> lqw = buildQueryWrapper(bo);
        Page<EmergencyCaseVersionScoreIndexVo> result = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(result);
    }

    /**
     * 查询符合条件的病例版本评分项索引列表
     *
     * @param bo 查询条件
     * @return 病例版本评分项索引列表
     */
    @Override
    public List<EmergencyCaseVersionScoreIndexVo> queryList(EmergencyCaseVersionScoreIndexBo bo) {
        LambdaQueryWrapper<EmergencyCaseVersionScoreIndex> lqw = buildQueryWrapper(bo);
        return baseMapper.selectVoList(lqw);
    }

    private LambdaQueryWrapper<EmergencyCaseVersionScoreIndex> buildQueryWrapper(EmergencyCaseVersionScoreIndexBo bo) {
        Map<String, Object> params = bo.getParams();
        LambdaQueryWrapper<EmergencyCaseVersionScoreIndex> lqw = Wrappers.lambdaQuery();
        lqw.eq(bo.getDeptId() != null, EmergencyCaseVersionScoreIndex::getDeptId, bo.getDeptId());
        lqw.eq(bo.getUserId() != null, EmergencyCaseVersionScoreIndex::getUserId, bo.getUserId());
        lqw.eq(bo.getOrderNum() != null, EmergencyCaseVersionScoreIndex::getOrderNum, bo.getOrderNum());
        lqw.orderByAsc(EmergencyCaseVersionScoreIndex::getCaseVersionId);
        lqw.orderByAsc(EmergencyCaseVersionScoreIndex::getMetricConfigId);
        lqw.eq(bo.getLibraryItemId() != null, EmergencyCaseVersionScoreIndex::getLibraryItemId, bo.getLibraryItemId());
        lqw.eq(bo.getLibraryVersionId() != null, EmergencyCaseVersionScoreIndex::getLibraryVersionId, bo.getLibraryVersionId());
        lqw.eq(bo.getWeight() != null, EmergencyCaseVersionScoreIndex::getWeight, bo.getWeight());
        lqw.eq(bo.getEnabled() != null, EmergencyCaseVersionScoreIndex::getEnabled, bo.getEnabled());
        return lqw;
    }

    /**
     * 新增病例版本评分项索引
     *
     * @param bo 病例版本评分项索引
     * @return 是否新增成功
     */
    @Override
    public Boolean insertByBo(EmergencyCaseVersionScoreIndexBo bo) {
        EmergencyCaseVersionScoreIndex add = MapstructUtils.convert(bo, EmergencyCaseVersionScoreIndex.class);
        validEntityBeforeSave(add);
        boolean flag = baseMapper.insert(add) > 0;
        if (flag) {
            bo.setCaseVersionId(add.getCaseVersionId());
        }
        return flag;
    }

    /**
     * 修改病例版本评分项索引
     *
     * @param bo 病例版本评分项索引
     * @return 是否修改成功
     */
    @Override
    public Boolean updateByBo(EmergencyCaseVersionScoreIndexBo bo) {
        EmergencyCaseVersionScoreIndex update = MapstructUtils.convert(bo, EmergencyCaseVersionScoreIndex.class);
        validEntityBeforeSave(update);
        return baseMapper.updateById(update) > 0;
    }

    /**
     * 保存前的数据校验
     */
    private void validEntityBeforeSave(EmergencyCaseVersionScoreIndex entity){
        //TODO 做一些数据校验,如唯一约束
    }

    /**
     * 校验并批量删除病例版本评分项索引信息
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
