package com.gan.authservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.gan.authservice.model.security.Role;
import com.gan.authservice.model.security.User;
import com.gan.authservice.model.security.enums.RoleName;
import com.gan.authservice.repository.UserRepository;
import com.gan.authservice.service.dto.UserResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @InjectMocks
    private UserService userService;

    @Test
    void getAllUsers_mapsEntitiesToResponses() {
        User user = new User("Alice", "Smith", new Role(RoleName.USER));
        UUID id = UUID.randomUUID();
        user.setId(id);
        when(userRepository.findAll()).thenReturn(List.of(user));

        List<UserResponse> responses = userService.getAllUsers();

        assertThat(responses).hasSize(1);
        UserResponse response = responses.getFirst();
        assertThat(response.getId()).isEqualTo(id.toString());
        assertThat(response.getFirstName()).isEqualTo("Alice");
        assertThat(response.getLastName()).isEqualTo("Smith");
        assertThat(response.getRoleName()).isEqualTo(RoleName.USER.name());
    }
}
