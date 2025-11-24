package com.hdfsdrive.core;

import org.apache.commons.io.IOUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.*;
import org.apache.hadoop.fs.permission.FsAction;
import org.apache.hadoop.fs.permission.FsPermission;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Closeable;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight wrapper around Hadoop FileSystem to be used by a personal "HDFS Drive".
 * Provides upload, download, mkdirs, createFile, delete and search operations.
 */
public class HdfsService implements Closeable {
    private final FileSystem fs;

    /**
     * Small value object to expose path, type and basic metadata to callers.
     */
    public static class FileEntry {
        public String path;
        public boolean isDirectory;
        public long size;
        public long modificationTime;

        public FileEntry(String path, boolean isDirectory, long size, long modificationTime) {
            this.path = path;
            this.isDirectory = isDirectory;
            this.size = size;
            this.modificationTime = modificationTime;
        }
    }

    /**
     * Create HdfsService with an explicit HDFS URI and username. If conf is null a default Configuration will be used.
     */
    public HdfsService(String hdfsUri, String user, Configuration conf) throws Exception {
        if (conf == null) conf = new Configuration();
        if (user == null || user.isEmpty()) {
            this.fs = FileSystem.get(new URI(hdfsUri), conf);
        } else {
            this.fs = FileSystem.get(new URI(hdfsUri), conf, user);
        }
    }

    @Override
    public void close() throws IOException {
        if (fs != null) fs.close();
    }

    public boolean mkdirs(String remoteDir) throws IOException {
        return fs.mkdirs(new Path(remoteDir));
    }

    /**
     * Upload local file to HDFS target path. If target is a directory, the file will be placed inside it.
     */
    public void upload(String localPath, String remoteTarget) throws IOException {
        Path src = new Path(localPath);
        Path dst = new Path(remoteTarget);
        fs.copyFromLocalFile(false, true, src, dst);
    }

    /**
     * Download an HDFS file to a local path. If localPath is a directory it will create a file with the same name.
     */
    public void download(String remotePath, String localPath) throws IOException {
        Path src = new Path(remotePath);
        File localFile = new File(localPath);
        if (localFile.isDirectory()) {
            // append filename
            localFile = new File(localFile, src.getName());
        }
        try (FSDataInputStream in = fs.open(src); OutputStream out = new FileOutputStream(localFile)) {
            IOUtils.copyLarge(in, out);
        }
    }

    /**
     * Create a new file on HDFS and write the provided content bytes.
     */
    public void createFile(String remotePath, byte[] content, boolean overwrite) throws IOException {
        Path p = new Path(remotePath);
        try (FSDataOutputStream out = fs.create(p, overwrite)) {
            out.write(content);
        }
    }

    /**
     * Delete a file or directory. If recursive is true directories will be deleted recursively.
     */
    public boolean delete(String remotePath, boolean recursive) throws IOException {
        return fs.delete(new Path(remotePath), recursive);
    }

    /**
     * Move/rename a file or directory within HDFS.
     */
    public boolean move(String srcPath, String dstPath) throws IOException {
        Path src = new Path(srcPath);
        Path dst = new Path(dstPath);
        // ensure parent of dst exists
        Path parent = dst.getParent();
        if (parent != null && !fs.exists(parent)) {
            fs.mkdirs(parent);
        }
        return fs.rename(src, dst);
    }

    /**
     * Copy a file within HDFS. This performs a stream copy from src to dst. Parent of dst will be created if needed.
     */
    public boolean copy(String srcPath, String dstPath) throws IOException {
        Path src = new Path(srcPath);
        Path dst = new Path(dstPath);
        if (!fs.exists(src)) return false;
        // ensure parent of dst exists
        Path parent = dst.getParent();
        if (parent != null && !fs.exists(parent)) {
            fs.mkdirs(parent);
        }
        // If dst exists, overwrite
        try (FSDataInputStream in = fs.open(src); FSDataOutputStream out = fs.create(dst, true)) {
            IOUtils.copyLarge(in, out);
        }
        return true;
    }

    /**
     * Search for files whose name contains the given pattern under startDir (searched recursively).
     * Returns FileEntry objects including size and modification time so callers can present richer UI.
     */
    public List<FileEntry> search(String startDir, String nameContains) throws IOException {
        List<FileEntry> results = new ArrayList<>();
        RemoteIterator<LocatedFileStatus> it = fs.listFiles(new Path(startDir), true);
        while (it.hasNext()) {
            LocatedFileStatus s = it.next();
            if (s.getPath().getName().contains(nameContains)) {
                // return only the FS path (no scheme/authority) so front-end breadcrumb/path handling is correct
                String p = s.getPath().toUri().getPath();
                results.add(new FileEntry(p, s.isDirectory(), s.getLen(), s.getModificationTime()));
            }
        }
        return results;
    }

    /**
     * Search for files under startDir (recursively) whose filename extension matches one of provided extensions.
     * Extensions should be provided without leading dot and matching is case-insensitive.
     * Returns FileEntry objects so callers can read size and mod time.
     */
    public List<FileEntry> searchByExtensions(String startDir, List<String> exts) throws IOException {
        List<FileEntry> results = new ArrayList<>();
        if (exts == null || exts.isEmpty()) return results;
        // normalize extensions to lower-case for comparison
        List<String> lower = new ArrayList<>();
        for (String e : exts) {
            if (e == null) continue;
            lower.add(e.trim().toLowerCase());
        }

        RemoteIterator<LocatedFileStatus> it = fs.listFiles(new Path(startDir), true);
        while (it.hasNext()) {
            LocatedFileStatus s = it.next();
            String name = s.getPath().getName();
            int idx = name.lastIndexOf('.');
            if (idx >= 0 && idx < name.length()-1) {
                String ext = name.substring(idx+1).toLowerCase();
                if (lower.contains(ext)) {
                    results.add(new FileEntry(s.getPath().toUri().getPath(), s.isDirectory(), s.getLen(), s.getModificationTime()));
                }
            }
        }
        return results;
    }

    /**
     * List children of a directory (non-recursive) returning names with types.
     * This legacy method keeps returning a simple string list for compatibility; prefer listDirWithMeta for richer info.
     */
    public List<String> listDir(String remoteDir) throws IOException {
        Path p = new Path(remoteDir);
        FileStatus[] statuses = fs.listStatus(p);
        List<String> out = new ArrayList<>();
        for (FileStatus s : statuses) {
            // return only the FS path (no scheme/authority)
            out.add((s.isDirectory() ? "DIR : " : "FILE: ") + s.getPath().toUri().getPath());
        }
        return out;
    }

    /**
     * New: list children including size and modification time metadata.
     */
    public List<FileEntry> listDirWithMeta(String remoteDir) throws IOException {
        Path p = new Path(remoteDir);
        FileStatus[] statuses = fs.listStatus(p);
        List<FileEntry> out = new ArrayList<>();
        for (FileStatus s : statuses) {
            String pathOnly = s.getPath().toUri().getPath();
            long size = s.isDirectory() ? 0L : s.getLen();
            long mtime = s.getModificationTime();
            out.add(new FileEntry(pathOnly, s.isDirectory(), size, mtime));
        }
        return out;
    }

    /**
     * Read small file content as String from HDFS
     */
    public String readFileAsString(String remotePath) throws IOException {
        Path p = new Path(remotePath);
        try (FSDataInputStream in = fs.open(p)) {
            return org.apache.commons.io.IOUtils.toString(in, "UTF-8");
        }
    }

    // New helpers
    public boolean exists(String remotePath) throws IOException {
        return fs.exists(new Path(remotePath));
    }

    public boolean isDirectory(String remotePath) throws IOException {
        Path p = new Path(remotePath);
        if (!fs.exists(p)) return false;
        FileStatus status = fs.getFileStatus(p);
        return status.isDirectory();
    }

    /**
     * Set owner (and optionally group) of a remote path.
     */
    public void setOwner(String remotePath, String owner, String group) throws IOException {
        Path p = new Path(remotePath);
        fs.setOwner(p, owner, group);
    }

    /**
     * Set permission from octal string like "700" or "755".
     */
    public void setPermissionOctal(String remotePath, String octal) throws IOException {
        if (octal == null || octal.isEmpty()) return;
        short mode = (short) Integer.parseInt(octal, 8);
        FsPermission perm = new FsPermission(mode);
        fs.setPermission(new Path(remotePath), perm);
    }
}
