package com.operaciones.backend.auth;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.operaciones.backend.auth.AuthTokenStore.Session;
import com.operaciones.backend.auth.dto.CreateManagedUserRequest;
import com.operaciones.backend.auth.dto.CreateManagedUserResponse;
import com.operaciones.backend.auth.dto.ManagedUserResponse;

@Service
public class UserAdminService {

    private static final long INVITE_TTL_SECONDS = 60L * 60L * 72L;
    private static final String TEMP_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UsuarioEmpresaRepository empresaRepository;
    private final UsuarioDniRepository dniRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenStore tokenStore;

    public UserAdminService(
            UsuarioEmpresaRepository empresaRepository,
            UsuarioDniRepository dniRepository,
            PasswordEncoder passwordEncoder,
            AuthTokenStore tokenStore) {
        this.empresaRepository = empresaRepository;
        this.dniRepository = dniRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenStore = tokenStore;
    }

    public List<ManagedUserResponse> listUsers(String bearerToken) {
        requireStaff(bearerToken);
        List<ManagedUserResponse> rows = new ArrayList<>();
        for (UsuarioEmpresa usuario : empresaRepository.findAll()) {
            rows.add(toEmpresaRow(usuario));
        }
        for (UsuarioDni usuario : dniRepository.findAll()) {
            rows.add(toDniRow(usuario));
        }
        rows.sort(Comparator.comparing(ManagedUserResponse::nombre, String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    public CreateManagedUserResponse createUser(String bearerToken, CreateManagedUserRequest request) {
        Session actor = requireStaff(bearerToken);
        UsuarioEmpresa admin = empresaRepository.findById(actor.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión inválida"));

        String kind = requireText(request.kind(), "Indica el tipo de usuario").toLowerCase(Locale.ROOT);
        String nombre = requireText(request.nombre(), "El nombre es obligatorio");
        String tempPassword = generateTempPassword();
        String inviteToken = newInviteToken();
        Instant inviteExpira = Instant.now().plusSeconds(INVITE_TTL_SECONDS);

        if ("empresa".equals(kind)) {
            String correo = requireEmail(request.correo());
            String role;
            try {
                role = StaffRole.normalizeStaff(request.role() == null || request.role().isBlank()
                        ? StaffRole.OPERACIONES
                        : request.role());
            } catch (IllegalArgumentException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rol no válido");
            }
            if (StaffRole.isSuperAdmin(role) && !StaffRole.isSuperAdmin(admin.getRol())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo un Superadmin puede crear otro Superadmin");
            }
            if (empresaRepository.existsByCorreoIgnoreCase(correo)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "El correo ya está registrado");
            }

            UsuarioEmpresa usuario = new UsuarioEmpresa();
            usuario.setNombre(nombre);
            usuario.setApodo(nombre);
            usuario.setCorreo(correo);
            usuario.setRol(role);
            usuario.setPasswordHash(passwordEncoder.encode(tempPassword));
            usuario.setMustChangePassword(true);
            usuario.setActivo(true);
            usuario.setTotpEnabled(false);
            usuario.setTotpSecret(TotpUtil.generateSecret());
            usuario.setInviteToken(inviteToken);
            usuario.setInviteExpira(inviteExpira);
            empresaRepository.save(usuario);

            ManagedUserResponse row = toEmpresaRow(usuario);
            return new CreateManagedUserResponse(
                    row,
                    tempPassword,
                    row.invitePath(),
                    "Usuario de personal creado. Comparte la clave temporal o el link de invitación.");
        }

        if ("dni".equals(kind)) {
            String dni = requireDni(request.dni());
            if (dniRepository.existsByDni(dni)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "El DNI ya está registrado");
            }
            UsuarioDni usuario = new UsuarioDni();
            usuario.setNombres(nombre);
            usuario.setApodo(nombre);
            usuario.setDni(dni);
            usuario.setPasswordHash(passwordEncoder.encode(tempPassword));
            usuario.setMustChangePassword(true);
            usuario.setActivo(true);
            usuario.setInviteToken(inviteToken);
            usuario.setInviteExpira(inviteExpira);
            dniRepository.save(usuario);

            ManagedUserResponse row = toDniRow(usuario);
            return new CreateManagedUserResponse(
                    row,
                    tempPassword,
                    row.invitePath(),
                    "Cliente creado. Comparte la clave temporal o el link de invitación.");
        }

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de usuario no válido");
    }

    public CreateManagedUserResponse refreshInvite(String bearerToken, String kind, Long id) {
        requireStaff(bearerToken);
        String inviteToken = newInviteToken();
        Instant inviteExpira = Instant.now().plusSeconds(INVITE_TTL_SECONDS);

        if ("empresa".equalsIgnoreCase(kind)) {
            UsuarioEmpresa usuario = empresaRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
            usuario.setInviteToken(inviteToken);
            usuario.setInviteExpira(inviteExpira);
            usuario.setMustChangePassword(true);
            empresaRepository.save(usuario);
            ManagedUserResponse row = toEmpresaRow(usuario);
            return new CreateManagedUserResponse(row, null, row.invitePath(), "Link de invitación renovado");
        }

        if ("dni".equalsIgnoreCase(kind)) {
            UsuarioDni usuario = dniRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
            usuario.setInviteToken(inviteToken);
            usuario.setInviteExpira(inviteExpira);
            usuario.setMustChangePassword(true);
            dniRepository.save(usuario);
            ManagedUserResponse row = toDniRow(usuario);
            return new CreateManagedUserResponse(row, null, row.invitePath(), "Link de invitación renovado");
        }

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de usuario no válido");
    }

    public record UserPhoto(byte[] bytes, String contentType) {
    }

    public UserPhoto getUserPhoto(String bearerToken, String kind, Long id) {
        requireStaff(bearerToken);
        if ("empresa".equalsIgnoreCase(kind)) {
            UsuarioEmpresa usuario = empresaRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
            if (usuario.getFoto() == null || usuario.getFoto().length == 0) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sin foto");
            }
            String type = usuario.getFotoContentType() == null || usuario.getFotoContentType().isBlank()
                    ? "image/jpeg"
                    : usuario.getFotoContentType();
            return new UserPhoto(usuario.getFoto(), type);
        }
        UsuarioDni usuario = dniRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        if (usuario.getFoto() == null || usuario.getFoto().length == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sin foto");
        }
        String type = usuario.getFotoContentType() == null || usuario.getFotoContentType().isBlank()
                ? "image/jpeg"
                : usuario.getFotoContentType();
        return new UserPhoto(usuario.getFoto(), type);
    }

    private ManagedUserResponse toEmpresaRow(UsuarioEmpresa usuario) {
        boolean hasPhoto = usuario.getFoto() != null && usuario.getFoto().length > 0;
        boolean inviteActive = isInviteActive(usuario.getInviteToken(), usuario.getInviteExpira());
        String roleCode = usuario.getRol() == null || usuario.getRol().isBlank()
                ? StaffRole.OPERACIONES
                : usuario.getRol();
        return new ManagedUserResponse(
                usuario.getId(),
                "empresa",
                usuario.getNombre(),
                blank(usuario.getApodo(), usuario.getNombre()),
                usuario.getCorreo(),
                StaffRole.label(roleCode),
                roleCode,
                usuario.isActivo(),
                usuario.isMustChangePassword(),
                usuario.isTotpEnabled(),
                hasPhoto,
                hasPhoto ? "/api/admin/users/empresa/" + usuario.getId() + "/photo" : null,
                inviteActive,
                inviteActive ? "/invitar/" + usuario.getInviteToken() : null,
                inviteActive ? usuario.getInviteExpira().toString() : null);
    }

    private ManagedUserResponse toDniRow(UsuarioDni usuario) {
        boolean hasPhoto = usuario.getFoto() != null && usuario.getFoto().length > 0;
        boolean inviteActive = isInviteActive(usuario.getInviteToken(), usuario.getInviteExpira());
        return new ManagedUserResponse(
                usuario.getId(),
                "dni",
                usuario.getNombres(),
                blank(usuario.getApodo(), usuario.getNombres()),
                usuario.getDni(),
                "Cliente",
                StaffRole.CLIENTE,
                usuario.isActivo(),
                usuario.isMustChangePassword(),
                false,
                hasPhoto,
                hasPhoto ? "/api/admin/users/dni/" + usuario.getId() + "/photo" : null,
                inviteActive,
                inviteActive ? "/invitar/" + usuario.getInviteToken() : null,
                inviteActive ? usuario.getInviteExpira().toString() : null);
    }

    public ManagedUserResponse setActive(String bearerToken, String kind, Long id, boolean active) {
        Session actor = requireStaff(bearerToken);
        if ("empresa".equalsIgnoreCase(kind)) {
            UsuarioEmpresa usuario = empresaRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
            if (!active && actor.userId().equals(usuario.getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No puedes desactivar tu propia cuenta");
            }
            usuario.setActivo(active);
            empresaRepository.save(usuario);
            return toEmpresaRow(usuario);
        }
        if ("dni".equalsIgnoreCase(kind)) {
            UsuarioDni usuario = dniRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
            usuario.setActivo(active);
            dniRepository.save(usuario);
            return toDniRow(usuario);
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de usuario no válido");
    }

    private Session requireStaff(String bearerToken) {
        Session session = tokenStore.findSession(extractBearer(bearerToken))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión inválida"));
        if (!"empresa".equals(session.kind())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo personal de FinanTech S.A.");
        }
        return session;
    }

    private static boolean isInviteActive(String token, Instant expires) {
        return token != null && !token.isBlank() && expires != null && expires.isAfter(Instant.now());
    }

    private static String blank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String newInviteToken() {
        return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private static String generateTempPassword() {
        StringBuilder out = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            out.append(TEMP_ALPHABET.charAt(RANDOM.nextInt(TEMP_ALPHABET.length())));
        }
        return out.toString();
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }

    private static String requireEmail(String value) {
        String correo = requireText(value, "El correo es obligatorio").toLowerCase(Locale.ROOT);
        if (!correo.contains("@") || correo.length() < 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Correo no válido");
        }
        return correo;
    }

    private static String requireDni(String value) {
        String dni = requireText(value, "El DNI es obligatorio");
        if (!dni.matches("\\d{8}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El DNI debe tener 8 dígitos");
        }
        return dni;
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
}
