package com.operaciones.backend.auth;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.operaciones.backend.auth.AuthTokenStore.ChallengePurpose;
import com.operaciones.backend.auth.AuthTokenStore.Session;
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
import com.operaciones.backend.mail.AppUrlProperties;
import com.operaciones.backend.mail.MailService;
import com.operaciones.backend.mail.MailTemplates;

@Service
public class AuthService {

    private static final String ISSUER = "FinanTech S.A.";
    private static final String COMPANY = "FinanTech S.A.";
    private static final long MAX_PHOTO_BYTES = 2L * 1024L * 1024L;
    private static final long CODE_TTL_SECONDS = 10L * 60L;
    private static final SecureRandom CODE_RANDOM = new SecureRandom();
    private static final Set<String> ALLOWED_PHOTO_TYPES = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp");

    public record ProfilePhoto(byte[] bytes, String contentType) {
    }

    private final UsuarioEmpresaRepository empresaRepository;
    private final UsuarioDniRepository dniRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenStore tokenStore;
    private final TrustedDeviceStore trustedDeviceStore;
    private final MailService mailService;
    private final AppUrlProperties appUrls;

    public AuthService(
            UsuarioEmpresaRepository empresaRepository,
            UsuarioDniRepository dniRepository,
            PasswordEncoder passwordEncoder,
            AuthTokenStore tokenStore,
            TrustedDeviceStore trustedDeviceStore,
            MailService mailService,
            AppUrlProperties appUrls) {
        this.empresaRepository = empresaRepository;
        this.dniRepository = dniRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenStore = tokenStore;
        this.trustedDeviceStore = trustedDeviceStore;
        this.mailService = mailService;
        this.appUrls = appUrls;
    }

    public AuthResponse registerEmpresa(EmpresaRegisterRequest request) {
        String nombre = requireText(request.nombre(), "El nombre es obligatorio");
        String correo = requireEmail(request.correo());
        String password = requirePassword(request.password());

        if (empresaRepository.existsByCorreoIgnoreCase(correo)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El correo ya está registrado");
        }

        UsuarioEmpresa usuario = new UsuarioEmpresa();
        usuario.setNombre(nombre);
        usuario.setApodo(nombre);
        usuario.setCorreo(correo);
        usuario.setRol(defaultStaffRole(correo));
        usuario.setPasswordHash(passwordEncoder.encode(password));
        usuario.setTotpEnabled(false);
        usuario.setMustChangePassword(true);
        usuario.setTotpSecret(TotpUtil.generateSecret());
        empresaRepository.save(usuario);

        return beginPasswordChange(usuario);
    }

    public AuthResponse loginEmpresa(EmpresaLoginRequest request) {
        String correo = requireEmail(request.correo());
        String password = requirePassword(request.password());

        UsuarioEmpresa usuario = empresaRepository.findByCorreoIgnoreCase(correo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas"));

        if (!passwordEncoder.matches(password, usuario.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas");
        }

        if (!usuario.isActivo()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cuenta desactivada. Contacta a FinanTech S.A.");
        }

        // Orden: 1) cambiar contraseña  2) vincular 2FA  3) verificar 2FA
        if (usuario.isMustChangePassword()) {
            return beginPasswordChange(usuario);
        }

        if (!usuario.isTotpEnabled()) {
            return beginEmpresaTwoFactorSetup(prepareTotpSecret(usuario));
        }

        String display = displayOf(usuario);
        String trustedToken = request.trustedDeviceToken();
        if (trustedDeviceStore.isTrusted(usuario.getId(), trustedToken)) {
            trustedDeviceStore.revoke(trustedToken);
            String refreshedTrust = trustedDeviceStore.issue(usuario.getId());
            Session session = tokenStore.createSession(usuario.getId(), "empresa", display);
            return AuthResponse.ok(session.token(), "empresa", display, refreshedTrust);
        }

        var challenge = tokenStore.createChallenge(
                usuario.getId(),
                "empresa",
                display,
                ChallengePurpose.VERIFY_2FA);
        return AuthResponse.need2fa(challenge.token(), "empresa", display, usuario.isCorreoVerificado());
    }

    /** Envía código de login al correo (solo si el correo ya está verificado). */
    public VerificationSendResponse sendLoginEmailCode(TotpCodeRequest request) {
        var challenge = tokenStore.findChallenge(request.token(), ChallengePurpose.VERIFY_2FA)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión 2FA expirada"));
        if (!"empresa".equals(challenge.kind())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo aplica a acceso por correo");
        }
        UsuarioEmpresa usuario = empresaRepository.findById(challenge.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado"));
        if (!usuario.isCorreoVerificado()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Primero verifica tu correo desde el perfil");
        }
        String code = newVerificationCode();
        tokenStore.putLoginEmailCode(challenge.token(), code, Instant.now().plusSeconds(CODE_TTL_SECONDS));
        boolean mailed = mailService.sendText(
                usuario.getCorreo(),
                "FinanTech S.A. — Código de inicio de sesión",
                "Hola,\n\nTu código para iniciar sesión en FinanTech S.A. es: "
                        + code
                        + "\n\nCaduca en unos minutos. Si no solicitaste este código, ignora este mensaje.\n");
        return new VerificationSendResponse(
                "login-email",
                mailed
                        ? "Código enviado a " + usuario.getCorreo() + "."
                        : "No se pudo enviar el correo. Código temporal: " + code,
                mailed ? null : code,
                null,
                null,
                mailed,
                CODE_TTL_SECONDS);
    }

    public AuthResponse completeFirstPasswordChange(FirstPasswordChangeRequest request) {
        var challenge = tokenStore.consumeChallenge(request.token(), ChallengePurpose.CHANGE_PASSWORD)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión expirada"));

        String currentPassword = requirePassword(request.currentPassword());
        String newPassword = requirePassword(request.newPassword());

        if ("empresa".equals(challenge.kind())) {
            UsuarioEmpresa usuario = empresaRepository.findById(challenge.userId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado"));

            if (!passwordEncoder.matches(currentPassword, usuario.getPasswordHash())) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Contraseña actual incorrecta");
            }
            if (passwordEncoder.matches(newPassword, usuario.getPasswordHash())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La nueva contraseña debe ser distinta");
            }

            usuario.setPasswordHash(passwordEncoder.encode(newPassword));
            usuario.setMustChangePassword(false);
            usuario.setInviteToken(null);
            usuario.setInviteExpira(null);
            usuario.setTotpEnabled(false);
            usuario.setTotpSecret(TotpUtil.generateSecret());
            empresaRepository.save(usuario);
            return beginEmpresaTwoFactorSetup(usuario);
        }

        if ("dni".equals(challenge.kind())) {
            UsuarioDni usuario = dniRepository.findById(challenge.userId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado"));
            if (!passwordEncoder.matches(currentPassword, usuario.getPasswordHash())) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Contraseña actual incorrecta");
            }
            if (passwordEncoder.matches(newPassword, usuario.getPasswordHash())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La nueva contraseña debe ser distinta");
            }
            usuario.setPasswordHash(passwordEncoder.encode(newPassword));
            usuario.setMustChangePassword(false);
            usuario.setInviteToken(null);
            usuario.setInviteExpira(null);
            dniRepository.save(usuario);
            String display = displayOf(usuario);
            Session session = tokenStore.createSession(usuario.getId(), "dni", display);
            return AuthResponse.ok(session.token(), "dni", display);
        }

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cambio de contraseña no válido");
    }

    /** Reinicia el autenticador (pide QR + clave otra vez). Requiere correo y contraseña. */
    public AuthResponse resetEmpresaTwoFactor(EmpresaLoginRequest request) {
        String correo = requireEmail(request.correo());
        String password = requirePassword(request.password());

        UsuarioEmpresa usuario = empresaRepository.findByCorreoIgnoreCase(correo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas"));

        if (!passwordEncoder.matches(password, usuario.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas");
        }

        usuario.setMustChangePassword(false);
        usuario.setTotpEnabled(false);
        usuario.setTotpSecret(TotpUtil.generateSecret());
        empresaRepository.save(usuario);
        return beginEmpresaTwoFactorSetup(usuario);
    }

    public AuthResponse registerDni(DniRegisterRequest request) {
        String nombres = requireText(request.nombres(), "Los nombres son obligatorios");
        String dni = requireDni(request.dni());
        String password = requirePassword(request.password());

        if (dniRepository.existsByDni(dni)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El DNI ya está registrado");
        }

        UsuarioDni usuario = new UsuarioDni();
        usuario.setNombres(nombres);
        usuario.setApodo(nombres);
        usuario.setDni(dni);
        usuario.setPasswordHash(passwordEncoder.encode(password));
        usuario.setCorreoVerificado(false);
        dniRepository.save(usuario);

        String display = displayOf(usuario);
        Session session = tokenStore.createSession(usuario.getId(), "dni", display);
        return AuthResponse.ok(session.token(), "dni", display);
    }

    public AuthResponse loginDni(DniLoginRequest request) {
        String dni = requireDni(request.dni());
        String password = requirePassword(request.password());

        UsuarioDni usuario = dniRepository.findByDni(dni)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas"));

        if (!passwordEncoder.matches(password, usuario.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas");
        }

        if (!usuario.isActivo()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cuenta desactivada. Contacta a FinanTech S.A.");
        }

        if (usuario.isMustChangePassword()) {
            return beginDniPasswordChange(usuario);
        }

        String display = displayOf(usuario);
        Session session = tokenStore.createSession(usuario.getId(), "dni", display);
        return AuthResponse.ok(session.token(), "dni", display);
    }

    public AuthResponse openInvite(String inviteToken) {
        String token = requireText(inviteToken, "Invitación inválida");
        UsuarioEmpresa empresa = empresaRepository.findByInviteToken(token).orElse(null);
        if (empresa != null) {
            if (!empresa.isActivo()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cuenta desactivada");
            }
            assertInviteActive(empresa.getInviteToken(), empresa.getInviteExpira());
            return beginPasswordChange(empresa);
        }
        UsuarioDni dni = dniRepository.findByInviteToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invitación no válida o expirada"));
        if (!dni.isActivo()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cuenta desactivada");
        }
        assertInviteActive(dni.getInviteToken(), dni.getInviteExpira());
        return beginDniPasswordChange(dni);
    }

    public AuthResponse completeInvitePassword(InvitePasswordRequest request) {
        var challenge = tokenStore.consumeChallenge(request.token(), ChallengePurpose.CHANGE_PASSWORD)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión expirada"));

        String newPassword = requirePassword(request.newPassword());

        if ("empresa".equals(challenge.kind())) {
            UsuarioEmpresa usuario = empresaRepository.findById(challenge.userId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado"));
            if (passwordEncoder.matches(newPassword, usuario.getPasswordHash())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La nueva contraseña debe ser distinta");
            }
            usuario.setPasswordHash(passwordEncoder.encode(newPassword));
            usuario.setMustChangePassword(false);
            usuario.setInviteToken(null);
            usuario.setInviteExpira(null);
            usuario.setTotpEnabled(false);
            usuario.setTotpSecret(TotpUtil.generateSecret());
            empresaRepository.save(usuario);
            return beginEmpresaTwoFactorSetup(usuario);
        }

        if ("dni".equals(challenge.kind())) {
            UsuarioDni usuario = dniRepository.findById(challenge.userId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado"));
            if (passwordEncoder.matches(newPassword, usuario.getPasswordHash())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La nueva contraseña debe ser distinta");
            }
            usuario.setPasswordHash(passwordEncoder.encode(newPassword));
            usuario.setMustChangePassword(false);
            usuario.setInviteToken(null);
            usuario.setInviteExpira(null);
            dniRepository.save(usuario);
            String display = displayOf(usuario);
            Session session = tokenStore.createSession(usuario.getId(), "dni", display);
            return AuthResponse.ok(session.token(), "dni", display);
        }

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invitación no válida");
    }

    public AuthResponse confirmTwoFactorSetup(TotpCodeRequest request) {
        var challenge = tokenStore.consumeChallenge(request.token(), ChallengePurpose.SETUP_2FA)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión 2FA expirada"));

        if (!"empresa".equals(challenge.kind())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "2FA solo aplica a acceso por correo");
        }

        UsuarioEmpresa usuario = empresaRepository.findById(challenge.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado"));

        if (usuario.isMustChangePassword()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debes cambiar la contraseña antes del 2FA");
        }

        if (!TotpUtil.verifyCode(usuario.getTotpSecret(), requireTotpCode(request.code()))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Código del autenticador inválido");
        }

        usuario.setTotpEnabled(true);
        empresaRepository.save(usuario);

        String display = displayOf(usuario);
        Session session = tokenStore.createSession(usuario.getId(), "empresa", display);
        return AuthResponse.ok(
                session.token(),
                "empresa",
                display,
                issueTrustedDeviceIfRequested(usuario.getId(), request.rememberDevice()));
    }

    public AuthResponse verifyTwoFactor(TotpCodeRequest request) {
        var challenge = tokenStore.findChallenge(request.token(), ChallengePurpose.VERIFY_2FA)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión 2FA expirada"));

        if (!"empresa".equals(challenge.kind())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "2FA solo aplica a acceso por correo");
        }

        UsuarioEmpresa usuario = empresaRepository.findById(challenge.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado"));

        String code = requireTotpCode(request.code());
        boolean totpOk = usuario.isTotpEnabled() && TotpUtil.verifyCode(usuario.getTotpSecret(), code);
        boolean emailOk = false;
        if (!totpOk) {
            var emailCode = tokenStore.findLoginEmailCode(challenge.token());
            emailOk = emailCode.isPresent() && emailCode.get().code().equals(code);
        }
        if (!totpOk && !emailOk) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Código inválido");
        }

        tokenStore.consumeChallenge(challenge.token(), ChallengePurpose.VERIFY_2FA);

        String display = displayOf(usuario);
        Session session = tokenStore.createSession(usuario.getId(), "empresa", display);
        return AuthResponse.ok(
                session.token(),
                "empresa",
                display,
                issueTrustedDeviceIfRequested(usuario.getId(), request.rememberDevice()));
    }

    private String issueTrustedDeviceIfRequested(Long userId, Boolean rememberDevice) {
        if (!Boolean.TRUE.equals(rememberDevice)) {
            return null;
        }
        return trustedDeviceStore.issue(userId);
    }

    public AuthResponse changeEmpresaPassword(String bearerToken, ChangePasswordRequest request) {
        Session session = requireEmpresaSession(bearerToken);
        UsuarioEmpresa usuario = empresaRepository.findById(session.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado"));

        String currentPassword = requirePassword(request.currentPassword());
        String newPassword = requirePassword(request.newPassword());

        if (!passwordEncoder.matches(currentPassword, usuario.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Contraseña actual incorrecta");
        }

        if (passwordEncoder.matches(newPassword, usuario.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La nueva contraseña debe ser distinta");
        }

        usuario.setPasswordHash(passwordEncoder.encode(newPassword));
        usuario.setMustChangePassword(false);
        usuario.setTotpEnabled(false);
        usuario.setTotpSecret(TotpUtil.generateSecret());
        empresaRepository.save(usuario);

        tokenStore.revokeSession(session.token());
        return beginEmpresaTwoFactorSetup(usuario);
    }

    public AuthResponse me(String bearerToken) {
        Session session = requireSession(bearerToken);
        return AuthResponse.ok(session.token(), session.kind(), session.displayName());
    }

    public ProfileResponse getProfile(String bearerToken) {
        Session session = requireSession(bearerToken);
        return buildProfile(session);
    }

    public ProfileResponse updateProfile(String bearerToken, UpdateProfileRequest request) {
        Session session = requireSession(bearerToken);
        String token = session.token();
        String apodo = requireText(request.apodo(), "El apodo es obligatorio");
        String telefono = normalizeTelefono(request.telefono());

        if ("empresa".equals(session.kind())) {
            UsuarioEmpresa usuario = empresaRepository.findById(session.userId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión inválida"));
            ensureStaffDefaults(usuario);

            if (request.nombre() != null && !request.nombre().isBlank()) {
                usuario.setNombre(request.nombre().trim());
            }

            String previousPhone = blankToNull(usuario.getTelefono());
            usuario.setApodo(apodo);
            usuario.setTelefono(telefono);
            if (telefono == null || !telefono.equals(previousPhone)) {
                usuario.setTelefonoVerificado(false);
                usuario.setCodigoTelefono(null);
                usuario.setCodigoTelefonoExpira(null);
            }

            if (request.role() != null && !request.role().isBlank()) {
                if (!StaffRole.isSuperAdmin(usuario.getRol())) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo un Superadmin puede cambiar el rol");
                }
                try {
                    usuario.setRol(StaffRole.normalizeStaff(request.role()));
                } catch (IllegalArgumentException ex) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rol no válido");
                }
            }

            empresaRepository.save(usuario);
            tokenStore.updateSessionDisplayName(token, displayOf(usuario));
        } else {
            UsuarioDni usuario = dniRepository.findById(session.userId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión inválida"));
            if (request.nombre() != null && !request.nombre().isBlank()) {
                usuario.setNombres(request.nombre().trim());
            }
            String previousPhone = blankToNull(usuario.getTelefono());
            usuario.setApodo(apodo);
            usuario.setTelefono(telefono);
            if (telefono == null || !telefono.equals(previousPhone)) {
                usuario.setTelefonoVerificado(false);
                usuario.setCodigoTelefono(null);
                usuario.setCodigoTelefonoExpira(null);
            }
            if (request.direccion() != null) {
                usuario.setDireccion(request.direccion().isBlank() ? null : request.direccion().trim());
            }
            if (request.trabajo() != null) {
                usuario.setTrabajo(request.trabajo().isBlank() ? null : request.trabajo().trim());
            }
            if (request.ingresosMensuales() != null) {
                String raw = request.ingresosMensuales().trim().replace(",", ".");
                if (raw.isBlank()) {
                    usuario.setIngresosMensuales(null);
                } else {
                    try {
                        usuario.setIngresosMensuales(new java.math.BigDecimal(raw).setScale(2, java.math.RoundingMode.HALF_UP));
                    } catch (NumberFormatException ex) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ingresos mensuales inválidos");
                    }
                }
            }
            if (request.correo() != null) {
                String previous = blankToNull(usuario.getCorreo());
                if (request.correo().isBlank()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El correo es obligatorio para clientes");
                }
                String correo = requireEmail(request.correo());
                if (dniRepository.existsByCorreoIgnoreCase(correo)
                        && (previous == null || !previous.equalsIgnoreCase(correo))) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Ese correo ya está en uso");
                }
                usuario.setCorreo(correo);
                if (previous == null || !previous.equalsIgnoreCase(correo)) {
                    usuario.setCorreoVerificado(false);
                    usuario.setCodigoCorreo(null);
                    usuario.setCodigoCorreoExpira(null);
                    mailService.sendHtml(
                            correo,
                            "FinanTech S.A. — Bienvenido, registra y verifica tu correo",
                            MailTemplates.welcomeClient(
                                    displayOf(usuario),
                                    usuario.getDni(),
                                    appUrls.absoluteFrontend("/perfil")));
                }
            }
            dniRepository.save(usuario);
            tokenStore.updateSessionDisplayName(token, displayOf(usuario));
        }

        return buildProfile(tokenStore.findSession(token).orElse(session));
    }

    public ProfileResponse uploadProfilePhoto(String bearerToken, MultipartFile file) {
        Session session = requireSession(bearerToken);
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona una foto");
        }
        if (file.getSize() > MAX_PHOTO_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La foto no puede superar 2 MB");
        }

        String contentType = file.getContentType() == null
                ? ""
                : file.getContentType().trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_PHOTO_TYPES.contains(contentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Usa una imagen JPG, PNG o WEBP");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se pudo leer la foto");
        }
        if (bytes.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona una foto");
        }

        String normalizedType = contentType.equals("image/jpg") ? "image/jpeg" : contentType;

        if ("empresa".equals(session.kind())) {
            UsuarioEmpresa usuario = empresaRepository.findById(session.userId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión inválida"));
            usuario.setFoto(bytes);
            usuario.setFotoContentType(normalizedType);
            empresaRepository.save(usuario);
        } else {
            UsuarioDni usuario = dniRepository.findById(session.userId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión inválida"));
            usuario.setFoto(bytes);
            usuario.setFotoContentType(normalizedType);
            dniRepository.save(usuario);
        }

        return buildProfile(session);
    }

    public ProfilePhoto getProfilePhoto(String bearerToken) {
        Session session = requireSession(bearerToken);
        if ("empresa".equals(session.kind())) {
            UsuarioEmpresa usuario = empresaRepository.findById(session.userId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión inválida"));
            if (usuario.getFoto() == null || usuario.getFoto().length == 0) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sin foto de perfil");
            }
            String type = usuario.getFotoContentType() == null || usuario.getFotoContentType().isBlank()
                    ? "image/jpeg"
                    : usuario.getFotoContentType();
            return new ProfilePhoto(usuario.getFoto(), type);
        }

        UsuarioDni usuario = dniRepository.findById(session.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión inválida"));
        if (usuario.getFoto() == null || usuario.getFoto().length == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sin foto de perfil");
        }
        String type = usuario.getFotoContentType() == null || usuario.getFotoContentType().isBlank()
                ? "image/jpeg"
                : usuario.getFotoContentType();
        return new ProfilePhoto(usuario.getFoto(), type);
    }

    public VerificationSendResponse sendEmailVerification(String bearerToken) {
        Session session = requireSession(bearerToken);
        String path;
        String url;
        String to;
        String nombre;
        boolean mailed;

        if ("empresa".equals(session.kind())) {
            UsuarioEmpresa usuario = loadEmpresa(session.userId());
            if (usuario.isCorreoVerificado()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El correo ya está verificado");
            }
            String token = newEmailVerifyToken();
            usuario.setCodigoCorreo(token);
            usuario.setCodigoCorreoExpira(Instant.now().plusSeconds(CODE_TTL_SECONDS));
            empresaRepository.save(usuario);
            path = "/verificar-correo/" + token;
            url = appUrls.absoluteFrontend(path);
            to = usuario.getCorreo();
            nombre = displayOf(usuario);
            mailed = mailService.sendHtml(
                    to,
                    "FinanTech S.A. — Verifica tu correo",
                    MailTemplates.verifyEmail(nombre, url));
        } else {
            UsuarioDni usuario = loadDni(session.userId());
            if (blankToNull(usuario.getCorreo()) == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Primero registra tu correo en el perfil");
            }
            if (usuario.isCorreoVerificado()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El correo ya está verificado");
            }
            String token = newEmailVerifyToken();
            usuario.setCodigoCorreo(token);
            usuario.setCodigoCorreoExpira(Instant.now().plusSeconds(CODE_TTL_SECONDS));
            dniRepository.save(usuario);
            path = "/verificar-correo/" + token;
            url = appUrls.absoluteFrontend(path);
            to = usuario.getCorreo();
            nombre = displayOf(usuario);
            mailed = mailService.sendHtml(
                    to,
                    "FinanTech S.A. — Verifica tu correo",
                    MailTemplates.verifyEmail(nombre, url));
        }

        return new VerificationSendResponse(
                "email",
                mailed
                        ? "Enviamos un enlace a " + to + " para verificar tu correo."
                        : "No se pudo enviar el correo. Usa el enlace de verificación manual.",
                null,
                path,
                url,
                mailed,
                CODE_TTL_SECONDS);
    }

    /** Confirma correo con el enlace del mensaje (sin sesión). */
    public void confirmEmailByLink(String rawToken) {
        String token = requireText(rawToken, "Enlace inválido");
        UsuarioEmpresa empresa = empresaRepository.findByCodigoCorreo(token).orElse(null);
        if (empresa != null) {
            if (empresa.getCodigoCorreoExpira() == null || empresa.getCodigoCorreoExpira().isBefore(Instant.now())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El enlace expiró. Solicita uno nuevo desde tu perfil");
            }
            empresa.setCorreoVerificado(true);
            empresa.setCodigoCorreo(null);
            empresa.setCodigoCorreoExpira(null);
            empresaRepository.save(empresa);
            return;
        }
        UsuarioDni cliente = dniRepository.findByCodigoCorreo(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enlace inválido o ya usado"));
        if (cliente.getCodigoCorreoExpira() == null || cliente.getCodigoCorreoExpira().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El enlace expiró. Solicita uno nuevo desde tu perfil");
        }
        cliente.setCorreoVerificado(true);
        cliente.setCodigoCorreo(null);
        cliente.setCodigoCorreoExpira(null);
        dniRepository.save(cliente);
    }

    public ProfileResponse confirmEmailVerification(String bearerToken, VerificationCodeRequest request) {
        Session session = requireSession(bearerToken);
        String token = requireText(request.code(), "Enlace inválido");
        if ("empresa".equals(session.kind())) {
            UsuarioEmpresa usuario = loadEmpresa(session.userId());
            if (usuario.getCodigoCorreo() == null
                    || usuario.getCodigoCorreoExpira() == null
                    || !usuario.getCodigoCorreo().equals(token)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Enlace inválido");
            }
            if (usuario.getCodigoCorreoExpira().isBefore(Instant.now())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El enlace expiró. Solicita uno nuevo");
            }
            usuario.setCorreoVerificado(true);
            usuario.setCodigoCorreo(null);
            usuario.setCodigoCorreoExpira(null);
            empresaRepository.save(usuario);
        } else {
            UsuarioDni usuario = loadDni(session.userId());
            if (usuario.getCodigoCorreo() == null
                    || usuario.getCodigoCorreoExpira() == null
                    || !usuario.getCodigoCorreo().equals(token)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Enlace inválido");
            }
            if (usuario.getCodigoCorreoExpira().isBefore(Instant.now())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El enlace expiró. Solicita uno nuevo");
            }
            usuario.setCorreoVerificado(true);
            usuario.setCodigoCorreo(null);
            usuario.setCodigoCorreoExpira(null);
            dniRepository.save(usuario);
        }
        return buildProfile(session);
    }

    /** Cliente debe tener correo verificado para operar créditos/pagos. */
    public void requireClientEmailVerified(String bearer) {
        Session session = requireSession(bearer);
        if (!"dni".equals(session.kind())) {
            return;
        }
        UsuarioDni usuario = loadDni(session.userId());
        if (blankToNull(usuario.getCorreo()) == null || !usuario.isCorreoVerificado()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Debes registrar y verificar tu correo en Perfil para continuar");
        }
    }

    public VerificationSendResponse sendPhoneVerification(String bearerToken) {
        Session session = requireSession(bearerToken);
        String code = newVerificationCode();
        Instant expires = Instant.now().plusSeconds(CODE_TTL_SECONDS);

        if ("empresa".equals(session.kind())) {
            UsuarioEmpresa usuario = loadEmpresa(session.userId());
            String phone = blankToNull(usuario.getTelefono());
            if (phone == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Primero guarda un número de teléfono");
            }
            if (usuario.isTelefonoVerificado()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El teléfono ya está verificado");
            }
            usuario.setCodigoTelefono(code);
            usuario.setCodigoTelefonoExpira(expires);
            empresaRepository.save(usuario);
            return new VerificationSendResponse(
                    "phone",
                    "Código generado para " + phone + ". Ingresa el código para verificar.",
                    code,
                    null,
                    null,
                    false,
                    CODE_TTL_SECONDS);
        }

        UsuarioDni usuario = loadDni(session.userId());
        String phone = blankToNull(usuario.getTelefono());
        if (phone == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Primero guarda un número de teléfono");
        }
        if (usuario.isTelefonoVerificado()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El teléfono ya está verificado");
        }
        usuario.setCodigoTelefono(code);
        usuario.setCodigoTelefonoExpira(expires);
        dniRepository.save(usuario);
        return new VerificationSendResponse(
                "phone",
                "Código generado para " + phone + ". Ingresa el código para verificar.",
                code,
                null,
                null,
                false,
                CODE_TTL_SECONDS);
    }

    public ProfileResponse confirmPhoneVerification(String bearerToken, VerificationCodeRequest request) {
        Session session = requireSession(bearerToken);
        if ("empresa".equals(session.kind())) {
            UsuarioEmpresa usuario = loadEmpresa(session.userId());
            assertCode(usuario.getCodigoTelefono(), usuario.getCodigoTelefonoExpira(), request.code());
            usuario.setTelefonoVerificado(true);
            usuario.setCodigoTelefono(null);
            usuario.setCodigoTelefonoExpira(null);
            empresaRepository.save(usuario);
            return buildProfile(session);
        }

        UsuarioDni usuario = loadDni(session.userId());
        assertCode(usuario.getCodigoTelefono(), usuario.getCodigoTelefonoExpira(), request.code());
        usuario.setTelefonoVerificado(true);
        usuario.setCodigoTelefono(null);
        usuario.setCodigoTelefonoExpira(null);
        dniRepository.save(usuario);
        return buildProfile(session);
    }

    public ProfileResponse disableTwoFactor(String bearerToken, TotpActionRequest request) {
        Session session = requireEmpresaSession(bearerToken);
        UsuarioEmpresa usuario = loadEmpresa(session.userId());
        if (!usuario.isTotpEnabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La verificación en dos pasos ya está desactivada");
        }
        if (!TotpUtil.verifyCode(usuario.getTotpSecret(), requireTotpCode(request.code()))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Código del autenticador inválido");
        }
        usuario.setTotpEnabled(false);
        empresaRepository.save(usuario);
        return buildProfile(session);
    }

    public TwoFactorSetupResponse beginEnableTwoFactor(String bearerToken) {
        Session session = requireEmpresaSession(bearerToken);
        UsuarioEmpresa usuario = loadEmpresa(session.userId());
        if (usuario.isTotpEnabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La verificación en dos pasos ya está activa");
        }
        usuario = prepareTotpSecret(usuario);
        String otpauthUrl = TotpUtil.otpAuthUrl(ISSUER, usuario.getCorreo(), usuario.getTotpSecret());
        return new TwoFactorSetupResponse(
                otpauthUrl,
                usuario.getTotpSecret(),
                "Escanea el código o ingresa la clave en tu autenticador y confirma con un código.");
    }

    public ProfileResponse confirmEnableTwoFactor(String bearerToken, TotpActionRequest request) {
        Session session = requireEmpresaSession(bearerToken);
        UsuarioEmpresa usuario = loadEmpresa(session.userId());
        if (usuario.isTotpEnabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La verificación en dos pasos ya está activa");
        }
        usuario = prepareTotpSecret(usuario);
        if (!TotpUtil.verifyCode(usuario.getTotpSecret(), requireTotpCode(request.code()))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Código del autenticador inválido");
        }
        usuario.setTotpEnabled(true);
        empresaRepository.save(usuario);
        return buildProfile(session);
    }

    public void logout(String bearerToken) {
        tokenStore.revokeSession(extractBearer(bearerToken));
    }

    private ProfileResponse buildProfile(Session session) {
        if ("empresa".equals(session.kind())) {
            UsuarioEmpresa usuario = loadEmpresa(session.userId());
            ensureStaffDefaults(usuario);
            boolean hasPhoto = usuario.getFoto() != null && usuario.getFoto().length > 0;
            String roleCode = usuario.getRol();
            return new ProfileResponse(
                    "empresa",
                    displayOf(usuario),
                    usuario.getNombre(),
                    blankToNull(usuario.getApodo()) == null ? usuario.getNombre() : usuario.getApodo(),
                    COMPANY,
                    "Correo de empresa",
                    usuario.getCorreo(),
                    usuario.getCorreo(),
                    blankToNull(usuario.getTelefono()),
                    StaffRole.label(roleCode),
                    roleCode,
                    StaffRole.isSuperAdmin(roleCode),
                    usuario.isCorreoVerificado(),
                    usuario.isTelefonoVerificado(),
                    usuario.isTotpEnabled(),
                    hasPhoto,
                    hasPhoto ? "/api/auth/profile/photo" : null,
                    null,
                    null,
                    null);
        }

        UsuarioDni usuario = loadDni(session.userId());
        boolean hasPhoto = usuario.getFoto() != null && usuario.getFoto().length > 0;
        String ingresos = usuario.getIngresosMensuales() == null
                ? null
                : usuario.getIngresosMensuales().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
        return new ProfileResponse(
                "dni",
                displayOf(usuario),
                usuario.getNombres(),
                blankToNull(usuario.getApodo()) == null ? usuario.getNombres() : usuario.getApodo(),
                COMPANY,
                "DNI de cliente",
                usuario.getDni(),
                blankToNull(usuario.getCorreo()),
                blankToNull(usuario.getTelefono()),
                "Cliente",
                StaffRole.CLIENTE,
                false,
                usuario.isCorreoVerificado(),
                usuario.isTelefonoVerificado(),
                false,
                hasPhoto,
                hasPhoto ? "/api/auth/profile/photo" : null,
                blankToNull(usuario.getDireccion()),
                blankToNull(usuario.getTrabajo()),
                ingresos);
    }

    private static String normalizeTelefono(String telefono) {
        if (telefono == null || telefono.isBlank()) {
            return null;
        }
        String value = telefono.trim();
        if (value.length() > 30) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Teléfono demasiado largo");
        }
        return value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private AuthResponse beginPasswordChange(UsuarioEmpresa usuario) {
        String display = displayOf(usuario);
        var challenge = tokenStore.createChallenge(
                usuario.getId(),
                "empresa",
                display,
                ChallengePurpose.CHANGE_PASSWORD);
        return AuthResponse.changePassword(challenge.token(), "empresa", display);
    }

    private AuthResponse beginDniPasswordChange(UsuarioDni usuario) {
        String display = displayOf(usuario);
        var challenge = tokenStore.createChallenge(
                usuario.getId(),
                "dni",
                display,
                ChallengePurpose.CHANGE_PASSWORD);
        return AuthResponse.changePassword(challenge.token(), "dni", display);
    }

    private static void assertInviteActive(String token, Instant expires) {
        if (token == null || token.isBlank() || expires == null || expires.isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invitación no válida o expirada");
        }
    }

    private UsuarioEmpresa prepareTotpSecret(UsuarioEmpresa usuario) {
        if (usuario.getTotpSecret() == null || usuario.getTotpSecret().isBlank()) {
            usuario.setTotpSecret(TotpUtil.generateSecret());
            empresaRepository.save(usuario);
        }
        return usuario;
    }

    private AuthResponse beginEmpresaTwoFactorSetup(UsuarioEmpresa usuario) {
        usuario = prepareTotpSecret(usuario);
        String display = displayOf(usuario);
        var challenge = tokenStore.createChallenge(
                usuario.getId(),
                "empresa",
                display,
                ChallengePurpose.SETUP_2FA);
        String otpauthUrl = TotpUtil.otpAuthUrl(ISSUER, usuario.getCorreo(), usuario.getTotpSecret());
        return AuthResponse.setup2fa(
                challenge.token(),
                "empresa",
                display,
                otpauthUrl,
                usuario.getTotpSecret());
    }

    private UsuarioEmpresa loadEmpresa(Long id) {
        return empresaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión inválida"));
    }

    private UsuarioDni loadDni(Long id) {
        return dniRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión inválida"));
    }

    private void ensureStaffDefaults(UsuarioEmpresa usuario) {
        boolean dirty = false;
        if (usuario.getApodo() == null || usuario.getApodo().isBlank()) {
            usuario.setApodo(usuario.getNombre());
            dirty = true;
        }
        if (usuario.getRol() == null || usuario.getRol().isBlank()) {
            usuario.setRol(defaultStaffRole(usuario.getCorreo()));
            dirty = true;
        }
        if (dirty) {
            empresaRepository.save(usuario);
        }
    }

    private static String defaultStaffRole(String correo) {
        if (correo != null && correo.toLowerCase(Locale.ROOT).startsWith("admin@")) {
            return StaffRole.SUPERADMIN;
        }
        return StaffRole.OPERACIONES;
    }

    private static String displayOf(UsuarioEmpresa usuario) {
        if (usuario.getApodo() != null && !usuario.getApodo().isBlank()) {
            return usuario.getApodo().trim();
        }
        return usuario.getNombre();
    }

    private static String displayOf(UsuarioDni usuario) {
        if (usuario.getApodo() != null && !usuario.getApodo().isBlank()) {
            return usuario.getApodo().trim();
        }
        return usuario.getNombres();
    }

    private static String newVerificationCode() {
        int value = CODE_RANDOM.nextInt(1_000_000);
        return String.format(Locale.ROOT, "%06d", value);
    }

    private static String newEmailVerifyToken() {
        byte[] bytes = new byte[24];
        CODE_RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format(Locale.ROOT, "%02x", b));
        }
        return sb.toString();
    }

    private static void assertCode(String expected, Instant expiresAt, String provided) {
        String code = requireTotpCode(provided);
        if (expected == null || expected.isBlank() || expiresAt == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solicita un código primero");
        }
        if (expiresAt.isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El código expiró. Solicita uno nuevo");
        }
        if (!expected.equals(code)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Código inválido");
        }
    }

    private Session requireEmpresaSession(String bearerToken) {
        Session session = requireSession(bearerToken);
        if (!"empresa".equals(session.kind())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo disponible para acceso por correo");
        }
        return session;
    }

    private Session requireSession(String bearerToken) {
        return tokenStore.findSession(extractBearer(bearerToken))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión inválida"));
    }

    private static String extractBearer(String header) {
        if (header == null || header.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión inválida");
        }
        String value = header.trim();
        if (value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            value = value.substring(7).trim();
        }
        if (value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión inválida");
        }
        return value;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }

    private static String requireEmail(String value) {
        String correo = requireText(value, "El correo es obligatorio").toLowerCase();
        if (!correo.contains("@") || correo.length() < 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Correo inválido");
        }
        return correo;
    }

    private static String requirePassword(String value) {
        if (value == null || value.length() < 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contraseña debe tener al menos 6 caracteres");
        }
        return value;
    }

    private static String requireDni(String value) {
        String dni = requireText(value, "El DNI es obligatorio");
        if (!dni.matches("\\d{8}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El DNI debe tener 8 dígitos");
        }
        return dni;
    }

    private static String requireTotpCode(String value) {
        String code = requireText(value, "El código es obligatorio").replace(" ", "");
        if (!code.matches("\\d{6}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El código debe tener 6 dígitos");
        }
        return code;
    }
}
