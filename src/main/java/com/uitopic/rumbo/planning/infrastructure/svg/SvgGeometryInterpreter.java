package com.uitopic.rumbo.planning.infrastructure.svg;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic, read-only SVG geometry extraction. The output is a candidate
 * proposal for human editing, never an automatically published route network.
 * It accepts only untransformed geometry; there is no raster/OCR guesswork.
 */
public final class SvgGeometryInterpreter {
  private static final int MAX_SVG_CHARS = 1_000_000;
  private static final int MAX_SHAPES = 10_000;
  private static final Pattern TOKEN = Pattern.compile("[A-Za-z]|[-+]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][-+]?\\d+)?");

  public record Segment(double x1, double y1, double x2, double y2, String source) {}
  public record Label(double x, double y, String text, String category) {}
  public record ViewBox(double x, double y, double width, double height) {}
  public record Interpretation(ViewBox viewBox, List<Segment> wallCandidates,
                               List<Label> roomLabels, List<String> warnings) {
    public Interpretation {
      wallCandidates = List.copyOf(wallCandidates);
      roomLabels = List.copyOf(roomLabels);
      warnings = List.copyOf(warnings);
    }
  }

  public static final class InvalidSvg extends RuntimeException {
    public InvalidSvg(String message) { super(message); }
  }

  public Interpretation interpret(String source) {
    if (source == null || source.isBlank() || source.length() > MAX_SVG_CHARS) {
      throw new InvalidSvg("SVG vacío o mayor que 1 MB");
    }
    Document doc = readXml(source);
    Element root = doc.getDocumentElement();
    if (root == null || !"svg".equals(localName(root))) throw new InvalidSvg("El documento debe tener una raíz SVG");
    var walls = new ArrayList<Segment>();
    var labels = new ArrayList<Label>();
    var warnings = new ArrayList<String>();
    ViewBox viewBox = parseViewBox(root.getAttribute("viewBox"), warnings);
    NodeList elements = root.getElementsByTagName("*");
    if (elements.getLength() > MAX_SHAPES) throw new InvalidSvg("Demasiados elementos SVG");
    if (root.hasAttribute("transform")) throw new InvalidSvg("Transformaciones SVG no soportadas; exporte coordenadas absolutas");

    for (int i = 0; i < elements.getLength(); i++) {
      Element element = (Element) elements.item(i);
      if (element.hasAttribute("transform"))
        throw new InvalidSvg("Transformaciones SVG no soportadas; exporte coordenadas absolutas");
      String tag = localName(element);
      String explicitRole = element.getAttribute("data-rumbo-role").strip().toLowerCase(Locale.ROOT);
      // A marked doorway is an opening, not a blocking wall. Semantic zones are
      // candidates for room topology, not physical boundary-wall declarations.
      if (("line".equals(tag) && "door".equals(explicitRole))
          || ("rect".equals(tag) && Set.of("room", "corridor", "service").contains(explicitRole))) {
        continue;
      }
      switch (tag) {
        case "line" -> add(walls, number(element, "x1", 0), number(element, "y1", 0),
          number(element, "x2", 0), number(element, "y2", 0), "line");
        case "rect" -> rectangle(element, walls);
        case "polyline" -> points(element.getAttribute("points"), false, walls, "polyline");
        case "polygon" -> points(element.getAttribute("points"), true, walls, "polygon");
        case "path" -> path(element.getAttribute("d"), walls, warnings);
        case "text" -> text(element, labels, warnings);
        case "circle", "ellipse" -> warnings.add("Se omitió una forma curva; requiere revisión manual");
        default -> { /* Metadata and unsupported shapes are not interpreted as walls. */ }
      }
      if (walls.size() > MAX_SHAPES) throw new InvalidSvg("Se excedió el límite de segmentos");
    }
    if (walls.isEmpty()) warnings.add("No se encontraron paredes candidatas con comandos SVG lineales");
    warnings.add("Las líneas son candidatas, no paredes confirmadas: verificar puertas, escala, obstáculos y conectividad");
    return new Interpretation(viewBox, walls, labels, warnings);
  }

  static Document readXml(String source) {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      factory.setXIncludeAware(false);
      factory.setExpandEntityReferences(false);
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      return factory.newDocumentBuilder().parse(new InputSource(new StringReader(source)));
    } catch (Exception e) {
      throw new InvalidSvg("SVG XML inválido o contiene entidades no permitidas");
    }
  }

  static String localName(Element element) {
    String local = element.getLocalName();
    return local == null ? element.getTagName() : local;
  }

  private static ViewBox parseViewBox(String raw, List<String> warnings) {
    if (raw == null || raw.isBlank()) {
      warnings.add("El SVG no declara viewBox; confirmar manualmente las dimensiones de referencia");
      return null;
    }
    double[] values = numbers(raw);
    if (values.length != 4 || values[2] <= 0 || values[3] <= 0) {
      throw new InvalidSvg("viewBox debe tener x, y, ancho y alto positivos");
    }
    return new ViewBox(values[0], values[1], values[2], values[3]);
  }

  static double number(Element e, String key, double fallback) {
    String raw = e.getAttribute(key);
    return raw == null || raw.isBlank() ? fallback : parse(raw);
  }

  private static double parse(String raw) {
    try {
      double value = Double.parseDouble(raw.strip());
      if (!Double.isFinite(value) || Math.abs(value) > 10_000_000)
        throw new NumberFormatException("Non-finite or unreasonable coordinate");
      return value;
    } catch (NumberFormatException bad) {
      throw new InvalidSvg("Coordenada SVG inválida: " + raw);
    }
  }

  private static double[] numbers(String source) {
    Matcher matcher = TOKEN.matcher(source);
    List<Double> values = new ArrayList<>();
    while (matcher.find()) {
      String token = matcher.group();
      if (Character.isLetter(token.charAt(0)) && token.length() == 1)
        throw new InvalidSvg("Se esperaba un número SVG, no un comando");
      values.add(parse(token));
    }
    return values.stream().mapToDouble(Double::doubleValue).toArray();
  }

  private static void add(List<Segment> segments, double x1, double y1, double x2, double y2, String source) {
    if (x1 != x2 || y1 != y2) segments.add(new Segment(x1, y1, x2, y2, source));
  }

  private static void rectangle(Element e, List<Segment> result) {
    double x = number(e, "x", 0), y = number(e, "y", 0);
    double width = number(e, "width", 0), height = number(e, "height", 0);
    if (width <= 0 || height <= 0) throw new InvalidSvg("Rectángulo SVG sin ancho o alto positivo");
    add(result, x, y, x + width, y, "rect");
    add(result, x + width, y, x + width, y + height, "rect");
    add(result, x + width, y + height, x, y + height, "rect");
    add(result, x, y + height, x, y, "rect");
  }

  private static void points(String raw, boolean closed, List<Segment> result, String source) {
    double[] values = numbers(raw);
    if (values.length < 4 || values.length % 2 != 0)
      throw new InvalidSvg("Polyline/polygon requiere pares de coordenadas");
    for (int i = 2; i < values.length; i += 2)
      add(result, values[i - 2], values[i - 1], values[i], values[i + 1], source);
    if (closed) add(result, values[values.length - 2], values[values.length - 1], values[0], values[1], source);
  }

  /** Straight-line path commands only: M/m, L/l, H/h, V/v and Z/z. */
  private static void path(String data, List<Segment> result, List<String> warnings) {
    if (data == null || data.isBlank()) return;
    List<String> tokens = new ArrayList<>();
    Matcher matcher = TOKEN.matcher(data);
    while (matcher.find()) tokens.add(matcher.group());
    List<Segment> candidate = new ArrayList<>();
    int i = 0;
    char cmd = 0;
    double x = 0, y = 0, startX = 0, startY = 0;
    while (i < tokens.size()) {
      String token = tokens.get(i);
      if (token.length() == 1 && Character.isLetter(token.charAt(0))) {
        cmd = token.charAt(0);
        i++;
        if ("Zz".indexOf(cmd) >= 0) {
          add(candidate, x, y, startX, startY, "path");
          x = startX; y = startY; cmd = 0;
        } else if ("MmLlHhVv".indexOf(cmd) < 0) {
          warnings.add("Se omitió un path con curvas o comandos SVG no compatibles: " + cmd);
          return;
        }
        continue;
      }
      if (cmd == 0) throw new InvalidSvg("Path SVG sin comando inicial");
      boolean relative = Character.isLowerCase(cmd);
      switch (Character.toUpperCase(cmd)) {
        case 'M', 'L' -> {
          if (i + 1 >= tokens.size()) throw new InvalidSvg("Path SVG con par incompleto");
          double nx = parse(tokens.get(i)), ny = parse(tokens.get(i + 1));
          i += 2;
          if (relative) { nx += x; ny += y; }
          if (Character.toUpperCase(cmd) == 'M') {
            startX = nx; startY = ny;
            cmd = relative ? 'l' : 'L';
          } else add(candidate, x, y, nx, ny, "path");
          x = nx; y = ny;
        }
        case 'H' -> {
          double nx = parse(tokens.get(i++)); if (relative) nx += x;
          add(candidate, x, y, nx, y, "path"); x = nx;
        }
        case 'V' -> {
          double ny = parse(tokens.get(i++)); if (relative) ny += y;
          add(candidate, x, y, x, ny, "path"); y = ny;
        }
        default -> throw new InvalidSvg("Path SVG no compatible");
      }
    }
    result.addAll(candidate);
  }

  private static void text(Element element, List<Label> labels, List<String> warnings) {
    String value = element.getTextContent();
    if (value == null || value.isBlank()) return;
    value = value.replaceAll("\\s+", " ").strip();
    if (value.length() > 160) { warnings.add("Etiqueta de texto demasiado larga; omitida"); return; }
    try {
      labels.add(new Label(number(element, "x", 0), number(element, "y", 0), value, categorize(value)));
    } catch (InvalidSvg bad) {
      warnings.add("No se pudo posicionar etiqueta: " + value);
    }
  }

  static String categorize(String value) {
    String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
      .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    if (normalized.contains("consultorio") || normalized.contains("sala") || normalized.contains("oficina"))
      return "ROOM";
    if (normalized.contains("pasillo") || normalized.contains("corredor") || normalized.contains("hall"))
      return "CORRIDOR";
    if (normalized.contains("ascensor") || normalized.contains("elevador")) return "ELEVATOR";
    if (normalized.contains("escalera")) return "STAIRS";
    if (normalized.contains("farmacia") || normalized.contains("laboratorio") || normalized.contains("recepcion"))
      return "SERVICE";
    return "UNCLASSIFIED";
  }
}
