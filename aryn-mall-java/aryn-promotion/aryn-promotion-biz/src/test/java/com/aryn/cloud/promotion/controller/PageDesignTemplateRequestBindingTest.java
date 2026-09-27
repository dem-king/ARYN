package com.aryn.cloud.promotion.controller;

import com.aryn.cloud.promotion.api.vo.PageDesignTemplateVO;
import com.aryn.cloud.promotion.controller.admin.PageDesignTemplateController;
import com.aryn.cloud.promotion.service.impl.PageDesignTemplateServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 模板保存请求体使用 JSON 对象承载 templateContent，回归“String 字段接收对象”的 400 反序列化错误。
 */
@ExtendWith(MockitoExtension.class)
class PageDesignTemplateRequestBindingTest {

	@Mock
	private PageDesignTemplateServiceImpl templateService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(new PageDesignTemplateController(templateService)).build();
	}

	@Test
	void acceptsTemplateContentAsJsonObject() throws Exception {
		when(templateService.saveTenantTemplate(any())).thenReturn(new PageDesignTemplateVO());

		String payload = """
				{
				  "pageType": "1",
				  "schemaVersion": 3,
				  "systemFlag": "0",
				  "templateName": "首页模板",
				  "templateType": "0",
				  "templateContent": {
				    "schemaVersion": 3,
				    "page": {"title": "首页"},
				    "sections": [
				      {"id": "s-1", "type": "default", "components": [{"id": "c-1", "type": "notice", "version": 1, "props": {"text": "公告"}}]}
				    ]
				  }
				}
				""";

		mockMvc.perform(post("/pagedesign/templates")
				.contentType(MediaType.APPLICATION_JSON)
				.content(payload))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value(0));
	}

	@Test
	void acceptsTemplateContentAsJsonObjectOnUpdate() throws Exception {
		when(templateService.saveTenantTemplate(any())).thenReturn(new PageDesignTemplateVO());

		String payload = """
				{
				  "templateName": "首页模板",
				  "templateContent": {"schemaVersion": 3, "sections": []}
				}
				""";

		mockMvc.perform(put("/pagedesign/templates/t-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(payload))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value(0));
	}

}
