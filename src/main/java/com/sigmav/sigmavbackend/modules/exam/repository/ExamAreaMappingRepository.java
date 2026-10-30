package com.sigmav.sigmavbackend.modules.exam.repository;

import com.sigmav.sigmavbackend.modules.exam.entity.ExamAreaMapping;
import com.sigmav.sigmavbackend.modules.exam.entity.ExamAreaMappingId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExamAreaMappingRepository extends JpaRepository<ExamAreaMapping, ExamAreaMappingId> {

    @Query("SELECT m FROM ExamAreaMapping m WHERE m.id.examId = :examId")
    List<ExamAreaMapping> findByExamId(Long examId);

    @Query("SELECT m FROM ExamAreaMapping m WHERE m.id.areaId = :areaId")
    List<ExamAreaMapping> findByAreaId(Long areaId);

    @Modifying
    @Query("DELETE FROM ExamAreaMapping m WHERE m.id.examId = :examId")
    void deleteByExamId(Long examId);

    @Query("SELECT COUNT(m) > 0 FROM ExamAreaMapping m WHERE m.id.examId = :examId AND m.id.areaId = :areaId")
    boolean existsByExamIdAndAreaId(Long examId, Long areaId);
}
