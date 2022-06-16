package io.github.jhahn.enhancedcdi.messaging.serialization;

import com.rabbitmq.client.BasicProperties;
import io.github.jhahn.enhancedcdi.messaging.InvalidMessageException;
import io.github.jhahn.enhancedcdi.messaging.PropertiesBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.enterprise.context.Dependent;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Pattern;

@Dependent
public class XmlCodec implements ContentTypeBasedDeserializer<Document>, BuiltInCodec<Document, Node>, CharsetAware {
    private static final Pattern CONTENT_TYPE_PATTERN = Pattern.compile(
            "^(text/xml|application/xml|\\w+/(\\w+)\\+?xml)");

    //region Decoder
    @Override
    public Pattern getContentTypePattern() {
        return CONTENT_TYPE_PATTERN;
    }

    @Override
    public Deserialized<Document> deserialize(InputStream messageBody, BasicProperties properties) {
        if (!canDeserialize(properties)) {
            throw new IllegalStateException("deserialize() called on non-XML content type");
        }

        try {
            final DocumentBuilder documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            final InputSource is = new InputSource(messageBody);
            return new Deserialized<>(documentBuilder.parse(is));
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new InvalidMessageException("XML message could not be parsed", e);
        }
    }
    //endregion

    //region Encoder
    @Override
    public Class<Node> getEncodableType() {
        return Node.class;
    }

    @Override
    public byte[] serialize(Node payload, PropertiesBuilder responseProperties) {
        try {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, getCharset(responseProperties).name());

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            transformer.transform(new DOMSource(payload), new StreamResult(outputStream));
            return outputStream.toByteArray();
        } catch (TransformerException ex) {
            throw new RuntimeException("Error converting to String", ex);
        }
    }
    //endregion

    /**
     * slightly higher than {@link BuiltInCodec#getPriority()} so that {@code "text/xml"} gets decoded as XML, not as
     * String.
     */
    @Override
    public int getPriority() {
        return Integer.MIN_VALUE + 1;
    }
}
