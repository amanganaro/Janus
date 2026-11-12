import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { JobDto, MoleculeResultDto, WorkflowResultDto } from '../../api';
import { JanusService } from '../../core/janus.service';
import {
  Severity,
  bioaccumulationSeverity,
  formatBioaccumulation,
  formatPersistence,
  formatReliability,
  formatText,
  formatToxicity,
  hazardSeverity,
  persistenceSeverity,
  toxicitySeverity,
} from '../../core/result-format';

/** One of the seven blocks of the detail page. */
interface WorkflowSection {
  /** The title of the card, as in the legacy detail page. */
  title: string;
  /** The label of the prediction, with its unit when there is one. */
  predictionLabel: string;
  /** What to show as the overall prediction. */
  prediction: string;
  /** The reliability of the assessment. */
  reliability: string;
  /** The intermediate values, in the order the workflow produced them. */
  values: { name?: string; value?: string }[];
}

/** One tile of the summary block. */
interface SummaryTile {
  label: string;
  value: string;
  reliability: string;
  severity: Severity;
}

/**
 * The detail of one molecule: the same sequence of cards as the legacy `details.html`.
 */
@Component({
  selector: 'app-molecule-detail-page',
  imports: [MatButtonModule, MatCardModule, MatIconModule, RouterLink],
  templateUrl: './molecule-detail-page.html',
  styleUrl: './molecule-detail-page.scss',
})
export class MoleculeDetailPage {
  private readonly janus = inject(JanusService);

  /** The job the molecule belongs to, bound from the route. */
  readonly jobId = input.required<string>();
  /** The position of the molecule within the job, bound from the route. */
  readonly num = input.required<string>();

  /** Why the molecule cannot be shown, when that happens. */
  protected readonly error = signal<string | null>(null);

  private readonly job = signal<JobDto | null>(null);

  /** The molecule being shown. */
  protected readonly result = computed<MoleculeResultDto | null>(() => {
    const wanted = Number(this.num());
    return this.job()?.results?.find((row) => row.num === wanted) ?? null;
  });

  /** The seven coloured tiles of the SUMMARY card. */
  protected readonly summary = computed<SummaryTile[]>(() => {
    const result = this.result();
    if (!result) {
      return [];
    }
    return [
      tile('Persistence', formatText(result.persistence?.predictionClass), result.persistence,
        persistenceSeverity(result.persistence?.prediction)),
      tile('Bioaccumulation', formatBioaccumulation(result.bioaccumulation?.prediction),
        result.bioaccumulation, bioaccumulationSeverity(result.bioaccumulation?.prediction)),
      tile('Toxicity', formatToxicity(result.toxicity?.prediction), result.toxicity,
        toxicitySeverity(result.toxicity?.prediction)),
      // as in the legacy, carcinogenicity is coloured on its score and the other three on their
      // assessment
      tile('Carcinogenicity', formatText(result.carcinogenicity?.predictionClass),
        result.carcinogenicity, hazardSeverity(result.carcinogenicity?.score)),
      tile('Mutagenicity', formatText(result.mutagenicity?.predictionClass), result.mutagenicity,
        hazardSeverity(result.mutagenicity?.prediction)),
      tile('Reproductive toxicity', formatText(result.reproToxicity?.predictionClass),
        result.reproToxicity, hazardSeverity(result.reproToxicity?.prediction)),
      tile('Endocrine Disruptor', formatText(result.endocrine?.predictionClass), result.endocrine,
        hazardSeverity(result.endocrine?.prediction)),
    ];
  });

  /** The seven detail cards, with the titles of the legacy page. */
  protected readonly sections = computed<WorkflowSection[]>(() => {
    const result = this.result();
    if (!result) {
      return [];
    }
    return [
      section('Persistence assessment', 'Overall prediction', result.persistence,
        `${formatText(result.persistence?.predictionClass)} (${formatPersistence(result.persistence?.prediction)})`),
      section('BCF assessment', 'Overall prediction [log units]', result.bioaccumulation,
        formatBioaccumulation(result.bioaccumulation?.prediction)),
      section('Toxicity assessment', 'Overall prediction [mg/l]', result.toxicity,
        formatToxicity(result.toxicity?.prediction)),
      section('Carcinogenicity assessment', 'Overall prediction', result.carcinogenicity,
        formatText(result.carcinogenicity?.predictionClass)),
      section('Mutagenicity assessment', 'Overall prediction', result.mutagenicity,
        formatText(result.mutagenicity?.predictionClass)),
      section('Reproductive Toxicity assessment', 'Overall prediction', result.reproToxicity,
        formatText(result.reproToxicity?.predictionClass)),
      section('Endocrine Disruptor assessment', 'Overall prediction', result.endocrine,
        formatText(result.endocrine?.predictionClass)),
    ];
  });

  constructor() {
    effect(() => {
      void this.load(this.jobId());
    });

    effect(() => {
      if (this.job() !== null && this.result() === null) {
        this.error.set(`There is no molecule number ${this.num()} in this job.`);
      }
    });
  }

  private async load(jobId: string): Promise<void> {
    try {
      this.job.set(await this.janus.job(jobId));
    } catch {
      this.error.set('This job is no longer available. Results are kept for one hour.');
    }
  }
}

function tile(
  label: string,
  value: string,
  result: WorkflowResultDto | undefined,
  severity: Severity,
): SummaryTile {
  return { label, value, reliability: formatReliability(result?.reliability), severity };
}

function section(
  title: string,
  predictionLabel: string,
  result: WorkflowResultDto | undefined,
  prediction: string,
): WorkflowSection {
  return {
    title,
    predictionLabel,
    prediction,
    reliability: formatReliability(result?.reliability),
    values: result?.additionalValues ?? [],
  };
}
