package com.daaradji.budget.auth;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.daaradji.budget.account.AccountService;
import com.daaradji.budget.auth.AuthDtos.AuthResponse;
import com.daaradji.budget.auth.AuthDtos.LoginRequest;
import com.daaradji.budget.auth.AuthDtos.RegisterRequest;
import com.daaradji.budget.auth.AuthDtos.UserResponse;
import com.daaradji.budget.category.CategoryService;
import com.daaradji.budget.common.BusinessException;
import com.daaradji.budget.common.NotFoundException;
import com.daaradji.budget.user.User;
import com.daaradji.budget.user.UserRepository;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final CategoryService categoryService;
    private final AccountService accountService;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, TokenService tokenService,
            CategoryService categoryService, AccountService accountService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.categoryService = categoryService;
        this.accountService = accountService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        User user = createUser(req.fullName(), req.email(), req.password());
        return toAuthResponse(user);
    }

    /** Crée un utilisateur avec ses catégories et son compte par défaut. */
    @Transactional
    public User createUser(String fullName, String email, String rawPassword) {
        String normalizedEmail = email.trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new BusinessException("Un compte existe déjà avec cet email");
        }
        User user = new User();
        user.setFullName(fullName.trim());
        user.setEmail(normalizedEmail);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user = users.save(user);
        categoryService.createDefaults(user.getId());
        accountService.createDefault(user.getId());
        return user;
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        User user = users.findByEmailIgnoreCase(req.email().trim())
                .filter(u -> passwordEncoder.matches(req.password(), u.getPassword()))
                .orElseThrow(() -> new BadCredentialsException("bad credentials"));
        return toAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse me(Long userId) {
        return users.findById(userId).map(AuthService::toUserResponse)
                .orElseThrow(() -> new NotFoundException("Utilisateur introuvable"));
    }

    private AuthResponse toAuthResponse(User user) {
        return new AuthResponse(tokenService.generate(user), tokenService.expiresInSeconds(), toUserResponse(user));
    }

    private static UserResponse toUserResponse(User u) {
        return new UserResponse(u.getId(), u.getFullName(), u.getEmail(), u.getCurrency());
    }
}
