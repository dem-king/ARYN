
package com.aryn.cloud.common.core.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author 雨滴kian
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class GeoLocation {

	private String province;

	private String city;

	private String region;

}
