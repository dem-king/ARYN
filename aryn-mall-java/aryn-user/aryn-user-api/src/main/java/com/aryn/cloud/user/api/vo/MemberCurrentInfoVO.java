package com.aryn.cloud.user.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * C端当前登录用户会员等级与标签信息（用于装修条件渲染）
 *
 * @author 雨滴kian
 */
@Data
@Schema(description = "当前登录用户会员等级与标签")
public class MemberCurrentInfoVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "会员等级ID（未配置等级时为空）")
	private String levelId;

	@Schema(description = "会员等级名称（未配置等级时为空）")
	private String levelName;

	@Schema(description = "用户标签列表")
	private List<TagItem> tags = new ArrayList<>();

	@Data
	@Schema(description = "用户标签项")
	public static class TagItem implements Serializable {

		@Serial
		private static final long serialVersionUID = 1L;

		@Schema(description = "标签ID")
		private String tagId;

		@Schema(description = "标签名称")
		private String tagName;

	}

}
