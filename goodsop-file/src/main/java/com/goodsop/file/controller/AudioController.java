package com.goodsop.file.controller;

import com.goodsop.common.core.model.Result;
import com.goodsop.file.service.AudioService;
import com.goodsop.file.vo.AudioParseRequestVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * 音频处理相关接口
 */
@RestController
@RequestMapping("/file/audio")
@RequiredArgsConstructor
@Tag(name = "音频处理", description = "提供音频文件解析等功能")
public class AudioController {

    private final AudioService audioService;

    /**
     * 解析Speex编码的.bin文件为.wav文件
     *
     * @param requestVO 包含文件路径和解析选项的请求体
     * @return 包含生成的.wav文件相对路径的响应
     */
    @PostMapping("/parse/speex-to-wav")
    @Operation(summary = "解析Speex(.bin)为WAV", description = "将已上传的Speex编码的.bin文件解析为.wav文件，并保存在同一目录下")
    public Result<String> parseSpeexToWav(@Valid @RequestBody AudioParseRequestVO requestVO) {
        try {
            String wavPath = audioService.parseSpeexToWav(requestVO.getFilePath(), requestVO.isSkipHeader());
            return Result.success(wavPath, "文件解析成功");
        } catch (IOException e) {
            return Result.error("文件解析失败: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }
} 