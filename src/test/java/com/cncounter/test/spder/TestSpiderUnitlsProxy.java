package com.cncounter.test.spder;

import com.cncounter.spider.SpiderUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpHost;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 */
public class TestSpiderUnitlsProxy {

    private static final Log logger = LogFactory.getLog(TestSpiderUnitlsProxy.class);

    //
    public static void main(String[] args){
        // 初始URL
        String
        initUrl = "http://www.legsjapan.com/en/";
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
                "shipinmp4.com",
                "www.legsjapan.com",
        };
        String[] proxyHosts = {
                "legsjapan.com"
        };
        //
        HttpHost proxy = new HttpHost("localhost", 1080);
        //
        SpiderUtils.proxy = proxy;
        //
        List<String> hostList = Arrays.asList(targetHosts);
        List<String> proxyHostList = Arrays.asList(proxyHosts);
        //
        SpiderUtils.proxyHostList = proxyHostList;
        //
        SpiderUtils.useMultiThread = false;
        SpiderUtils.downloadThreadPoolSize = 3;
        SpiderUtils.slowSleepMillis = 20;
        SpiderUtils.MAX_CONN_TIMEOUT = 60;
        //
        FileOutputStream errorLogOutputStream = null;
        PrintStream err = System.out;
        try {
            // 错误日志。。。
            String errorLog = "error_"+ new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()) + ".log";
            errorLogOutputStream = new FileOutputStream(new File(basePath, errorLog));
            System.setOut(new PrintStream(errorLogOutputStream, true));
            //
            SpiderUtils.spiderGrab(initUrl, basePath, hostList, maxDeep);
            while(SpiderUtils.downloadingTaskCount.get() > 0){
                TimeUnit.SECONDS.sleep(5L); // 主线程等待
            }
            //}
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        } finally {
            //
            System.setOut(err);
            IOUtils.closeQuietly(errorLogOutputStream);
        }

    }

}
