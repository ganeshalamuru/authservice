package com.gan.authservice.controller.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gan.authservice.service.security.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

/**
 * Controller slice for {@link AuthController}. Security filters are disabled ({@code addFilters =
 * false}) so the slice exercises request mapping, bean validation, and the {@code GlobalExceptionHandler}
 * advice in isolation from the three security filter chains.
 */
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    private static final String VALID_BODY =
        "{\"username\":\"alice\",\"password\":\"s3cret\",\"firstName\":\"Alice\",\"lastName\":\"Smith\"}";

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private AuthService authService;

    @Test
    void signup_returns201OnValidRequest() throws Exception {
        mockMvc.perform(post("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY))
            .andExpect(status().isCreated())
            .andExpect(content().string("Successfully signed up"));
        verify(authService).createUser(any());
    }

    @Test
    void signup_returns400WhenRequiredFieldsBlank() throws Exception {
        mockMvc.perform(post("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"\",\"password\":\"\",\"firstName\":\"Alice\",\"lastName\":\"Smith\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void signup_returns409WhenServiceReportsDuplicate() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists"))
            .when(authService).createUser(any());

        mockMvc.perform(post("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.error").value("Username already exists"));
    }
}
