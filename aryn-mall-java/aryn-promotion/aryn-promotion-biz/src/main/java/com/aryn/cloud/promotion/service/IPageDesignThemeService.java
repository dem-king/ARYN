package com.aryn.cloud.promotion.service;

import com.aryn.cloud.promotion.api.entity.PageDesignTheme;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 页面装修主题服务
 *
 * @author 雨滴kian
 * @date 2026/09/05
 */
public interface IPageDesignThemeService extends IService<PageDesignTheme> {

	/**
	 * 当前租户主题列表（含系统主题，按 sort、创建时间排序）。
	 */
	List<PageDesignTheme> listThemes();

	/**
	 * 新建租户主题。
	 */
	PageDesignTheme createTheme(PageDesignTheme theme);

	/**
	 * 更新主题；系统主题不允许修改。
	 */
	boolean updateTheme(PageDesignTheme theme);

	/**
	 * 删除主题；系统主题不允许删除，被页面引用时由发布校验兜底。
	 */
	boolean deleteTheme(String id);

	/**
	 * 校验页面内容中的 themeRef 引用可用，不可用时抛出可解释的业务异常。
	 */
	void assertThemeUsable(String pageContent);

	/**
	 * 将页面内容中的 themeRef 固化为 themeSnapshot 并返回新内容字符串：
	 * 已包含快照的内容原样返回（回滚重发场景）；v2 内容或未引用主题时原样返回；
	 * 引用不存在时抛出业务异常。
	 */
	String embedThemeSnapshot(String pageContent);

}
