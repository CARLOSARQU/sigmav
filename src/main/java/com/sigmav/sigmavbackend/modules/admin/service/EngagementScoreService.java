package com.sigmav.sigmavbackend.modules.admin.service;

import com.sigmav.sigmavbackend.modules.admin.dto.EngagementScoreResponse;
import com.sigmav.sigmavbackend.modules.admin.dto.EngagementScoreResponse.StudentEngagementScore;
import com.sigmav.sigmavbackend.modules.course.repository.CourseMaterialRepository;
import com.sigmav.sigmavbackend.modules.exam.repository.ExamAttemptRepository;
import com.sigmav.sigmavbackend.modules.exam.repository.ExamRepository;
import com.sigmav.sigmavbackend.modules.session.repository.AttendanceRecordRepository;
import com.sigmav.sigmavbackend.modules.student.entity.Student;
import com.sigmav.sigmavbackend.modules.student.repository.StudentProgressRepository;
import com.sigmav.sigmavbackend.modules.student.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EngagementScoreService {

    private final StudentRepository studentRepo;
    private final AttendanceRecordRepository attendanceRepo;
    private final StudentProgressRepository progressRepo;
    private final CourseMaterialRepository materialRepo;
    private final ExamAttemptRepository examAttemptRepo;
    private final ExamRepository examRepo;

    public EngagementScoreResponse getEngagementScores(Long academyId) {
        // Load all active students — single query
        List<Student> students = studentRepo.findByAcademyIdAndIsActiveTrue(academyId);

        // Attendance: batch aggregate [studentId, attended, total]
        Map<Long, double[]> attendanceMap = new HashMap<>();
        for (Object[] row : attendanceRepo.findAttendanceStatsByAcademy(academyId)) {
            Long studentId = ((Number) row[0]).longValue();
            double attended = ((Number) row[1]).doubleValue();
            double total    = ((Number) row[2]).doubleValue();
            attendanceMap.put(studentId, new double[]{attended, total});
        }

        // Material progress: batch aggregate [studentId, viewedCount]
        Map<Long, Long> materialViewMap = new HashMap<>();
        for (Object[] row : progressRepo.findMaterialViewCountsByAcademy(academyId)) {
            Long studentId   = ((Number) row[0]).longValue();
            long viewedCount = ((Number) row[1]).longValue();
            materialViewMap.put(studentId, viewedCount);
        }
        long totalMaterials = materialRepo.countByAcademyIdAndIsActiveTrue(academyId);

        // Exam completion: batch aggregate [studentId, distinctExamCount]
        Map<Long, Long> examAttemptsMap = new HashMap<>();
        for (Object[] row : examAttemptRepo.findDistinctExamCountsByAcademy(academyId)) {
            Long studentId = ((Number) row[0]).longValue();
            long examCount = ((Number) row[1]).longValue();
            examAttemptsMap.put(studentId, examCount);
        }
        long totalPublishedExams = examRepo.countByAcademyIdAndStatus(academyId, "PUBLISHED");

        // Build per-student scores
        List<StudentEngagementScore> scores = students.stream()
                .map(s -> buildScore(s, attendanceMap, materialViewMap, totalMaterials,
                        examAttemptsMap, totalPublishedExams))
                .sorted((a, b) -> Integer.compare(a.score(), b.score())) // ascending: at-risk first
                .toList();

        double avgScore = scores.isEmpty() ? 0.0
                : scores.stream().mapToInt(StudentEngagementScore::score).average().orElse(0.0);

        return new EngagementScoreResponse(scores, Math.round(avgScore * 10.0) / 10.0);
    }

    private StudentEngagementScore buildScore(
            Student student,
            Map<Long, double[]> attendanceMap,
            Map<Long, Long> materialViewMap,
            long totalMaterials,
            Map<Long, Long> examAttemptsMap,
            long totalPublishedExams) {

        Long id = student.getId();

        // Attendance rate
        double attendanceRate;
        double[] attStats = attendanceMap.get(id);
        if (attStats == null || attStats[1] == 0) {
            attendanceRate = 1.0;
        } else {
            attendanceRate = attStats[0] / attStats[1];
        }

        // Material progress rate
        double materialProgress;
        if (totalMaterials == 0) {
            materialProgress = 1.0;
        } else {
            long viewed = materialViewMap.getOrDefault(id, 0L);
            materialProgress = Math.min(1.0, (double) viewed / totalMaterials);
        }

        // Exam completion rate
        double examCompletion;
        if (totalPublishedExams == 0) {
            examCompletion = 1.0;
        } else {
            long attempted = examAttemptsMap.getOrDefault(id, 0L);
            examCompletion = Math.min(1.0, (double) attempted / totalPublishedExams);
        }

        // Weighted score 0–100
        int score = (int) Math.round(
                (attendanceRate * 0.40 + materialProgress * 0.35 + examCompletion * 0.25) * 100
        );
        score = Math.max(0, Math.min(100, score));

        return new StudentEngagementScore(
                id,
                student.getFullName(),
                student.getEmail(),
                score,
                attendanceRate,
                materialProgress,
                examCompletion
        );
    }
}
