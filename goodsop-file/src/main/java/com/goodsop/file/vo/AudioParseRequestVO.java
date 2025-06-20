package com.goodsop.file.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

/**
 * 音频文件解析请求体
 */
@Data
@Schema(description = "音频文件解析请求体")
public class AudioParseRequestVO {

    /**
     * 要解析的.bin文件的相对路径
     */
    @Schema(description = "要解析的.bin文件的相对路径", requiredMode = Schema.RequiredMode.REQUIRED, example = "2024/05/15/xxxx.bin")
    @NotEmpty(message = "文件路径不能为空")
    private String filePath;

    /**
     * 是否跳过16字节的头部
     * <p>
     * 根据设备上传协议，某些文件可能包含一个16字节的串行通信协议头。
     * </p>
     */
    @Schema(description = "是否跳过16字节的头部", defaultValue = "true")
    private boolean skipHeader = true;
} 