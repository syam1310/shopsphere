package com.shopsphere.userservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopsphere.userservice.dto.RegisterUserRequest;
import com.shopsphere.userservice.dto.UserResponse;
import com.shopsphere.userservice.exception.EmailAlreadyRegisteredException;
import com.shopsphere.userservice.security.JwtService;
import com.shopsphere.userservice.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void register_shouldReturn201ForValidRequest() throws Exception {
        RegisterUserRequest request = new RegisterUserRequest(
                "Test User", "test@example.com", "Password123"
        );

        when(userService.registerUser(any(RegisterUserRequest.class)))
                .thenReturn(new UserResponse(1L, "Test User", "test@example.com"));

        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Test User"))
                .andExpect(jsonPath("$.email").value("test@example.com"));

        verify(userService).registerUser(any(RegisterUserRequest.class));
    }

    @Test
    void register_shouldRejectBlankName() throws Exception {
        assertValidationFailure(new RegisterUserRequest(
                " ", "test@example.com", "Password123"
        ));
    }

    @Test
    void register_shouldRejectMissingEmail() throws Exception {
        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Test User","password":"Password123"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService);
    }

    @Test
    void register_shouldRejectInvalidEmail() throws Exception {
        assertValidationFailure(new RegisterUserRequest(
                "Test User", "not-an-email", "Password123"
        ));
    }

    @Test
    void register_shouldRejectBlankPassword() throws Exception {
        assertValidationFailure(new RegisterUserRequest(
                "Test User", "test@example.com", " "
        ));
    }

    @Test
    void register_shouldRejectPasswordShorterThanEightCharacters() throws Exception {
        assertValidationFailure(new RegisterUserRequest(
                "Test User", "test@example.com", "short"
        ));
    }

    @Test
    void register_shouldReturnConflictForDuplicateEmail() throws Exception {
        RegisterUserRequest request = new RegisterUserRequest(
                "Test User", "test@example.com", "Password123"
        );

        when(userService.registerUser(any(RegisterUserRequest.class)))
                .thenThrow(new EmailAlreadyRegisteredException(
                        "Email is already registered"
                ));

        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());

        verify(userService).registerUser(any(RegisterUserRequest.class));
    }

    private void assertValidationFailure(RegisterUserRequest request)
            throws Exception {
        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService);
    }
}
