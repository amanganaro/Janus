import { MoleculeRequestDto } from '../api/models';

/** The outcome of reading an input file or a pasted text. */
export interface ParsedInput {
  /** The molecules to send to the backend. */
  molecules: MoleculeRequestDto[];
  /** Non blocking problems, to show next to the list. */
  warnings: string[];
}

/**
 * Reads the molecules from the legacy input format: one molecule per line, fields separated by
 * tabs. The first field is the SMILES; any further field shaped as `key:value` is an
 * experimental value. Empty lines are ignored.
 *
 * An unknown experimental key is reported as a warning and dropped, rather than failing the
 * whole input: the backend would ignore it anyway.
 *
 * @param text the content of the file, or what was pasted in the textarea
 * @param knownKeys the experimental keys the backend accepts, from `GET /api/metadata`
 * @returns the molecules and the warnings
 */
export function parseSmilesInput(text: string, knownKeys: string[] = []): ParsedInput {
  const molecules: MoleculeRequestDto[] = [];
  const warnings: string[] = [];
  const known = new Set(knownKeys.map((key) => key.toLowerCase()));

  const lines = text.split(/\r?\n/);

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i].trim();
    if (line === '') {
      continue;
    }

    const fields = line.split('\t').map((field) => field.trim());
    const smiles = fields[0];
    if (smiles === '') {
      continue;
    }

    const experimentalValues: Record<string, number> = {};
    for (const field of fields.slice(1)) {
      if (field === '' || !field.includes(':')) {
        continue;
      }
      const [rawKey, rawValue] = field.split(':', 2);
      const key = rawKey.trim().toLowerCase();
      const value = Number(rawValue.trim());

      if (known.size > 0 && !known.has(key)) {
        warnings.push(`Line ${i + 1}: unknown experimental key "${rawKey.trim()}", ignored.`);
        continue;
      }
      if (!Number.isFinite(value)) {
        warnings.push(`Line ${i + 1}: "${rawValue.trim()}" is not a number, ignored.`);
        continue;
      }
      experimentalValues[key] = value;
    }

    const molecule: MoleculeRequestDto = { smiles };
    if (Object.keys(experimentalValues).length > 0) {
      molecule.experimentalValues = experimentalValues;
    }
    molecules.push(molecule);
  }

  return { molecules, warnings };
}
