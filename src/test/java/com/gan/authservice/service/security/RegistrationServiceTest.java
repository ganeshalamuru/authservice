package com.gan.authservice.service.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gan.authservice.model.security.Role;
import com.gan.authservice.model.security.UserCredential;
import com.gan.authservice.model.security.enums.RoleName;
import com.gan.authservice.repository.RoleRepository;
import com.gan.authservice.repository.UserCredentialRepository;
import com.gan.authservice.service.security.dto.UserSignupRequest;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    @Mock
    private UserCredentialRepository userCredentialRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @InjectMocks
    private RegistrationService registrationService;

    private UserSignupRequest signupRequest() {
        return new UserSignupRequest("alice", "s3cret", "Alice", "Smith");
    }

    @Test
    void createUser_rejectsDuplicateUsernameWithConflict() {
        when(userCredentialRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> registrationService.createUser(signupRequest()))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT));

        verify(roleRepository, never()).findByName(any());
        verify(userCredentialRepository, never()).save(any());
    }

    @Test
    void createUser_persistsCredentialWithEncodedPasswordAndUserRole() {
        Role userRole = new Role(RoleName.USER);
        when(userCredentialRepository.existsByUsername("alice")).thenReturn(false);
        when(roleRepository.findByName(RoleName.USER)).thenReturn(Optional.of(userRole));
        when(passwordEncoder.encode("s3cret")).thenReturn("ENCODED");

        registrationService.createUser(signupRequest());

        ArgumentCaptor<UserCredential> captor = ArgumentCaptor.forClass(UserCredential.class);
        verify(userCredentialRepository).save(captor.capture());
        UserCredential saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo("alice");
        assertThat(saved.getEncryptedPassword()).isEqualTo("ENCODED");
        assertThat(saved.getUser().getFirstName()).isEqualTo("Alice");
        assertThat(saved.getUser().getLastName()).isEqualTo("Smith");
        assertThat(saved.getUser().getRole()).isSameAs(userRole);
    }
}
