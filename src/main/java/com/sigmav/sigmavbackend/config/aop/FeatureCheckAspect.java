package com.sigmav.sigmavbackend.config.aop;

import com.sigmav.sigmavbackend.modules.academy.service.AcademyService;
import com.sigmav.sigmavbackend.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.Map;

@Aspect
@Component
@RequiredArgsConstructor
public class FeatureCheckAspect {

    private final AcademyService academyService;

    @Around("@annotation(requiresFeature)")
    public Object checkFeature(ProceedingJoinPoint pjp, RequiresFeature requiresFeature) throws Throwable {
        Long academyId = TenantContext.getCurrentAcademyId();
        if (academyId == null) {
            return pjp.proceed();
        }

        Map<String, Boolean> features = academyService.getFeatures(academyId);
        if (!Boolean.TRUE.equals(features.get(requiresFeature.value().key()))) {
            throw new AccessDeniedException(
                    "La funcionalidad '" + requiresFeature.value().key() + "' no está habilitada para esta academia. " +
                    "Contacte al administrador del sistema.");
        }

        return pjp.proceed();
    }
}
