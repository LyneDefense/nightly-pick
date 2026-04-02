package com.nightlypick.server.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nightlypick.server.persistence.entity.DailyRecordEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DailyRecordMapper extends BaseMapper<DailyRecordEntity> {
}
