package com.uitopic.rumbo.planning.infrastructure.files;

import com.uitopic.rumbo.planning.application.ports.FloorPlanContentValidator;
import com.uitopic.rumbo.planning.domain.exceptions.InvalidFloorPlanFile;
import com.uitopic.rumbo.planning.domain.model.valueobjects.FloorPlanFile.Format;
import com.uitopic.rumbo.planning.domain.model.valueobjects.OriginalPlanBounds;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.springframework.stereotype.Component;

import static com.uitopic.rumbo.planning.domain.exceptions.InvalidFloorPlanFile.Reason.INVALID_CONTENT;

@Component
public class OriginalFloorPlanContentValidator implements FloorPlanContentValidator {
  private static final int MAX_IMAGE_SIDE = 8192;
  private static final String SVG_NAMESPACE = "http://www.w3.org/2000/svg";
  private static final Pattern SVG_LENGTH = Pattern.compile(
    "^([+]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?)(px|in|cm|mm|pt|pc|q)?$");
  private static final byte[] PDF_HEADER = "%PDF-".getBytes(StandardCharsets.US_ASCII);
  private static final byte[] JPEG_END = {(byte) 0xFF, (byte) 0xD9};
  private static final byte[] PNG_END = {0, 0, 0, 0, 'I', 'E', 'N', 'D', (byte) 0xAE, 0x42, 0x60, (byte) 0x82};

  @Override
  public void validate(Format format, byte[] bytes) {
    try {
      switch (format) {
        case SVG -> svg(bytes, true);
        case PDF -> pdf(bytes, true);
        case PNG, JPEG -> checkRaster(format, bytes);
      }
    } catch (IOException | XMLStreamException | IllegalArgumentException | IllegalStateException exception) {
      throw invalid("The file is damaged or does not match the declared format");
    }
  }

  @Override
  public OriginalPlanBounds bounds(Format format, byte[] bytes) {
    try {
      return switch (format) {
        case SVG -> svg(bytes, false);
        case PDF -> pdf(bytes, false);
        case PNG, JPEG -> raster(format, bytes, false);
      };
    } catch (IOException | XMLStreamException | IllegalArgumentException | IllegalStateException exception) {
      throw invalid("The original's dimensions could not be read");
    }
  }

  private OriginalPlanBounds svg(byte[] bytes, boolean validate) throws XMLStreamException {
    var factory = XMLInputFactory.newDefaultFactory();
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
    factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    var xml = factory.createXMLStreamReader(new ByteArrayInputStream(bytes));
    try {
      boolean rootSeen = false;
      int depth = 0;
      while (xml.hasNext()) {
        int event = xml.next();
        if (event == XMLStreamConstants.DTD) throw invalid("SVG documents must not contain a DOCTYPE");
        if (event == XMLStreamConstants.START_ELEMENT) {
          if (++depth > 128) throw invalid("SVG nesting exceeds the supported depth");
          if (!rootSeen) {
            if (!"svg".equals(xml.getLocalName()) || !SVG_NAMESPACE.equals(xml.getNamespaceURI())) {
              throw invalid("The document must have an SVG root element");
            }
            if (!validate) return svgBounds(xml);
            rootSeen = true;
          }
        } else if (event == XMLStreamConstants.END_ELEMENT) {
          depth--;
        }
      }
      if (!rootSeen) throw invalid("The document must have an SVG root element");
      return null;
    } finally {
      xml.close();
    }
  }

  private static OriginalPlanBounds svgBounds(javax.xml.stream.XMLStreamReader xml) {
    double width = svgLength(xml.getAttributeValue(null, "width"));
    double height = svgLength(xml.getAttributeValue(null, "height"));
    String viewBox = xml.getAttributeValue(null, "viewBox");
    if (viewBox != null && (width == 0 || height == 0)) {
      String[] values = viewBox.strip().split("[,\\s]+");
      if (values.length == 4) {
        double w = svgLength(values[2]), h = svgLength(values[3]);
        if (w > 0 && h > 0) {
          if (width == 0 && height == 0) return new OriginalPlanBounds(w, h);
          if (width == 0) width = height * w / h;
          else height = width * h / w;
        }
      }
    }
    return new OriginalPlanBounds(width > 0 ? width : 300, height > 0 ? height : 150);
  }

  private static double svgLength(String value) {
    if (value == null) return 0;
    var match = SVG_LENGTH.matcher(value.strip());
    if (!match.matches()) return 0;
    double factor = switch (match.group(2) == null ? "px" : match.group(2)) {
      case "in" -> 96;
      case "cm" -> 96 / 2.54;
      case "mm" -> 96 / 25.4;
      case "q" -> 96 / 101.6;
      case "pt" -> 96 / 72.0;
      case "pc" -> 16;
      default -> 1;
    };
    return Double.parseDouble(match.group(1)) * factor;
  }

  private OriginalPlanBounds pdf(byte[] bytes, boolean validate) throws IOException {
    if (bytes.length < PDF_HEADER.length
        || !Arrays.equals(bytes, 0, PDF_HEADER.length, PDF_HEADER, 0, PDF_HEADER.length)) {
      throw invalid("The document does not have a PDF header");
    }
    try (var source = new RandomAccessReadBuffer(bytes);
         var document = new PDFParser(source).parse(false)) {
      if (document.isEncrypted() || document.getNumberOfPages() != 1) {
        throw invalid("The PDF must contain one page and must not be encrypted");
      }
      if (validate) return null;
      var page = document.getPage(0);
      var box = page.getCropBox();
      double width = (double) box.getWidth() * page.getUserUnit();
      double height = (double) box.getHeight() * page.getUserUnit();
      return page.getRotation() % 180 == 0 ? new OriginalPlanBounds(width, height)
        : new OriginalPlanBounds(height, width);
    }
  }

  private void checkRaster(Format format, byte[] bytes) throws IOException {
    raster(format, bytes, true);
  }

  private OriginalPlanBounds raster(Format format, byte[] bytes, boolean decode) throws IOException {
    byte[] end = format == Format.PNG ? PNG_END : JPEG_END;
    if (bytes.length < end.length
        || !Arrays.equals(bytes, bytes.length - end.length, bytes.length, end, 0, end.length)) {
      throw invalid("The image is missing its end marker");
    }
    try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
      var readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext()) throw invalid("The uploaded file is not a readable image");
      var reader = readers.next();
      try {
        if (!format.name().equalsIgnoreCase(reader.getFormatName())) {
          throw invalid("The image format does not match its extension");
        }
        var damaged = new AtomicBoolean();
        reader.addIIOReadWarningListener((source, message) -> damaged.set(true));
        reader.setInput(input, true, true);
        int width = reader.getWidth(0), height = reader.getHeight(0);
        if (width < 1 || height < 1 || width > MAX_IMAGE_SIDE || height > MAX_IMAGE_SIDE) {
          throw invalid("Raster plans must be at most 8192 by 8192 pixels");
        }
        if (decode) {
          int step = Math.ceilDiv(Math.max(width, height), 1024);
          var parameters = reader.getDefaultReadParam();
          parameters.setSourceSubsampling(step, step, 0, 0);
          reader.read(0, parameters);
          if (damaged.get()) throw invalid("The image contains damaged data");
        }
        return new OriginalPlanBounds(width, height);
      } finally {
        reader.dispose();
      }
    }
  }

  private static InvalidFloorPlanFile invalid(String message) {
    return new InvalidFloorPlanFile(INVALID_CONTENT, message);
  }
}
