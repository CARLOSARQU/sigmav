package com.sigmav.sigmavbackend.modules.academy.repository;

import com.sigmav.sigmavbackend.modules.academy.entity.AcademySubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AcademySubscriptionRepository extends JpaRepository<AcademySubscription, Long> {

    List<AcademySubscription> findByAcademyIdOrderByCreatedAtDesc(Long academyId);
}
