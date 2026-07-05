
package com.aryn.cloud.notify.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.notify.api.dto.NotifyTemplateDTO;
import com.aryn.cloud.notify.api.entity.NotifyTemplate;

/**
 * 消息模板 Service
 *
 * @author aryn
 * @since 2026/07/05
 */
public interface INotifyTemplateService {

	/**
	 * 分页查询模板
	 * @param page 分页参数
	 * @param template 查询条件
	 * @return 模板分页
	 */
	IPage<NotifyTemplate> page(Page<NotifyTemplate> page, NotifyTemplate template);

	/**
	 * 模板详情
	 * @param id 主键
	 * @return 模板
	 */
	NotifyTemplate getById(String id);

	/**
	 * 新增模板
	 * @param dto 模板参数
	 */
	void save(NotifyTemplateDTO dto);

	/**
	 * 修改模板
	 * @param dto 模板参数
	 */
	void update(NotifyTemplateDTO dto);

	/**
	 * 删除模板
	 * @param id 主键
	 */
	void delete(String id);

}
