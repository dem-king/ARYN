package com.aryn.cloud.user.dubbo;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.user.api.entity.MemberLevel;
import com.aryn.cloud.user.api.entity.UserInfo;
import com.aryn.cloud.user.api.vo.MemberLevelSnapshotVO;
import com.aryn.cloud.user.mapper.MemberLevelMapper;
import com.aryn.cloud.user.mapper.UserInfoMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RemoteMemberLevelServiceImplTest {

	@Mock
	private UserInfoMapper userInfoMapper;

	@Mock
	private MemberLevelMapper memberLevelMapper;

	@AfterEach
	void clearTenant() {
		ArynTenantContextHolder.removeTenantId();
	}

	@Test
	void getUserLevelLocksExplicitTenantAndRestoresSnapshot() {
		ArynTenantContextHolder.setTenantId("tenant-before");
		when(userInfoMapper.selectById("user-1")).thenAnswer(invocation -> {
			assertThat(ArynTenantContextHolder.getTenantId())
				.as("查询 user_info 前必须锁定入参租户，否则租户拦截器注入 tenant_id = NULL 查不到行")
				.isEqualTo("tenant-1");
			return new UserInfo().setId("user-1").setMemberLevelId("lvl-2");
		});
		when(memberLevelMapper.selectById("lvl-2")).thenAnswer(invocation -> {
			assertThat(ArynTenantContextHolder.getTenantId()).isEqualTo("tenant-1");
			return level("lvl-2", "黄金会员", 20);
		});

		MemberLevelSnapshotVO snapshot = service().getUserLevel("tenant-1", "user-1");

		assertThat(snapshot.getLevelId()).isEqualTo("lvl-2");
		assertThat(snapshot.getLevelName()).isEqualTo("黄金会员");
		assertThat(snapshot.getSortOrder()).isEqualTo(20);
		assertThat(ArynTenantContextHolder.getTenantId()).as("调用结束必须还原调用方租户上下文").isEqualTo("tenant-before");
	}

	@Test
	void getUserLevelReturnsNullWhenUserHasNoLevel() {
		when(userInfoMapper.selectById("user-1")).thenReturn(new UserInfo().setId("user-1"));

		assertThat(service().getUserLevel("tenant-1", "user-1")).isNull();
		verifyNoInteractions(memberLevelMapper);
		assertThat(ArynTenantContextHolder.getTenantId()).isNull();
	}

	@Test
	void getUserLevelReturnsNullWhenUserMissingOrLevelRowMissing() {
		assertThat(service().getUserLevel("tenant-1", "user-1")).isNull();

		when(userInfoMapper.selectById("user-2")).thenReturn(new UserInfo().setId("user-2").setMemberLevelId("lvl-gone"));
		assertThat(service().getUserLevel("tenant-1", "user-2")).isNull();
	}

	@Test
	void getUserLevelFailsClosedOnQueryException() {
		ArynTenantContextHolder.setTenantId("tenant-before");
		when(userInfoMapper.selectById("user-1")).thenThrow(new IllegalStateException("db down"));

		assertThat(service().getUserLevel("tenant-1", "user-1")).isNull();
		assertThat(ArynTenantContextHolder.getTenantId()).isEqualTo("tenant-before");
	}

	@Test
	void getLevelByIdLocksTenantAndReturnsSnapshot() {
		when(memberLevelMapper.selectById("lvl-1")).thenAnswer(invocation -> {
			assertThat(ArynTenantContextHolder.getTenantId()).isEqualTo("tenant-1");
			return level("lvl-1", "白银会员", 10);
		});

		MemberLevelSnapshotVO snapshot = service().getLevelById("tenant-1", "lvl-1");

		assertThat(snapshot.getLevelId()).isEqualTo("lvl-1");
		assertThat(snapshot.getLevelName()).isEqualTo("白银会员");
		assertThat(snapshot.getSortOrder()).isEqualTo(10);
		assertThat(ArynTenantContextHolder.getTenantId()).isNull();
	}

	@Test
	void getLevelByIdReturnsNullWhenMissingOrOnException() {
		assertThat(service().getLevelById("tenant-1", "lvl-gone")).isNull();

		when(memberLevelMapper.selectById("lvl-1")).thenThrow(new IllegalStateException("db down"));
		assertThat(service().getLevelById("tenant-1", "lvl-1")).isNull();
	}

	@Test
	void blankParamsShortCircuitWithoutQuerying() {
		RemoteMemberLevelServiceImpl service = service();

		assertThat(service.getUserLevel(null, "user-1")).isNull();
		assertThat(service.getUserLevel("tenant-1", " ")).isNull();
		assertThat(service.getLevelById("", "lvl-1")).isNull();
		assertThat(service.getLevelById("tenant-1", null)).isNull();
		verifyNoInteractions(userInfoMapper, memberLevelMapper);
	}

	@Test
	void snapshotIsSerializableForStrictDubboMode() throws Exception {
		MemberLevelSnapshotVO snapshot = new MemberLevelSnapshotVO();
		snapshot.setLevelId("lvl-1");
		snapshot.setLevelName("白银会员");
		snapshot.setSortOrder(10);

		assertThat(snapshot).isInstanceOf(Serializable.class);
		assertThat(roundTrip(snapshot)).usingRecursiveComparison().isEqualTo(snapshot);
	}

	private RemoteMemberLevelServiceImpl service() {
		return new RemoteMemberLevelServiceImpl(userInfoMapper, memberLevelMapper);
	}

	private MemberLevel level(String id, String levelName, Integer sortOrder) {
		return new MemberLevel().setId(id).setLevelName(levelName).setSortOrder(sortOrder).setStatus("0");
	}

	private static Object roundTrip(Serializable value) throws Exception {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
			output.writeObject(value);
		}

		try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
			return input.readObject();
		}
	}

}
