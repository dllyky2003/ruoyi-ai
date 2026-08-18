package org.ruoyi.system.service;

import org.ruoyi.system.domain.vo.EmergencyLibraryItemVersionVo;
import org.ruoyi.system.domain.vo.EmergencyLibraryItemVo;
import org.ruoyi.system.domain.bo.EmergencyLibraryItemBo;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.ruoyi.common.mybatis.core.page.PageQuery;

import java.util.Collection;
import java.util.List;

/**
 * 公共库对象身份Service接口
 *
 * @author ageerle
 * @date 2026-08-18
 */
public interface IEmergencyLibraryItemService {
    public List<EmergencyLibraryItemVersionVo> getVersionsByItemId(Long itemId);
    public EmergencyLibraryItemVo getItemWithVersions(Long id);
    /**
     * 查询公共库对象身份
     *
     * @param id 主键
     * @return 公共库对象身份
     */
    EmergencyLibraryItemVo queryById(Long id);

    /**
     * 分页查询公共库对象身份列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 公共库对象身份分页列表
     */
    TableDataInfo<EmergencyLibraryItemVo> queryPageList(EmergencyLibraryItemBo bo, PageQuery pageQuery);

    /**
     * 查询符合条件的公共库对象身份列表
     *
     * @param bo 查询条件
     * @return 公共库对象身份列表
     */
    List<EmergencyLibraryItemVo> queryList(EmergencyLibraryItemBo bo);

    /**
     * 新增公共库对象身份
     *
     * @param bo 公共库对象身份
     * @return 是否新增成功
     */
    Boolean insertByBo(EmergencyLibraryItemBo bo);

    /**
     * 修改公共库对象身份
     *
     * @param bo 公共库对象身份
     * @return 是否修改成功
     */
    Boolean updateByBo(EmergencyLibraryItemBo bo);

    /**
     * 校验并批量删除公共库对象身份信息
     *
     * @param ids     待删除的主键集合
     * @param isValid 是否进行有效性校验
     * @return 是否删除成功
     */
    Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);
}
