import { Injectable, inject, signal } from '@angular/core';
import { Api, HealthDto, JanusJobRequest, JobDto, MetadataDto } from '../api';
import { cancelJob, getHealth, getJob, getMetadata, submitJob } from '../api';

/**
 * Access to the Janus backend.
 *
 * It wraps the generated client (`src/app/api`, regenerated from `api/openapi.json` with
 * `npm run api`) and caches the two pieces of information every page needs: whether the engine
 * is ready and what it can calculate.
 */
@Injectable({ providedIn: 'root' })
export class JanusService {
  private readonly api = inject(Api);

  /** Whether the engine finished loading its models; `null` until the first answer. */
  readonly health = signal<HealthDto | null>(null);

  /** The workflows and the experimental keys the backend accepts. */
  readonly metadata = signal<MetadataDto | null>(null);

  /**
   * Asks the backend whether the engine is ready. Called repeatedly by the input page while the
   * models load.
   *
   * @returns the health of the backend, or a failure description when it cannot be reached
   */
  async refreshHealth(): Promise<HealthDto> {
    try {
      const health = await this.api.invoke(getHealth, {});
      this.health.set(health);
      return health;
    } catch {
      const unreachable: HealthDto = { ready: false, error: 'The backend is not reachable.' };
      this.health.set(unreachable);
      return unreachable;
    }
  }

  /**
   * Loads the metadata once and keeps it.
   *
   * @returns the workflows and the experimental keys
   */
  async loadMetadata(): Promise<MetadataDto> {
    const cached = this.metadata();
    if (cached) {
      return cached;
    }
    const metadata = await this.api.invoke(getMetadata, {});
    this.metadata.set(metadata);
    return metadata;
  }

  /**
   * Queues a calculation.
   *
   * @param request the molecules and the options
   * @returns the newly created job
   */
  submit(request: JanusJobRequest): Promise<JobDto> {
    return this.api.invoke(submitJob, { body: request });
  }

  /**
   * @param jobId the identifier of the job
   * @returns the state of the job, with its results once it has completed
   */
  job(jobId: string): Promise<JobDto> {
    return this.api.invoke(getJob, { id: jobId });
  }

  /**
   * Cancels a job and forgets it.
   *
   * @param jobId the identifier of the job
   */
  cancel(jobId: string): Promise<void> {
    return this.api.invoke(cancelJob, { id: jobId });
  }
}
