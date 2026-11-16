package com.uitopic.rumbo.planning.infrastructure.files;

import com.uitopic.rumbo.planning.domain.exceptions.InvalidFloorPlanFile;
import com.uitopic.rumbo.planning.domain.model.valueobjects.FloorPlanFile.Format;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;

import static com.uitopic.rumbo.planning.domain.exceptions.InvalidFloorPlanFile.Reason.INVALID_CONTENT;
import static com.uitopic.rumbo.planning.domain.model.valueobjects.FloorPlanFile.Format.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OriginalFloorPlanContentValidatorTest {
  private final OriginalFloorPlanContentValidator validator = new OriginalFloorPlanContentValidator();

  @Test
  void acceptsWellFormedSvgAndRejectsMalformedXmlAndExternalEntities() {
    validator.validate(SVG, utf8("<svg xmlns=\"http://www.w3.org/2000/svg\"><rect width=\"20\"/></svg>"));
    reject(SVG, utf8("<svg xmlns=\"http://www.w3.org/2000/svg\"><rect></svg>"));
    reject(SVG, utf8("<html><svg/></html>"));
    reject(SVG, utf8("""
      <!DOCTYPE svg [<!ENTITY ext SYSTEM "file:///invalid-rumbo-test.txt">]>
      <svg xmlns="http://www.w3.org/2000/svg">&ext;</svg>
      """));
  }

  @Test
  void acceptsARealPdfAndRejectsAHeaderImitationOrMultiplePages() throws IOException {
    validator.validate(PDF, pdf(1));
    reject(PDF, utf8("%PDF-1.7\nnot a PDF\n%%EOF"));
    reject(PDF, pdf(2));
  }

  @Test
  void readsRasterContentAndRejectsTruncationFormatMismatchAndOversizedDimensions() throws IOException {
    byte[] png = image("png", 16, 16);
    validator.validate(PNG, png);
    validator.validate(JPEG, image("jpeg", 16, 16));
    reject(PNG, Arrays.copyOf(png, png.length - 12));
    reject(JPEG, png);
    reject(PNG, image("png", 8193, 1));
  }

  private void reject(Format format, byte[] bytes) {
    assertThatThrownBy(() -> validator.validate(format, bytes))
      .isInstanceOfSatisfying(InvalidFloorPlanFile.class, error -> assertThat(error.reason()).isEqualTo(INVALID_CONTENT));
  }

  private static byte[] utf8(String text) {
    return text.getBytes(StandardCharsets.UTF_8);
  }

  private static byte[] pdf(int pages) throws IOException {
    try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
      for (int i = 0; i < pages; i++) document.addPage(new PDPage());
      document.save(output);
      return output.toByteArray();
    }
  }

  private static byte[] image(String format, int width, int height) throws IOException {
    try (var output = new ByteArrayOutputStream()) {
      var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
      assertThat(ImageIO.write(image, format, output)).isTrue();
      return output.toByteArray();
    }
  }
}
