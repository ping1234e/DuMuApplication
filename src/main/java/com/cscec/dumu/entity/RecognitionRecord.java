package com.cscec.dumu.entity;

import lombok.Data;

@Data
public class RecognitionRecord {
    private Long id;
    private String userId;
    private String userName;
    private String cardNumber;
    private Integer recordType;
    private Integer verificationResult;
    private Integer passStatus;
    /**
     * 识别图片 暂时解析不了
     */
    private byte[] image;
    private Long timestamp;
    private String readableTime;
}
