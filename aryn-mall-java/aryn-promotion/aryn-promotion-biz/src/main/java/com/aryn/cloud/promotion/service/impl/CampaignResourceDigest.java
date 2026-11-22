package com.aryn.cloud.promotion.service.impl;
import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
@Component
@RequiredArgsConstructor
public class CampaignResourceDigest {
	private final ObjectMapper mapper;
	public String of(Object value) {
		try {
			var canonical=mapper.copy().configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY,true).configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS,true);
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.writeValueAsString(value).getBytes(StandardCharsets.UTF_8)));
		} catch(Exception invalid) {throw new IllegalStateException("资源摘要无法生成",invalid);}
	}
}
