import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;

/** Standalone PR-134 probe. Run with Java 21 source-file mode. */
public class Pr134RasterProbe {
  private static final int WIDTH = 160;
  private static final int HEIGHT = 120;
  private static final int THRESHOLD = 128;
  private static final int MIN_LENGTH = 18;

  private record Segment(int x1, int y1, int x2, int y2) {
    void draw(Graphics2D graphics) {
      graphics.drawLine(x1, y1, x2, y2);
    }
  }

  private static final List<Segment> WALLS = List.of(
      new Segment(10, 10, 150, 10), new Segment(10, 100, 150, 100),
      new Segment(10, 10, 10, 100), new Segment(150, 10, 150, 100),
      new Segment(80, 10, 80, 42), new Segment(80, 63, 80, 100));

  private record Sample(String name, int gray, boolean legend, double rotation) {}

  public static void main(String[] args) throws IOException {
    var samples = List.of(new Sample("orthogonal", 0, false, 0),
        new Sample("with-legend-line", 0, true, 0),
        new Sample("low-contrast", 170, false, 0),
        new Sample("rotated-12-deg", 0, false, 12));
    System.out.println("format,fixture,expected_wall_segments,detected,exact_axis_matches");
    for (String format : List.of("png", "jpeg")) {
      for (Sample sample : samples) {
        var found = detect(roundTrip(draw(sample), format));
        // A rotated wall cannot be an exact match for this axis-only probe.
        long matches = sample.rotation == 0 ? WALLS.stream().filter(found::contains).count() : 0;
        System.out.printf("%s,%s,%d,%d,%d%n", format, sample.name, WALLS.size(), found.size(), matches);
      }
    }
  }

  private static BufferedImage draw(Sample sample) {
    var image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = image.createGraphics();
    try {
      graphics.setColor(Color.WHITE);
      graphics.fillRect(0, 0, WIDTH, HEIGHT);
      graphics.rotate(Math.toRadians(sample.rotation), WIDTH / 2.0, HEIGHT / 2.0);
      graphics.setColor(new Color(sample.gray, sample.gray, sample.gray));
      WALLS.forEach(wall -> wall.draw(graphics));
      if (sample.legend) new Segment(30, 110, 62, 110).draw(graphics);
    } finally {
      graphics.dispose();
    }
    return image;
  }

  private static BufferedImage roundTrip(BufferedImage image, String format) throws IOException {
    var bytes = new ByteArrayOutputStream();
    if (!ImageIO.write(image, format, bytes)) throw new IOException("Missing " + format + " writer");
    var decoded = ImageIO.read(new ByteArrayInputStream(bytes.toByteArray()));
    if (decoded == null) throw new IOException("Could not decode " + format);
    return decoded;
  }

  private static Set<Segment> detect(BufferedImage image) {
    var segments = new HashSet<Segment>();
    scan(image, true, segments);
    scan(image, false, segments);
    return segments;
  }

  private static void scan(BufferedImage image, boolean horizontal, Set<Segment> segments) {
    int rows = horizontal ? image.getHeight() : image.getWidth();
    int columns = horizontal ? image.getWidth() : image.getHeight();
    for (int row = 0; row < rows; row++) {
      int start = -1;
      for (int column = 0; column <= columns; column++) {
        boolean dark = column < columns
            && (image.getRGB(horizontal ? column : row, horizontal ? row : column) & 255) < THRESHOLD;
        if (dark && start < 0) {
          start = column;
        } else if (!dark && start >= 0) {
          if (column - start >= MIN_LENGTH) {
            segments.add(horizontal ? new Segment(start, row, column - 1, row)
                : new Segment(row, start, row, column - 1));
          }
          start = -1;
        }
      }
    }
  }
}
