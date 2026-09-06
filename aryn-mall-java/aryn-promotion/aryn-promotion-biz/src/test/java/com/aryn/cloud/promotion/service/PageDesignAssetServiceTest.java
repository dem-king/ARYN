package com.aryn.cloud.promotion.service;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.entity.PageDesign;
import com.aryn.cloud.promotion.api.vo.PageDesignAssetVO;
import com.aryn.cloud.promotion.mapper.PageDesignMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PageDesignAssetServiceTest {

	@Mock
	private PageDesignMapper pageDesignMapper;

	private PageDesignAssetService service;

	@BeforeEach
	void setUp() {
		service = new PageDesignAssetService(pageDesignMapper);
	}

	@Test
	void checkCollectsImageAndVideoRefsFromV3Sections() {
		PageDesign page = page("""
				{"schemaVersion":3,"page":{},"sections":[
				  {"id":"s1","type":"default","components":[
				    {"id":"c1","type":"swiper-banner","version":1,"props":{"items":[
				      {"id":"i1","picUrl":"https://cdn.example.com/a.png"},
				      {"id":"i2","picUrl":"https://cdn.example.com/b.webp"}]}},
				    {"id":"c2","type":"video-player","version":1,"props":{"videoUrl":"https://cdn.example.com/v.mp4"}}]}]}
				""");
		when(pageDesignMapper.selectById("page-1")).thenReturn(page);

		PageDesignAssetVO result = service.check("page-1");

		assertEquals(2, result.getImageCount());
		assertEquals(1, result.getVideoCount());
		assertEquals(0, result.getInsecureCount());
		assertTrue(result.getAssets().stream().anyMatch(ref -> "video".equals(ref.getMediaType())));
		assertTrue(result.getAssets().stream().allMatch(PageDesignAssetVO.AssetRef::isExternal));
	}

	@Test
	void checkDeduplicatesSameUrlAcrossComponentsAndFlagsInsecureLinks() {
		PageDesign page = page("""
				{"schemaVersion":2,"components":[
				  {"id":"c1","type":"image-ad","version":1,"props":{"picUrl":"http://cdn.example.com/a.png"}},
				  {"id":"c2","type":"notice","version":1,"props":{"bgPicUrl":"http://cdn.example.com/a.png"}}]}
				""");
		when(pageDesignMapper.selectById("page-1")).thenReturn(page);

		PageDesignAssetVO result = service.check("page-1");

		assertEquals(1, result.getAssets().size());
		assertEquals(1, result.getInsecureCount());
		assertTrue(result.getAssets().get(0).isExternal());
		assertTrue(result.getAssets().get(0).isInsecure());
		assertEquals("image-ad", result.getAssets().get(0).getComponentType());
	}

	@Test
	void checkRejectsMissingPageForTenant() {
		when(pageDesignMapper.selectById("page-1")).thenReturn(null);

		ArynBusinessException error = assertThrows(ArynBusinessException.class, () -> service.check("page-1"));

		assertEquals("页面不存在或无权访问", error.getMsg());
	}

	private PageDesign page(String content) {
		PageDesign page = new PageDesign();
		page.setId("page-1");
		page.setPageName("素材检查页");
		page.setPageContent(content);
		return page;
	}

}
