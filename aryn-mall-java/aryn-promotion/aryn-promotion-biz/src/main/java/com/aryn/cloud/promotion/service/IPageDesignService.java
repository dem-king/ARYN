
package com.aryn.cloud.promotion.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.promotion.api.entity.PageDesign;

/**
 * 页面设计
 *
 * @author 雨滴kian
 * @date 2022/12/07
 */
public interface IPageDesignService extends IService<PageDesign> {

	/**
	 * @param request 页面设计
	 * @return
	 */
	PageDesign getHomePage(PageDesign request);

	/**
	 * 修改页面设计
	 * @param pageDesign 页面设计
	 * @return
	 */
	boolean updatePageDesignById(PageDesign pageDesign);

}
