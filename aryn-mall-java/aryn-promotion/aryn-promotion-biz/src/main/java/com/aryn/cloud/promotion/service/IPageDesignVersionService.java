package com.aryn.cloud.promotion.service;

import com.aryn.cloud.promotion.api.dto.PageDesignPublishDTO;
import com.aryn.cloud.promotion.api.entity.PageDesign;
import com.aryn.cloud.promotion.api.entity.PageDesignVersion;
import com.aryn.cloud.promotion.api.vo.PageDesignVersionVO;

import java.util.List;

public interface IPageDesignVersionService {

	PageDesignVersion publish(PageDesignPublishDTO request);

	/**
	 * 基于指定内容快照创建不可变发布版本并切换线上指针，
	 * 供发布申请审批通过后调用；expectedDraftRevision 非空时做乐观并发校验。
	 */
	PageDesignVersion publishSnapshot(PageDesign page, String pageName, String pageType, String pageContent,
			Integer schemaVersion, String publishRemark, Long expectedDraftRevision);

	/**
	 * 仅创建不可变版本，不切换线上稳定指针；供灰度发布使用。
	 */
	PageDesignVersion createVersionSnapshot(PageDesign page, String pageName, String pageType, String pageContent,
			Integer schemaVersion, String publishRemark);

	PageDesignVersion rollback(String pageId, String versionId, String publishRemark);

	boolean unpublish(String pageId);

	List<PageDesignVersionVO> listVersions(String pageId);

}
