package com.cscec.dumu.util;

import com.jcraft.jsch.*;

import java.io.FileOutputStream;
import java.io.IOException;

public class SSHFileDownloader {

    public void downloadDatabase(String host, String user, String password,
                                 String remotePath, String localPath) throws JSchException, IOException {
        JSch jsch = new JSch();
        Session session = jsch.getSession(user, host, 22);
        session.setPassword(password);

        // 跳过主机密钥检查（测试用）
        session.setConfig("StrictHostKeyChecking", "no");

        // 增加连接超时和认证超时
        session.connect(30000);

        ChannelSftp channel = (ChannelSftp) session.openChannel("sftp");
        channel.connect();

        // 下载文件
        try (FileOutputStream fos = new FileOutputStream(localPath)) {
            channel.get(remotePath, fos);
            System.out.println("文件下载成功: " + remotePath + " -> " + localPath);
        } catch (SftpException e) {
            throw new RuntimeException(e);
        }

        channel.disconnect();
        session.disconnect();
    }
}
