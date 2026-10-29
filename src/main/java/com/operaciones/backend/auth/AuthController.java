package com.operaciones.backend.auth;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.operaciones.backend.auth.AuthService.ProfilePhoto;
import com.operaciones.backend.auth.dto.AuthResponse;
import com.operaciones.backend.auth.dto.ChangePasswordRequest;
import com.operaciones.backend.auth.dto.DniLoginRequest;
import com.operaciones.backend.auth.dto.DniRegisterRequest;
import com.operaciones.backend.auth.dto.EmpresaLoginRequest;
import com.operaciones.backend.auth.dto.EmpresaRegisterRequest;
import com.operaciones.backend.auth.dto.FirstPasswordChangeRequest;
import com.operaciones.backend.auth.dto.InvitePasswordRequest;
import com.operaciones.backend.auth.dto.ProfileResponse;
import com.operaciones.backend.auth.dto.TotpActionRequest;
import com.operaciones.backend.auth.dto.TotpCodeRequest;
import com.operaciones.backend.auth.dto.TwoFactorSetupResponse;
import com.operaciones.backend.auth.dto.UpdateProfileRequest;
import com.operaciones.backend.auth.dto.VerificationCodeRequest;
import com.operaciones.backend.auth.dto.VerificationSendResponse;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/empresa/register")
    public AuthResponse registerEmpresa(@RequestBody EmpresaRegisterRequest request) {
        return authService.registerEmpresa(request);
    }

    @PostMapping("/empresa/login")
    public AuthResponse loginEmpresa(@RequestBody EmpresaLoginRequest request) {
        return authService.loginEmpresa(request);
    }

    @PostMapping("/empresa/reset-2fa")
    public AuthResponse resetEmpresaTwoFactor(@RequestBody EmpresaLoginRequest request) {
        return authService.resetEmpresaTwoFactor(request);
    }

    @PostMapping("/empresa/change-password")
    public AuthResponse changeEmpresaPassword(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody ChangePasswordRequest request) {
        return authService.changeEmpresaPassword(authorization, request);
    }

    @PostMapping("/empresa/first-password")
    public AuthResponse completeFirstPasswordChange(@RequestBody FirstPasswordChangeRequest request) {
        return authService.completeFirstPasswordChange(request);
    }

    @GetMapping("/invite/{token}")
    public AuthResponse openInvite(@PathVariable String token) {
        return authService.openInvite(token);
    }

    @PostMapping("/invite/password")
    public AuthResponse completeInvitePassword(@RequestBody InvitePasswordRequest request) {
        return authService.completeInvitePassword(request);
    }

    @PostMapping("/dni/register")
    public AuthResponse registerDni(@RequestBody DniRegisterRequest request) {
        return authService.registerDni(request);
    }

    @PostMapping("/dni/login")
    public AuthResponse loginDni(@RequestBody DniLoginRequest request) {
        return authService.loginDni(request);
    }

    @PostMapping("/2fa/setup")
    public AuthResponse confirmTwoFactorSetup(@RequestBody TotpCodeRequest request) {
        return authService.confirmTwoFactorSetup(request);
    }

    @PostMapping("/2fa/verify")
    public AuthResponse verifyTwoFactor(@RequestBody TotpCodeRequest request) {
        return authService.verifyTwoFactor(request);
    }

    @PostMapping("/2fa/email-code")
    public VerificationSendResponse sendLoginEmailCode(@RequestBody TotpCodeRequest request) {
        return authService.sendLoginEmailCode(request);
    }

    @GetMapping("/me")
    public AuthResponse me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return authService.me(authorization);
    }

    @GetMapping("/profile")
    public ProfileResponse getProfile(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return authService.getProfile(authorization);
    }

    @PutMapping("/profile")
    public ProfileResponse updateProfile(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody UpdateProfileRequest request) {
        return authService.updateProfile(authorization, request);
    }

    @PostMapping(value = "/profile/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProfileResponse uploadProfilePhoto(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestPart("file") MultipartFile file) {
        return authService.uploadProfilePhoto(authorization, file);
    }

    @GetMapping("/profile/photo")
    public ResponseEntity<byte[]> getProfilePhoto(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        ProfilePhoto photo = authService.getProfilePhoto(authorization);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .contentType(MediaType.parseMediaType(photo.contentType()))
                .body(photo.bytes());
    }

    @PostMapping("/profile/email/send-code")
    public VerificationSendResponse sendEmailCode(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return authService.sendEmailVerification(authorization);
    }

    @PostMapping("/profile/email/verify")
    public ProfileResponse verifyEmail(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody VerificationCodeRequest request) {
        return authService.confirmEmailVerification(authorization, request);
    }

    @GetMapping("/email/verify-link/{token}")
    public java.util.Map<String, String> verifyEmailLink(@PathVariable String token) {
        authService.confirmEmailByLink(token);
        return java.util.Map.of("message", "Correo verificado correctamente");
    }

    @PostMapping("/profile/phone/send-code")
    public VerificationSendResponse sendPhoneCode(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return authService.sendPhoneVerification(authorization);
    }

    @PostMapping("/profile/phone/verify")
    public ProfileResponse verifyPhone(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody VerificationCodeRequest request) {
        return authService.confirmPhoneVerification(authorization, request);
    }

    @PostMapping("/profile/2fa/disable")
    public ProfileResponse disableTwoFactor(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody TotpActionRequest request) {
        return authService.disableTwoFactor(authorization, request);
    }

    @PostMapping("/profile/2fa/enable/start")
    public TwoFactorSetupResponse startEnableTwoFactor(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return authService.beginEnableTwoFactor(authorization);
    }

    @PostMapping("/profile/2fa/enable/confirm")
    public ProfileResponse confirmEnableTwoFactor(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody TotpActionRequest request) {
        return authService.confirmEnableTwoFactor(authorization, request);
    }

    @PostMapping("/logout")
    public void logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        authService.logout(authorization);
    }
}
