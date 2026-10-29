package com.operaciones.backend.auth;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.operaciones.backend.auth.UserAdminService.UserPhoto;
import com.operaciones.backend.auth.dto.CreateManagedUserRequest;
import com.operaciones.backend.auth.dto.CreateManagedUserResponse;
import com.operaciones.backend.auth.dto.ManagedUserResponse;
import com.operaciones.backend.auth.dto.SetUserActiveRequest;

@RestController
@RequestMapping("/api/admin/users")
public class UserAdminController {

    private final UserAdminService userAdminService;

    public UserAdminController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    @GetMapping
    public List<ManagedUserResponse> list(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return userAdminService.listUsers(authorization);
    }

    @PostMapping
    public CreateManagedUserResponse create(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody CreateManagedUserRequest request) {
        return userAdminService.createUser(authorization, request);
    }

    @PostMapping("/{kind}/{id}/invite")
    public CreateManagedUserResponse refreshInvite(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String kind,
            @PathVariable Long id) {
        return userAdminService.refreshInvite(authorization, kind, id);
    }

    @PatchMapping("/{kind}/{id}/active")
    public ManagedUserResponse setActive(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String kind,
            @PathVariable Long id,
            @RequestBody SetUserActiveRequest request) {
        if (request.active() == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Indica si el usuario está activo");
        }
        return userAdminService.setActive(authorization, kind, id, request.active());
    }

    @GetMapping("/{kind}/{id}/photo")
    public ResponseEntity<byte[]> photo(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String kind,
            @PathVariable Long id) {
        UserPhoto photo = userAdminService.getUserPhoto(authorization, kind, id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .contentType(MediaType.parseMediaType(photo.contentType()))
                .body(photo.bytes());
    }
}
