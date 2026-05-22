package com.cscec.dumu;


import com.alibaba.fastjson.JSONObject;
import okhttp3.*;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class DumuClient {
    private String baseUrl;
    private String password;
    private String passwordMd5;
    private OkHttpClient httpClient;

    public DumuClient(String ip, int port, String password) {
        this.baseUrl = "http://" + ip + ":" + port;
        this.password = password;
        this.passwordMd5 = md5(password);

        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    // MD5加密
    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // AES加密
    public static String aesEncrypt(String plaintext, String key) {
        try {
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            e.printStackTrace();
            return plaintext;
        }
    }

    // 通用POST请求
    private String post(String url, JSONObject data) throws IOException {

        System.out.println(data.toJSONString());
        RequestBody body = RequestBody.create(
                MediaType.parse("application/json; charset=utf-8"),
                data.toJSONString()
        );
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            return response.body() != null ? response.body().string() : "";
        }
    }

    // ==================== 设备管理接口 ====================

    // 1. 密码设置
    public String setPassword(String oldPassword, String newPassword) throws IOException {
        JSONObject data = new JSONObject();
        data.put("old_password", oldPassword);
        data.put("new_password", newPassword);
        return post(baseUrl + "/deviceManage/setPassword", data);
    }

    // 2. 序列号获取
    public String getDeviceSN() throws IOException {
        JSONObject data = new JSONObject();
        data.put("pass", passwordMd5);
        return post(baseUrl + "/deviceManage/getDeviceSN", data);
    }

    // 3. 设备时间设置
    public String setTime(long timestamp) throws IOException {
        JSONObject data = new JSONObject();
        data.put("pass", passwordMd5);
        data.put("timestamp", timestamp);
        return post(baseUrl + "/deviceManage/setTime", data);
    }

    // 4. 设备重启
    public String restart() throws IOException {
        JSONObject data = new JSONObject();
        data.put("pass", passwordMd5);
        return post(baseUrl + "/deviceManage/restart", data);
    }

    // 5. 设备重置
    public String reset() throws IOException {
        JSONObject data = new JSONObject();
        data.put("pass", passwordMd5);
        return post(baseUrl + "/deviceManage/reset", data);
    }

    // 6. 界面皮肤设置
    public String setUI(String resourcePack) throws IOException {
        JSONObject data = new JSONObject();
        data.put("pass", passwordMd5);
        if (resourcePack != null && !resourcePack.isEmpty()) {
            data.put("resource_pack", resourcePack);
        }
        return post(baseUrl + "/deviceManage/setUI", data);
    }

    // 7. 屏保设置
    public String setScreenSaver(boolean isOpen, int time, JSONObject images) throws IOException {
        JSONObject data = new JSONObject();
        data.put("pass", passwordMd5);
        data.put("is_open", isOpen ? 1 : 0);
        if (isOpen) {
            data.put("time", time);
        }
        if (images != null) {
            data.put("images", images);
        }
        return post(baseUrl + "/deviceManage/setScreenSaver", data);
    }

    // 8. 待机设置
    public String standby(boolean isOpen, int time) throws IOException {
        JSONObject data = new JSONObject();
        data.put("pass", passwordMd5);
        data.put("is_open", isOpen ? 1 : 0);
        if (isOpen) {
            data.put("time", time);
        }
        return post(baseUrl + "/deviceManage/standby", data);
    }

    // 9. 业务场景及核验方式设置
    public String setAlOperatingMode(int mode, int checkType) throws IOException {
        JSONObject data = new JSONObject();
        data.put("pass", passwordMd5);
        data.put("mode", mode);
        data.put("check_type", checkType);
        return post(baseUrl + "/deviceManage/setAlOperatingMode", data);
    }

    // 10. 语音播报设置
    public String setAudioConfig(boolean audioOn, String audioPack, boolean feedbackAudio) throws IOException {
        JSONObject data = new JSONObject();
        data.put("pass", passwordMd5);
        data.put("audio_on", audioOn);
        data.put("audio_pack", audioPack);
        data.put("feedback_audio", feedbackAudio);
        return post(baseUrl + "/deviceManage/setAudioConfig", data);
    }

    // 12. 设置识别记录回调
    public String setRecognitionCallback(String callbackUrl, int intervalTime) throws IOException {
        JSONObject data = new JSONObject();
        data.put("pass", passwordMd5);
        data.put("callback_url", callbackUrl);
        data.put("interval_time", intervalTime);
        return post(baseUrl + "/deviceManage/setRecognitionCallback", data);
    }

    // 13. 设置设备心跳回调
    public String setDeviceHeartBeat(String callbackUrl, int intervalTime) throws IOException {
        JSONObject data = new JSONObject();
        data.put("pass", passwordMd5);
        data.put("callback_url", callbackUrl);
        data.put("interval_time", intervalTime);
        return post(baseUrl + "/deviceManage/setDeviceHeartBeat", data);
    }

    // 14. 获取软件版本
    public String getSoftVersion() throws IOException {
        JSONObject data = new JSONObject();
        data.put("pass", passwordMd5);
        return post(baseUrl + "/deviceManage/getSoftVersion", data);
    }

    // 15. 远程开门
    public String openGate(int mode, Integer wgNumber) throws IOException {
        JSONObject data = new JSONObject();
        data.put("pass", passwordMd5);
        data.put("mode", mode);
        if (wgNumber != null && (mode == 1 || mode == 2)) {
            data.put("wg_number", wgNumber);
        }
        return post(baseUrl + "/deviceManage/openGate", data);
    }

    // 16. 戴口罩开关 (CM-Lite-T)
    public String setMaskConfig(boolean maskOn) throws IOException {
        JSONObject data = new JSONObject();
        data.put("pass", passwordMd5);
        data.put("mask_on", maskOn);
        return post(baseUrl + "/deviceManage/setMaskConfig", data);
    }

    // 17. 测温开关 (CM-Lite-T)
    public String setThermometryConfig(boolean thermometryOn, Float threshold) throws IOException {
        JSONObject data = new JSONObject();
        data.put("pass", passwordMd5);
        data.put("thermometry_on", thermometryOn);
        if (threshold != null) {
            data.put("threshold", threshold);
        }
        return post(baseUrl + "/deviceManage/setThermometryConfig", data);
    }

    // 18. 设置识别记录回调扩展选项
    public String setRecgCbConfig(int recordType, int imgReportType) throws IOException {
        JSONObject data = new JSONObject();
        data.put("pass", passwordMd5);
        data.put("record_type", recordType);
        data.put("img_report_type", imgReportType);
        return post(baseUrl + "/deviceManage/setRecgCbConfig", data);
    }


    // ==================== 人员管理接口 ====================

    // 1. 人员注册或更新
    public String saveOrUpdateUser(String userId, String imageContent,
                                   JSONObject userInfo,
                                   Long authStartTime, Long authEndTime) throws IOException {
        JSONObject data = new JSONObject();
        data.put("pass", passwordMd5);
        data.put("user_id", userId);
        if (imageContent != null) {
            data.put("image_content", imageContent);
            data.put("image_type", "image");
        }
        if (userInfo != null) {
            data.put("user_info", userInfo);
            data.put("phone_number", userInfo.get("phone_number"));
            data.put("card_number", userInfo.get("card_number"));
            // 新增时用编号作为ID
            if (userId == null) {
                data.put("user_id", userInfo.get("card_number"));
            }
        }
        if (authStartTime != null) {
            data.put("auth_start_time", authStartTime);
        }
        if (authEndTime != null) {
            data.put("auth_end_time", authEndTime);
        }
        return post(baseUrl + "/userManage/addUser", data);
    }

    // 2. 单人员删除
    public String deleteUser(String userId) throws IOException {
        JSONObject data = new JSONObject();
        data.put("pass", passwordMd5);
        data.put("user_id", userId);
        return post(baseUrl + "/userManage/deleteUser", data);
    }

    // 3. 获取人员信息
    public String getUserInfo(String userId) throws IOException {
        JSONObject data = new JSONObject();
        data.put("pass", passwordMd5);
        data.put("user_id", userId);
        return post(baseUrl + "/userManage/getUserInfo", data);
    }

    // 4. 设置人员有效期
    public String setUserAuthTime(String userId, long authStartTime, long authEndTime) throws IOException {
        JSONObject data = new JSONObject();
        data.put("pass", passwordMd5);
        data.put("user_id", userId);
        data.put("auth_start_time", authStartTime);
        data.put("auth_end_time", authEndTime);
        return post(baseUrl + "/userManage/setUserAuthTime", data);
    }

    // 5. 查询人员列表信息
    public String getUserList(int start, int length) throws IOException {
        JSONObject data = new JSONObject();
        data.put("pass", passwordMd5);
        data.put("start", start);
        data.put("length", length);
        return post(baseUrl + "/userManage/getUserList", data);
    }

    // 6. 批量删除人员信息
    public String deleteUserInfo(List<String> userIdList) throws IOException {
        JSONObject data = new JSONObject();
        data.put("pass", passwordMd5);
        data.put("user_id_list", userIdList);
        return post(baseUrl + "/userManage/deleteUserInfo", data);
    }

    // 7. 清空全部人员信息
    public String deleteAllUserInfo() throws IOException {
        JSONObject data = new JSONObject();
        data.put("pass", passwordMd5);
        return post(baseUrl + "/userManage/deleteAllUserInfo", data);
    }

    // 8. 生成二维码
    public String genQrCode(String uuid, long validStartTime, long validPeriod, JSONObject qrcodeConfig) throws IOException {
        JSONObject data = new JSONObject();
        data.put("pass", passwordMd5);
        data.put("uuid", uuid);
        data.put("valid_start_time", validStartTime);
        data.put("valid_period", validPeriod);
        if (qrcodeConfig != null) {
            data.put("qrcode_config", qrcodeConfig);
        }
        return post(baseUrl + "/userManage/genQrCode", data);
    }
}
