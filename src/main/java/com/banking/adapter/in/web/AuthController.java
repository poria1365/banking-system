package com.banking.adapter.in.web;

import com.banking.adapter.in.web.dto.ApiResponse;
import com.banking.infrastructure.security.JwtTokenService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// Demo auth endpoint — accepts any non-blank credentials and hands back a JWT.
// Replace with OAuth2 / Keycloak in production. Never do this for real.
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtTokenService jwtTokenService;

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record TokenResponse(String token, String type, long expiresIn) {}

    @PostMapping("/token")
    public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest request) {
        String token = jwtTokenService.generate(request.username());
        return ResponseEntity.ok(ApiResponse.ok(new TokenResponse(token, "Bearer", 86400)));
    }
}
