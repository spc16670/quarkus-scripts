package uk.co.ionas.quarkus.scripts.opensearch;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import lombok.AllArgsConstructor;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.transport.OpenSearchTransport;

@ApplicationScoped
@AllArgsConstructor
public class OpenSearchClientProducer {

    private final OpenSearchTransport transport;

    @Produces
    @Singleton
    public OpenSearchAsyncClient openSearchAsyncClient() {
        return new OpenSearchAsyncClient(transport);
    }

}
