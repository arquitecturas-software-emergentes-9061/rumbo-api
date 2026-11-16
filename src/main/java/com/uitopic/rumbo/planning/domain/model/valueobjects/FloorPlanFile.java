package com.uitopic.rumbo.planning.domain.model.valueobjects;

import com.uitopic.rumbo.planning.domain.exceptions.InvalidFloorPlanFile;
import java.util.Locale;
import java.util.Objects;

import static com.uitopic.rumbo.planning.domain.exceptions.InvalidFloorPlanFile.Reason.*;

/** Metadata of one original file. The format still has to be checked against the bytes. */
public record FloorPlanFile(String name, Format format, long sizeBytes) {
  public static final int MAX_BYTES = 25 * 1024 * 1024;

  public enum Format {
    PDF("application/pdf"), SVG("image/svg+xml"), PNG("image/png"), JPEG("image/jpeg");

    private final String mediaType;

    Format(String mediaType) {
      this.mediaType = mediaType;
    }

    public String mediaType() {
      return mediaType;
    }

    public static Format fromMediaType(String mediaType) {
      for (Format format : values()) {
        if (format.mediaType.equals(mediaType)) return format;
      }
      throw new IllegalArgumentException("Unknown stored floor-plan media type");
    }
  }

  public FloorPlanFile {
    if (name == null || name.isBlank() || name.length() > 255 || name.indexOf('/') >= 0
        || name.indexOf('\\') >= 0 || name.indexOf(':') >= 0 || name.codePoints().anyMatch(c ->
        Character.isISOControl(c) || Character.getType(c) == Character.FORMAT)) {
      throw new InvalidFloorPlanFile(INVALID_NAME, "Provide a file name without a path or control characters");
    }
    Objects.requireNonNull(format);
    if (sizeBytes <= 0) throw new InvalidFloorPlanFile(EMPTY_FILE, "The plan file is empty");
    if (sizeBytes > MAX_BYTES) throw tooLarge();
  }

  public static FloorPlanFile describe(String name, String contentType, long sizeBytes) {
    if (name == null || name.isBlank()) {
      throw new InvalidFloorPlanFile(INVALID_NAME, "Provide a file name");
    }
    int dot = name.lastIndexOf('.');
    if (dot <= 0) throw unsupportedType();
    Format format = switch (name.substring(dot + 1).toLowerCase(Locale.ROOT)) {
      case "pdf" -> Format.PDF;
      case "svg" -> Format.SVG;
      case "png" -> Format.PNG;
      case "jpg", "jpeg" -> Format.JPEG;
      default -> throw unsupportedType();
    };
    if (contentType != null && !contentType.isBlank()) {
      String declared = contentType.split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
      if (!declared.equals("application/octet-stream") && !declared.equals(format.mediaType())
          && !(format == Format.JPEG && declared.equals("image/jpg"))) {
        throw unsupportedType();
      }
    }
    return new FloorPlanFile(name, format, sizeBytes);
  }

  public static InvalidFloorPlanFile tooLarge() {
    return new InvalidFloorPlanFile(FILE_TOO_LARGE, "The plan file exceeds 25 MiB");
  }

  private static InvalidFloorPlanFile unsupportedType() {
    return new InvalidFloorPlanFile(UNSUPPORTED_TYPE, "Use a PDF, SVG, PNG or JPEG file with a matching media type");
  }
}
