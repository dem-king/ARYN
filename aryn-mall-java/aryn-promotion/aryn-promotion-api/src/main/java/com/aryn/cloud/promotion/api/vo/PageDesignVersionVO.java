package com.aryn.cloud.promotion.api.vo;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.aryn.cloud.promotion.api.entity.PageDesignVersion;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Schema(description = "页面装修发布版本")
public class PageDesignVersionVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private String id;

	private String pageDesignId;

	private Integer versionNo;

	private Integer schemaVersion;

	private String pageName;

	private String pageType;

	private JSONObject pageContent;

	private String publishRemark;

	private String publishBy;

	private LocalDateTime publishedAt;

	public static PageDesignVersionVO from(PageDesignVersion version) {
		PageDesignVersionVO vo = new PageDesignVersionVO();
		vo.setId(version.getId());
		vo.setPageDesignId(version.getPageDesignId());
		vo.setVersionNo(version.getVersionNo());
		vo.setSchemaVersion(version.getSchemaVersion());
		vo.setPageName(version.getPageName());
		vo.setPageType(version.getPageType());
		vo.setPageContent(JSON.parseObject(version.getPageContent()));
		vo.setPublishRemark(version.getPublishRemark());
		vo.setPublishBy(version.getPublishBy());
		vo.setPublishedAt(version.getPublishedAt());
		return vo;
	}

}
