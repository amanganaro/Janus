package insilico.janus.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Regenerates the OpenAPI contract and checks that it describes the whole API.
 *
 * <p>The contract is written to the file named by the {@code openapi.output.file} system
 * property, which the build sets to {@code api/openapi.json}. That file is committed and is
 * what the frontend generates its HTTP client from, so a change to a controller or to a DTO
 * shows up as a change of the contract in the same commit.</p>
 *
 * <p>The models are not loaded: the contract does not depend on them and loading them would
 * make the test slow and memory hungry.</p>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "janus.engine.load-models=false")
class OpenApiContractTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Test
    void writesTheContract() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v3/api-docs"))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode spec = JSON.readTree(response.body());

        // every endpoint of the service has to be part of the contract
        JsonNode paths = spec.get("paths");
        assertThat(names(paths)).contains(
                "/api/jobs",
                "/api/jobs/{id}",
                "/api/jobs/{id}/events",
                "/api/metadata",
                "/api/health");

        // the schemas the frontend client is generated from
        JsonNode schemas = spec.get("components").get("schemas");
        assertThat(names(schemas)).contains(
                "JanusJobRequest", "JobDto", "MoleculeResultDto", "WorkflowResultDto", "ErrorDto");

        // the descriptions are read from the Javadoc: if the annotation processor is not
        // configured they silently disappear, which would quietly gut the documentation
        assertThat(schemas.get("WorkflowResultDto").get("description").asText())
                .contains("workflow");

        Path output = Path.of(System.getProperty("openapi.output.file", "../api/openapi.json"));
        Files.createDirectories(output.toAbsolutePath().getParent());
        // written below, after the assertions, so a broken contract is never published
        Files.writeString(output,
                JSON.writerWithDefaultPrettyPrinter().writeValueAsString(spec) + "\n",
                StandardCharsets.UTF_8);
    }

    /**
     * @param node an object node of the contract
     * @return the names of its properties
     */
    private static List<String> names(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
