package org.ruoyi.system.service;

import org.ruoyi.system.domain.vo.EmergencyLibraryItemVersionVo;
import org.ruoyi.system.domain.bo.EmergencyLibraryItemVersionBo;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.ruoyi.common.mybatis.core.page.PageQuery;

import java.util.Collection;
import java.util.List;

/**
 * 公共库冻结定义；published 后禁止 UPDATEService接口
 *
 * @author ageerle
 * @date 2026-08-18
 */
public interface IEmergencyLibraryItemVersionService {

    /**
     * 查询公共库冻结定义；published 后禁止 UPDATE
     *
     * @param id 主键
     * @return 公共库冻结定义；published 后禁止 UPDATE
     */
    EmergencyLibraryItemVersionVo queryById(Long id);

    /**
     * 分页查询公共库冻结定义；published 后禁止 UPDATE列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 公共库冻结定义；published 后禁止 UPDATE分页列表
     */
    TableDataInfo<EmergencyLibraryItemVersionVo> queryPageList(EmergencyLibraryItemVersionBo bo, PageQuery pageQuery);

    /**
     * 查询符合条件的公共库冻结定义；published 后禁止 UPDATE列表
     *
     * @param bo 查询条件
     * @return 公共库冻结定义；published 后禁止 UPDATE列表
     */
    List<EmergencyLibraryItemVersionVo> queryList(EmergencyLibraryItemVersionBo bo);

    /**
     * 新增公共库冻结定义；published 后禁止 UPDATE
     *
     * @param bo 公共库冻结定义；published 后禁止 UPDATE
     * @return 是否新增成功
     */
    Boolean insertByBo(EmergencyLibraryItemVersionBo bo);

    /**
     * 修改公共库冻结定义；published 后禁止 UPDATE
     *
     * @param bo 公共库冻结定义；published 后禁止 UPDATE
     * @return 是否修改成功
     */
    Boolean updateByBo(EmergencyLibraryItemVersionBo bo);

    /**
     * 校验并批量删除公共库冻结定义；published 后禁止 UPDATE信息
     *
     * @param ids     待删除的主键集合
     * @param isValid 是否进行有效性校验
     * @return 是否删除成功
     */
    Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);
}
