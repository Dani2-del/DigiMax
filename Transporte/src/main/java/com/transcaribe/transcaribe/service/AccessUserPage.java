package com.transcaribe.transcaribe.service;

import java.util.List;

public record AccessUserPage(
        List<AccessUser> content,
        long totalElements,
        int totalPages,
        int page,
        int size) {
}
