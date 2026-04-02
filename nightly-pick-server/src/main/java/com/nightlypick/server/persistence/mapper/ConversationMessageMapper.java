package com.nightlypick.server.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nightlypick.server.persistence.entity.ConversationMessageEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConversationMessageMapper extends BaseMapper<ConversationMessageEntity> {
}
