package com.aryn.cloud.product.service.impl;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.api.entity.ShipSkuProfile;
import com.aryn.cloud.product.api.vo.QuickCartInfoVO;
import com.aryn.cloud.product.api.vo.QuickCartSkuVO;
import com.aryn.cloud.product.mapper.GoodsSpuMapper;
import com.aryn.cloud.product.service.IQuickCartService;
import com.aryn.cloud.product.service.IShipProductProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 快捷加购服务实现。
 *
 * @author aryn
 * @since 2026/9/21
 */
@Service
@RequiredArgsConstructor
public class QuickCartServiceImpl implements IQuickCartService {

	/** SKU 上架状态：0 正常，与结算链路 selectSkuByIds 的过滤条件保持一致 */
	private static final String SKU_STATUS_NORMAL = "0";

	/** 单规格商品标识：0 否（即单规格） */
	private static final String SINGLE_SPEC = "0";

	private final GoodsSpuMapper goodsSpuMapper;

	private final IShipProductProfileService shipProductProfileService;

	@Override
	public QuickCartInfoVO getQuickCartInfo(String spuId) {
		if (!StringUtils.hasText(spuId)) {
			return null;
		}
		// 复用 C 端详情查询：已下架/已删除商品返回 null，且不写浏览足迹
		GoodsSpu spu = goodsSpuMapper.selectApiSpuById(spuId);
		if (spu == null) {
			return null;
		}

		QuickCartInfoVO vo = new QuickCartInfoVO();
		vo.setSpuId(spu.getId());
		vo.setName(spu.getName());
		vo.setSpuUrls(spu.getSpuUrls());
		vo.setEnableSpecs(spu.getEnableSpecs());

		Map<String, ShipSkuProfile> profiles = loadProfiles(spu.getId());

		// 多规格必须由用户选择规格后才能确定价格与库存，交由前端唤起 SKU 弹层
		if (!SINGLE_SPEC.equals(spu.getEnableSpecs())) {
			List<QuickCartSkuVO> skus = toSellableSkuVos(spu, profiles);
			if (skus.isEmpty()) {
				vo.setMode(QuickCartInfoVO.MODE_UNAVAILABLE);
				vo.setReason("商品暂时缺货");
				return vo;
			}
			vo.setMode(QuickCartInfoVO.MODE_CHOOSE);
			vo.setGoodsSkus(skus);
			return vo;
		}

		GoodsSku sku = resolveSingleSku(spu);
		if (sku == null) {
			vo.setMode(QuickCartInfoVO.MODE_UNAVAILABLE);
			vo.setReason("商品暂时缺货");
			return vo;
		}

		vo.setMode(QuickCartInfoVO.MODE_DIRECT);
		vo.setSkuId(sku.getId());
		vo.setSalesPrice(sku.getSalesPrice());
		vo.setStock(sku.getStock());
		vo.setPicUrl(resolvePicUrl(sku, spu));
		vo.setSpecsInfo(toSpecsInfo(sku));
		applyProfile(vo, profiles.get(sku.getId()));
		return vo;
	}

	/**
	 * 单规格商品取唯一在售 SKU。
	 *
	 * <p>同一个 SPU 可能存在历史下架 SKU，优先取状态正常且库存为正的行；
	 * 全部无库存时返回 null，由调用方给出缺货提示。
	 */
	private GoodsSku resolveSingleSku(GoodsSpu spu) {
		List<GoodsSku> sellable = filterSellableSkus(spu);
		if (sellable.isEmpty()) {
			return null;
		}
		// 多行时取库存最充足的一行，避免一键加购选到即将售罄的 SKU
		return sellable.stream()
			.max(Comparator.comparingInt(GoodsSku::getStock))
			.orElse(null);
	}

	private List<GoodsSku> filterSellableSkus(GoodsSpu spu) {
		if (CollectionUtils.isEmpty(spu.getGoodsSkus())) {
			return List.of();
		}
		return spu.getGoodsSkus().stream()
			.filter(Objects::nonNull)
			.filter(sku -> StringUtils.hasText(sku.getId()))
			.filter(sku -> SKU_STATUS_NORMAL.equals(sku.getStatus()))
			.filter(sku -> sku.getStock() != null && sku.getStock() > 0)
			.toList();
	}

	private List<QuickCartSkuVO> toSellableSkuVos(GoodsSpu spu, Map<String, ShipSkuProfile> profiles) {
		return filterSellableSkus(spu).stream()
			.map((sku) -> {
				QuickCartSkuVO skuVO = new QuickCartSkuVO();
				skuVO.setSkuId(sku.getId());
				skuVO.setSalesPrice(sku.getSalesPrice());
				skuVO.setStock(sku.getStock());
				skuVO.setPicUrl(resolvePicUrl(sku, spu));
				skuVO.setSpecsInfo(toSpecsInfo(sku));
				skuVO.setSpecsArr(sku.getSpecsArr());
				ShipSkuProfile profile = profiles.get(sku.getId());
				if (profile != null) {
					skuVO.setMoq(profile.getMoq());
					skuVO.setStepQty(profile.getStepQty());
					skuVO.setPurchaseUnit(resolvePurchaseUnit(profile));
				}
				return skuVO;
			})
			.toList();
	}

	/**
	 * 查询商品的船供包装资料，按 SKU ID 建索引。
	 *
	 * <p>船供资料是「可选扩展属性」：未维护的商品按 MOQ=1、步长=1 处理，
	 * 与 {@code SharedCartServiceImpl.validateQuantityRules} 的默认值保持一致。
	 */
	private Map<String, ShipSkuProfile> loadProfiles(String spuId) {
		String tenantId = ArynTenantContextHolder.getTenantId();
		if (!StringUtils.hasText(tenantId)) {
			return Map.of();
		}
		List<ShipSkuProfile> profiles = shipProductProfileService.listSkuProfiles(tenantId, spuId);
		if (CollectionUtils.isEmpty(profiles)) {
			return Map.of();
		}
		return profiles.stream()
			.filter(profile -> StringUtils.hasText(profile.getSkuId()))
			.collect(Collectors.toMap(ShipSkuProfile::getSkuId, Function.identity(), (first, second) -> first));
	}

	private void applyProfile(QuickCartInfoVO vo, ShipSkuProfile profile) {
		if (profile == null) {
			return;
		}
		vo.setMoq(profile.getMoq());
		vo.setStepQty(profile.getStepQty());
		vo.setPurchaseUnit(resolvePurchaseUnit(profile));
	}

	private String resolvePurchaseUnit(ShipSkuProfile profile) {
		return StringUtils.hasText(profile.getPurchaseUnit()) ? profile.getPurchaseUnit() : profile.getBaseUnit();
	}

	private String resolvePicUrl(GoodsSku sku, GoodsSpu spu) {
		if (StringUtils.hasText(sku.getPicUrl())) {
			return sku.getPicUrl();
		}
		String[] urls = spu.getSpuUrls();
		return urls != null && urls.length > 0 ? urls[0] : null;
	}

	private String toSpecsInfo(GoodsSku sku) {
		if (CollectionUtils.isEmpty(sku.getSpecsArr())) {
			return "";
		}
		return sku.getSpecsArr().stream()
			.map(GoodsSku.Specs::getSpecsValueName)
			.filter(StringUtils::hasText)
			.collect(Collectors.joining("；"));
	}

}
