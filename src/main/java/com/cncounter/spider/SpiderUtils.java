package com.cncounter.spider;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.client.utils.HttpClientUtils;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 蜘蛛工具
 */
public class SpiderUtils {

    public static ConcurrentHashMap<String, String> GRABS_URL = new ConcurrentHashMap<String, String>();
    public static ConcurrentHashMap<String, String> GRABS_FILES = new ConcurrentHashMap<String, String>();

    private static final Log logger = LogFactory.getLog(HttpClientUtils.class);

    /**
     * 将URL连接为 InputStream, 主要用于下载文件
     * @param url
     * @return
     */
    public static InputStream getUrlAsStream(String url){
        InputStream inputStream = null;
        try {
            inputStream = _getUrlAsStream(url);
        } catch (IOException e) {
            logger.error(e);
        }
        //
        return inputStream;
    }

    /**
     * 内部实现
     * @param url
     * @return
     * @throws IOException
     */
    private static InputStream _getUrlAsStream(String url) throws IOException {
        InputStream inputStream = null;
        URL realUrl = new URL(url);
        // 打开和URL之间的连接
        URLConnection connection = realUrl.openConnection();
        // 设置超时时间,10秒。宁可连接失败，也不能太慢
        connection.setConnectTimeout((int) TimeUnit.SECONDS.toMillis(10));
        // 建立实际的连接
        connection.connect();
        //
        inputStream = connection.getInputStream();
        //
        return inputStream;
    }

    public static byte[] getUrlAsByteArray(String url){
        InputStream inputStream =  getUrlAsStream(url);
        //
        byte[] result = new byte[0];
        try {
            result = IOUtils.toByteArray(inputStream);
        } catch (IOException e) {
            logger.error("下载出错: url="+url);
            logger.error("下载出错: inputStream="+inputStream);
        }
        IOUtils.closeQuietly(inputStream);
        //
        return result;
    }

    public static String getUrlAsString(String url){
        //
        byte[] result = getUrlAsByteArray(url);
        String str = null; //
        try {
            str = new String(result, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            logger.error(e.getMessage(), e);
        }
        //
        return str;
    }


    public static String getScheme(String url){
        //
        String host = null;
        try {
            URI uri = new URI(url);
            host = uri.getScheme();
        } catch (URISyntaxException e) {
            logger.error(e.getMessage(), e);
        }
        return host;
    }
    public static String getHost(String url){
        //
        String host = null;
        try {
            URI uri = new URI(url);
            host = uri.getHost();
        } catch (URISyntaxException e) {
            logger.error(e.getMessage(), e);
        }
        return host;
    }
    //
    public static String getPath(String url){
        //
        String path = null;
        try {
            URI uri = new URI(url);
            path = uri.getPath();
        } catch (URISyntaxException e) {
            logger.error(e.getMessage(), e);
        }
        return path;
    }

    /**
     * 组合两个URI；主要是处理2个斜线或者没有斜线 "/" 问题
     * @param basePath
     * @param subUri
     * @return
     */
    public static String concatUri(String basePath, String subUri){
        final  String SLASH = "/";
        //
        if(null == basePath){
            basePath = "";
        }
        if(null == subUri){
            subUri = "";
        }
        String resultUri = basePath;
        if(resultUri.endsWith(SLASH) && subUri.startsWith(SLASH)){
            resultUri = resultUri.substring(1);
        } else if(!resultUri.endsWith(SLASH) && !subUri.startsWith(SLASH)){
            resultUri += SLASH;
        }
        //
        resultUri += subUri;
        //
        return resultUri;
    }

    // 解析资源相对page的路径
    private static String parseRelativeUriPath(String pageUrl, String resourceUrl) {
        if(null == pageUrl || pageUrl.trim().isEmpty()){
            return resourceUrl;
        }
        if(null == resourceUrl || resourceUrl.trim().isEmpty()){
            return resourceUrl;
        } else if(resourceUrl.startsWith("http")){
            // 不需要特殊处理
            return resourceUrl;
        }
        //以下方法对相对路径进行转换
        try {
            URL absoluteUrl = new URL(pageUrl);
            URL relativeUrl = new URL(absoluteUrl ,resourceUrl );
            String tgtUrl = relativeUrl.toExternalForm();
            if(null != tgtUrl){
                return tgtUrl;
            }
        } catch (Exception e) {
            logger.error("解析项目对路径出错; pageUrl="+pageUrl+";resourceUrl="+resourceUrl, e);
        }
        //
        String targerUrl = resourceUrl;
        //
        String scheme = getScheme(pageUrl);
        String host = getHost(pageUrl);
        //
         if(resourceUrl.startsWith("//")){
             // 双斜线开始
             targerUrl = scheme + ":" + resourceUrl;
        } else if(resourceUrl.startsWith("/")){
            // 根路径; 不考虑 <base 标签>
            String hostScheme = scheme + "://" + host;
            targerUrl = concatUri(hostScheme, resourceUrl);
        } else {
             //
             if(resourceUrl.startsWith("./")){
                 resourceUrl = resourceUrl.substring(2);
             }
             // ../../../ 这种形式?
            // 相对路径
            int hostIndex = pageUrl.indexOf(host);
            int lastSlashIndex = pageUrl.lastIndexOf("/");
            //
            String uriPath = pageUrl;
            if(hostIndex < lastSlashIndex){
                uriPath = pageUrl.substring(0, lastSlashIndex);
            }
            targerUrl = concatUri(uriPath, resourceUrl);
        }
        return targerUrl;
    }
    //
    public static Set<String> parseHrefSet(String content){
        //
        Set<String> urlSet = new HashSet<String>();
        //
        String regexStr = "<a[^>]+>";
        //
        Pattern pattern = Pattern.compile(regexStr);
        Matcher matcher = pattern.matcher(content);
        while(matcher.find()){
            //
            String cur = matcher.group();
            //
            int srcIndex = cur.indexOf("href=");
            int srcIndex1 = srcIndex + 5;
            //
            String charSplit = cur.substring(srcIndex1, srcIndex1+1);
            //
            int srcStartIndex = cur.indexOf(charSplit, srcIndex1);
            int srcEndIndex = cur.indexOf(charSplit, srcIndex1+1);
            //
            if(srcStartIndex < 0 || srcEndIndex < 1){
                continue;
            }
            if(srcEndIndex < srcStartIndex+1){
                continue;
            }

            String src = cur.substring(srcStartIndex+1, srcEndIndex);
            //
            urlSet.add(src);
        }

        //
        return urlSet;
    }
    // 解析图片列表
    public static Set<String> parseImageSet(String content){
        //
        Set<String> urlSet = new HashSet<String>();
        //
        String regexStr = "<img[^>]+>";
        //
        Pattern pattern = Pattern.compile(regexStr);
        Matcher matcher = pattern.matcher(content);
        while(matcher.find()){
            //
            String cur = matcher.group();
            //
            int srcIndex = cur.indexOf("src=");
            int srcIndex1 = srcIndex + "src=".length();
            if(srcIndex < 0){
                srcIndex = cur.indexOf("data-original=");
                srcIndex1 = srcIndex + "data-original=".length();
            }
            // 没找到。。。
            if(srcIndex < 0){
                continue;
            }
            //
            String charSplit = cur.substring(srcIndex1, srcIndex1+1);
            //
            int srcStartIndex = cur.indexOf(charSplit, srcIndex1);
            int srcEndIndex = cur.indexOf(charSplit, srcIndex1+1);
            //
            String src = cur.substring(srcStartIndex+1, srcEndIndex);
            //
            urlSet.add(src);
        }

        //
        return urlSet;
    }
    //
    public static ThreadLocal<Integer> deepRecorder = new ThreadLocal<Integer>();

    // 递归抓取
    public static void spiderGrab(String url, String basePath, List<String> whiteList, int maxDeep){
        //
        Integer curDeep = deepRecorder.get();
        if(null == curDeep){
            deepRecorder.set(0);
            curDeep = deepRecorder.get();
        } else if(curDeep.intValue() > maxDeep){
            // log("已经达到最大深度:curDeep="+curDeep);
            return;
        }
        // 已经包含，则不进行处理
        if(GRABS_URL.containsKey(url)){
            return;
        }

        // 创建目录
        File baseDir = new File(basePath);
        if(!baseDir.exists()){
            baseDir.mkdirs();
        }
        //
        //String scheme = getScheme(url);
        String host = getHost(url);
        // 不在白名单之中...

        if(!isInWhiteList(whiteList, host)){
            return;
        }
        // 创建 host 目录
        File hostDir = new File(baseDir, host);
        if(!hostDir.exists()){
            hostDir.mkdirs();
        }
        //
        log("准备抓取:" + url);
        GRABS_URL.put(url, url);
        String initContent = getUrlAsString(url);
        //
        Set<String> imgUrlSet = parseImageSet(initContent);
        Set<String> hrefUrlSet = parseHrefSet(initContent);

        Iterator<String> iteratorH = hrefUrlSet.iterator();
        while (iteratorH.hasNext()){
            String href = iteratorH.next();
            // 满足特定后缀, 则不抓取，当做资源处理
            if(isInResourceSuffix(href)){
                iteratorH.remove();
                imgUrlSet.add(href);
            }
        }
        // 遍历图片 ...
        //log("===========================img:");
        for(String imgUrl : imgUrlSet){
            // 保存到本地
            // 解析相对绝对路径
            imgUrl = parseRelativeUriPath(url, imgUrl);

            try{
                saveUrlAsFile(imgUrl, hostDir);
                //TimeUnit.MILLISECONDS.sleep(10);
            } catch (Exception e){
                logger.error(e.getMessage(), e);
            }
        }
        //
        //log("===========================href:");
        for(String href : hrefUrlSet){
            if(null == href){
                continue;
            }
            if(href.trim().startsWith("javascript:")){
                continue;
            }
            // 解析相对路径
            href = parseRelativeUriPath(url, href);
            if(GRABS_URL.containsKey(href)){
                continue;
            }
            try{
                // 循环时-重置
                deepRecorder.set(curDeep + 1);
                if(curDeep + 1 > maxDeep){
                    // log("已经达到最大深度:curDeep="+curDeep);
                    continue;
                }
                // 递归
                spiderGrab(href, basePath,whiteList, maxDeep);
            } catch (Exception e){
                logger.error(e.getMessage(), e);
            }
        }
        //
        return;
    }

    // 判断是否在白名单。。。
    private static boolean isInWhiteList(List<String> whiteList, String host) {
        boolean isIn = false;
        if(null == host){ return isIn; }
        isIn = whiteList.contains(host);
        if(isIn){ return isIn; }
        //
        for(String whiteHost : whiteList){
            if(null == whiteHost || whiteHost.trim().isEmpty()){ continue; }
            //
            if(host.endsWith(whiteHost)){
                isIn = true;
                break;
            }
        }
        return isIn;
    }

    private static boolean isInResourceSuffix(String href) {
        if(null == href){return false;}
        if(href.contains("?")){
            href = href.substring(0, href.indexOf("?"));
        }
        String[] resourceSuffix = {
                ".jpg",
                ".png",
                ".jpeg",
                ".bmp",
                ".gif",
        }
        ;
        href = href.toLowerCase();
        for(String suffix : resourceSuffix){
            if(null == suffix){continue;}
            suffix = suffix.toLowerCase();
            if(href.endsWith(suffix)){
                return true;
            }
        }

        return false;
    }

    public static void saveUrlAsFile(String url, File baseDir) {
        // 已经包含，则不进行处理
        if(GRABS_FILES.containsKey(url)){
            return;
        }

        String host = getHost(url);
        //
        String path = getPath(url);
        String[] pathArray = path.split("/");

        // 创建 host 目录
        File hostDir = new File(baseDir, host);
        if(!hostDir.exists()){
            hostDir.mkdirs();
        }
        File targetDir = hostDir;
        String fileName = null;
        for(int i=0; i < pathArray.length; i++){
            //
            String subPath = pathArray[i];
            if(i == pathArray.length-1){
                //
                fileName = subPath;
                continue;
            }
            if(null == subPath || subPath.isEmpty()){
                continue;
            }
            //
            File subPathDir = new File(targetDir, subPath);
            subPathDir.mkdirs();
            targetDir = subPathDir;
        }
        //
        if(null == fileName){
            return;
        }
        //
        fileName = fileName.replace("?", "_").replace("&", "_");
        File outputFile = new File(targetDir, fileName);
        if(outputFile.exists()){
            GRABS_FILES.put(url, url);
            return;
        }
        // 使用临时文件下载
        String tempFileName = fileName + ".temp";
        File tempFile = new File(targetDir, tempFileName);
        //
        log("准备下载:"+url);
        InputStream inputStream = null;
        FileOutputStream fileOutputStream = null;
        try {
            inputStream = getUrlAsStream(url);
            fileOutputStream = new FileOutputStream(tempFile);
            if(null != fileOutputStream){
                IOUtils.copy(inputStream, fileOutputStream);
                IOUtils.closeQuietly(fileOutputStream);
                // 重命名
                tempFile.renameTo(outputFile);
                //
                log("文件下载成功: " + outputFile.getAbsolutePath());
            } else {
                logger.debug("!!下载失败: url="+url);
            }
        } catch (Exception e) {
            logger.error("下载失败: url="+url, e);
        } finally {
            //
            IOUtils.closeQuietly(inputStream);
            IOUtils.closeQuietly(fileOutputStream);
        }
        //
        GRABS_FILES.put(url, url);
    }

    //
    public static void log(String msg){
        logger.info(msg);
    }

    //
    public static void main(String[] args){
        // 初始URL
        String
                initUrl = "http://beautyleg.com";
                initUrl = "http://www.mzitu.com";
                initUrl = "http://pp.163.com/square/";
                initUrl = "http://www.fuliwc.com/";
        // 保存的基本路径
        String basePath = "D:\\usr\\spider_all";
        // 最大遍历深度
        int maxDeep = 150;
        // 白名单 host
        String[] targetHosts = {
                "beautyleg.com",
                "www.beautyleg.com",
                "www.mzitu.com",
                "pp.163.com",
                "www.fuliwc.com",
        };
        //
        List<String> hostList = Arrays.asList(targetHosts);
        //
        FileOutputStream errorLogOutputStream = null;
        PrintStream err = System.err;
        try {
            // 错误日志。。。
            String errorLog = "error_"+ new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()) + ".log";
            errorLogOutputStream = new FileOutputStream(new File(basePath, errorLog));
            //System.setErr(new PrintStream(errorLogOutputStream, true));

            // 开始迭代抓取;
            // TODO: 考虑返回值;分层逐级抓取; 不使用迭代
            // http://www.beautyleg.com/sample.php?no=300-1422
            //String path = "http://beautyleg.com/photo/show.php?no=";
            String path = "http://www.beautyleg.com/sample.php?no=";
            for(int i=300; i<= 1422; i++){
                initUrl = path + i;
                spiderGrab(initUrl, basePath, hostList, maxDeep);
            }
        } catch (FileNotFoundException e) {
            logger.error(e.getMessage(), e);
        } finally {
            //
            System.setErr(err);
            IOUtils.closeQuietly(errorLogOutputStream);
        }

    }

}
