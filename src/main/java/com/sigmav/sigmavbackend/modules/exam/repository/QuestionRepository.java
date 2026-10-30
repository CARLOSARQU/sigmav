package com.sigmav.sigmavbackend.modules.exam.repository;

import com.sigmav.sigmavbackend.modules.exam.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuestionRepository extends JpaRepository<Question, Long> {

    List<Question> findByExamIdOrderByOrderIndex(Long examId);

    long countByExamId(Long examId);

    boolean existsByIdAndExamId(Long id, Long examId);
}
