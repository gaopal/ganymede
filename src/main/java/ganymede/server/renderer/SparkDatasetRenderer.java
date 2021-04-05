package ganymede.server.renderer;
/*-
 * ##########################################################################
 * Ganymede
 * $Id$
 * $HeadURL$
 * %%
 * Copyright (C) 2021 Allen D. Ball
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ##########################################################################
 */
import ball.annotation.ServiceProviderFor;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ganymede.server.Renderer;
import java.util.Map;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.apache.spark.sql.Dataset;

/**
 * Spark {@link Dataset} {@link Renderer} service provider.
 *
 * @author {@link.uri mailto:ball@hcf.dev Allen D. Ball}
 * @version $Revision$
 */
@ServiceProviderFor({ Renderer.class })
@ForClassName("org.apache.spark.sql.Dataset")
@NoArgsConstructor @ToString
public class SparkDatasetRenderer implements Renderer {
    @Override
    public void renderTo(ObjectNode bundle, Object object) {
        var resource = getClass().getSimpleName() + ".html";
        var map = Map.<String,Object>of("dataset", (Dataset) object, "view", 50);
        var output = new ThymeleafTemplateRenderer.Output(getClass(), resource, "html", map);

        MAP.renderTo(bundle, output);
    }
}
