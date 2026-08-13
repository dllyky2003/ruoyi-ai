package org.ruoyi.ecg.service;

import org.ruoyi.ecg.domain.EcgPatientInfo;
import java.util.List;

/**
 * 病人信息Service接口
 *
 * @author ruoyi
 * @date 2026-08-04
 */
public interface IEcgPatientInfoService {

    /**
     * 查询病人信息
     *
     * @param patientId 病人信息主键
     * @return 病人信息
     */
    public EcgPatientInfo selectEcgPatientInfoById(Long patientId);

    /**
     * 根据病人编码查询病人信息
     *
     * @param patientCode 病人编码
     * @return 病人信息
     */
    public EcgPatientInfo selectEcgPatientInfoByCode(String patientCode);

    /**
     * 查询病人信息列表
     *
     * @param ecgPatientInfo 病人信息
     * @return 病人信息集合
     */
    public List<EcgPatientInfo> selectEcgPatientInfoList(EcgPatientInfo ecgPatientInfo);

    /**
     * 新增病人信息
     *
     * @param ecgPatientInfo 病人信息
     * @return 结果
     */
    public int insertEcgPatientInfo(EcgPatientInfo ecgPatientInfo);

    /**
     * 修改病人信息
     *
     * @param ecgPatientInfo 病人信息
     * @return 结果
     */
    public int updateEcgPatientInfo(EcgPatientInfo ecgPatientInfo);

    /**
     * 批量新增病人信息
     *
     * @param patientList 病人信息列表
     * @return 结果
     */
    public int insertEcgPatientInfoBatch(List<EcgPatientInfo> patientList);

    /**
     * 批量修改病人信息
     *
     * @param patientList 病人信息列表
     * @return 结果
     */
    public int updateEcgPatientInfoBatch(List<EcgPatientInfo> patientList);

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

    /**
     * 逻辑删除病人信息
     *
     * @param patientId 病人信息主键
     * @return 结果
     */
    public int deleteEcgPatientInfoById(Long patientId);

    /**
     * 批量逻辑删除病人信息
     *
     * @param patientIds 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteEcgPatientInfoByIds(Long[] patientIds);

    /**
     * 物理删除病人信息（慎用）
     *
     * @param patientId 病人信息主键
     * @return 结果
     */
    public int physicalDeleteEcgPatientInfoById(Long patientId);

    /**
     * 批量物理删除病人信息（慎用）
     *
     * @param patientIds 需要删除的数据主键集合
     * @return 结果
     */
    public int physicalDeleteEcgPatientInfoByIds(Long[] patientIds);

    /**
     * 检查病人编码是否存在
     *
     * @param patientCode 病人编码
     * @return true-存在，false-不存在
     */
    public boolean checkPatientCodeExists(String patientCode);

    /**
     * 批量导入病人信息
     *
     * @param patientList 病人信息列表
     * @param isUpdateSupport 是否更新支持，如果已存在则更新
     * @param operName 操作人
     * @return 导入结果
     */
    public String importPatientInfo(List<EcgPatientInfo> patientList, boolean isUpdateSupport, String operName);
}
