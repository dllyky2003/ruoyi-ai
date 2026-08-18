package org.ruoyi.system.service;

import org.ruoyi.system.domain.vo.EmergencyCaseVersionRuleIndexVo;
import org.ruoyi.system.domain.bo.EmergencyCaseVersionRuleIndexBo;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.ruoyi.common.mybatis.core.page.PageQuery;

import java.util.Collection;
import java.util.List;

/**
 * 病例版本的规则索引Service接口
 *
 * @author ageerle
 * @date 2026-08-18
 */
public interface IEmergencyCaseVersionRuleIndexService {

    /**
     * 查询病例版本的规则索引
     *
     * @param caseVersionId 主键
     * @return 病例版本的规则索引
     */
    EmergencyCaseVersionRuleIndexVo queryById(Long caseVersionId);

    /**
     * 分页查询病例版本的规则索引列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 病例版本的规则索引分页列表
     */
    TableDataInfo<EmergencyCaseVersionRuleIndexVo> queryPageList(EmergencyCaseVersionRuleIndexBo bo, PageQuery pageQuery);

    /**
     * 查询符合条件的病例版本的规则索引列表
     *
     * @param bo 查询条件
     * @return 病例版本的规则索引列表
     */
    List<EmergencyCaseVersionRuleIndexVo> queryList(EmergencyCaseVersionRuleIndexBo bo);

    /**
     * 新增病例版本的规则索引
     *
     * @param bo 病例版本的规则索引
     * @return 是否新增成功
     */
    Boolean insertByBo(EmergencyCaseVersionRuleIndexBo bo);

    /**
     * 修改病例版本的规则索引
     *
     * @param bo 病例版本的规则索引
     * @return 是否修改成功
     */
    Boolean updateByBo(EmergencyCaseVersionRuleIndexBo bo);

    /**
     * 校验并批量删除病例版本的规则索引信息
     *
     * @param ids     待删除的主键集合
     * @param isValid 是否进行有效性校验
     * @return 是否删除成功
     */
    Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);
}
