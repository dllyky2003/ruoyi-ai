package org.ruoyi.system.service;

import org.ruoyi.system.domain.vo.EmergencyMediaAssetVo;
import org.ruoyi.system.domain.bo.EmergencyMediaAssetBo;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.ruoyi.common.mybatis.core.page.PageQuery;

import java.util.Collection;
import java.util.List;

/**
 * 文件仅存对象存储，数据库保存元数据Service接口
 *
 * @author ageerle
 * @date 2026-08-18
 */
public interface IEmergencyMediaAssetService {

    /**
     * 查询文件仅存对象存储，数据库保存元数据
     *
     * @param id 主键
     * @return 文件仅存对象存储，数据库保存元数据
     */
    EmergencyMediaAssetVo queryById(Long id);

    /**
     * 分页查询文件仅存对象存储，数据库保存元数据列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 文件仅存对象存储，数据库保存元数据分页列表
     */
    TableDataInfo<EmergencyMediaAssetVo> queryPageList(EmergencyMediaAssetBo bo, PageQuery pageQuery);

    /**
     * 查询符合条件的文件仅存对象存储，数据库保存元数据列表
     *
     * @param bo 查询条件
     * @return 文件仅存对象存储，数据库保存元数据列表
     */
    List<EmergencyMediaAssetVo> queryList(EmergencyMediaAssetBo bo);

    /**
     * 新增文件仅存对象存储，数据库保存元数据
     *
     * @param bo 文件仅存对象存储，数据库保存元数据
     * @return 是否新增成功
     */
    Boolean insertByBo(EmergencyMediaAssetBo bo);

    /**
     * 修改文件仅存对象存储，数据库保存元数据
     *
     * @param bo 文件仅存对象存储，数据库保存元数据
     * @return 是否修改成功
     */
    Boolean updateByBo(EmergencyMediaAssetBo bo);

    /**
     * 校验并批量删除文件仅存对象存储，数据库保存元数据信息
     *
     * @param ids     待删除的主键集合
     * @param isValid 是否进行有效性校验
     * @return 是否删除成功
     */
    Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);
}
