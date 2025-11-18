package com.bogdatech.integration;

import com.bogdatech.utils.StringUtils;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.UploadResult;
import com.qcloud.cos.region.Region;
import com.qcloud.cos.transfer.Transfer;
import com.qcloud.cos.transfer.TransferManager;
import com.qcloud.cos.transfer.TransferProgress;
import com.qcloud.cos.transfer.Upload;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import java.io.ByteArrayInputStream;
import static com.bogdatech.constants.TencentConstants.*;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.TimeOutUtils.*;
import static com.bogdatech.utils.TimeOutUtils.DEFAULT_MAX_RETRIES;

@Component
public class HunYuanBucketIntegration {

    private static final String SECRET_ID = System.getenv(TENCENT_BUCKET_SECRET_ID);
    private static final String SECRET_KEY = System.getenv(TENCENT_BUCKET_SECRET_KEY);
    private static final String BUCKET_NAME = "ciwi-us-1327177217";
    private static final String COS_REGION = "na-ashburn";
    public static final String PATH_NAME = "image-Translation";
    private static final String HTTP = "https://ciwi-us-1327177217.cos.na-ashburn.myqcloud.com/";

    /**
     * 初始化用户身份信息
     */
    private static TransferManager createTransferManager() {
        // 1 初始化用户身份信息（secretId, secretKey）。
        COSCredentials cred = new BasicCOSCredentials(SECRET_ID, SECRET_KEY);
        // 2 设置 bucket 的地域
        // clientConfig 中包含了设置 region, https(默认 http), 超时, 代理等 set 方法, 使用可参见源码或者常见问题 Java SDK 部分。
        Region region = new Region(COS_REGION);
        ClientConfig clientConfig = new ClientConfig(region);
        // 这里建议设置使用 https 协议
        // 从 5.6.54 版本开始，默认使用了 https
        clientConfig.setHttpProtocol(HttpProtocol.https);
        // 3 生成 cos 客户端。
        COSClient cosclient = new COSClient(cred, clientConfig);
        // 传入一个threadpool, 若不传入线程池, 默认TransferManager中会生成一个单线程的线程池。
        return new TransferManager(cosclient);
    }

    /**
     * 进度条展示
     * */
    private static void showTransferProgress(Transfer transfer) {
        appInsights.trackTrace(transfer.getDescription());
        do {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                return;
            }
            TransferProgress progress = transfer.getProgress();
            long soFar = progress.getBytesTransferred();
            long total = progress.getTotalBytesToTransfer();
            double pct = progress.getPercentTransferred();
            appInsights.trackTrace("[" + soFar + " / " + total + "] = " + pct);
        } while (transfer.isDone() == false);
        appInsights.trackTrace(String.valueOf(transfer.getState()));
    }

    /**
     * 上传文件图片
     * 上传文件, 根据文件大小自动选择简单上传或者分块上传。
     * */
    public static String uploadFile(MultipartFile file, String shopName, String imageId) {
            TransferManager transferManager = createTransferManager();
            String originalFilename = file.getOriginalFilename();
            String extension = "";

            // 获取文件后缀名
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }

            // 随机生成8位随机数
            String generate8DigitNumber = StringUtils.generate8DigitNumber();
            String key = PATH_NAME + "/" + shopName + "/" + imageId + "/" + generate8DigitNumber + extension;
            String afterUrl = HTTP + key;
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());
            metadata.setContentType(file.getContentType());

            try {
                PutObjectRequest putObjectRequest = callWithTimeoutAndRetry(() -> {
                            try {
                                return new PutObjectRequest(BUCKET_NAME, key, file.getInputStream(), metadata);
                            } catch (Exception e) {
                                appInsights.trackTrace("每日须看 uploadFile 腾讯上传图片报错信息 errors ： " + e.getMessage() + " imageId : " + imageId + " 用户：" + shopName);
                                appInsights.trackException(e);
                                return null;
                            }
                        },
                        DEFAULT_TIMEOUT, DEFAULT_UNIT,    // 超时时间
                        DEFAULT_MAX_RETRIES                // 最多重试3次
                );
                if (putObjectRequest == null) {
                    return null;
                }
//                long startTime = System.currentTimeMillis();
                Upload upload = transferManager.upload(putObjectRequest);
                showTransferProgress(upload);
                UploadResult uploadResult = upload.waitForUploadResult();
//                long endTime = System.currentTimeMillis();
//                appInsights.trackTrace("used time: " + (endTime - startTime) / 1000);
                transferManager.shutdownNow();
                return afterUrl; // 上传成功直接返回true
            } catch (Exception e) {
                appInsights.trackTrace("每日须看 uploadFile 腾讯上传图片报错信息 errors : " + e.getMessage() + ", shopName: " + shopName + " imageId: " + imageId);
            } finally {
                transferManager.shutdownNow();
            }

        return null;
    }

    /**
     * 重新实现一个存bucket桶的方法
     * 会于上面uploadFile大部分重复，先实现后面再优化
     */
    public static String uploadBytes(byte[] bytes, String key, String contentType){
        TransferManager transferManager = createTransferManager();
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(bytes.length);
        metadata.setContentType(contentType);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);

        try {
            PutObjectRequest putObjectRequest = callWithTimeoutAndRetry(() -> {
                        try {
                            return new PutObjectRequest(BUCKET_NAME, key, inputStream, metadata);
                        } catch (Exception e) {
                            appInsights.trackTrace("每日须看 uploadFile 腾讯上传图片报错信息 errors ： " + e.getMessage() + " key : " + key );
                            appInsights.trackException(e);
                            return null;
                        }
                    },
                    DEFAULT_TIMEOUT, DEFAULT_UNIT,    // 超时时间
                    DEFAULT_MAX_RETRIES                // 最多重试3次
            );
            if (putObjectRequest == null) {
                return null;
            }

//            long startTime = System.currentTimeMillis();
            Upload upload = transferManager.upload(putObjectRequest);
            showTransferProgress(upload);
            upload.waitForUploadResult();
//            long endTime = System.currentTimeMillis();
//            System.out.println("used time: " + (endTime - startTime) / 1000);
            transferManager.shutdownNow();
            return HTTP + key; // 上传成功直接返回true
        } catch (Exception e) {
            appInsights.trackTrace("每日须看 uploadFile 腾讯上传图片报错信息 errors : " + e.getMessage() + ", key : " + key );
        } finally {
            transferManager.shutdownNow();
        }
        return null;
    }
}
