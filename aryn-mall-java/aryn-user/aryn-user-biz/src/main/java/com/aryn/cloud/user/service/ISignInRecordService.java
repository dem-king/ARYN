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

	IPage<SignInRecordVO> getUserPage(Page page, String userId);

	/**
	 * C端用户签到
	 * @param userId 用户ID
	 * @return 签到结果
	 */
	SignInResultVO signIn(String userId);

}
