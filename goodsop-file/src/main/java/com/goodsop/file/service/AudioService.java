package com.goodsop.file.service;

import java.io.IOException;

/**
 * 音频处理服务接口
 */
public interface AudioService {

    /**
     * 解析Speex编码的 .bin 文件并将其转换为 .wav 文件
     *
     * @param relativePath 要解析的 .bin 文件的相对路径
     * @param skipHeader   是否跳过16字节的协议头
     * @return 生成的 .wav 文件的相对路径
     * @throws IOException 如果文件读写或解析过程中发生错误
     */
    String parseSpeexToWav(String relativePath, boolean skipHeader) throws IOException;
} 