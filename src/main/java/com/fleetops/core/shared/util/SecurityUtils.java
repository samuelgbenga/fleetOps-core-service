package com.fleetops.core.shared.util;

import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityUtils {

    private SecurityUtils() {}

    public static String currentUserEmail() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
