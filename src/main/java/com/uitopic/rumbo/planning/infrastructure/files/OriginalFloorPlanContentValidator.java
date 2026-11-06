package com.uitopic.rumbo.planning.infrastructure.files;

import com.uitopic.rumbo.planning.application.ports.FloorPlanContentValidator;
import com.uitopic.rumbo.planning.domain.exceptions.InvalidFloorPlanFile;
import com.uitopic.rumbo.planning.domain.model.valueobjects.FloorPlanFile.Format;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
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
  private static final byte[] PDF_HEADER = "%PDF-".getBytes(StandardCharsets.US_ASCII);
  private static final byte[] JPEG_END = {(byte) 0xFF, (byte) 0xD9};
  private static final byte[] PNG_END = {0, 0, 0, 0, 'I', 'E', 'N', 'D', (byte) 0xAE, 0x42, 0x60, (byte) 0x82};

  @Override
  public void validate(Format format, byte[] bytes) {
    try {
      switch (format) {
        case SVG -> checkSvg(bytes);
        case PDF -> checkPdf(bytes);
        case PNG, JPEG -> checkRaster(format, bytes);
      }
    } catch (IOException | XMLStreamException | IllegalArgumentException | IllegalStateException exception) {
      throw invalid("The file is damaged or does not match the declared format");
    }
  }

  private void checkSvg(byte[] bytes) throws XMLStreamException {
    var factory = XMLInputFactory.newDefaultFactory();
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
    factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    var xml = factory.createXMLStreamReader(new ByteArrayInputStream(bytes));
    try {
      boolean root = false;
      int depth = 0;
      while (xml.hasNext()) {
        int event = xml.next();
        if (event == XMLStreamConstants.DTD) throw invalid("SVG documents must not contain a DOCTYPE");
        if (event == XMLStreamConstants.START_ELEMENT) {
          if (++depth > 128) throw invalid("SVG nesting exceeds the supported depth");
          if (!root) {
            if (!"svg".equals(xml.getLocalName()) || !SVG_NAMESPACE.equals(xml.getNamespaceURI())) {
              throw invalid("The document must have an SVG root element");
            }
            root = true;
          }
        } else if (event == XMLStreamConstants.END_ELEMENT) {
          depth--;
        }
      }
      if (!root) throw invalid("The document must have an SVG root element");
    } finally {
      xml.close();
    }
  }

  private void checkPdf(byte[] bytes) throws IOException {
    if (bytes.length < PDF_HEADER.length
        || !Arrays.equals(bytes, 0, PDF_HEADER.length, PDF_HEADER, 0, PDF_HEADER.length)) {
      throw invalid("The document does not have a PDF header");
    }
    try (var source = new RandomAccessReadBuffer(bytes);
         var document = new PDFParser(source).parse(false)) {
      if (document.isEncrypted() || document.getNumberOfPages() != 1) {
        throw invalid("The PDF must contain one page and must not be encrypted");
      }
      document.getPage(0);
    }
  }

  private void checkRaster(Format format, byte[] bytes) throws IOException {
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
        int step = Math.ceilDiv(Math.max(width, height), 1024);
        var parameters = reader.getDefaultReadParam();
        parameters.setSourceSubsampling(step, step, 0, 0);
        reader.read(0, parameters);
        if (damaged.get()) throw invalid("The image contains damaged data");
      } finally {
        reader.dispose();
      }
    }
  }

  private static InvalidFloorPlanFile invalid(String message) {
    return new InvalidFloorPlanFile(INVALID_CONTENT, message);
  }
}
