package com.uitopic.rumbo.planning.infrastructure.mapper;

import com.uitopic.rumbo.planning.domain.model.valueobjects.FloorPlanFile.Format;
import com.uitopic.rumbo.planning.domain.model.valueobjects.OriginalPlanBounds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class StructuralMapperPlanInterpreterTest {
  private static final OriginalPlanBounds BOUNDS = new OriginalPlanBounds(642, 516);

  private MockRestServiceServer server;
  private StructuralMapperPlanInterpreter interpreter;
  private RestClient.Builder builderForMl;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl("http://mapper.test");
    server = MockRestServiceServer.bindTo(builder).build();
    interpreter = new StructuralMapperPlanInterpreter(builder.build(), false);
    builderForMl = builder;
  }

  private static String scene() throws IOException {
    try (InputStream in = StructuralMapperPlanInterpreterTest.class.getResourceAsStream("/mapper/clinic-scene.json")) {
      return new String(in.readAllBytes());
    }
  }

  @Test
  void sendsTheOriginalAsAMultipartFileAndTurnsTheSceneIntoAProposal() throws IOException {
    server.expect(requestTo("http://mapper.test/analyze")).andExpect(method(HttpMethod.POST))
      .andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
      .andExpect(content().string(containsString("filename=\"plan.png\"")))
      .andExpect(content().string(containsString("image/png")))
      .andExpect(content().string(containsString("name=\"use_ml\"")))
      .andRespond(withSuccess(scene(), MediaType.APPLICATION_JSON));

    var proposal = interpreter.propose(new byte[]{1, 2, 3}, Format.PNG, BOUNDS, null);

    assertThat(proposal).isPresent();
    assertThat(proposal.get().zones()).hasSize(2);
    server.verify();
  }

  @Test
  void aCalibratedScaleTravelsInThePixelsTheMapperWillUse() throws IOException {
    server.expect(requestTo("http://mapper.test/analyze"))
      .andExpect(content().string(containsString("name=\"pixels_per_meter\"")))
      .andExpect(content().string(containsString("20.0")))
      .andRespond(withSuccess(scene(), MediaType.APPLICATION_JSON));

    assertThat(interpreter.propose(new byte[]{1}, Format.PNG, BOUNDS, 0.05)).isPresent();
    server.verify();
  }

  @Test
  void withoutCalibrationNoScaleIsSentSoTheMapperEstimatesItsOwn() throws IOException {
    server.expect(requestTo("http://mapper.test/analyze"))
      .andExpect(content().string(not(containsString("pixels_per_meter"))))
      .andRespond(withSuccess(scene(), MediaType.APPLICATION_JSON));

    assertThat(interpreter.propose(new byte[]{1}, Format.JPEG, BOUNDS, null)).isPresent();
    server.verify();
  }

  @Test
  void neuralPerceptionIsOnlyRequestedWhenConfigured() throws IOException {
    server.expect(requestTo("http://mapper.test/analyze"))
      .andExpect(content().string(org.hamcrest.Matchers.matchesPattern("(?s).*name=\"use_ml\".*?\\r\\n\\r\\nfalse.*")))
      .andRespond(withSuccess(scene(), MediaType.APPLICATION_JSON));
    assertThat(interpreter.propose(new byte[]{1}, Format.PNG, BOUNDS, null)).isPresent();
    server.verify();

    server.reset();
    server.expect(requestTo("http://mapper.test/analyze"))
      .andExpect(content().string(org.hamcrest.Matchers.matchesPattern("(?s).*name=\"use_ml\".*?\\r\\n\\r\\ntrue.*")))
      .andRespond(withSuccess(scene(), MediaType.APPLICATION_JSON));
    assertThat(new StructuralMapperPlanInterpreter(builderForMl.build(), true)
      .propose(new byte[]{1}, Format.PNG, BOUNDS, null)).isPresent();
    server.verify();
  }

  @Test
  void aFailingServiceLeavesThePlanAsAPreview() {
    server.expect(requestTo("http://mapper.test/analyze")).andRespond(withStatus(
      org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE));
    assertThat(interpreter.propose(new byte[]{1}, Format.PNG, BOUNDS, null)).isEmpty();
  }

  @Test
  void aFileTheMapperCannotDecodeLeavesThePlanAsAPreview() {
    server.expect(requestTo("http://mapper.test/analyze")).andRespond(withStatus(
      org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY).contentType(MediaType.APPLICATION_JSON)
      .body("{\"detail\":\"No se pudo decodificar el plano\"}"));
    assertThat(interpreter.propose(new byte[]{1}, Format.PNG, BOUNDS, null)).isEmpty();
  }

  @Test
  void aSceneWithoutImageSizeLeavesThePlanAsAPreview() {
    server.expect(requestTo("http://mapper.test/analyze"))
      .andRespond(withSuccess("{\"spaces\":[]}", MediaType.APPLICATION_JSON));
    assertThat(interpreter.propose(new byte[]{1}, Format.PNG, BOUNDS, null)).isEmpty();
  }

  @Test
  void theScaleHintAccountsForReducedRastersAndRenderedPdfs() {
    assertThat(StructuralMapperPlanInterpreter.pixelsPerMeter(Format.PNG, new OriginalPlanBounds(2000, 1000), 0.1))
      .isEqualTo(10.0);
    assertThat(StructuralMapperPlanInterpreter.pixelsPerMeter(Format.PNG, new OriginalPlanBounds(8192, 4096), 0.1))
      .isEqualTo(5.0);
    assertThat(StructuralMapperPlanInterpreter.pixelsPerMeter(Format.PDF, new OriginalPlanBounds(842, 595), 0.1))
      .isEqualTo(20.0);
    assertThat(StructuralMapperPlanInterpreter.pixelsPerMeter(Format.SVG, BOUNDS, 0.1)).isNull();
    assertThat(StructuralMapperPlanInterpreter.pixelsPerMeter(Format.PNG, BOUNDS, null)).isNull();
    assertThat(StructuralMapperPlanInterpreter.pixelsPerMeter(Format.PNG, BOUNDS, 0.0)).isNull();
  }
}
