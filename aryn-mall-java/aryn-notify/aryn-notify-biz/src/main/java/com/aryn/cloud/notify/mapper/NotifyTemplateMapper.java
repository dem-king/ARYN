
package com.aryn.cloud.notify.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aryn.cloud.notify.api.entity.NotifyTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 消息模板 Mapper
 *
 * @author aryn
 * @since 2026/07/05
 */
@Mapper
public interface NotifyTemplateMapper extends BaseMapper<NotifyTemplate> {

	/**
	 * 根据模板编码查询启用的模板
	 * @param templateCode 模板编码
	 * @return 模板实体
	 */
	NotifyTemplate selectByCode(@Param("templateCode") String templateCode);

}
