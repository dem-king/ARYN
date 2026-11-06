package com.aryn.cloud.promotion.api.vo;

import com.aryn.cloud.promotion.api.entity.SalesTransferReward;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 客户转交奖励视图
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Data
@Schema(description = "客户转交奖励视图")
@EqualsAndHashCode(callSuper = true)
public class SalesTransferRewardVO extends SalesTransferReward {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "客户昵称")
	private String nickname;

	@Schema(description = "原分销员昵称")
	private String distributorNickname;

}
