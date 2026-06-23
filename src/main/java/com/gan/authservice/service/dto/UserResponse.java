package com.gan.authservice.service.dto;

import com.gan.authservice.model.security.User;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class UserResponse {

    private String id;
    private String roleName;
    private String firstName;
    private String lastName;

    public static UserResponse createResponse(User user) {
        return UserResponse.builder()
            .id(user.getId().toString())
            .roleName(user.getRole().getName().name())
            .firstName(user.getFirstName())
            .lastName(user.getLastName())
            .build();
    }

}
