package com.uitopic.rumbo.planning.infrastructure.persistence.jpa;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.uitopic.rumbo.planning.application.ports.IndoorMapStore;
import com.uitopic.rumbo.planning.interfaces.rest.IndoorMapsController;
import org.springframework.context.ApplicationContext;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Ensures the PostgreSQL profile boots with Planning persistence enabled. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("postgres")
@EnabledIfEnvironmentVariable(named = "RUMBO_TEST_DB_URL", matches = ".+")
class PlanningPostgresApplicationIntegrationTest {
  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry properties) {
    properties.add("spring.datasource.url", () -> System.getenv("RUMBO_TEST_DB_URL"));
    properties.add("spring.datasource.username", () -> System.getenv("RUMBO_TEST_DB_USER"));
    properties.add("spring.datasource.password", () -> System.getenv("RUMBO_TEST_DB_PASSWORD"));
  }

  @Autowired IndoorMapStore store;
  @Autowired ApplicationContext context;

  @Test
  void startsWithJpaIndoorMapStore() {
    assertNotNull(store);
  }

  @Test
  void publicationEndpointRequiresExplicitEnablement() {
    assertThrows(NoSuchBeanDefinitionException.class,
        () -> context.getBean(IndoorMapsController.class));
  }
}
