package com.aryn.cloud.promotion.service;

import com.aryn.cloud.promotion.api.dto.PageDesignPublishDTO;
import com.aryn.cloud.promotion.api.entity.PageDesignVersion;
import com.aryn.cloud.promotion.api.vo.PageDesignVersionVO;

import java.util.List;

public interface IPageDesignVersionService {

	PageDesignVersion publish(PageDesignPublishDTO request);

	PageDesignVersion rollback(String pageId, String versionId, String publishRemark);

	boolean unpublish(String pageId);

	List<PageDesignVersionVO> listVersions(String pageId);

}
