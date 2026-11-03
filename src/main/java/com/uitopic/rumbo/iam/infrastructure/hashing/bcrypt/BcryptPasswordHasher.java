package com.uitopic.rumbo.iam.infrastructure.hashing.bcrypt;

import com.uitopic.rumbo.iam.application.ports.PasswordHasher;
import com.uitopic.rumbo.iam.domain.model.valueobjects.PasswordHash;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/** BCrypt adapter of {@link PasswordHasher}. The salt is generated per hash and stored inside it. */
@Component
public class BcryptPasswordHasher implements PasswordHasher {
  private final BCryptPasswordEncoder encoder;

  public BcryptPasswordHasher(@Value("${rumbo.security.bcrypt.strength:10}") int strength) {
    this.encoder = new BCryptPasswordEncoder(strength);
  }

  @Override
  public PasswordHash hash(String rawPassword) {
    return new PasswordHash(encoder.encode(rawPassword));
  }

  @Override
  public boolean matches(String rawPassword, PasswordHash hash) {
    return rawPassword != null && encoder.matches(rawPassword, hash.value());
  }
}
