package com.aryn.cloud.promotion.service.impl;
import com.alibaba.fastjson2.*;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.dto.*;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.stream.Collectors;
/** 确定的组件白名单；只写选品引用与业务文案，不把合同价/成本固化进可分享文档。 */
@Component
public class CampaignPageDraftBuilder {
	// 现有trade-calendar固定读取全局当前档期，尚不能绑定本包核定档期，故不进入包白名单。
	public static final Set<String> ALLOWED=Set.of("title-text","notice","goods-group","image-ad","gap");
	public JSONObject build(String id,Long revision,CampaignGenerationInput input) {
		var definition=input.getConstraints();
		String titleText=Objects.toString(definition.getTitle(),"采购活动");
		var spuIds=input.getCandidates().stream().map(CampaignGenerationInput.Candidate::getSpuId).filter(Objects::nonNull).distinct().toList();
		if(spuIds.isEmpty()){throw new ArynBusinessException("缺少可核实商品SPU引用，无法生成装修草稿");}
		var doc=new JSONObject();doc.put("schemaVersion",3);
		doc.put("page",Map.of("backgroundColor","#f6f7f9","backgroundImage","","enablePullDownRefresh",false,
			"navigation",Map.of("backgroundColor","#ffffff","textColor","#1f2937","title",titleText,"visible",true),
			"share",Map.of("title",titleText,"description","采购活动","imageUrl","")));
		var title=new JSONObject();title.put("title",titleText);title.put("showDesc",true);title.put("desc",definition.getStartTime()+" 至 "+definition.getEndTime());title.put("moreBtn",false);title.put("link",null);title.put("titleSize",20);title.put("descSize",12);title.put("titleColor","#1f2937");title.put("descColor","#6b7280");title.put("commonStyle",style());
		var notice=new JSONObject();notice.put("contentList",List.of(Map.of("content","活动资格、阶梯门槛及最终成交价以结算核验为准")));notice.put("direction","horizontal");notice.put("speed",50);notice.put("color","#6b7280");notice.put("commonStyle",style());
		var components=new JSONArray();components.add(component("campaign-title","title-text",title));components.add(component("campaign-notice","notice",notice));
		for(int start=0;start<spuIds.size();start+=20){
			var selected=spuIds.subList(start,Math.min(start+20,spuIds.size()));var props=new JSONObject();props.put("title","采购选品");props.put("columns",2);props.put("count",selected.size());props.put("showOriginalPrice",true);props.put("showSales",false);props.put("dataSource",Map.of("mode","manual","targetIds",selected));props.put("emptyStrategy","hide");props.put("invalidStrategy","hide");props.put("commonStyle",style());components.add(component("campaign-goods-"+start,"goods-group",props));
		}
		doc.put("sections",List.of(Map.of("id","campaign-section","title","采购活动","components",components,"style",Map.of("condition","always"))));
		return JSONObject.parseObject(doc.toJSONString());
	}
	/** 人工编辑可沿用现有装修器，活动包发布仍限制组件与引用范围。 */
	public void validate(JSONObject doc,CampaignGenerationInput input) {
		assertSafe(doc);
		if(doc.getIntValue("schemaVersion")!=3){throw new ArynBusinessException("活动包装修仅支持schema v3");}
		var approved=input.getCandidates().stream().map(CampaignGenerationInput.Candidate::getSpuId).filter(Objects::nonNull).collect(Collectors.toSet());
		var sections=doc.getJSONArray("sections");if(sections==null){throw new ArynBusinessException("缺少页面区块");}
		for(var raw:sections){var section=(JSONObject)raw;var components=section.getJSONArray("components");if(components==null){continue;}
			for(var value:components){var component=(JSONObject)value;
				if(!ALLOWED.contains(component.getString("type"))){throw new ArynBusinessException("活动包不支持该组件："+component.getString("type"));}
				if("trade-calendar".equals(component.getString("type")) && input.getConstraints().getCalendarId()==null){throw new ArynBusinessException("档期组件需已核定档期");}
				var props=component.getJSONObject("props");
				if("campaign-title".equals(component.getString("id")) && props!=null && !Objects.equals(props.getString("desc"),input.getConstraints().getStartTime()+" 至 "+input.getConstraints().getEndTime())){throw new ArynBusinessException("活动时间文案必须与核定窗口一致");}
				if("goods-group".equals(component.getString("type"))){var source=props==null?null:props.getJSONObject("dataSource");
					if(source==null||!"manual".equals(source.getString("mode"))||source.getJSONArray("targetIds")==null||!approved.containsAll(source.getJSONArray("targetIds").toJavaList(String.class))){throw new ArynBusinessException("活动包商品组件必须引用本包已核定货盘");}}
				assertSafe(component);
			}
		}
	}
	private void assertSafe(Object node) {
		if(node instanceof Map<?,?> map){for(var e:map.entrySet()){
			String key=String.valueOf(e.getKey()).toLowerCase(Locale.ROOT);
			if(Set.of("price","salesprice","costprice","stock","starttime","endtime","html","script").contains(key)){throw new ArynBusinessException("页面不能固化价格/库存/业务窗口或代码");}
			if("link".equals(key)||"morelink".equals(key)){if(e.getValue()!=null){throw new ArynBusinessException("活动包一期不生成自定义跳转；商品由受权货盘打开");}}
			assertSafe(e.getValue());
		}} else if(node instanceof Collection<?> values){values.forEach(this::assertSafe);}
		else if(node instanceof String text){var lower=text.toLowerCase(Locale.ROOT).replaceAll("\\s+","");if(lower.contains("javascript:")||lower.contains("<script")||lower.contains("data:text/html")||lower.contains("/seckill")||lower.contains("/group-buy")||lower.contains("coupon-center")){throw new ArynBusinessException("活动包包含危险或停运入口");}}
	}
	private JSONObject component(String id,String type,JSONObject props){var value=new JSONObject();value.put("id",id);value.put("type",type);value.put("version",1);value.put("props",props);return value;}
	private Map<String,Object> style(){return Map.of("bgStartColor","#ffffff","bgEndColor","","styleTopPadding",12,"styleBottomPadding",12,"styleLeftPadding",12,"styleRightPadding",12,"styleTopMargin",10,"styleBottomMargin",10);}
}
