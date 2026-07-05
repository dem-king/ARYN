
package com.aryn.cloud.notify.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.notify.api.dto.NotifyBroadcastDTO;
import com.aryn.cloud.notify.api.entity.NotifyBroadcast;

/**
 * 群发消息 Service
 *
 * @author aryn
 * @since 2026/07/05
 */
public interface INotifyBroadcastService {

	/**
	 * 发送群发消息
	 * @param dto 群发参数
	 */
	void send(NotifyBroadcastDTO dto);

	/**
	 * 分页查询群发记录
	 * @param page 分页参数
	 * @param broadcast 查询条件
	 * @return 群发记录分页
	 */
	IPage<NotifyBroadcast> page(Page<NotifyBroadcast> page, NotifyBroadcast broadcast);

	/**
	 * 群发记录详情
	 * @param id 主键
	 * @return 群发记录
	 */
	NotifyBroadcast getById(String id);

}
