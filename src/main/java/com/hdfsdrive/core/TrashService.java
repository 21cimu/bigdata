package com.hdfsdrive.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Simple persistent trash metadata service.
 * Stores a JSON array of entries: { path, isDirectory, name, deletedAt }
 */
public class TrashService {
    private final File storeFile;
    private final ObjectMapper mapper = new ObjectMapper();

    public static class Entry {
        public String path;
        public boolean isDirectory;
        public String name;
        public long deletedAt;
        // expireAt: epoch millis when this trash entry should be permanently purged.
        // 0 means not yet set (backwards compatibility) and will be computed from deletedAt + default retention.
        public long expireAt;

        public Entry() {}

        public Entry(String path, boolean isDirectory, String name, long deletedAt) {
            this.path = path;
            this.isDirectory = isDirectory;
            this.name = name;
            this.deletedAt = deletedAt;
            this.expireAt = 0L; // will be computed lazily
        }
    }

    public TrashService(String storePath) throws IOException {
        this.storeFile = new File(storePath);
        File parent = this.storeFile.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        if (!this.storeFile.exists()) {
            // create empty list
            mapper.writeValue(this.storeFile, new ArrayList<Entry>());
        }
    }

    private synchronized List<Entry> readAll() throws IOException {
        return mapper.readValue(storeFile, new TypeReference<List<Entry>>(){});
    }

    private synchronized void writeAll(List<Entry> entries) throws IOException {
        mapper.writeValue(storeFile, entries);
    }

    public synchronized void add(String path, boolean isDirectory) throws IOException {
        add(path, isDirectory, 0L);
    }

    /**
     * Add a trash entry with optional explicit expireAt (epoch millis). If expireAt==0 it will be computed later
     * using deletedAt + default retention when purging.
     */
    public synchronized void add(String path, boolean isDirectory, long expireAt) throws IOException {
        List<Entry> entries = readAll();
        // avoid duplicates
        for (Entry e : entries) {
            if (e.path.equals(path)) return;
        }
        String name = path.substring(path.lastIndexOf('/') + 1);
        if (name.isEmpty()) name = "/";
        Entry ne = new Entry(path, isDirectory, name, System.currentTimeMillis());
        ne.expireAt = expireAt;
        entries.add(ne);
        writeAll(entries);
    }

    /**
     * Purge expired trash entries. For each entry whose expireAt (or deletedAt + defaultRetention) is <= now,
     * attempt to permanently delete it from the given HdfsService and remove it from the metadata list.
     * Returns list of paths that were purged.
     */
    public synchronized List<String> purgeExpired(HdfsService hdfsService, long defaultRetentionMillis) throws IOException {
        List<Entry> entries = readAll();
        long now = System.currentTimeMillis();
        boolean changed = false;
        List<String> purged = new ArrayList<>();
        Iterator<Entry> it = entries.iterator();
        while (it.hasNext()) {
            Entry e = it.next();
            long expiration = e.expireAt > 0 ? e.expireAt : (e.deletedAt + defaultRetentionMillis);
            if (expiration <= now) {
                // attempt to remove from HDFS (best-effort)
                try {
                    // attempt permanent delete; if entry is directory, try recursive
                    // attempt permanent delete; if entry is directory, try recursive
                    try {
                        hdfsService.delete(e.path, true);
                    } catch (Exception ioe) {
                        // ignore HDFS delete failure but still remove metadata to avoid accumulating stale entries
                    }
                    // remove metadata regardless of HDFS deletion success to avoid reprocessing
                    it.remove();
                    changed = true;
                    purged.add(e.path);
                } catch (Exception ex) {
                    // on unexpected errors, skip this entry and continue
                    ex.printStackTrace();
                }
            }
        }
        if (changed) writeAll(entries);
        return purged;
    }

    /**
     * Convenience overload with default retention of 30 days.
     */
    public synchronized List<String> purgeExpired(HdfsService hdfsService) throws IOException {
        long thirtyDays = 30L * 24L * 60L * 60L * 1000L;
        return purgeExpired(hdfsService, thirtyDays);
    }

    public synchronized List<Entry> list() throws IOException {
        return readAll();
    }

    public synchronized boolean remove(String path) throws IOException {
        List<Entry> entries = readAll();
        Iterator<Entry> it = entries.iterator();
        boolean found = false;
        while (it.hasNext()) {
            Entry e = it.next();
            if (e.path.equals(path)) {
                it.remove();
                found = true;
            }
        }
        if (found) writeAll(entries);
        return found;
    }

    public synchronized boolean contains(String path) throws IOException {
        List<Entry> entries = readAll();
        for (Entry e : entries) if (e.path.equals(path)) return true;
        return false;
    }
}