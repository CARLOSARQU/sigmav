package com.sigmav.sigmavbackend.modules.exam.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "exam_area_mapping")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ExamAreaMapping {

    @EmbeddedId
    private ExamAreaMappingId id;
}
