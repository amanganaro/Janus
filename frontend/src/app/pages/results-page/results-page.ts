import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatIconModule } from '@angular/material/icon';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';
import { JobDto, MoleculeResultDto } from '../../api';
import { JanusService } from '../../core/janus.service';
import {
  MISSING,
  Severity,
  carcinogenicityLabel,
  endocrineLabel,
  formatBioaccumulation,
  formatNumber,
  formatPersistence,
  formatReliability,
  formatScore,
  formatText,
  formatToxicity,
  mutagenicityLabel,
  reproToxicityLabel,
  severityLabels,
} from '../../core/result-format';
import { resultsToTsv } from '../../core/results-export';

/** The groups of columns the user can show or hide, as in the legacy summary page. */
interface ColumnGroup {
  key: string;
  label: string;
  shown: boolean;
}

const TRUNCATE_AT = 20;

/**
 * The summary table: one row per molecule, with the link to its details.
 */
@Component({
  selector: 'app-results-page',
  imports: [
    MatButtonModule,
    MatCardModule,
    MatCheckboxModule,
    MatIconModule,
    MatSortModule,
    MatTableModule,
    MatTooltipModule,
    RouterLink,
  ],
  templateUrl: './results-page.html',
  styleUrl: './results-page.scss',
})
export class ResultsPage {
  private readonly janus = inject(JanusService);

  /** The job whose results are shown, bound from the route. */
  readonly jobId = input.required<string>();

  /** The job, once it has been read. */
  protected readonly job = signal<JobDto | null>(null);
  /** Why the results cannot be shown, when that happens. */
  protected readonly error = signal<string | null>(null);

  /** The current sort, empty until the user clicks a header. */
  protected readonly sort = signal<Sort | null>(null);

  /**
   * The groups of columns, with the legacy defaults: only the final scores are shown at first.
   */
  protected readonly groups = signal<ColumnGroup[]>([
    { key: 'pbt', label: 'PBT', shown: false },
    { key: 'cmr', label: 'CMR', shown: false },
    { key: 'ed', label: 'ED', shown: false },
    { key: 'partial', label: 'Partial scores', shown: false },
    { key: 'final', label: 'Final scores', shown: true },
  ]);

  /** The results, sorted as the user asked. */
  protected readonly rows = computed(() => {
    const results = this.job()?.results ?? [];
    const sort = this.sort();
    if (!sort || sort.direction === '') {
      return results;
    }
    const factor = sort.direction === 'asc' ? 1 : -1;
    return [...results].sort((a, b) => factor * compare(value(a, sort.active), value(b, sort.active)));
  });

  /** The columns to render, in the legacy order, according to the active groups. */
  protected readonly columns = computed(() => {
    const shown = (key: string) => this.groups().find((group) => group.key === key)?.shown ?? false;
    const partial = shown('partial');

    const columns = ['detail', 'num', 'metabolite', 'id', 'smiles', 'labels'];
    if (shown('pbt')) {
      columns.push('p', 'pRel', ...(partial ? ['pScore'] : []));
      columns.push('b', 'bRel', ...(partial ? ['bScore'] : []));
      columns.push('t', 'tRel', ...(partial ? ['tScore'] : []));
    }
    if (shown('cmr')) {
      columns.push('c', 'cRel', ...(partial ? ['cScore'] : []));
      columns.push('m', 'mRel', ...(partial ? ['mScore'] : []));
      columns.push('r', 'rRel', ...(partial ? ['rScore'] : []));
    }
    if (shown('ed')) {
      columns.push('ed', 'edRel', ...(partial ? ['edScore'] : []));
    }
    if (shown('final')) {
      columns.push('vpvb', 'svhc', 'pbt');
    }
    return columns;
  });

  constructor() {
    effect(() => {
      void this.load(this.jobId());
    });
  }

  /**
   * Shows or hides a group of columns.
   *
   * @param key the group
   * @param shown whether it is now shown
   */
  protected toggleGroup(key: string, shown: boolean): void {
    this.groups.update((groups) =>
      groups.map((group) => (group.key === key ? { ...group, shown } : group)),
    );
  }

  /** Downloads the table as the tab separated file the legacy application produced. */
  protected exportTsv(): void {
    const blob = new Blob([resultsToTsv(this.job()?.results ?? [])], {
      type: 'text/tab-separated-values',
    });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'janus-result.txt';
    link.click();
    URL.revokeObjectURL(url);
  }

  // --- what each cell shows ---------------------------------------------------------------

  protected labels(row: MoleculeResultDto): { letter: string; severity: Severity }[] {
    return severityLabels(row);
  }

  protected truncate(text: string | undefined): string {
    if (!text) {
      return MISSING;
    }
    return text.length <= TRUNCATE_AT ? text : `${text.slice(0, TRUNCATE_AT - 5)} [..]`;
  }

  protected persistence = (row: MoleculeResultDto) => formatText(row.persistence?.predictionClass);
  protected bioaccumulation = (row: MoleculeResultDto) =>
    formatBioaccumulation(row.bioaccumulation?.prediction);
  protected toxicity = (row: MoleculeResultDto) => formatToxicity(row.toxicity?.prediction);
  protected carcinogenicity = (row: MoleculeResultDto) => carcinogenicityLabel(row.carcinogenicity);
  protected mutagenicity = (row: MoleculeResultDto) => mutagenicityLabel(row.mutagenicity);
  protected reproToxicity = (row: MoleculeResultDto) => reproToxicityLabel(row.reproToxicity);
  protected endocrine = (row: MoleculeResultDto) => endocrineLabel(row.endocrine);

  protected reliability = formatReliability;
  protected score = formatScore;
  protected index = formatNumber;
  protected persistenceIndex = formatPersistence;

  private async load(jobId: string): Promise<void> {
    try {
      const job = await this.janus.job(jobId);
      this.job.set(job);
      this.error.set(
        job.status === 'COMPLETED' ? null : `The job is ${job.status}, there are no results.`,
      );
    } catch {
      this.error.set('This job is no longer available. Results are kept for one hour.');
    }
  }
}

/**
 * @param row a result
 * @param column the column being sorted
 * @returns the value to sort that column on; numbers stay numbers, so the order is numeric and
 *     not lexicographic as it was in the legacy page
 */
function value(row: MoleculeResultDto, column: string): number | string | null | undefined {
  switch (column) {
    case 'num':
      return row.num;
    case 'id':
      return row.id;
    case 'smiles':
      return row.smiles;
    case 'p':
      return row.persistence?.prediction;
    case 'pRel':
      return row.persistence?.reliability;
    case 'pScore':
      return row.persistence?.score;
    case 'b':
      return row.bioaccumulation?.prediction;
    case 'bRel':
      return row.bioaccumulation?.reliability;
    case 'bScore':
      return row.bioaccumulation?.score;
    case 't':
      return row.toxicity?.prediction;
    case 'tRel':
      return row.toxicity?.reliability;
    case 'tScore':
      return row.toxicity?.score;
    case 'c':
      return row.carcinogenicity?.prediction;
    case 'cRel':
      return row.carcinogenicity?.reliability;
    case 'cScore':
      return row.carcinogenicity?.score;
    case 'm':
      return row.mutagenicity?.prediction;
    case 'mRel':
      return row.mutagenicity?.reliability;
    case 'mScore':
      return row.mutagenicity?.score;
    case 'r':
      return row.reproToxicity?.prediction;
    case 'rRel':
      return row.reproToxicity?.reliability;
    case 'rScore':
      return row.reproToxicity?.score;
    case 'ed':
      return row.endocrine?.prediction;
    case 'edRel':
      return row.endocrine?.reliability;
    case 'edScore':
      return row.endocrine?.score;
    case 'vpvb':
      return row.scoreVPvB;
    case 'svhc':
      return row.scoreSVHC;
    case 'pbt':
      return row.scorePBT;
    default:
      return undefined;
  }
}

function compare(a: number | string | null | undefined, b: number | string | null | undefined): number {
  // missing values always sort last, whatever the direction
  if (a === null || a === undefined) {
    return b === null || b === undefined ? 0 : 1;
  }
  if (b === null || b === undefined) {
    return -1;
  }
  if (typeof a === 'number' && typeof b === 'number') {
    return a - b;
  }
  return String(a).localeCompare(String(b));
}
