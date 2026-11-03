package com.uitopic.rumbo.iam.domain;

import com.uitopic.rumbo.iam.domain.model.valueobjects.EmailAddress;
import com.uitopic.rumbo.iam.domain.model.valueobjects.PasswordHash;
import com.uitopic.rumbo.iam.domain.model.valueobjects.PasswordPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValueObjectsTest {
  @Test
  void emailIsNormalized() {
    assertThat(new EmailAddress("  Ana.Torres@Clinica.PE ").value()).isEqualTo("ana.torres@clinica.pe");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "ana", "ana@", "@clinica.pe", "ana@clinica", "ana torres@clinica.pe"})
  void malformedEmailsAreRejected(String email) {
    assertThatThrownBy(() -> new EmailAddress(email)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void theDefaultPolicyMatchesTheSignUpForm() {
    PasswordPolicy policy = PasswordPolicy.DEFAULT;

    assertThat(policy.isSatisfiedBy("Rumbo2026")).isTrue();
    assertThat(policy.isSatisfiedBy("Rumbo26")).as("too short").isFalse();
    assertThat(policy.isSatisfiedBy("rumbo2026")).as("no uppercase").isFalse();
    assertThat(policy.isSatisfiedBy("RumboRumbo")).as("no digit").isFalse();
    assertThat(policy.isSatisfiedBy("R1" + "a".repeat(PasswordPolicy.MAX_LENGTH))).as("over BCrypt limit").isFalse();
    assertThat(policy.isSatisfiedBy(null)).isFalse();
  }

  @Test
  void aPasswordHashNeverPrintsItsValue() {
    assertThat(new PasswordHash("$2a$10$secret").toString()).doesNotContain("secret");
  }
}
