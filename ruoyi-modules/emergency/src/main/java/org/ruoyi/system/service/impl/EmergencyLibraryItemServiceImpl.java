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
import org.springframework.transaction.annotation.Transactional;
import org.ruoyi.system.domain.bo.EmergencyLibraryItemBo;
import org.ruoyi.system.domain.vo.EmergencyLibraryItemVo;
import org.ruoyi.system.domain.vo.EmergencyLibraryItemVersionVo;
import org.ruoyi.system.domain.EmergencyLibraryItem;
import org.ruoyi.system.domain.EmergencyLibraryItemVersion;
import org.ruoyi.system.mapper.EmergencyLibraryItemMapper;
import org.ruoyi.system.mapper.EmergencyLibraryItemVersionMapper;
import org.ruoyi.system.service.IEmergencyLibraryItemService;

import java.util.List;
import java.util.Map;
import java.util.Collection;

/**
 * 公共库对象身份Service业务层处理
 *
 * @author ageerle
 * @date 2026-08-18
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class EmergencyLibraryItemServiceImpl implements IEmergencyLibraryItemService {

    private final EmergencyLibraryItemMapper baseMapper;

    /**
     * 注入子表 Mapper（用于查询版本列表）
     */
    private final EmergencyLibraryItemVersionMapper versionMapper;

    /**
     * 查询公共库对象身份
     *
     * @param id 主键
     * @return 公共库对象身份
     */
    @Override
    public EmergencyLibraryItemVo queryById(Long id){
        return baseMapper.selectVoById(id);
    }

    /**
     * 分页查询公共库对象身份列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 公共库对象身份分页列表
     */
    @Override
    public TableDataInfo<EmergencyLibraryItemVo> queryPageList(EmergencyLibraryItemBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<EmergencyLibraryItem> lqw = buildQueryWrapper(bo);
        Page<EmergencyLibraryItemVo> result = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(result);
    }

    /**
     * 查询符合条件的公共库对象身份列表
     *
     * @param bo 查询条件
     * @return 公共库对象身份列表
     */
    @Override
    public List<EmergencyLibraryItemVo> queryList(EmergencyLibraryItemBo bo) {
        LambdaQueryWrapper<EmergencyLibraryItem> lqw = buildQueryWrapper(bo);
        return baseMapper.selectVoList(lqw);
    }

    private LambdaQueryWrapper<EmergencyLibraryItem> buildQueryWrapper(EmergencyLibraryItemBo bo) {
        Map<String, Object> params = bo.getParams();
        LambdaQueryWrapper<EmergencyLibraryItem> lqw = Wrappers.lambdaQuery();
        lqw.orderByAsc(EmergencyLibraryItem::getId);
        lqw.eq(bo.getOrganizationId() != null, EmergencyLibraryItem::getOrganizationId, bo.getOrganizationId());
        lqw.eq(StringUtils.isNotBlank(bo.getLibraryType()), EmergencyLibraryItem::getLibraryType, bo.getLibraryType());
        lqw.eq(StringUtils.isNotBlank(bo.getItemCode()), EmergencyLibraryItem::getItemCode, bo.getItemCode());
        lqw.like(StringUtils.isNotBlank(bo.getName()), EmergencyLibraryItem::getName, bo.getName());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), EmergencyLibraryItem::getStatus, bo.getStatus());
        lqw.eq(bo.getCurrentPublishedVersionId() != null, EmergencyLibraryItem::getCurrentPublishedVersionId, bo.getCurrentPublishedVersionId());
        return lqw;
    }

    /**
     * 新增公共库对象身份
     *
     * @param bo 公共库对象身份
     * @return 是否新增成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean insertByBo(EmergencyLibraryItemBo bo) {
        EmergencyLibraryItem add = MapstructUtils.convert(bo, EmergencyLibraryItem.class);
        validEntityBeforeSave(add);
        boolean flag = baseMapper.insert(add) > 0;
        if (flag) {
            bo.setId(add.getId());
        }
        return flag;
    }

    /**
     * 修改公共库对象身份
     *
     * @param bo 公共库对象身份
     * @return 是否修改成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateByBo(EmergencyLibraryItemBo bo) {
        EmergencyLibraryItem update = MapstructUtils.convert(bo, EmergencyLibraryItem.class);
        validEntityBeforeSave(update);
        return baseMapper.updateById(update) > 0;
    }

    /**
     * 保存前的数据校验
     */
    private void validEntityBeforeSave(EmergencyLibraryItem entity){
        //TODO 做一些数据校验,如唯一约束
    }

    /**
     * 校验并批量删除公共库对象身份信息
     *
     * @param ids     待删除的主键集合
     * @param isValid 是否进行有效性校验
     * @return 是否删除成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        if(isValid){
            //TODO 做一些业务上的校验,判断是否需要校验
        }
        return baseMapper.deleteByIds(ids) > 0;
    }

    // ==================== 父子表关联查询方法 ====================

    /**
     * 获取公共库条目及其所有版本（父子表联合查询）
     *
     * @param id 公共库条目ID
     * @return 包含版本列表的 Vo
     */
    @Override
    @Transactional(readOnly = true)
    public EmergencyLibraryItemVo getItemWithVersions(Long id) {
        // 1. 查询主表
        EmergencyLibraryItemVo itemVo = baseMapper.selectVoById(id);
        if (itemVo == null) {
            return null;
        }

        // 2. 查询子表（所有版本，按版本号降序）
        LambdaQueryWrapper<EmergencyLibraryItemVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EmergencyLibraryItemVersion::getLibraryItemId, id)
            .orderByDesc(EmergencyLibraryItemVersion::getVersionNo);
        List<EmergencyLibraryItemVersionVo> versions = versionMapper.selectVoList(wrapper);

        // 3. 设置到 Vo 中
        itemVo.setVersions(versions);
        return itemVo;
    }

    /**
     * 获取公共库条目下的所有版本列表
     *
     * @param itemId 公共库条目ID
     * @return 版本列表
     */
    @Override
    @Transactional(readOnly = true)
    public List<EmergencyLibraryItemVersionVo> getVersionsByItemId(Long itemId) {
        LambdaQueryWrapper<EmergencyLibraryItemVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EmergencyLibraryItemVersion::getLibraryItemId, itemId)
            .orderByDesc(EmergencyLibraryItemVersion::getVersionNo);
        return versionMapper.selectVoList(wrapper);
    }
}
