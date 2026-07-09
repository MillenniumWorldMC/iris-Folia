package art.arcane.iris.client;

import art.arcane.iris.spi.protocol.IrisMessage;

import java.util.concurrent.ConcurrentHashMap;

public final class IrisClientPregenState {
    private final ConcurrentHashMap<Long, IrisMessage.PregenProgress> jobs;
    private volatile Long activeJobId;

    public IrisClientPregenState() {
        this.jobs = new ConcurrentHashMap<>();
        this.activeJobId = null;
    }

    public void onProgress(IrisMessage.PregenProgress progress) {
        jobs.put(progress.jobId(), progress);
        activeJobId = progress.jobId();
    }

    public void onEnd(long jobId) {
        jobs.remove(jobId);
        Long current = activeJobId;
        if (current != null && current == jobId) {
            activeJobId = jobs.keySet().stream().findFirst().orElse(null);
        }
    }

    public IrisMessage.PregenProgress active() {
        Long current = activeJobId;
        if (current == null) {
            return null;
        }
        return jobs.get(current);
    }

    public Long activeJobId() {
        return activeJobId;
    }

    public void clear() {
        jobs.clear();
        activeJobId = null;
    }
}
