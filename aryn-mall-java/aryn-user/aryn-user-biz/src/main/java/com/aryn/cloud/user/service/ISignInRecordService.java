package com.aryn.cloud.user.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.user.api.entity.SignInRecord;
import com.aryn.cloud.user.api.vo.SignInRecordVO;
import com.aryn.cloud.user.api.vo.SignInResultVO;

/**
 * 签到记录
 *
 * @author 雨滴kian
 */
public interface ISignInRecordService extends IService<SignInRecord> {

	IPage<SignInRecordVO> getPage(Page page, String nickname, String beginDate, String endDate);

	/**
	 * C端签到记录分页（按登录态取本人记录）
	 * @param page 分页
	 * @param userId 用户ID
	 * @param beginDate 起始签到日期（含），可空
	 * @param endDate 截止签到日期（含），可空
	 * @return 签到记录分页
	 */
	IPage<SignInRecordVO> getUserPage(Page page, String userId, String beginDate, String endDate);

	/**
	 * C端用户签到
	 * @param userId 用户ID
	 * @return 签到结果
	 */
	SignInResultVO signIn(String userId);

}
