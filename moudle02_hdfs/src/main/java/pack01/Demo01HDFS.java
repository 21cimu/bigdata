package pack01;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.*;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

public class Demo01HDFS {
    @Test
    public void getFileSystem() throws Exception {
        FileSystem fileSystem = FileSystem.get(new URI("hdfs://node1:8020"), new Configuration());
        System.out.println("fileSystem:"+fileSystem);
    }

    @Test
    public void listMyFiles()throws Exception{
        //获取fileSystem类
        FileSystem fileSystem = FileSystem.get(new URI("hdfs://node1:8020"), new Configuration(),"root");
        //获取RemoteIterator 得到所有的文件或者文件夹，第一个参数指定遍历的路径，第二个参数表示是否要递归遍历
        RemoteIterator<LocatedFileStatus> locatedFileStatusRemoteIterator = fileSystem.listFiles(new Path("/"), true);
        while (locatedFileStatusRemoteIterator.hasNext()){
            LocatedFileStatus next = locatedFileStatusRemoteIterator.next();
            System.out.println(next.getPath().toString());
        }
        fileSystem.close();
    }


    @Test
    public void mkdirs() throws  Exception{
        FileSystem fileSystem = FileSystem.get(new URI("hdfs://node1:8020"), new Configuration(),"root");
        boolean mkdirs = fileSystem.mkdirs(new Path("/hello/mydir/test"));
        fileSystem.close();
    }

    @Test
    public void putData() throws  Exception{
        FileSystem fileSystem = FileSystem.get(new URI("hdfs://node1:8020"), new Configuration(),"root");
        fileSystem.copyFromLocalFile(new Path("file:///d:\\mycode\\install.log"),new Path("/hello/mydir/test"));
        fileSystem.close();
    }


}
