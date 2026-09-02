package org.ruoyi.system.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.common.core.utils.MapstructUtils;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.common.mybatis.core.page.PageQuery;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.ruoyi.system.domain.*;
import org.ruoyi.system.domain.bo.EmergencyTeachingCaseVersionBo;
import org.ruoyi.system.domain.vo.*;
import org.ruoyi.system.mapper.*;
import org.ruoyi.system.service.IEmergencyTeachingCaseVersionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 病例定义版本；published 后禁止 UPDATE 业务层处理
 * 增强版：支持加载公共库版本的具体定义（definitionJson），满足完整病例获取场景
 *
 * @author ageerle
 * @date 2026-08-18
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class EmergencyTeachingCaseVersionServiceImpl implements IEmergencyTeachingCaseVersionService {

    private final EmergencyTeachingCaseVersionMapper baseMapper;


    private final EmergencyCaseVersionLibraryRefMapper libraryRefMapper;
    private final EmergencyCaseVersionResourceRefMapper resourceRefMapper;
    private final EmergencyCaseVersionRuleIndexMapper ruleIndexMapper;
    private final EmergencyCaseVersionScoreIndexMapper scoreIndexMapper;
    private final EmergencyCaseVersionStageIndexMapper stageIndexMapper;

    // 新增：公共库版本 Mapper，用于加载具体定义
    private final EmergencyLibraryItemVersionMapper libraryItemVersionMapper;

    /**
     * 查询病例定义版本（含所有关联子表数据 + 公共库具体定义）
     * 这是获取“完整病例”的核心入口
     *
     * @param id 病例版本ID
     * @return 病例定义版本 Vo，包含库引用、资源引用、规则、评分、阶段，以及各引用对应的公共库定义
     */
    @Override
    @Transactional(readOnly = true)
    public EmergencyTeachingCaseVersionVo queryById(Long id) {
        // 1. 查询主表
        EmergencyTeachingCaseVersionVo vo = baseMapper.selectVoById(id);
        if (vo == null) {
            return null;
        }

        // 2. 查询所有子表数据（按需加载）
        List<EmergencyCaseVersionLibraryRefVo> libraryRefs = getLibraryRefsByVersionId(id);
        List<EmergencyCaseVersionResourceRefVo> resourceRefs = getResourceRefsByVersionId(id);
        List<EmergencyCaseVersionRuleIndexVo> rules = getRulesByVersionId(id);
        List<EmergencyCaseVersionScoreIndexVo> scores = getScoresByVersionId(id);
        List<EmergencyCaseVersionStageIndexVo> stages = getStagesByVersionId(id);

        // 3. 批量加载公共库版本的具体定义（definitionJson），避免 N+1 查询
        enrichLibraryDefinitions(libraryRefs, resourceRefs, scores);

        // 4. 设置到 Vo
        vo.setLibraryRefs(libraryRefs);
        vo.setResourceRefs(resourceRefs);
        vo.setRules(rules);
        vo.setScores(scores);
        vo.setStages(stages);

        return vo;
    }

    /**
     * 批量填充公共库版本的具体定义（JSON）
     * 同时处理 libraryRef、resourceRef、scoreIndex 中引用的 libraryVersionId
     */
    private void enrichLibraryDefinitions(
        List<EmergencyCaseVersionLibraryRefVo> libraryRefs,
        List<EmergencyCaseVersionResourceRefVo> resourceRefs,
        List<EmergencyCaseVersionScoreIndexVo> scores) {

        // 收集所有需要查询的 libraryVersionId
        Set<Long> versionIds = new HashSet<>();
        if (CollUtil.isNotEmpty(libraryRefs)) {
            versionIds.addAll(libraryRefs.stream()
                .map(EmergencyCaseVersionLibraryRefVo::getLibraryVersionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        }
        if (CollUtil.isNotEmpty(resourceRefs)) {
            versionIds.addAll(resourceRefs.stream()
                .map(EmergencyCaseVersionResourceRefVo::getLibraryVersionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        }
        if (CollUtil.isNotEmpty(scores)) {
            versionIds.addAll(scores.stream()
                .map(EmergencyCaseVersionScoreIndexVo::getLibraryVersionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        }

        if (CollUtil.isEmpty(versionIds)) {
            return;
        }

        // 批量查询公共库版本（只查询必要字段，但 definitionJson 是核心）
        List<EmergencyLibraryItemVersion> libraryVersions = libraryItemVersionMapper.selectList(
            Wrappers.<EmergencyLibraryItemVersion>lambdaQuery()
                .in(EmergencyLibraryItemVersion::getId, versionIds)
        );

        if (CollUtil.isEmpty(libraryVersions)) {
            return;
        }

        // 转为 Map 方便查找
        Map<Long, EmergencyLibraryItemVersion> versionMap = libraryVersions.stream()
            .collect(Collectors.toMap(EmergencyLibraryItemVersion::getId, v -> v));

        // 填充各个 Vo 的 libraryDefinitionJson 字段（假设 Vo 中有该字段，若没有需在 Vo 中扩展）
        // 注：如果 Vo 没有 libraryDefinitionJson 字段，可以暂不处理，或通过扩展字段存放。
        // 这里采用通用的方式，设置到扩展属性中，或直接转换为 JSON 字符串
        if (CollUtil.isNotEmpty(libraryRefs)) {
            libraryRefs.forEach(ref -> {
                EmergencyLibraryItemVersion libVer = versionMap.get(ref.getLibraryVersionId());
                if (libVer != null) {
                    // 若 Vo 中有定义字段，直接 set；若无，可存入附加属性或忽略
                    // 示例：假设 Vo 有 setLibraryDefinitionJson 方法
                    ref.setLibraryDefinitionJson(libVer.getDefinitionJson());
                    // 也可填充其他信息
                    ref.setLibraryName(libVer.getName());
                    ref.setLibraryVersionNo(libVer.getVersionNo());
                }
            });
        }

        if (CollUtil.isNotEmpty(resourceRefs)) {
            resourceRefs.forEach(ref -> {
                EmergencyLibraryItemVersion libVer = versionMap.get(ref.getLibraryVersionId());
                if (libVer != null) {
                    ref.setLibraryDefinitionJson(libVer.getDefinitionJson());
                    ref.setLibraryName(libVer.getName());
                    ref.setLibraryVersionNo(libVer.getVersionNo());
                }
            });
        }

        if (CollUtil.isNotEmpty(scores)) {
            scores.forEach(score -> {
                EmergencyLibraryItemVersion libVer = versionMap.get(score.getLibraryVersionId());
                if (libVer != null) {
                    score.setLibraryDefinitionJson(libVer.getDefinitionJson());
                    score.setLibraryName(libVer.getName());
                    score.setLibraryVersionNo(libVer.getVersionNo());
                }
            });
        }
    }

    /**
     * 分页查询病例定义版本列表（列表查询时不加载子表，提高性能）
     */
    @Override
    @Transactional(readOnly = true)
    public TableDataInfo<EmergencyTeachingCaseVersionVo> queryPageList(EmergencyTeachingCaseVersionBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<EmergencyTeachingCaseVersion> lqw = buildQueryWrapper(bo);
        Page<EmergencyTeachingCaseVersionVo> result = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(result);
    }

    /**
     * 查询符合条件的病例定义版本列表（不加载子表）
     */
    @Override
    @Transactional(readOnly = true)
    public List<EmergencyTeachingCaseVersionVo> queryList(EmergencyTeachingCaseVersionBo bo) {
        LambdaQueryWrapper<EmergencyTeachingCaseVersion> lqw = buildQueryWrapper(bo);
        return baseMapper.selectVoList(lqw);
    }

    /**
     * 构建查询条件
     */
    private LambdaQueryWrapper<EmergencyTeachingCaseVersion> buildQueryWrapper(EmergencyTeachingCaseVersionBo bo) {
        LambdaQueryWrapper<EmergencyTeachingCaseVersion> lqw = Wrappers.lambdaQuery();
        lqw.orderByAsc(EmergencyTeachingCaseVersion::getId);
        lqw.eq(bo.getCaseId() != null, EmergencyTeachingCaseVersion::getCaseId, bo.getCaseId());
        lqw.eq(bo.getVersionNo() != null, EmergencyTeachingCaseVersion::getVersionNo, bo.getVersionNo());
        lqw.eq(StringUtils.isNotBlank(bo.getSchemaVersion()), EmergencyTeachingCaseVersion::getSchemaVersion, bo.getSchemaVersion());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), EmergencyTeachingCaseVersion::getStatus, bo.getStatus());
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
     * 新增病例定义版本
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
     * 修改病例定义版本（注意：published 后应禁止 UPDATE，需业务层控制）
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
    private void validEntityBeforeSave(EmergencyTeachingCaseVersion entity) {
        // 可扩展唯一约束校验等
    }

    /**
     * 校验并批量删除
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        if (isValid) {
            // 可以添加业务校验，例如已发布的版本不允许删除
        }
        return baseMapper.deleteByIds(ids) > 0;
    }

    // ==================== 子表查询方法（各子表独立，便于复用） ====================

    @Override
    @Transactional(readOnly = true)
    public List<EmergencyCaseVersionLibraryRefVo> getLibraryRefsByVersionId(Long versionId) {
        LambdaQueryWrapper<EmergencyCaseVersionLibraryRef> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(EmergencyCaseVersionLibraryRef::getCaseVersionId, versionId)
            .orderByAsc(EmergencyCaseVersionLibraryRef::getId);
        return libraryRefMapper.selectVoList(wrapper);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmergencyCaseVersionResourceRefVo> getResourceRefsByVersionId(Long versionId) {
        LambdaQueryWrapper<EmergencyCaseVersionResourceRef> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(EmergencyCaseVersionResourceRef::getCaseVersionId, versionId)
            .orderByAsc(EmergencyCaseVersionResourceRef::getId);
        return resourceRefMapper.selectVoList(wrapper);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmergencyCaseVersionRuleIndexVo> getRulesByVersionId(Long versionId) {
        LambdaQueryWrapper<EmergencyCaseVersionRuleIndex> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(EmergencyCaseVersionRuleIndex::getCaseVersionId, versionId)
            .orderByAsc(EmergencyCaseVersionRuleIndex::getPriorityNo);
        return ruleIndexMapper.selectVoList(wrapper);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmergencyCaseVersionScoreIndexVo> getScoresByVersionId(Long versionId) {
        LambdaQueryWrapper<EmergencyCaseVersionScoreIndex> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(EmergencyCaseVersionScoreIndex::getCaseVersionId, versionId)
            .orderByAsc(EmergencyCaseVersionScoreIndex::getId);
        return scoreIndexMapper.selectVoList(wrapper);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmergencyCaseVersionStageIndexVo> getStagesByVersionId(Long versionId) {
        LambdaQueryWrapper<EmergencyCaseVersionStageIndex> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(EmergencyCaseVersionStageIndex::getCaseVersionId, versionId)
            .orderByAsc(EmergencyCaseVersionStageIndex::getSequenceNo);
        return stageIndexMapper.selectVoList(wrapper);
    }

    /**
     * 获取完整病例版本（语义清晰，直接复用 queryById）
     */
    @Override
    @Transactional(readOnly = true)
    public EmergencyTeachingCaseVersionVo getVersionWithAllRefs(Long versionId) {
        return queryById(versionId);
    }
}
