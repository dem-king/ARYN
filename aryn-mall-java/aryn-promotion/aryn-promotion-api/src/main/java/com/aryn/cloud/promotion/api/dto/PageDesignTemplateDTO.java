package com.aryn.cloud.promotion.api.dto;

import com.alibaba.fastjson2.JSONObject;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 页面装修模板保存请求。
 * <p>
 * 模板内容与草稿一致以 JSON 对象在 HTTP 层传输，落库时序列化为字符串，
 * 避免实体 String 字段直接接收 JSON 对象导致的请求体解析失败。
 *
 * @author 雨滴kian
 * @date 2026/09/22
 */
@Data
@Schema(description = "页面装修模板保存请求")
public class PageDesignTemplateDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "主键")
	private String id;

	@NotBlank(message = "模板名称不能为空")
	@Schema(description = "模板名称")
	private String templateName;

	@Schema(description = "模板类型：0.页面；1.组件组合；")
	private String templateType;

	@Schema(description = "适用页面类型：0.微页面；1.首页；2.通用；")
	private String pageType;

	@NotNull(message = "模板内容不能为空")
	@Schema(description = "模板内容")
	private JSONObject templateContent;

	@Schema(description = "装修协议版本")
	private Integer schemaVersion;

	@Schema(description = "行业标签（行业模板筛选用，通用为空）")
	private String industryTag;

	@Schema(description = "市场状态：0.未上架；1.已上架；2.已下架；（保存接口不生效，上架/下架走专用接口）")
	private String marketStatus;

	@Schema(description = "状态：0.正常；1.停用；")
	private String status;

	@Schema(description = "排序")
	private Integer sort;

}
