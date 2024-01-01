package uk.co.ionas.quarkus.scripts.opensearch;

import java.security.*;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.net.ssl.*;

import jakarta.enterprise.inject.Instance;

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
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.client5.http.ssl.TrustSelfSignedStrategy;
import org.apache.hc.core5.function.Factory;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;

import org.apache.hc.core5.reactor.ssl.TlsDetails;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.util.Timeout;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.transport.aws.AwsSdk2Transport;
import org.opensearch.client.transport.aws.AwsSdk2TransportOptions;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5Transport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;


import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;

@Slf4j
@NoArgsConstructor
public final class OpenSearchTransportHelper {

    @SneakyThrows
    public static ApacheHttpClient5Transport createApacheHttpClient5Transport(final OpenSearchConfig config,
                                                                              final Instance<ObjectMapper> objectMappers)
            throws NoSuchAlgorithmException, KeyManagementException {

//        for (Provider provider: Security.getProviders()) {
//            System.out.println(provider.getName());
//            for (String key: provider.stringPropertyNames())
//                System.out.println("\t" + key + "\t" + provider.getProperty(key));
//        }

        System.setProperty("javax.net.ssl.trustStore", "D:\\Workspaces\\Playground\\scripts\\src\\main\\resources\\trust-store.pkcs12");
        System.setProperty("javax.net.ssl.trustStorePassword", "changeit");

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

        // use existing ObjectMapper or create new ObjectMapper and register all modules
        final ObjectMapper objectMapper = objectMappers.stream().findFirst()
                .orElse(new ObjectMapper().findAndRegisterModules());
        builder.setMapper(new JacksonJsonpMapper(objectMapper));

        TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        System.out.println("accepted");
                        return new X509Certificate[0];
                    }
                    public void checkClientTrusted(
                            java.security.cert.X509Certificate[] certs, String authType) {
                        System.out.println("trusted");
                    }
                    public void checkServerTrusted(
                            java.security.cert.X509Certificate[] certs, String authType) {
                        System.out.println("tr");
                    }
                }
        };

// Install the all-trusting trust manager


//        final SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
//        sslContext.init(null, trustAllCerts, new java.security.SecureRandom());


        final SSLContext sslContext = SSLContextBuilder.create()

                .loadTrustMaterial(null, (chains, authType) -> true)
//                .loadTrustMaterial(null, new TrustSelfSignedStrategy()) // conn closed
//                .loadTrustMaterial(null, new TrustAllStrategy())
                .build();


        builder.setHttpClientConfigCallback(httpAsyncClientBuilder -> {

            final TlsStrategy tlsStrategy = ClientTlsStrategyBuilder.create()
                    .setSslContext(sslContext)
                    .setCiphers("TLS_AES_256_GCM_SHA384")

//                    .setHostnameVerifier((hostName, session) -> {
//                        System.out.println("\n\n\n\n here");
//                        return true;
//                    })
//                    .setTlsDetailsFactory(new Factory<SSLEngine, TlsDetails>() {
//                        @Override
//                        public TlsDetails create(final SSLEngine sslEngine) {
//                            return new TlsDetails(sslEngine.getSession(), sslEngine.getApplicationProtocol());
//                        }
//                    })
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
                BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
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
        final SdkAsyncHttpClient nettyHttpClient = NettyNioAsyncHttpClient.create();
        AwsSdk2TransportOptions.Builder options = AwsSdk2TransportOptions.builder();

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

        return new AwsSdk2Transport(
                nettyHttpClient,
                config.hosts().orElse(List.of("")).get(0),
                config.awsService().get(),
                Region.of(config.awsRegion()),
                options.build());
    }

}