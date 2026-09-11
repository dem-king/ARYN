package com.aryn.cloud.product.service.impl;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.product.api.dto.ProductImportRowDTO;
import com.aryn.cloud.product.api.dto.ShipProductProfileDTO;
import com.aryn.cloud.product.api.entity.GoodsBrand;
import com.aryn.cloud.product.api.entity.GoodsCategory;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.api.entity.ProductChangeLog;
import com.aryn.cloud.product.api.entity.ProductCodeMapping;
import com.aryn.cloud.product.api.entity.ProductImportError;
import com.aryn.cloud.product.api.entity.ProductImportJob;
import com.aryn.cloud.product.api.entity.ProductImportRow;
import com.aryn.cloud.product.api.entity.ShipGoodsProfile;
import com.aryn.cloud.product.api.entity.ShipSkuProfile;
import com.aryn.cloud.product.api.vo.ProductImportPreviewVO;
import com.aryn.cloud.product.mapper.GoodsBrandMapper;
import com.aryn.cloud.product.mapper.GoodsCategoryMapper;
import com.aryn.cloud.product.mapper.GoodsSkuMapper;
import com.aryn.cloud.product.mapper.GoodsSpuMapper;
import com.aryn.cloud.product.mapper.ProductChangeLogMapper;
import com.aryn.cloud.product.mapper.ProductCodeMappingMapper;
import com.aryn.cloud.product.mapper.ProductImportErrorMapper;
import com.aryn.cloud.product.mapper.ProductImportJobMapper;
import com.aryn.cloud.product.mapper.ProductImportRowMapper;
import com.aryn.cloud.product.service.IShipProductProfileService;
import com.aryn.cloud.product.service.ProductImportService;
import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 商品批量导入服务实现。
 *
 * <p>Excel 由服务端解析（EasyExcel，首行中文标题映射字段），解析行持久化到
 * product_import_row；确认导入时服务端从行表重新校验后写入。更新商品只能按
 * SKU ID、IMPA 或内部编码匹配，禁止按名称覆盖；错误行不导入并全部落库。
 *
 * @author aryn
 * @since 2026/9/11
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductImportServiceImpl implements ProductImportService {

	private static final int MAX_IMPORT_ROWS = 10000;

	private final ProductImportJobMapper productImportJobMapper;

	private final ProductImportRowMapper productImportRowMapper;

	private final ProductImportErrorMapper productImportErrorMapper;

	private final ProductChangeLogMapper productChangeLogMapper;

	private final GoodsSpuMapper goodsSpuMapper;

	private final GoodsSkuMapper goodsSkuMapper;

	private final GoodsCategoryMapper goodsCategoryMapper;

	private final GoodsBrandMapper goodsBrandMapper;

	private final ProductCodeMappingMapper productCodeMappingMapper;

	private final IShipProductProfileService shipProductProfileService;

	private final ObjectMapper objectMapper;

	@Value("${hx.ship-import.max-rows:10000}")
	private int maxRows = MAX_IMPORT_ROWS;

	@Override
	@Transactional(rollbackFor = Exception.class)
	public ProductImportPreviewVO previewExcel(String tenantId, String fileName, InputStream excelStream) {
		List<ProductImportRowDTO> rows = ProductExcelConverter.parse(excelStream);
		if (rows.isEmpty()) {
			throw new ArynBusinessException("Excel 未解析到数据行，请按模板填写后重试");
		}
		return preview(tenantId, fileName, rows);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public ProductImportPreviewVO preview(String tenantId, String fileName, List<ProductImportRowDTO> rows) {
		List<ProductImportRowDTO> safeRows = rows != null ? rows : List.of();
		if (safeRows.size() > maxRows) {
			throw new ArynBusinessException("单次导入最多 " + maxRows + " 行，请拆分文件");
		}
		ProductImportJob job = new ProductImportJob();
		job.setId(IdWorker.getIdStr());
		job.setJobNo("PI" + IdWorker.getIdStr());
		job.setFileName(fileName);
		job.setTotalRows(safeRows.size());
		job.setStatus(ProductImportJob.STATUS_PENDING_CONFIRM);
		job.setTenantId(tenantId);
		job.setCreateTime(LocalDateTime.now());
		job.setDelFlag("0");
		productImportJobMapper.insert(job);

		ValidationResult result = validateRows(tenantId, safeRows);
		persistRows(tenantId, job.getId(), safeRows, result.errorRowNos());
		persistErrors(tenantId, job.getId(), result.errors());

		ProductImportPreviewVO vo = new ProductImportPreviewVO();
		vo.setJobId(job.getId());
		vo.setTotalRows(safeRows.size());
		vo.setSuccessRows(safeRows.size() - result.errors().size());
		vo.setErrorRows(result.errors().size());
		vo.setConfirmable(vo.getSuccessRows() > 0);
		vo.setErrors(result.errors());
		return vo;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public int confirmImport(String tenantId, String jobId, String operatorId, String operatorName) {
		ProductImportJob job = productImportJobMapper.selectById(jobId);
		if (job == null || !Objects.equals(job.getTenantId(), tenantId)) {
			throw new ArynBusinessException("导入任务不存在");
		}
		if (!ProductImportJob.STATUS_PENDING_CONFIRM.equals(job.getStatus())) {
			throw new ArynBusinessException("导入任务当前状态不可确认");
		}
		List<ProductImportRow> storedRows = productImportRowMapper.selectByJobId(tenantId, jobId);
		List<ProductImportRowDTO> rows = storedRows.stream().map(this::deserializeRow).toList();
		ValidationResult result = validateRows(tenantId, rows);

		job.setStatus(ProductImportJob.STATUS_IMPORTING);
		productImportJobMapper.updateById(job);

		int imported = 0;
		for (ProductImportRowDTO row : rows) {
			if (result.errorRowNos().contains(row.getRowNo())) {
				continue;
			}
			importRow(tenantId, row, operatorId, operatorName);
			imported++;
		}

		job.setStatus(ProductImportJob.STATUS_COMPLETED);
		job.setSuccessRows(imported);
		job.setErrorRows(result.errors().size());
		job.setCompletedTime(LocalDateTime.now());
		productImportJobMapper.updateById(job);

		ProductChangeLog jobLog = changeLog(tenantId, ProductChangeLog.BIZ_IMPORT_JOB, job.getId(),
				ProductChangeLog.CHANGE_UPDATE, operatorId, operatorName);
		jobLog.setAfterSnapshot("{\"imported\":" + imported + ",\"errors\":" + result.errors().size() + "}");
		productChangeLogMapper.insert(jobLog);
		return imported;
	}

	@Override
	public List<ProductImportError> listErrors(String tenantId, String jobId) {
		return productImportErrorMapper.selectList(Wrappers.lambdaQuery(ProductImportError.class)
			.eq(ProductImportError::getTenantId, tenantId)
			.eq(ProductImportError::getJobId, jobId)
			.orderByAsc(ProductImportError::getRowNo));
	}

	// ---------------------------------------------------------------------
	// 校验
	// ---------------------------------------------------------------------

	private record ValidationResult(List<ProductImportError> errors, Set<Integer> errorRowNos) {
	}

	private ValidationResult validateRows(String tenantId, List<ProductImportRowDTO> rows) {
		List<ProductImportError> errors = new ArrayList<>();
		Set<Integer> errorRowNos = new HashSet<>();
		Set<String> batchCodeValues = new HashSet<>();
		Set<String> batchSkuValues = new HashSet<>();
		int rowNo = 0;
		for (ProductImportRowDTO row : rows) {
			rowNo++;
			row.setRowNo(row.getRowNo() != null ? row.getRowNo() : rowNo);
			int before = errors.size();
			validateRow(tenantId, row, rowNo, errors, batchCodeValues, batchSkuValues);
			if (errors.size() > before) {
				errorRowNos.add(rowNo);
			}
		}
		return new ValidationResult(errors, errorRowNos);
	}

	private void validateRow(String tenantId, ProductImportRowDTO row, int rowNo, List<ProductImportError> errors,
			Set<String> batchCodeValues, Set<String> batchSkuValues) {
		if (!StringUtils.hasText(row.getName())) {
			addError(tenantId, rowNo, ProductImportError.TYPE_EMPTY_NAME, "商品名称为空", errors);
			return;
		}
		for (String codeValue : new String[] { row.getImpaCode(), row.getIssaCode(), row.getInternalItemCode() }) {
			if (StringUtils.hasText(codeValue) && !batchCodeValues.add(codeValue)) {
				addError(tenantId, rowNo, ProductImportError.TYPE_DUPLICATE_CODE, "同批次重复编码：" + codeValue, errors);
				return;
			}
		}
		String skuKey = StringUtils.hasText(row.getSkuId()) ? row.getSkuId()
				: "SKU".equals(row.getMatchType()) ? row.getMatchValue() : null;
		if (StringUtils.hasText(skuKey) && !batchSkuValues.add(skuKey)) {
			addError(tenantId, rowNo, ProductImportError.TYPE_DUPLICATE_SKU, "同批次重复SKU：" + skuKey, errors);
			return;
		}
		if (row.getSalesPrice() != null && row.getSalesPrice().compareTo(BigDecimal.ZERO) < 0) {
			addError(tenantId, rowNo, ProductImportError.TYPE_ILLEGAL_PRICE, "售价不能为负数", errors);
			return;
		}
		if (row.getStock() != null && row.getStock() < 0) {
			addError(tenantId, rowNo, ProductImportError.TYPE_ILLEGAL_STOCK, "库存不能为负数", errors);
			return;
		}
		if (row.getMoq() != null || row.getStepQty() != null) {
			int moq = row.getMoq() != null ? row.getMoq() : 1;
			int stepQty = row.getStepQty() != null ? row.getStepQty() : 1;
			if (stepQty <= 0 || moq < 1 || moq % stepQty != 0) {
				addError(tenantId, rowNo, ProductImportError.TYPE_ILLEGAL_QTY_RULE, "MOQ/步长不合规", errors);
				return;
			}
		}
		if (StringUtils.hasText(row.getCategorySecondId())
				&& goodsCategoryMapper.selectById(row.getCategorySecondId()) == null) {
			addError(tenantId, rowNo, ProductImportError.TYPE_UNKNOWN_CATEGORY, "类目不存在", errors);
			return;
		}
		if (StringUtils.hasText(row.getBrandId()) && goodsBrandMapper.selectById(row.getBrandId()) == null) {
			addError(tenantId, rowNo, ProductImportError.TYPE_UNKNOWN_BRAND, "品牌不存在", errors);
			return;
		}
		if (StringUtils.hasText(row.getMatchType())) {
			validateUpdateTarget(tenantId, row, rowNo, errors);
		}
	}

	private void validateUpdateTarget(String tenantId, ProductImportRowDTO row, int rowNo,
			List<ProductImportError> errors) {
		String matchType = row.getMatchType();
		String matchValue = row.getMatchValue();
		if (!StringUtils.hasText(matchValue)) {
			addError(tenantId, rowNo, ProductImportError.TYPE_MISSING_UPDATE_TARGET, "更新匹配值缺失", errors);
			return;
		}
		switch (matchType) {
			case "SKU" -> {
				GoodsSku sku = goodsSkuMapper.selectById(matchValue);
				if (sku == null || !Objects.equals(sku.getTenantId(), tenantId)) {
					addError(tenantId, rowNo, ProductImportError.TYPE_MISSING_UPDATE_TARGET, "SKU 不存在或不属于当前租户",
							errors);
				}
			}
			case "IMPA", "INTERNAL" -> {
				String codeType = "IMPA".equals(matchType) ? ProductCodeMapping.TYPE_IMPA
						: ProductCodeMapping.TYPE_INTERNAL;
				ProductCodeMapping mapping = productCodeMappingMapper.selectByCodeGlobal(codeType, matchValue);
				if (mapping == null) {
					addError(tenantId, rowNo, ProductImportError.TYPE_MISSING_UPDATE_TARGET,
							matchType + " 编码未匹配到商品", errors);
				}
				else if (!Objects.equals(mapping.getTenantId(), tenantId)) {
					addError(tenantId, rowNo, ProductImportError.TYPE_CROSS_TENANT_CODE, "编码归属其他租户：" + matchValue,
							errors);
				}
			}
			default -> addError(tenantId, rowNo, ProductImportError.TYPE_OTHER, "不支持的匹配方式：" + matchType, errors);
		}
	}

	private void addError(String tenantId, int rowNo, String errorType, String message,
			List<ProductImportError> errors) {
		ProductImportError error = new ProductImportError();
		error.setId(IdWorker.getIdStr());
		error.setRowNo(rowNo);
		error.setErrorType(errorType);
		error.setErrorMessage(message);
		error.setTenantId(tenantId);
		error.setCreateTime(LocalDateTime.now());
		error.setDelFlag("0");
		errors.add(error);
	}

	private void persistErrors(String tenantId, String jobId, List<ProductImportError> errors) {
		for (ProductImportError error : errors) {
			error.setJobId(jobId);
			productImportErrorMapper.insert(error);
		}
	}

	private void persistRows(String tenantId, String jobId, List<ProductImportRowDTO> rows, Set<Integer> errorRowNos) {
		for (ProductImportRowDTO row : rows) {
			ProductImportRow storedRow = new ProductImportRow();
			storedRow.setId(IdWorker.getIdStr());
			storedRow.setJobId(jobId);
			storedRow.setRowNo(row.getRowNo());
			storedRow.setRowContent(serializeRow(row));
			storedRow.setValidFlag(errorRowNos.contains(row.getRowNo()) ? ProductImportRow.VALID_FLAG_ERROR
					: ProductImportRow.VALID_FLAG_OK);
			storedRow.setTenantId(tenantId);
			storedRow.setCreateTime(LocalDateTime.now());
			storedRow.setDelFlag("0");
			productImportRowMapper.insert(storedRow);
		}
	}

	private String serializeRow(ProductImportRowDTO row) {
		try {
			return objectMapper.writeValueAsString(row);
		}
		catch (Exception ex) {
			throw new ArynBusinessException("导入行序列化失败：" + row.getRowNo());
		}
	}

	private ProductImportRowDTO deserializeRow(ProductImportRow storedRow) {
		try {
			ProductImportRowDTO row = objectMapper.readValue(storedRow.getRowContent(), ProductImportRowDTO.class);
			row.setRowNo(storedRow.getRowNo());
			return row;
		}
		catch (Exception ex) {
			log.error("导入行反序列化失败 jobId={} rowNo={}", storedRow.getJobId(), storedRow.getRowNo(), ex);
			ProductImportRowDTO row = new ProductImportRowDTO();
			row.setRowNo(storedRow.getRowNo());
			return row;
		}
	}

	// ---------------------------------------------------------------------
	// 导入执行
	// ---------------------------------------------------------------------

	private void importRow(String tenantId, ProductImportRowDTO row, String operatorId, String operatorName) {
		boolean update = StringUtils.hasText(row.getMatchType());
		String spuId;
		String skuId;
		if (update) {
			Map<String, String> target = resolveUpdateTarget(tenantId, row);
			spuId = target.get("spuId");
			skuId = target.get("skuId");
			updateSpu(tenantId, row, spuId, operatorId, operatorName);
		}
		else {
			spuId = createSpu(tenantId, row, operatorId, operatorName);
			skuId = null;
		}
		upsertSku(tenantId, row, spuId, skuId, operatorId, operatorName);
		upsertShipProfile(tenantId, row, spuId);
	}

	private Map<String, String> resolveUpdateTarget(String tenantId, ProductImportRowDTO row) {
		Map<String, String> target = new HashMap<>();
		if ("SKU".equals(row.getMatchType())) {
			GoodsSku sku = goodsSkuMapper.selectById(row.getMatchValue());
			target.put("spuId", sku.getSpuId());
			target.put("skuId", sku.getId());
			return target;
		}
		String codeType = "IMPA".equals(row.getMatchType()) ? ProductCodeMapping.TYPE_IMPA
				: ProductCodeMapping.TYPE_INTERNAL;
		ProductCodeMapping mapping = productCodeMappingMapper.selectByCodeGlobal(codeType, row.getMatchValue());
		target.put("spuId", mapping.getSpuId());
		target.put("skuId", mapping.getSkuId());
		return target;
	}

	private String createSpu(String tenantId, ProductImportRowDTO row, String operatorId, String operatorName) {
		GoodsSpu spu = new GoodsSpu();
		spu.setId(IdWorker.getIdStr());
		spu.setName(row.getName());
		if (StringUtils.hasText(row.getCategorySecondId())) {
			spu.setCategorySecondId(row.getCategorySecondId());
			GoodsCategory category = goodsCategoryMapper.selectById(row.getCategorySecondId());
			if (category != null) {
				spu.setCategoryFirstId(category.getParentId());
			}
		}
		spu.setBrandId(row.getBrandId());
		spu.setStatus("0");
		spu.setTenantId(tenantId);
		spu.setCreateBy(operatorName);
		spu.setCreateTime(LocalDateTime.now());
		spu.setDelFlag("0");
		goodsSpuMapper.insert(spu);

		ProductChangeLog spuLog = changeLog(tenantId, ProductChangeLog.BIZ_SPU, spu.getId(),
				ProductChangeLog.CHANGE_CREATE, operatorId, operatorName);
		spuLog.setAfterSnapshot("{\"name\":\"" + row.getName() + "\"}");
		productChangeLogMapper.insert(spuLog);
		return spu.getId();
	}

	private void updateSpu(String tenantId, ProductImportRowDTO row, String spuId, String operatorId,
			String operatorName) {
		GoodsSpu spu = goodsSpuMapper.selectById(spuId);
		if (spu == null || !Objects.equals(spu.getTenantId(), tenantId)) {
			throw new ArynBusinessException("待更新商品不存在");
		}
		boolean changed = false;
		if (StringUtils.hasText(row.getName()) && !Objects.equals(spu.getName(), row.getName())) {
			// 名称仅作为展示信息同步，禁止以名称作为更新匹配键
			spu.setName(row.getName());
			changed = true;
		}
		if (StringUtils.hasText(row.getBrandId()) && !Objects.equals(spu.getBrandId(), row.getBrandId())) {
			spu.setBrandId(row.getBrandId());
			changed = true;
		}
		if (StringUtils.hasText(row.getCategorySecondId())
				&& !Objects.equals(spu.getCategorySecondId(), row.getCategorySecondId())) {
			spu.setCategorySecondId(row.getCategorySecondId());
			GoodsCategory category = goodsCategoryMapper.selectById(row.getCategorySecondId());
			spu.setCategoryFirstId(category != null ? category.getParentId() : null);
			changed = true;
		}
		if (changed) {
			spu.setUpdateBy(operatorName);
			spu.setUpdateTime(LocalDateTime.now());
			goodsSpuMapper.updateById(spu);
			ProductChangeLog spuLog = changeLog(tenantId, ProductChangeLog.BIZ_SPU, spuId,
					ProductChangeLog.CHANGE_UPDATE, operatorId, operatorName);
			spuLog.setAfterSnapshot("{\"name\":\"" + spu.getName() + "\"}");
			productChangeLogMapper.insert(spuLog);
		}
	}

	private void upsertSku(String tenantId, ProductImportRowDTO row, String spuId, String skuId, String operatorId,
			String operatorName) {
		GoodsSku sku;
		if (StringUtils.hasText(skuId)) {
			sku = goodsSkuMapper.selectById(skuId);
			if (sku == null || !Objects.equals(sku.getTenantId(), tenantId)) {
				throw new ArynBusinessException("待更新SKU不存在");
			}
			if (row.getSalesPrice() != null) {
				sku.setSalesPrice(row.getSalesPrice());
			}
			if (row.getStock() != null) {
				sku.setStock(row.getStock());
			}
			sku.setUpdateBy(operatorName);
			sku.setUpdateTime(LocalDateTime.now());
			goodsSkuMapper.updateById(sku);
		}
		else {
			sku = new GoodsSku();
			sku.setId(IdWorker.getIdStr());
			sku.setSpuId(spuId);
			sku.setSalesPrice(row.getSalesPrice() != null ? row.getSalesPrice() : BigDecimal.ZERO);
			sku.setStock(row.getStock() != null ? row.getStock() : 0);
			sku.setStatus("1");
			sku.setVersion(0);
			sku.setTenantId(tenantId);
			sku.setCreateBy(operatorName);
			sku.setCreateTime(LocalDateTime.now());
			sku.setDelFlag("0");
			goodsSkuMapper.insert(sku);
		}
		row.setSkuId(sku.getId());
	}

	private void upsertShipProfile(String tenantId, ProductImportRowDTO row, String spuId) {
		ShipGoodsProfile profile = new ShipGoodsProfile();
		profile.setSpuId(spuId);
		profile.setSaleScope(StringUtils.hasText(row.getSaleScope()) ? row.getSaleScope() : "3");
		profile.setImpaCode(row.getImpaCode());
		profile.setIssaCode(row.getIssaCode());
		profile.setInternalItemCode(row.getInternalItemCode());
		profile.setBarcode(row.getBarcode());
		profile.setNameEn(row.getNameEn());
		profile.setStorageType(row.getStorageType());

		ShipSkuProfile skuProfile = new ShipSkuProfile();
		skuProfile.setSkuId(row.getSkuId());
		skuProfile.setPurchaseUnit(row.getPurchaseUnit());
		skuProfile.setPackageSpec(row.getPackageSpec());
		skuProfile.setMoq(row.getMoq());
		skuProfile.setStepQty(row.getStepQty());

		ShipProductProfileDTO profileDTO = new ShipProductProfileDTO();
		profileDTO.setSpuId(spuId);
		profileDTO.setProfile(profile);
		profileDTO.setSkuProfiles(skuProfile.getSkuId() != null ? List.of(skuProfile) : null);

		if (StringUtils.hasText(row.getImpaCode())) {
			ProductCodeMapping mapping = new ProductCodeMapping();
			mapping.setSpuId(spuId);
			mapping.setSkuId(row.getSkuId());
			mapping.setCodeType(ProductCodeMapping.TYPE_IMPA);
			mapping.setCodeValue(row.getImpaCode());
			mapping.setMatchSource(ProductCodeMapping.SOURCE_IMPORT);
			profileDTO.setCodeMappings(List.of(mapping));
		}
		shipProductProfileService.saveProfile(tenantId, profileDTO);
	}

	private ProductChangeLog changeLog(String tenantId, String bizType, String bizId, String changeType,
			String operatorId, String operatorName) {
		ProductChangeLog changeLog = new ProductChangeLog();
		changeLog.setId(IdWorker.getIdStr());
		changeLog.setBizType(bizType);
		changeLog.setBizId(bizId);
		changeLog.setChangeType(changeType);
		changeLog.setOperatorId(operatorId);
		changeLog.setOperatorName(operatorName);
		changeLog.setTenantId(tenantId);
		changeLog.setCreateTime(LocalDateTime.now());
		changeLog.setDelFlag("0");
		return changeLog;
	}

}
