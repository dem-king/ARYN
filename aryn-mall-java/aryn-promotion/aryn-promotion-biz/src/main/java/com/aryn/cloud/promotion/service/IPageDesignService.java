
package com.aryn.cloud.promotion.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.promotion.api.dto.PageDesignDraftDTO;
import com.aryn.cloud.promotion.api.entity.PageDesign;
import com.aryn.cloud.promotion.api.vo.PageDesignEditorVO;

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
	 * 获取或初始化当前租户的首页装修数据
	 * @return 首页装修数据
	 */
	PageDesign getOrCreateHomePage();

	/**
	 * 修改页面设计
	 * @param pageDesign 页面设计
	 * @return
	 */
	boolean updatePageDesignById(PageDesign pageDesign);

	/**
	 * 使用乐观锁保存页面装修草稿。
	 * @param draft 草稿内容和客户端修订号
	 * @return 保存后的修订号
	 */
	long saveDraft(PageDesignDraftDTO draft);

	/**
	 * 获取页面装修编辑数据。
	 * @param id 页面ID
	 * @return 编辑器数据
	 */
	PageDesignEditorVO getEditor(String id);

	/**
	 * 复制页面装修为新的未发布微页面草稿。
	 * @param id 来源页面ID
	 * @return 新页面
	 */
	PageDesign copyPage(String id);

}
