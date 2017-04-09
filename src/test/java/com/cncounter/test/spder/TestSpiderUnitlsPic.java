package com.cncounter.test.spder;

import com.cncounter.spider.SpiderUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

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
public class TestSpiderUnitlsPic {

    private static final Log logger = LogFactory.getLog(TestSpiderUnitlsPic.class);

    //
    public static void main(String[] args){
        // 初始URL
        String
                initUrl = "http://beautyleg.com";
        initUrl = "http://www.mzitu.com";
        initUrl = "http://pp.163.com/square/";
        initUrl = "http://www.fuliwc.com/";
        initUrl = "http://www.youzan6.com/";
        initUrl = "http://www.74gan.com/html/pic/156302.html";
        initUrl = "http://www.youzan6.com/post/5491.html?temp=0.8975390617551002";
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
                "mp4.79yyy.com",
                "youzan6.com",
                "www.74gan.com",
        };
        //
        List<String> hostList = Arrays.asList(targetHosts);
        //
        SpiderUtils.useMultiThread = false;
        SpiderUtils.downloadThreadPoolSize = 3;
        SpiderUtils.slowSleepMillis = 200;
        //
        FileOutputStream errorLogOutputStream = null;
        PrintStream err = System.out;
        try {
            // 错误日志。。。
            String errorLog = "error_"+ new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()) + ".log";
            errorLogOutputStream = new FileOutputStream(new File(basePath, errorLog));
            System.setOut(new PrintStream(errorLogOutputStream, true));

            // 开始迭代抓取;
            // TODO: 考虑返回值;分层逐级抓取; 不使用迭代
            // http://www.beautyleg.com/sample.php?no=300-1422
            //String path = "http://www.beautyleg.com/sample.php?no=";
            //for(int i=300; i<= 1422; i++){
                SpiderUtils.spiderGrab(initUrl, basePath, hostList, maxDeep);
            while(SpiderUtils.downloadingTaskCount.get() > 0){
                TimeUnit.SECONDS.sleep(5L); // 主线程等待
            }
            //}
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        } finally {
            //
            System.setErr(err);
            IOUtils.closeQuietly(errorLogOutputStream);
        }

    }

}
