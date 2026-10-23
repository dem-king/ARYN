package com.aryn.cloud.user.dubbo;

import com.aryn.cloud.user.api.remote.RemotePointsService;
import com.aryn.cloud.user.api.vo.PointsDeductContextVO;
import com.aryn.cloud.user.service.IPointsGrantService;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

/**
 * 积分远程服务实现。
 */
@Service
@DubboService
@RequiredArgsConstructor
public class RemotePointsServiceImpl implements RemotePointsService {

	private final IPointsGrantService pointsGrantService;

	@Override
	public PointsDeductContextVO getDeductContext(String userId) {
		return pointsGrantService.getDeductContext(userId);
	}

	@Override
	public void deductPoints(String userId, Integer points, String bizNo, String remark) {
		pointsGrantService.deduct(userId, points, bizNo, remark);
	}

	@Override
	public void refundPoints(String userId, Integer points, String bizNo, String remark) {
		pointsGrantService.refund(userId, points, bizNo, remark);
	}

}
