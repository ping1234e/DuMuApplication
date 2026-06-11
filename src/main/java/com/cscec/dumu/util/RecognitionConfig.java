package com.cscec.dumu.util;

import com.jcraft.jsch.JSchException;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

/**
 * 识别记录同步配置
 */
public class RecognitionConfig {
    public static final String DB_DIR = System.getProperty("user.dir") + "/data/db/";
    public static final long FILE_VALID_DURATION = 30 * 60 * 1000L;
    public static final String REMOTE_DB_PATH = "/opt/data/record/recog_logging.db";

    /**
     * 获取目录下最新的 .db 文件
     */
    public static File getLatestDbFile() {
        File dbDir = new File(RecognitionConfig.DB_DIR);
        if (!dbDir.exists()) {
            return null;
        }

        File[] dbFiles = dbDir.listFiles((dir, name) -> name.endsWith(".db"));
        if (dbFiles == null || dbFiles.length == 0) {
            return null;
        }

        // 按最后修改时间排序，取最新的
        Arrays.sort(dbFiles, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return dbFiles[0];
    }

    /**
     * 清理所有旧的数据库文件（下载新文件前调用）
     */
    public static int cleanOldDbFiles() {
        File dbDir = new File(RecognitionConfig.DB_DIR);
        if (!dbDir.exists()) {
            return 0;
        }

        File[] dbFiles = dbDir.listFiles((dir, name) -> name.endsWith(".db"));
        if (dbFiles == null) {
            return 0;
        }

        int deletedCount = 0;
        for (File file : dbFiles) {
            if (file.delete()) {
                deletedCount++;
            }
        }

        return deletedCount;
    }

    /**
     * 从设备下载数据库文件
     * 使用已有的 SSH 连接逻辑
     */
    public static int downloadDbFromDevice(String host, String localPath) {
        SSHFileDownloader sshFileDownloader = new SSHFileDownloader();
        try {
            sshFileDownloader.downloadDatabase(host, "root", "", REMOTE_DB_PATH, localPath);
        } catch (JSchException e) {
            return -1;
        } catch (IOException e) {
            return -2;
        }
        return 0;
    }
}
