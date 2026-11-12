package insilico.janus.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Describes the API contract.
 *
 * <p>The contract is the single source of truth shared with the frontend: it is served at
 * {@code /v3/api-docs}, browsable at {@code /swagger-ui.html}, and written to
 * {@code api/openapi.json} by the build, from where the Angular client is generated. The
 * descriptions come from the Javadoc of the controllers and of the DTOs, so they cannot drift
 * away from the code.</p>
 */
@Configuration
public class OpenApiConfig {

    /**
     * @return the metadata of the API
     */
    @Bean
    public OpenAPI janusOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Janus API")
                        .version("1.0.0")
                        .description("""
                                Assessment of the persistence, bioaccumulation, toxicity, \
                                carcinogenicity, mutagenicity, reproductive toxicity and \
                                endocrine disruption of a chemical, from its SMILES.

                                A calculation is always a job: the request is accepted \
                                immediately, the progress can be followed on the event stream \
                                of the job, and the results are fetched once it completes.

                                The service runs locally inside the desktop application and \
                                has no authentication on purpose.""")
                        .license(new License().name("Proprietary - ProtoQSAR")))
                // relative: the frontend calls the backend on its own origin, through the
                // dev-server proxy now and from the packaged desktop application later
                .servers(List.of(new Server()
                        .url("/")
                        .description("The backend itself")));
    }
}
