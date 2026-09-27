
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
	 * 将已发布的页面设为租户线上首页。
	 * <p>
	 * 移动端首页固定读取 {@code page_type=1 AND home_status=1 AND published_status=1} 的记录，
	 * 因此这里必须同时翻转目标页面与旧首页的两个标记，否则发布微页面不会改变 C 端首页。
	 * @param pageId 目标页面ID，须已有线上发布版本
	 * @return 是否切换成功（目标已是首页时视为成功）
	 */
	boolean setAsHome(String pageId);


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

	/**
	 * 新建页面装修，补齐 NOT NULL 的初始装修文档与发布元数据。
	 * @param pageDesign 前端提交的页面基本信息
	 * @return 已落库的页面
	 */
	PageDesign createPage(PageDesign pageDesign);

}
