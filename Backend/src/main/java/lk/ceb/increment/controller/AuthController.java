package lk.ceb.increment.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lk.ceb.increment.dto.request.LoginRequest;
import lk.ceb.increment.dto.request.SignupRequest;
import lk.ceb.increment.dto.response.AuthUserResponse;
import lk.ceb.increment.entity.AdminUser;
import lk.ceb.increment.repository.AdminUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecurityContextRepository securityContextRepository =
        new HttpSessionSecurityContextRepository();

    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf(CsrfToken ignored) {
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/login")
    public AuthUserResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        Authentication authentication = authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken.unauthenticated(
                request.getUsername().trim(), request.getPassword()));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, servletRequest, servletResponse);

        AdminUser admin = adminUserRepository.findByUsernameIgnoreCase(authentication.getName())
            .orElseThrow(() -> new IllegalStateException("Authenticated administrator was not found"));
        return AuthUserResponse.builder()
            .username(admin.getUsername())
            .displayName(admin.getDisplayName())
            .role("ADMIN")
            .build();
    }

    @PostMapping("/signup")
    public ResponseEntity<Void> signup(@Valid @RequestBody SignupRequest request) {
        String username = request.getUsername().trim();
        if (adminUserRepository.findByUsernameIgnoreCase(username).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        AdminUser admin = new AdminUser();
        admin.setUsername(username);
        admin.setDisplayName(request.getDisplayName().trim());
        admin.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        admin.setEnabled(true);
        adminUserRepository.save(admin);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/me")
    public ResponseEntity<AuthUserResponse> me(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        AdminUser admin = adminUserRepository.findByUsernameIgnoreCase(authentication.getName())
            .orElseThrow(() -> new IllegalStateException("Authenticated administrator was not found"));
        return ResponseEntity.ok(AuthUserResponse.builder()
            .username(admin.getUsername())
            .displayName(admin.getDisplayName())
            .role("ADMIN")
            .build());
    }
}
