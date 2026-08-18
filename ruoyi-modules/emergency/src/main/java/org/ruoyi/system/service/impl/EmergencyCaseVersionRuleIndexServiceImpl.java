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
import org.ruoyi.system.domain.bo.EmergencyCaseVersionRuleIndexBo;
import org.ruoyi.system.domain.vo.EmergencyCaseVersionRuleIndexVo;
import org.ruoyi.system.domain.EmergencyCaseVersionRuleIndex;
import org.ruoyi.system.mapper.EmergencyCaseVersionRuleIndexMapper;
import org.ruoyi.system.service.IEmergencyCaseVersionRuleIndexService;

import java.util.List;
import java.util.Map;
import java.util.Collection;

/**
 * 病例版本的规则索引Service业务层处理
 *
 * @author ageerle
 * @date 2026-08-18
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class EmergencyCaseVersionRuleIndexServiceImpl implements IEmergencyCaseVersionRuleIndexService {

    private final EmergencyCaseVersionRuleIndexMapper baseMapper;

    /**
     * 查询病例版本的规则索引
     *
     * @param caseVersionId 主键
     * @return 病例版本的规则索引
     */
    @Override
    public EmergencyCaseVersionRuleIndexVo queryById(Long caseVersionId){
        return baseMapper.selectVoById(caseVersionId);
    }

    /**
     * 分页查询病例版本的规则索引列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 病例版本的规则索引分页列表
     */
    @Override
    public TableDataInfo<EmergencyCaseVersionRuleIndexVo> queryPageList(EmergencyCaseVersionRuleIndexBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<EmergencyCaseVersionRuleIndex> lqw = buildQueryWrapper(bo);
        Page<EmergencyCaseVersionRuleIndexVo> result = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(result);
    }

    /**
     * 查询符合条件的病例版本的规则索引列表
     *
     * @param bo 查询条件
     * @return 病例版本的规则索引列表
     */
    @Override
    public List<EmergencyCaseVersionRuleIndexVo> queryList(EmergencyCaseVersionRuleIndexBo bo) {
        LambdaQueryWrapper<EmergencyCaseVersionRuleIndex> lqw = buildQueryWrapper(bo);
        return baseMapper.selectVoList(lqw);
    }

    private LambdaQueryWrapper<EmergencyCaseVersionRuleIndex> buildQueryWrapper(EmergencyCaseVersionRuleIndexBo bo) {
        Map<String, Object> params = bo.getParams();
        LambdaQueryWrapper<EmergencyCaseVersionRuleIndex> lqw = Wrappers.lambdaQuery();
        lqw.eq(bo.getDeptId() != null, EmergencyCaseVersionRuleIndex::getDeptId, bo.getDeptId());
        lqw.eq(bo.getUserId() != null, EmergencyCaseVersionRuleIndex::getUserId, bo.getUserId());
        lqw.eq(bo.getOrderNum() != null, EmergencyCaseVersionRuleIndex::getOrderNum, bo.getOrderNum());
        lqw.orderByAsc(EmergencyCaseVersionRuleIndex::getCaseVersionId);
        lqw.orderByAsc(EmergencyCaseVersionRuleIndex::getRuleId);
        lqw.eq(StringUtils.isNotBlank(bo.getStageId()), EmergencyCaseVersionRuleIndex::getStageId, bo.getStageId());
        lqw.like(StringUtils.isNotBlank(bo.getRuleName()), EmergencyCaseVersionRuleIndex::getRuleName, bo.getRuleName());
        lqw.eq(bo.getPriorityNo() != null, EmergencyCaseVersionRuleIndex::getPriorityNo, bo.getPriorityNo());
        lqw.eq(bo.getEnabled() != null, EmergencyCaseVersionRuleIndex::getEnabled, bo.getEnabled());
        return lqw;
    }

    /**
     * 新增病例版本的规则索引
     *
     * @param bo 病例版本的规则索引
     * @return 是否新增成功
     */
    @Override
    public Boolean insertByBo(EmergencyCaseVersionRuleIndexBo bo) {
        EmergencyCaseVersionRuleIndex add = MapstructUtils.convert(bo, EmergencyCaseVersionRuleIndex.class);
        validEntityBeforeSave(add);
        boolean flag = baseMapper.insert(add) > 0;
        if (flag) {
            bo.setCaseVersionId(add.getCaseVersionId());
        }
        return flag;
    }

    /**
     * 修改病例版本的规则索引
     *
     * @param bo 病例版本的规则索引
     * @return 是否修改成功
     */
    @Override
    public Boolean updateByBo(EmergencyCaseVersionRuleIndexBo bo) {
        EmergencyCaseVersionRuleIndex update = MapstructUtils.convert(bo, EmergencyCaseVersionRuleIndex.class);
        validEntityBeforeSave(update);
        return baseMapper.updateById(update) > 0;
    }

    /**
     * 保存前的数据校验
     */
    private void validEntityBeforeSave(EmergencyCaseVersionRuleIndex entity){
        //TODO 做一些数据校验,如唯一约束
    }

    /**
     * 校验并批量删除病例版本的规则索引信息
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
