package uk.co.ionas.quarkus.scripts;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;

import javax.net.ssl.*;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;

@QuarkusMain
@ApplicationScoped
public class Main implements QuarkusApplication {

    @Inject
    OpenSearchAsyncClient client;

    public static void main(String... args) throws NoSuchAlgorithmException {
//        System.setProperty("com.sun.net.ssl.checkRevocation", "false");
//        System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");
//        TrustManager[] trustAllCerts = new TrustManager[] {
//                new X509TrustManager() {
//                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
//                        return null;
//                    }
//                    @Override
//                    public void checkClientTrusted(X509Certificate[] arg0, String arg1)
//                            throws CertificateException {}
//
//                    @Override
//                    public void checkServerTrusted(X509Certificate[] arg0, String arg1)
//                            throws CertificateException {}
//                }
//        };
//
//        SSLContext sc=null;
//        try {
//            sc = SSLContext.getInstance("TLS");
//        } catch (NoSuchAlgorithmException e) {
//            e.printStackTrace();
//        }
//        try {
//            sc.init(null, trustAllCerts, new java.security.SecureRandom());
//        } catch (KeyManagementException e) {
//            e.printStackTrace();
//        }
//        SSLContext.setDefault(sc);
//
//        // Create all-trusting host name verifier
//        HostnameVerifier validHosts = new HostnameVerifier() {
//            @Override
//            public boolean verify(String arg0, SSLSession arg1) {
//                return true;
//            }
//        };
        // All hosts will be valid

        Quarkus.run(Main.class, args);
    }

    @Override
    public int run(String... args) throws IOException {
        List<Thread> threadSet = new ArrayList<>(Thread.getAllStackTraces().keySet());
        System.out.println("Hello There are " + threadSet.size() + " threads");
        Collections.sort(threadSet, Comparator.comparing(Thread::getName));
        for (Thread thread : threadSet) {
            System.out.println(thread.getName());
        }

        Uni.createFrom()
                .completionStage(client.cat().indices())
                .onItem()
                .invoke(r -> System.out.println("r:: " + r.valueBody() + " on " + Thread.currentThread().getName()))
                .onFailure()
                .invoke(err ->  err.printStackTrace())
                .await()
                .indefinitely();



        return 0;
    }
}