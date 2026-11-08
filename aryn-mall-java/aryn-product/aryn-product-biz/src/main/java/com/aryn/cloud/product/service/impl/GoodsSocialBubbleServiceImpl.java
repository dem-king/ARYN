package com.aryn.cloud.product.service.impl;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.order.api.dto.GoodsSpuSalesStatDTO;
import com.aryn.cloud.order.api.remote.RemoteGoodsSalesStatService;
import com.aryn.cloud.product.api.entity.GoodsAppraise;
import com.aryn.cloud.product.api.vo.AppraiseCountVO;
import com.aryn.cloud.product.api.vo.GoodsSocialBubbleVO;
import com.aryn.cloud.product.mapper.GoodsAppraiseMapper;
import com.aryn.cloud.product.service.IGoodsAppraiseService;
import com.aryn.cloud.product.service.IGoodsSocialBubbleService;
import com.aryn.cloud.product.support.SocialProofFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 商详口碑气泡装配实现：社证（order 域 T+1 统计，与商品卡同源同门槛）+
 * 好评率（评价区同口径，达标才成文）+ 买家头像（近期非匿名评价快照）。
 *
 * <p>三路数据互相独立降级：社证失败只丢社证文案、评价失败只丢好评率、头像失败只丢头像，
 * 全部不可得返回 null——气泡属增强展示，绝不拖垮商详主流程。
 *
 * <p>远程接口与 {@code GoodsCardTagServiceImpl} 同款 @DubboReference 非 final 字段注入
 * （构造器注入远程接口曾有双候选代理致 boot 启动失败的前科）。
 *
 * @author aryn
 * @since 2026/11/08
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoodsSocialBubbleServiceImpl implements IGoodsSocialBubbleService {

	/** 气泡头像上限（叠压展示 3 个足够，再多失去辨识度） */
	private static final int MAX_AVATARS = 3;

	@DubboReference
	private RemoteGoodsSalesStatService remoteGoodsSalesStatService;

	private final IGoodsAppraiseService goodsAppraiseService;

	private final GoodsAppraiseMapper goodsAppraiseMapper;

	/** 社证展示门槛：买家/回头客数下限（与商品卡 goods-card-tag 同 key 同默认值，卡面与商详永远同一句话） */
	@Value("${goods-card.social-proof.min-buyer-cnt:1000}")
	private int minBuyerCnt;

	/** 社证展示门槛：回购率%下限（与商品卡同 key 同默认值） */
	@Value("${goods-card.social-proof.min-repurchase-rate:30}")
	private int minRepurchaseRate;

	/** 好评率展示门槛：评价数下限（低于不下发，小样本好评率不可信） */
	@Value("${goods-card.social-proof.min-appraise-cnt:10}")
	private int minAppraiseCnt;

	@Override
	public GoodsSocialBubbleVO build(String spuId) {
		if (!StringUtils.hasText(spuId)) {
			return null;
		}
		GoodsSocialBubbleVO bubble = new GoodsSocialBubbleVO();
		fillSocialProof(bubble, spuId);
		fillPraiseRate(bubble, spuId);
		if (!StringUtils.hasText(bubble.getSocialText()) && !StringUtils.hasText(bubble.getPraiseText())) {
			// 两条文案都未达门槛：气泡必为 null，头像不必再查
			return null;
		}
		fillAvatars(bubble, spuId);
		return bubble;
	}

	/** 社证文案（order 域 T+1 统计）：门槛/取整见 SocialProofFormatter，与商品卡同源 */
	private void fillSocialProof(GoodsSocialBubbleVO bubble, String spuId) {
		try {
			// 租户显式随参：远端实现 fail-closed（租户缺失返回空），不依赖 Dubbo 隐式传播
			List<GoodsSpuSalesStatDTO> stats = remoteGoodsSalesStatService.latestStats(
					ArynTenantContextHolder.getTenantId(), List.of(spuId));
			if (stats == null || stats.isEmpty()) {
				return;
			}
			GoodsSpuSalesStatDTO stat = stats.get(0);
			if (stat == null) {
				return;
			}
			SocialProofFormatter.Proof proof = SocialProofFormatter.socialProof(
					stat.getBuyerCnt(), stat.getRepeatBuyerCnt(), stat.getRepurchaseRate(),
					minBuyerCnt, minRepurchaseRate);
			if (proof != null) {
				bubble.setSocialText(proof.text());
				bubble.setSocialKind(proof.kind());
			}
		}
		catch (Exception ex) {
			log.warn("商详口碑气泡社证查询失败，降级 spuId={}", spuId, ex);
		}
	}

	/** 好评率（评价区同口径 goods_score≥4 占比）：达标才成文，round 半进一位小数去 .0 */
	private void fillPraiseRate(GoodsSocialBubbleVO bubble, String spuId) {
		try {
			GoodsAppraise query = new GoodsAppraise();
			query.setSpuId(spuId);
			AppraiseCountVO count = goodsAppraiseService.getCount(query);
			if (count == null || count.getAllCount() == null || count.getGoodCount() == null
					|| count.getAllCount() < minAppraiseCnt || count.getAllCount() <= 0) {
				return;
			}
			BigDecimal rate = BigDecimal.valueOf(count.getGoodCount() * 100L)
					.divide(BigDecimal.valueOf(count.getAllCount()), 1, RoundingMode.HALF_UP)
					.stripTrailingZeros();
			bubble.setPraiseRate(rate);
			bubble.setPraiseText("好评率" + rate.toPlainString() + "%");
		}
		catch (Exception ex) {
			log.warn("商详口碑气泡好评率查询失败，降级 spuId={}", spuId, ex);
		}
	}

	/** 买家头像（近期非匿名评价快照）：失败只丢头像，纯文字胶囊兜底 */
	private void fillAvatars(GoodsSocialBubbleVO bubble, String spuId) {
		try {
			List<String> avatars = goodsAppraiseMapper.selectRecentBuyerAvatars(spuId, MAX_AVATARS);
			if (avatars != null && !avatars.isEmpty()) {
				bubble.setAvatars(avatars);
			}
		}
		catch (Exception ex) {
			log.warn("商详口碑气泡头像查询失败，降级 spuId={}", spuId, ex);
		}
	}

}
