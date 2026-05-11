
package com.aryn.cloud.order.api.vo;

import com.aryn.cloud.order.api.entity.ShoppingCart;
import lombok.Data;

import java.util.List;

@Data
public class ShoppingCartShopVO {

	private List<ShoppingCart> shoppingCartList;

}
