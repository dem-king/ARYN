package com.aryn.cloud.promotion.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 页面装修素材引用检查结果。
 * <p>
 * 引用在检查时从草稿内容实时提取，不落库；
 * 可达性与真实字节数需要外网出口和 SSRF 防护，属后续阶段能力。
 *
 * @author 雨滴kian
 * @date 2026/09/05
 */
@Data
@Schema(description = "页面装修素材引用检查结果")
public class PageDesignAssetVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "页面ID")
	private String pageDesignId;

	@Schema(description = "素材引用列表")
	private List<AssetRef> assets = new ArrayList<>();

	@Schema(description = "图片引用数")
	private int imageCount;

	@Schema(description = "视频引用数")
	private int videoCount;

	@Schema(description = "不安全 http:// 引用数")
	private int insecureCount;

	@Data
	@Schema(description = "素材引用项")
	public static class AssetRef implements Serializable {

		@Serial
		private static final long serialVersionUID = 1L;

		@Schema(description = "组件ID")
		private String componentId;

		@Schema(description = "组件类型")
		private String componentType;

		@Schema(description = "素材地址")
		private String url;

		@Schema(description = "素材类型：image/video")
		private String mediaType;

		@Schema(description = "是否外链")
		private boolean external;

		@Schema(description = "是否非 https 链接")
		private boolean insecure;

	}

}
