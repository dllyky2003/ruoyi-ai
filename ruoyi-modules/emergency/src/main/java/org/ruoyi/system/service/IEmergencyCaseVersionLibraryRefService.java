package org.ruoyi.system.service;

import org.ruoyi.system.domain.vo.EmergencyCaseVersionLibraryRefVo;
import org.ruoyi.system.domain.bo.EmergencyCaseVersionLibraryRefBo;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.ruoyi.common.mybatis.core.page.PageQuery;

import java.util.Collection;
import java.util.List;

/**
 * 病例引用了哪些公共库版本；用于影响分析与发布校验Service接口
 *
 * @author ageerle
 * @date 2026-08-18
 */
public interface IEmergencyCaseVersionLibraryRefService {

    /**
     * 查询病例引用了哪些公共库版本；用于影响分析与发布校验
     *
     * @param caseVersionId 主键
     * @return 病例引用了哪些公共库版本；用于影响分析与发布校验
     */
    EmergencyCaseVersionLibraryRefVo queryById(Long caseVersionId);

    /**
     * 分页查询病例引用了哪些公共库版本；用于影响分析与发布校验列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 病例引用了哪些公共库版本；用于影响分析与发布校验分页列表
     */
    TableDataInfo<EmergencyCaseVersionLibraryRefVo> queryPageList(EmergencyCaseVersionLibraryRefBo bo, PageQuery pageQuery);

    /**
     * 查询符合条件的病例引用了哪些公共库版本；用于影响分析与发布校验列表
     *
     * @param bo 查询条件
     * @return 病例引用了哪些公共库版本；用于影响分析与发布校验列表
     */
    List<EmergencyCaseVersionLibraryRefVo> queryList(EmergencyCaseVersionLibraryRefBo bo);

    /**
     * 新增病例引用了哪些公共库版本；用于影响分析与发布校验
     *
     * @param bo 病例引用了哪些公共库版本；用于影响分析与发布校验
     * @return 是否新增成功
     */
    Boolean insertByBo(EmergencyCaseVersionLibraryRefBo bo);

    /**
     * 修改病例引用了哪些公共库版本；用于影响分析与发布校验
     *
     * @param bo 病例引用了哪些公共库版本；用于影响分析与发布校验
     * @return 是否修改成功
     */
    Boolean updateByBo(EmergencyCaseVersionLibraryRefBo bo);

    /**
     * 校验并批量删除病例引用了哪些公共库版本；用于影响分析与发布校验信息
     *
     * @param ids     待删除的主键集合
     * @param isValid 是否进行有效性校验
     * @return 是否删除成功
     */
    Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);
}
