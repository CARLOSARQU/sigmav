package com.sigmav.sigmavbackend.modules.user.entity;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public enum Role {
    SUPER_ADMIN,
    ACADEMY_ADMIN,
    TEACHER,
    STUDENT,
    SALES_ADVISOR,
    ACADEMIC_COORDINATOR,
    COLLECTIONS_MANAGER;

    private static final Set<String> BUILT_IN_NAMES = Arrays.stream(values())
            .map(Enum::name)
            .collect(Collectors.toUnmodifiableSet());

    public static boolean isBuiltIn(String name) {
        return BUILT_IN_NAMES.contains(name);
    }

    public static List<String> getBuiltInRoles() {
        return Arrays.stream(values())
                .map(Enum::name)
                .toList();
    }

    public static Set<String> getBuiltInNames() {
        return BUILT_IN_NAMES;
    }
}
