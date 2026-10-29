package com.operaciones.backend.auth;

import java.util.Locale;

public final class StaffRole {

    public static final String SUPERADMIN = "SUPERADMIN";
    public static final String OPERACIONES = "OPERACIONES";
    public static final String CLIENTE = "CLIENTE";

    private StaffRole() {
    }

    public static String normalizeStaff(String value) {
        if (value == null || value.isBlank()) {
            return OPERACIONES;
        }
        String role = value.trim().toUpperCase(Locale.ROOT);
        if (SUPERADMIN.equals(role) || OPERACIONES.equals(role)) {
            return role;
        }
        throw new IllegalArgumentException("Rol no válido");
    }

    public static String label(String role) {
        return switch (normalizeStaff(role)) {
            case SUPERADMIN -> "Superadmin";
            default -> "Operaciones";
        };
    }

    public static boolean isSuperAdmin(String role) {
        return SUPERADMIN.equals(normalizeStaff(role));
    }
}
