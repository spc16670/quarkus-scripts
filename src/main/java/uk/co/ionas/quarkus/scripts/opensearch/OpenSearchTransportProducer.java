package uk.co.ionas.quarkus.scripts.opensearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.opensearch.client.transport.OpenSearchTransport;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashSet;
import java.util.Set;

@ApplicationScoped
@RequiredArgsConstructor
public class OpenSearchTransportProducer {
    private final Instance<ObjectMapper> objectMappers;
    private final OpenSearchConfig config;

    private final Set<OpenSearchTransport> transports = new HashSet<>();


    @Produces
    @Singleton
    public OpenSearchTransport openSearchTransport() {
        if (config.awsService().isPresent()) {
            return addTransport(OpenSearchTransportHelper.createAwsSdk2Transport(config, objectMappers));
        }
        return addTransport(OpenSearchTransportHelper.createApacheHttpClient5Transport(config, objectMappers));
    }

    private OpenSearchTransport addTransport(final OpenSearchTransport transport) {
        transports.add(transport);
        return transport;
    }

    @PreDestroy
    void destroy() {
        for (OpenSearchTransport transport : transports) {
            try {
                transport.close();
            } catch (IOException ioe) {
                throw new UncheckedIOException(ioe);
            }
        }
    }
}