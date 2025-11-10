package pack01;

import org.apache.commons.io.IOUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight wrapper around Hadoop FileSystem to be used by a personal "HDFS Drive".
 * Provides upload, download, mkdirs, createFile, delete and search operations.
 */
public class HdfsService {
    private final FileSystem fs;

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
     * Search for files whose name contains the given pattern under startDir (searched recursively).
     */
    public List<String> search(String startDir, String nameContains) throws IOException {
        List<String> results = new ArrayList<>();
        RemoteIterator<LocatedFileStatus> it = fs.listFiles(new Path(startDir), true);
        while (it.hasNext()) {
            LocatedFileStatus s = it.next();
            if (s.getPath().getName().contains(nameContains)) {
                results.add(s.getPath().toString());
            }
        }
        return results;
    }

    /**
     * List children of a directory (non-recursive) returning names with types.
     */
    public List<String> listDir(String remoteDir) throws IOException {
        Path p = new Path(remoteDir);
        FileStatus[] statuses = fs.listStatus(p);
        List<String> out = new ArrayList<>();
        for (FileStatus s : statuses) {
            out.add((s.isDirectory() ? "DIR : " : "FILE: ") + s.getPath().toString());
        }
        return out;
    }
}

