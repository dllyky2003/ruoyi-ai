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
import org.ruoyi.system.domain.bo.EmergencyCaseVersionResourceRefBo;
import org.ruoyi.system.domain.vo.EmergencyCaseVersionResourceRefVo;
import org.ruoyi.system.domain.EmergencyCaseVersionResourceRef;
import org.ruoyi.system.mapper.EmergencyCaseVersionResourceRefMapper;
import org.ruoyi.system.service.IEmergencyCaseVersionResourceRefService;

import java.util.List;
import java.util.Map;
import java.util.Collection;

/**
 * 病例版本引用的资源配置与公共库版本映射Service业务层处理
 *
 * @author ageerle
 * @date 2026-08-18
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class EmergencyCaseVersionResourceRefServiceImpl implements IEmergencyCaseVersionResourceRefService {

    private final EmergencyCaseVersionResourceRefMapper baseMapper;

    /**
     * 查询病例版本引用的资源配置与公共库版本映射
     *
     * @param caseVersionId 主键
     * @return 病例版本引用的资源配置与公共库版本映射
     */
    @Override
    public EmergencyCaseVersionResourceRefVo queryById(Long caseVersionId){
        return baseMapper.selectVoById(caseVersionId);
    }

    /**
     * 分页查询病例版本引用的资源配置与公共库版本映射列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 病例版本引用的资源配置与公共库版本映射分页列表
     */
    @Override
    public TableDataInfo<EmergencyCaseVersionResourceRefVo> queryPageList(EmergencyCaseVersionResourceRefBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<EmergencyCaseVersionResourceRef> lqw = buildQueryWrapper(bo);
        Page<EmergencyCaseVersionResourceRefVo> result = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(result);
    }

    /**
     * 查询符合条件的病例版本引用的资源配置与公共库版本映射列表
     *
     * @param bo 查询条件
     * @return 病例版本引用的资源配置与公共库版本映射列表
     */
    @Override
    public List<EmergencyCaseVersionResourceRefVo> queryList(EmergencyCaseVersionResourceRefBo bo) {
        LambdaQueryWrapper<EmergencyCaseVersionResourceRef> lqw = buildQueryWrapper(bo);
        return baseMapper.selectVoList(lqw);
    }

    private LambdaQueryWrapper<EmergencyCaseVersionResourceRef> buildQueryWrapper(EmergencyCaseVersionResourceRefBo bo) {
        Map<String, Object> params = bo.getParams();
        LambdaQueryWrapper<EmergencyCaseVersionResourceRef> lqw = Wrappers.lambdaQuery();
        lqw.eq(bo.getDeptId() != null, EmergencyCaseVersionResourceRef::getDeptId, bo.getDeptId());
        lqw.eq(bo.getUserId() != null, EmergencyCaseVersionResourceRef::getUserId, bo.getUserId());
        lqw.eq(bo.getOrderNum() != null, EmergencyCaseVersionResourceRef::getOrderNum, bo.getOrderNum());
        lqw.orderByAsc(EmergencyCaseVersionResourceRef::getCaseVersionId);
        lqw.orderByAsc(EmergencyCaseVersionResourceRef::getResourceConfigId);
        lqw.eq(bo.getLibraryItemId() != null, EmergencyCaseVersionResourceRef::getLibraryItemId, bo.getLibraryItemId());
        lqw.eq(bo.getLibraryVersionId() != null, EmergencyCaseVersionResourceRef::getLibraryVersionId, bo.getLibraryVersionId());
        lqw.eq(StringUtils.isNotBlank(bo.getResourceType()), EmergencyCaseVersionResourceRef::getResourceType, bo.getResourceType());
        lqw.eq(bo.getEnabled() != null, EmergencyCaseVersionResourceRef::getEnabled, bo.getEnabled());
        return lqw;
    }

    /**
     * 新增病例版本引用的资源配置与公共库版本映射
     *
     * @param bo 病例版本引用的资源配置与公共库版本映射
     * @return 是否新增成功
     */
    @Override
    public Boolean insertByBo(EmergencyCaseVersionResourceRefBo bo) {
        EmergencyCaseVersionResourceRef add = MapstructUtils.convert(bo, EmergencyCaseVersionResourceRef.class);
        validEntityBeforeSave(add);
        boolean flag = baseMapper.insert(add) > 0;
        if (flag) {
            bo.setCaseVersionId(add.getCaseVersionId());
        }
        return flag;
    }

    /**
     * 修改病例版本引用的资源配置与公共库版本映射
     *
     * @param bo 病例版本引用的资源配置与公共库版本映射
     * @return 是否修改成功
     */
    @Override
    public Boolean updateByBo(EmergencyCaseVersionResourceRefBo bo) {
        EmergencyCaseVersionResourceRef update = MapstructUtils.convert(bo, EmergencyCaseVersionResourceRef.class);
        validEntityBeforeSave(update);
        return baseMapper.updateById(update) > 0;
    }

    /**
     * 保存前的数据校验
     */
    private void validEntityBeforeSave(EmergencyCaseVersionResourceRef entity){
        //TODO 做一些数据校验,如唯一约束
    }

    /**
     * 校验并批量删除病例版本引用的资源配置与公共库版本映射信息
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
