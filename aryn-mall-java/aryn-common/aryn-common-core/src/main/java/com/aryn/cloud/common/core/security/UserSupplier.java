package com.aryn.cloud.common.core.security;

@FunctionalInterface
public interface UserSupplier {

	String getCurrentUserName();

}
