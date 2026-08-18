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
import org.ruoyi.system.domain.bo.EmergencyMediaAssetBo;
import org.ruoyi.system.domain.vo.EmergencyMediaAssetVo;
import org.ruoyi.system.domain.EmergencyMediaAsset;
import org.ruoyi.system.mapper.EmergencyMediaAssetMapper;
import org.ruoyi.system.service.IEmergencyMediaAssetService;

import java.util.List;
import java.util.Map;
import java.util.Collection;

/**
 * 文件仅存对象存储，数据库保存元数据Service业务层处理
 *
 * @author ageerle
 * @date 2026-08-18
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class EmergencyMediaAssetServiceImpl implements IEmergencyMediaAssetService {

    private final EmergencyMediaAssetMapper baseMapper;

    /**
     * 查询文件仅存对象存储，数据库保存元数据
     *
     * @param id 主键
     * @return 文件仅存对象存储，数据库保存元数据
     */
    @Override
    public EmergencyMediaAssetVo queryById(Long id){
        return baseMapper.selectVoById(id);
    }

    /**
     * 分页查询文件仅存对象存储，数据库保存元数据列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 文件仅存对象存储，数据库保存元数据分页列表
     */
    @Override
    public TableDataInfo<EmergencyMediaAssetVo> queryPageList(EmergencyMediaAssetBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<EmergencyMediaAsset> lqw = buildQueryWrapper(bo);
        Page<EmergencyMediaAssetVo> result = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(result);
    }

    /**
     * 查询符合条件的文件仅存对象存储，数据库保存元数据列表
     *
     * @param bo 查询条件
     * @return 文件仅存对象存储，数据库保存元数据列表
     */
    @Override
    public List<EmergencyMediaAssetVo> queryList(EmergencyMediaAssetBo bo) {
        LambdaQueryWrapper<EmergencyMediaAsset> lqw = buildQueryWrapper(bo);
        return baseMapper.selectVoList(lqw);
    }

    private LambdaQueryWrapper<EmergencyMediaAsset> buildQueryWrapper(EmergencyMediaAssetBo bo) {
        Map<String, Object> params = bo.getParams();
        LambdaQueryWrapper<EmergencyMediaAsset> lqw = Wrappers.lambdaQuery();
        lqw.orderByAsc(EmergencyMediaAsset::getId);
        lqw.eq(bo.getDeptId() != null, EmergencyMediaAsset::getDeptId, bo.getDeptId());
        lqw.eq(bo.getUserId() != null, EmergencyMediaAsset::getUserId, bo.getUserId());
        lqw.eq(bo.getOrderNum() != null, EmergencyMediaAsset::getOrderNum, bo.getOrderNum());
        lqw.eq(bo.getOrganizationId() != null, EmergencyMediaAsset::getOrganizationId, bo.getOrganizationId());
        lqw.eq(StringUtils.isNotBlank(bo.getAssetType()), EmergencyMediaAsset::getAssetType, bo.getAssetType());
        lqw.eq(StringUtils.isNotBlank(bo.getStorageKey()), EmergencyMediaAsset::getStorageKey, bo.getStorageKey());
        lqw.like(StringUtils.isNotBlank(bo.getOriginalFilename()), EmergencyMediaAsset::getOriginalFilename, bo.getOriginalFilename());
        lqw.eq(StringUtils.isNotBlank(bo.getMimeType()), EmergencyMediaAsset::getMimeType, bo.getMimeType());
        lqw.eq(bo.getByteSize() != null, EmergencyMediaAsset::getByteSize, bo.getByteSize());
        lqw.eq(StringUtils.isNotBlank(bo.getSha256()), EmergencyMediaAsset::getSha256, bo.getSha256());
        return lqw;
    }

    /**
     * 新增文件仅存对象存储，数据库保存元数据
     *
     * @param bo 文件仅存对象存储，数据库保存元数据
     * @return 是否新增成功
     */
    @Override
    public Boolean insertByBo(EmergencyMediaAssetBo bo) {
        EmergencyMediaAsset add = MapstructUtils.convert(bo, EmergencyMediaAsset.class);
        validEntityBeforeSave(add);
        boolean flag = baseMapper.insert(add) > 0;
        if (flag) {
            bo.setId(add.getId());
        }
        return flag;
    }

    /**
     * 修改文件仅存对象存储，数据库保存元数据
     *
     * @param bo 文件仅存对象存储，数据库保存元数据
     * @return 是否修改成功
     */
    @Override
    public Boolean updateByBo(EmergencyMediaAssetBo bo) {
        EmergencyMediaAsset update = MapstructUtils.convert(bo, EmergencyMediaAsset.class);
        validEntityBeforeSave(update);
        return baseMapper.updateById(update) > 0;
    }

    /**
     * 保存前的数据校验
     */
    private void validEntityBeforeSave(EmergencyMediaAsset entity){
        //TODO 做一些数据校验,如唯一约束
    }

    /**
     * 校验并批量删除文件仅存对象存储，数据库保存元数据信息
     *
     * @param ids     待删除的主键集合
     * @param isValid 是否进行有效性校验
     * @return 是否删除成功
     */
    @Override
    public Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        if(isValid){
            //TODO 做一些业务上的校验,判断是否需要校验
        }
        return baseMapper.deleteByIds(ids) > 0;
    }
}
