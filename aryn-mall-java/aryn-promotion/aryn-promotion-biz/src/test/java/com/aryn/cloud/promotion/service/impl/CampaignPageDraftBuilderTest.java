package com.aryn.cloud.promotion.service.impl;
import com.alibaba.fastjson2.*;
import com.aryn.cloud.promotion.api.dto.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class CampaignPageDraftBuilderTest {
	private final CampaignPageDraftBuilder builder=new CampaignPageDraftBuilder();
	@Test void generatedDocumentUsesRegisteredComponentsAndContainsNoPriceOrCostSnapshot() {
		var input=input();var doc=builder.build("campaign",1L,input);builder.validate(doc,input);
		assertTrue(new DefaultPageDesignDocumentValidator().validate(doc.toJSONString(),"0").isEmpty());
		String fixture=System.getProperty("aryn.campaign.fixture.path");
		if(fixture!=null){try{java.nio.file.Files.writeString(java.nio.file.Path.of(fixture),doc.toJSONString());}catch(Exception invalid){throw new IllegalStateException(invalid);}}
		assertEquals(3,doc.getIntValue("schemaVersion"));assertFalse(doc.toJSONString().contains("salesPrice"));assertFalse(doc.toJSONString().contains("costPrice"));
		assertEquals(List.of("spu"),doc.getJSONArray("sections").getJSONObject(0).getJSONArray("components").getJSONObject(2).getJSONObject("props").getJSONObject("dataSource").getJSONArray("targetIds").toJavaList(String.class));
	}
	@Test void editedDocumentRejectsUnknownComponentOutsideGoodsDangerousLinkAndFixedPrice() {
		var doc=builder.build("campaign",0L,input());var component=doc.getJSONArray("sections").getJSONObject(0).getJSONArray("components").getJSONObject(2);
		component.put("type","newbie-floor");assertThrows(RuntimeException.class,()->builder.validate(doc,input()));component.put("type","goods-group");
		component.getJSONObject("props").getJSONObject("dataSource").put("targetIds",List.of("outside"));assertThrows(RuntimeException.class,()->builder.validate(doc,input()));component.getJSONObject("props").getJSONObject("dataSource").put("targetIds",List.of("spu"));
		component.getJSONObject("props").put("price",1);assertThrows(RuntimeException.class,()->builder.validate(doc,input()));component.getJSONObject("props").remove("price");
		component.getJSONObject("props").put("link",Map.of("type","custom","path","javascript:alert(1)"));assertThrows(RuntimeException.class,()->builder.validate(doc,input()));
	}
	@Test void oneHundredProductsUseBoundedGroupsAndMissingSpuCannotBeInvented() {
		var input=input();var candidates=new ArrayList<CampaignGenerationInput.Candidate>();for(int i=0;i<100;i++){var c=new CampaignGenerationInput.Candidate();c.setSkuId("s"+i);c.setSpuId("p"+i);candidates.add(c);}input.setCandidates(candidates);
		var doc=builder.build("campaign",0L,input);builder.validate(doc,input);assertTrue(new DefaultPageDesignDocumentValidator().validate(doc.toJSONString(),"0").isEmpty());assertEquals(7,doc.getJSONArray("sections").getJSONObject(0).getJSONArray("components").size());
		input.getCandidates().forEach(c->c.setSpuId(null));assertThrows(RuntimeException.class,()->builder.build("campaign",0L,input));
	}
	@Test void sectionMetadataCannotSmuggleExecutableContentAndGlobalCalendarCannotReplaceBoundCalendar() {
		var doc=builder.build("campaign",0L,input());
		doc.getJSONArray("sections").getJSONObject(0).put("style",Map.of("backgroundImage","javascript:alert(1)"));
		assertThrows(RuntimeException.class,()->builder.validate(doc,input()));
		var edited=builder.build("campaign",0L,input());
		edited.getJSONArray("sections").getJSONObject(0).getJSONArray("components").getJSONObject(2).put("type","trade-calendar");
		assertThrows(RuntimeException.class,()->builder.validate(edited,input()));
	}
	private CampaignGenerationInput input(){var def=new CampaignDraftSaveDTO();def.setTitle("采购活动");def.setStartTime(LocalDateTime.of(2031,1,1,0,0));def.setEndTime(LocalDateTime.of(2031,2,1,0,0));var input=new CampaignGenerationInput();input.setConstraints(def);var c=new CampaignGenerationInput.Candidate();c.setSkuId("sku");c.setSpuId("spu");input.setCandidates(List.of(c));return input;}
}
