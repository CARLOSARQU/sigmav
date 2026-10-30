package com.sigmav.sigmavbackend.modules.student.dto;

public record DniLookupResponse(
        String firstName,
        String lastName,
        String source   // "LOCAL" | "API"
) {}
