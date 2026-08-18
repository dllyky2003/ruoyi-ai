package org.ruoyi.system.service;

import org.ruoyi.system.domain.vo.EmergencyCaseVersionResourceRefVo;
import org.ruoyi.system.domain.bo.EmergencyCaseVersionResourceRefBo;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.ruoyi.common.mybatis.core.page.PageQuery;

import java.util.Collection;
import java.util.List;

/**
 * 病例版本引用的资源配置与公共库版本映射Service接口
 *
 * @author ageerle
 * @date 2026-08-18
 */
public interface IEmergencyCaseVersionResourceRefService {

    /**
     * 查询病例版本引用的资源配置与公共库版本映射
     *
     * @param caseVersionId 主键
     * @return 病例版本引用的资源配置与公共库版本映射
     */
    EmergencyCaseVersionResourceRefVo queryById(Long caseVersionId);

    /**
     * 分页查询病例版本引用的资源配置与公共库版本映射列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 病例版本引用的资源配置与公共库版本映射分页列表
     */
    TableDataInfo<EmergencyCaseVersionResourceRefVo> queryPageList(EmergencyCaseVersionResourceRefBo bo, PageQuery pageQuery);

    /**
     * 查询符合条件的病例版本引用的资源配置与公共库版本映射列表
     *
     * @param bo 查询条件
     * @return 病例版本引用的资源配置与公共库版本映射列表
     */
    List<EmergencyCaseVersionResourceRefVo> queryList(EmergencyCaseVersionResourceRefBo bo);

    /**
     * 新增病例版本引用的资源配置与公共库版本映射
     *
     * @param bo 病例版本引用的资源配置与公共库版本映射
     * @return 是否新增成功
     */
    Boolean insertByBo(EmergencyCaseVersionResourceRefBo bo);

    /**
     * 修改病例版本引用的资源配置与公共库版本映射
     *
     * @param bo 病例版本引用的资源配置与公共库版本映射
     * @return 是否修改成功
     */
    Boolean updateByBo(EmergencyCaseVersionResourceRefBo bo);

    /**
     * 校验并批量删除病例版本引用的资源配置与公共库版本映射信息
     *
     * @param ids     待删除的主键集合
     * @param isValid 是否进行有效性校验
     * @return 是否删除成功
     */
    Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);
}
