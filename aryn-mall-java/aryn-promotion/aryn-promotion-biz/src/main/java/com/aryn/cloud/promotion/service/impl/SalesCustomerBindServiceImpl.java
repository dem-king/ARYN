package com.aryn.cloud.promotion.service.impl;

import cn.hutool.core.convert.Convert;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.dto.SalesCustomerAssignDTO;
import com.aryn.cloud.promotion.api.entity.SalesCustomerBind;
import com.aryn.cloud.promotion.api.entity.SalesCustomerClaim;
import com.aryn.cloud.promotion.api.entity.SalesStaff;
import com.aryn.cloud.promotion.api.enums.SalesCustomerBindSourceEnum;
import com.aryn.cloud.promotion.api.enums.SalesCustomerClaimStatusEnum;
import com.aryn.cloud.promotion.api.vo.SalesCustomerVO;
import com.aryn.cloud.promotion.mapper.SalesCustomerBindMapper;
import com.aryn.cloud.promotion.mapper.SalesCustomerClaimMapper;
import com.aryn.cloud.promotion.service.ISalesCustomerBindService;
import com.aryn.cloud.promotion.service.ISalesStaffService;
import com.aryn.cloud.user.api.vo.UserInfoVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 客户销售归属服务实现
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalesCustomerBindServiceImpl extends ServiceImpl<SalesCustomerBindMapper, SalesCustomerBind>
	implements ISalesCustomerBindService {

	private final ISalesStaffService salesStaffService;

	private final SalesCustomerClaimMapper salesCustomerClaimMapper;

	private final SalesBindWriter salesBindWriter;

	private final SalesUserEnricher salesUserEnricher;

	@Override
	public SalesCustomerBind getByCustomerUserId(String customerUserId) {
		if (customerUserId == null || customerUserId.isBlank()) {
			return null;
		}
		return this.getOne(Wrappers.<SalesCustomerBind>lambdaQuery()
			.eq(SalesCustomerBind::getCustomerUserId, customerUserId)
			.last("limit 1"));
	}

	@Override
	public String getActiveSalesStaffId(String customerUserId) {
		SalesCustomerBind bind = getByCustomerUserId(customerUserId);
		if (bind == null) {
			return null;
		}
		SalesStaff staff = salesStaffService.getActiveById(bind.getSalesStaffId());
		return staff == null ? null : staff.getId();
	}

	@Override
	public IPage<SalesCustomerVO> selectAdminPage(Page<SalesCustomerBind> page, SalesCustomerBind query) {
		SalesCustomerBind condition = query == null ? new SalesCustomerBind() : query;
		IPage<SalesCustomerBind> bindPage = this.page(page, Wrappers.<SalesCustomerBind>lambdaQuery()
			.eq(notBlank(condition.getCustomerUserId()), SalesCustomerBind::getCustomerUserId,
					condition.getCustomerUserId())
			.eq(notBlank(condition.getSalesStaffId()), SalesCustomerBind::getSalesStaffId,
					condition.getSalesStaffId())
			.eq(notBlank(condition.getBindSource()), SalesCustomerBind::getBindSource, condition.getBindSource())
			.orderByDesc(SalesCustomerBind::getBindTime));
		List<String> customerIds = bindPage.getRecords()
			.stream()
			.map(SalesCustomerBind::getCustomerUserId)
			.filter(Objects::nonNull)
			.distinct()
			.toList();
		Map<String, SalesCustomerClaim> claimMap = customerIds.isEmpty() ? Collections.emptyMap()
				: salesCustomerClaimMapper
					.selectList(Wrappers.<SalesCustomerClaim>lambdaQuery()
						.in(SalesCustomerClaim::getCustomerUserId, customerIds))
					.stream()
					.collect(Collectors.toMap(SalesCustomerClaim::getCustomerUserId, Function.identity(),
							(a, b) -> a));
		Map<String, SalesStaff> staffMap = salesStaffService.getStaffMap(bindPage.getRecords()
			.stream()
			.flatMap(bind -> java.util.stream.Stream.of(bind.getSalesStaffId(),
					claimMap.containsKey(bind.getCustomerUserId())
							? claimMap.get(bind.getCustomerUserId()).getSalesStaffId() : null))
			.toList());
		Map<String, UserInfoVO> userMap = salesUserEnricher.getUserMap(customerIds);

		return bindPage.convert(bind -> {
			SalesCustomerVO vo = new SalesCustomerVO();
			BeanUtils.copyProperties(bind, vo);
			SalesStaff staff = staffMap.get(bind.getSalesStaffId());
			if (staff != null) {
				vo.setSalesStaffName(staff.getStaffName());
			}
			SalesCustomerClaim claim = claimMap.get(bind.getCustomerUserId());
			if (claim != null) {
				vo.setPendingClaimStaffId(claim.getSalesStaffId());
				SalesStaff claimStaff = staffMap.get(claim.getSalesStaffId());
				vo.setPendingClaimStaffName(claimStaff == null ? null : claimStaff.getStaffName());
				vo.setClaimStatus(claim.getStatus());
				vo.setClaimExpireTime(claim.getExpireTime());
			}
			UserInfoVO user = userMap.get(bind.getCustomerUserId());
			if (user != null) {
				vo.setNickname(user.getNickname());
				vo.setAvatar(user.getAvatarUrl());
			}
			return vo;
		});
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean assign(SalesCustomerAssignDTO dto) {
		if (dto == null || dto.getCustomerUserId() == null || dto.getCustomerUserId().isBlank()) {
			throw new ArynBusinessException("客户不能为空");
		}
		if (dto.getSalesStaffId() == null || dto.getSalesStaffId().isBlank()) {
			throw new ArynBusinessException("请选择归属销售");
		}
		if (dto.getReason() == null || dto.getReason().isBlank()) {
			throw new ArynBusinessException("请填写改绑原因");
		}
		SalesStaff target = salesStaffService.getActiveById(dto.getSalesStaffId());
		if (target == null) {
			throw new ArynBusinessException("目标销售不存在或已离职");
		}
		SalesCustomerBind bind = getByCustomerUserId(dto.getCustomerUserId());
		if (bind != null && target.getId().equals(bind.getSalesStaffId())) {
			log.info("客户已归属目标销售，改绑幂等返回 customerUserId={}, salesStaffId={}",
					dto.getCustomerUserId(), target.getId());
			return true;
		}
		SalesStaff oldStaff = bind == null ? null : salesStaffService.getStaffMap(List.of(bind.getSalesStaffId()))
			.get(bind.getSalesStaffId());
		salesBindWriter.upsertBind(dto.getCustomerUserId(), target, SalesCustomerBindSourceEnum.ADMIN.getCode(),
				oldStaff, null, dto.getReason());
		int invalidated = salesBindWriter.invalidatePendingClaims(dto.getCustomerUserId());
		log.info("后台改绑客户归属 customerUserId={}, salesStaffId={}, invalidatedClaims={}",
				dto.getCustomerUserId(), target.getId(), invalidated);
		return true;
	}

	@Override
	public Long countBySalesStaffId(String salesStaffId) {
		if (salesStaffId == null || salesStaffId.isBlank()) {
			return 0L;
		}
		return this.baseMapper.selectCount(Wrappers.<SalesCustomerBind>lambdaQuery()
			.eq(SalesCustomerBind::getSalesStaffId, salesStaffId));
	}

	@Override
	public Map<String, Long> countByStaffIds(Collection<String> staffIds) {
		if (staffIds == null || staffIds.isEmpty()) {
			return Collections.emptyMap();
		}
		List<String> ids = staffIds.stream().filter(Objects::nonNull).distinct().toList();
		if (ids.isEmpty()) {
			return Collections.emptyMap();
		}
		QueryWrapper<SalesCustomerBind> wrapper = new QueryWrapper<>();
		wrapper.select("sales_staff_id AS staff_id", "COUNT(*) AS customer_count")
			.in("sales_staff_id", ids)
			.groupBy("sales_staff_id");
		List<Map<String, Object>> rows = this.baseMapper.selectMaps(wrapper);
		Map<String, Long> result = new HashMap<>(rows.size());
		for (Map<String, Object> row : rows) {
			result.put(Convert.toStr(row.get("staff_id")), Convert.toLong(row.get("customer_count"), 0L));
		}
		return result;
	}

	@Override
	public List<SalesCustomerBind> listBySalesStaffId(String salesStaffId) {
		if (salesStaffId == null || salesStaffId.isBlank()) {
			return Collections.emptyList();
		}
		return this.list(Wrappers.<SalesCustomerBind>lambdaQuery()
			.eq(SalesCustomerBind::getSalesStaffId, salesStaffId)
			.orderByAsc(SalesCustomerBind::getBindTime));
	}

	private boolean notBlank(String value) {
		return value != null && !value.isBlank();
	}

}
