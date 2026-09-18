package com.ruoyi.ems.asr;


/**
 * ASR 网关不可用（连接被拒、连接超时、非正常关闭、非 101 握手等）。
 *
 * <p>与业务异常区分开：
 * <ul>
 *     <li>本异常：视为"外部依赖未启动/暂时不可用"，上层只做 INFO 提示，不打 ERROR 堆栈</li>
 *     <li>其他 Exception：真正的代码/协议错误，保留 ERROR + 堆栈</li>
 * </ul>
 */
public class AsrUnavailableException extends RuntimeException {

    public AsrUnavailableException(String message) {
        super(message);
    }

    public AsrUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
