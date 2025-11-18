package com.hdfsdrive.app;

import com.hdfsdrive.core.HdfsService;
import org.apache.hadoop.conf.Configuration;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Minimal CLI demo for HdfsService.
 * Usage examples (run with -cp target/classes plus Hadoop jars on the classpath):
 * java com.hdfsdrive.app.HdfsDriveApp upload <localPath> <remoteTarget>
 * java com.hdfsdrive.app.HdfsDriveApp download <remotePath> <localPath>
 * java com.hdfsdrive.app.HdfsDriveApp mkdir <remoteDir>
 * java com.hdfsdrive.app.HdfsDriveApp create <remoteFile> <content>
 * java com.hdfsdrive.app.HdfsDriveApp delete <remotePath> <recursive:true|false>
 * java com.hdfsdrive.app.HdfsDriveApp search <startDir> <nameContains>
 * java com.hdfsdrive.app.HdfsDriveApp list <remoteDir>
 */
public class HdfsDriveApp {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: java com.hdfsdrive.app.HdfsDriveApp <command> [args...]");
            return;
        }
        String hdfsUri = "hdfs://node1:8020"; // keep existing default
        String user = "root";
        HdfsService service = new HdfsService(hdfsUri, user, new Configuration());
        try {
            String cmd = args[0];
            switch (cmd) {
                case "upload":
                    service.upload(args[1], args[2]);
                    System.out.println("uploaded");
                    break;
                case "download":
                    service.download(args[1], args[2]);
                    System.out.println("downloaded");
                    break;
                case "mkdir":
                    service.mkdirs(args[1]);
                    System.out.println("mkdir done");
                    break;
                case "create":
                    service.createFile(args[1], args[2].getBytes(StandardCharsets.UTF_8), true);
                    System.out.println("file created");
                    break;
                case "delete":
                    boolean rec = Boolean.parseBoolean(args[2]);
                    service.delete(args[1], rec);
                    System.out.println("deleted");
                    break;
                case "search":
                    // search now returns FileEntry objects with metadata
                    List<HdfsService.FileEntry> found = service.search(args[1], args[2]);
                    for (HdfsService.FileEntry fe : found) {
                        String type = fe.isDirectory ? "DIR" : "FILE";
                        String mtime = fe.modificationTime > 0 ? new java.util.Date(fe.modificationTime).toString() : "";
                        System.out.printf("%s\t%s\t%s\t%d bytes\n", type, fe.path, mtime, fe.size);
                    }
                    break;
                case "list":
                    // listDirWithMeta returns FileEntry with size and modification time
                    List<HdfsService.FileEntry> list = service.listDirWithMeta(args[1]);
                    for (HdfsService.FileEntry fe : list) {
                        String type = fe.isDirectory ? "DIR" : "FILE";
                        String mtime = fe.modificationTime > 0 ? new java.util.Date(fe.modificationTime).toString() : "";
                        System.out.printf("%s\t%s\t%s\t%d bytes\n", type, fe.path, mtime, fe.size);
                    }
                    break;
                default:
                    System.out.println("unknown command: " + cmd);
            }
        } finally {
            service.close();
        }
    }
}