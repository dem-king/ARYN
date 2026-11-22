package com.aryn.cloud.promotion.controller.admin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.promotion.api.dto.*;
import com.aryn.cloud.promotion.api.vo.*;
import com.aryn.cloud.promotion.service.impl.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/campaign-draft")
public class CampaignDraftController {
	private final CampaignDraftService drafts;
	private final CampaignGenerationService generator;
	private final CampaignGenerationStore jobs;
	private final CampaignPolicyDraftService policies;
	private final CampaignPageDraftService pages;
	private final CampaignPublishService publisher;
	private final CampaignPublishStore publications;
 private final com.aryn.cloud.promotion.service.PageDesignMetricService metrics;
 private final B2bActivityCommandService activities;
	@GetMapping @SaCheckPermission("promotion:campaign:edit")
	public Result<List<CampaignDraftVO>> list() { return Result.success(drafts.list(SecurityUtils.getUserId())); }
	@GetMapping("/{id}") @SaCheckPermission("promotion:campaign:edit")
	public Result<CampaignDraftVO> get(@PathVariable String id) { return Result.success(drafts.get(SecurityUtils.getUserId(), id)); }
	@PostMapping @SaCheckPermission("promotion:campaign:edit")
	public Result<CampaignDraftVO> create(@RequestBody CampaignDraftSaveDTO request) { return Result.success(drafts.save(SecurityUtils.getUserId(), null, request)); }
	@PutMapping("/{id}") @SaCheckPermission("promotion:campaign:edit")
	public Result<CampaignDraftVO> update(@PathVariable String id, @RequestBody CampaignDraftSaveDTO request) { return Result.success(drafts.save(SecurityUtils.getUserId(), id, request)); }
	@PostMapping("/{id}/generation") @SaCheckPermission("promotion:campaign:generate")
	public Result<CampaignGenerationVO> generate(@PathVariable String id, @RequestBody CampaignGenerationCommandDTO request) {
		return Result.success(view(generator.enqueue(SecurityUtils.getUserId(), id, request.getExpectedRevision(), request.getRequestId())));
	}
	@GetMapping("/{id}/generation") @SaCheckPermission("promotion:campaign:generate")
	public Result<List<CampaignGenerationVO>> jobs(@PathVariable String id) { return Result.success(jobs.list(SecurityUtils.getUserId(), id).stream().map(this::view).toList()); }
	@GetMapping("/generation/{jobId}") @SaCheckPermission("promotion:campaign:generate")
	public Result<CampaignGenerationVO> job(@PathVariable String jobId) { return Result.success(view(jobs.get(SecurityUtils.getUserId(), jobId))); }
	@PostMapping("/generation/{jobId}/retry") @SaCheckPermission("promotion:campaign:generate")
	public Result<Void> retry(@PathVariable String jobId) { jobs.retry(SecurityUtils.getUserId(), jobId); return Result.success(); }
	@PostMapping("/generation/{jobId}/cancel") @SaCheckPermission("promotion:campaign:generate")
	public Result<Void> cancel(@PathVariable String jobId) { jobs.cancel(SecurityUtils.getUserId(), jobId); return Result.success(); }
	@PutMapping("/{id}/policy") @SaCheckPermission({"promotion:campaign:edit", "promotion:pricepolicy:save"})
	public Result<CampaignDraftVO> policy(@PathVariable String id,@RequestBody CampaignPolicyCommandDTO command) {
		return Result.success(policies.save(SecurityUtils.getUserId(),id,command));
	}

	@PostMapping("/{id}/page") @SaCheckPermission({"promotion:campaign:edit", "promotion:pagedesign:add", "promotion:pagedesign:edit"})
	public Result<CampaignDraftVO> page(@PathVariable String id,@RequestBody CampaignPageCommandDTO command) {return Result.success(pages.generate(SecurityUtils.getUserId(),id,command));}
	@PostMapping("/{id}/page-preview") @SaCheckPermission({"promotion:campaign:simulate", "promotion:pagedesign:edit"})
	public Result<CampaignPagePreviewVO> pagePreview(@PathVariable String id,@RequestBody CampaignPageCommandDTO command) {return Result.success(pages.preview(SecurityUtils.getUserId(),id,command));}

	@PostMapping("/{id}/page-review") @SaCheckPermission({"promotion:campaign:simulate", "promotion:pagedesign:edit"})
	public Result<CampaignPagePreviewVO> pageReview(@PathVariable String id,@RequestBody CampaignPageCommandDTO command) {return Result.success(pages.review(SecurityUtils.getUserId(),id,command));}
	@GetMapping("/{id}/publications") @SaCheckPermission("promotion:campaign:publish")
	public Result<List<CampaignPublishVO>> publications(@PathVariable String id){return Result.success(publisher.list(SecurityUtils.getUserId(),id));}
	@PostMapping("/{id}/publish") @SaCheckPermission({"promotion:campaign:publish","promotion:campaign:simulate","promotion:pagedesign:publish"})
	public Result<CampaignPublishVO> publish(@PathVariable String id,@RequestBody CampaignPublishCommandDTO command){
		checkPublishResources(id);return Result.success(publisher.publish(SecurityUtils.getUserId(),id,command));
	}
	@PostMapping("/publications/{operationId}/retry") @SaCheckPermission({"promotion:campaign:publish","promotion:campaign:simulate","promotion:pagedesign:publish"})
	public Result<CampaignPublishVO> retryPublication(@PathVariable String operationId){
		var operation=publications.owned(SecurityUtils.getUserId(),operationId,false);checkPublishResources(operation.getDraftId());
		return Result.success(publisher.retry(SecurityUtils.getUserId(),operationId));
	}
	private void checkPublishResources(String id){
		var draft=drafts.get(SecurityUtils.getUserId(),id);
		cn.dev33.satoken.stp.StpUtil.checkPermission(draft.getMainPolicyId()==null?"promotion:shipactivity:publish":"promotion:pricepolicy:publish");
		if(draft.getDefinition().getCoupon()!=null){cn.dev33.satoken.stp.StpUtil.checkPermission("promotion:couponinfo:add");}
	}

 @GetMapping("/{id}/metrics") @SaCheckPermission({"promotion:campaign:edit","promotion:pagedesign:metrics"})
 public Result<java.util.Map<String,Object>> metrics(@PathVariable String id){
  var draft=drafts.get(SecurityUtils.getUserId(),id);var result=new java.util.LinkedHashMap<String,Object>();
  var records=draft.getPageId()==null?java.util.List.<com.aryn.cloud.promotion.api.entity.PageDesignMetricDaily>of():metrics.listMetrics(draft.getPageId(),7);
  result.put("PAGE_VIEW",records.stream().mapToLong(v->v.getViewCount()==null?0:v.getViewCount()).sum());
  result.put("COMPONENT_CLICK",records.stream().mapToLong(v->v.getClickCount()==null?0:v.getClickCount()).sum());
  result.put("RENDER_ERROR",records.stream().mapToLong(v->v.getErrorCount()==null?0:v.getErrorCount()).sum());
  result.put("ACTIVITY_CONSUMED_BUDGET",draft.getMainActivityId()==null?null:activities.get(draft.getMainActivityId()).getConsumedBudget());
  result.put("ATTRIBUTED_ORDERS",null);result.put("ATTRIBUTED_PROFIT",null);
  return Result.success(result);
 }

	private CampaignGenerationVO view(com.aryn.cloud.promotion.api.entity.CampaignGenerationJob job) {
		var result = new CampaignGenerationVO(); BeanUtils.copyProperties(job, result); return result;
	}
}
