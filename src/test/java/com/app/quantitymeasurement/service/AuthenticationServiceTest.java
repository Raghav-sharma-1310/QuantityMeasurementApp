package com.app.quantitymeasurement.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import com.app.quantitymeasurement.dto.request.AuthRequest;
import com.app.quantitymeasurement.dto.request.RegisterRequest;
import com.app.quantitymeasurement.dto.response.AuthResponse;
import com.app.quantitymeasurement.dto.response.MessageResponse;
import com.app.quantitymeasurement.entity.User;
import com.app.quantitymeasurement.enums.AuthProvider;
import com.app.quantitymeasurement.enums.Role;
import com.app.quantitymeasurement.repository.UserRepository;
import com.app.quantitymeasurement.security.UserPrincipal;
import com.app.quantitymeasurement.security.jwt.JwtTokenProvider;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock private AuthenticationManager authenticationManager;
    @Mock private UserRepository         userRepository;
    @Mock private PasswordEncoder        passwordEncoder;
    @Mock private JwtTokenProvider       jwtTokenProvider;
    @Mock private EmailService           emailService;
    @Mock private Authentication         authentication;

    @InjectMocks
    private AuthenticationService authService;

    private User localUser;
    private UserPrincipal userPrincipal;

    @BeforeEach
    void setUp() {
        // Standard user for testing
        localUser = User.builder()
            .email("alice@example.com")
            .name("Alice")
            .password("$2a$10$hashedPassword")
            .provider(AuthProvider.LOCAL)
            .role(Role.USER)
            .verified(true) // Users are now instantly verified upon signup
            .build();

        userPrincipal = UserPrincipal.create(localUser);
    }

    // =========================================================================
    // 1. REGISTER (Instant Verification, Manual Login)
    // =========================================================================

    @Test
    void testRegister_NewEmail_ReturnsSuccessMessageAndSendsEmail() {
        RegisterRequest req = new RegisterRequest("alice@example.com", "Strong@123", "Alice");

        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Strong@123")).thenReturn("$2a$10$hashedPassword");

        MessageResponse response = authService.register(req);

        // Capture saved user
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User savedUser = captor.getValue();

        // Assertions
        assertEquals("Registration successful! Please log in.", response.getMessage());
        assertTrue(savedUser.getVerified(), "User should be instantly verified");
        assertEquals("$2a$10$hashedPassword", savedUser.getPassword());
        
        verify(emailService, times(1)).sendRegistrationEmail(eq("alice@example.com"), eq("Alice"));
    }

    @Test
    void testRegister_DuplicateEmail_ThrowsConflict() {
        RegisterRequest req = new RegisterRequest("dup@example.com", "Strong@123", "Dup");

        when(userRepository.existsByEmail("dup@example.com")).thenReturn(true);

        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> authService.register(req)
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Email is already in use"));
        verify(userRepository, never()).save(any());
    }

    // =========================================================================
    // 2. LOGIN
    // =========================================================================

    @Test
    void testLogin_ValidCredentials_ReturnsToken() {
        AuthRequest req = new AuthRequest("alice@example.com", "Strong@123");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(userPrincipal);
        when(jwtTokenProvider.generateToken(authentication)).thenReturn("jwt.login.token");

        AuthResponse response = authService.login(req);

        assertNotNull(response);
        assertEquals("jwt.login.token", response.getAccessToken());
        verify(emailService, times(1)).sendLoginNotificationEmail("alice@example.com");
    }

    @Test
    void testLogin_InvalidCredentials_ThrowsUnauthorized() {
        AuthRequest req = new AuthRequest("alice@example.com", "WrongPwd@1");

        when(authenticationManager.authenticate(any()))
            .thenThrow(new BadCredentialsException("Bad credentials"));

        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> authService.login(req)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        verify(emailService, never()).sendLoginNotificationEmail(anyString());
    }

    // =========================================================================
    // 3. FORGOT PASSWORD - STEP 1 (Request OTP)
    // =========================================================================

    @Test
    void testRequestForgotPasswordOtp_ExistingEmail_SendsOtp() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(localUser));

        MessageResponse response = authService.requestForgotPasswordOtp("alice@example.com");

        assertNotNull(localUser.getOtpCode());
        assertNotNull(localUser.getOtpExpiry());
        assertEquals("OTP sent to your email.", response.getMessage());
        verify(emailService).sendForgotPasswordOtpEmail(eq("alice@example.com"), eq("Alice"), anyString());
    }

    @Test
    void testRequestForgotPasswordOtp_UnknownEmail_ThrowsNotFound() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> authService.requestForgotPasswordOtp("nobody@example.com")
        );

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    // =========================================================================
    // 4. FORGOT PASSWORD - STEP 2 (Verify OTP)
    // =========================================================================

    @Test
    void testVerifyForgotPasswordOtp_ValidOtp_Success() {
        localUser.setOtpCode("654321");
        localUser.setOtpExpiry(LocalDateTime.now().plusMinutes(5)); // Valid
        
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(localUser));

        MessageResponse response = authService.verifyForgotPasswordOtp("alice@example.com", "654321");
        assertEquals("OTP verified successfully.", response.getMessage());
    }

    @Test
    void testVerifyForgotPasswordOtp_ExpiredOtp_ThrowsBadRequest() {
        localUser.setOtpCode("654321");
        localUser.setOtpExpiry(LocalDateTime.now().minusMinutes(5)); // Expired!
        
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(localUser));

        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> authService.verifyForgotPasswordOtp("alice@example.com", "654321")
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("OTP has expired"));
    }

    // =========================================================================
    // 5. FORGOT PASSWORD - STEP 3 (Reset Password)
    // =========================================================================

    @Test
    void testResetPasswordWithOtp_ValidOtp_UpdatesPasswordAndClearsOtp() {
        localUser.setOtpCode("654321");
        localUser.setOtpExpiry(LocalDateTime.now().plusMinutes(5));

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(localUser));
        when(passwordEncoder.encode("NewStrong@123")).thenReturn("$2a$10$newHash");

        MessageResponse response = authService.resetPasswordWithOtp("alice@example.com", "654321", "NewStrong@123");

        assertEquals("Password reset successfully.", response.getMessage());
        assertEquals("$2a$10$newHash", localUser.getPassword());
        assertNull(localUser.getOtpCode()); // Ensure OTP is cleared so it cannot be reused
        assertNull(localUser.getOtpExpiry());
        
        verify(userRepository).save(localUser);
        verify(emailService).sendPasswordResetEmail("alice@example.com");
    }

    @Test
    void testResetPasswordWithOtp_InvalidOtp_ThrowsBadRequest() {
        localUser.setOtpCode("654321");

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(localUser));

        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> authService.resetPasswordWithOtp("alice@example.com", "000000", "NewStrong@123") // Wrong OTP
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertNotEquals("$2a$10$newHash", localUser.getPassword()); // Password shouldn't change
        verify(userRepository, never()).save(any());
    }
}