package com.aryn.cloud.message.mapper;

import com.aryn.cloud.message.api.entity.MessageNotice;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** MessageNotice 持久层。 */
@Mapper
public interface MessageNoticeMapper extends BaseMapper<MessageNotice> {

	MessageNotice selectByIdForUpdate(@Param("tenantId") String tenantId, @Param("id") String id);

	MessageNotice selectBySource(@Param("tenantId") String tenantId, @Param("sourceType") String sourceType,
			@Param("sourceKey") String sourceKey);

	int insertIgnoreSource(MessageNotice notice);
}
