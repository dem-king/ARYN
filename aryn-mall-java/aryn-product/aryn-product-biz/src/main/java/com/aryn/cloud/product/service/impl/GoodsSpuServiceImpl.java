
package com.aryn.cloud.product.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.core.enums.MallErrorCodeEnum;
import com.aryn.cloud.common.security.entity.ArynUser;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.product.api.entity.*;
import com.aryn.cloud.product.api.util.GoodsCostPriceMasker;
import com.aryn.cloud.product.api.vo.GoodsCatalogSummaryVO;
import com.aryn.cloud.product.mapper.*;
import com.aryn.cloud.product.service.IGoodsSpuService;
import com.aryn.cloud.product.service.IGoodsSocialBubbleService;
import com.aryn.cloud.product.service.support.GoodsZoneTagRuleValidator;
import com.aryn.cloud.product.support.GoodsAttrTagNormalizer;
import com.aryn.cloud.product.support.GoodsIntroductionSanitizer;
import com.aryn.cloud.upms.api.remote.RemoteMaterialService;
import com.aryn.cloud.upms.api.vo.RemoteImageSizeVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 商品spu
 *
 * @author 雨滴kian
 * @since 2022/2/22 15:27
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoodsSpuServiceImpl extends ServiceImpl<GoodsSpuMapper, GoodsSpu> implements IGoodsSpuService {

	/** 低价捡漏折扣率阈值下界（开区间）：必须大于 1.00，否则等于无限价要求。 */
	private static final BigDecimal BARGAIN_RATE_MIN_EXCLUSIVE = new BigDecimal("1.00");

	/** 低价捡漏折扣率阈值上界（闭区间）：9.99 已是「原价近十倍」，再大必然是脏数据。 */
	private static final BigDecimal BARGAIN_RATE_MAX_INCLUSIVE = new BigDecimal("9.99");

	private static final String BARGAIN_RATE_RANGE_TIP = "折扣率阈值 bargainRate 必须在 (1.00, 9.99] 区间内";

	/** 管理端「专区」筛选支持的值：会员专区 / 低价捡漏 / 未加入任何专区 / 未加入指定专区。 */
	private static final Set<String> ADMIN_ZONE_TYPES = Set.of("member", "clearance", "none", "not_member",
			"not_clearance");

	/** 管理端「专区」筛选的取值校验提示（与前端下拉选项一一对应）。 */
	private static final String ADMIN_ZONE_TYPE_TIP = "专区筛选只支持 member / clearance / none / not_member / not_clearance";

	/** 推荐流分类上下文的商品上限（购物车/订单可能很多，只取样最近/前 N 个） */
	private static final int RECOMMEND_SOURCE_LIMIT = 20;

	/** 推荐流排除商品上限，避免 IN 列表与请求 URL 过长 */
	private static final int RECOMMEND_EXCLUDE_LIMIT = 50;

	/** 个人中心个性化：近期浏览足迹取样上限 */
	private static final int RECOMMEND_FOOTPRINT_LIMIT = 20;

	private final GoodsSkuMapper goodsSkuMapper;

	private final GoodsCollectMapper goodsCollectMapper;

	private final GoodsFootprintMapper goodsFootprintMapper;

	private final GoodsCategoryMapper goodsCategoryMapper;

	private final GoodsPriceHistoryService goodsPriceHistoryService;

	private final IGoodsSocialBubbleService goodsSocialBubbleService;

	/**
	 * 专区默认折扣：表单保存出清商品时与批量打标同口径校验。
	 *
	 * <p>非 final + @DubboReference（构造器注入远程接口曾在 boot 启动失败）。
	 */
	@org.apache.dubbo.config.annotation.DubboReference
	private com.aryn.cloud.promotion.api.remote.RemoteMallZoneSettingService remoteMallZoneSettingService;

	/** 主图宽高解析（upms 读本地文件/受信域名，结果有缓存）；非 final + @DubboReference 同上一字段 */
	@org.apache.dubbo.config.annotation.DubboReference
	private RemoteMaterialService remoteMaterialService;

	@Override
	public IPage<GoodsSpu> adminPage(Page page, GoodsSpu goodsSpu, String zoneType) {
		return baseMapper.selectPageByAdmin(page, goodsSpu, normalizeAdminZoneType(zoneType));
	}

	/**
	 * 管理端「专区」筛选归一化：空白视为不筛选，未知取值直接拒绝。
	 *
	 * <p>不在 SQL 里做字符串判断，避免前端拼错（如把 member 写成 members）时静默返回全量，
	 * 运营误以为筛选生效；校验风格与 C 端 {@code zoneSort} 一致。
	 */
	private String normalizeAdminZoneType(String zoneType) {
		if (!StringUtils.hasText(zoneType)) {
			return null;
		}
		String value = zoneType.trim();
		if (!ADMIN_ZONE_TYPES.contains(value)) {
			throw new ArynBusinessException(ADMIN_ZONE_TYPE_TIP);
		}
		return value;
	}

	@Override
	public IPage<GoodsCatalogSummaryVO> catalogPage(String tenantId, Page<GoodsCatalogSummaryVO> page, String keyword,
			String status) {
		// LIKE 通配符按字面量匹配：用户输入 %/_ 不再当通配符（与联想词同口径）
		String escapedKeyword = StringUtils.hasText(keyword) ? escapeLikeKeyword(keyword) : keyword;
		return baseMapper.selectCatalogPage(page, tenantId, escapedKeyword, status);
	}

	@Override
	public IPage<GoodsSpu> warehousePage(Page page, GoodsSpu goodsSpu) {
		return baseMapper.selectPageWarehouse(page, goodsSpu);
	}

	@Override
	public GoodsSpu getSpuById(String id) {
		GoodsSpu goodsSpu = baseMapper.selectSpuById(id);
		if (Objects.nonNull(goodsSpu)) {
			// 查询分类
			GoodsCategory firstCategory = goodsCategoryMapper.selectById(goodsSpu.getCategoryFirstId());
			GoodsCategory secondCategory = goodsCategoryMapper.selectById(goodsSpu.getCategorySecondId());
			StringBuilder categoryName = new StringBuilder();
			if (Objects.nonNull(firstCategory)) {
				categoryName.append(firstCategory.getName());
			}
			if (Objects.nonNull(secondCategory)) {
				categoryName.append("/").append(secondCategory.getName());
			}
			goodsSpu.setCategoryName(categoryName.toString());
		}
		return goodsSpu;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean updateGoods(GoodsSpu goodsSpu) {
		if (goodsSpu == null || !StringUtils.hasText(goodsSpu.getId())) {
			throw new ArynBusinessException("商品标识不能为空");
		}
		GoodsSpu storedSpu = baseMapper.selectById(goodsSpu.getId());
		if (storedSpu == null) {
			throw new ArynBusinessException("商品不存在或无权操作");
		}
		boolean zoneChanged = zoneFieldsChanged(storedSpu, goodsSpu);
		goodsSpu.setAttrTags(GoodsAttrTagNormalizer.normalize(goodsSpu.getAttrTags()));
		// 显式提交了卖点（哪怕是空编辑器）就要真正落库：清洗成 null 时写空串，
		// 否则会被 MyBatis-Plus 的 NOT_NULL 更新策略忽略，运营清空卖点不生效
		boolean introductionSubmitted = goodsSpu.getIntroduction() != null;
		goodsSpu.setIntroduction(GoodsIntroductionSanitizer.sanitize(goodsSpu.getIntroduction()));
		if (introductionSubmitted && goodsSpu.getIntroduction() == null) {
			goodsSpu.setIntroduction("");
		}
		List<GoodsSku> goodsSkuList = validateGoodsSkus(goodsSpu);
		Map<String, GoodsSku> storedSkuMap = Optional.ofNullable(goodsSkuMapper.selectBySpuId(goodsSpu.getId()))
			.orElseGet(Collections::emptyList)
			.stream()
			.collect(LinkedHashMap::new, (map, sku) -> map.put(sku.getId(), sku), Map::putAll);
		int stockDelta = 0;
		for (GoodsSku goodsSku : goodsSkuList) {
			if (StringUtils.hasText(goodsSku.getId())) {
				GoodsSku storedSku = storedSkuMap.remove(goodsSku.getId());
				if (storedSku == null) {
					throw new ArynBusinessException("SKU不属于当前商品");
				}
				int storedVersion = Objects.requireNonNullElse(storedSku.getVersion(), 0);
				if (goodsSku.getVersion() == null || goodsSku.getVersion() != storedVersion) {
					throw new ArynBusinessException("商品库存已变化，请刷新后重试");
				}
				stockDelta = Math.addExact(stockDelta, goodsSku.getStock() - storedSku.getStock());
				recordSkuPriceChanges(goodsSpu.getId(), storedSku, goodsSku);
				prepareSkuForUpdate(goodsSku, goodsSpu.getId());
				if (goodsSkuMapper.updateById(goodsSku) <= 0) {
					throw new ArynBusinessException("商品库存已变化，请刷新后重试");
				}
			}
			else {
				stockDelta = Math.addExact(stockDelta, goodsSku.getStock());
				prepareSkuForInsert(goodsSku, goodsSpu.getId());
				if (goodsSkuMapper.insert(goodsSku) <= 0) {
					throw new ArynBusinessException("新增SKU失败");
				}
			}
		}
		for (GoodsSku removedSku : storedSkuMap.values()) {
			int version = Objects.requireNonNullElse(removedSku.getVersion(), 0);
			if (goodsSkuMapper.delete(Wrappers.<GoodsSku>lambdaQuery()
				.eq(GoodsSku::getId, removedSku.getId())
				.eq(GoodsSku::getSpuId, goodsSpu.getId())
				.eq(GoodsSku::getVersion, version)) <= 0) {
				throw new ArynBusinessException("商品库存已变化，请刷新后重试");
			}
			stockDelta = Math.subtractExact(stockDelta, removedSku.getStock());
		}
		applyAggregatePrices(goodsSpu, goodsSkuList);
		recordSpuPriceChanges(storedSpu, goodsSpu);
		validateZoneFields(goodsSpu, storedSpu);
		prepareSpuForUpdate(goodsSpu);
		if (super.updateById(goodsSpu) == false) {
			throw new ArynBusinessException("商品修改失败");
		}
		adjustSpuStock(goodsSpu.getId(), stockDelta);
		goodsSpu.setZoneChanged(zoneChanged);
		return Boolean.TRUE;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean saveGoods(GoodsSpu goodsSpu) {
		goodsSpu.setAttrTags(GoodsAttrTagNormalizer.normalize(goodsSpu.getAttrTags()));
		goodsSpu.setIntroduction(GoodsIntroductionSanitizer.sanitize(goodsSpu.getIntroduction()));
		List<GoodsSku> goodsSkuList = validateGoodsSkus(goodsSpu);
		applyAggregatePrices(goodsSpu, goodsSkuList);
		validateZoneFields(goodsSpu, null);
		goodsSpu.setZoneChanged(hasZoneConfig(goodsSpu));
		goodsSpu.setStock(calculateTotalStock(goodsSkuList));
		prepareSpuForInsert(goodsSpu);
		if (super.save(goodsSpu) == false) {
			throw new ArynBusinessException("新增商品失败");
		}
		for (GoodsSku goodsSku : goodsSkuList) {
			prepareSkuForInsert(goodsSku, goodsSpu.getId());
			if (goodsSkuMapper.insert(goodsSku) <= 0) {
				throw new ArynBusinessException("新增SKU失败");
			}
		}
		return Boolean.TRUE;
	}

	private List<GoodsSku> validateGoodsSkus(GoodsSpu goodsSpu) {
		if (goodsSpu == null || CollectionUtils.isEmpty(goodsSpu.getGoodsSkus())) {
			throw new ArynBusinessException("商品SKU不能为空");
		}
		List<GoodsSku> goodsSkuList = goodsSpu.getGoodsSkus();
		if ("0".equals(goodsSpu.getEnableSpecs()) && goodsSkuList.size() != 1) {
			throw new ArynBusinessException("单规格商品只能包含一个SKU");
		}
		Set<String> specsKeys = new HashSet<>();
		for (GoodsSku goodsSku : goodsSkuList) {
			if (goodsSku == null || goodsSku.getSalesPrice() == null || goodsSku.getOriginalPrice() == null
					|| goodsSku.getCostPrice() == null || goodsSku.getStock() == null || goodsSku.getStock() < 0
					|| goodsSku.getSalesPrice().signum() < 0 || goodsSku.getOriginalPrice().signum() < 0
					|| goodsSku.getCostPrice().signum() < 0) {
				throw new ArynBusinessException("SKU价格或库存不合法");
			}
			String specsKey = buildSpecsKey(goodsSku);
			if ("1".equals(goodsSpu.getEnableSpecs()) && !StringUtils.hasText(specsKey)) {
				throw new ArynBusinessException("多规格商品的SKU规格不能为空");
			}
			if (!specsKeys.add(specsKey)) {
				throw new ArynBusinessException("SKU规格组合重复");
			}
		}
		return goodsSkuList;
	}

	private int calculateTotalStock(List<GoodsSku> goodsSkuList) {
		long totalStock = 0;
		for (GoodsSku goodsSku : goodsSkuList) {
			totalStock += goodsSku.getStock();
			if (totalStock > Integer.MAX_VALUE) {
				throw new ArynBusinessException("商品库存总量超出上限");
			}
		}
		return (int) totalStock;
	}

	private String buildSpecsKey(GoodsSku goodsSku) {
		if (CollectionUtils.isEmpty(goodsSku.getSpecsArr())) {
			return "";
		}
		List<String> keys = new ArrayList<>();
		for (GoodsSku.Specs specs : goodsSku.getSpecsArr()) {
			if (specs == null || !StringUtils.hasText(specs.getSpecsId())
					|| !StringUtils.hasText(specs.getSpecsValueId())) {
				throw new ArynBusinessException("SKU规格信息不完整");
			}
			keys.add(specs.getSpecsId() + ":" + specs.getSpecsValueId());
		}
		Collections.sort(keys);
		return String.join("|", keys);
	}

	private void applyAggregatePrices(GoodsSpu goodsSpu, List<GoodsSku> goodsSkuList) {
		goodsSpu.setSalesPrice(
				goodsSkuList.stream().map(GoodsSku::getSalesPrice).min(Comparator.naturalOrder()).orElseThrow());
		goodsSpu.setOriginalPrice(
				goodsSkuList.stream().map(GoodsSku::getOriginalPrice).min(Comparator.naturalOrder()).orElseThrow());
		goodsSpu.setCostPrice(
				goodsSkuList.stream().map(GoodsSku::getCostPrice).min(Comparator.naturalOrder()).orElseThrow());
	}

	private void prepareSpuForInsert(GoodsSpu goodsSpu) {
		goodsSpu.setId(null);
		goodsSpu.setTenantId(null);
		goodsSpu.setSalesVolume(0);
		goodsSpu.setCreateBy(null);
		goodsSpu.setUpdateBy(null);
		goodsSpu.setCreateTime(null);
		goodsSpu.setUpdateTime(null);
		goodsSpu.setDelFlag(null);
	}

	private void prepareSpuForUpdate(GoodsSpu goodsSpu) {
		goodsSpu.setTenantId(null);
		goodsSpu.setSalesVolume(null);
		goodsSpu.setStock(null);
		goodsSpu.setCreateBy(null);
		goodsSpu.setUpdateBy(null);
		goodsSpu.setCreateTime(null);
		goodsSpu.setUpdateTime(null);
		goodsSpu.setDelFlag(null);
	}

	private void prepareSkuForInsert(GoodsSku goodsSku, String spuId) {
		goodsSku.setId(null);
		goodsSku.setSpuId(spuId);
		goodsSku.setTenantId(null);
		goodsSku.setVersion(null);
		goodsSku.setCreateBy(null);
		goodsSku.setUpdateBy(null);
		goodsSku.setCreateTime(null);
		goodsSku.setUpdateTime(null);
		goodsSku.setDelFlag(null);
		goodsSku.setGoodsSpu(null);
	}

	private void prepareSkuForUpdate(GoodsSku goodsSku, String spuId) {
		goodsSku.setSpuId(spuId);
		goodsSku.setTenantId(null);
		goodsSku.setCreateBy(null);
		goodsSku.setUpdateBy(null);
		goodsSku.setCreateTime(null);
		goodsSku.setUpdateTime(null);
		goodsSku.setDelFlag(null);
		goodsSku.setGoodsSpu(null);
	}

	private void adjustSpuStock(String spuId, int stockDelta) {
		if (stockDelta == 0) {
			return;
		}
		if (stockDelta > 0) {
			if (baseMapper.update(new GoodsSpu(),
					Wrappers.<GoodsSpu>lambdaUpdate()
						.eq(GoodsSpu::getId, spuId)
						.setSql("stock = stock + " + stockDelta)) <= 0) {
				throw new ArynBusinessException("商品库存汇总更新失败");
			}
			return;
		}
		int quantity = Math.negateExact(stockDelta);
		if (baseMapper.update(new GoodsSpu(),
				Wrappers.<GoodsSpu>lambdaUpdate()
					.eq(GoodsSpu::getId, spuId)
					.ge(GoodsSpu::getStock, quantity)
					.setSql("stock = stock - " + quantity)) <= 0) {
			throw new ArynBusinessException("商品库存汇总更新失败");
		}
	}

	@Override
	public IPage<GoodsSpu> apiPage(Page page, GoodsSpu goodsSpu) {
		return apiPage(page, goodsSpu, null);
	}

	@Override
	public IPage<GoodsSpu> apiPage(Page page, GoodsSpu goodsSpu, BigDecimal bargainRate) {
		return apiPage(page, goodsSpu, bargainRate, null, null, null);
	}

	@Override
	public IPage<GoodsSpu> apiPage(Page page, GoodsSpu goodsSpu, BigDecimal bargainRate, Boolean memberItem,
			Boolean clearanceItem, String zoneSort) {
		String normalizedZoneSort = normalizeZoneSort(zoneSort);
		if (Objects.nonNull(bargainRate) && normalizedZoneSort == null) {
			// 区间 (1.00, 9.99]：前端缺省 1.15，越界按业务错误返回而不是让 SQL 跑出离谱结果
			if (bargainRate.compareTo(BARGAIN_RATE_MIN_EXCLUSIVE) <= 0
					|| bargainRate.compareTo(BARGAIN_RATE_MAX_INCLUSIVE) > 0) {
				throw new ArynBusinessException(BARGAIN_RATE_RANGE_TIP);
			}
		}
		if (Objects.nonNull(bargainRate) || normalizedZoneSort != null) {
			// 固定排序由 XML 输出，这里必须清空 asc/desc：既避免分页插件再拼一层排序列，
			// 也彻底关掉「用户传列名」的注入面（排序键只能是 XML 里写死的表达式）。
			if (Objects.nonNull(page)) {
				page.setOrders(Collections.emptyList());
			}
		}
		if (Objects.nonNull(goodsSpu) && StringUtils.hasText(goodsSpu.getName())) {
			// 结果页按名称搜索：通配符按字面量匹配（XML 直接读 #{query.name}，原地改写查询对象）
			goodsSpu.setName(escapeLikeKeyword(goodsSpu.getName()));
		}
		IPage<GoodsSpu> result = baseMapper.selectApiPage(page, goodsSpu,
				normalizedZoneSort != null ? null : bargainRate, memberItem, clearanceItem, normalizedZoneSort);
		// C 端出参脱敏：成本价/会员定价规则是内部经营数据，不下发（详见 GoodsCostPriceMasker）
		if (Objects.nonNull(result)) {
			result.getRecords().forEach(GoodsCostPriceMasker::maskSpu);
			fillPicSizes(result.getRecords());
		}
		return result;
	}

	private String normalizeZoneSort(String zoneSort) {
		if (!StringUtils.hasText(zoneSort)) {
			return null;
		}
		String value = zoneSort.trim();
		if (!"member_price".equals(value) && !"clearance".equals(value)) {
			throw new ArynBusinessException("不支持的专区排序：" + zoneSort);
		}
		return value;
	}

	/** SKU 售价/原价变更落历史：用于先涨后降判定与价格审计。 */
	private void recordSkuPriceChanges(String spuId, GoodsSku stored, GoodsSku incoming) {
		goodsPriceHistoryService.recordIfChanged(spuId, incoming.getId(), "sales", stored.getSalesPrice(),
			incoming.getSalesPrice(), "product_save", "商品编辑修改SKU售价");
		goodsPriceHistoryService.recordIfChanged(spuId, incoming.getId(), "original", stored.getOriginalPrice(),
			incoming.getOriginalPrice(), "product_save", "商品编辑修改SKU原价");
	}

	/** SPU 聚合价（min/SKU）变更落历史。 */
	private void recordSpuPriceChanges(GoodsSpu stored, GoodsSpu incoming) {
		goodsPriceHistoryService.recordIfChanged(incoming.getId(), null, "sales", stored.getSalesPrice(),
			incoming.getSalesPrice(), "product_save", "商品编辑修改SPU聚合售价");
		goodsPriceHistoryService.recordIfChanged(incoming.getId(), null, "original", stored.getOriginalPrice(),
			incoming.getOriginalPrice(), "product_save", "商品编辑修改SPU聚合原价");
	}

	/**
	 * 专区字段校验：表单直改商品时也必须走与批量打标相同的规则，
	 * 防止绕过接口写入非法折扣率/价格。
	 *
	 * @param stored 更新前的商品（新增传 null），用于区分「未提交」与「主动改成过去时间」
	 */
	private void validateZoneFields(GoodsSpu spu, GoodsSpu stored) {
		GoodsZoneTagRuleValidator.validateRate(spu.getMemberDiscountRate(), "会员折扣率");
		GoodsZoneTagRuleValidator.validateRate(spu.getClearanceDiscountRate(), "出清折扣率");
		GoodsZoneTagRuleValidator.validatePositive(spu.getMemberPriceFixed(), "会员固定价");
		GoodsZoneTagRuleValidator.validatePositive(spu.getMemberPriceFloor(), "会员价下限");
		GoodsZoneTagRuleValidator.validatePositive(spu.getClearancePrice(), "出清价");
		Boolean clearanceTag = spu.getIsClearanceItem() != null ? Objects.equals(spu.getIsClearanceItem(), 1)
				: (stored != null && Objects.equals(stored.getIsClearanceItem(), 1));
		GoodsZoneTagRuleValidator.validateClearanceAvailability(clearanceTag, spu.getClearancePrice(),
				spu.getClearanceDiscountRate(), stored == null ? null : stored.getClearancePrice(),
				stored == null ? null : stored.getClearanceDiscountRate(), loadClearanceDefaultRate(),
				spu.getOriginalPrice());
		if (spu.getClearanceDiscountRate() != null
				&& (spu.getOriginalPrice() == null || spu.getOriginalPrice().signum() <= 0)) {
			throw new ArynBusinessException("商品缺少原价，无法按出清折扣算价，请配置出清固定价");
		}
		LocalDateTime expireTime = spu.getClearanceExpireTime();
		LocalDateTime storedExpireTime = stored == null ? null : stored.getClearanceExpireTime();
		if (expireTime != null && !Objects.equals(expireTime, storedExpireTime)
				&& !expireTime.isAfter(LocalDateTime.now())) {
			throw new ArynBusinessException("出清截止时间不能早于当前时间");
		}
	}

	/**
	 * 读取专区默认折扣；远程不可用时按未配置处理（回退为必须配置商品规则）。
	 */
	private BigDecimal loadClearanceDefaultRate() {
		if (remoteMallZoneSettingService == null) {
			return null;
		}
		try {
			com.aryn.cloud.promotion.api.vo.MallZoneSettingSnapshotVO setting = remoteMallZoneSettingService
				.getSetting(com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder.getTenantId(),
						com.aryn.cloud.promotion.api.entity.MallZoneSetting.ZONE_CLEARANCE);
			return setting == null ? null : setting.getDefaultDiscountRate();
		}
		catch (Exception ex) {
			return null;
		}
	}

	private void validateZoneRate(BigDecimal rate, String label) {
		if (rate != null && (rate.signum() <= 0 || rate.compareTo(BigDecimal.ONE) >= 0)) {
			throw new ArynBusinessException(label + "必须在 (0, 1) 区间内，如 0.9 表示 9 折");
		}
	}

	private void validateZonePositive(BigDecimal value, String label) {
		if (value != null && value.signum() <= 0) {
			throw new ArynBusinessException(label + "必须大于 0");
		}
	}

	/** 本次提交是否带了专区配置（新增商品时决定是否需要同步系统活动）。 */
	private boolean hasZoneConfig(GoodsSpu spu) {
		return Objects.equals(spu.getIsMemberItem(), 1) || Objects.equals(spu.getIsClearanceItem(), 1)
				|| spu.getMemberDiscountRate() != null || spu.getMemberPriceFixed() != null
				|| spu.getMemberPriceFloor() != null || spu.getClearancePrice() != null
				|| spu.getClearanceDiscountRate() != null || spu.getClearanceExpireTime() != null;
	}

	/**
	 * 专区字段是否变化：null 视为「本次未提交」（与 MyBatis-Plus NOT_NULL 更新策略一致），
	 * 避免普通商品编辑也触发租户级全量同步。
	 */
	private boolean zoneFieldsChanged(GoodsSpu stored, GoodsSpu incoming) {
		return changed(stored.getIsMemberItem(), incoming.getIsMemberItem())
				|| changed(stored.getIsClearanceItem(), incoming.getIsClearanceItem())
				|| changed(stored.getMemberDiscountRate(), incoming.getMemberDiscountRate())
				|| changed(stored.getMemberPriceFixed(), incoming.getMemberPriceFixed())
				|| changed(stored.getMemberPriceFloor(), incoming.getMemberPriceFloor())
				|| changed(stored.getClearancePrice(), incoming.getClearancePrice())
				|| changed(stored.getClearanceDiscountRate(), incoming.getClearanceDiscountRate())
				|| changed(stored.getClearanceExpireTime(), incoming.getClearanceExpireTime());
	}

	private boolean changed(Object stored, Object incoming) {
		return incoming != null && !Objects.equals(stored, incoming);
	}

	private boolean changed(BigDecimal stored, BigDecimal incoming) {
		if (incoming == null) {
			return false;
		}
		return stored == null || stored.compareTo(incoming) != 0;
	}

	@Override
	public IPage<GoodsSpu> recommendPage(Page page, String spuId, List<String> sourceSpuIds, List<String> excludeIds) {
		if (Objects.nonNull(page)) {
			// 固定排序由 XML 输出：清空 asc/desc，避免分页插件再拼一层排序列
			page.setOrders(Collections.emptyList());
			// 推荐流是无限滚动，前端只按返回条数判断是否还有下一页；
			// 关闭 count 既省一次查询，也让带绑定参数的分类排序可以安全下推
			page.setSearchCount(false);
		}

		Set<String> sources = new LinkedHashSet<>();
		if (StringUtils.hasText(spuId)) {
			sources.add(spuId);
		}
		addIds(sources, sourceSpuIds, RECOMMEND_SOURCE_LIMIT);
		if (sources.isEmpty()) {
			// 无显式上下文（个人中心）：登录用户按近期浏览足迹的品类个性化
			sources.addAll(recentFootprintSpuIds());
		}

		Set<String> excludes = new LinkedHashSet<>();
		if (StringUtils.hasText(spuId)) {
			excludes.add(spuId);
		}
		addIds(excludes, excludeIds, RECOMMEND_EXCLUDE_LIMIT);

		List<String> categorySecondIds = new ArrayList<>();
		List<String> categoryFirstIds = new ArrayList<>();
		if (!sources.isEmpty()) {
			List<GoodsSpu> contextGoods = baseMapper.selectBatchIds(sources);
			if (!CollectionUtils.isEmpty(contextGoods)) {
				Set<String> secondIds = new LinkedHashSet<>();
				Set<String> firstIds = new LinkedHashSet<>();
				for (GoodsSpu goods : contextGoods) {
					if (StringUtils.hasText(goods.getCategorySecondId())) {
						secondIds.add(goods.getCategorySecondId());
					}
					if (StringUtils.hasText(goods.getCategoryFirstId())) {
						firstIds.add(goods.getCategoryFirstId());
					}
				}
				categorySecondIds.addAll(secondIds);
				categoryFirstIds.addAll(firstIds);
			}
		}

		IPage<GoodsSpu> result = baseMapper.selectRecommendPage(page, new ArrayList<>(excludes),
				categorySecondIds, categoryFirstIds);
		// C 端出参脱敏：成本价是内部经营数据，不下发
		if (Objects.nonNull(result)) {
			result.getRecords().forEach(GoodsCostPriceMasker::maskSpu);
			// 推荐卡同样按主图宽高锁占位比例，避免瀑布流图片加载时跳动
			fillPicSizes(result.getRecords());
		}
		return result;
	}

	/**
	 * 登录用户近期浏览过的商品 ID（按时间倒序、去重、限流）。
	 *
	 * <p>只作为推荐分类上下文；未登录或没有足迹时返回空列表，推荐退化为全店排序。
	 */
	private List<String> recentFootprintSpuIds() {
		ArynUser user = SecurityUtils.getUser();
		if (Objects.isNull(user) || !StringUtils.hasText(user.getUserId())) {
			return Collections.emptyList();
		}
		List<GoodsFootprint> footprints = goodsFootprintMapper.selectList(Wrappers.<GoodsFootprint>lambdaQuery()
			.eq(GoodsFootprint::getUserId, user.getUserId())
			.orderByDesc(GoodsFootprint::getCreateTime)
			.last("LIMIT " + RECOMMEND_FOOTPRINT_LIMIT));
		if (CollectionUtils.isEmpty(footprints)) {
			return Collections.emptyList();
		}
		Set<String> spuIds = new LinkedHashSet<>();
		for (GoodsFootprint footprint : footprints) {
			if (StringUtils.hasText(footprint.getSpuId())) {
				spuIds.add(footprint.getSpuId());
			}
		}
		return new ArrayList<>(spuIds);
	}

	private static void addIds(Set<String> target, List<String> ids, int limit) {
		if (CollectionUtils.isEmpty(ids)) {
			return;
		}
		for (String id : ids) {
			if (target.size() >= limit) {
				return;
			}
			if (StringUtils.hasText(id)) {
				target.add(id.trim());
			}
		}
	}

	@Override
	public GoodsSpu getApiSpuById(String id) {
		GoodsSpu goodsSpu = baseMapper.selectApiSpuById(id);
		// 商品不存在/已下架：返回 null 而不是在脱敏处 NPE 500（与 IQuickCartService
		// 「商品不存在或已下架返回 null」同口径）；Controller 包装成 code=0/data=null，
		// C 端商详页据此弹「商品已下架」并退回，而不是卡在空态。
		if (Objects.isNull(goodsSpu)) {
			return null;
		}
		ArynUser hxUser = SecurityUtils.getUser();
		if (Objects.nonNull(hxUser) && StringUtils.hasText(hxUser.getUserId())) {
			GoodsCollect goodsCollect = goodsCollectMapper.selectOne(Wrappers.<GoodsCollect>lambdaQuery()
				.eq(GoodsCollect::getSpuId, id)
				.eq(GoodsCollect::getUserId, hxUser.getUserId()));
			if (Objects.nonNull(goodsCollect)) {
				goodsSpu.setCollectId(goodsCollect.getId());
			}
			// 保存浏览记录：同一商品只保留最近一次，重复浏览刷新时间而不是新增
			GoodsFootprint goodsFootprint = goodsFootprintMapper.selectOne(Wrappers.<GoodsFootprint>lambdaQuery()
				.eq(GoodsFootprint::getUserId, hxUser.getUserId())
				.eq(GoodsFootprint::getSpuId, id)
				.orderByDesc(GoodsFootprint::getCreateTime)
				.last("LIMIT 1"));
			if (Objects.nonNull(goodsFootprint)) {
				goodsFootprint.setCreateTime(LocalDateTime.now());
				goodsFootprintMapper.updateById(goodsFootprint);
			}
			else {
				goodsFootprint = new GoodsFootprint();
				goodsFootprint.setSpuId(id);
				goodsFootprint.setUserId(hxUser.getUserId());
				goodsFootprintMapper.insert(goodsFootprint);
			}
		}
		// C 端出参脱敏：SPU 与嵌套 SKU 的成本价不下发（详见 GoodsCostPriceMasker）
		GoodsSpu masked = GoodsCostPriceMasker.maskSpu(goodsSpu);
		// 口碑气泡增强展示：内部自行逐路降级，全部不可得返回 null（气泡不渲染）
		masked.setSocialBubble(goodsSocialBubbleService.build(id));
		return masked;
	}

	/**
	 * C 端按 ID 批量查在售商品（装修楼层/分类页用）。
	 *
	 * <p>从控制器收拢进服务层：查询条件（仅上架）保持不变，出参统一脱敏成本价。
	 */
	@Override
	public List<GoodsSpu> apiListByIds(List<String> ids) {
		List<GoodsSpu> goodsSpuList = list(Wrappers.<GoodsSpu>lambdaQuery()
			.in(GoodsSpu::getId, ids)
			.eq(GoodsSpu::getStatus, "1"));
		goodsSpuList.forEach(GoodsCostPriceMasker::maskSpu);
		fillPicSizes(goodsSpuList);
		return goodsSpuList;
	}

	@Override
	public void fillPicSizes(List<GoodsSpu> records) {
		if (CollectionUtils.isEmpty(records)) {
			return;
		}
		Map<String, List<GoodsSpu>> byUrl = new LinkedHashMap<>();
		for (GoodsSpu spu : records) {
			String url = firstImageUrl(spu);
			if (StringUtils.hasText(url)) {
				byUrl.computeIfAbsent(url, key -> new ArrayList<>()).add(spu);
			}
		}
		if (byUrl.isEmpty()) {
			return;
		}
		try {
			Map<String, RemoteImageSizeVO> sizes = remoteMaterialService
				.resolveImageSizes(new ArrayList<>(byUrl.keySet()));
			if (sizes == null || sizes.isEmpty()) {
				return;
			}
			byUrl.forEach((url, spus) -> {
				RemoteImageSizeVO size = sizes.get(url);
				if (size == null || size.getWidth() == null || size.getHeight() == null) {
					return;
				}
				spus.forEach(spu -> {
					spu.setPicWidth(size.getWidth());
					spu.setPicHeight(size.getHeight());
				});
			});
		}
		catch (Exception exception) {
			// 主图尺寸只影响卡片占位，解析失败不能影响列表返回（前端 1:1 兜底）
			log.warn("商品卡主图尺寸解析失败，按 1:1 兜底 size={}", records.size(), exception);
		}
	}

	private String firstImageUrl(GoodsSpu spu) {
		String[] urls = spu.getSpuUrls();
		return urls == null || urls.length == 0 ? null : urls[0];
	}

	@Override
	public boolean updateSalesVolume(String spuId, Integer buyQuantity) {
		return baseMapper.update(new GoodsSpu(),
				Wrappers.<GoodsSpu>lambdaUpdate()
					.eq(GoodsSpu::getId, spuId)
					.setSql(" sales_volume = sales_volume + " + buyQuantity)) > 0;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean reduceStock(Map<String, Integer> result) {
		for (Map.Entry<String, Integer> entry : result.entrySet()) {
			String spuId = entry.getKey();
			int quantity = entry.getValue();
			if (!StringUtils.hasText(spuId) || quantity <= 0) {
				throw new ArynBusinessException("库存变更参数不合法");
			}
			if (baseMapper.update(new GoodsSpu(),
					Wrappers.<GoodsSpu>lambdaUpdate()
						.eq(GoodsSpu::getId, spuId)
						.ge(GoodsSpu::getStock, quantity)
						.setSql(" stock = stock - " + quantity)) <= 0) {
				throw new ArynBusinessException(MallErrorCodeEnum.ERROR_60008.getCode(),
						MallErrorCodeEnum.ERROR_60008.getMsg());
			}
		}
		return Boolean.TRUE;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean rollbackStock(Map<String, Integer> result) {
		for (Map.Entry<String, Integer> entry : result.entrySet()) {
			String spuId = entry.getKey();
			int quantity = entry.getValue();
			if (!StringUtils.hasText(spuId) || quantity <= 0) {
				throw new ArynBusinessException("库存变更参数不合法");
			}
			if (baseMapper.update(new GoodsSpu(),
					Wrappers.<GoodsSpu>lambdaUpdate()
						.eq(GoodsSpu::getId, spuId)
						.ge(GoodsSpu::getStock, 0)
						.setSql(" stock = stock + " + quantity)) <= 0) {
				throw new ArynBusinessException("回滚库存失败！");
			}
		}
		return Boolean.TRUE;
	}

	@Override
	public List<String> suggestNames(String tenantId, String keyword) {
		// fail-closed：租户缺失宁可不出词，也绝不能退化成跨租户联想
		if (!StringUtils.hasText(tenantId)) {
			return Collections.emptyList();
		}
		String cleaned = sanitizeSuggestKeyword(keyword);
		if (cleaned.isEmpty()) {
			return Collections.emptyList();
		}
		List<String> candidates = baseMapper.selectSuggestNames(tenantId, escapeLikeKeyword(cleaned),
				SUGGEST_CANDIDATE_LIMIT);
		return rankSuggestWords(candidates, cleaned);
	}

	/** 联想词最长输出条数（与前端 MAX_SUGGEST 对齐） */
	private static final int SUGGEST_MAX_WORDS = 10;

	/** 联想候选上限：输出截断前多取一些，给去重/精排留余量 */
	private static final int SUGGEST_CANDIDATE_LIMIT = 50;

	/** 联想关键词最大长度，超长视为无意义输入直接返回空（不抛错，前端无感） */
	private static final int SUGGEST_KEYWORD_MAX_LENGTH = 20;

	/** LIKE 转义符：用 '!' 而非默认反斜杠，避开 MySQL 字符串转义语义的方言歧义 */
	private static final char LIKE_ESCAPE_CHAR = '!';

	/**
	 * LIKE 通配符转义：%/_/转义符本身转成字面量，SQL 侧需配 {@code ESCAPE '!'}。
	 * 不转义时用户输入 % 即可绕过联想语义、触发全租户 LIKE 扫描。
	 */
	static String escapeLikeKeyword(String keyword) {
		StringBuilder escaped = new StringBuilder(keyword.length() + 8);
		for (int i = 0; i < keyword.length(); i++) {
			char c = keyword.charAt(i);
			if (c == LIKE_ESCAPE_CHAR || c == '%' || c == '_') {
				escaped.append(LIKE_ESCAPE_CHAR);
			}
			escaped.append(c);
		}
		return escaped.toString();
	}

	/**
	 * 联想关键词清洗：剔除控制字符与首尾空白，超长判空。返回空串表示无有效输入。
	 */
	private static String sanitizeSuggestKeyword(String keyword) {
		if (!StringUtils.hasText(keyword)) {
			return "";
		}
		StringBuilder visible = new StringBuilder(keyword.length());
		for (int i = 0; i < keyword.length(); i++) {
			char c = keyword.charAt(i);
			if (c > 0x1F && c != 0x7F) {
				visible.append(c);
			}
		}
		String trimmed = visible.toString().trim();
		return trimmed.length() > SUGGEST_KEYWORD_MAX_LENGTH ? "" : trimmed;
	}

	/**
	 * 联想精排：前缀命中优先于包含命中（与前端本地兜底 buildSearchSuggest 同规则），
	 * 忽略大小写去重，与输入完全相同的词不重复推荐。
	 */
	private static List<String> rankSuggestWords(List<String> candidates, String query) {
		if (CollectionUtils.isEmpty(candidates)) {
			return Collections.emptyList();
		}
		String lowerQuery = query.toLowerCase(Locale.ROOT);
		List<String> prefixHits = new ArrayList<>();
		List<String> containsHits = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (String candidate : candidates) {
			if (!StringUtils.hasText(candidate)) {
				continue;
			}
			String lower = candidate.toLowerCase(Locale.ROOT);
			if (!seen.add(lower) || lower.equals(lowerQuery)) {
				continue;
			}
			if (lower.startsWith(lowerQuery)) {
				prefixHits.add(candidate);
			}
			else if (lower.contains(lowerQuery)) {
				containsHits.add(candidate);
			}
		}
		List<String> merged = new ArrayList<>(prefixHits.size() + containsHits.size());
		merged.addAll(prefixHits);
		merged.addAll(containsHits);
		return merged.size() <= SUGGEST_MAX_WORDS ? merged : new ArrayList<>(merged.subList(0, SUGGEST_MAX_WORDS));
	}

}
