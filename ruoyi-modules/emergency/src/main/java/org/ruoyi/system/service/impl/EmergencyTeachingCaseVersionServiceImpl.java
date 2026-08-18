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
import org.ruoyi.system.domain.bo.EmergencyTeachingCaseVersionBo;
import org.ruoyi.system.domain.vo.EmergencyTeachingCaseVersionVo;
import org.ruoyi.system.domain.vo.EmergencyCaseVersionLibraryRefVo;
import org.ruoyi.system.domain.vo.EmergencyCaseVersionResourceRefVo;
import org.ruoyi.system.domain.vo.EmergencyCaseVersionRuleIndexVo;
import org.ruoyi.system.domain.vo.EmergencyCaseVersionScoreIndexVo;
import org.ruoyi.system.domain.vo.EmergencyCaseVersionStageIndexVo;
import org.ruoyi.system.domain.EmergencyTeachingCaseVersion;
import org.ruoyi.system.domain.EmergencyCaseVersionLibraryRef;
import org.ruoyi.system.domain.EmergencyCaseVersionResourceRef;
import org.ruoyi.system.domain.EmergencyCaseVersionRuleIndex;
import org.ruoyi.system.domain.EmergencyCaseVersionScoreIndex;
import org.ruoyi.system.domain.EmergencyCaseVersionStageIndex;
import org.ruoyi.system.mapper.EmergencyTeachingCaseVersionMapper;
import org.ruoyi.system.mapper.EmergencyCaseVersionLibraryRefMapper;
import org.ruoyi.system.mapper.EmergencyCaseVersionResourceRefMapper;
import org.ruoyi.system.mapper.EmergencyCaseVersionRuleIndexMapper;
import org.ruoyi.system.mapper.EmergencyCaseVersionScoreIndexMapper;
import org.ruoyi.system.mapper.EmergencyCaseVersionStageIndexMapper;
import org.ruoyi.system.service.IEmergencyTeachingCaseVersionService;

import java.util.List;
import java.util.Map;
import java.util.Collection;

/**
 * 病例定义版本；published 后禁止 UPDATEService业务层处理
 *
 * @author ageerle
 * @date 2026-08-18
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class EmergencyTeachingCaseVersionServiceImpl implements IEmergencyTeachingCaseVersionService {

    private final EmergencyTeachingCaseVersionMapper baseMapper;

    /**
     * 注入所有关联子表 Mapper
     */
    private final EmergencyCaseVersionLibraryRefMapper libraryRefMapper;
    private final EmergencyCaseVersionResourceRefMapper resourceRefMapper;
    private final EmergencyCaseVersionRuleIndexMapper ruleIndexMapper;
    private final EmergencyCaseVersionScoreIndexMapper scoreIndexMapper;
    private final EmergencyCaseVersionStageIndexMapper stageIndexMapper;

    /**
     * 查询病例定义版本；published 后禁止 UPDATE
     *
     * @param id 主键
     * @return 病例定义版本；published 后禁止 UPDATE
     */
    @Override
    @Transactional(readOnly = true)
    public EmergencyTeachingCaseVersionVo queryById(Long id){
        return baseMapper.selectVoById(id);
    }

    /**
     * 分页查询病例定义版本；published 后禁止 UPDATE列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 病例定义版本；published 后禁止 UPDATE分页列表
     */
    @Override
    @Transactional(readOnly = true)
    public TableDataInfo<EmergencyTeachingCaseVersionVo> queryPageList(EmergencyTeachingCaseVersionBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<EmergencyTeachingCaseVersion> lqw = buildQueryWrapper(bo);
        Page<EmergencyTeachingCaseVersionVo> result = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(result);
    }

    /**
     * 查询符合条件的病例定义版本；published 后禁止 UPDATE列表
     *
     * @param bo 查询条件
     * @return 病例定义版本；published 后禁止 UPDATE列表
     */
    @Override
    @Transactional(readOnly = true)
    public List<EmergencyTeachingCaseVersionVo> queryList(EmergencyTeachingCaseVersionBo bo) {
        LambdaQueryWrapper<EmergencyTeachingCaseVersion> lqw = buildQueryWrapper(bo);
        return baseMapper.selectVoList(lqw);
    }

    private LambdaQueryWrapper<EmergencyTeachingCaseVersion> buildQueryWrapper(EmergencyTeachingCaseVersionBo bo) {
        Map<String, Object> params = bo.getParams();
        LambdaQueryWrapper<EmergencyTeachingCaseVersion> lqw = Wrappers.lambdaQuery();
        lqw.orderByAsc(EmergencyTeachingCaseVersion::getId);
        // 已删除 deptId、userId、orderNum（这些字段在实体类中已移除，属于基类字段）
        // lqw.eq(bo.getDeptId() != null, EmergencyTeachingCaseVersion::getDeptId, bo.getDeptId());
        // lqw.eq(bo.getUserId() != null, EmergencyTeachingCaseVersion::getUserId, bo.getUserId());
        // lqw.eq(bo.getOrderNum() != null, EmergencyTeachingCaseVersion::getOrderNum, bo.getOrderNum());
        lqw.eq(bo.getCaseId() != null, EmergencyTeachingCaseVersion::getCaseId, bo.getCaseId());
        lqw.eq(bo.getVersionNo() != null, EmergencyTeachingCaseVersion::getVersionNo, bo.getVersionNo());
        lqw.eq(StringUtils.isNotBlank(bo.getSchemaVersion()), EmergencyTeachingCaseVersion::getSchemaVersion, bo.getSchemaVersion());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), EmergencyTeachingCaseVersion::getStatus, bo.getStatus());
        // definitionJson 是 JSON 字段，不适用 eq 查询（已移除）
        // lqw.eq(StringUtils.isNotBlank(bo.getDefinitionJson()), EmergencyTeachingCaseVersion::getDefinitionJson, bo.getDefinitionJson());
        lqw.eq(StringUtils.isNotBlank(bo.getDefinitionHash()), EmergencyTeachingCaseVersion::getDefinitionHash, bo.getDefinitionHash());
        lqw.eq(bo.getBasedOnVersionId() != null, EmergencyTeachingCaseVersion::getBasedOnVersionId, bo.getBasedOnVersionId());
        lqw.eq(StringUtils.isNotBlank(bo.getChangeSummary()), EmergencyTeachingCaseVersion::getChangeSummary, bo.getChangeSummary());
        lqw.eq(bo.getReviewedBy() != null, EmergencyTeachingCaseVersion::getReviewedBy, bo.getReviewedBy());
        lqw.eq(bo.getReviewedAt() != null, EmergencyTeachingCaseVersion::getReviewedAt, bo.getReviewedAt());
        lqw.eq(bo.getPublishedBy() != null, EmergencyTeachingCaseVersion::getPublishedBy, bo.getPublishedBy());
        lqw.eq(bo.getPublishedAt() != null, EmergencyTeachingCaseVersion::getPublishedAt, bo.getPublishedAt());
        return lqw;
    }

    /**
     * 新增病例定义版本；published 后禁止 UPDATE
     *
     * @param bo 病例定义版本；published 后禁止 UPDATE
     * @return 是否新增成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean insertByBo(EmergencyTeachingCaseVersionBo bo) {
        EmergencyTeachingCaseVersion add = MapstructUtils.convert(bo, EmergencyTeachingCaseVersion.class);
        validEntityBeforeSave(add);
        boolean flag = baseMapper.insert(add) > 0;
        if (flag) {
            bo.setId(add.getId());
        }
        return flag;
    }

    /**
     * 修改病例定义版本；published 后禁止 UPDATE
     *
     * @param bo 病例定义版本；published 后禁止 UPDATE
     * @return 是否修改成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateByBo(EmergencyTeachingCaseVersionBo bo) {
        EmergencyTeachingCaseVersion update = MapstructUtils.convert(bo, EmergencyTeachingCaseVersion.class);
        validEntityBeforeSave(update);
        return baseMapper.updateById(update) > 0;
    }

    /**
     * 保存前的数据校验
     */
    private void validEntityBeforeSave(EmergencyTeachingCaseVersion entity){
        //TODO 做一些数据校验,如唯一约束
    }

    /**
     * 校验并批量删除病例定义版本；published 后禁止 UPDATE信息
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
     * 获取病例版本及其所有关联数据（库引用、资源引用、规则、评分、阶段）
     *
     * @param versionId 病例版本ID
     * @return 包含所有关联数据的 Vo
     */
    @Override
    @Transactional(readOnly = true)
    public EmergencyTeachingCaseVersionVo getVersionWithAllRefs(Long versionId) {
        // 1. 查询主表
        EmergencyTeachingCaseVersionVo vo = baseMapper.selectVoById(versionId);
        if (vo == null) {
            return null;
        }

        // 2. 查询所有子表数据
        vo.setLibraryRefs(getLibraryRefsByVersionId(versionId));
        vo.setResourceRefs(getResourceRefsByVersionId(versionId));
        vo.setRules(getRulesByVersionId(versionId));
        vo.setScores(getScoresByVersionId(versionId));
        vo.setStages(getStagesByVersionId(versionId));

        return vo;
    }

    /**
     * 获取病例版本引用的公共库列表
     *
     * @param versionId 病例版本ID
     * @return 库引用列表
     */
    @Override
    @Transactional(readOnly = true)
    public List<EmergencyCaseVersionLibraryRefVo> getLibraryRefsByVersionId(Long versionId) {
        LambdaQueryWrapper<EmergencyCaseVersionLibraryRef> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EmergencyCaseVersionLibraryRef::getCaseVersionId, versionId)
            .orderByAsc(EmergencyCaseVersionLibraryRef::getId);
        return libraryRefMapper.selectVoList(wrapper);
    }

    /**
     * 获取病例版本的资源引用列表
     *
     * @param versionId 病例版本ID
     * @return 资源引用列表
     */
    @Override
    @Transactional(readOnly = true)
    public List<EmergencyCaseVersionResourceRefVo> getResourceRefsByVersionId(Long versionId) {
        LambdaQueryWrapper<EmergencyCaseVersionResourceRef> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EmergencyCaseVersionResourceRef::getCaseVersionId, versionId)
            .orderByAsc(EmergencyCaseVersionResourceRef::getId);
        return resourceRefMapper.selectVoList(wrapper);
    }

    /**
     * 获取病例版本的规则列表（按优先级升序）
     *
     * @param versionId 病例版本ID
     * @return 规则列表
     */
    @Override
    @Transactional(readOnly = true)
    public List<EmergencyCaseVersionRuleIndexVo> getRulesByVersionId(Long versionId) {
        LambdaQueryWrapper<EmergencyCaseVersionRuleIndex> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EmergencyCaseVersionRuleIndex::getCaseVersionId, versionId)
            .orderByAsc(EmergencyCaseVersionRuleIndex::getPriorityNo);
        return ruleIndexMapper.selectVoList(wrapper);
    }

    /**
     * 获取病例版本的评分项列表
     *
     * @param versionId 病例版本ID
     * @return 评分项列表
     */
    @Override
    @Transactional(readOnly = true)
    public List<EmergencyCaseVersionScoreIndexVo> getScoresByVersionId(Long versionId) {
        LambdaQueryWrapper<EmergencyCaseVersionScoreIndex> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EmergencyCaseVersionScoreIndex::getCaseVersionId, versionId)
            .orderByAsc(EmergencyCaseVersionScoreIndex::getId);
        return scoreIndexMapper.selectVoList(wrapper);
    }

    /**
     * 获取病例版本的阶段列表（按顺序号升序）
     *
     * @param versionId 病例版本ID
     * @return 阶段列表
     */
    @Override
    @Transactional(readOnly = true)
    public List<EmergencyCaseVersionStageIndexVo> getStagesByVersionId(Long versionId) {
        LambdaQueryWrapper<EmergencyCaseVersionStageIndex> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EmergencyCaseVersionStageIndex::getCaseVersionId, versionId)
            .orderByAsc(EmergencyCaseVersionStageIndex::getSequenceNo);
        return stageIndexMapper.selectVoList(wrapper);
    }
}
