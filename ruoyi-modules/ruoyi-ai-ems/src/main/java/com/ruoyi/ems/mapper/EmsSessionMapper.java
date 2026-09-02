package com.ruoyi.ems.mapper;

import com.ruoyi.ems.domain.entity.EmsSession;
import com.ruoyi.ems.domain.vo.EmsSessionVo;  // 需要创建对应的 Vo 类
import org.ruoyi.common.mybatis.core.mapper.BaseMapperPlus;
import org.apache.ibatis.annotations.Mapper;

/**
 * EMS会话Mapper接口
 *
 * @author yourname
 * @date 2026-08-23
 */
@Mapper
public interface EmsSessionMapper extends BaseMapperPlus<EmsSession, EmsSessionVo> {
    // 可在此扩展自定义SQL方法
}
