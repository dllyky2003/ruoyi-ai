package org.ruoyi.system.service;

import org.ruoyi.system.domain.vo.EmergencyTeachingCaseVersionVo;
import org.ruoyi.system.domain.vo.EmergencyTeachingCaseVo;
import org.ruoyi.system.domain.bo.EmergencyTeachingCaseBo;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.ruoyi.common.mybatis.core.page.PageQuery;

import java.util.Collection;
import java.util.List;

/**
 * 病例身份；不保存具体教学定义Service接口
 *
 * @author ageerle
 * @date 2026-08-18
 */
public interface IEmergencyTeachingCaseService {
    /**
     * 获取病例及其所有版本（父子表联合查询）
     */
    EmergencyTeachingCaseVo getCaseWithVersions(Long id);

    /**
     * 获取病例下的所有版本列表
     */
    List<EmergencyTeachingCaseVersionVo> getVersionsByCaseId(Long caseId);
    /**
     * 查询病例身份；不保存具体教学定义
     *
     * @param id 主键
     * @return 病例身份；不保存具体教学定义
     */
    EmergencyTeachingCaseVo queryById(Long id);

    /**
     * 分页查询病例身份；不保存具体教学定义列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 病例身份；不保存具体教学定义分页列表
     */
    TableDataInfo<EmergencyTeachingCaseVo> queryPageList(EmergencyTeachingCaseBo bo, PageQuery pageQuery);

    /**
     * 查询符合条件的病例身份；不保存具体教学定义列表
     *
     * @param bo 查询条件
     * @return 病例身份；不保存具体教学定义列表
     */
    List<EmergencyTeachingCaseVo> queryList(EmergencyTeachingCaseBo bo);

    /**
     * 新增病例身份；不保存具体教学定义
     *
     * @param bo 病例身份；不保存具体教学定义
     * @return 是否新增成功
     */
    Boolean insertByBo(EmergencyTeachingCaseBo bo);

    /**
     * 修改病例身份；不保存具体教学定义
     *
     * @param bo 病例身份；不保存具体教学定义
     * @return 是否修改成功
     */
    Boolean updateByBo(EmergencyTeachingCaseBo bo);

    /**
     * 校验并批量删除病例身份；不保存具体教学定义信息
     *
     * @param ids     待删除的主键集合
     * @param isValid 是否进行有效性校验
     * @return 是否删除成功
     */
    Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);
}
