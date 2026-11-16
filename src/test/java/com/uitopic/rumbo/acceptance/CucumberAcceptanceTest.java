package com.uitopic.rumbo.acceptance;

import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

/**
 * Runs every Gherkin scenario under {@code src/test/resources/features} during {@code mvn verify}.
 * Glue code and reports are configured in {@code junit-platform.properties}.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
class CucumberAcceptanceTest {
}
