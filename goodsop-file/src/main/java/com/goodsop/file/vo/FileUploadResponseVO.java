package com.goodsop.file.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "文件上传响应实体")
public class FileUploadResponseVO {


    @Schema(description = "文件ID")
    private Long fileId;

    @Schema(description = "文件名")
    private String fileName;

    @Schema(description = "文件大小（字节）")
    private Long fileSize;

    @Schema(description = "文件类型（MIME Type）")
    private String fileType;

    @Schema(description = "上传时间")
    private LocalDateTime uploadTime;

    @Schema(description = "文件存储路径")
    private String filePath;

    @Schema(description = "文件访问URL")
    private String accessUrl;

    @Schema(description = "局域网文件访问URL")
    private String lanAccessUrl;

    @Schema(description = "是否加密 (0-否, 1-是)")
    private Integer isEncrypted;

    @Schema(description = "是否压缩 (0-否, 1-是)")
    private Integer isCompressed;

    @Schema(description = "是否上传完成")
    private Boolean completed;

    @Schema(description = "响应消息")
    private String message;
    
    @Schema(description = "总块数")
    private Integer chunks;

    @Schema(description = "当前块索引")
    private Integer chunk;

    @Schema(description = "操作是否成功")
    private Boolean success;
} 