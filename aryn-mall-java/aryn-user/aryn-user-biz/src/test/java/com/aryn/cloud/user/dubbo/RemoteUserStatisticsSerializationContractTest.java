package com.aryn.cloud.user.dubbo;

import com.aryn.cloud.user.api.dto.UserStatisticsDTO;
import com.aryn.cloud.user.api.vo.UserTrendVO;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class RemoteUserStatisticsSerializationContractTest {

	@Test
	void dubboRequestAndResponseTypesShouldBeSerializable() throws Exception {
		UserStatisticsDTO request = new UserStatisticsDTO();
		request.setStartTime(LocalDateTime.of(2026, 7, 18, 0, 0));
		request.setEndTime(LocalDateTime.of(2026, 7, 18, 23, 59));

		UserTrendVO response = new UserTrendVO();
		response.setTimePoint("2026-07-18");
		response.setNewUserCount(3);

		assertThat(request).isInstanceOf(Serializable.class);
		assertThat(response).isInstanceOf(Serializable.class);
		assertThat(roundTrip(request)).usingRecursiveComparison().isEqualTo(request);
		assertThat(roundTrip(response)).usingRecursiveComparison().isEqualTo(response);
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
