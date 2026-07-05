
package com.aryn.cloud.notify.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.notify.api.dto.NotifyBroadcastDTO;
import com.aryn.cloud.notify.api.dto.NotifySendDTO;
import com.aryn.cloud.notify.api.entity.NotifyBroadcast;
import com.aryn.cloud.notify.api.enums.NotifyBroadcastStatusEnum;
import com.aryn.cloud.notify.api.enums.NotifyTargetTypeEnum;
import com.aryn.cloud.notify.mapper.NotifyBroadcastMapper;
import com.aryn.cloud.notify.service.INotifyBroadcastService;
import com.aryn.cloud.notify.service.INotifyMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 群发消息 Service 实现
 * <p>
 * 一期仅支持「指定用户」(targetType=2) 模式。
 * 「全部用户」「会员等级」「标签」需要 user 模块扩展 Dubbo 远程接口后补全。
 * </p>
 *
 * @author aryn
 * @since 2026/07/05
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotifyBroadcastServiceImpl implements INotifyBroadcastService {

	private final NotifyBroadcastMapper notifyBroadcastMapper;

	private final INotifyMessageService notifyMessageService;

	@Override
	public void send(NotifyBroadcastDTO dto) {
		NotifyTargetTypeEnum targetType = NotifyTargetTypeEnum.values()[dto.getTargetType() - 1];
		if (targetType != NotifyTargetTypeEnum.SPECIFIED_USER) {
			throw new ArynBusinessException("一期暂仅支持「指定用户」群发模式，其他模式待用户服务扩展远程接口后支持");
		}
		if (StrUtil.isBlank(dto.getTargetIds())) {
			throw new ArynBusinessException("指定用户模式下，目标用户ID列表不能为空");
		}

		// 解析用户ID列表
		List<String> userIds = JSONUtil.toList(dto.getTargetIds(), String.class);
		if (CollUtil.isEmpty(userIds)) {
			throw new ArynBusinessException("目标用户ID列表为空");
		}

		// 创建群发记录
		NotifyBroadcast broadcast = new NotifyBroadcast();
		BeanUtils.copyProperties(dto, broadcast);
		broadcast.setTargetType(targetType.getCode());
		broadcast.setStatus(NotifyBroadcastStatusEnum.SENDING.getCode());
		broadcast.setSendTime(LocalDateTime.now());
		broadcast.setTotalCount(userIds.size());
		notifyBroadcastMapper.insert(broadcast);

		// 异步发送
		int success = 0;
		List<NotifySendDTO> dtoList = new ArrayList<>(userIds.size());
		for (String userId : userIds) {
			NotifySendDTO sendDTO = new NotifySendDTO()
					.setUserId(userId)
					.setNotifyType(dto.getNotifyType())
					.setTitle(dto.getTitle())
					.setContent(dto.getContent())
					.setBizType("broadcast")
					.setBizId(broadcast.getId())
					.setJumpType(dto.getJumpType() != null ? dto.getJumpType() : 0)
					.setJumpUrl(dto.getJumpUrl());
			dtoList.add(sendDTO);
		}
		for (NotifySendDTO sendDTO : dtoList) {
			try {
				notifyMessageService.sendMessage(sendDTO);
				success++;
			}
			catch (Exception e) {
				log.error("群发消息失败: userId={}, broadcastId={}", sendDTO.getUserId(), broadcast.getId(), e);
			}
		}

		// 更新记录状态
		broadcast.setSuccessCount(success);
		broadcast.setStatus(NotifyBroadcastStatusEnum.COMPLETED.getCode());
		notifyBroadcastMapper.updateById(broadcast);
	}

	@Override
	public IPage<NotifyBroadcast> page(Page<NotifyBroadcast> page, NotifyBroadcast broadcast) {
		LambdaQueryWrapper<NotifyBroadcast> wrapper = new LambdaQueryWrapper<NotifyBroadcast>()
			.like(StrUtil.isNotBlank(broadcast.getTitle()), NotifyBroadcast::getTitle, broadcast.getTitle())
			.eq(broadcast.getNotifyType() != null, NotifyBroadcast::getNotifyType, broadcast.getNotifyType())
			.eq(StrUtil.isNotBlank(broadcast.getStatus()), NotifyBroadcast::getStatus, broadcast.getStatus())
			.orderByDesc(NotifyBroadcast::getCreateTime);
		return notifyBroadcastMapper.selectPage(page, wrapper);
	}

	@Override
	public NotifyBroadcast getById(String id) {
		return notifyBroadcastMapper.selectById(id);
	}

}
