package com.sigmav.sigmavbackend.modules.academy.entity;

public enum Feature {
    EXAMS("exams"),
    S3_STORAGE("s3Storage"),
    QR_ATTENDANCE("qrAttendance"),
    FORUMS("forums"),
    PDF_EXPORT("pdfExport"),
    GRADE_REPORTS("gradeReports"),
    BULK_IMPORT("bulkImport"),
    ENGAGEMENT_SCORE("engagementScore"),
    CUSTOM_ROLES("customRoles");

    private final String key;

    Feature(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static Feature fromKey(String key) {
        for (Feature f : values()) {
            if (f.key.equals(key)) return f;
        }
        throw new IllegalArgumentException("Feature desconocido: '" + key + "'");
    }
}
