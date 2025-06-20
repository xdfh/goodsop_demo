package com.goodsop.file.service.impl;

import com.goodsop.file.config.FileProperties;
import com.goodsop.file.service.AudioService;
import com.goodsop.file.util.SpeexUtil;
import com.goodsop.file.util.WavUtil;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 音频处理服务实现类
 */
@Service
@RequiredArgsConstructor
public class AudioServiceImpl implements AudioService {

    private static final Logger logger = LoggerFactory.getLogger(AudioServiceImpl.class);

    private final FileProperties fileProperties;

    /**
     * 解析Speex编码的 .bin 文件并将其转换为 .wav 文件
     *
     * @param relativePath 要解析的 .bin 文件的相对路径
     * @param skipHeader   是否跳过16字节的协议头
     * @return 生成的 .wav 文件的相对路径
     * @throws IOException 如果文件读写或解析过程中发生错误
     */
    @Override
    public String parseSpeexToWav(String relativePath, boolean skipHeader) throws IOException {
        if (!StringUtils.hasText(relativePath)) {
            throw new IllegalArgumentException("文件路径不能为空");
        }

        Path sourceFilePath;
        String wavRelativePath;
        Path targetFilePath;

        String storageBasePath = fileProperties.getStorage().getPath();
        Path storageBase = Paths.get(storageBasePath);
        Path providedPath = Paths.get(relativePath);

        // 如果提供的是绝对路径
        if (providedPath.isAbsolute()) {
            // 安全校验：确保绝对路径在存储根目录之下
            if (!providedPath.normalize().startsWith(storageBase.normalize())) {
                logger.warn("拒绝访问存储目录之外的绝对路径: {}", providedPath);
                throw new IllegalArgumentException("不允许访问存储目录之外的绝对路径。");
            }
            sourceFilePath = providedPath;
            // 将绝对路径转换为相对于存储目录的路径，以便后续处理
            wavRelativePath = storageBase.relativize(providedPath).toString().replaceAll("(?i)\\.bin$", ".wav");
            targetFilePath = Paths.get(wavRelativePath).isAbsolute()
                    ? Paths.get(wavRelativePath)
                    : storageBase.resolve(wavRelativePath);

        } else { // 如果提供的是相对路径
            sourceFilePath = storageBase.resolve(relativePath);
            wavRelativePath = relativePath.replaceAll("(?i)\\.bin$", ".wav");
            targetFilePath = storageBase.resolve(wavRelativePath);
        }

        if (Files.notExists(sourceFilePath)) {
            logger.error("找不到要解析的音频文件: {}", sourceFilePath);
            throw new IOException("源文件不存在: " + relativePath);
        }

        // 2. 读取源文件内容
        byte[] speexData = Files.readAllBytes(sourceFilePath);
        logger.info("开始解析文件: {}, 大小: {} bytes, 是否跳过头部: {}", relativePath, speexData.length, skipHeader);


        // 3. 解码Speex数据
        byte[] pcmData = SpeexUtil.decode(speexData, skipHeader);
        if (pcmData.length == 0) {
            logger.warn("解码后的PCM数据为空，无法生成WAV文件: {}", relativePath);
            throw new IOException("解码失败或无有效数据");
        }

        // 4. 添加WAV头
        // 参数: PCM数据, 采样率 16000Hz, 1个通道 (mono), 16位深度
        byte[] wavData = WavUtil.addWavHeader(pcmData, 16000, 1, 16);

        // 确保父目录存在
        Path parentDir = targetFilePath.getParent();
        if (parentDir != null && Files.notExists(parentDir)) {
            Files.createDirectories(parentDir);
        }

        // 6. 保存WAV文件
        Files.write(targetFilePath, wavData);
        logger.info("成功生成WAV文件: {}", targetFilePath);

        // 7. 返回相对路径
        return wavRelativePath;
    }
} 