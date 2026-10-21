package com.aryn.cloud.vessel.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 管理端解绑成员入参。
 *
 * <p>存在的理由：审核通过后此前没有任何管理端解绑入口，误绑只能让用户自己去 C 端退出，
 * 而用户往往正是不会操作的那个人。解绑走逻辑删除，被解绑的人之后仍可被重新拉进来
 * （成员关系复活由 {@code VesselMemberRegistrar} 统一处理）。
 *
 * @author aryn
 * @since 2026/10/16
 */
@Data
@Schema(description = "船舶成员解绑DTO")
public class VesselUnbindDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "船舶ID")
	private String vesselId;

	@Schema(description = "要解绑的商城用户ID")
	private String userId;

	@Schema(description = "解绑原因（会写入记录并随通知告知本人）")
	private String reason;

}
