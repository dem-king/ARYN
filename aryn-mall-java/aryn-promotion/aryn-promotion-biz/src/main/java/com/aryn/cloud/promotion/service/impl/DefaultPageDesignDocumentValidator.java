package com.aryn.cloud.promotion.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.aryn.cloud.promotion.service.PageDesignDocumentValidator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class DefaultPageDesignDocumentValidator implements PageDesignDocumentValidator {

	@Override
	public List<String> validate(String pageContent) {
		List<String> errors = new ArrayList<>();
		try {
			JSONObject document = JSON.parseObject(pageContent);
			if (document == null || document.getIntValue("schemaVersion") != 2) {
				errors.add("schemaVersion必须为2");
			}
			if (document == null || document.getJSONArray("components") == null) {
				errors.add("components必须为数组");
			}
		}
		catch (Exception exception) {
			errors.add("页面内容不是有效JSON");
		}
		return errors;
	}

}
