package com.aryn.cloud.promotion.service.impl;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.entity.PageDesignTemplate;
import com.aryn.cloud.promotion.mapper.PageDesignTemplateMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
		PageDesignTemplate template = new PageDesignTemplate();
		template.setTemplateName("Campaign");
		template.setTemplateContent("{\"schemaVersion\":2,\"components\":[]}");
		when(mapper.insert(any(PageDesignTemplate.class))).thenReturn(1);

		PageDesignTemplate saved = service.saveTenantTemplate(template);

		assertEquals("0", saved.getSystemFlag());
		assertEquals(2, saved.getSchemaVersion());
		assertEquals("0", saved.getStatus());
		verify(mapper).insert(saved);
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
