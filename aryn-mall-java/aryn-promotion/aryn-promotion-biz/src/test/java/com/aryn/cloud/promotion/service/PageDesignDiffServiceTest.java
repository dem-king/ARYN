package com.aryn.cloud.promotion.service;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.entity.PageDesign;
import com.aryn.cloud.promotion.api.entity.PageDesignVersion;
import com.aryn.cloud.promotion.api.vo.PageDesignDiffVO;
import com.aryn.cloud.promotion.mapper.PageDesignMapper;
import com.aryn.cloud.promotion.mapper.PageDesignVersionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PageDesignDiffServiceTest {

	@Mock
	private PageDesignMapper pageDesignMapper;

	@Mock
	private PageDesignVersionMapper versionMapper;

	private PageDesignDiffService service;

	@BeforeEach
	void setUp() {
		service = new PageDesignDiffService(pageDesignMapper, versionMapper);
	}

	@Test
	void diffDetectsAddedRemovedChangedComponentsAndPageSettings() {
		stubPage();
		PageDesignVersion from = version("version-1", 1, """
				{"schemaVersion":2,"page":{"backgroundColor":"#ffffff"},
				 "components":[
				   {"id":"a","type":"gap","version":1,"props":{"height":10}},
				   {"id":"b","type":"notice","version":1,"props":{"text":"旧公告"}}]}
				""");
		PageDesignVersion to = version("version-2", 2, """
				{"schemaVersion":2,"page":{"backgroundColor":"#ff5500"},
				 "components":[
				   {"id":"a","type":"gap","version":1,"props":{"height":24}},
				   {"id":"c","type":"goods-group","version":1,"props":{}}]}
				""");
		when(versionMapper.selectById("version-1")).thenReturn(from);
		when(versionMapper.selectById("version-2")).thenReturn(to);

		PageDesignDiffVO diff = service.diff("page-1", "version-1", "version-2");

		assertEquals("version-1", diff.getFromVersionId());
		assertEquals(2, diff.getToVersionNo());
		assertEquals(1, diff.getAdded().size());
		assertEquals("c", diff.getAdded().get(0).getComponentId());
		assertEquals(1, diff.getRemoved().size());
		assertEquals("b", diff.getRemoved().get(0).getComponentId());
		assertEquals(1, diff.getChanged().size());
		assertEquals(1, diff.getChanged().get(0).getChanges().size());
		assertEquals("props.height", diff.getChanged().get(0).getChanges().get(0).getField());
		assertEquals(1, diff.getPageChanged().size());
		assertEquals("page.backgroundColor", diff.getPageChanged().get(0).getField());
	}

	@Test
	void diffFlattensV3SectionsBeforeComparing() {
		stubPage();
		PageDesignVersion from = version("version-1", 1, """
				{"schemaVersion":2,"components":[
				   {"id":"a","type":"gap","version":1,"props":{"height":10}}]}
				""");
		PageDesignVersion to = version("version-2", 2, """
				{"schemaVersion":3,"page":{},"sections":[
				  {"id":"s1","type":"default","components":[
				    {"id":"a","type":"gap","version":1,"props":{"height":10}}]}]}
				""");
		when(versionMapper.selectById("version-1")).thenReturn(from);
		when(versionMapper.selectById("version-2")).thenReturn(to);

		PageDesignDiffVO diff = service.diff("page-1", "version-1", "version-2");

		assertEquals(0, diff.getAdded().size());
		assertEquals(0, diff.getRemoved().size());
		assertEquals(0, diff.getChanged().size());
	}

	@Test
	void diffRejectsVersionFromAnotherPage() {
		stubPage();
		PageDesignVersion foreign = version("version-9", 1, "{\"schemaVersion\":2,\"components\":[]}");
		foreign.setPageDesignId("page-2");
		when(versionMapper.selectById("version-9")).thenReturn(foreign);

		ArynBusinessException error = assertThrows(ArynBusinessException.class,
				() -> service.diff("page-1", "version-9", "version-9"));

		assertEquals("起始版本不存在或无权访问", error.getMsg());
	}

	private void stubPage() {
		PageDesign page = new PageDesign();
		page.setId("page-1");
		when(pageDesignMapper.selectById("page-1")).thenReturn(page);
	}

	private PageDesignVersion version(String id, int versionNo, String content) {
		PageDesignVersion version = new PageDesignVersion();
		version.setId(id);
		version.setPageDesignId("page-1");
		version.setVersionNo(versionNo);
		version.setSchemaVersion(2);
		version.setPageContent(content);
		return version;
	}

}
