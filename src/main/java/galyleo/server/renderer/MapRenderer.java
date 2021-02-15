package galyleo.server.renderer;

import ball.annotation.ServiceProviderFor;
import com.fasterxml.jackson.databind.node.ObjectNode;
import galyleo.server.Renderer;
import java.util.Map;
import lombok.NoArgsConstructor;
import lombok.ToString;

import static galyleo.server.Server.OBJECT_MAPPER;

/**
 * {@link Map} {@link Renderer} service provider.
 *
 * @author {@link.uri mailto:ball@hcf.dev Allen D. Ball}
 * @version $Revision$
 */
@ServiceProviderFor({ Renderer.class })
@MimeType("application/json") @ForType(Map.class)
@NoArgsConstructor @ToString
public class MapRenderer extends JsonNodeRenderer {
    @Override
    public void renderTo(ObjectNode bundle, Object object) {
        super.renderTo(bundle, OBJECT_MAPPER.valueToTree(object));
    }
}
