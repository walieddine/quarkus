package io.quarkus.platform.descriptor.loader.json.impl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.function.Function;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;

import io.quarkus.maven.utilities.MojoUtils;
import io.quarkus.platform.descriptor.QuarkusPlatformDescriptor;
import io.quarkus.platform.descriptor.loader.QuarkusPlatformDescriptorLoader;
import io.quarkus.platform.descriptor.loader.QuarkusPlatformDescriptorLoaderContext;
import io.quarkus.platform.descriptor.loader.json.ArtifactResolver;
import io.quarkus.platform.descriptor.loader.json.QuarkusJsonPlatformDescriptorLoaderContext;
import io.quarkus.platform.tools.MessageWriter;

public class QuarkusJsonPlatformDescriptorLoaderBootstrap
        implements QuarkusPlatformDescriptorLoader<QuarkusPlatformDescriptor, QuarkusPlatformDescriptorLoaderContext> {

    private static InputStream getResourceStream(String relativePath) {
        final InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(relativePath);
        if (is == null) {
            throw new IllegalStateException("Failed to locate " + relativePath + " on the classpath");
        }
        return is;
    }

    @Override
    public QuarkusPlatformDescriptor load(QuarkusPlatformDescriptorLoaderContext context) {

        context.getMessageWriter().debug("Loading the default Quarkus Platform descriptor from the classpath");

        final Properties props = new Properties();
        final InputStream quarkusProps = getResourceStream("quarkus.properties");
        try {
            props.load(quarkusProps);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load properties quarkus.properties", e);
        }

        final ArtifactResolver resolver = new ArtifactResolver() {
            @Override
            public <T> T process(String groupId, String artifactId, String classifier, String type, String version,
                    Function<Path, T> processor) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<Dependency> getManagedDependencies(String groupId, String artifactId, String version) {
                if (artifactId.equals("quarkus-bom")
                        && version.equals(props.get("plugin-version"))
                        && groupId.equals("io.quarkus")) {
                    try {
                        final Model model = MojoUtils.readPom(getResourceStream("quarkus-bom/pom.xml"));
                        final List<Dependency> deps = model.getDependencyManagement().getDependencies();
                        for (Dependency d : deps) {
                            if (d.getVersion().startsWith("${")) {
                                d.setVersion(model.getProperties()
                                        .getProperty(d.getVersion().substring(2, d.getVersion().length() - 1)));
                            }
                        }
                        return deps;
                    } catch (IOException e) {
                        throw new IllegalStateException(
                                "Failed to load POM model from " + groupId + ":" + artifactId + ":" + version, e);
                    }
                }
                throw new IllegalStateException("Unexpected artifact " + groupId + ":" + artifactId + ":" + version);
            }
        };

        final Path resourceRoot;
        try {
            resourceRoot = MojoUtils.getClassOrigin(QuarkusJsonPlatformDescriptorLoaderBootstrap.class);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to determine the resource root for " + getClass().getName(), e);
        }

        return new QuarkusJsonPlatformDescriptorLoaderImpl().load(new QuarkusJsonPlatformDescriptorLoaderContext() {

            @Override
            public MessageWriter getMessageWriter() {
                return context.getMessageWriter();
            }

            @Override
            public <T> T parseJson(Function<Path, T> parser) {
                if (Files.isDirectory(resourceRoot)) {
                    return doParse(resourceRoot.resolve("quarkus-bom-descriptor/extensions.json"), parser);
                }

                try (FileSystem fs = FileSystems.newFileSystem(resourceRoot, null)) {
                    return doParse(fs.getPath("/quarkus-bom-descriptor/extensions.json"), parser);
                } catch (IOException e) {
                    throw new IllegalStateException("Failed to open " + resourceRoot, e);
                }
            }

            @Override
            public ArtifactResolver getArtifactResolver() {
                return resolver;
            }
        });
    }

    private static <T> T doParse(Path p, Function<Path, T> parser) {
        if (!Files.exists(p)) {
            throw new IllegalStateException("Path does not exist: " + p);
        }
        return parser.apply(p);
    }
}
