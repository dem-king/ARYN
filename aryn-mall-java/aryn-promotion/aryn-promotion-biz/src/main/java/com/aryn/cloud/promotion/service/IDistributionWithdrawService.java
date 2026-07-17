package com.aryn.cloud.promotion.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.promotion.api.dto.DistributionWithdrawApplyDTO;
import com.aryn.cloud.promotion.api.dto.DistributionWithdrawAuditDTO;
import com.aryn.cloud.promotion.api.entity.DistributionWithdraw;
import com.aryn.cloud.promotion.api.vo.DistributionCenterVO;
import com.aryn.cloud.promotion.api.vo.DistributionWithdrawVO;

public interface IDistributionWithdrawService extends IService<DistributionWithdraw> {

    Boolean apply(String userId, DistributionWithdrawApplyDTO dto);

    Boolean audit(DistributionWithdrawAuditDTO dto);

    DistributionCenterVO getCenter(String userId);

	IPage<DistributionWithdrawVO> getAdminPage(Page<DistributionWithdraw> page, DistributionWithdraw query);

	DistributionWithdrawVO getDetail(String id);

	IPage<DistributionWithdrawVO> getUserPage(Page<DistributionWithdraw> page, String userId);

	int reencryptLegacyAccounts();
}
