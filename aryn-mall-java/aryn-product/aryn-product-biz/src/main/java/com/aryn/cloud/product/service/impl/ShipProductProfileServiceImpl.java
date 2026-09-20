package com.aryn.cloud.product.service.impl;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.product.api.dto.ShipProductProfileDTO;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.api.entity.ProductCodeMapping;
import com.aryn.cloud.product.api.entity.ShipGoodsProfile;
import com.aryn.cloud.product.api.entity.ShipSkuProfile;
import com.aryn.cloud.product.api.enums.SaleScopeEnum;
import com.aryn.cloud.product.api.vo.ShipProductSummaryVO;
import com.aryn.cloud.product.mapper.GoodsSpuMapper;
import com.aryn.cloud.product.mapper.ProductCodeMappingMapper;
import com.aryn.cloud.product.mapper.ShipGoodsProfileMapper;
import com.aryn.cloud.product.mapper.ShipSkuProfileMapper;
import com.aryn.cloud.product.service.IShipProductProfileService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 船供商品资料服务实现。
 *
 * <p>SPU 船供资料、SKU 包装资料与编码映射同事务保存；
 * 编码映射采用“同键复活”策略：同一租户同一编码全生命周期只有一行，
 * 重新启用时更新原行，避免逻辑删除与唯一键冲突。
 *
 * @author aryn
 * @since 2026/9/11
 */
@Service
@RequiredArgsConstructor
public class ShipProductProfileServiceImpl implements IShipProductProfileService {

	private static final String STATUS_ACTIVE = "1";

	private static final String DEL_FLAG_NORMAL = "0";

	private final ShipGoodsProfileMapper shipGoodsProfileMapper;

	private final ShipSkuProfileMapper shipSkuProfileMapper;

	private final ProductCodeMappingMapper productCodeMappingMapper;

	private final GoodsSpuMapper goodsSpuMapper;

	@Override
	@Transactional(rollbackFor = Exception.class)
	public ShipGoodsProfile saveProfile(String tenantId, ShipProductProfileDTO dto) {
		GoodsSpu spu = goodsSpuMapper.selectById(dto.getSpuId());
		if (spu == null || !Objects.equals(spu.getTenantId(), tenantId)) {
			throw new ArynBusinessException("商品不存在");
		}
		ShipGoodsProfile profile = dto.getProfile() != null ? dto.getProfile() : new ShipGoodsProfile();
		profile.setSpuId(dto.getSpuId());
		validateProfile(tenantId, profile, dto.getSkuProfiles());
		upsertProfile(tenantId, profile);
		upsertSkuProfiles(tenantId, dto);
		upsertCodeMappings(tenantId, dto);
		return profile;
	}

	@Override
	public ShipGoodsProfile getProfile(String tenantId, String spuId) {
		return shipGoodsProfileMapper.selectOne(Wrappers.lambdaQuery(ShipGoodsProfile.class)
				.eq(ShipGoodsProfile::getTenantId, tenantId)
				.eq(ShipGoodsProfile::getSpuId, spuId));
	}

	@Override
	public List<ShipSkuProfile> listSkuProfiles(String tenantId, String spuId) {
		return shipSkuProfileMapper.selectList(Wrappers.lambdaQuery(ShipSkuProfile.class)
				.eq(ShipSkuProfile::getTenantId, tenantId)
				.eq(ShipSkuProfile::getSpuId, spuId));
	}

	@Override
	public List<ProductCodeMapping> listCodeMappings(String tenantId, String spuId) {
		return productCodeMappingMapper.selectList(Wrappers.lambdaQuery(ProductCodeMapping.class)
				.eq(ProductCodeMapping::getTenantId, tenantId)
				.eq(ProductCodeMapping::getSpuId, spuId)
				.eq(ProductCodeMapping::getStatus, STATUS_ACTIVE));
	}

	@Override
	public IPage<ShipProductSummaryVO> shipSummaryPage(String tenantId, IPage<ShipProductSummaryVO> page,
			ShipProductSummaryVO query) {
		return shipGoodsProfileMapper.selectShipSummaryPage(page, tenantId, query);
	}

	@Override
	public IPage<ShipProductSummaryVO> personalSummaryPage(String tenantId, IPage<ShipProductSummaryVO> page,
			ShipProductSummaryVO query) {
		return shipGoodsProfileMapper.selectPersonalSummaryPage(page, tenantId, query);
	}

	/**
	 * 校验船供扩展资料。
	 *
	 * <p>商品统一（2026-09-20）后，船供属性是**可选扩展资料**而非商品池闸门：
	 * 未填写 IMPA/ISSA/采购单位等字段的商品同样可以销售，缺省按采购单位=基本单位、
	 * MOQ=1、步长=1 处理。因此这里不再要求「船供可见必须有编码/包装资料」，
	 * 仅在**填写了**相关字段时校验其取值合法性，避免脏数据。
	 */
	private void validateProfile(String tenantId, ShipGoodsProfile profile, List<ShipSkuProfile> skuProfiles) {
		if (StringUtils.hasText(profile.getSaleScope())
				&& SaleScopeEnum.getValue(profile.getSaleScope()) == null) {
			throw new ArynBusinessException("销售范围不合法");
		}
		if (skuProfiles != null) {
			for (ShipSkuProfile skuProfile : skuProfiles) {
				boolean hasQtyRule = skuProfile.getMoq() != null || skuProfile.getStepQty() != null;
				if (skuProfile.getMoq() != null && skuProfile.getMoq() < 1) {
					throw new ArynBusinessException("最小起订量必须大于 0");
				}
				if (skuProfile.getStepQty() != null && skuProfile.getStepQty() <= 0) {
					throw new ArynBusinessException("数量步长必须大于 0");
				}
				if (hasQtyRule && skuProfile.getMoq() != null && skuProfile.getStepQty() != null
						&& skuProfile.getMoq() % skuProfile.getStepQty() != 0) {
					throw new ArynBusinessException("最小起订量必须是数量步长的整数倍");
				}
			}
		}
		profile.setPublishCompleteness(computeCompleteness(profile));
	}

	/**
	 * 资料完整度：按船供关键资料字段填充比例计算（0-100）。
	 */
	public static int computeCompleteness(ShipGoodsProfile profile) {
		List<Object> fields = java.util.Arrays.asList(profile.getSaleScope(), profile.getImpaCode(),
				profile.getIssaCode(), profile.getInternalItemCode(), profile.getBarcode(), profile.getNameEn(),
				profile.getSearchAliases(), profile.getStorageType(), profile.getShelfLifeDays(),
				profile.getTemperatureRequirement(), profile.getShipSupplyRemark());
		long filled = fields.stream().filter(Objects::nonNull).filter(v -> StringUtils.hasText(v.toString())).count();
		return (int) Math.round(filled * 100.0 / fields.size());
	}

	private void upsertProfile(String tenantId, ShipGoodsProfile profile) {
		ShipGoodsProfile existing = getProfile(tenantId, profile.getSpuId());
		if (existing == null) {
			profile.setId(null);
			profile.setTenantId(tenantId);
			profile.setCreateTime(LocalDateTime.now());
			profile.setDelFlag(DEL_FLAG_NORMAL);
			shipGoodsProfileMapper.insert(profile);
		}
		else {
			profile.setId(existing.getId());
			profile.setTenantId(tenantId);
			profile.setUpdateTime(LocalDateTime.now());
			shipGoodsProfileMapper.updateById(profile);
		}
	}

	private void upsertSkuProfiles(String tenantId, ShipProductProfileDTO dto) {
		if (dto.getSkuProfiles() == null) {
			return;
		}
		for (ShipSkuProfile skuProfile : dto.getSkuProfiles()) {
			if (!StringUtils.hasText(skuProfile.getSkuId())) {
				throw new ArynBusinessException("SKU 包装资料缺少 SKU ID");
			}
			ShipSkuProfile existing = shipSkuProfileMapper.selectOne(Wrappers.lambdaQuery(ShipSkuProfile.class)
					.eq(ShipSkuProfile::getTenantId, tenantId)
					.eq(ShipSkuProfile::getSkuId, skuProfile.getSkuId()));
			skuProfile.setSpuId(dto.getSpuId());
			if (existing == null) {
				skuProfile.setId(null);
				skuProfile.setTenantId(tenantId);
				skuProfile.setCreateTime(LocalDateTime.now());
				skuProfile.setDelFlag(DEL_FLAG_NORMAL);
				shipSkuProfileMapper.insert(skuProfile);
			}
			else {
				skuProfile.setId(existing.getId());
				skuProfile.setTenantId(tenantId);
				skuProfile.setUpdateTime(LocalDateTime.now());
				shipSkuProfileMapper.updateById(skuProfile);
			}
		}
	}

	private void upsertCodeMappings(String tenantId, ShipProductProfileDTO dto) {
		List<ProductCodeMapping> mappings = dto.getCodeMappings() != null ? dto.getCodeMappings() : List.of();
		Set<String> requestedKeys = new HashSet<>();
		for (ProductCodeMapping mapping : mappings) {
			if (!StringUtils.hasText(mapping.getCodeType()) || !StringUtils.hasText(mapping.getCodeValue())) {
				throw new ArynBusinessException("编码映射缺少编码类型或编码值");
			}
			String key = mapping.getCodeType() + ":" + mapping.getCodeValue();
			if (!requestedKeys.add(key)) {
				throw new ArynBusinessException("编码映射存在重复编码：" + mapping.getCodeValue());
			}
			validateCodeUniqueness(tenantId, mapping, dto.getSpuId());
			ProductCodeMapping existing = productCodeMappingMapper.selectByCodeIgnoreDelFlag(tenantId,
					mapping.getCodeType(), mapping.getCodeValue());
			if (existing == null) {
				mapping.setId(null);
				mapping.setTenantId(tenantId);
				mapping.setSpuId(dto.getSpuId());
				mapping.setStatus(STATUS_ACTIVE);
				if (!StringUtils.hasText(mapping.getMatchSource())) {
					mapping.setMatchSource(ProductCodeMapping.SOURCE_MANUAL);
				}
				mapping.setCreateTime(LocalDateTime.now());
				mapping.setDelFlag(DEL_FLAG_NORMAL);
				productCodeMappingMapper.insert(mapping);
			}
			else {
				existing.setSkuId(mapping.getSkuId());
				existing.setSpuId(dto.getSpuId());
				existing.setMatchSource(StringUtils.hasText(mapping.getMatchSource()) ? mapping.getMatchSource()
						: existing.getMatchSource());
				existing.setConfidence(mapping.getConfidence());
				existing.setStatus(STATUS_ACTIVE);
				existing.setUpdateTime(LocalDateTime.now());
				productCodeMappingMapper.updateById(existing);
			}
		}
	}

	/**
	 * 同一租户同一编码只能绑定一个有效 SKU：与其他 SPU/SKU 的有效映射冲突时报错。
	 */
	private void validateCodeUniqueness(String tenantId, ProductCodeMapping mapping, String spuId) {
		List<ProductCodeMapping> conflicts = productCodeMappingMapper.selectActiveByCodeValues(tenantId,
				List.of(mapping.getCodeValue()));
		for (ProductCodeMapping conflict : conflicts) {
			if (!Objects.equals(conflict.getCodeType(), mapping.getCodeType())) {
				continue;
			}
			if (!Objects.equals(conflict.getSpuId(), spuId)) {
				throw new ArynBusinessException("编码已被其他商品绑定：" + mapping.getCodeValue());
			}
			if (StringUtils.hasText(mapping.getSkuId()) && StringUtils.hasText(conflict.getSkuId())
					&& !Objects.equals(conflict.getSkuId(), mapping.getSkuId())) {
				throw new ArynBusinessException("编码已被同一商品的其他 SKU 绑定：" + mapping.getCodeValue());
			}
		}
	}

}
