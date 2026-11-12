import { Component, OnDestroy, computed, effect, inject, input, signal } from '@angular/core';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { JobWatcher, JobWatcherService } from '../../core/job-watcher.service';
import { JanusService } from '../../core/janus.service';

/**
 * The page shown while a job is queued or running.
 *
 * It is a page and not an overlay on purpose: the job id is in the URL, so the wait survives a
 * reload and the link can be reopened later.
 */
@Component({
  selector: 'app-running-page',
  imports: [MatButtonModule, MatCardModule, MatProgressBarModule],
  templateUrl: './running-page.html',
  styleUrl: './running-page.scss',
})
export class RunningPage implements OnDestroy {
  private readonly watcherService = inject(JobWatcherService);
  private readonly janus = inject(JanusService);
  private readonly router = inject(Router);

  /** The job to follow, bound from the route. */
  readonly jobId = input.required<string>();

  /** Whether the cancellation is in flight. */
  protected readonly cancelling = signal(false);

  private watcher: JobWatcher | null = null;
  private readonly currentWatcher = signal<JobWatcher | null>(null);

  /** The last known state of the job. */
  protected readonly job = computed(() => this.currentWatcher()?.state() ?? null);
  /** Why the job cannot be followed, when that happens. */
  protected readonly error = computed(() => this.currentWatcher()?.error() ?? null);

  /** The progress of the current phase, as a percentage. */
  protected readonly percentage = computed(() => {
    const progress = this.job()?.progress;
    if (!progress?.totalMolecules) {
      return 0;
    }
    return Math.min(100, (100 * (progress.processedMolecules ?? 0)) / progress.totalMolecules);
  });

  constructor() {
    effect(() => {
      const jobId = this.jobId();
      this.watcher?.stop();
      this.watcher = this.watcherService.watch(jobId);
      this.currentWatcher.set(this.watcher);
    });

    effect(() => {
      const status = this.job()?.status;
      if (status === 'COMPLETED') {
        void this.router.navigate(['/jobs', this.jobId(), 'results']);
      }
    });
  }

  ngOnDestroy(): void {
    this.watcher?.stop();
  }

  /** Cancels the job and goes back to the input page. */
  protected async cancel(): Promise<void> {
    this.cancelling.set(true);
    try {
      await this.janus.cancel(this.jobId());
    } catch {
      // the job may have finished in the meantime: leaving the page is still the right outcome
    } finally {
      this.cancelling.set(false);
      this.watcher?.stop();
      await this.router.navigate(['/']);
    }
  }

  /** Goes back to the input page. */
  protected async backToInput(): Promise<void> {
    await this.router.navigate(['/']);
  }
}
