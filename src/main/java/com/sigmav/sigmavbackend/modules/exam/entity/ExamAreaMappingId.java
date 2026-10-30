package com.sigmav.sigmavbackend.modules.exam.entity;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Embeddable
@Data @NoArgsConstructor @AllArgsConstructor
public class ExamAreaMappingId implements Serializable {

    private Long examId;

    private Long areaId;
}
