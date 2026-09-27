package com.aryn.cloud.promotion.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.dto.PageDesignTemplateDTO;
import com.aryn.cloud.promotion.api.entity.PageDesignTemplate;
import com.aryn.cloud.promotion.api.vo.PageDesignTemplateVO;
import com.aryn.cloud.promotion.mapper.PageDesignTemplateMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PageDesignTemplateServiceTest {

	@Mock
	private PageDesignTemplateMapper mapper;

	private PageDesignTemplateServiceImpl service;

	@BeforeEach
	void setUp() {
		service = new PageDesignTemplateServiceImpl(mapper);
	}

	@Test
	void savesTenantTemplateWithV2Defaults() {
		PageDesignTemplateDTO request = new PageDesignTemplateDTO();
		request.setTemplateName("Campaign");
		request.setTemplateContent(JSON.parseObject("{\"schemaVersion\":2,\"components\":[]}"));
		when(mapper.insert(any(PageDesignTemplate.class))).thenReturn(1);

		PageDesignTemplateVO saved = service.saveTenantTemplate(request);

		assertEquals("0", saved.getSystemFlag());
		assertEquals(2, saved.getSchemaVersion());
		assertEquals("0", saved.getStatus());
		assertEquals("Campaign", saved.getTemplateName());
		verify(mapper).insert(any(PageDesignTemplate.class));
	}

	@Test
	void storesTemplateContentAsJsonStringAtPersistenceBoundary() {
		PageDesignTemplateDTO request = new PageDesignTemplateDTO();
		request.setTemplateName("Campaign");
		request.setTemplateContent(JSON.parseObject("{\"schemaVersion\":3,\"sections\":[]}"));
		request.setSchemaVersion(3);
		when(mapper.insert(any(PageDesignTemplate.class))).thenReturn(1);

		service.saveTenantTemplate(request);

		ArgumentCaptor<PageDesignTemplate> captor = ArgumentCaptor.forClass(PageDesignTemplate.class);
		verify(mapper).insert(captor.capture());
		String persisted = captor.getValue().getTemplateContent();
		assertEquals("{\"schemaVersion\":3,\"sections\":[]}", persisted);
		assertEquals(3, captor.getValue().getSchemaVersion());
	}

	@Test
	void readsPersistedJsonStringBackAsStructuredContent() {
		PageDesignTemplate stored = new PageDesignTemplate();
		stored.setId("t-1");
		stored.setTemplateName("Campaign");
		stored.setTemplateContent("{\"schemaVersion\":3,\"sections\":[]}");
		when(mapper.selectList(any())).thenReturn(List.of(stored));

		List<PageDesignTemplateVO> templates = service.listTemplates("0");

		assertEquals(1, templates.size());
		assertInstanceOf(JSONObject.class, templates.getFirst().getTemplateContent());
		assertEquals(3, templates.getFirst().getTemplateContent().getIntValue("schemaVersion"));
	}

	@Test
	void rejectsChangesToSystemTemplates() {
		PageDesignTemplate system = new PageDesignTemplate();
		system.setId("system-1");
		system.setSystemFlag("1");
		when(mapper.selectById("system-1")).thenReturn(system);

		assertThrows(ArynBusinessException.class, () -> service.removeTenantTemplate("system-1"));

		verify(mapper, never()).deleteById("system-1");
	}
}
