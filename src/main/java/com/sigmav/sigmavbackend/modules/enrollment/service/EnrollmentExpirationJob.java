package com.sigmav.sigmavbackend.modules.enrollment.service;

import com.sigmav.sigmavbackend.modules.enrollment.repository.EnrollmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class EnrollmentExpirationJob {

    private final EnrollmentRepository enrollmentRepository;

    // Runs every hour at minute 0
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void expireEnrollments() {
        int updated = enrollmentRepository.markExpiredAsVencido();
        if (updated > 0) {
            log.info("EnrollmentExpirationJob: {} enrollment(s) marked as VENCIDO", updated);
        }
    }
}
