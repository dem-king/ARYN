package com.aryn.cloud.message.service.impl;

import com.aryn.cloud.message.api.enums.ConversationType;
import com.aryn.cloud.message.api.enums.MessageIdentityType;
import com.aryn.cloud.message.api.vo.conversation.ConversationVO;
import com.aryn.cloud.message.mapper.MessageParticipantMapper;
import com.aryn.cloud.user.api.dto.UserMessageAudienceRequest;
import com.aryn.cloud.user.api.remote.RemoteMessageAudienceService;
import com.aryn.cloud.user.api.vo.UserMessageAudiencePageVO;
import com.aryn.cloud.user.api.vo.UserMessageRecipientVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 会员参与者昵称/头像快照补齐。
 *
 * <p>背景（2026-11-05 管理端客服工作台把会员显示成「会员 + 会员 ID」）：会话参与者行里的
 * 会员快照取自 TOC 登录 token，而 TOC 登录只写了 {@code username}、没有写
 * {@code nickname}/{@code avatar}，导致 {@code message_participant.participant_name} 为 NULL；
 * 管理端列表/会话头/信息面板只能回退显示会员 ID。</p>
 *
 * <p>修复口径：昵称/头像以用户域（{@link RemoteMessageAudienceService}）为权威来源，
 * 创建会话时直接写入参与者快照；读取历史会话时发现快照缺失则即时解析并回填，
 * 一次批量查询 + 逐行 best-effort 更新，查询失败不影响会话主流程（前端仍回退 ID）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerSnapshotService {

	/** 单次批量解析上限：管理端列表分页最大 50，留足余量且不超过受众查询限制。 */
	private static final int BATCH_LIMIT = 100;

	private final MessageParticipantMapper participantMapper;

	@DubboReference
	private final RemoteMessageAudienceService audienceService;

	/**
	 * 按会员 ID 解析昵称/头像快照。
	 *
	 * @return 会员不存在、已禁用、跨租户或用户域查询失败时返回 {@code null}
	 */
	public UserMessageRecipientVO resolve(String tenantId, String customerId) {
		if (!StringUtils.hasText(tenantId) || !StringUtils.hasText(customerId)) {
			return null;
		}
		try {
			UserMessageAudiencePageVO page = query(tenantId, List.of(customerId), 1);
			UserMessageRecipientVO recipient = firstRecord(page);
			return recipient != null && customerId.equals(recipient.getId()) ? recipient : null;
		}
		catch (RuntimeException exception) {
			log.warn("解析会员资料快照失败 tenantId={}, customerId={}", tenantId, customerId, exception);
			return null;
		}
	}

	/**
	 * 单个会话补齐：只补空字段，已有昵称/头像不覆盖。
	 *
	 * @param fallbackName   用户域查不到昵称时的兜底值（如 token 里的昵称、通知快照），可为 null
	 * @param fallbackAvatar 用户域查不到头像时的兜底值，可为 null
	 */
	public void fillMissing(String tenantId, String conversationId, String customerId, String fallbackName,
			String fallbackAvatar, ConversationVO view) {
		if (view == null || !StringUtils.hasText(conversationId) || !StringUtils.hasText(customerId)) {
			return;
		}
		if (isComplete(view.getCustomerName(), view.getCustomerAvatar())) {
			return;
		}
		UserMessageRecipientVO recipient = resolve(tenantId, customerId);
		fillView(view, recipient, fallbackName, fallbackAvatar);
		persistSnapshot(tenantId, conversationId, customerId, view.getCustomerName(), view.getCustomerAvatar());
	}

	/** 列表批量补齐：一次用户域查询，逐行 best-effort 回写参与者快照。 */
	public void fillMissingBatch(String tenantId, List<ConversationVO> conversations) {
		if (conversations == null || conversations.isEmpty()) {
			return;
		}
		Map<String, ConversationVO> pending = new LinkedHashMap<>();
		Set<String> customerIds = new LinkedHashSet<>();
		for (ConversationVO view : conversations) {
			if (!needsEnrich(view)) {
				continue;
			}
			pending.put(view.getId(), view);
			customerIds.add(view.getCustomerId());
		}
		if (customerIds.isEmpty()) {
			return;
		}
		Map<String, UserMessageRecipientVO> recipients = resolveBatch(tenantId, customerIds);
		for (ConversationVO view : pending.values()) {
			UserMessageRecipientVO recipient = recipients.get(view.getCustomerId());
			if (recipient == null) {
				continue;
			}
			fillView(view, recipient, null, null);
			persistSnapshot(tenantId, view.getId(), view.getCustomerId(), view.getCustomerName(),
					view.getCustomerAvatar());
		}
	}

	/** 会话需要补齐：客服会话 + 有会员 ID + 昵称或头像缺失。 */
	private boolean needsEnrich(ConversationVO view) {
		return view != null
				&& ConversationType.CUSTOMER_SERVICE.name().equals(view.getConversationType())
				&& StringUtils.hasText(view.getCustomerId())
				&& !isComplete(view.getCustomerName(), view.getCustomerAvatar());
	}

	private boolean isComplete(String name, String avatar) {
		return StringUtils.hasText(name) && StringUtils.hasText(avatar);
	}

	private void fillView(ConversationVO view, UserMessageRecipientVO recipient, String fallbackName,
			String fallbackAvatar) {
		if (!StringUtils.hasText(view.getCustomerName())) {
			view.setCustomerName(firstNonBlank(recipient == null ? null : recipient.getNickname(), fallbackName));
		}
		if (!StringUtils.hasText(view.getCustomerAvatar())) {
			view.setCustomerAvatar(firstNonBlank(recipient == null ? null : recipient.getAvatarUrl(), fallbackAvatar));
		}
	}

	/** 回写参与者快照：失败只记日志，不能让管理员刷新列表时报错。 */
	private void persistSnapshot(String tenantId, String conversationId, String customerId, String name, String avatar) {
		if (!StringUtils.hasText(conversationId) || !StringUtils.hasText(customerId)
				|| (!StringUtils.hasText(name) && !StringUtils.hasText(avatar))) {
			return;
		}
		try {
			participantMapper.fillSnapshotIfBlank(tenantId, conversationId,
					MessageIdentityType.MALL_USER.name(), customerId, name, avatar);
		}
		catch (RuntimeException exception) {
			log.warn("回填会员资料快照失败 tenantId={}, conversationId={}, customerId={}", tenantId, conversationId,
					customerId, exception);
		}
	}

	private Map<String, UserMessageRecipientVO> resolveBatch(String tenantId, Set<String> customerIds) {
		List<String> ids = new ArrayList<>(customerIds);
		if (ids.size() > BATCH_LIMIT) {
			ids = ids.subList(0, BATCH_LIMIT);
		}
		try {
			UserMessageAudiencePageVO page = query(tenantId, ids, ids.size());
			Map<String, UserMessageRecipientVO> result = new LinkedHashMap<>();
			if (page != null && page.getRecords() != null) {
				for (UserMessageRecipientVO recipient : page.getRecords()) {
					if (recipient != null && StringUtils.hasText(recipient.getId())) {
						result.put(recipient.getId(), recipient);
					}
				}
			}
			return result;
		}
		catch (RuntimeException exception) {
			log.warn("批量解析会员资料快照失败 tenantId={}, size={}", tenantId, ids.size(), exception);
			return Map.of();
		}
	}

	private UserMessageAudiencePageVO query(String tenantId, List<String> userIds, int limit) {
		UserMessageAudienceRequest request = new UserMessageAudienceRequest();
		request.setTenantId(tenantId);
		request.setUserIds(userIds);
		request.setLimit(limit);
		return audienceService.queryRecipients(request);
	}

	private UserMessageRecipientVO firstRecord(UserMessageAudiencePageVO page) {
		if (page == null || page.getRecords() == null || page.getRecords().isEmpty()) {
			return null;
		}
		return page.getRecords().get(0);
	}

	private String firstNonBlank(String first, String second) {
		return StringUtils.hasText(first) ? first : (StringUtils.hasText(second) ? second : null);
	}

}
