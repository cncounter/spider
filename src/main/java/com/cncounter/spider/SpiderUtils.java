package com.cncounter.spider;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpHost;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * 蜘蛛工具
 */
public class SpiderUtils {

    public static ConcurrentHashMap<String, String> GRABS_URL = new ConcurrentHashMap<String, String>();
    public static ConcurrentHashMap<String, String> GRABS_FILES = new ConcurrentHashMap<String, String>();
    //
    public static ThreadLocal<Integer> deepRecorder = new ThreadLocal<Integer>();
    public static AtomicInteger downloadingTaskCount = new AtomicInteger(0);
    // 控制抓取频率
    public static boolean useMultiThread = true;
    public static int downloadThreadPoolSize = 10;
    public static int slowSleepMillis = 20;
    //
    public static ExecutorService downloadThreadPool = null;
    // 代理
    public static HttpHost proxy = null;
    public static List<String> proxyHostList = new ArrayList<String>();
    // 只允许某种前缀
    public static String filterURLPrefix = null;

    //
    public static int MAX_CONN_TIMEOUT = 15;

    private static final Log logger = LogFactory.getLog(SpiderUtils.class);

    public static List<String> resourceSuffix = new ArrayList<String>(){
        private boolean toInit = toInitMethod();
        private boolean toInitMethod(){
            String[] suffixs = {
                    ".jpg",
                    ".png",
                    ".jpeg",
                    ".bmp",
                    ".gif"
            };
            for(String s : suffixs){
                this.add(s);
            }
            //
            return true;
        }
    };

    private static AtomicBoolean inited = new AtomicBoolean(false);
    private static void checkInit(){
        if(inited.get()){
            return;
        }
        //
        initDownloadThreadPool();
        //
        inited.getAndSet(true);
    }

    private static synchronized void initDownloadThreadPool() {
        //
        if(null != downloadThreadPool){
            return;
        }
        if(!useMultiThread){
            return;
        }
        if(downloadThreadPoolSize < 0){
            downloadThreadPoolSize = 1;
        }
        downloadThreadPool = Executors.newFixedThreadPool(downloadThreadPoolSize, new ThreadFactory() {
            private AtomicInteger count = new AtomicInteger(0);
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                thread.setDaemon(true);
                thread.setName("download-thread-" + count.getAndIncrement());
                return thread;
            }
        });
    }

    /**
     * 将URL连接为 InputStream, 主要用于下载文件
     * @param url
     * @return
     */
    public static InputStream getUrlAsStream(String url){
        InputStream inputStream = null;
        try {
            url = encodeURI(url);
            inputStream = _getUrlAsStream(url);
        } catch (IOException e) {
            log("下载失败: url="+url);
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
        URLConnection connection = null;
        //
        if(null != proxy && isInProxyList(url)){
            connection = realUrl.openConnection(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxy.getHostName(), proxy.getPort())));
        } else {
            connection = realUrl.openConnection();
        }

        Map<String, String> map = new HashMap<String,String>();
        map.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
        map.put("Accept-Encoding", "gzip, deflate, sdch");
        map.put("Accept-Language", "zh-CN,zh;q=0.8,en;q=0.6");
        map.put("Cache-Control", "max-age=0");
        map.put("Connection", "keep-alive");
        //map.put("Host", "201610.shipinmp4.com");
        map.put("Upgrade-Insecure-Requests", "1");
        map.put("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64); AppleWebKit/537.36 (KHTML, like Gecko); Chrome/56.0.2924.87 Safari/537.36");
        //
        Set<String> keySet = map.keySet();
        for(String key: keySet){
            String value = map.get(key);
            connection.setRequestProperty(key, value);
        }


        // 设置超时时间,10秒。宁可连接失败，也不能太慢
        connection.setConnectTimeout((int) TimeUnit.SECONDS.toMillis(MAX_CONN_TIMEOUT));
        if(isInResourceSuffix(url)){
            connection.setReadTimeout((int) TimeUnit.SECONDS.toMillis(MAX_CONN_TIMEOUT));
        }
        // 建立实际的连接
        connection.connect();

        //
        inputStream = connection.getInputStream();
        // 处理 gzip
        String contentEncoding = connection.getContentEncoding();
        if(null != contentEncoding && contentEncoding.contains("gzip")){
            inputStream = new GZIPInputStream(inputStream);
        }
        //
        return inputStream;
    }

    private static boolean isInProxyList(String url) {
        // 判断是否在代理名单。。。
        boolean isIn = false;
        if(null == url){ return isIn; }
        isIn = proxyHostList.contains(url);
        if(isIn){ return isIn; }
        //
        for(String proxyHost : proxyHostList){
            if(null == proxyHost || proxyHost.trim().isEmpty()){ continue; }
            //
            if(url.contains(proxyHost)){
                isIn = true;
                break;
            }
        }
        return isIn;
    }

    public static byte[] getUrlAsByteArray(String url){
        InputStream inputStream =  getUrlAsStream(url);
        //
        byte[] result = new byte[0];
        try {
            if(null != inputStream){
                result = IOUtils.toByteArray(inputStream);
            } else {
                logger.error("下载出错: url="+url);
            }
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

    //
    public static String encodeURI(String url){
        url = url.replace(" ", "%20"); // 替换空格 ; TO_DO
        return url;
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
        resourceUrl = resourceUrl.replace("\\", "/");
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
        try {
            targerUrl = URLEncoder.encode(targerUrl, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
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
        Pattern pattern = Pattern.compile(regexStr, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(content);
        while(matcher.find()){
            //
            String cur = matcher.group();
            //
            int srcIndex = cur.indexOf("href=");
            if(srcIndex < 0){
                srcIndex = cur.indexOf("HREF=");
            }
            if(srcIndex < 0){
                continue;
            }
            int srcIndex1 = srcIndex + 5;
            //
            String charSplit = cur.substring(srcIndex1, srcIndex1+1);
            //
            int srcStartIndex = cur.indexOf(charSplit, srcIndex1);
            int srcEndIndex = cur.indexOf(charSplit, srcIndex1 + 1);
            //
            if(srcStartIndex < 0 || srcEndIndex < 1){
                continue;
            }
            if(srcEndIndex < srcStartIndex+1){
                continue;
            }

            String src = cur.substring(srcStartIndex + 1, srcEndIndex);
            // 处理 # 的URL
            if(null == src || src.isEmpty() || src.startsWith("#")){
                continue;
            }
            int sharpIndex = src.indexOf("#");
            if(sharpIndex > 0){
                src = src.substring(0, sharpIndex);
            }
            // 不是标准的src
            if(src.contains("\n") || src.contains("'") || src.contains("\"")){
                continue;
            }
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
            if(srcIndex < 0 || cur.contains("data-original=")){
                srcIndex = cur.indexOf("data-original=");
                srcIndex1 = srcIndex + "data-original=".length();
            }
            if(srcIndex < 0 || cur.contains("data-src=")){
                srcIndex = cur.indexOf("data-src=");
                srcIndex1 = srcIndex + "data-src=".length();
            }
            // 没找到。。。
            if(srcIndex < 0){
                continue;
            }
            //
            String charSplit = cur.substring(srcIndex1, srcIndex1 + 1);
            //
            int srcStartIndex = cur.indexOf(charSplit, srcIndex1);
            int srcEndIndex = cur.indexOf(charSplit, srcIndex1 + 1);
            if("'".equals(charSplit) || "\"".equals(charSplit) ){
            } else {
                srcStartIndex = srcIndex1 -1;
                srcEndIndex=cur.indexOf(" ", srcIndex1 + 1);
            }
            if(srcStartIndex < 0 || srcEndIndex<0){
                continue;
            }
            if(srcStartIndex >= srcEndIndex){
                continue;
            }
            //
            String src = cur.substring(srcStartIndex + 1, srcEndIndex);
            //
            if(null == src || src.isEmpty() || src.startsWith("data:image/")){
                continue;
            }
            // 不是标准的src
            if(src.contains("\n") || src.contains("'") || src.contains("\"")){
                continue;
            }
            urlSet.add(src);
        }

        Set<String> imgLiUrlSet = parseImageLiSet(content);
        //
        urlSet.addAll(imgLiUrlSet);
        //
        return urlSet;
    }

    // 解析图片列表
    public static Set<String> parseImageLiSet(String content){
        //
        Set<String> urlSet = new HashSet<String>();
        //
        String regexStr = "<li[^>]+>";
        //
        Pattern pattern = Pattern.compile(regexStr);
        Matcher matcher = pattern.matcher(content);
        while(matcher.find()){
            //
            String cur = matcher.group();
            //
            int srcIndex = cur.indexOf("src=");
            int srcIndex1 = srcIndex + "src=".length();
            if(srcIndex < 0 || cur.contains("data-original=")){
                srcIndex = cur.indexOf("data-original=");
                srcIndex1 = srcIndex + "data-original=".length();
            }
            if(srcIndex < 0 || cur.contains("data-src=")){
                srcIndex = cur.indexOf("data-src=");
                srcIndex1 = srcIndex + "data-src=".length();
            }
            // 没找到。。。
            if(srcIndex < 0){
                continue;
            }
            //
            String charSplit = cur.substring(srcIndex1, srcIndex1 + 1);
            //
            int srcStartIndex = cur.indexOf(charSplit, srcIndex1);
            int srcEndIndex = cur.indexOf(charSplit, srcIndex1 + 1);
            //
            String src = cur.substring(srcStartIndex + 1, srcEndIndex);
            //
            if(null == src || src.isEmpty() || src.startsWith("data:image/")){
                continue;
            }
            //
            urlSet.add(src);
        }
        //
        return urlSet;
    }

    // 递归抓取
    public static void spiderGrab(String url, String basePath, List<String> whiteList, int maxDeep){
        //
        checkInit();
        Integer curDeep = deepRecorder.get();
        if(null == curDeep){
            deepRecorder.set(0);
            curDeep = deepRecorder.get();
        } else if(curDeep.intValue() > maxDeep){
            // logger.info("已经达到最大深度:curDeep="+curDeep);
            return;
        }
        // 已经包含，则不进行处理
        if(GRABS_URL.containsKey(url)){
            return;
        }

        GRABS_URL.put(url, url);
        if(GRABS_URL.size() % 100 == 0){
            System.out.println("GRABS_URL.size()="+GRABS_URL.size());
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
        // 只抓取特定前缀时
        if(null != filterURLPrefix && !url.startsWith(filterURLPrefix)){
            return;
        }
        // 控制抓取频率
        if(!useMultiThread){
            try {
                TimeUnit.MILLISECONDS.sleep(slowSleepMillis);
            } catch (InterruptedException e) {
                logger.error(e);
            }
        }
        //
        logger.info("准备抓取:" + url);
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
        //logger.info("===========================img:");
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
        //logger.info("===========================href:");
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
                    // logger.info("已经达到最大深度:curDeep="+curDeep);
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

    public static void saveUrlAsFile(final String url, final File baseDir) {
        if(!useMultiThread){
            _saveUrlAsFile(url, baseDir);
            //
            try {
                TimeUnit.MILLISECONDS.sleep(slowSleepMillis);
            } catch (InterruptedException e) {
                logger.error(e);
            }
            return;
        }
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                downloadingTaskCount.incrementAndGet();
                try {
                    _saveUrlAsFile(url, baseDir);
                } catch (Throwable ex){
                    logger.error(ex);
                } finally {
                    downloadingTaskCount.decrementAndGet();
                }
            }
        };
        // 判断数量... 超过阀值。。。 等待?
        //
        downloadThreadPool.submit(runnable);
    }

    public static void _saveUrlAsFile(String url, File baseDir) {
        // 已经包含，则不进行处理
        if(GRABS_FILES.containsKey(url)){
            return;
        }
        if(!isInResourceSuffix(url)){
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
        logger.info("准备下载:"+url);
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
                log("文件下载成功: " + url);
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
        if(GRABS_FILES.size() % 100 == 0){
            System.out.println("GRABS_FILES.size()="+GRABS_FILES.size());
        }
    }

    //
    public static void log(String msg){
        System.out.println(msg);
        logger.info(msg);
    }

}
