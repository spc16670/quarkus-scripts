package uk.co.ionas.quarkus.scripts.opensearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.inject.Instance;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.util.Timeout;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.transport.aws.AwsSdk2Transport;
import org.opensearch.client.transport.aws.AwsSdk2TransportOptions;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5Transport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProviderChain;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.SdkHttpConfigurationOption;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.utils.AttributeMap;

import javax.net.ssl.SSLContext;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class OpenSearchTransportHelper {

    @SneakyThrows
    public static ApacheHttpClient5Transport createApacheHttpClient5Transport(final OpenSearchConfig config,
                                                                              final Instance<ObjectMapper> objectMappers) {
        List<HttpHost> list = new ArrayList<>();
        for (String s : config.hosts()
                .orElse(List.of("127.0.0.1:9200"))) {
            String[] h = s.split(":");
            HttpHost apply = new HttpHost(config.protocol(), h[0], Integer.valueOf(h[1]));
            list.add(apply);
        }

        final HttpHost[] hosts = list.toArray(new HttpHost[0]);
        final ApacheHttpClient5TransportBuilder builder = ApacheHttpClient5TransportBuilder
                .builder(hosts);

        final ObjectMapper objectMapper = objectMappers.stream().findFirst()
                .orElse(new ObjectMapper().findAndRegisterModules());
        builder.setMapper(new JacksonJsonpMapper(objectMapper));

        final SSLContextBuilder sslContextBuilder = SSLContextBuilder.create();
        if (config.skipHostVerification()) {
            sslContextBuilder.loadTrustMaterial(null, (chains, authType) -> true);
        }
        final SSLContext context = sslContextBuilder.build();

        builder.setHttpClientConfigCallback(httpAsyncClientBuilder -> {

            final TlsStrategy tlsStrategy = ClientTlsStrategyBuilder.create()
                    .setSslContext(context)
                    .build();
            final ConnectionConfig connectionConfig = ConnectionConfig.custom()
                    .setConnectTimeout(Timeout.of(config.connectionTimeout().toMillis(), TimeUnit.MILLISECONDS))
                    .setSocketTimeout(Timeout.of(config.socketTimeout().toMillis(), TimeUnit.MILLISECONDS))
                    .build();
            final PoolingAsyncClientConnectionManager connectionManager = PoolingAsyncClientConnectionManagerBuilder
                    .create()

                    .setTlsStrategy(tlsStrategy)
                    .setMaxConnPerRoute(config.maxConnectionsPerRoute())
                    .setMaxConnTotal(config.maxConnections())
                    .setDefaultConnectionConfig(connectionConfig)
                    .build();

            if (config.username().isPresent() && config.password().isPresent()) {
                if (!"https".equalsIgnoreCase(config.protocol())) {
                    log.warn("Using Basic authentication in HTTP implies sending plain text passwords over the wire, " +
                            "use the HTTPS protocol instead.");
                }
                final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                credentialsProvider.setCredentials(new AuthScope(null, -1),
                        new UsernamePasswordCredentials(config.username().get(), config.password().get().toCharArray()));
                httpAsyncClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
            }
            return httpAsyncClientBuilder.setConnectionManager(connectionManager);
        });

        return builder.build();
    }

    public static AwsSdk2Transport createAwsSdk2Transport(final OpenSearchConfig config,
                                                          final Instance<ObjectMapper> objectMappers) {

        final AttributeMap.Builder attrBuilder = AttributeMap.builder();
        if (config.skipHostVerification()) {
            attrBuilder.put(SdkHttpConfigurationOption.TRUST_ALL_CERTIFICATES, true);
        }
        final AttributeMap attributeMap = attrBuilder.build();
        final SdkAsyncHttpClient nettyHttpClient = NettyNioAsyncHttpClient.builder()
                .buildWithDefaults(attributeMap);
        final AwsSdk2TransportOptions.Builder options = AwsSdk2TransportOptions.builder();

        // use existing ObjectMapper or create new ObjectMapper and register all modules
        final ObjectMapper objectMapper = objectMappers.stream().findFirst()
                .orElse(new ObjectMapper().findAndRegisterModules());
        options.setMapper(new JacksonJsonpMapper(objectMapper));


        if (config.accessKeyId().isPresent() && config.secretAccessKey().isPresent()) {
            options.setCredentials(AwsCredentialsProviderChain.of(
                    StaticCredentialsProvider
                            .create(AwsBasicCredentials.create(config.accessKeyId().get(), config.secretAccessKey().get())),
                    DefaultCredentialsProvider.create()));
        } else {
            options.setCredentials(DefaultCredentialsProvider.create());
        }

        // These are overwritten by aws sig 4
//        String auth = config.username().get() +":"+ config.password().get();
//        String s = Base64.getEncoder().encodeToString(auth.getBytes());
//        System.out.println("h:: " + s);
//        options.addHeader("Authorization", "Basic " + s);

        return new AwsSdk2Transport(
                nettyHttpClient,
                config.hosts().orElse(List.of("")).get(0),
                config.awsService().get(),
                Region.of(config.awsRegion()),
                options.build());
    }

}