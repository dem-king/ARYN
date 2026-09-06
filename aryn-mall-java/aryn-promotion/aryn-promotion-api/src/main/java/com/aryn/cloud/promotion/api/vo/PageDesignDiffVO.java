package com.aryn.cloud.promotion.api.vo;

import com.alibaba.fastjson2.JSONObject;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 页面装修版本差异（组件级结构化 diff）。
 *
 * @author 雨滴kian
 * @date 2026/09/05
 */
@Data
@Schema(description = "页面装修版本差异")
public class PageDesignDiffVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "页面ID")
	private String pageDesignId;

	@Schema(description = "起始版本ID")
	private String fromVersionId;

	@Schema(description = "起始版本号")
	private Integer fromVersionNo;

	@Schema(description = "目标版本ID")
	private String toVersionId;

	@Schema(description = "目标版本号")
	private Integer toVersionNo;

	@Schema(description = "起始协议版本")
	private Integer fromSchemaVersion;

	@Schema(description = "目标协议版本")
	private Integer toSchemaVersion;

	@Schema(description = "新增组件")
	private List<ComponentChange> added = new ArrayList<>();

	@Schema(description = "移除组件")
	private List<ComponentChange> removed = new ArrayList<>();

	@Schema(description = "修改组件")
	private List<ComponentUpdate> changed = new ArrayList<>();

	@Schema(description = "页面设置变更")
	private List<FieldChange> pageChanged = new ArrayList<>();

	@Data
	@Schema(description = "组件增删项")
	public static class ComponentChange implements Serializable {

		@Serial
		private static final long serialVersionUID = 1L;

		@Schema(description = "组件ID")
		private String componentId;

		@Schema(description = "组件类型")
		private String componentType;

	}

	@Data
	@Schema(description = "组件修改项")
	public static class ComponentUpdate implements Serializable {

		@Serial
		private static final long serialVersionUID = 1L;

		@Schema(description = "组件ID")
		private String componentId;

		@Schema(description = "组件类型")
		private String componentType;

		@Schema(description = "字段级变更")
		private List<FieldChange> changes = new ArrayList<>();

	}

	@Data
	@Schema(description = "字段级变更")
	public static class FieldChange implements Serializable {

		@Serial
		private static final long serialVersionUID = 1L;

		@Schema(description = "字段名")
		private String field;

		@Schema(description = "变更前")
		private Object from;

		@Schema(description = "变更后")
		private Object to;

	}

}
