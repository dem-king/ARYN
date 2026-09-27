package com.aryn.cloud.promotion.api.vo;

import com.alibaba.fastjson2.JSONObject;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Schema(description = "Published or preview page decoration")
public class AppPageDesignVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private String id;
	private String pageName;
	@Schema(description = "页面类型：0.微页面；1.首页；2.商品详情页；3.分类页；4.个人中心页；")
	private String pageType;
	private Integer schemaVersion;
	private JSONObject pageContent;
	private Long draftRevision;
	private String publishedVersionId;
	private Integer publishedVersionNo;
	private LocalDateTime publishedAt;

}
