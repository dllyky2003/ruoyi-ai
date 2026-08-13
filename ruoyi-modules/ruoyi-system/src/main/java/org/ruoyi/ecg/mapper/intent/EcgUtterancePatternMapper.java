package org.ruoyi.ecg.mapper.intent;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import org.ruoyi.ecg.domain.intent.EcgUtterancePattern;

import java.util.List;

/**
 * 语料主表 Mapper 接口
 *
 * @author ecg-system
 */
@Mapper
public interface EcgUtterancePatternMapper {

    // ==================== 查询方法 ====================

    /**
     * 查询语料列表（支持分页）
     *
     * @param utterance 查询条件
     * @return 语料集合
     */
    public List<EcgUtterancePattern> selectEcgUtterancePatternList(EcgUtterancePattern utterance);

    /**
     * 根据意图ID查询语料列表
     *
     * @param intentId 意图编码
     * @return 语料集合
     */
    public List<EcgUtterancePattern> selectUtterancesByIntentId(@Param("intentId") String intentId);

    /**
     * 根据主键ID查询语料
     *
     * @param id 主键
     * @return 语料
     */
    public EcgUtterancePattern selectEcgUtterancePatternById(@Param("id") Long id);

    /**
     * 查询高频语料（按命中次数降序）
     *
     * @param limit 返回条数
     * @return 语料集合
     */
    public List<EcgUtterancePattern> selectHotUtterances(@Param("limit") Integer limit);

    /**
     * 检查语料是否已被使用（防止重复）
     *
     * @param intentId 意图编码
     * @param utterance 话术内容
     * @return 数量
     */
    public int countByUtterance(@Param("intentId") String intentId, @Param("utterance") String utterance);

    // ==================== 插入方法 ====================

    /**
     * 新增语料
     *
     * @param utterance 语料
     * @return 结果
     */
    public int insertEcgUtterancePattern(EcgUtterancePattern utterance);

    /**
     * 批量新增语料
     *
     * @param utterances 语料列表
     * @return 结果
     */
    public int insertEcgUtterancePatternBatch(@Param("list") List<EcgUtterancePattern> utterances);

    // ==================== 更新方法 ====================

    /**
     * 修改语料
     *
     * @param utterance 语料
     * @return 结果
     */
    public int updateEcgUtterancePattern(EcgUtterancePattern utterance);

    /**
     * 增加语料命中次数
     *
     * @param id 主键
     * @return 结果
     */
    public int incrementHitCount(@Param("id") Long id);

    // ==================== 删除方法（逻辑删除） ====================

    /**
     * 逻辑删除语料
     *
     * @param id 主键
     * @return 结果
     */
    public int deleteEcgUtterancePatternById(@Param("id") Long id);

    /**
     * 根据意图ID批量逻辑删除语料
     *
     * @param intentId 意图编码
     * @return 结果
     */
    public int deleteUtterancesByIntentId(@Param("intentId") String intentId);

    /**
     * 批量逻辑删除语料
     *
     * @param ids 主键数组
     * @return 结果
     */
    public int deleteEcgUtterancePatternByIds(@Param("array") Long[] ids);
}
