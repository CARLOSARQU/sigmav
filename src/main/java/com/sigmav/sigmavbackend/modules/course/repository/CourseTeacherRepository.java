package com.sigmav.sigmavbackend.modules.course.repository;

import com.sigmav.sigmavbackend.modules.course.entity.CourseTeacher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CourseTeacherRepository extends JpaRepository<CourseTeacher, Long> {

    List<CourseTeacher> findByCourseId(Long courseId);

    List<CourseTeacher> findByTeacherId(Long teacherId);

    Optional<CourseTeacher> findByCourseIdAndTeacherId(Long courseId, Long teacherId);

    boolean existsByCourseIdAndTeacherId(Long courseId, Long teacherId);

    void deleteByCourseIdAndTeacherId(Long courseId, Long teacherId);

    @Query("SELECT ct.courseId FROM CourseTeacher ct WHERE ct.teacherId = :teacherId")
    List<Long> findCourseIdsByTeacherId(@Param("teacherId") Long teacherId);
}
