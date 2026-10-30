package com.sigmav.sigmavbackend.modules.auth.service;

import com.sigmav.sigmavbackend.modules.academy.entity.Academy;
import com.sigmav.sigmavbackend.modules.academy.repository.AcademyRepository;
import com.sigmav.sigmavbackend.modules.auth.dto.LoginRequest;
import com.sigmav.sigmavbackend.modules.auth.dto.LoginResponse;
import com.sigmav.sigmavbackend.modules.auth.entity.ActiveSession;
import com.sigmav.sigmavbackend.modules.auth.entity.PasswordResetToken;
import com.sigmav.sigmavbackend.modules.auth.repository.ActiveSessionRepository;
import com.sigmav.sigmavbackend.modules.auth.repository.PasswordResetTokenRepository;
import com.sigmav.sigmavbackend.modules.user.entity.User;
import com.sigmav.sigmavbackend.modules.user.repository.UserRepository;
import com.sigmav.sigmavbackend.security.JwtTokenProvider;
import com.sigmav.sigmavbackend.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    // SonarQube Fix: Constante para evitar literales duplicados
    private static final String UNKNOWN_VALUE = "unknown";

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final AcademyRepository academyRepository;
    private final ActiveSessionRepository activeSessionRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate stringRedisTemplate;
    @Transactional
    public LoginResponse login(LoginRequest request, String ipAddress, String userAgent) {
        // Validar subdomain antes de autenticar para dar un mensaje específico
        if (request.subdomain() != null && !request.subdomain().isBlank()) {
            academyRepository.findBySubdomain(request.subdomain())
                    .orElseThrow(() -> new BadCredentialsException("Código de academia no válido"));
        }

        // Encodear como "email::subdomain" para que CustomUserDetailsService pueda resolver el tenant
        String usernameKey = request.email().toLowerCase().trim() + "::" + (request.subdomain() != null ? request.subdomain() : "");

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(usernameKey, request.password())
            );
        } catch (Exception e) {
            throw new BadCredentialsException("Credenciales inválidas");
        }

        if (!(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new BadCredentialsException("Credenciales inválidas");
        }

        // Verificar que la academia esté activa (si no es SUPER_ADMIN)
        if (principal.getAcademyId() != null) {
            Academy academy = academyRepository.findById(principal.getAcademyId())
                    .orElseThrow(() -> new BadCredentialsException("Academia no encontrada"));
            if (!Boolean.TRUE.equals(academy.getIsActive())) {
                throw new BadCredentialsException("La academia no está activa. Contacte al administrador.");
            }
        }

        String token = jwtTokenProvider.generateToken(principal);
        String refreshToken = jwtTokenProvider.generateRefreshToken(principal);

        // Sesión activa única para STUDENT: invalida la anterior y crea la nueva
        if ("STUDENT".equals(principal.getRole())) {
            manageActiveSession(principal.getId(), token, ipAddress, userAgent);
        }
        Map<String, Boolean> features = null;
        if (principal.getAcademyId() != null) {
            Academy academy = academyRepository.findById(principal.getAcademyId())
                    .orElse(null);
            if (academy != null) {
                features = academy.getFeatures();
            }
        }
        List<String> permissions = new ArrayList<>(principal.getPermissions());

        return new LoginResponse(
                token,
                refreshToken,
                principal.getEmail(),
                getUserFullName(principal.getId()),
                principal.getRole(),
                principal.getAcademyId(),
                features,
                permissions
        );
    }

    @Transactional
    public void logout(Long userId) {
        activeSessionRepository.deleteByUserId(userId);
        stringRedisTemplate.delete("student-session:" + userId);
    }

    private void manageActiveSession(Long userId, String token, String ipAddress, String userAgent) {
        // Elimina todas las sesiones previas del usuario (invalida tokens anteriores)
        activeSessionRepository.deleteByUserId(userId);

        Map<String, Object> deviceInfo = parseUserAgent(userAgent);

        ActiveSession session = ActiveSession.builder()
                .userId(userId)
                .sessionToken(token)
                .ipAddress(ipAddress)
                .deviceInfo(deviceInfo)
                .build();

        activeSessionRepository.save(session);
        stringRedisTemplate.opsForValue().set(
            "student-session:" + userId,
            token,
            java.time.Duration.ofMillis(jwtTokenProvider.getExpiration())
        );
    }

    /**
     * Parsea el User-Agent header para extraer información del dispositivo.
     * SonarQube Fix: Complejidad reducida delegando a submétodos.
     */
    private Map<String, Object> parseUserAgent(String userAgent) {
        Map<String, Object> info = new HashMap<>();

        if (userAgent == null || userAgent.isBlank()) {
            info.put("userAgent", UNKNOWN_VALUE);
            info.put("browser", UNKNOWN_VALUE);
            info.put("os", UNKNOWN_VALUE);
            info.put("isMobile", false);
            return info;
        }

        String ua = userAgent.toLowerCase();
        info.put("userAgent", userAgent);
        info.put("browser", detectBrowser(ua));
        info.put("os", detectOS(ua));
        info.put("isMobile", detectMobile(ua));

        return info;
    }

    private String detectBrowser(String ua) {
        if (ua.contains("edg/") || ua.contains("edge/")) return "Edge";
        if (ua.contains("opr/") || ua.contains("opera/")) return "Opera";
        if (ua.contains("chrome/")) return "Chrome";
        if (ua.contains("firefox/")) return "Firefox";
        if (ua.contains("safari/")) return "Safari";
        return "Other";
    }

    private String detectOS(String ua) {
        if (ua.contains("android")) return "Android";
        if (ua.contains("iphone") || ua.contains("ipad")) return "iOS";
        if (ua.contains("windows")) return "Windows";
        if (ua.contains("mac os") || ua.contains("macos")) return "macOS";
        if (ua.contains("linux")) return "Linux";
        return "Other";
    }

    private boolean detectMobile(String ua) {
        return ua.contains("mobile") || ua.contains("android")
                || ua.contains("iphone") || ua.contains("ipad");
    }

    private String getUserFullName(Long userId) {
        return userRepository.findById(userId)
                .map(User::getFullName)
                .orElse("");
    }

    @Transactional
    public void forgotPassword(String email, String subdomain) {
        Academy academy = null;
        if (subdomain != null && !subdomain.isBlank()) {
            academy = academyRepository.findBySubdomain(subdomain.trim()).orElse(null);
        }

        Optional<User> userOpt = (academy != null)
                ? userRepository.findByEmailAndAcademyId(email.toLowerCase().trim(), academy.getId())
                : userRepository.findByEmail(email.toLowerCase().trim());

        // Si el usuario existe, generamos el token y enviamos el email.
        // Si no existe, no revelamos nada para evitar user enumeration.
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            passwordResetTokenRepository.deleteByUserId(user.getId());

            String token = UUID.randomUUID().toString().replace("-", "");
            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .token(token)
                    .userId(user.getId())
                    .expiresAt(LocalDateTime.now().plusHours(1))
                    .build();
            passwordResetTokenRepository.save(resetToken);

            String academyName = (academy != null) ? academy.getName() : "Sigmav";
            emailService.sendPasswordResetEmail(user.getEmail(), token, academyName);
        }
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Token inválido o expirado"));

        if (Boolean.TRUE.equals(resetToken.getUsed()) || resetToken.isExpired()) {
            throw new IllegalArgumentException("Token inválido o expirado");
        }

        User user = userRepository.findById(resetToken.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);

        activeSessionRepository.deleteByUserId(user.getId());
        stringRedisTemplate.delete("student-session:" + user.getId());
    }
}