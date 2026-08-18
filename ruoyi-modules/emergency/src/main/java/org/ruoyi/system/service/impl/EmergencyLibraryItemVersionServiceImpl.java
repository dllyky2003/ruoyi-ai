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
import org.ruoyi.system.domain.bo.EmergencyLibraryItemVersionBo;
import org.ruoyi.system.domain.vo.EmergencyLibraryItemVersionVo;
import org.ruoyi.system.domain.EmergencyLibraryItemVersion;
import org.ruoyi.system.mapper.EmergencyLibraryItemVersionMapper;
import org.ruoyi.system.service.IEmergencyLibraryItemVersionService;

import java.util.List;
import java.util.Map;
import java.util.Collection;

/**
 * 公共库冻结定义；published 后禁止 UPDATEService业务层处理
 *
 * @author ageerle
 * @date 2026-08-18
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class EmergencyLibraryItemVersionServiceImpl implements IEmergencyLibraryItemVersionService {

    private final EmergencyLibraryItemVersionMapper baseMapper;

    /**
     * 查询公共库冻结定义；published 后禁止 UPDATE
     *
     * @param id 主键
     * @return 公共库冻结定义；published 后禁止 UPDATE
     */
    @Override
    public EmergencyLibraryItemVersionVo queryById(Long id){
        return baseMapper.selectVoById(id);
    }

    /**
     * 分页查询公共库冻结定义；published 后禁止 UPDATE列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 公共库冻结定义；published 后禁止 UPDATE分页列表
     */
    @Override
    public TableDataInfo<EmergencyLibraryItemVersionVo> queryPageList(EmergencyLibraryItemVersionBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<EmergencyLibraryItemVersion> lqw = buildQueryWrapper(bo);
        Page<EmergencyLibraryItemVersionVo> result = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(result);
    }

    /**
     * 查询符合条件的公共库冻结定义；published 后禁止 UPDATE列表
     *
     * @param bo 查询条件
     * @return 公共库冻结定义；published 后禁止 UPDATE列表
     */
    @Override
    public List<EmergencyLibraryItemVersionVo> queryList(EmergencyLibraryItemVersionBo bo) {
        LambdaQueryWrapper<EmergencyLibraryItemVersion> lqw = buildQueryWrapper(bo);
        return baseMapper.selectVoList(lqw);
    }

    private LambdaQueryWrapper<EmergencyLibraryItemVersion> buildQueryWrapper(EmergencyLibraryItemVersionBo bo) {
        Map<String, Object> params = bo.getParams();
        LambdaQueryWrapper<EmergencyLibraryItemVersion> lqw = Wrappers.lambdaQuery();
        lqw.orderByAsc(EmergencyLibraryItemVersion::getId);
         lqw.eq(bo.getLibraryItemId() != null, EmergencyLibraryItemVersion::getLibraryItemId, bo.getLibraryItemId());
        lqw.eq(bo.getVersionNo() != null, EmergencyLibraryItemVersion::getVersionNo, bo.getVersionNo());
        lqw.eq(StringUtils.isNotBlank(bo.getSchemaVersion()), EmergencyLibraryItemVersion::getSchemaVersion, bo.getSchemaVersion());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), EmergencyLibraryItemVersion::getStatus, bo.getStatus());

        lqw.eq(StringUtils.isNotBlank(bo.getDefinitionHash()), EmergencyLibraryItemVersion::getDefinitionHash, bo.getDefinitionHash());
        lqw.eq(StringUtils.isNotBlank(bo.getSourceGuideline()), EmergencyLibraryItemVersion::getSourceGuideline, bo.getSourceGuideline());
        lqw.eq(StringUtils.isNotBlank(bo.getSourceVersion()), EmergencyLibraryItemVersion::getSourceVersion, bo.getSourceVersion());
        lqw.eq(bo.getEffectiveFrom() != null, EmergencyLibraryItemVersion::getEffectiveFrom, bo.getEffectiveFrom());
        lqw.eq(bo.getEffectiveTo() != null, EmergencyLibraryItemVersion::getEffectiveTo, bo.getEffectiveTo());
        lqw.eq(StringUtils.isNotBlank(bo.getChangeReason()), EmergencyLibraryItemVersion::getChangeReason, bo.getChangeReason());
        lqw.eq(bo.getReviewedBy() != null, EmergencyLibraryItemVersion::getReviewedBy, bo.getReviewedBy());
        lqw.eq(bo.getReviewedAt() != null, EmergencyLibraryItemVersion::getReviewedAt, bo.getReviewedAt());
        return lqw;
    }

    /**
     * 新增公共库冻结定义；published 后禁止 UPDATE
     *
     * @param bo 公共库冻结定义；published 后禁止 UPDATE
     * @return 是否新增成功
     */
    @Override
    public Boolean insertByBo(EmergencyLibraryItemVersionBo bo) {
        EmergencyLibraryItemVersion add = MapstructUtils.convert(bo, EmergencyLibraryItemVersion.class);
        validEntityBeforeSave(add);
        boolean flag = baseMapper.insert(add) > 0;
        if (flag) {
            bo.setId(add.getId());
        }
        return flag;
    }

    /**
     * 修改公共库冻结定义；published 后禁止 UPDATE
     *
     * @param bo 公共库冻结定义；published 后禁止 UPDATE
     * @return 是否修改成功
     */
    @Override
    public Boolean updateByBo(EmergencyLibraryItemVersionBo bo) {
        EmergencyLibraryItemVersion update = MapstructUtils.convert(bo, EmergencyLibraryItemVersion.class);
        validEntityBeforeSave(update);
        return baseMapper.updateById(update) > 0;
    }

    /**
     * 保存前的数据校验
     */
    private void validEntityBeforeSave(EmergencyLibraryItemVersion entity){
        //TODO 做一些数据校验,如唯一约束
    }

    /**
     * 校验并批量删除公共库冻结定义；published 后禁止 UPDATE信息
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
