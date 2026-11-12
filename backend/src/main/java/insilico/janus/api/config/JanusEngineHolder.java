package insilico.janus.api.config;

import insilico.janus.JanusEngine;
import jakarta.annotation.PostConstruct;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Owns the single {@link JanusEngine} instance of the service.
 *
 * <p>Building the engine loads every QSAR model of the seven workflows into memory, which takes
 * a long time, so it happens once, in the background, while the HTTP port is already open. A
 * client can poll {@code GET /api/health} to know when the engine is ready; a job submitted
 * before that simply waits in the queue.</p>
 *
 * <p>The engine is <strong>not thread-safe</strong>: the metabolism flag, the progress listener
 * and the per-molecule state of the workflows are all shared mutable fields. Only the single
 * worker thread of the job service may touch it.</p>
 */
@Component
public class JanusEngineHolder {

    private static final Logger LOGGER = LoggerFactory.getLogger(JanusEngineHolder.class);

    /**
     * Whether to load the models at startup. Only the tests that do not calculate anything
     * turn it off, to keep the context light.
     */
    @Value("${janus.engine.load-models:true}")
    private boolean loadModels;

    private final CountDownLatch initialized = new CountDownLatch(1);

    private volatile JanusEngine engine;
    private volatile String error;

    /**
     * Starts loading the models in a daemon thread.
     */
    @PostConstruct
    public void startInitialization() {
        if (!loadModels) {
            error = "The models were not loaded (janus.engine.load-models is false)";
            LOGGER.warn("Starting without the Janus models: no calculation is possible");
            initialized.countDown();
            return;
        }
        Thread thread = new Thread(this::initialize, "janus-engine-init");
        thread.setDaemon(true);
        thread.start();
    }

    private void initialize() {
        long start = System.currentTimeMillis();
        LOGGER.info("Loading the Janus models, this takes a while...");
        try {
            // BatchByMoleculeMode = true: the workflows are built once and reused by every job
            engine = new JanusEngine(true);
            LOGGER.info("Janus engine ready in {} ms", System.currentTimeMillis() - start);
        } catch (Throwable e) {
            error = e.getMessage() == null ? e.toString() : e.getMessage();
            LOGGER.error("Unable to initialize the Janus engine", e);
        } finally {
            initialized.countDown();
        }
    }

    /**
     * @return whether the engine finished loading successfully
     */
    public boolean isReady() {
        return engine != null;
    }

    /**
     * @return the reason why the initialization failed, or {@code null} if it did not
     */
    public String getError() {
        return error;
    }

    /**
     * Returns the engine, waiting for the initialization to finish.
     *
     * @return the engine
     * @throws IllegalStateException if the initialization failed
     * @throws InterruptedException  if the calling thread is interrupted while waiting
     */
    public JanusEngine awaitEngine() throws InterruptedException {
        initialized.await();
        if (engine == null) {
            throw new IllegalStateException("The Janus engine could not be initialized: " + error);
        }
        return engine;
    }

    /**
     * Waits a limited amount of time for the engine to be ready. Used by the tests and by the
     * health endpoint, never by the calculation itself.
     *
     * @param timeout the maximum time to wait
     * @param unit    the unit of the timeout
     * @return whether the initialization finished within the timeout
     * @throws InterruptedException if the calling thread is interrupted while waiting
     */
    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        return initialized.await(timeout, unit);
    }
}
