package com.aryn.cloud.upms.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.util.ObjectUtil;
import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.core.dto.SysStorageConfigDTO;
import com.aryn.cloud.common.core.enums.DeviceTypeEnum;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.security.entity.ArynUser;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.common.storage.entity.StoredObject;
import com.aryn.cloud.common.storage.handler.StorageFactory;
import com.aryn.cloud.upms.api.entity.SysMaterial;
import com.aryn.cloud.upms.service.ISysMaterialService;
import com.aryn.cloud.upms.service.ISysStorageConfigService;
import com.aryn.cloud.upms.support.UploadFileValidator;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

/** 配送员专用履约凭证上传。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/file/staff/delivery-evidence")
@Tag(name = "配送凭证上传")
public class StaffDeliveryEvidenceController {

	private final ISysStorageConfigService storageConfigService;
	private final StorageFactory storageFactory;
	private final ISysMaterialService materialService;
	private final UploadFileValidator uploadFileValidator;

	@PostMapping("/upload")
	@SaCheckPermission("order:delivery:execute")
	@Operation(summary = "上传配送履约凭证")
	public Result<String> upload(@RequestPart("file") MultipartFile file) throws Exception {
		ArynUser staff = SecurityUtils.requireUser(DeviceTypeEnum.TOB);
		uploadFileValidator.validateDeliveryEvidence(file);
		SysStorageConfigDTO storageConfig = storageConfigService.getConfig();
		if (ObjectUtil.isNull(storageConfig)) {
			throw new ArynBusinessException("当前租户未配置启用的文件存储");
		}
		StoredObject storedObject = storageFactory.getStrategy(storageConfig.getType())
			.uploadObject(storageConfig, file.getInputStream(), file.getOriginalFilename(), file.getContentType(),
				file.getSize());
		LocalDateTime now = LocalDateTime.now();
		SysMaterial material = new SysMaterial();
		material.setId(IdWorker.getIdStr());
		material.setName(file.getOriginalFilename());
		material.setType("1");
		material.setUrl(storedObject.getUrl());
		material.setObjectKey(storedObject.getObjectKey());
		material.setFileSize(file.getSize());
		material.setBusinessType("DELIVERY_EVIDENCE");
		material.setBindingStatus("UNBOUND");
		material.setTenantId(staff.getTenantId());
		material.setCreateBy(staff.getUserId());
		material.setCreateTime(now);
		material.setDelFlag(CommonConstants.NO);
		if (!materialService.save(material)) {
			throw new ArynBusinessException("配送凭证素材保存失败");
		}
		return Result.success(material.getId());
	}

}
