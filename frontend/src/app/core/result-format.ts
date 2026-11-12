import { MoleculeResultDto, WorkflowResultDto } from '../api/models';

/**
 * Formatting and colour rules of the results.
 *
 * In the legacy application these lived in two places: the rounding was applied in Java by
 * `JSONOutputWriter`, the colours and the derived labels in `appJanus.js`. The REST API sends
 * raw numbers and omits what it could not calculate, so both jobs are done here, in one place,
 * and the thresholds are the legacy ones.
 */

/** What is shown in place of a value the backend could not produce. */
export const MISSING = '-';

/** The severity a value maps to, used to colour it. */
export type Severity = 'low' | 'medium' | 'high' | 'unknown';

function format(value: number | null | undefined, decimals: number): string {
  if (value === null || value === undefined) {
    return MISSING;
  }
  return value.toFixed(decimals);
}

/**
 * @param value a reliability, in [0, 1]
 * @returns the reliability with two decimals, as in the legacy output
 */
export function formatReliability(value: number | null | undefined): string {
  return format(value, 2);
}

/**
 * @param value a score or an aggregated index, in [0, 1]
 * @returns the score with three decimals, as in the legacy output
 */
export function formatScore(value: number | null | undefined): string {
  return format(value, 3);
}

/**
 * @param value a persistence prediction, in days
 * @returns two decimals below 10, an integer above, as `JSONOutputWriter` did
 */
export function formatPersistence(value: number | null | undefined): string {
  if (value === null || value === undefined) {
    return MISSING;
  }
  return value < 10 ? format(value, 2) : format(value, 0);
}

/**
 * @param value a bioaccumulation prediction, in log(L/kg)
 * @returns the value with two decimals
 */
export function formatBioaccumulation(value: number | null | undefined): string {
  return format(value, 2);
}

/**
 * @param value an aquatic toxicity prediction, in mg/l
 * @returns the value on the adaptive scale of the legacy output, which keeps very small
 *     concentrations readable
 */
export function formatToxicity(value: number | null | undefined): string {
  if (value === null || value === undefined) {
    return MISSING;
  }
  if (value < 0.0001) {
    return format(value, 6);
  }
  if (value < 0.01) {
    return format(value, 4);
  }
  if (value < 10) {
    return format(value, 2);
  }
  return format(value, 0);
}

/**
 * @param value a plain number, or nothing
 * @returns the value with three decimals, or the missing-value marker
 */
export function formatNumber(value: number | null | undefined): string {
  return format(value, 3);
}

/**
 * @param text a value that may be absent
 * @returns the text, or the missing-value marker
 */
export function formatText(text: string | null | undefined): string {
  return text === null || text === undefined || text === '' ? MISSING : text;
}

// --- colours ---------------------------------------------------------------------------------
// Thresholds copied from the legacy appJanus.js. Note that carcinogenicity is coloured on its
// score while mutagenicity, reproductive toxicity and endocrine disruption are coloured on their
// assessment: the inconsistency is in the original and is kept on purpose.

/**
 * @param prediction the persistence prediction
 * @returns its severity: below 1 low, below 2 medium, above high
 */
export function persistenceSeverity(prediction: number | null | undefined): Severity {
  if (prediction === null || prediction === undefined) {
    return 'unknown';
  }
  if (prediction < 1) {
    return 'low';
  }
  return prediction < 2 ? 'medium' : 'high';
}

/**
 * @param prediction the bioaccumulation prediction, in log(L/kg)
 * @returns its severity: below 3 low, below 3.7 medium, above high
 */
export function bioaccumulationSeverity(prediction: number | null | undefined): Severity {
  if (prediction === null || prediction === undefined) {
    return 'unknown';
  }
  if (prediction < 3) {
    return 'low';
  }
  return prediction < 3.7 ? 'medium' : 'high';
}

/**
 * @param prediction the aquatic toxicity prediction, in mg/l
 * @returns its severity on the inverted scale: a low concentration means a high hazard
 */
export function toxicitySeverity(prediction: number | null | undefined): Severity {
  if (prediction === null || prediction === undefined) {
    return 'unknown';
  }
  if (prediction < 0.01) {
    return 'high';
  }
  return prediction < 1 ? 'medium' : 'low';
}

/**
 * The rule the legacy applies to carcinogenicity, mutagenicity, reproductive toxicity and
 * endocrine disruption. Exactly 0.5 means "no information", not "borderline".
 *
 * @param value the value the property is coloured on
 * @returns its severity
 */
export function hazardSeverity(value: number | null | undefined): Severity {
  if (value === null || value === undefined || value === 0.5) {
    return 'unknown';
  }
  return value < 0.5 ? 'low' : 'high';
}

/**
 * The seven letters of the summary table, in the legacy order, each with its severity.
 *
 * @param result the result of one molecule
 * @returns the letters to render, with the colour of each
 */
export function severityLabels(result: MoleculeResultDto): { letter: string; severity: Severity }[] {
  return [
    { letter: 'P', severity: persistenceSeverity(result.persistence?.prediction) },
    { letter: 'B', severity: bioaccumulationSeverity(result.bioaccumulation?.prediction) },
    { letter: 'T', severity: toxicitySeverity(result.toxicity?.prediction) },
    { letter: 'C', severity: hazardSeverity(result.carcinogenicity?.score) },
    { letter: 'M', severity: hazardSeverity(result.mutagenicity?.prediction) },
    { letter: 'R', severity: hazardSeverity(result.reproToxicity?.prediction) },
    { letter: 'E', severity: hazardSeverity(result.endocrine?.prediction) },
  ];
}

// --- derived labels of the summary table -------------------------------------------------------

/**
 * @param result the carcinogenicity result
 * @returns the slope factor when the molecule is a carcinogen, `NC` when it is not, the
 *     missing-value marker when there is no information
 */
export function carcinogenicityLabel(result: WorkflowResultDto | undefined): string {
  const score = result?.score;
  if (score === null || score === undefined || score === 0.5) {
    return MISSING;
  }
  if (score > 0.5) {
    return result?.prediction === null || result?.prediction === undefined
      ? MISSING
      : formatBioaccumulation(result.prediction);
  }
  return 'NC';
}

function binaryLabel(
  value: number | null | undefined,
  positive: string,
  negative: string,
  unknownAtHalf = true,
): string {
  if (value === null || value === undefined || (unknownAtHalf && value === 0.5)) {
    return MISSING;
  }
  return value > 0.5 ? positive : negative;
}

/**
 * @param result the mutagenicity result
 * @returns `M`, `NM`, or the missing-value marker
 */
export function mutagenicityLabel(result: WorkflowResultDto | undefined): string {
  return binaryLabel(result?.prediction, 'M', 'NM');
}

/**
 * @param result the reproductive toxicity result
 * @returns `T`, `NT`, or the missing-value marker
 */
export function reproToxicityLabel(result: WorkflowResultDto | undefined): string {
  return binaryLabel(result?.prediction, 'T', 'NT');
}

/**
 * @param result the endocrine disruption result
 * @returns `A`, `NA`, or the missing-value marker. Unlike the other three, the legacy has no
 *     special case for exactly 0.5 here.
 */
export function endocrineLabel(result: WorkflowResultDto | undefined): string {
  return binaryLabel(result?.prediction, 'A', 'NA', false);
}
