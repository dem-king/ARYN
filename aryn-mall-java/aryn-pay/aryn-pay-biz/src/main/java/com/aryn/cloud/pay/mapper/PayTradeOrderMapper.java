
package com.aryn.cloud.pay.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aryn.cloud.pay.api.entity.PayTradeOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 支付订单
 *
 * @author 雨滴kian
 * @date 2022/6/16
 */
@Mapper
public interface PayTradeOrderMapper extends BaseMapper<PayTradeOrder> {

	int markPaidIfPending(@Param("tenantId") String tenantId, @Param("id") String id,
			@Param("channelOrderNo") String channelOrderNo, @Param("paySuccessTime") LocalDateTime paySuccessTime);

	/**
	 * 旧配置迁移时，仅当订单尚未绑定身份版本才回填，重复执行不会覆盖已冻结的身份。
	 */
	int bindRevisionIfAbsent(@Param("tenantId") String tenantId, @Param("id") String id,
			@Param("revisionId") String revisionId);

	/**
	 * 已绑定身份版本的交易笔数，用于阻止删除仍有历史交易的支付配置。
	 */
	long countByRevisionId(@Param("tenantId") String tenantId, @Param("revisionId") String revisionId);

	/**
	 * 该渠道+终端下的全部历史支付单（含尚未绑定身份版本的存量单），删除配置前必须为空。
	 */
	long countByChannelTerminal(@Param("tenantId") String tenantId, @Param("terminalType") String terminalType,
			@Param("tradeTypePrefix") String tradeTypePrefix);

	/**
	 * 抢占预支付提交权：只有 PENDING/UNKNOWN（或历史空值）能进入 SUBMITTING。
	 */
	int markSubmitting(@Param("tenantId") String tenantId, @Param("id") String id);

	int markPending(@Param("tenantId") String tenantId, @Param("id") String id);

	int markUnknown(@Param("tenantId") String tenantId, @Param("id") String id, @Param("errCode") String errCode,
			@Param("errMsg") String errMsg);

	int markClosing(@Param("tenantId") String tenantId, @Param("id") String id);

	/**
	 * 抢占关单意图：PENDING/UNKNOWN 或超时未更新的 CLOSING 才能进入 CLOSING。
	 */
	int claimClosing(@Param("tenantId") String tenantId, @Param("id") String id,
			@Param("staleBefore") LocalDateTime staleBefore);

	int markClosed(@Param("tenantId") String tenantId, @Param("id") String id);

}
