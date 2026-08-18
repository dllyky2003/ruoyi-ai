package org.ruoyi.system.service;

import org.ruoyi.system.domain.vo.*;
import org.ruoyi.system.domain.bo.EmergencyTeachingCaseVersionBo;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.ruoyi.common.mybatis.core.page.PageQuery;

import java.util.Collection;
import java.util.List;

/**
 * 病例定义版本；published 后禁止 UPDATEService接口
 *
 * @author ageerle
 * @date 2026-08-18
 */
public interface IEmergencyTeachingCaseVersionService {
    /**
     * 获取病例版本及其所有关联数据（库引用、资源引用、规则、评分、阶段）
     */
    EmergencyTeachingCaseVersionVo getVersionWithAllRefs(Long versionId);

    /**
     * 获取病例版本引用的公共库列表
     */
    List<EmergencyCaseVersionLibraryRefVo> getLibraryRefsByVersionId(Long versionId);

    /**
     * 获取病例版本的资源引用列表
     */
    List<EmergencyCaseVersionResourceRefVo> getResourceRefsByVersionId(Long versionId);

    /**
     * 获取病例版本的规则列表（按优先级升序）
     */
    List<EmergencyCaseVersionRuleIndexVo> getRulesByVersionId(Long versionId);

    /**
     * 获取病例版本的评分项列表
     */
    List<EmergencyCaseVersionScoreIndexVo> getScoresByVersionId(Long versionId);

    /**
     * 获取病例版本的阶段列表（按顺序号升序）
     */
    List<EmergencyCaseVersionStageIndexVo> getStagesByVersionId(Long versionId);
    /**
     * 查询病例定义版本；published 后禁止 UPDATE
     *
     * @param id 主键
     * @return 病例定义版本；published 后禁止 UPDATE
     */
    EmergencyTeachingCaseVersionVo queryById(Long id);

    /**
     * 分页查询病例定义版本；published 后禁止 UPDATE列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 病例定义版本；published 后禁止 UPDATE分页列表
     */
    TableDataInfo<EmergencyTeachingCaseVersionVo> queryPageList(EmergencyTeachingCaseVersionBo bo, PageQuery pageQuery);

    /**
     * 查询符合条件的病例定义版本；published 后禁止 UPDATE列表
     *
     * @param bo 查询条件
     * @return 病例定义版本；published 后禁止 UPDATE列表
     */
    List<EmergencyTeachingCaseVersionVo> queryList(EmergencyTeachingCaseVersionBo bo);

    /**
     * 新增病例定义版本；published 后禁止 UPDATE
     *
     * @param bo 病例定义版本；published 后禁止 UPDATE
     * @return 是否新增成功
     */
    Boolean insertByBo(EmergencyTeachingCaseVersionBo bo);

    /**
     * 修改病例定义版本；published 后禁止 UPDATE
     *
     * @param bo 病例定义版本；published 后禁止 UPDATE
     * @return 是否修改成功
     */
    Boolean updateByBo(EmergencyTeachingCaseVersionBo bo);

    /**
     * 校验并批量删除病例定义版本；published 后禁止 UPDATE信息
     *
     * @param ids     待删除的主键集合
     * @param isValid 是否进行有效性校验
     * @return 是否删除成功
     */
    Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);

}
