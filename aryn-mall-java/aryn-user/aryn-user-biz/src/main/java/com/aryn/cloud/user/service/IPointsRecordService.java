package com.aryn.cloud.user.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.user.api.entity.PointsRecord;
import com.aryn.cloud.user.api.vo.PointsRecordVO;

/**
 * 积分记录
 *
 * @author 雨滴kian
 */
public interface IPointsRecordService extends IService<PointsRecord> {

	IPage<PointsRecordVO> getPage(Page page, String nickname, String changeType, String beginTime, String endTime);

	IPage<PointsRecordVO> getUserPage(Page page, String userId);

	/**
	 * 记录积分变动
	 * @param userId 用户ID
	 * @param changeType 变动类型：1-获取；2-消耗
	 * @param changePoint 变动积分（正整数）
	 * @param triggerScene 触发场景
	 * @param remark 备注
	 */
	void recordPointsChange(String userId, String changeType, Integer changePoint, String triggerScene, String remark);

}
