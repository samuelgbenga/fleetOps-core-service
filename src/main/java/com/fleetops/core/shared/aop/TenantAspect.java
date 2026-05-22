package com.fleetops.core.shared.aop;

import com.fleetops.core.shared.context.TenantContext;
import com.fleetops.core.shared.exception.TenantAccessException;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class TenantAspect {

    @Before("execution(* com.fleetops.core.module.*.repository.*.*(..))")
    public void enforceTenantContext(JoinPoint jp) {
        String userType = TenantContext.getUserType();
        // Only enforce if an authenticated user request is active (userType is set)
        // Scheduled jobs, Kafka consumers, and startup seeders have no context — allow them
        if (userType != null && !TenantContext.isPlatformLevel() && TenantContext.getCompanyId() == null) {
            throw new TenantAccessException("Tenant context not set");
        }
    }
}
