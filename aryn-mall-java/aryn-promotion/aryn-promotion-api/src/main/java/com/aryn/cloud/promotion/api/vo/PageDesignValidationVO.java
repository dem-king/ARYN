package com.aryn.cloud.promotion.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 页面装修结构化校验结果。
 * <p>
 * 发布前检查输出：错误（阻断发布）、警告（可配置阻断）、业务引用清单和性能预算。
 *
 * @author 雨滴kian
 * @date 2026/09/05
 */
@Data
@Schema(description = "页面装修结构化校验结果")
public class PageDesignValidationVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "阻断性错误")
	private List<Issue> errors = new ArrayList<>();

	@Schema(description = "警告项")
	private List<Issue> warnings = new ArrayList<>();

	@Schema(description = "页面引用的业务对象")
	private References references = new References();

	@Schema(description = "性能预算")
	private Budget performance = new Budget();

	@Schema(description = "是否存在阻断性错误")
	public boolean hasErrors() {
		return !errors.isEmpty();
	}

	@Data
	@Schema(description = "校验问题项")
	public static class Issue implements Serializable {

		@Serial
		private static final long serialVersionUID = 1L;

		@Schema(description = "问题码")
		private String code;

		@Schema(description = "问题描述")
		private String message;

		@Schema(description = "关联组件ID")
		private String componentId;

		@Schema(description = "关联组件类型")
		private String componentType;

		@Schema(description = "关联字段路径")
		private String field;

	}

	@Data
	@Schema(description = "页面业务引用")
	public static class References implements Serializable {

		@Serial
		private static final long serialVersionUID = 1L;

		@Schema(description = "引用的商品ID")
		private Set<String> goodsIds = new LinkedHashSet<>();

		@Schema(description = "引用的优惠券ID")
		private Set<String> couponIds = new LinkedHashSet<>();

		@Schema(description = "引用的活动ID")
		private Set<String> activityIds = new LinkedHashSet<>();

		@Schema(description = "引用的分类ID")
		private Set<String> categoryIds = new LinkedHashSet<>();

	}

	@Data
	@Schema(description = "性能预算")
	public static class Budget implements Serializable {

		@Serial
		private static final long serialVersionUID = 1L;

		@Schema(description = "组件数量")
		private int componentCount;

		@Schema(description = "图片数量")
		private int imageCount;

		@Schema(description = "预估接口请求数")
		private int requestCount;

		@Schema(description = "页面内容字节数")
		private long contentBytes;

	}

}
