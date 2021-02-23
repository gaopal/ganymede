package galyleo.dependency;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.settings.Repository;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;
import static org.apache.maven.artifact.ArtifactUtils.versionlessKey;

/**
 * Target class for YAML representation of POM elements.
 *
 * {@bean.info}
 *
 * @author {@link.uri mailto:ball@hcf.dev Allen D. Ball}
 * @version $Revision$
 */
@JsonPropertyOrder({ "localRepository", "interactiveMode", "offline",
                     "repositories", "dependencies" })
@Data @NoArgsConstructor @ToString @Log4j2
public class POM {
    private static final YAMLFactory YAML_FACTORY =
        new YAMLFactory()
        .enable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER);
    private static final ObjectMapper OBJECT_MAPPER =
        new ObjectMapper(YAML_FACTORY)
        .setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
        .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_VALUES)
        .enable(SerializationFeature.INDENT_OUTPUT);

    /**
     * Static method to get default {@link POM}.
     *
     * @return  The parsed {@link POM}.
     */
    public static POM getDefault() throws Exception {
        POM pom = null;
        var resource = POM.class.getSimpleName() + ".yaml";

        try (var in = POM.class.getResourceAsStream(resource)) {
            pom = OBJECT_MAPPER.readValue(in, POM.class);
        }

        return pom;
    }

    /**
     * Static method to parse a YAML {@link String} to a {@link POM}.
     *
     * @param   yaml            The YAML {@link String}.
     *
     * @return  The parsed {@link POM}.
     */
    public static POM parse(String yaml) throws Exception {
        return OBJECT_MAPPER.readValue(yaml, POM.class);
    }

    private String localRepository = null;
    private Boolean interactiveMode = null;
    private Boolean offline = null;
    private Set<Repository> repositories = new LinkedHashSet<>();
    private Set<Dependency> dependencies = new LinkedHashSet<>();

    /**
     * Method to merge a {@link POM} into {@link.this} {@link POM}.
     *
     * @param   that            The {@link POM} to merge.
     *
     * @return  {@code true} if this {@link POM} was modified; {@code false}
     *          otherwise.
     */
    public boolean merge(POM that) {
        var modified = false;

        modified |= update(this, that, POM::getLocalRepository, this::setLocalRepository);
        modified |= update(this, that, POM::getInteractiveMode, this::setInteractiveMode);
        modified |= update(this, that, POM::getOffline, this::setOffline);
        /*
         * TBD: Need to complete modified tests.
         */
        var ids =
            that.getRepositories().stream()
            .map(t -> t.getId())
            .filter(Objects::nonNull)
            .collect(toSet());
        var urls =
            that.getRepositories().stream()
            .map(t -> t.getUrl())
            .filter(Objects::nonNull)
            .collect(toSet());

        this.getRepositories()
            .removeIf(t -> ids.contains(t.getId()) || urls.contains(t.getUrl()));
        this.getRepositories().addAll(that.getRepositories());

        var keys =
            that.getDependencies().stream()
            .map(t -> versionlessKey(t))
            .collect(toSet());

        this.getDependencies()
            .removeIf(t -> keys.contains(versionlessKey(t)));
        this.getDependencies().addAll(that.getDependencies());

        return modified;
    }

    private <T,U> boolean update(T left, T right, Function<T,U> get, Consumer<U> set) {
        var value = get.apply(right);
        var modified = (value != null && (! Objects.equals(get.apply(left), value)));

        if (modified) {
            set.accept(value);
        }

        return modified;
    }

    /**
     * Method to write {@link.this} {@link POM}'s YAML representation.
     *
     * @param   out             The target {@link OutputStream}.
     *
     * @throws  IOException     If the {@link POM} cannot be written for any
     *                          reason.
     */
    public void writeTo(OutputStream out) throws IOException {
        OBJECT_MAPPER.writeValue(out, this);
    }

    /**
     * {@link POM} {@link Dependency Dependency}
     * {@link DefaultArtifact Artifact}.
     *
     * {@bean.info}
     */
    @JsonSerialize(as = SerializedDependency.class)
    public static class Dependency extends DefaultArtifact implements SerializedDependency {

        /**
         * No-argument constructor.
         */
        public Dependency() { this(new String[] { }); }

        /**
         * G-A-V constructor.
         *
         * @param       gav     The "{@code groupId:artifactId:version}"
         *                      {@link String}.
         */
        public Dependency(String gav) { this(gav.split(":")); }

        /**
         * Construct from "{@code groupId}", "{@code artifactId}", and
         * "{@code version}".
         *
         * @param       g       The "{@code groupId}" {@link String}.
         * @param       a       The "{@code artifactId}" {@link String}.
         * @param       v       The "{@code version}" {@link String}.
         */
        public Dependency(String g, String a, String v) {
            this(new String[] { g, a, v });
        }

        private Dependency(String[] argv) {
            super((argv.length > 0) ? argv[0] : "unknown",
                  (argv.length > 1) ? argv[1] : "unknown",
                  (argv.length > 2) ? argv[2] : LATEST_VERSION,
                  (argv.length > 3) ? argv[3] : "runtime",
                  (argv.length > 4) ? argv[4] : "jar",
                  (argv.length > 5) ? argv[5] : "",
                  null);
        }

        @Override
        public String toString() { return ArtifactUtils.key(this); }
    }

    /**
     * {@link POM} {@link Dependency Dependency} {@link JsonSerialize}
     * annotation argument type.
     */
    @JsonPropertyOrder({ "groupId", "artifactId", "version", "type" })
    public static interface SerializedDependency {
        String getGroupId();
        String getArtifactId();
        String getVersion();
        String getScope();
        String getType();
        String getClassifier();
    }
}
