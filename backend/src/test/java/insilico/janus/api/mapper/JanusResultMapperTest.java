package insilico.janus.api.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import insilico.janus.ExperimentalValues;
import insilico.janus.JanusResult;
import insilico.janus.api.dto.MoleculeRequestDto;
import insilico.janus.api.dto.MoleculeResultDto;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests the conventions of the legacy domain objects that the mapper has to hide.
 */
class JanusResultMapperTest {

    private final JanusResultMapper mapper = new JanusResultMapper();

    @Test
    void mapsTheMissingValueSentinelToNull() {
        // a fresh result has every number set to MISSING_VALUE
        MoleculeResultDto dto = mapper.toDto(new JanusResult());

        assertThat(dto.persistence().prediction()).isNull();
        assertThat(dto.persistence().reliability()).isNull();
        assertThat(dto.persistence().score()).isNull();
        assertThat(dto.endocrine().predictionClass()).isEqualTo("-");
    }

    @Test
    void mapsTheAbsentParentToNull() {
        JanusResult result = new JanusResult();

        assertThat(mapper.toDto(result).parentNum()).isNull();

        result.ParentNum = 3;
        assertThat(mapper.toDto(result).parentNum()).isEqualTo(3);
    }

    @Test
    void keepsTheOrderOfTheAdditionalValues() {
        JanusResult result = new JanusResult();
        LinkedHashMap<String, String> values = result.B.AdditionalValues;
        values.put("first", "1");
        values.put("second", "2");
        values.put("third", "3");

        assertThat(mapper.toDto(result).bioaccumulation().additionalValues())
                .extracting("name")
                .containsExactly("first", "second", "third");
    }

    @Test
    void keepsTheValuesThatAreActuallySet() {
        JanusResult result = new JanusResult();
        result.P.Prediction = 0.75;
        result.P.PredictionClass = "P";
        result.S_PBT = 0.25;

        MoleculeResultDto dto = mapper.toDto(result);

        assertThat(dto.persistence().prediction()).isEqualTo(0.75);
        assertThat(dto.persistence().predictionClass()).isEqualTo("P");
        assertThat(dto.scorePBT()).isEqualTo(0.25);
    }

    @Test
    void buildsTheExperimentalValuesFromTheMap() throws Exception {
        Map<String, Double> input = new LinkedHashMap<>();
        input.put(ExperimentalValues.KEY_LOGP, 2.13);
        input.put(ExperimentalValues.KEY_BCF, 0.5);

        ExperimentalValues values =
                mapper.toExperimentalValues(new MoleculeRequestDto("id", "c1ccccc1", input));

        assertThat(values.getProperty(ExperimentalValues.KEY_LOGP)).isEqualTo(2.13);
        assertThat(values.getProperty(ExperimentalValues.KEY_BCF)).isEqualTo(0.5);
        assertThat(values.hasProperty(ExperimentalValues.KEY_TOX)).isFalse();
    }

    @Test
    void acceptsAMoleculeWithoutExperimentalValues() throws Exception {
        ExperimentalValues values =
                mapper.toExperimentalValues(new MoleculeRequestDto(null, "c1ccccc1", null));

        assertThat(values.hasProperty(ExperimentalValues.KEY_LOGP)).isFalse();
    }
}
