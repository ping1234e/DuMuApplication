package com.cscec.dumu;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    /** 请求结果 true-成功 false-失败 */
    private Boolean success;

    /** 状态码（接收数据处理是否成功）0-成功 */
    private Integer errorCode;

    /** 失败提示信息（只有当success为false时，接口返回message字段） */
    private String errorMsg;

    /** 响应信息 */
    private T result;
}