package pack01;

import org.apache.hadoop.conf.Configuration;

import java.util.List;

/**
 * Minimal CLI demo for HdfsService.
 * Usage examples (run with -cp target/classes plus Hadoop jars on the classpath):
 * java pack01.HdfsDriveApp upload <localPath> <remoteTarget>
 * java pack01.HdfsDriveApp download <remotePath> <localPath>
 * java pack01.HdfsDriveApp mkdir <remoteDir>
 * java pack01.HdfsDriveApp create <remoteFile> <content>
 * java pack01.HdfsDriveApp delete <remotePath> <recursive:true|false>
 * java pack01.HdfsDriveApp search <startDir> <nameContains>
 * java pack01.HdfsDriveApp list <remoteDir>
 */
public class HdfsDriveApp {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: java pack01.HdfsDriveApp <command> [args...]");
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
                    service.createFile(args[1], args[2].getBytes("UTF-8"), true);
                    System.out.println("file created");
                    break;
                case "delete":
                    boolean rec = Boolean.parseBoolean(args[2]);
                    service.delete(args[1], rec);
                    System.out.println("deleted");
                    break;
                case "search":
                    List<String> found = service.search(args[1], args[2]);
                    found.forEach(System.out::println);
                    break;
                case "list":
                    List<String> list = service.listDir(args[1]);
                    list.forEach(System.out::println);
                    break;
                default:
                    System.out.println("unknown command: " + cmd);
            }
        } finally {
            service.close();
        }
    }
}

