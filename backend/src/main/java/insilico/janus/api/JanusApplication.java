package insilico.janus.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point of the Janus REST backend.
 *
 * <p>The service runs locally, bundled in the desktop application, and exposes the Janus
 * assessment workflows over HTTP. It has no security layer on purpose: it is not meant to be
 * reachable from outside the machine it runs on.</p>
 */
@SpringBootApplication
@EnableScheduling
public class JanusApplication {

    /**
     * @param args the command line arguments, passed to Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(JanusApplication.class, args);
    }
}
