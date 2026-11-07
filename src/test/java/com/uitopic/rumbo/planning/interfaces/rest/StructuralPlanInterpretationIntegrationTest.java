package com.uitopic.rumbo.planning.interfaces.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uitopic.rumbo.TestcontainersConfiguration;
import com.uitopic.rumbo.planning.application.internal.models.SvgPlanPreview.CandidateLink;
import com.uitopic.rumbo.planning.application.internal.models.SvgPlanPreview.CandidateNode;
import com.uitopic.rumbo.planning.application.internal.models.SvgPlanPreview.Door;
import com.uitopic.rumbo.planning.application.internal.models.SvgPlanPreview.Topology;
import com.uitopic.rumbo.planning.application.internal.models.SvgPlanPreview.Zone;
import com.uitopic.rumbo.planning.application.internal.outboundservices.StructuralPlanInterpreterPort;
import com.uitopic.rumbo.planning.domain.model.valueobjects.FloorPlanFile.Format;
import com.uitopic.rumbo.planning.domain.model.valueobjects.OriginalPlanBounds;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The interpretation endpoint decides who reads a stored original: an SVG with annotated zones is read exactly, and
 * anything else goes to the structural mapper. The mapper itself is replaced by a double; its HTTP contract and its
 * scene conversion are covered next to the adapter.
 */
@SpringBootTest
@ActiveProfiles("postgres")
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class StructuralPlanInterpretationIntegrationTest {
  private static final String PASSWORD = "PlanningTest2026";
  private static final Topology ONE_ROOM = new Topology(
    List.of(new Zone("s1", "Cardiologia", "ROOM", 10, 10, 60, 40)),
    List.of(new Door("o1", 40, 50, 60, 50)),
    List.of(new CandidateNode("s1", "ROOM", "Cardiologia", 40, 30)),
    List.<CandidateLink>of(), List.of(), List.of("Propuesta automática no publicada"));
  private static final Topology NOTHING = new Topology(List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
  private static final String ANNOTATED_SVG = """
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 100">
      <rect data-rumbo-role="room" x="0" y="0" width="80" height="40"/>
      <rect data-rumbo-role="corridor" x="0" y="40" width="200" height="20"/>
      <line data-rumbo-role="door" x1="30" y1="40" x2="50" y2="40"/>
      <text x="25" y="20">Cardiologia</text>
    </svg>""";
  private static final String PLAIN_SVG = """
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 100">
      <line x1="0" y1="50" x2="120" y2="50"/><line x1="140" y1="50" x2="200" y2="50"/>
    </svg>""";

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;
  @MockitoBean StructuralPlanInterpreterPort mapper;

  @Test
  void aPngIsReadByTheMapperAndTheScaleStaysProvisionalUntilItIsCalibrated() throws Exception {
    when(mapper.propose(any(), eq(Format.PNG), any(), any())).thenReturn(Optional.of(ONE_ROOM));
    Plan plan = plan("plano.png", "image/png", png(200, 100));

    interpret(plan).andExpect(status().isOk()).andExpect(jsonPath("$.processing").value("SVG_CANDIDATES"))
      .andExpect(jsonPath("$.scaleStatus").value("PROVISIONAL")).andExpect(jsonPath("$.metersPerUnit").doesNotExist())
      .andExpect(jsonPath("$.proposal.zones[0].name").value("Cardiologia"))
      .andExpect(jsonPath("$.proposal.doors[0].id").value("o1"));
    ArgumentCaptor<OriginalPlanBounds> bounds = ArgumentCaptor.forClass(OriginalPlanBounds.class);
    verify(mapper).propose(any(), eq(Format.PNG), bounds.capture(), eq(null));
    assertThat(bounds.getValue()).isEqualTo(new OriginalPlanBounds(200, 100));
  }

  @Test
  void aCalibratedPlanReachesTheMapperWithItsScaleAndComesBackConfirmed() throws Exception {
    when(mapper.propose(any(), eq(Format.PNG), any(), any())).thenReturn(Optional.of(ONE_ROOM));
    Plan plan = plan("plano.png", "image/png", png(200, 100));
    mvc.perform(put("/api/v1/sites/" + plan.site + "/plan-calibrations/" + plan.version)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + plan.token).contentType(MediaType.APPLICATION_JSON)
        .content(("{\"floorId\":\"%s\",\"unit\":\"METERS\",\"measuredDistance\":5,\"startX\":10,\"startY\":10,"
          + "\"endX\":110,\"endY\":10}").formatted(plan.floor)))
      .andExpect(status().isOk());

    interpret(plan).andExpect(jsonPath("$.scaleStatus").value("CONFIRMED"))
      .andExpect(jsonPath("$.metersPerUnit").value(0.05));
    verify(mapper).propose(any(), eq(Format.PNG), any(), eq(0.05));
  }

  @Test
  void whenTheMapperDoesNotAnswerTheOriginalStaysAPreview() throws Exception {
    when(mapper.propose(any(), any(), any(), any())).thenReturn(Optional.empty());

    interpret(plan("plano.png", "image/png", png(200, 100))).andExpect(status().isOk())
      .andExpect(jsonPath("$.processing").value("PREVIEW_ONLY")).andExpect(jsonPath("$.proposal").doesNotExist());
  }

  @Test
  void aValidSvgOutsideTheAnnotationParsersSubsetStillReachesTheStructuralMapper() throws Exception {
    when(mapper.propose(any(), eq(Format.SVG), any(), any())).thenReturn(Optional.of(ONE_ROOM));
    String svg = """
      <svg xmlns="http://www.w3.org/2000/svg" width="200" height="100">
        <rect width="100%" height="100%" fill="white"/>
        <path d="M 10 10 L 190 10 L 190 90 L 10 90 Z" fill="none" stroke="black" stroke-width="5"/>
      </svg>""";
    interpret(plan("plano.svg", "image/svg+xml", svg.getBytes(StandardCharsets.UTF_8)))
      .andExpect(status().isOk()).andExpect(jsonPath("$.proposal.zones[0].name").value("Cardiologia"));
    verify(mapper).propose(any(), eq(Format.SVG), any(), eq(null));
  }

  @Test
  void aSceneWithoutAnyRecognizedSpaceIsNotPresentedAsAProposal() throws Exception {
    when(mapper.propose(any(), any(), any(), any())).thenReturn(Optional.of(NOTHING));

    interpret(plan("plano.png", "image/png", png(200, 100))).andExpect(jsonPath("$.processing").value("PREVIEW_ONLY"));
  }

  @Test
  void anSvgWithAnnotatedZonesIsReadExactlyWithoutAskingTheMapper() throws Exception {
    interpret(plan("plano.svg", "image/svg+xml", ANNOTATED_SVG.getBytes(StandardCharsets.UTF_8)))
      .andExpect(jsonPath("$.processing").value("SVG_CANDIDATES"))
      .andExpect(jsonPath("$.proposal.zones[0].name").value("Cardiologia"));
    verify(mapper, never()).propose(any(), any(), any(), anyDouble());
  }

  @Test
  void anSvgWithoutAnnotationsFallsBackToTheMapperWhenItFindsSpaces() throws Exception {
    when(mapper.propose(any(), eq(Format.SVG), any(), any())).thenReturn(Optional.of(ONE_ROOM));

    interpret(plan("plano.svg", "image/svg+xml", PLAIN_SVG.getBytes(StandardCharsets.UTF_8)))
      .andExpect(jsonPath("$.processing").value("SVG_CANDIDATES"))
      .andExpect(jsonPath("$.proposal.zones[0].id").value("s1"));
  }

  @Test
  void anSvgWithoutAnnotationsKeepsItsWallCandidatesWhenTheMapperFindsNothing() throws Exception {
    when(mapper.propose(any(), eq(Format.SVG), any(), any())).thenReturn(Optional.empty());

    interpret(plan("plano.svg", "image/svg+xml", PLAIN_SVG.getBytes(StandardCharsets.UTF_8)))
      .andExpect(jsonPath("$.processing").value("SVG_CANDIDATES")).andExpect(jsonPath("$.proposal.zones").isEmpty())
      .andExpect(jsonPath("$.proposal.wallCandidates").isNotEmpty());
  }

  private record Plan(String token, UUID site, UUID floor, String version) {
  }

  private ResultActions interpret(Plan plan) throws Exception {
    return mvc.perform(post("/api/v1/sites/" + plan.site + "/floors/" + plan.floor + "/plans/versions/"
      + plan.version + "/interpretation").header(HttpHeaders.AUTHORIZATION, "Bearer " + plan.token));
  }

  private Plan plan(String name, String mime, byte[] bytes) throws Exception {
    String token = token();
    String siteBody = mvc.perform(post("/api/v1/sites").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Mapper Test Site\"}"))
      .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    UUID site = UUID.fromString(json.readTree(siteBody).path("id").asText());
    UUID floor = UUID.randomUUID();
    jdbc.update("INSERT INTO planning_floor_sites (floor_id, site_id) VALUES (?, ?)", floor, site);
    String uploaded = mvc.perform(multipart("/api/v1/sites/" + site + "/floors/" + floor + "/plans")
        .file(new MockMultipartFile("file", name, mime, bytes)).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
      .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    JsonNode version = json.readTree(uploaded);
    return new Plan(token, site, floor, version.path("id").asText());
  }

  private String token() throws Exception {
    String email = "structural-" + UUID.randomUUID() + "@example.test";
    mvc.perform(post("/api/v1/auth/sign-up").contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD, "role", "CLINIC_ADMIN",
          "fullName", "Plan Test", "clinicName", "Test Clinic", "acceptedTerms", true))))
      .andExpect(status().isCreated());
    String session = mvc.perform(post("/api/v1/auth/sign-in").contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
      .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    return json.readTree(session).path("accessToken").asText();
  }

  private static byte[] png(int width, int height) throws Exception {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    var graphics = image.createGraphics();
    graphics.setColor(Color.WHITE);
    graphics.fillRect(0, 0, width, height);
    graphics.setColor(Color.BLACK);
    graphics.drawLine(10, height / 2, width - 10, height / 2);
    graphics.dispose();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(image, "png", out);
    return out.toByteArray();
  }
}
