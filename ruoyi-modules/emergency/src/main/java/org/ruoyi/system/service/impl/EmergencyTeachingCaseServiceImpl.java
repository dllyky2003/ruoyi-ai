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
import org.ruoyi.system.domain.bo.EmergencyTeachingCaseBo;
import org.ruoyi.system.domain.vo.EmergencyTeachingCaseVo;
import org.ruoyi.system.domain.vo.EmergencyTeachingCaseVersionVo;
import org.ruoyi.system.domain.EmergencyTeachingCase;
import org.ruoyi.system.domain.EmergencyTeachingCaseVersion;
import org.ruoyi.system.mapper.EmergencyTeachingCaseMapper;
import org.ruoyi.system.mapper.EmergencyTeachingCaseVersionMapper;
import org.ruoyi.system.service.IEmergencyTeachingCaseService;

import java.util.List;
import java.util.Map;
import java.util.Collection;

/**
 * 病例身份；不保存具体教学定义Service业务层处理
 *
 * @author ageerle
 * @date 2026-08-18
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class EmergencyTeachingCaseServiceImpl implements IEmergencyTeachingCaseService {

    private final EmergencyTeachingCaseMapper baseMapper;

    /**
     * 注入子表 Mapper（用于查询版本列表）
     */
    private final EmergencyTeachingCaseVersionMapper versionMapper;

    /**
     * 查询病例身份；不保存具体教学定义
     *
     * @param id 主键
     * @return 病例身份；不保存具体教学定义
     */
    @Override
    @Transactional(readOnly = true)
    public EmergencyTeachingCaseVo queryById(Long id){
        return baseMapper.selectVoById(id);
    }

    /**
     * 分页查询病例身份；不保存具体教学定义列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 病例身份；不保存具体教学定义分页列表
     */
    @Override
    @Transactional(readOnly = true)
    public TableDataInfo<EmergencyTeachingCaseVo> queryPageList(EmergencyTeachingCaseBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<EmergencyTeachingCase> lqw = buildQueryWrapper(bo);
        Page<EmergencyTeachingCaseVo> result = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(result);
    }

    /**
     * 查询符合条件的病例身份；不保存具体教学定义列表
     *
     * @param bo 查询条件
     * @return 病例身份；不保存具体教学定义列表
     */
    @Override
    @Transactional(readOnly = true)
    public List<EmergencyTeachingCaseVo> queryList(EmergencyTeachingCaseBo bo) {
        LambdaQueryWrapper<EmergencyTeachingCase> lqw = buildQueryWrapper(bo);
        return baseMapper.selectVoList(lqw);
    }

    private LambdaQueryWrapper<EmergencyTeachingCase> buildQueryWrapper(EmergencyTeachingCaseBo bo) {
        Map<String, Object> params = bo.getParams();
        LambdaQueryWrapper<EmergencyTeachingCase> lqw = Wrappers.lambdaQuery();
        lqw.orderByAsc(EmergencyTeachingCase::getId);
        // 已删除 deptId、userId、orderNum（这些字段在实体类中已移除，属于基类字段）
        // lqw.eq(bo.getDeptId() != null, EmergencyTeachingCase::getDeptId, bo.getDeptId());
        // lqw.eq(bo.getUserId() != null, EmergencyTeachingCase::getUserId, bo.getUserId());
        // lqw.eq(bo.getOrderNum() != null, EmergencyTeachingCase::getOrderNum, bo.getOrderNum());
        lqw.eq(StringUtils.isNotBlank(bo.getCaseCode()), EmergencyTeachingCase::getCaseCode, bo.getCaseCode());
        lqw.eq(bo.getOrganizationId() != null, EmergencyTeachingCase::getOrganizationId, bo.getOrganizationId());
        lqw.like(StringUtils.isNotBlank(bo.getName()), EmergencyTeachingCase::getName, bo.getName());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), EmergencyTeachingCase::getStatus, bo.getStatus());
        lqw.eq(bo.getCurrentPublishedVersionId() != null, EmergencyTeachingCase::getCurrentPublishedVersionId, bo.getCurrentPublishedVersionId());
        return lqw;
    }

    /**
     * 新增病例身份；不保存具体教学定义
     *
     * @param bo 病例身份；不保存具体教学定义
     * @return 是否新增成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean insertByBo(EmergencyTeachingCaseBo bo) {
        EmergencyTeachingCase add = MapstructUtils.convert(bo, EmergencyTeachingCase.class);
        validEntityBeforeSave(add);
        boolean flag = baseMapper.insert(add) > 0;
        if (flag) {
            bo.setId(add.getId());
        }
        return flag;
    }

    /**
     * 修改病例身份；不保存具体教学定义
     *
     * @param bo 病例身份；不保存具体教学定义
     * @return 是否修改成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateByBo(EmergencyTeachingCaseBo bo) {
        EmergencyTeachingCase update = MapstructUtils.convert(bo, EmergencyTeachingCase.class);
        validEntityBeforeSave(update);
        return baseMapper.updateById(update) > 0;
    }

    /**
     * 保存前的数据校验
     */
    private void validEntityBeforeSave(EmergencyTeachingCase entity){
        //TODO 做一些数据校验,如唯一约束
    }

    /**
     * 校验并批量删除病例身份；不保存具体教学定义信息
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
     * 获取病例及其所有版本（父子表联合查询）
     *
     * @param id 病例ID
     * @return 包含版本列表的 Vo
     */
    @Override
    @Transactional(readOnly = true)
    public EmergencyTeachingCaseVo getCaseWithVersions(Long id) {
        // 1. 查询主表
        EmergencyTeachingCaseVo caseVo = baseMapper.selectVoById(id);
        if (caseVo == null) {
            return null;
        }

        // 2. 查询子表（所有版本，按版本号降序）
        LambdaQueryWrapper<EmergencyTeachingCaseVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EmergencyTeachingCaseVersion::getCaseId, id)
            .orderByDesc(EmergencyTeachingCaseVersion::getVersionNo);
        List<EmergencyTeachingCaseVersionVo> versions = versionMapper.selectVoList(wrapper);

        // 3. 设置到 Vo 中
        caseVo.setVersions(versions);
        return caseVo;
    }

    /**
     * 获取病例下的所有版本列表
     *
     * @param caseId 病例ID
     * @return 版本列表
     */
    @Override
    @Transactional(readOnly = true)
    public List<EmergencyTeachingCaseVersionVo> getVersionsByCaseId(Long caseId) {
        LambdaQueryWrapper<EmergencyTeachingCaseVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EmergencyTeachingCaseVersion::getCaseId, caseId)
            .orderByDesc(EmergencyTeachingCaseVersion::getVersionNo);
        return versionMapper.selectVoList(wrapper);
    }
}
