package com.bogdatech.integration;

import com.bogdatech.entity.DO.UserPicturesDO;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.exception.CosServiceException;
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
import java.io.IOException;
import static com.bogdatech.constants.TencentConstants.*;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;

@Component
public class HunYuanBucketIntegration {

    private static final String SECRET_ID = System.getenv(TENCENT_BUCKET_SECRET_ID);
    private static final String SECRET_KEY = System.getenv(TENCENT_BUCKET_SECRET_KEY);
    private static final String BUCKET_NAME = "ciwi-us-1327177217";
    private static final String COS_REGION = "na-ashburn";
    private static final String PATH_NAME = "image-Translation";
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
            System.out.printf("[%d / %d] = %.02f%%\n", soFar, total, pct);
        } while (transfer.isDone() == false);
        appInsights.trackTrace(String.valueOf(transfer.getState()));
    }

    /**
     * 上传文件图片
     * 上传文件, 根据文件大小自动选择简单上传或者分块上传。
     * */
    public static String uploadFile(MultipartFile file, String shopName, UserPicturesDO userPicturesDO) {
        int maxRetries = 3; // 最大重试次数
        int retryCount = 0;
        long retryDelayMillis = 2000; // 重试间隔2秒

        while (retryCount < maxRetries) {
            TransferManager transferManager = createTransferManager();
            String originalFilename = file.getOriginalFilename();
            String key = PATH_NAME + "/" + shopName + "/" + userPicturesDO.getImageId() + "/" + originalFilename;
            String afterUrl = HTTP + key;
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());
            metadata.setContentType(file.getContentType());

            try {
                PutObjectRequest putObjectRequest = new PutObjectRequest(BUCKET_NAME, key, file.getInputStream(), metadata);
//                long startTime = System.currentTimeMillis();
                Upload upload = transferManager.upload(putObjectRequest);
                showTransferProgress(upload);
                UploadResult uploadResult = upload.waitForUploadResult();
//                long endTime = System.currentTimeMillis();
//                appInsights.trackTrace("used time: " + (endTime - startTime) / 1000);
//                appInsights.trackTrace(uploadResult.getETag());
//                appInsights.trackTrace(uploadResult.getCrc64Ecma());
                transferManager.shutdownNow();
                return afterUrl; // 上传成功直接返回true
            } catch (Exception e) {
                retryCount++;
                appInsights.trackTrace("插入图片 errors : " + e.getMessage() + ", retry " + retryCount + "/" + maxRetries);
                transferManager.shutdownNow();
                if (retryCount >= maxRetries) {
                    // 超过最大重试次数，返回失败
                    return null;
                }
                try {
                    Thread.sleep(retryDelayMillis);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }
        return null;
    }

}
