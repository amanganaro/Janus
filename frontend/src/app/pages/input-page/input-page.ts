import { Component, OnDestroy, inject, signal } from '@angular/core';
import { KeyValuePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { JanusJobRequest, MoleculeRequestDto, WorkflowInfo } from '../../api';
import { JanusService } from '../../core/janus.service';
import { parseSmilesInput } from '../../core/smiles-input.parser';

/** How often the page asks the backend whether the engine finished loading. */
const HEALTH_POLL_MS = 3000;

/**
 * The first page: the molecules to assess and the options of the calculation.
 */
@Component({
  selector: 'app-input-page',
  imports: [
    KeyValuePipe,
    FormsModule,
    MatButtonModule,
    MatCardModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './input-page.html',
  styleUrl: './input-page.scss',
})
export class InputPage implements OnDestroy {
  private readonly janus = inject(JanusService);
  private readonly router = inject(Router);

  /** The raw text of the uploaded file or of the textarea. */
  protected readonly text = signal('');
  /** The name of the uploaded file, when the molecules come from one. */
  protected readonly fileName = signal<string | null>(null);

  /** The molecules read from the input. */
  protected readonly molecules = signal<MoleculeRequestDto[]>([]);
  /** Problems found while reading the input, which do not prevent the submission. */
  protected readonly warnings = signal<string[]>([]);

  /** The workflows offered by the backend. */
  protected readonly workflows = signal<WorkflowInfo[]>([]);
  /** The workflows the user picked. */
  protected readonly selectedWorkflows = signal<Set<string>>(new Set());
  /** Whether to generate and assess the metabolites. */
  protected readonly metabolism = signal(false);

  /** Whether the engine finished loading its models. */
  protected readonly ready = signal(false);
  /** Why the backend cannot be used, when that is the case. */
  protected readonly backendError = signal<string | null>(null);
  /** Whether a submission is in flight. */
  protected readonly submitting = signal(false);
  /** The error returned by the last submission. */
  protected readonly submitError = signal<string | null>(null);

  private readonly experimentalKeys = signal<string[]>([]);
  private healthTimer: ReturnType<typeof setInterval> | null = null;

  constructor() {
    void this.loadMetadata();
    void this.checkHealth();
    this.healthTimer = setInterval(() => void this.checkHealth(), HEALTH_POLL_MS);
  }

  ngOnDestroy(): void {
    if (this.healthTimer !== null) {
      clearInterval(this.healthTimer);
    }
  }

  /** @returns whether the calculation can be submitted */
  protected canSubmit(): boolean {
    return (
      this.ready() &&
      !this.submitting() &&
      this.molecules().length > 0 &&
      this.selectedWorkflows().size > 0
    );
  }

  /**
   * Reads the file the user picked.
   *
   * @param event the change event of the file input
   */
  protected onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) {
      return;
    }
    const reader = new FileReader();
    reader.onload = () => {
      this.fileName.set(file.name);
      this.text.set(String(reader.result ?? ''));
      this.parse();
    };
    reader.readAsText(file);
  }

  /**
   * Re-reads the input after the textarea changed.
   *
   * @param value the text now in the textarea
   */
  protected onTextChanged(value: string): void {
    this.fileName.set(null);
    this.text.set(value);
    this.parse();
  }

  /**
   * Adds or removes a workflow from the selection.
   *
   * @param id the identifier of the workflow
   * @param selected whether it is now selected
   */
  protected toggleWorkflow(id: string, selected: boolean): void {
    const next = new Set(this.selectedWorkflows());
    if (selected) {
      next.add(id);
    } else {
      next.delete(id);
    }
    this.selectedWorkflows.set(next);
  }

  /**
   * @param id the identifier of a workflow
   * @returns whether it is selected
   */
  protected isSelected(id: string): boolean {
    return this.selectedWorkflows().has(id);
  }

  /** Sends the calculation to the backend and moves to the progress page. */
  protected async submit(): Promise<void> {
    this.submitting.set(true);
    this.submitError.set(null);

    const request: JanusJobRequest = {
      molecules: this.molecules(),
      metabolism: this.metabolism(),
      workflows: [...this.selectedWorkflows()] as JanusJobRequest['workflows'],
    };

    try {
      const job = await this.janus.submit(request);
      await this.router.navigate(['/jobs', job.jobId]);
    } catch (error: unknown) {
      this.submitError.set(describeError(error));
    } finally {
      this.submitting.set(false);
    }
  }

  private parse(): void {
    // a previous failure says nothing about the input that is being typed now
    this.submitError.set(null);
    const parsed = parseSmilesInput(this.text(), this.experimentalKeys());
    this.molecules.set(parsed.molecules);
    this.warnings.set(parsed.warnings);
  }

  private async loadMetadata(): Promise<void> {
    try {
      const metadata = await this.janus.loadMetadata();
      this.workflows.set(metadata.workflows ?? []);
      this.experimentalKeys.set((metadata.experimentalKeys ?? []).map((key) => key.key ?? ''));
      // everything is selected by default, as in the legacy application
      this.selectedWorkflows.set(
        new Set((metadata.workflows ?? []).map((workflow) => workflow.id ?? '')),
      );
      this.parse();
    } catch {
      this.backendError.set('Unable to read the capabilities of the backend.');
    }
  }

  private async checkHealth(): Promise<void> {
    const health = await this.janus.refreshHealth();
    this.ready.set(health.ready === true);
    this.backendError.set(health.ready ? null : (health.error ?? null));
    if (health.ready && this.healthTimer !== null) {
      clearInterval(this.healthTimer);
      this.healthTimer = null;
      if (this.workflows().length === 0) {
        void this.loadMetadata();
      }
    }
  }
}

/**
 * @param error whatever the HTTP client threw
 * @returns the message of the backend when there is one, a generic message otherwise
 */
export function describeError(error: unknown): string {
  const body = (error as { error?: { error?: string; details?: string[] } })?.error;
  if (body?.error) {
    const details = body.details?.length ? ` ${body.details.join(' ')}` : '';
    return `${body.error}.${details}`;
  }
  return 'The calculation could not be submitted.';
}
