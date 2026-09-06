package com.aryn.cloud.promotion.service.impl;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.entity.PageDesignTheme;
import com.aryn.cloud.promotion.mapper.PageDesignThemeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PageDesignThemeServiceImplTest {

	@Mock
	private PageDesignThemeMapper themeMapper;

	private PageDesignThemeServiceImpl service;

	@BeforeEach
	void setUp() {
		service = new TestPageDesignThemeService(themeMapper);
	}

	@Test
	void createThemeAssignsIdentityAndDefaults() {
		PageDesignTheme theme = new PageDesignTheme();
		theme.setThemeName("夏日主题");
		theme.setPrimaryColor("#ff5500");
		when(themeMapper.insert(any(PageDesignTheme.class))).thenReturn(1);

		PageDesignTheme created = service.createTheme(theme);

		assertNotNull(created.getId());
		assertEquals(PageDesignTheme.SYSTEM_NO, created.getSystemFlag());
		assertEquals(PageDesignTheme.STATUS_ENABLED, created.getStatus());
		assertEquals(8, created.getRadius());
		assertEquals(0, created.getSort());
	}

	@Test
	void updateRejectsSystemTheme() {
		when(themeMapper.selectById("theme-1")).thenReturn(systemTheme());

		PageDesignTheme update = new PageDesignTheme();
		update.setId("theme-1");

		ArynBusinessException error = assertThrows(ArynBusinessException.class, () -> service.updateTheme(update));

		assertTrue(error.getMsg().contains("系统主题不允许修改"));
	}

	@Test
	void deleteRejectsSystemTheme() {
		when(themeMapper.selectById("theme-1")).thenReturn(systemTheme());

		ArynBusinessException error = assertThrows(ArynBusinessException.class, () -> service.deleteTheme("theme-1"));

		assertTrue(error.getMsg().contains("系统主题不允许删除"));
	}

	@Test
	void embedSnapshotResolvesThemeRefForV3Document() {
		PageDesignTheme theme = tenantTheme();
		when(themeMapper.selectById("theme-9")).thenReturn(theme);

		String content = "{\"schemaVersion\":3,\"themeRef\":\"theme-9\",\"sections\":[],\"components\":[]}";
		String embedded = service.embedThemeSnapshot(content);

		assertTrue(embedded.contains("\"themeSnapshot\""));
		assertTrue(embedded.contains("\"themeId\":\"theme-9\""));
		assertTrue(embedded.contains("\"primaryColor\":\"#ff5500\""));
	}

	@Test
	void embedSnapshotKeepsExistingSnapshotForRollback() {
		String content = "{\"schemaVersion\":3,\"themeRef\":\"theme-9\",\"themeSnapshot\":{\"themeId\":\"theme-1\"},"
				+ "\"sections\":[],\"components\":[]}";

		String result = service.embedThemeSnapshot(content);

		assertSame(content, result);
		verify(themeMapper, never()).selectById(any());
	}

	@Test
	void embedSnapshotIgnoresV2ContentWithoutThemeRef() {
		String content = "{\"schemaVersion\":2,\"components\":[]}";

		assertSame(content, service.embedThemeSnapshot(content));
		verify(themeMapper, never()).selectById(any());
	}

	@Test
	void embedSnapshotThrowsWhenThemeMissing() {
		when(themeMapper.selectById("theme-404")).thenReturn(null);
		String content = "{\"schemaVersion\":3,\"themeRef\":\"theme-404\",\"sections\":[],\"components\":[]}";

		ArynBusinessException error = assertThrows(ArynBusinessException.class,
				() -> service.embedThemeSnapshot(content));

		assertTrue(error.getMsg().contains("主题不存在"));
	}

	@Test
	void assertThemeUsableThrowsWhenReferencedThemeMissing() {
		when(themeMapper.selectById("theme-404")).thenReturn(null);

		ArynBusinessException error = assertThrows(ArynBusinessException.class,
				() -> service.assertThemeUsable(
						"{\"schemaVersion\":3,\"themeRef\":\"theme-404\",\"sections\":[],\"components\":[]}"));

		assertTrue(error.getMsg().contains("主题不存在"));
	}

	@Test
	void assertThemeUsablePassesForV2OrUnthemedContent() {
		service.assertThemeUsable("{\"schemaVersion\":2,\"components\":[]}");
		service.assertThemeUsable("{\"schemaVersion\":3,\"sections\":[],\"components\":[]}");
		verify(themeMapper, never()).selectById(any());
	}

	@Test
	void listThemesQueriesEnabledThemesOnly() {
		when(themeMapper.selectList(any())).thenReturn(List.of(tenantTheme()));

		List<PageDesignTheme> themes = service.listThemes();

		assertEquals(1, themes.size());
		ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.Wrapper<PageDesignTheme>> captor = ArgumentCaptor
			.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
		verify(themeMapper).selectList(captor.capture());
		assertNotNull(captor.getValue());
	}

	private PageDesignTheme tenantTheme() {
		PageDesignTheme theme = new PageDesignTheme();
		theme.setId("theme-9");
		theme.setThemeName("大促红");
		theme.setPrimaryColor("#ff5500");
		theme.setPageBackgroundColor("#fff7f2");
		theme.setNavigationColor("#ffffff");
		theme.setNavigationTextColor("#222222");
		theme.setRadius(12);
		theme.setSystemFlag(PageDesignTheme.SYSTEM_NO);
		theme.setStatus(PageDesignTheme.STATUS_ENABLED);
		return theme;
	}

	private PageDesignTheme systemTheme() {
		PageDesignTheme theme = tenantTheme();
		theme.setId("theme-1");
		theme.setSystemFlag(PageDesignTheme.SYSTEM_YES);
		return theme;
	}

	private static final class TestPageDesignThemeService extends PageDesignThemeServiceImpl {

		private TestPageDesignThemeService(PageDesignThemeMapper themeMapper) {
			this.baseMapper = themeMapper;
		}
	}

}
