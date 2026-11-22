package com.aryn.cloud.promotion.service.impl;
import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson2.JSONObject;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.dto.*;
import com.aryn.cloud.promotion.api.entity.*;
import com.aryn.cloud.promotion.api.vo.*;
import com.aryn.cloud.promotion.mapper.*;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.time.LocalDateTime;
import java.util.*;
/** 发布所有资源均属promotion同库；状态与页面指针同一真实事务提交，不需要跨域补偿。 */
@Service
@RequiredArgsConstructor
public class CampaignPublishStore {
 private final CampaignPublishOperationMapper operations;
 private final CampaignDraftStore drafts;
 private final CampaignDraftMapper campaigns;
 private final PromotionActivityMapper activities;
 private final PricePolicyMapper policies;
 private final PricePolicyItemMapper policyItems;
 private final PageDesignMapper pages;
 private final CouponInfoMapper coupons;
 private final B2bActivityCommandService commands;
 private final PricePolicyService policyCommands;
 private final PageDesignVersionServiceImpl versions;
 private final CampaignResourceDigest digest;
 @Transactional(rollbackFor=Exception.class)
 public CampaignPublishOperation begin(String actor,String id,CampaignPublishCommandDTO command) {
  if(command==null||!StringUtils.hasText(command.getRequestId())||command.getRequestId().length()>64
   ||command.getExpectedRevision()==null||command.getExpectedPageRevision()==null
   ||!StringUtils.hasText(command.getSimulationId())||!StringUtils.hasText(command.getInputDigest())
   ||!StringUtils.hasText(command.getFactDigest())||!StringUtils.hasText(command.getPageDigest())) {throw new ArynBusinessException("发布请求、修订和确认摘要不能为空");}
  // 根锁串行化同包创建，唯一键兜底。相同信封即使响应丢失也返回既有记录。
  var draft=drafts.owned(actor,id,true);
  var prior=operations.selectOne(Wrappers.lambdaQuery(CampaignPublishOperation.class).eq(CampaignPublishOperation::getTenantId,tenant()).eq(CampaignPublishOperation::getDraftId,id).eq(CampaignPublishOperation::getRequestId,command.getRequestId()));
  if(prior!=null){if(!actor.equals(prior.getActorId())||!commandDigest(command).equals(prior.getCommandDigest())){throw new ArynBusinessException(409,"同一发布requestId不能更换命令");}return prior;}
  if(!"READY".equals(draft.getStatus())||!Objects.equals(draft.getRevision(),command.getExpectedRevision())){throw new ArynBusinessException(409,"活动包已变化或已经发布");}
  var op=new CampaignPublishOperation();op.setId(IdUtil.getSnowflakeNextIdStr());op.setTenantId(tenant());op.setActorId(actor);op.setDraftId(id);op.setRequestId(command.getRequestId());
  op.setCommandJson(drafts.encode(command));op.setCommandDigest(commandDigest(command));op.setStatus("VALIDATING");op.setStepsJson("[]");op.setCreateTime(LocalDateTime.now());op.setUpdateTime(LocalDateTime.now());op.setDelFlag("0");operations.insert(op);return op;
 }
 public CampaignPublishOperation owned(String actor,String id,boolean lock){
  if(!StringUtils.hasText(actor)){throw new ArynBusinessException("缺少操作者");}
  var query=Wrappers.lambdaQuery(CampaignPublishOperation.class).eq(CampaignPublishOperation::getTenantId,tenant()).eq(CampaignPublishOperation::getActorId,actor).eq(CampaignPublishOperation::getId,id);
  if(lock){query.last("FOR UPDATE");}var op=operations.selectOne(query);if(op==null){throw new ArynBusinessException("发布记录不存在");}return op;
 }
 public CampaignPublishCommandDTO command(CampaignPublishOperation op){try{return new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().readValue(op.getCommandJson(),CampaignPublishCommandDTO.class);}catch(Exception invalid){throw new ArynBusinessException("发布信封无法恢复");}}
 public List<CampaignPublishVO> list(String actor,String id){drafts.owned(actor,id,false);return operations.selectList(Wrappers.lambdaQuery(CampaignPublishOperation.class).eq(CampaignPublishOperation::getTenantId,tenant()).eq(CampaignPublishOperation::getActorId,actor).eq(CampaignPublishOperation::getDraftId,id).orderByDesc(CampaignPublishOperation::getCreateTime).last("LIMIT 50")).stream().map(this::view).toList();}
 @Transactional(rollbackFor=Exception.class)
 public void fail(String actor,String id){var op=owned(actor,id,true);if(!"PUBLISHED".equals(op.getStatus())){op.setStatus("FAILED_RECOVERABLE");op.setFailureCode("VALIDATION_OR_LOCAL_TRANSACTION_FAILED");op.setUpdateTime(LocalDateTime.now());operations.updateById(op);}}
 @Transactional(rollbackFor=Exception.class)
 public CampaignPublishOperation complete(String actor,String operationId,CampaignPublishCommandDTO command,CampaignPagePreviewVO preview,CampaignGenerationInput facts){
  // 所有发布命令一致使用根→operation→资源的锁序。
  var reference=owned(actor,operationId,false);var draft=drafts.owned(actor,reference.getDraftId(),true);var op=owned(actor,operationId,true);
  if("PUBLISHED".equals(op.getStatus())){return op;}
  if(!commandDigest(command).equals(op.getCommandDigest())||!"READY".equals(draft.getStatus())||!Objects.equals(draft.getRevision(),command.getExpectedRevision())
   ||!Objects.equals(draft.getSimulationId(),command.getSimulationId())||!Objects.equals(draft.getSimulationRevision(),draft.getRevision())
   ||!Objects.equals(draft.getPageId(),preview.getPage().getId())||!Objects.equals(draft.getPageDigest(),command.getPageDigest())){throw new ArynBusinessException(409,"活动包或模拟绑定已变化");}
  if(preview.getSimulation().getExpiresAt()==null||!preview.getSimulation().getExpiresAt().isAfter(LocalDateTime.now())){throw new ArynBusinessException(409,"模拟已过期");}
  var page=pages.selectOne(Wrappers.lambdaQuery(PageDesign.class).eq(PageDesign::getId,draft.getPageId()).eq(PageDesign::getTenantId,tenant()).last("FOR UPDATE"));
  if(page==null||!draft.getId().equals(page.getCampaignDraftId())||!"0".equals(page.getPageType())||!Objects.equals(page.getDraftRevision(),command.getExpectedPageRevision())||!Objects.equals(digest.of(JSONObject.parseObject(page.getPageContent())),command.getPageDigest())){throw new ArynBusinessException(409,"页面已被修改");}
  op.setStatus("DRAFT_VALIDATED");op.setStepsJson("[\"DRAFT_VALIDATED\"]");operations.updateById(op);
  if(draft.getMainPolicyId()!=null){
   var policy=policies.selectOne(Wrappers.lambdaQuery(PricePolicy.class).eq(PricePolicy::getTenantId,tenant()).eq(PricePolicy::getId,draft.getMainPolicyId()).last("FOR UPDATE"));
   var items=policyItems.selectList(Wrappers.lambdaQuery(PricePolicyItem.class).eq(PricePolicyItem::getTenantId,tenant()).eq(PricePolicyItem::getPolicyId,draft.getMainPolicyId()).orderByAsc(PricePolicyItem::getSkuId).last("FOR UPDATE"));
   if(policy==null||!PricePolicy.STATUS_DRAFT.equals(policy.getStatus())||!Objects.equals(draft.getMainPolicyDigest(),digest.of(Arrays.asList(policy,items)))){throw new ArynBusinessException(409,"协议草稿已被独立修改");}
   policyCommands.changeStatus(tenant(),policy.getId(),PricePolicy.STATUS_ACTIVE);op.setMainPolicyId(policy.getId());
  }else{
   var activity=activities.selectOne(Wrappers.lambdaQuery(PromotionActivity.class).eq(PromotionActivity::getTenantId,tenant()).eq(PromotionActivity::getId,draft.getMainActivityId()).last("FOR UPDATE"));
   if(activity==null||!PromotionActivity.STATUS_DRAFT.equals(activity.getStatus())||!Objects.equals(activity.getRevision(),draft.getMainActivityRevision())){throw new ArynBusinessException(409,"主活动已被独立修改");}
   var sales=new HashMap<String,java.math.BigDecimal>();for(var candidate:facts.getCandidates()){sales.put(candidate.getSkuId(),candidate.getSalesPrice());}
   commands.publishCampaign(activity.getId(),activity.getRevision(),command.isAcceptOverlap(),sales);op.setMainActivityId(activity.getId());
  }
  var definition=drafts.decode(draft.getDefinitionJson());
  if(definition.getCoupon()!=null){
   var rule=definition.getCoupon();var coupon=new CouponInfo();coupon.setId(op.getId());coupon.setTenantId(tenant());coupon.setCouponName(rule.getName());coupon.setCouponType("1");coupon.setAmount(rule.getAmount());coupon.setThreshold(rule.getThreshold());coupon.setTotalNum(rule.getQuantity());coupon.setRemainNum(rule.getQuantity());coupon.setAssignCount(0);coupon.setUsedCount(0);coupon.setReceiveCount(1);coupon.setReceiveStartedAt(definition.getStartTime());coupon.setReceiveEndedAt(definition.getEndTime());coupon.setUseRange("1");coupon.setStatus("1");coupon.setUseDescription("活动包定向券规则，尚未启用或发放；发放对象须经既有授权命令核定。包ID："+draft.getId());coupon.setVersion(0);coupon.setDelFlag("0");coupon.setCreateTime(LocalDateTime.now());coupons.insert(coupon);op.setCouponId(coupon.getId());
  }
  op.setStatus("RESOURCES_PREPARED");op.setStepsJson("[\"DRAFT_VALIDATED\",\"RESOURCES_PREPARED\",\"PUBLISHING\"]");operations.updateById(op);
  // 页面最后切换，价格事实不写公开JSON；内部引用使用已存在的真实资源ID。
  var version=versions.publishCampaignSnapshot(draft.getId(),page.getId(),page.getPageContent(),page.getDraftRevision());
  op.setPageId(page.getId());op.setPageVersionId(version.getId());op.setStatus("PUBLISHED");op.setFailureCode(null);op.setStepsJson("[\"DRAFT_VALIDATED\",\"RESOURCES_PREPARED\",\"PUBLISHING\",\"PUBLISHED\"]");op.setUpdateTime(LocalDateTime.now());
  operations.update(null,Wrappers.lambdaUpdate(CampaignPublishOperation.class).eq(CampaignPublishOperation::getTenantId,tenant()).eq(CampaignPublishOperation::getId,op.getId()).set(CampaignPublishOperation::getStatus,op.getStatus()).set(CampaignPublishOperation::getStepsJson,op.getStepsJson()).set(CampaignPublishOperation::getMainActivityId,op.getMainActivityId()).set(CampaignPublishOperation::getMainPolicyId,op.getMainPolicyId()).set(CampaignPublishOperation::getCouponId,op.getCouponId()).set(CampaignPublishOperation::getPageId,op.getPageId()).set(CampaignPublishOperation::getPageVersionId,op.getPageVersionId()).set(CampaignPublishOperation::getFailureCode,null).set(CampaignPublishOperation::getUpdateTime,op.getUpdateTime()));
  if(campaigns.update(null,Wrappers.lambdaUpdate(CampaignDraft.class).eq(CampaignDraft::getId,draft.getId()).eq(CampaignDraft::getTenantId,tenant()).eq(CampaignDraft::getRevision,draft.getRevision()).set(CampaignDraft::getStatus,"PUBLISHED").set(CampaignDraft::getUpdateTime,LocalDateTime.now()))!=1){throw new ArynBusinessException(409,"活动包发布状态发生冲突");}
  return op;
 }
 public CampaignPublishVO view(CampaignPublishOperation op){var vo=new CampaignPublishVO();org.springframework.beans.BeanUtils.copyProperties(op,vo);vo.setSteps(com.alibaba.fastjson2.JSON.parseArray(op.getStepsJson(),String.class));return vo;}
 private String commandDigest(CampaignPublishCommandDTO command){
  if(command.getAcceptedRiskCodes()!=null){command.setAcceptedRiskCodes(new java.util.TreeSet<>(command.getAcceptedRiskCodes()));}
  return digest.of(command);
 }
 private String tenant(){var tenant=ArynTenantContextHolder.getTenantId();if(!StringUtils.hasText(tenant)){throw new ArynBusinessException("缺少租户上下文");}return tenant;}
}
