package com.uitopic.rumbo.acceptance;

import com.uitopic.rumbo.TestcontainersConfiguration;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** Single Spring context shared by all acceptance scenarios: the real API over HTTP on a random port. */
@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("demo")
@Import(TestcontainersConfiguration.class)
public class CucumberSpringConfiguration {
}
