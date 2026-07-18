package com.jjx.system.service;

import java.util.List;

public interface IPermissionService {
    List<String> getPermissionsByUserId(Long userId);

    List<String> getRolesByUserId(Long userId);
}
