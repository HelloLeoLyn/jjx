package com.jjx.system.service;

import com.jjx.common.exception.BusinessException;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.*;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.util.Locale;
import java.util.Set;

/** 仅接收可解码的 PNG/JPEG 或无脚本、无外链的静态 SVG；不信任浏览器 MIME/扩展名。 */
final class ProcessIconValidator {
    private static final int MAX_BYTES = 1024 * 1024;
    private static final int MAX_SIDE = 2048;
    private static final String SVG = "http://www.w3.org/2000/svg";
    private static final Set<String> ELEMENTS = Set.of("svg", "g", "defs", "path", "rect", "circle", "ellipse",
        "line", "polyline", "polygon", "title", "desc", "text", "tspan", "linearGradient", "radialGradient",
        "stop", "clipPath", "mask", "use", "symbol");
    private static final Set<String> ATTRIBUTES = Set.of("id", "viewBox", "width", "height", "x", "y", "x1", "y1",
        "x2", "y2", "cx", "cy", "r", "rx", "ry", "d", "points", "transform", "fill", "fill-rule",
        "fill-opacity", "stroke", "stroke-width", "stroke-linecap", "stroke-linejoin", "stroke-miterlimit",
        "stroke-dasharray", "stroke-dashoffset", "stroke-opacity", "opacity", "offset", "stop-color",
        "stop-opacity", "gradientUnits", "gradientTransform", "spreadMethod", "fx", "fy", "fr",
        "clip-path", "clip-rule", "clipPathUnits", "mask", "maskUnits", "maskContentUnits",
        "preserveAspectRatio", "font-family", "font-size", "font-weight", "text-anchor", "dx", "dy",
        "dominant-baseline", "vector-effect", "version", "href");

    static String validate(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_BYTES)
            throw new BusinessException("请选择不超过1MB的PNG、JPEG或静态SVG图标");
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length > MAX_BYTES) throw new BusinessException("图标不能超过1MB");
            // 先由解码器识别位图类型，忽略客户端宣称的 MIME。
            try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(input);
                if (readers.hasNext()) {
                    ImageReader reader = readers.next();
                    try {
                        reader.setInput(input, true, true);
                        String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                        if (!Set.of("png", "jpeg", "jpg").contains(format)) throw new BusinessException("仅支持PNG、JPEG和静态SVG");
                        if (reader.getWidth(0) > MAX_SIDE || reader.getHeight(0) > MAX_SIDE)
                            throw new BusinessException("图标宽高不能超过2048像素");
                        if (reader.read(0) == null) throw new BusinessException("图标图片无法解码");
                        return format.equals("png") ? "image/png" : "image/jpeg";
                    } finally { reader.dispose(); }
                }
            }
            validateSvg(bytes);
            return "image/svg+xml";
        } catch (BusinessException ex) { throw ex; }
        catch (Exception ex) { throw new BusinessException("图标格式无效；SVG仅支持静态图形，不支持脚本、样式表或外部资源"); }
    }

    private static void validateSvg(byte[] bytes) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        var builder = factory.newDocumentBuilder();
        builder.setErrorHandler(new DefaultHandler() {
            @Override public void error(org.xml.sax.SAXParseException ex) throws SAXException { throw ex; }
            @Override public void fatalError(org.xml.sax.SAXParseException ex) throws SAXException { throw ex; }
        });
        Document document = builder.parse(new InputSource(new ByteArrayInputStream(bytes)));
        if (!"svg".equals(document.getDocumentElement().getLocalName())) throw new SAXException("Not SVG");
        inspect(document, 0, new int[] {0});
    }

    private static void inspect(Node node, int depth, int[] count) throws SAXException {
        if (depth > 32 || ++count[0] > 10000) throw new SAXException("SVG too complex");
        if (node.getNodeType() == Node.PROCESSING_INSTRUCTION_NODE || node.getNodeType() == Node.ENTITY_REFERENCE_NODE)
            throw new SAXException("Active XML");
        if (node instanceof Element element) {
            if (!SVG.equals(element.getNamespaceURI()) || !ELEMENTS.contains(element.getLocalName()))
                throw new SAXException("Unsupported SVG element");
            NamedNodeMap attributes = element.getAttributes();
            for (int i = 0; i < attributes.getLength(); i++) {
                Node attr = attributes.item(i);
                if (XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(attr.getNamespaceURI())) {
                    if (!Set.of(SVG, "http://www.w3.org/1999/xlink").contains(attr.getNodeValue()))
                        throw new SAXException("Foreign namespace");
                    continue;
                }
                String name = attr.getLocalName();
                String namespace = attr.getNamespaceURI();
                if (!ATTRIBUTES.contains(name) || (namespace != null
                        && !("http://www.w3.org/1999/xlink".equals(namespace) && "href".equals(name))))
                    throw new SAXException("Unsupported SVG attribute");
                String value = attr.getNodeValue().strip();
                if ("href".equals(name)) {
                    if (!value.matches("#[A-Za-z_][A-Za-z0-9_.-]*")) throw new SAXException("External reference");
                } else if (Set.of("fill", "stroke", "clip-path", "mask").contains(name)) {
                    if (!value.matches("(?:#[A-Fa-f0-9]{3,8}|[A-Za-z]+|(?:rgb|rgba|hsl|hsla)\\([0-9.,% +\\-]+\\)|url\\(#[A-Za-z_][A-Za-z0-9_.-]*\\))"))
                        throw new SAXException("Unsafe paint/reference");
                }
            }
        }
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling())
            inspect(child, depth + 1, count);
    }
}
