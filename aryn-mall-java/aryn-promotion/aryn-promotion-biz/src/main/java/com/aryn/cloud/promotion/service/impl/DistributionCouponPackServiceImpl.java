package com.aryn.cloud.promotion.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.dto.DistributionCouponPackSaveDTO;
import com.aryn.cloud.promotion.api.entity.CouponInfo;
import com.aryn.cloud.promotion.api.entity.DistributionCouponPack;
import com.aryn.cloud.promotion.api.entity.DistributionCouponPackItem;
import com.aryn.cloud.promotion.api.enums.DistributionCouponPackStatusEnum;
import com.aryn.cloud.promotion.api.vo.DistributionCouponPackVO;
import com.aryn.cloud.promotion.mapper.CouponInfoMapper;
import com.aryn.cloud.promotion.mapper.DistributionCouponPackItemMapper;
import com.aryn.cloud.promotion.mapper.DistributionCouponPackMapper;
import com.aryn.cloud.promotion.service.IDistributionCouponPackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 分销佣金兑换券包
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DistributionCouponPackServiceImpl implements IDistributionCouponPackService {

	private static final BigDecimal ZERO = BigDecimal.ZERO;

	private final DistributionCouponPackMapper distributionCouponPackMapper;

	private final DistributionCouponPackItemMapper distributionCouponPackItemMapper;

	private final CouponInfoMapper couponInfoMapper;

	@Override
	public IPage<DistributionCouponPackVO> getPage(Page<DistributionCouponPack> page, DistributionCouponPack query) {
		DistributionCouponPack safeQuery = query == null ? new DistributionCouponPack() : query;
		IPage<DistributionCouponPack> result = distributionCouponPackMapper.selectPage(page,
			Wrappers.<DistributionCouponPack>lambdaQuery()
				.like(StringUtils.hasText(safeQuery.getPackName()), DistributionCouponPack::getPackName,
					safeQuery.getPackName())
				.eq(StringUtils.hasText(safeQuery.getStatus()), DistributionCouponPack::getStatus, safeQuery.getStatus())
				.orderByAsc(DistributionCouponPack::getSort)
				.orderByDesc(DistributionCouponPack::getCreateTime));
		Page<DistributionCouponPackVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
		List<DistributionCouponPackVO> records = result.getRecords().stream().map(this::toVo).toList();
		fillCouponInfos(records);
		voPage.setRecords(records);
		return voPage;
	}

	@Override
	public List<DistributionCouponPackVO> listAll() {
		return toVoList(distributionCouponPackMapper.selectList(Wrappers.<DistributionCouponPack>lambdaQuery()
			.orderByAsc(DistributionCouponPack::getSort)
			.orderByDesc(DistributionCouponPack::getCreateTime)));
	}

	@Override
	public DistributionCouponPackVO getDetail(String id) {
		if (!StringUtils.hasText(id)) {
			throw new ArynBusinessException("券包ID不能为空");
		}
		DistributionCouponPack pack = distributionCouponPackMapper.selectById(id);
		if (pack == null) {
			throw new ArynBusinessException("券包不存在");
		}
		DistributionCouponPackVO vo = toVo(pack);
		fillCouponInfos(List.of(vo));
		return vo;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public Boolean savePack(DistributionCouponPackSaveDTO dto) {
		validatePack(dto);
		DistributionCouponPack pack = buildPack(dto, null);
		pack.setId(null);
		if (distributionCouponPackMapper.insert(pack) <= 0) {
			throw new ArynBusinessException("券包保存失败，请重试");
		}
		saveItems(pack, dto.getItems());
		log.info("新增分销券包成功 packId={}, packName={}, requiredCommission={}", pack.getId(), pack.getPackName(),
			pack.getRequiredCommission());
		return Boolean.TRUE;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public Boolean updatePack(DistributionCouponPackSaveDTO dto) {
		if (dto == null || !StringUtils.hasText(dto.getId())) {
			throw new ArynBusinessException("券包ID不能为空");
		}
		DistributionCouponPack exists = distributionCouponPackMapper.selectById(dto.getId());
		if (exists == null) {
			throw new ArynBusinessException("券包不存在");
		}
		validatePack(dto);
		DistributionCouponPack pack = buildPack(dto, exists);
		pack.setId(dto.getId());
		// 使用显式 set 更新：编辑时允许把「有效期/门槛/库存」清空为 NULL（NULL 库存=不限）
		int rows = distributionCouponPackMapper.update(null, Wrappers.<DistributionCouponPack>lambdaUpdate()
			.eq(DistributionCouponPack::getId, dto.getId())
			.set(DistributionCouponPack::getPackName, pack.getPackName())
			.set(DistributionCouponPack::getFaceAmount, pack.getFaceAmount())
			.set(DistributionCouponPack::getThresholdAmount, pack.getThresholdAmount())
			.set(DistributionCouponPack::getValidDays, pack.getValidDays())
			.set(DistributionCouponPack::getRequiredCommission, pack.getRequiredCommission())
			.set(DistributionCouponPack::getStock, pack.getStock())
			.set(DistributionCouponPack::getStatus, pack.getStatus())
			.set(DistributionCouponPack::getSort, pack.getSort())
			.set(DistributionCouponPack::getRemark, pack.getRemark())
			.set(DistributionCouponPack::getUpdateTime, LocalDateTime.now()));
		if (rows <= 0) {
			throw new ArynBusinessException("券包不存在或已删除");
		}
		saveItems(pack, dto.getItems());
		log.info("编辑分销券包成功 packId={}, packName={}", pack.getId(), pack.getPackName());
		return Boolean.TRUE;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public Boolean updateStatus(String id, String status) {
		if (!StringUtils.hasText(id)) {
			throw new ArynBusinessException("券包ID不能为空");
		}
		if (!DistributionCouponPackStatusEnum.STATUS_0.getCode().equals(status)
				&& !DistributionCouponPackStatusEnum.STATUS_1.getCode().equals(status)) {
			throw new ArynBusinessException("券包状态不合法");
		}
		DistributionCouponPack pack = distributionCouponPackMapper.selectById(id);
		if (pack == null) {
			throw new ArynBusinessException("券包不存在");
		}
		pack.setStatus(status);
		if (distributionCouponPackMapper.updateById(pack) <= 0) {
			throw new ArynBusinessException("券包状态更新失败，请重试");
		}
		log.info("券包上下架 packId={}, status={}", id, status);
		return Boolean.TRUE;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public Boolean removePack(String id) {
		if (!StringUtils.hasText(id)) {
			throw new ArynBusinessException("券包ID不能为空");
		}
		DistributionCouponPack pack = distributionCouponPackMapper.selectById(id);
		if (pack == null) {
			throw new ArynBusinessException("券包不存在");
		}
		// 明细与主表一致走逻辑删除，保留历史配置与兑换记录的可追溯性
		distributionCouponPackItemMapper.delete(Wrappers.<DistributionCouponPackItem>lambdaQuery()
			.eq(DistributionCouponPackItem::getPackId, id));
		if (distributionCouponPackMapper.deleteById(id) <= 0) {
			throw new ArynBusinessException("券包删除失败，请重试");
		}
		log.info("逻辑删除分销券包 packId={}, packName={}", id, pack.getPackName());
		return Boolean.TRUE;
	}

	@Override
	public List<DistributionCouponPackVO> listOnShelf() {
		List<DistributionCouponPack> packs = distributionCouponPackMapper.selectList(
			Wrappers.<DistributionCouponPack>lambdaQuery()
				.eq(DistributionCouponPack::getStatus, DistributionCouponPackStatusEnum.STATUS_0.getCode())
				.and(wrapper -> wrapper.isNull(DistributionCouponPack::getStock)
					.or().gt(DistributionCouponPack::getStock, 0))
				.orderByAsc(DistributionCouponPack::getSort)
				.orderByDesc(DistributionCouponPack::getCreateTime));
		return toVoList(packs);
	}

	private List<DistributionCouponPackVO> toVoList(List<DistributionCouponPack> packs) {
		List<DistributionCouponPackVO> records = packs.stream().map(this::toVo).toList();
		fillCouponInfos(records);
		return records;
	}

	private DistributionCouponPackVO toVo(DistributionCouponPack pack) {
		if (pack == null) {
			return null;
		}
		DistributionCouponPackVO vo = new DistributionCouponPackVO();
		BeanUtils.copyProperties(pack, vo);
		return vo;
	}

	/**
	 * 批量回填券包内券模板的名称/面额，避免管理端逐行查询。
	 */
	private void fillCouponInfos(List<DistributionCouponPackVO> packs) {
		if (CollectionUtils.isEmpty(packs)) {
			return;
		}
		List<String> packIds = packs.stream().map(DistributionCouponPackVO::getId).filter(StringUtils::hasText)
			.distinct().toList();
		if (CollectionUtils.isEmpty(packIds)) {
			return;
		}
		List<DistributionCouponPackItem> items = distributionCouponPackItemMapper.selectList(
			Wrappers.<DistributionCouponPackItem>lambdaQuery()
				.in(DistributionCouponPackItem::getPackId, packIds));
		if (CollectionUtils.isEmpty(items)) {
			return;
		}
		Map<String, CouponInfo> templateMap = loadTemplateMap(items.stream()
			.map(DistributionCouponPackItem::getCouponInfoId).filter(StringUtils::hasText).distinct().toList());
		Map<String, List<DistributionCouponPackVO.Item>> itemMap = new LinkedHashMap<>();
		for (DistributionCouponPackItem item : items) {
			DistributionCouponPackVO.Item voItem = new DistributionCouponPackVO.Item();
			voItem.setCouponInfoId(item.getCouponInfoId());
			voItem.setQuantity(item.getQuantity());
			CouponInfo template = templateMap.get(item.getCouponInfoId());
			if (template != null) {
				voItem.setName(template.getCouponName());
				voItem.setFaceAmount(template.getAmount());
			}
			itemMap.computeIfAbsent(item.getPackId(), key -> new ArrayList<>()).add(voItem);
		}
		packs.forEach(pack -> pack.setCouponInfos(itemMap.getOrDefault(pack.getId(), List.of())));
	}

	private Map<String, CouponInfo> loadTemplateMap(List<String> couponInfoIds) {
		if (CollectionUtils.isEmpty(couponInfoIds)) {
			return Map.of();
		}
		return couponInfoMapper.selectList(Wrappers.<CouponInfo>lambdaQuery()
			.in(CouponInfo::getId, couponInfoIds))
			.stream()
			.collect(Collectors.toMap(CouponInfo::getId, Function.identity(), (left, right) -> left));
	}

	private void validatePack(DistributionCouponPackSaveDTO dto) {
		if (dto == null) {
			throw new ArynBusinessException("券包配置不能为空");
		}
		if (!StringUtils.hasText(dto.getPackName())) {
			throw new ArynBusinessException("请填写券包名称");
		}
		if (dto.getFaceAmount() == null || dto.getFaceAmount().compareTo(ZERO) < 0) {
			throw new ArynBusinessException("券包面额不能为空或负数");
		}
		if (dto.getRequiredCommission() == null || dto.getRequiredCommission().compareTo(ZERO) < 0) {
			throw new ArynBusinessException("消耗佣金不能为空或负数");
		}
		if (dto.getThresholdAmount() != null && dto.getThresholdAmount().compareTo(ZERO) < 0) {
			throw new ArynBusinessException("使用门槛不能为负数");
		}
		if (dto.getStock() != null && dto.getStock() < 0) {
			throw new ArynBusinessException("库存不能为负数，不限库存请留空");
		}
		if (dto.getValidDays() != null && dto.getValidDays() <= 0) {
			throw new ArynBusinessException("有效期必须大于 0 天");
		}
		if (StringUtils.hasText(dto.getStatus())
				&& !DistributionCouponPackStatusEnum.STATUS_0.getCode().equals(dto.getStatus())
				&& !DistributionCouponPackStatusEnum.STATUS_1.getCode().equals(dto.getStatus())) {
			throw new ArynBusinessException("券包状态不合法");
		}
		validateItems(dto.getItems());
	}

	private void validateItems(List<DistributionCouponPackSaveDTO.Item> items) {
		if (CollectionUtils.isEmpty(items)) {
			throw new ArynBusinessException("请至少关联一个优惠券模板");
		}
		Set<String> couponInfoIds = new HashSet<>();
		for (DistributionCouponPackSaveDTO.Item item : items) {
			if (item == null || !StringUtils.hasText(item.getCouponInfoId())) {
				throw new ArynBusinessException("优惠券模板不能为空");
			}
			if (item.getQuantity() != null && item.getQuantity() <= 0) {
				throw new ArynBusinessException("券包内优惠券数量必须大于 0");
			}
			if (!couponInfoIds.add(item.getCouponInfoId())) {
				throw new ArynBusinessException("券包内优惠券模板不能重复");
			}
		}
		Map<String, CouponInfo> templates = loadTemplateMap(new ArrayList<>(couponInfoIds));
		List<String> missing = couponInfoIds.stream().filter(id -> !templates.containsKey(id)).toList();
		if (!missing.isEmpty()) {
			throw new ArynBusinessException("优惠券模板不存在：" + String.join(",", missing));
		}
	}

	private DistributionCouponPack buildPack(DistributionCouponPackSaveDTO dto, DistributionCouponPack existing) {
		DistributionCouponPack pack = new DistributionCouponPack();
		BeanUtils.copyProperties(dto, pack);
		if (!StringUtils.hasText(pack.getStatus())) {
			pack.setStatus(existing == null ? DistributionCouponPackStatusEnum.STATUS_0.getCode()
					: existing.getStatus());
		}
		if (pack.getSort() == null) {
			pack.setSort(existing == null ? 0 : existing.getSort());
		}
		return pack;
	}

	/**
	 * 明细按模板ID做增量对齐：已存在只改数量、缺失的先尝试恢复历史逻辑删除行、再删除不再需要的明细。
	 */
	private void saveItems(DistributionCouponPack pack, List<DistributionCouponPackSaveDTO.Item> items) {
		List<DistributionCouponPackItem> existing = distributionCouponPackItemMapper.selectList(
			Wrappers.<DistributionCouponPackItem>lambdaQuery()
				.eq(DistributionCouponPackItem::getPackId, pack.getId()));
		Map<String, DistributionCouponPackItem> existingByCouponId = existing.stream()
			.collect(Collectors.toMap(DistributionCouponPackItem::getCouponInfoId, Function.identity(),
				(left, right) -> left));
		String tenantId = StringUtils.hasText(pack.getTenantId()) ? pack.getTenantId()
				: ArynTenantContextHolder.getTenantId();
		Set<String> desired = new LinkedHashSet<>();
		for (DistributionCouponPackSaveDTO.Item item : items) {
			desired.add(item.getCouponInfoId());
			int quantity = item.getQuantity() == null ? 1 : item.getQuantity();
			DistributionCouponPackItem exists = existingByCouponId.get(item.getCouponInfoId());
			if (exists != null) {
				if (!Objects.equals(exists.getQuantity(), quantity)) {
					exists.setQuantity(quantity);
					distributionCouponPackItemMapper.updateById(exists);
				}
				continue;
			}
			if (StringUtils.hasText(tenantId) && distributionCouponPackItemMapper.restoreDeletedItem(tenantId,
					pack.getId(), item.getCouponInfoId(), quantity) > 0) {
				continue;
			}
			DistributionCouponPackItem created = new DistributionCouponPackItem()
				.setPackId(pack.getId())
				.setCouponInfoId(item.getCouponInfoId())
				.setQuantity(quantity)
				.setTenantId(tenantId);
			if (distributionCouponPackItemMapper.insert(created) <= 0) {
				throw new ArynBusinessException("券包优惠券关联保存失败，请重试");
			}
		}
		existing.stream()
			.filter(item -> !desired.contains(item.getCouponInfoId()))
			.forEach(item -> distributionCouponPackItemMapper.deleteById(item.getId()));
	}

}
