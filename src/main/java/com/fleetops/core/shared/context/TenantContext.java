package com.fleetops.core.shared.context;

public class TenantContext {

    private static final ThreadLocal<Long> COMPANY_ID = new ThreadLocal<>();
    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> ROLE = new ThreadLocal<>();
    private static final ThreadLocal<String> USER_TYPE = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(Long companyId, Long userId, String role, String userType) {
        COMPANY_ID.set(companyId);
        USER_ID.set(userId);
        ROLE.set(role);
        USER_TYPE.set(userType);
    }

    public static Long getCompanyId() {
        return COMPANY_ID.get();
    }

    public static Long getUserId() {
        return USER_ID.get();
    }

    public static String getRole() {
        return ROLE.get();
    }

    public static String getUserType() {
        return USER_TYPE.get();
    }

    public static boolean isPlatformLevel() {
        return "PLATFORM".equals(USER_TYPE.get());
    }

    public static void clear() {
        COMPANY_ID.remove();
        USER_ID.remove();
        ROLE.remove();
        USER_TYPE.remove();
    }
}
