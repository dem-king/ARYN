package com.aryn.cloud.common.dubbo.contract;

import com.aryn.cloud.common.core.entity.SysLoginLogBase;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class SysLoginLogBaseSerializationTest {

	@Test
	void supportsRpcSerializationRoundTrip() throws Exception {
		SysLoginLogBase loginLog = new SysLoginLogBase();
		loginLog.setId("login-log-id");
		loginLog.setUserName("system");
		loginLog.setStatus("1");
		loginLog.setCreateTime(LocalDateTime.of(2026, 7, 27, 12, 15, 14));
		loginLog.setTenantId("tenant-id");

		assertThat(loginLog).isInstanceOf(Serializable.class);

		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
			output.writeObject(loginLog);
		}

		SysLoginLogBase restored;
		try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
			restored = (SysLoginLogBase) input.readObject();
		}

		assertThat(restored)
			.usingRecursiveComparison()
			.isEqualTo(loginLog);
	}

}
