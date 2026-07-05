
package com.aryn.cloud.notify.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.notify.api.dto.NotifySendDTO;
import com.aryn.cloud.notify.api.entity.NotifyMessage;
import com.aryn.cloud.notify.api.vo.NotifyMessageVO;
import com.aryn.cloud.notify.api.vo.NotifyUnreadCountVO;

/**
 * 消息记录 Service
 *
 * @author aryn
 * @since 2026/07/05
 */
public interface INotifyMessageService {

	/**
	 * 发送单条消息（内部调用，异步执行）
	 * @param dto 发送参数
	 */
	void sendMessage(NotifySendDTO dto);

	/**
	 * C端：分页查询用户消息
	 * @param page 分页参数
	 * @param userId 用户ID
	 * @param notifyType 消息类型（可空）
	 * @return 消息分页
	 */
	IPage<NotifyMessageVO> pageUserMessages(Page<NotifyMessage> page, String userId, Integer notifyType);

	/**
	 * 管理端：分页查询消息记录
	 * @param page 分页参数
	 * @param notifyMessage 查询条件
	 * @return 消息分页
	 */
	IPage<NotifyMessage> pageAdminMessages(Page<NotifyMessage> page, NotifyMessage notifyMessage);

	/**
	 * C端：获取未读消息数（按类型分组）
	 * @param userId 用户ID
	 * @return 未读计数
	 */
	NotifyUnreadCountVO getUnreadCount(String userId);

	/**
	 * C端：标记消息已读
	 * @param userId 用户ID
	 * @param messageId 消息ID
	 */
	void markAsRead(String userId, String messageId);

	/**
	 * C端：全部已读
	 * @param userId 用户ID
	 * @param notifyType 消息类型（可空，空表示全部类型）
	 */
	void markAllAsRead(String userId, Integer notifyType);

	/**
	 * C端：删除消息（逻辑删除）
	 * @param userId 用户ID
	 * @param messageId 消息ID
	 */
	void deleteMessage(String userId, String messageId);

}
