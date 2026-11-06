package com.aryn.cloud.promotion.api.vo;

import com.aryn.cloud.promotion.api.entity.SalesStaff;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 销售员视图
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Data
@Schema(description = "销售员视图")
@EqualsAndHashCode(callSuper = true)
public class SalesStaffVO extends SalesStaff {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "名下客户数（仅分页列表回填）")
	private Long customerCount;

	@Schema(description = "销售本人会员昵称（分页/详情回填，远程失败为空）")
	private String memberNickname;

	@Schema(description = "销售本人会员头像（分页/详情回填，远程失败为空）")
	private String memberAvatar;

}
