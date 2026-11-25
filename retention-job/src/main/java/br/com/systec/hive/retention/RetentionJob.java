package br.com.systec.hive.retention;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Orquestracao: selecionar roots vencidos -> agrupar por traceId -> DELETE em lotes -> auditar. */
public final class RetentionJob {
    private final RetentionConfig config;
    private final LangfuseClient client;

    public RetentionJob(RetentionConfig config, LangfuseClient client) {
        this.config = config;
        this.client = client;
    }

    public int run(PrintStream out) {
        Result result = execute();
        Audit.write(out, config, result);
        return result.exit();
    }

    private Result execute() {
        List<String> errors = new ArrayList<>();
        List<String> traceIds = new ArrayList<>();
        int[] submitted = {0};
        int exit = 0;
        try (FileChannel channel = FileChannel.open(config.lockFile(), StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            FileLock lock;
            try {
                lock = channel.tryLock();
            } catch (OverlappingFileLockException e) {
                lock = null;
            }
            if (lock == null) {
                errors.add("lock_unavailable");
                return new Result(0, 0, traceIds, errors, 2);
            }
            Instant cutoff = config.cutoff();
            List<Observation> rows = client.readExpiredRoots(config.fromStartTime(), cutoff);
            LinkedHashSet<String> unique = new LinkedHashSet<>();
            for (Observation row : rows) {
                if (!row.isRootObservation()) continue;
                if (!Instant.parse(row.startTime()).isBefore(cutoff)) continue;
                unique.add(row.traceId());
            }
            List<String> candidates = new ArrayList<>(unique);
            for (int from = 0; from < candidates.size(); from += LangfuseClient.PAGE_LIMIT) {
                List<String> batch = candidates.subList(from, Math.min(from + LangfuseClient.PAGE_LIMIT, candidates.size()));
                traceIds.addAll(batch);
                submitted[0] += batch.size(); // submetido != excluido (submissao pode falhar)
                try {
                    client.deleteTraces(batch);
                } catch (HttpFailure e) {
                    errors.add("delete_batch_http_" + e.status());
                    exit = 1;
                }
            }
            return new Result(candidates.size(), submitted[0], traceIds, errors, exit);
        } catch (HttpFailure e) {
            errors.add("read_http_" + e.status());
            return new Result(0, 0, traceIds, errors, 1);
        } catch (IOException e) {
            errors.add("io_failure");
            return new Result(0, 0, traceIds, errors, 1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            errors.add("interrupted");
            return new Result(0, 0, traceIds, errors, 1);
        }
    }
}
