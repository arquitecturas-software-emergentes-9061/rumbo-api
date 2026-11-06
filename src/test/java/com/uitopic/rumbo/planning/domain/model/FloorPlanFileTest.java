package com.uitopic.rumbo.planning.domain.model;

import com.uitopic.rumbo.planning.domain.exceptions.InvalidFloorPlanFile;
import com.uitopic.rumbo.planning.domain.model.valueobjects.FloorPlanFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.uitopic.rumbo.planning.domain.exceptions.InvalidFloorPlanFile.Reason.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FloorPlanFileTest {
  @ParameterizedTest
  @CsvSource({
    "Piso 1.PDF,application/pdf",
    "Piso 1.svg,image/svg+xml",
    "Piso 1.PNG,image/png",
    "Piso 1.jpg,image/jpeg",
    "Piso 1.jpeg,image/jpeg"
  })
  void resolvesCanonicalMediaTypeEvenWhenClientDeclaresGenericBinary(String name, String mediaType) {
    var file = FloorPlanFile.describe(name, "application/octet-stream", 100);
    assertThat(file.name()).isEqualTo(name);
    assertThat(file.format().mediaType()).isEqualTo(mediaType);
    assertThat(file.sizeBytes()).isEqualTo(100);
  }

  @Test
  void rejectsUnsafeNamesEmptyFilesAndTheFirstByteBeyondTheLimit() {
    reject("../piso.svg", "image/svg+xml", 20, INVALID_NAME);
    reject("C:\\piso.svg", "image/svg+xml", 20, INVALID_NAME);
    reject("C:piso.svg", "image/svg+xml", 20, INVALID_NAME);
    reject("piso\r\n.svg", "image/svg+xml", 20, INVALID_NAME);
    reject("piso.svg", "image/svg+xml", 0, EMPTY_FILE);
    assertThat(FloorPlanFile.describe("piso.svg", null, FloorPlanFile.MAX_BYTES).sizeBytes())
      .isEqualTo(FloorPlanFile.MAX_BYTES);
    reject("piso.svg", "image/svg+xml", FloorPlanFile.MAX_BYTES + 1L, FILE_TOO_LARGE);
  }

  @Test
  void rejectsUnsupportedExtensionsAndDisagreementWithDeclaredContentType() {
    reject("piso.dwg", "application/octet-stream", 25, UNSUPPORTED_TYPE);
    reject("piso.pdf", "image/png", 25, UNSUPPORTED_TYPE);
  }

  private void reject(String name, String type, long bytes, InvalidFloorPlanFile.Reason reason) {
    assertThatThrownBy(() -> FloorPlanFile.describe(name, type, bytes))
      .isInstanceOfSatisfying(InvalidFloorPlanFile.class, error -> assertThat(error.reason()).isEqualTo(reason));
  }
}
