package com.aryn.cloud.promotion.api.vo;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.aryn.cloud.promotion.api.entity.PageDesignTemplate;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 页面装修模板视图对象。
 * <p>
 * 与请求体对称，模板内容以 JSON 对象返回，前端无需二次解析字符串。
 *
 * @author 雨滴kian
 * @date 2026/09/22
 */
@Data
@Schema(description = "页面装修模板")
public class PageDesignTemplateVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "主键")
	private String id;

	@Schema(description = "模板名称")
	private String templateName;

	@Schema(description = "模板类型：0.页面；1.组件组合；")
	private String templateType;

	@Schema(description = "适用页面类型：0.微页面；1.首页；2.通用；")
	private String pageType;

	@Schema(description = "模板内容")
	private JSONObject templateContent;

	@Schema(description = "装修协议版本")
	private Integer schemaVersion;

	@Schema(description = "系统模板：0.否；1.是；")
	private String systemFlag;

	@Schema(description = "行业标签（行业模板筛选用，通用为空）")
	private String industryTag;

	@Schema(description = "市场状态：0.未上架；1.已上架；2.已下架；")
	private String marketStatus;

	@Schema(description = "下载量")
	private Integer downloadCount;

	@Schema(description = "状态：0.正常；1.停用；")
	private String status;

	@Schema(description = "排序")
	private Integer sort;

	@Schema(description = "创建人租户ID（市场列表展示来源租户用）")
	private String tenantId;

	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	@Schema(description = "更新时间")
	private LocalDateTime updateTime;

	public static PageDesignTemplateVO from(PageDesignTemplate template) {
		PageDesignTemplateVO vo = new PageDesignTemplateVO();
		vo.setId(template.getId());
		vo.setTemplateName(template.getTemplateName());
		vo.setTemplateType(template.getTemplateType());
		vo.setPageType(template.getPageType());
		vo.setTemplateContent(parseContent(template.getTemplateContent()));
		vo.setSchemaVersion(template.getSchemaVersion());
		vo.setSystemFlag(template.getSystemFlag());
		vo.setIndustryTag(template.getIndustryTag());
		vo.setMarketStatus(template.getMarketStatus());
		vo.setDownloadCount(template.getDownloadCount());
		vo.setStatus(template.getStatus());
		vo.setSort(template.getSort());
		vo.setTenantId(template.getTenantId());
		vo.setCreateTime(template.getCreateTime());
		vo.setUpdateTime(template.getUpdateTime());
		return vo;
	}

	/**
	 * 市场列表轻量视图：不返回完整 templateContent（长文本），仅回显列表所需字段。
	 */
	public static PageDesignTemplateVO fromMarketList(PageDesignTemplate template) {
		PageDesignTemplateVO vo = new PageDesignTemplateVO();
		vo.setId(template.getId());
		vo.setTemplateName(template.getTemplateName());
		vo.setTemplateType(template.getTemplateType());
		vo.setPageType(template.getPageType());
		vo.setTemplateContent(null);
		vo.setIndustryTag(template.getIndustryTag());
		vo.setMarketStatus(template.getMarketStatus());
		vo.setDownloadCount(template.getDownloadCount());
		vo.setTenantId(template.getTenantId());
		vo.setCreateTime(template.getCreateTime());
		return vo;
	}

	private static JSONObject parseContent(String content) {
		if (content == null || content.isBlank()) {
			return new JSONObject();
		}
		try {
			return JSON.parseObject(content);
		}
		catch (RuntimeException exception) {
			// 历史脏数据不阻断模板列表，降级为空文档由前端迁移逻辑兜底
			return new JSONObject();
		}
	}

}
