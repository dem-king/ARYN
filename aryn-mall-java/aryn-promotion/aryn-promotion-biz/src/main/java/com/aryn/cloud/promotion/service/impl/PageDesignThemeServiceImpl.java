package com.aryn.cloud.promotion.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.entity.PageDesignTheme;
import com.aryn.cloud.promotion.mapper.PageDesignThemeMapper;
import com.aryn.cloud.promotion.service.IPageDesignThemeService;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 页面装修主题服务实现
 *
 * @author 雨滴kian
 * @date 2026/09/05
 */
@Service
public class PageDesignThemeServiceImpl extends ServiceImpl<PageDesignThemeMapper, PageDesignTheme>
		implements IPageDesignThemeService {

	private static final int DEFAULT_RADIUS = 8;

	@Override
	public List<PageDesignTheme> listThemes() {
		return list(Wrappers.<PageDesignTheme>lambdaQuery()
			.eq(PageDesignTheme::getStatus, PageDesignTheme.STATUS_ENABLED)
			.orderByAsc(PageDesignTheme::getSort)
			.orderByDesc(PageDesignTheme::getCreateTime));
	}

	@Override
	public PageDesignTheme createTheme(PageDesignTheme theme) {
		theme.setId(IdWorker.getIdStr());
		theme.setSystemFlag(PageDesignTheme.SYSTEM_NO);
		theme.setStatus(PageDesignTheme.STATUS_ENABLED);
		if (theme.getRadius() == null) {
			theme.setRadius(DEFAULT_RADIUS);
		}
		if (theme.getSort() == null) {
			theme.setSort(0);
		}
		save(theme);
		return theme;
	}

	@Override
	public boolean updateTheme(PageDesignTheme theme) {
		PageDesignTheme existing = requireTheme(theme.getId());
		if (PageDesignTheme.SYSTEM_YES.equals(existing.getSystemFlag())) {
			throw new ArynBusinessException("系统主题不允许修改");
		}
		theme.setSystemFlag(null);
		theme.setTenantId(null);
		return updateById(theme);
	}

	@Override
	public boolean deleteTheme(String id) {
		PageDesignTheme existing = requireTheme(id);
		if (PageDesignTheme.SYSTEM_YES.equals(existing.getSystemFlag())) {
			throw new ArynBusinessException("系统主题不允许删除");
		}
		return removeById(id);
	}

	@Override
	public void assertThemeUsable(String pageContent) {
		String themeRef = parseThemeRef(pageContent);
		if (themeRef != null && requireThemeOrNull(themeRef) == null) {
			throw new ArynBusinessException("页面引用的主题不存在或无权访问，请重新选择主题");
		}
	}

	@Override
	public String embedThemeSnapshot(String pageContent) {
		JSONObject document = parseDocument(pageContent);
		if (document == null || document.getIntValue("schemaVersion") != 3) {
			return pageContent;
		}
		if (document.containsKey("themeSnapshot")) {
			// 回滚重发历史快照时保持固化的主题，不随当前主题漂移
			return pageContent;
		}
		String themeRef = document.getString("themeRef");
		if (!StringUtils.hasText(themeRef)) {
			return pageContent;
		}
		PageDesignTheme theme = requireThemeOrNull(themeRef);
		if (theme == null) {
			throw new ArynBusinessException("页面引用的主题不存在或无权访问，请重新选择主题");
		}
		document.put("themeSnapshot", buildSnapshot(theme));
		return document.toJSONString();
	}

	private JSONObject buildSnapshot(PageDesignTheme theme) {
		JSONObject snapshot = new JSONObject();
		snapshot.put("themeId", theme.getId());
		snapshot.put("themeName", theme.getThemeName());
		snapshot.put("primaryColor", theme.getPrimaryColor());
		snapshot.put("pageBackgroundColor", theme.getPageBackgroundColor());
		snapshot.put("navigationColor", theme.getNavigationColor());
		snapshot.put("navigationTextColor", theme.getNavigationTextColor());
		snapshot.put("radius", theme.getRadius());
		return snapshot;
	}

	private String parseThemeRef(String pageContent) {
		JSONObject document = parseDocument(pageContent);
		return document == null ? null : document.getString("themeRef");
	}

	private JSONObject parseDocument(String pageContent) {
		if (!StringUtils.hasText(pageContent)) {
			return null;
		}
		try {
			return JSON.parseObject(pageContent);
		}
		catch (RuntimeException exception) {
			return null;
		}
	}

	private PageDesignTheme requireThemeOrNull(String id) {
		return getById(id);
	}

	private PageDesignTheme requireTheme(String id) {
		PageDesignTheme theme = getById(id);
		if (theme == null) {
			throw new ArynBusinessException("主题不存在或无权访问");
		}
		return theme;
	}

}
