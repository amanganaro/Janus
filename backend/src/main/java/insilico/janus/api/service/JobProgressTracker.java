package insilico.janus.api.service;

import insilico.janus.api.dto.JobProgressDto;
import insilico.janus.progress.JanusProgressListener;

/**
 * Turns the progress callbacks of the engine into the state of a job and into SSE events.
 *
 * <p>The engine announces a phase with {@code SendMessage}, then calls {@code UpdateProgress}
 * once per molecule; {@code SetDatasetSize} is called before the first phase and again after
 * the metabolism step. The counter is therefore reset on every phase change, and the progress
 * is only meaningful relative to the current phase.</p>
 */
class JobProgressTracker implements JanusProgressListener {

    private final JanusJob job;
    private final JobEventPublisher publisher;

    private String phase = "Initialization";
    private int totalMolecules;
    private int processedMolecules;

    JobProgressTracker(JanusJob job, JobEventPublisher publisher) {
        this.job = job;
        this.publisher = publisher;
        publishProgress();
    }

    @Override
    public void SendMessage(String msg) {
        phase = msg;
        processedMolecules = 0;
        publishProgress();
    }

    @Override
    public void UpdateProgress() {
        processedMolecules++;
        publishProgress();
    }

    @Override
    public void SetDatasetSize(int size) {
        totalMolecules = size;
        processedMolecules = 0;
        publishProgress();
    }

    @Override
    public void SendLogMessage(String msg) {
        job.addLogMessage(msg);
        publisher.publish(job.getId(), "log", job.toDto(false));
    }

    private void publishProgress() {
        job.setProgress(new JobProgressDto(phase, processedMolecules, totalMolecules));
        publisher.publish(job.getId(), "progress", job.toDto(false));
    }
}
