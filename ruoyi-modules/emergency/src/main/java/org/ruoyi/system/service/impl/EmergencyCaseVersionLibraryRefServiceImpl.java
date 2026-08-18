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
import org.ruoyi.system.domain.bo.EmergencyCaseVersionLibraryRefBo;
import org.ruoyi.system.domain.vo.EmergencyCaseVersionLibraryRefVo;
import org.ruoyi.system.domain.EmergencyCaseVersionLibraryRef;
import org.ruoyi.system.mapper.EmergencyCaseVersionLibraryRefMapper;
import org.ruoyi.system.service.IEmergencyCaseVersionLibraryRefService;

import java.util.List;
import java.util.Map;
import java.util.Collection;

/**
 * 病例引用了哪些公共库版本；用于影响分析与发布校验Service业务层处理
 *
 * @author ageerle
 * @date 2026-08-18
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class EmergencyCaseVersionLibraryRefServiceImpl implements IEmergencyCaseVersionLibraryRefService {

    private final EmergencyCaseVersionLibraryRefMapper baseMapper;

    /**
     * 查询病例引用了哪些公共库版本；用于影响分析与发布校验
     *
     * @param caseVersionId 主键
     * @return 病例引用了哪些公共库版本；用于影响分析与发布校验
     */
    @Override
    public EmergencyCaseVersionLibraryRefVo queryById(Long caseVersionId){
        return baseMapper.selectVoById(caseVersionId);
    }

    /**
     * 分页查询病例引用了哪些公共库版本；用于影响分析与发布校验列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 病例引用了哪些公共库版本；用于影响分析与发布校验分页列表
     */
    @Override
    public TableDataInfo<EmergencyCaseVersionLibraryRefVo> queryPageList(EmergencyCaseVersionLibraryRefBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<EmergencyCaseVersionLibraryRef> lqw = buildQueryWrapper(bo);
        Page<EmergencyCaseVersionLibraryRefVo> result = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(result);
    }

    /**
     * 查询符合条件的病例引用了哪些公共库版本；用于影响分析与发布校验列表
     *
     * @param bo 查询条件
     * @return 病例引用了哪些公共库版本；用于影响分析与发布校验列表
     */
    @Override
    public List<EmergencyCaseVersionLibraryRefVo> queryList(EmergencyCaseVersionLibraryRefBo bo) {
        LambdaQueryWrapper<EmergencyCaseVersionLibraryRef> lqw = buildQueryWrapper(bo);
        return baseMapper.selectVoList(lqw);
    }

    private LambdaQueryWrapper<EmergencyCaseVersionLibraryRef> buildQueryWrapper(EmergencyCaseVersionLibraryRefBo bo) {
        Map<String, Object> params = bo.getParams();
        LambdaQueryWrapper<EmergencyCaseVersionLibraryRef> lqw = Wrappers.lambdaQuery();

        lqw.orderByAsc(EmergencyCaseVersionLibraryRef::getCaseVersionId);
        lqw.eq(bo.getLibraryItemId() != null, EmergencyCaseVersionLibraryRef::getLibraryItemId, bo.getLibraryItemId());
        lqw.orderByAsc(EmergencyCaseVersionLibraryRef::getLibraryVersionId);
        lqw.eq(StringUtils.isNotBlank(bo.getRefType()), EmergencyCaseVersionLibraryRef::getRefType, bo.getRefType());
        lqw.orderByAsc(EmergencyCaseVersionLibraryRef::getJsonPath);
        return lqw;
    }

    /**
     * 新增病例引用了哪些公共库版本；用于影响分析与发布校验
     *
     * @param bo 病例引用了哪些公共库版本；用于影响分析与发布校验
     * @return 是否新增成功
     */
    @Override
    public Boolean insertByBo(EmergencyCaseVersionLibraryRefBo bo) {
        EmergencyCaseVersionLibraryRef add = MapstructUtils.convert(bo, EmergencyCaseVersionLibraryRef.class);
        validEntityBeforeSave(add);
        boolean flag = baseMapper.insert(add) > 0;
        if (flag) {
            bo.setCaseVersionId(add.getCaseVersionId());
        }
        return flag;
    }

    /**
     * 修改病例引用了哪些公共库版本；用于影响分析与发布校验
     *
     * @param bo 病例引用了哪些公共库版本；用于影响分析与发布校验
     * @return 是否修改成功
     */
    @Override
    public Boolean updateByBo(EmergencyCaseVersionLibraryRefBo bo) {
        EmergencyCaseVersionLibraryRef update = MapstructUtils.convert(bo, EmergencyCaseVersionLibraryRef.class);
        validEntityBeforeSave(update);
        return baseMapper.updateById(update) > 0;
    }

    /**
     * 保存前的数据校验
     */
    private void validEntityBeforeSave(EmergencyCaseVersionLibraryRef entity){
        //TODO 做一些数据校验,如唯一约束
    }

    /**
     * 校验并批量删除病例引用了哪些公共库版本；用于影响分析与发布校验信息
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
