package com.aryn.cloud.user.service.impl;

import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.user.api.entity.UserAddress;
import com.aryn.cloud.user.mapper.UserAddressMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UserAddressServiceImpl 单元测试：默认地址唯一性。
 */
@ExtendWith(MockitoExtension.class)
class UserAddressServiceImplTest {

	@Mock
	private UserAddressMapper userAddressMapper;

	private TestUserAddressService userAddressService;

	@BeforeEach
	void setUp() {
		userAddressService = new TestUserAddressService(userAddressMapper);
	}

	@Test
	@DisplayName("新增默认地址：先清旧默认再插入，userId 以服务端为准")
	void saveDefaultAddress_clearsExistingDefaultFirst() {
		// given
		UserAddress address = new UserAddress()
			.setRecipientName("张伟")
			.setIsDefault(CommonConstants.YES)
			.setUserId("forged-user");
		when(userAddressMapper.update(any(UserAddress.class), any())).thenReturn(1);
		when(userAddressMapper.insert(any(UserAddress.class))).thenReturn(1);

		// when
		UserAddress result = userAddressService.saveOrUpdateForUser(address, "user-1");

		// then
		assertEquals(address, result);
		ArgumentCaptor<UserAddress> updateCaptor = ArgumentCaptor.forClass(UserAddress.class);
		InOrder inOrder = inOrder(userAddressMapper);
		inOrder.verify(userAddressMapper).update(updateCaptor.capture(), any());
		inOrder.verify(userAddressMapper).insert(address);
		assertEquals(CommonConstants.NO, updateCaptor.getValue().getIsDefault());
		assertEquals("user-1", address.getUserId());
	}

	@Test
	@DisplayName("新增非默认地址：不清除已有默认地址")
	void saveNormalAddress_keepsExistingDefault() {
		// given
		UserAddress address = new UserAddress().setIsDefault(CommonConstants.NO);
		when(userAddressMapper.insert(any(UserAddress.class))).thenReturn(1);

		// when
		UserAddress result = userAddressService.saveOrUpdateForUser(address, "user-1");

		// then
		assertEquals(address, result);
		verify(userAddressMapper, never()).update(any(UserAddress.class), any());
		verify(userAddressMapper).insert(address);
		assertEquals("user-1", address.getUserId());
	}

	@Test
	@DisplayName("编辑为默认地址：先清旧默认，再按 id + userId 更新")
	void updateToDefault_clearsExistingDefaultFirst() {
		// given
		UserAddress address = new UserAddress()
			.setId("addr-1")
			.setIsDefault(CommonConstants.YES);
		when(userAddressMapper.update(any(UserAddress.class), any())).thenReturn(1);

		// when
		UserAddress result = userAddressService.saveOrUpdateForUser(address, "user-1");

		// then
		assertEquals(address, result);
		ArgumentCaptor<UserAddress> entityCaptor = ArgumentCaptor.forClass(UserAddress.class);
		verify(userAddressMapper, times(2)).update(entityCaptor.capture(), any());
		List<UserAddress> updated = entityCaptor.getAllValues();
		assertEquals(CommonConstants.NO, updated.get(0).getIsDefault());
		assertEquals(CommonConstants.YES, updated.get(1).getIsDefault());
		assertEquals("user-1", address.getUserId());
		verify(userAddressMapper, never()).insert(any(UserAddress.class));
	}

	@Test
	@DisplayName("编辑非默认地址：不影响其他默认地址")
	void updateNormalAddress_keepsExistingDefault() {
		// given
		UserAddress address = new UserAddress()
			.setId("addr-1")
			.setIsDefault(CommonConstants.NO);
		when(userAddressMapper.update(any(UserAddress.class), any())).thenReturn(1);

		// when
		UserAddress result = userAddressService.saveOrUpdateForUser(address, "user-1");

		// then
		assertEquals(address, result);
		verify(userAddressMapper, times(1)).update(any(UserAddress.class), any());
		verify(userAddressMapper, never()).insert(any(UserAddress.class));
	}

	@Test
	@DisplayName("更新未命中（如地址不属于该用户）：返回实体改为抛业务异常，避免前端误判成功")
	void updateMissedRow_throwsBusinessException() {
		// given
		UserAddress address = new UserAddress()
			.setId("addr-1")
			.setIsDefault(CommonConstants.NO);
		// update 按 id + userId 过滤，0 行代表地址不存在或属于他人
		when(userAddressMapper.update(any(UserAddress.class), any())).thenReturn(0);

		// then
		assertThrows(ArynBusinessException.class,
				() -> userAddressService.saveOrUpdateForUser(address, "user-1"));
		verify(userAddressMapper, never()).insert(any(UserAddress.class));
	}

	private static final class TestUserAddressService extends UserAddressServiceImpl {

		private TestUserAddressService(UserAddressMapper userAddressMapper) {
			this.baseMapper = userAddressMapper;
		}
	}

}
