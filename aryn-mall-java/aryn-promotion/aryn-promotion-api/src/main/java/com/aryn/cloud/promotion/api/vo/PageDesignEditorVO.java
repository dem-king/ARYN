package com.aryn.cloud.promotion.api.vo;

import com.alibaba.fastjson2.JSONObject;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Schema(description = "页面装修编辑器数据")
public class PageDesignEditorVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private String id;

	private String pageName;

	@Schema(description = "页面类型：0.微页面；1.首页；2.商品详情页；3.分类页；4.个人中心页；")
	private String pageType;

	private JSONObject pageContent;

	private Long draftRevision;

	private Integer schemaVersion;

	private String publishedVersionId;

	private String publishedStatus;

	private LocalDateTime publishedAt;

	private LocalDateTime updateTime;

}
