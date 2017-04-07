package com.cncounter.test.spder;

import com.cncounter.spider.SpiderUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 */
public class TestSpiderUnitls {

    private static final Log logger = LogFactory.getLog(TestSpiderUnitls.class);

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
                "shipinmp4.com",
        };
        //
        SpiderUtils.resourceSuffix.add(".xml");
        SpiderUtils.resourceSuffix.add(".ts");
        SpiderUtils.resourceSuffix.add(".mu38");
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
            String path = "http://201610.shipinmp4.com/";
            //String path = "http://www.beautyleg.com/sample.php?no=";
            //for(int i=300; i<= 1422; i++){
                initUrl = path;// + i;
                SpiderUtils.spiderGrab(initUrl, basePath, hostList, maxDeep);
            //}
        } catch (FileNotFoundException e) {
            logger.error(e.getMessage(), e);
        } finally {
            //
            System.setErr(err);
            IOUtils.closeQuietly(errorLogOutputStream);
        }

    }

}
