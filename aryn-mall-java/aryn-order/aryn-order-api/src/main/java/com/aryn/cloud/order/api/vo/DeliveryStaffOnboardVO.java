
package com.aryn.cloud.order.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 向导式创建配送员结果摘要
 *
 * @author aryn
 * @since 2026/9/5
 */
@Data
@Schema(description = "向导式创建配送员结果摘要")
public class DeliveryStaffOnboardVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "配送员ID")
	private String staffId;

	@Schema(description = "员工账号ID")
	private String userId;

	@Schema(description = "绑定结果：BOUND已绑定 UNBOUND未绑定（含选择暂不绑定）")
	private String bindingStatus;

	@Schema(description = "资格结果：GRANTED已开通 PROCESSING开通处理中（后台自动重试） NOT_GRANTED未申请 GRANT_FAILED开通失败")
	private String qualificationStatus;

	@Schema(description = "资格开通说明（PROCESSING/GRANT_FAILED 时返回）")
	private String qualificationMessage;

}
