
package com.aryn.cloud.order.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 共享购物车成员展示姓名设置请求。
 *
 * <p>成员加入共享购物车时填写一次，作为该成员在本轮采购中的展示名，
 * 下单时结转进订单明细的贡献者姓名快照，供仓库/司机打印配送到人标签。
 *
 * @author aryn
 * @since 2026/9/20
 */
@Data
@Schema(description = "共享购物车成员姓名设置请求")
public class SharedCartMemberNameDTO {

	@Schema(description = "成员展示姓名")
	@NotBlank(message = "姓名不能为空")
	@Size(max = 64, message = "姓名长度不能超过64")
	private String displayName;

}
