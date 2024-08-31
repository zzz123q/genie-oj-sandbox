package com.zzz123q.genieojsandbox.security;

import java.security.Permission;

/**
 * 默认安全管理器
 */
public class DefaultSecurityManager extends SecurityManager {

    @Override
    public void checkPermission(Permission perm) {
        System.out.println("不做任何限制");
        super.checkPermission(perm);
    }

}
