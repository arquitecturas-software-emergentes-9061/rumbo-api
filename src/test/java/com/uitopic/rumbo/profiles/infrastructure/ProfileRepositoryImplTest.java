package com.uitopic.rumbo.profiles.infrastructure;

import com.uitopic.rumbo.TestcontainersConfiguration;
import com.uitopic.rumbo.profiles.domain.model.aggregates.Profile;
import com.uitopic.rumbo.profiles.domain.repositories.ProfileRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Persistence of profiles against the real Flyway schema (PostgreSQL in Testcontainers). */
@SpringBootTest
@ActiveProfiles("demo")
@Import(TestcontainersConfiguration.class)
@Transactional
class ProfileRepositoryImplTest {
  @Autowired ProfileRepository profiles;
  @Autowired EntityManager entityManager;
  @Autowired JdbcTemplate jdbc;

  @Test
  void savesAndReloadsTheNullableFieldsAsNull() {
    Profile profile = Profile.initialize(UUID.randomUUID(), "Ana Torres", "PATIENT", Instant.now());

    profiles.save(profile);
    entityManager.clear();

    Profile loaded = profiles.findByUserId(profile.userId()).orElseThrow();
    assertThat(loaded.id()).isEqualTo(profile.id());
    assertThat(loaded.fullName()).isEqualTo(profile.fullName());
    assertThat(loaded.role()).isEqualTo(profile.role());
    assertThat(loaded.phoneNumber()).isNull();
    assertThat(loaded.image()).isNull();
    assertThat(loaded.createdAt()).isNotNull();
    assertThat(loaded.updatedAt()).isNotNull();
    Map<String, Object> row = jdbc.queryForMap(
      "select phone_number, image_url, image_storage_key from profiles_user_profiles where user_id = ?",
      profile.userId());
    assertThat(row.values()).containsOnlyNulls();
  }

  @Test
  void rejectsASecondProfileForTheSameUser() {
    UUID userId = UUID.randomUUID();
    profiles.save(Profile.initialize(userId, "Ana Torres", "PATIENT", Instant.now()));

    assertThatThrownBy(() -> profiles.save(Profile.initialize(userId, "Ana Torres", "PATIENT", Instant.now())))
      .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void migrationMatchesTheEntityColumnByColumn() {
    List<Map<String, Object>> columns = jdbc.queryForList("""
      select column_name, data_type, character_maximum_length, is_nullable
      from information_schema.columns
      where table_schema = current_schema() and table_name = 'profiles_user_profiles'
      order by ordinal_position
      """);

    assertThat(columns).hasSize(9);
    assertColumn(columns, "id", "uuid", null, "NO");
    assertColumn(columns, "user_id", "uuid", null, "NO");
    assertColumn(columns, "full_name", "character varying", 255, "NO");
    assertColumn(columns, "role", "character varying", 20, "NO");
    assertColumn(columns, "phone_number", "character varying", 30, "YES");
    assertColumn(columns, "image_url", "character varying", 2048, "YES");
    assertColumn(columns, "image_storage_key", "character varying", 255, "YES");
    assertColumn(columns, "created_at", "timestamp with time zone", null, "NO");
    assertColumn(columns, "updated_at", "timestamp with time zone", null, "NO");
  }

  @Test
  void updatingExistingProfilePreservesItsRowAndIdentity() {
    Profile initial = profiles.save(Profile.initialize(UUID.randomUUID(), "Ana", "PATIENT", Instant.now()));
    Profile updated = profiles.save(initial.updatePersonalData(
        new com.uitopic.rumbo.profiles.domain.model.valueobjects.FullName("Maria"), null, Instant.now()));
    entityManager.clear();
    assertThat(updated.id()).isEqualTo(initial.id());
    assertThat(profiles.findByUserId(initial.userId()).orElseThrow().fullName().value()).isEqualTo("Maria");
    assertThat(jdbc.queryForObject("select count(*) from profiles_user_profiles where user_id = ?",
        Long.class, initial.userId())).isEqualTo(1);
  }

  private static void assertColumn(List<Map<String, Object>> columns, String name, String type, Integer length,
                                   String nullable) {
    Map<String, Object> column = columns.stream()
      .filter(candidate -> name.equals(candidate.get("column_name")))
      .findFirst()
      .orElseThrow(() -> new AssertionError("Missing column " + name));
    assertThat(column.get("data_type")).as(name).isEqualTo(type);
    assertThat(column.get("character_maximum_length")).as(name).isEqualTo(length);
    assertThat(column.get("is_nullable")).as(name).isEqualTo(nullable);
  }
}
