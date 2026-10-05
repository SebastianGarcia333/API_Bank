package com.bankapi.service;

import com.bankapi.domain.User;
import com.bankapi.dto.LoginRequest;
import com.bankapi.dto.RegisterRequest;
import com.bankapi.exception.BankException;
import com.bankapi.repository.UserRepository;
import com.bankapi.security.JwtService;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final String EMAIL_TAKEN = "El email ya está registrado";

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    /** Se compara cuando el email no existe, para igualar los tiempos de respuesta. */
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.dummyHash = encoder.encode("no-existe");
    }

    public User register(RegisterRequest request) {
        String email = normalize(request.email());
        if (users.existsByEmail(email)) {
            throw BankException.conflict(EMAIL_TAKEN);
        }
        User user = new User(email, request.fullName().trim(), encoder.encode(request.password()));
        try {
            return users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw BankException.conflict(EMAIL_TAKEN);
        }
    }

    public String login(LoginRequest request) {
        User user = users.findByEmail(normalize(request.email())).orElse(null);
        String hash = user != null ? user.getPasswordHash() : dummyHash;
        boolean valid = encoder.matches(request.password(), hash);
        if (user == null || !valid) {
            throw BankException.unauthorized("Email o contraseña incorrectos");
        }
        return jwt.createToken(user.getId());
    }

    public User getUser(Long id) {
        return users.findById(id).orElseThrow(() -> BankException.unauthorized("Credenciales inválidas"));
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
