package com.cncounter.test.spder;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.*;
import java.util.*;

public class TestMergeFiles {

    private static final Log logger = LogFactory.getLog(TestMergeFiles.class);

    public static File targetDir = new File("D:\\usr\\spider_all\\201610.shipinmp4.com\\merged");
    public static String basePath = null;
    //
    public static void main(String[] args){
        // 保存的基本路径
        basePath = "D:\\usr\\spider_all\\201610.shipinmp4.com\\201610.shipinmp4.com";
        //basePath = "D:\\usr\\spider_all\\201610.shipinmp4.com\\201610.shipinmp4.com\\20170316";
        // 最大遍历深度
        try {
            toMerge(basePath);
            //}
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        } finally {
        }

    }

    private static void toMerge(String basePath) {
        //
        File baseDir = new File(basePath);
        if(!baseDir.exists() || !baseDir.isDirectory()){
            logger.error("不是目录:" + basePath);
            return;
        }
        //
        File[] subFiles = baseDir.listFiles();
        for(File sub : subFiles){
            if(null == sub || !sub.isDirectory()){
                continue;
            }
            // 查找并Merge-dir
            lookupAndMerge(sub);
        }
    }

    private static void lookupAndMerge(File dir) {
        //
        if(!dir.exists() || !dir.isDirectory()){
            logger.error("不是目录:" + dir.getAbsolutePath());
            return;
        }
        //
        File[] subFiles = dir.listFiles();
        for(File sub : subFiles){
            if(null == sub){
                continue;
            }
            if(sub.isDirectory()){
                // 递归
                lookupAndMerge(sub);
                continue;
            }
            // 如果是文件 .... 暂不考虑
        }
        //
        String prefix = "index";
        String suffix = ".ts";
        // 合并自身目录下的文件
        mergeFileOnly(dir, prefix, suffix);
        //
    }

    // 只merge文件
    private static void mergeFileOnly(File pathDir, final String prefix, final String suffix) {
        // 根据前缀后缀过滤文件
        File[] subFiles = pathDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                if(name.startsWith(prefix) && name.endsWith(suffix)){
                    return true;
                }
                return false;
            }
        });
        //
        if(null == subFiles || subFiles.length < 1){
            return;
        }
        //
        // 排序
        List<File> sortedFiles = toSort(subFiles, prefix, suffix);//
        if(null == sortedFiles || sortedFiles.isEmpty()){
            return;
        }
        //
        if(!targetDir.exists()){
            targetDir.mkdirs();
        }
        String currentPath = pathDir.getAbsolutePath();
        //
        String fileName = currentPath.replace(basePath, "");
        fileName = fileName.replace("/", "_");
        fileName = fileName.replace("\\", "_");
        if(fileName.startsWith("_")){
            fileName = fileName.substring(1);
        }
        //
        fileName += "_All";
        fileName += suffix;

        //
        File targetFile = new File(targetDir, fileName);
        File tempFile = new File(targetDir, fileName+".temp");
        logger.info("目标文件位置: " + targetFile.getAbsolutePath());
        FileOutputStream fileOutputStream = null;
        try {
            //
            fileOutputStream = new FileOutputStream(tempFile);
            //
            for (File sub : sortedFiles) {
                //
                logger.info("正在合并: " + sub.getAbsolutePath());
                FileInputStream fileInputStream = new FileInputStream(sub);
                IOUtils.copy(fileInputStream, fileOutputStream);
                logger.info("已合并: " + sub.getAbsolutePath());
                //
                close(fileInputStream);
            }
        } catch (Exception e){
            logger.error(e);
            return;
        } finally {
            close(fileOutputStream);
        }
        //
        tempFile.renameTo(targetFile);

    }

    private static void close(Closeable closeable){
        if(null == closeable){
            return;
        }
        try{
            closeable.close();
        } catch (Exception e){
            logger.error(e);
        }
    }

    private static List<File> toSort(File[] subFiles, final String prefix, final String suffix) {
        //
        List<File> sortedList = new ArrayList<File>();
        for(File sub : subFiles){
            sortedList.add(sub);
        }
        //
        Collections.sort(sortedList, new Comparator<File>() {
            @Override
            public int compare(File f1, File f2) {
                //
                String name1 = f1.getName();
                String name2 = f2.getName();
                //
                String idx1 = name1.replace(prefix, "").replace(suffix, "");
                String idx2 = name2.replace(prefix, "").replace(suffix, "");
                //
                if(idx1.matches("\\d+") && idx2.matches("\\d+")){
                    int id1 = Integer.parseInt(idx1);
                    int id2 = Integer.parseInt(idx2);
                    return id1 - id2;
                }
                //
                return idx1.compareTo(idx2);
            }
        });

        //
        return sortedList;
    }

}

