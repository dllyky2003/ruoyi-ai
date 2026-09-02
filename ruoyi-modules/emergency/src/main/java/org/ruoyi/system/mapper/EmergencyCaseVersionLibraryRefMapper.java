package org.ruoyi.system.mapper;

import jodd.introspector.Mapper;
import org.ruoyi.system.domain.EmergencyCaseVersionLibraryRef;
import org.ruoyi.system.domain.vo.EmergencyCaseVersionLibraryRefVo;
import org.ruoyi.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 病例引用了哪些公共库版本；用于影响分析与发布校验Mapper接口
 *
 * @author ageerle
 * @date 2026-08-18
 */
public interface EmergencyCaseVersionLibraryRefMapper extends BaseMapperPlus<EmergencyCaseVersionLibraryRef, EmergencyCaseVersionLibraryRefVo> {

}
