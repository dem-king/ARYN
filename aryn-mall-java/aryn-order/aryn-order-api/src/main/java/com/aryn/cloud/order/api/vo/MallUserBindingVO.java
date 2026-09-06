
package com.aryn.cloud.order.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 可绑定商城用户搜索结果（配送员绑定用）
 *
 * @author aryn
 * @since 2026/9/5
 */
@Data
@Schema(description = "可绑定商城用户搜索结果")
public class MallUserBindingVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "商城用户ID")
	private String mallUserId;

	@Schema(description = "商城用户昵称")
	private String nickname;

	@Schema(description = "手机号（脱敏）")
	private String phone;

	@Schema(description = "绑定状态：unbound未绑定 bound已绑定")
	private String bindingStatus;

	@Schema(description = "已绑定的配送员姓名（冲突提示用）")
	private String boundStaffName;

}
