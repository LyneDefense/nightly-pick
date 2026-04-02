package com.nightlypick.server.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nightlypick.server.persistence.entity.ConversationSessionEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConversationSessionMapper extends BaseMapper<ConversationSessionEntity> {
}
