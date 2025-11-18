package com.hdfsdrive.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Simple persistent share metadata service.
 * Stores a JSON array of entries: { id, path, name, createdAt, expireAt }
 */
public class ShareService {
    private final File storeFile;
    private final ObjectMapper mapper = new ObjectMapper();

    public static class Entry {
        public String id;
        public String path;
        public String name;
        public long createdAt;
        public long expireAt; // 0 means never

        public Entry() {}

        public Entry(String id, String path, String name, long createdAt, long expireAt) {
            this.id = id;
            this.path = path;
            this.name = name;
            this.createdAt = createdAt;
            this.expireAt = expireAt;
        }
    }

    public ShareService(String storePath) throws IOException {
        this.storeFile = new File(storePath);
        File parent = this.storeFile.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        if (!this.storeFile.exists()) {
            mapper.writeValue(this.storeFile, new ArrayList<Entry>());
        }
    }

    private synchronized List<Entry> readAll() throws IOException {
        return mapper.readValue(storeFile, new TypeReference<List<Entry>>(){});
    }

    private synchronized void writeAll(List<Entry> entries) throws IOException {
        mapper.writeValue(storeFile, entries);
    }

    public synchronized Entry add(String path, String name, long expireAt) throws IOException {
        List<Entry> entries = readAll();
        String id = UUID.randomUUID().toString();
        Entry e = new Entry(id, path, name, System.currentTimeMillis(), expireAt);
        entries.add(e);
        writeAll(entries);
        return e;
    }

    public synchronized List<Entry> list() throws IOException {
        List<Entry> all = readAll();
        long now = System.currentTimeMillis();
        List<Entry> out = new ArrayList<>();
        for (Entry e : all) {
            if (e.expireAt > 0 && e.expireAt < now) continue; // skip expired
            out.add(e);
        }
        return out;
    }

    public synchronized boolean remove(String id) throws IOException {
        List<Entry> entries = readAll();
        Iterator<Entry> it = entries.iterator();
        boolean removed = false;
        while (it.hasNext()) {
            Entry e = it.next();
            if (e.id.equals(id)) {
                it.remove();
                removed = true;
            }
        }
        if (removed) writeAll(entries);
        return removed;
    }

    public synchronized Entry getById(String id) throws IOException {
        List<Entry> entries = readAll();
        long now = System.currentTimeMillis();
        for (Entry e : entries) {
            if (e.id.equals(id)) {
                if (e.expireAt > 0 && e.expireAt < now) return null;
                return e;
            }
        }
        return null;
    }
}