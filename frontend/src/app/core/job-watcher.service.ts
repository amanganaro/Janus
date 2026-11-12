import { Injectable, Signal, inject, signal } from '@angular/core';
import { JobDto } from '../api';
import { JanusService } from './janus.service';

/** The job statuses after which nothing else happens. */
const TERMINAL = ['COMPLETED', 'FAILED', 'CANCELLED'];

/** How often the fallback asks the backend for the state of the job. */
const POLL_INTERVAL_MS = 2000;

/** A running subscription to the progress of one job. */
export interface JobWatcher {
  /** The last known state of the job. */
  readonly state: Signal<JobDto | null>;
  /** Why the job could not be followed, if that happened. */
  readonly error: Signal<string | null>;
  /** Stops following the job. Always call it when leaving the page. */
  stop(): void;
}

/**
 * Follows the progress of a job.
 *
 * The backend publishes it as server-sent events, which the generated client cannot consume —
 * `HttpClient` has no streaming — so this is the one place that talks to the API by hand. The
 * payload is still the generated {@link JobDto}, and if the stream cannot be opened or drops,
 * the watcher falls back to polling: the result must never depend on the event stream.
 */
@Injectable({ providedIn: 'root' })
export class JobWatcherService {
  private readonly janus = inject(JanusService);

  /**
   * Starts following a job.
   *
   * @param jobId the identifier of the job
   * @returns the watcher; call `stop()` on it when the page is left
   */
  watch(jobId: string): JobWatcher {
    const state = signal<JobDto | null>(null);
    const error = signal<string | null>(null);

    let source: EventSource | null = null;
    let pollTimer: ReturnType<typeof setInterval> | null = null;
    let stopped = false;

    const stop = () => {
      stopped = true;
      source?.close();
      source = null;
      if (pollTimer !== null) {
        clearInterval(pollTimer);
        pollTimer = null;
      }
    };

    const accept = (job: JobDto) => {
      if (stopped) {
        return;
      }
      state.set(job);
      if (job.status && TERMINAL.includes(job.status)) {
        stop();
      }
    };

    const poll = async () => {
      try {
        accept(await this.janus.job(jobId));
      } catch {
        error.set('The job is no longer available.');
        stop();
      }
    };

    const startPolling = () => {
      if (stopped || pollTimer !== null) {
        return;
      }
      void poll();
      pollTimer = setInterval(() => void poll(), POLL_INTERVAL_MS);
    };

    // first state straight away, so the page is never empty while the stream opens
    void poll();

    try {
      source = new EventSource(`/api/jobs/${encodeURIComponent(jobId)}/events`);
      // the backend names its events, so there is nothing to read from onmessage
      for (const name of ['progress', 'log', 'status']) {
        source.addEventListener(name, (event) => {
          try {
            accept(JSON.parse((event as MessageEvent).data) as JobDto);
          } catch {
            // a malformed event is not worth failing over: the next one, or the poll, will do
          }
        });
      }
      source.onerror = () => {
        source?.close();
        source = null;
        // the stream also closes normally when the job ends; polling settles which one it was
        startPolling();
      };
    } catch {
      startPolling();
    }

    return { state, error, stop };
  }
}
