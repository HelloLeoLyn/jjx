package com.jjx.production.service;

/** 解析当前登录人的生产逻辑身份。 */
public interface ProductionRoleResolver {

    boolean isProductionAdmin();

    boolean isGlobalProductionScope();
}
