package org.ruoyi.ecg.mapper;

import org.ruoyi.ecg.domain.EcgPatientInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 病人信息Mapper接口
 *
 * @author ruoyi
 * @date 2026-08-04
 */
@Mapper
public interface EcgPatientInfoMapper
{
    // ==================== 查询方法 ====================

    /**
     * 查询病人信息列表（支持分页）
     *
     * @param ecgPatientInfo 病人信息查询条件
     * @return 病人信息集合
     */
    public List<EcgPatientInfo> selectEcgPatientInfoList(EcgPatientInfo ecgPatientInfo);

    /**
     * 根据病人ID查询病人信息
     *
     * @param patientId 病人信息主键
     * @return 病人信息
     */
    public EcgPatientInfo selectEcgPatientInfoById(@Param("patientId") Long patientId);

    /**
     * 根据病人编码查询病人信息
     *
     * @param patientCode 病人编码
     * @return 病人信息
     */
    public EcgPatientInfo selectEcgPatientInfoByCode(@Param("patientCode") String patientCode);



    // ==================== 插入方法 ====================

    /**
     * 新增病人信息
     *
     * @param ecgPatientInfo 病人信息
     * @return 结果
     */
    public int insertEcgPatientInfo(EcgPatientInfo ecgPatientInfo);

    /**
     * 批量新增病人信息
     *
     * @param patientList 病人信息列表
     * @return 结果
     */
    public int insertEcgPatientInfoBatch(@Param("list") List<EcgPatientInfo> patientList);

    // ==================== 更新方法 ====================

    /**
     * 修改病人信息
     *
     * @param ecgPatientInfo 病人信息
     * @return 结果
     */
    public int updateEcgPatientInfo(EcgPatientInfo ecgPatientInfo);

    /**
     * 批量更新病人信息
     *
     * @param patientList 病人信息列表
     * @return 结果
     */
    public int updateEcgPatientInfoBatch(@Param("list") List<EcgPatientInfo> patientList);

    /**
     * 更新病人分诊信息
     *
     * @param ecgPatientInfo 病人信息（包含分诊相关字段）
     * @return 结果
     */
    public int updateTriageInfo(EcgPatientInfo ecgPatientInfo);

    /**
     * 更新病人生命体征
     *
     * @param ecgPatientInfo 病人信息（包含生命体征相关字段）
     * @return 结果
     */
    public int updateVitalSigns(EcgPatientInfo ecgPatientInfo);

    // ==================== 删除方法（逻辑删除） ====================

    /**
     * 逻辑删除病人信息
     *
     * @param patientId 病人信息主键
     * @return 结果
     */
    public int deleteEcgPatientInfoById(@Param("patientId") Long patientId);

    /**
     * 批量逻辑删除病人信息
     *
     * @param patientIds 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteEcgPatientInfoByIds(@Param("array") Long[] patientIds);

    /**
     * 物理删除病人信息（慎用）
     *
     * @param patientId 病人信息主键
     * @return 结果
     */
    public int physicalDeleteEcgPatientInfoById(@Param("patientId") Long patientId);

    /**
     * 批量物理删除病人信息（慎用）
     *
     * @param patientIds 需要删除的数据主键集合
     * @return 结果
     */
    public int physicalDeleteEcgPatientInfoByIds(@Param("array") Long[] patientIds);
}
