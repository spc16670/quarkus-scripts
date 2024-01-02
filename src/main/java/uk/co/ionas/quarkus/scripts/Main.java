package uk.co.ionas.quarkus.scripts;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@QuarkusMain
@ApplicationScoped
public class Main implements QuarkusApplication {

    @Inject
    OpenSearchAsyncClient client;

    public static void main(String... args) {
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