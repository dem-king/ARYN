package com.aryn.cloud.message.mapper;

import com.aryn.cloud.message.api.entity.MessageConversation;
import com.aryn.cloud.message.api.vo.conversation.ConversationVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** MessageConversation 持久层。 */
@Mapper
public interface MessageConversationMapper extends BaseMapper<MessageConversation> {

	MessageConversation selectActiveCustomerConversation(@Param("tenantId") String tenantId,
			@Param("customerId") String customerId, @Param("queueCode") String queueCode);

	MessageConversation selectActiveStaffConversation(@Param("tenantId") String tenantId,
			@Param("staffPairKey") String staffPairKey);

	MessageConversation selectRecentlyClosedCustomerConversation(@Param("tenantId") String tenantId,
			@Param("customerId") String customerId, @Param("queueCode") String queueCode,
			@Param("now") LocalDateTime now);

	MessageConversation selectByIdForUpdate(@Param("tenantId") String tenantId, @Param("id") String id);

	/** 不加锁读取会话（WAITING 只读预览校验用，避免读接口持有行锁）。 */
	MessageConversation selectTenantById(@Param("tenantId") String tenantId, @Param("id") String id);

	ConversationVO selectForParticipant(@Param("tenantId") String tenantId,
			@Param("participantType") String participantType, @Param("participantId") String participantId,
			@Param("conversationId") String conversationId);

	/**
	 * 本人会话游标页。
	 *
	 * <p>排序键为 {@code COALESCE(last_message_time, create_time) DESC, id DESC}（最近活跃优先），
	 * 游标由服务端编码为 lastActiveTime + id 的复合游标，保证「无消息的新会话」不会翻页漏项。
	 *
	 * @param status OPEN（WAITING/ASSIGNED/ACTIVE）/CLOSED，缺省不过滤
	 * @param cursorTime 上一页最后一条的最近活跃时间
	 * @param cursorId 上一页最后一条的会话 ID
	 */
	List<ConversationVO> selectInbox(@Param("tenantId") String tenantId,
			@Param("participantType") String participantType, @Param("participantId") String participantId,
			@Param("status") String status, @Param("cursorTime") LocalDateTime cursorTime,
			@Param("cursorId") String cursorId, @Param("fetchSize") int fetchSize);

	/**
	 * 主管全量会话游标页：不要求调用者是参与者，含已关闭会话。
	 *
	 * <p>与 {@link #selectInbox} 使用同一套排序与复合游标语义，复用
	 * {@code ConversationInboxPageVO} 返回。
	 */
	List<ConversationVO> selectTenantInbox(@Param("tenantId") String tenantId, @Param("status") String status,
			@Param("cursorTime") LocalDateTime cursorTime, @Param("cursorId") String cursorId,
			@Param("fetchSize") int fetchSize);

	/** 本人参与、且最后一次已读之后有新消息的会话，按最新消息倒序。 */
	List<ConversationVO> selectUnreadForParticipant(@Param("tenantId") String tenantId,
			@Param("participantType") String participantType, @Param("participantId") String participantId,
			@Param("limit") int limit);

	/** 与 selectUnreadForParticipant 同口径的总数，用于管理端铃铛角标。 */
	long countUnreadForParticipant(@Param("tenantId") String tenantId,
			@Param("participantType") String participantType, @Param("participantId") String participantId);

	/** 共享池待领取会话数；分配条件与 selectWaitingPool 保持一致。 */
	long countWaiting(@Param("tenantId") String tenantId, @Param("queueCode") String queueCode);

	/** 共享池待领取列表（带会员昵称/头像快照，供工作台「待领取」页签展示）。 */
	List<ConversationVO> selectWaitingPool(@Param("tenantId") String tenantId,
			@Param("queueCode") String queueCode, @Param("limit") int limit);

	/** 跨队列的共享池待领取会话（上线回收/维护任务使用，按排队时间升序）。 */
	List<MessageConversation> selectWaitingForTenant(@Param("tenantId") String tenantId,
			@Param("limit") int limit);

	int assignWaiting(@Param("tenantId") String tenantId, @Param("conversationId") String conversationId,
			@Param("staffId") String staffId);

	/**
	 * 重开会话回 WAITING。assigned_staff_id 等字段必须显式置 NULL：
	 * updateById 默认忽略 null 字段，残留旧坐席会让 assignWaiting 的
	 * assigned_staff_id IS NULL 条件永远不成立，会话无法被领取。
	 */
	int reopenToWaiting(@Param("tenantId") String tenantId, @Param("conversationId") String conversationId,
			@Param("customerId") String customerId);

	/**
	 * 定时任务用的全租户超时扫描（XxlJob 线程没有租户上下文，需跨租户发现候选）。
	 *
	 * <p>保留历史旁路：新维护任务已改为按租户扫描 {@link #selectInactiveConversationsByTenant}，
	 * 本方法仅作为租户上下文发现阶段的兜底（已通过 TenantInterceptorBypassAuditTest 审计）。</p>
	 */
	@InterceptorIgnore(tenantLine = "true")
	List<MessageConversation> selectInactiveConversations(@Param("inactiveBefore") LocalDateTime inactiveBefore,
			@Param("limit") int limit);

	/**
	 * 按租户扫描超时无消息的会话（维护任务使用，显式带 tenantId 参数，不绕过租户拦截器）。
	 *
	 * <p>关闭条件：CUSTOMER_SERVICE 会话、状态 OPEN（WAITING/ASSIGNED/ACTIVE）、
	 * {@code COALESCE(last_message_time, create_time)} 早于阈值。
	 */
	List<MessageConversation> selectInactiveConversationsByTenant(@Param("tenantId") String tenantId,
			@Param("inactiveBefore") LocalDateTime inactiveBefore, @Param("limit") int limit);

	/**
	 * 统计某坐席当前真实在办会话数（status IN ASSIGNED/ACTIVE），用于校正
	 * message_agent.current_active_count 快照。
	 */
	long countActiveByStaff(@Param("tenantId") String tenantId, @Param("staffId") String staffId);

}
