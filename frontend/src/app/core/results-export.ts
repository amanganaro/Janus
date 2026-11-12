import { MoleculeResultDto } from '../api/models';
import {
  formatBioaccumulation,
  formatPersistence,
  formatReliability,
  formatScore,
  formatText,
  formatToxicity,
} from './result-format';

/** The header of the exported file, identical to `janus_txt_main_results.txt` of the legacy. */
const HEADER = [
  'No.',
  'Id',
  'SMILES',
  'P assessment [class]',
  'P assessment [index]',
  'P reliability',
  'P score',
  'B assessment [log units]',
  'B reliability',
  'B score',
  'T assessment [mg/l]',
  'T reliability',
  'T score',
  'C assessment',
  'C reliability',
  'C score',
  'M assessment',
  'M reliability',
  'M score',
  'R assessment',
  'R reliability',
  'R score',
  'ED assessment [class]',
  'ED assessment [index]',
  'ED reliability',
  'ED score',
  'SCORE(vPvB)',
  'SCORE(SVHC)',
  'SCORE(PBT)',
  'Remarks',
];

/**
 * Renders the results as the tab separated file the legacy application wrote.
 *
 * Unlike the legacy export, the values are the formatted ones: a missing number is a dash and
 * never the `-999999` sentinel.
 *
 * @param results the results of a job
 * @returns the content of the file
 */
export function resultsToTsv(results: MoleculeResultDto[]): string {
  const lines = [HEADER.join('\t')];

  for (const result of results) {
    lines.push(
      [
        result.num ?? '',
        result.id ?? '',
        result.smiles ?? '',
        formatText(result.persistence?.predictionClass),
        formatPersistence(result.persistence?.prediction),
        formatReliability(result.persistence?.reliability),
        formatScore(result.persistence?.score),
        formatBioaccumulation(result.bioaccumulation?.prediction),
        formatReliability(result.bioaccumulation?.reliability),
        formatScore(result.bioaccumulation?.score),
        formatToxicity(result.toxicity?.prediction),
        formatReliability(result.toxicity?.reliability),
        formatScore(result.toxicity?.score),
        formatText(result.carcinogenicity?.predictionClass),
        formatReliability(result.carcinogenicity?.reliability),
        formatScore(result.carcinogenicity?.score),
        formatText(result.mutagenicity?.predictionClass),
        formatReliability(result.mutagenicity?.reliability),
        formatScore(result.mutagenicity?.score),
        formatText(result.reproToxicity?.predictionClass),
        formatReliability(result.reproToxicity?.reliability),
        formatScore(result.reproToxicity?.score),
        formatText(result.endocrine?.predictionClass),
        formatScore(result.endocrine?.prediction),
        formatReliability(result.endocrine?.reliability),
        formatScore(result.endocrine?.score),
        formatScore(result.scoreVPvB),
        formatScore(result.scoreSVHC),
        formatScore(result.scorePBT),
        result.notes ?? '',
      ].join('\t'),
    );
  }

  return lines.join('\n');
}
