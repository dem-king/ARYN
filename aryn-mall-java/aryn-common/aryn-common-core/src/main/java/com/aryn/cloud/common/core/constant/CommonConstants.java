
package com.aryn.cloud.common.core.constant;

/**
 * 公共常量
 *
 * @author 雨滴kian
 * @since 2022/2/10 12:00
 */
public interface CommonConstants {

	int SUCCESS = 0;

	int FAIL = 1;

	/**
	 * 业务码：共享购物车成员尚未填写姓名（display_name，配送按人贴标签用）。
	 * C 端按此 code 弹出姓名补填层并引导到详情页，勿复用 FAIL。
	 */
	int SHARED_CART_NAME_REQUIRED = 1201;

	/**
	 * 系统状态：0.正常；1.停用
	 */
	String NORMAL_STATUS = "0";

	/**
	 * 顶级ID
	 */
	String PARENT_ID = "0";

	/**
	 * 菜单
	 */
	String MENU_TYPE_0 = "0";

	/**
	 * 按钮
	 */
	String MENU_TYPE_1 = "1";

	/**
	 * 登录状态：0.失败；1.成功；
	 */
	String LOGIN_LOG_STATUS_0 = "0";

	String LOGIN_LOG_STATUS_1 = "1";

	/**
	 * 公共状态：1.是；0.否；
	 */
	String YES = "1";

	String NO = "0";

	/**
	 * 管理员角色编码
	 */
	String ROLE_ADMIN_CODE = "ROLE_ADMIN";

	/**
	 * 管理员角色名称
	 */
	String ROLE_ADMIN_NAME = "系统管理员";

	/**
	 * 配送资格受保护角色编码：只能由配送员管理（资格接口/向导）授予或回收，
	 * 通用用户新增/编辑接口必须拒绝直接修改，编辑时保留现有关联
	 */
	String PROTECTED_DELIVERY_ROLE_CODE = "delivery_staff";

	/**
	 * 请求头租户ID
	 */
	String TENANT_ID = "tenant-id";

	/**
	 * 平台租户ID
	 */
	String PLATFORM_TENANT_ID = "1881232176465358849";

	/**
	 * 平台专属应用标识：该应用下的菜单仅平台租户可用，禁止写入租户套餐或直接分配给租户
	 */
	String PLATFORM_APP_KEY = "sys_key";

	/**
	 * 租户类型：平台租户。不参与授权期管理，auth_end_time 为空表示长期有效
	 */
	String TENANT_TYPE_PLATFORM = "1";

	/**
	 * 租户类型：普通租户。按授权开始/结束时间管控
	 */
	String TENANT_TYPE_STANDARD = "2";

	/**
	 * token key
	 */
	String TOKEN_KEY = "satoken";

	/**
	 * 滑块验证码类型
	 */
	String IMAGE_CODE_TYPE_BP = "blockPuzzle";

	/**
	 * 文字点选验证码类型
	 */
	String IMAGE_CODE_TYPE_CW = "clickWord";

	// 系统主账户
	String USER_TYPE_PRIMARY = "0";

	// 用户名注册类型
	String REGISTER_TYPE_USERNAME = "username";

	// 手机号注册类型
	String REGISTER_TYPE_PHONE = "phone";

	String UTF8 = "UTF-8";

}
