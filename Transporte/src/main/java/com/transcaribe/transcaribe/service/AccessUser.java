package com.transcaribe.transcaribe.service;

import java.time.LocalDateTime;

public record AccessUser(
        String id,
        String name,
        String email,
        String role,
        boolean active,
        boolean verified,
        LocalDateTime registeredAt,
        String phone,
        String serviceAssignment) {
}
