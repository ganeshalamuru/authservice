package com.gan.authservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gan.authservice.model.Status;
import com.gan.authservice.model.security.Role;
import com.gan.authservice.model.security.User;
import com.gan.authservice.model.security.UserCredential;
import com.gan.authservice.model.security.enums.RoleName;
import com.gan.authservice.repository.UserCredentialRepository;
import com.gan.authservice.repository.UserRepository;
import com.gan.authservice.service.dto.UserResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserCredentialRepository userCredentialRepository;
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
        assertThat(response.id()).isEqualTo(id.toString());
        assertThat(response.firstName()).isEqualTo("Alice");
        assertThat(response.lastName()).isEqualTo("Smith");
        assertThat(response.roleName()).isEqualTo(RoleName.USER.name());
    }

    @Test
    void deleteUser_softDeletesCredentialAndUser() {
        UUID userId = UUID.randomUUID();
        User user = new User("Alice", "Smith", new Role(RoleName.USER));
        user.setId(userId);
        UserCredential credential = new UserCredential(user, "alice", "hash");
        when(userCredentialRepository.findByUserId(userId)).thenReturn(Optional.of(credential));

        userService.deleteUser(userId);

        // Both rows are stamped deleted + INACTIVE so the @SQLRestriction hides them everywhere.
        assertThat(credential.getDeletedAt()).isNotNull();
        assertThat(credential.getStatus()).isEqualTo(Status.INACTIVE);
        assertThat(user.getDeletedAt()).isNotNull();
        assertThat(user.getStatus()).isEqualTo(Status.INACTIVE);

        ArgumentCaptor<UserCredential> saved = ArgumentCaptor.forClass(UserCredential.class);
        verify(userCredentialRepository).save(saved.capture());
        assertThat(saved.getValue()).isSameAs(credential);
    }

    @Test
    void deleteUser_unknownId_throwsNotFound() {
        UUID userId = UUID.randomUUID();
        when(userCredentialRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.deleteUser(userId))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);

        verify(userCredentialRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
