package com.gan.authservice.service.dto;

import com.gan.authservice.model.security.User;

/**
 * Immutable API view of a {@link User}. Exposes only {@code roleName} (not the {@code Role} JPA
 * entity) so the response carries no {@code BaseEntity} audit fields and risks no lazy-load on
 * serialize.
 */
public record UserResponse(String id, String roleName, String firstName, String lastName) {

    public static UserResponse createResponse(User user) {
        return new UserResponse(
            user.getId().toString(),
            user.getRole().getName().name(),
            user.getFirstName(),
            user.getLastName());
    }

}
