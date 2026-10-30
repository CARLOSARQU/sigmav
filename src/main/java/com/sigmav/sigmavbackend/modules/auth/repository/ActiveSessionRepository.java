package com.sigmav.sigmavbackend.modules.auth.repository;

import com.sigmav.sigmavbackend.modules.auth.entity.ActiveSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ActiveSessionRepository extends JpaRepository<ActiveSession, Long> {

    List<ActiveSession> findByUserId(Long userId);

    Optional<ActiveSession> findBySessionToken(String sessionToken);

    @Modifying
    @Query("DELETE FROM ActiveSession a WHERE a.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);

}
