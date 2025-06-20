package com.goodsop.file.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xiph.speex.SpeexDecoder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * Speex音频解码工具类
 * <p>
 * 用于将Speex编码的音频数据解码为PCM原始音频数据。
 * 支持带16字节自定义协议头的音频文件。
 * </p>
 */
public class SpeexUtil {

    private static final Logger logger = LoggerFactory.getLogger(SpeexUtil.class);

    /**
     * 音频采样率 (16kHz, 宽带)
     */
    private static final int SAMPLE_RATE = 16000;

    /**
     * 音频通道数 (1 for mono)
     */
    private static final int CHANNELS = 1;

    // 根据协议文档，帧标识头固定为 0x5a5aa5a5, WiFi端接收为 a5 a5 5a 5a，两种都支持
    private static final byte[] HEADER_MAGIC_1 = new byte[]{(byte) 0x5A, (byte) 0x5A, (byte) 0xA5, (byte) 0xA5};
    private static final byte[] HEADER_MAGIC_2 = new byte[]{(byte) 0xA5, (byte) 0xA5, (byte) 0x5A, (byte) 0x5A};
    private static final int PROTOCOL_HEADER_SIZE = 16;

    /**
     * 解码Speex数据到PCM格式
     *
     * @param fileData  包含Speex编码的音频文件原始数据
     * @param hasHeader 文件是否包含16字节的自定义协议头
     * @return PCM格式的音频数据
     */
    public static byte[] decode(byte[] fileData, boolean hasHeader) {
        if (fileData == null || fileData.length == 0) {
            return new byte[0];
        }

        byte[] speexPayload;

        if (hasHeader) {
            if (fileData.length < PROTOCOL_HEADER_SIZE) {
                logger.error("数据长度小于{}字节，无法处理协议头。", PROTOCOL_HEADER_SIZE);
                return new byte[0];
            }

            // 1. 验证4字节的帧标识头
            byte[] headerMagic = Arrays.copyOfRange(fileData, 0, 4);
            if (!Arrays.equals(headerMagic, HEADER_MAGIC_1) && !Arrays.equals(headerMagic, HEADER_MAGIC_2)) {
                logger.error("无效的帧标识头。期望为 5A5AA5A5 或 A5A55A5A，但接收到: {}", bytesToHex(headerMagic));
                return new byte[0];
            }

            // 2. 从头部（偏移量8）提取2字节的数据长度n (小端格式)
            int payloadLength = ((fileData[9] & 0xFF) << 8) | (fileData[8] & 0xFF);

            // 3. 校验文件总长度是否与头部信息一致
            int expectedTotalLength = PROTOCOL_HEADER_SIZE + payloadLength;
            if (fileData.length < expectedTotalLength) {
                logger.error("文件数据不完整。头部声明数据长度为 {} 字节，但文件总大小为 {} 字节。", payloadLength, fileData.length);
                return new byte[0];
            }
            if (fileData.length > expectedTotalLength) {
                logger.warn("文件实际大小 ({}) 超出头部声明大小 ({})。将只处理声明长度的数据。", fileData.length, expectedTotalLength);
            }
            
            // 4. TODO: 实现校验和 (偏移量4, 2字节)
            // 协议中未明确校验和算法，暂时跳过。

            // 5. 提取实际的Speex数据负载
            speexPayload = new byte[payloadLength];
            System.arraycopy(fileData, PROTOCOL_HEADER_SIZE, speexPayload, 0, payloadLength);

        } else {
            // 文件不包含协议头，整个文件都是Speex数据
            speexPayload = fileData;
        }

        // 初始化Speex解码器
        SpeexDecoder decoder = new SpeexDecoder();
        decoder.init(1, SAMPLE_RATE, CHANNELS, false);

        // 使用动态帧长逻辑解码Speex负载
        try (ByteArrayOutputStream pcmStream = new ByteArrayOutputStream()) {
            byte[] pcmBuffer = new byte[1024];
            int currentPosition = 0;

            while (currentPosition < speexPayload.length) {
                // 每个数据帧的第一个字节是该帧的数据长度
                int frameDataLength = speexPayload[currentPosition] & 0xFF;
                int dataStartPosition = currentPosition + 1;

                // 检查是否有足够的数据构成一个完整的帧
                if (dataStartPosition + frameDataLength > speexPayload.length) {
                    logger.warn("在位置 {} 发现不完整的音频帧。期望 {} 字节，但仅剩 {} 字节。停止解析。",
                            currentPosition, frameDataLength, speexPayload.length - dataStartPosition);
                    break;
                }

                if (frameDataLength > 0) {
                    try {
                        decoder.processData(speexPayload, dataStartPosition, frameDataLength);
                        int decodedSize = decoder.getProcessedDataByteSize();

                        if (decodedSize > 0) {
                            if (decodedSize > pcmBuffer.length) {
                                pcmBuffer = new byte[decodedSize];
                            }
                            decoder.getProcessedData(pcmBuffer, 0);
                            pcmStream.write(pcmBuffer, 0, decodedSize);
                        }
                    } catch (Exception e) {
                        logger.error("在位置 {} 解码长度为 {} 的帧时发生错误，跳过此帧。", currentPosition, frameDataLength, e);
                    }
                }

                // 移动到下一帧的起始位置
                currentPosition += (1 + frameDataLength);
            }

            if (pcmStream.size() == 0) {
                logger.warn("解码完成，但未生成任何PCM数据。请检查Speex数据是否有效。");
            } else {
                logger.info("Speex解码完成。处理了 {} 字节的负载，生成了 {} 字节的PCM数据。", speexPayload.length, pcmStream.size());
            }
            return pcmStream.toByteArray();
        } catch (IOException e) {
            logger.error("解码Speex数据时发生严重的IO异常。", e);
            return new byte[0];
        }
    }

    /**
     * 字节数组转十六进制字符串工具
     */
    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
    private static String bytesToHex(byte[] bytes) {
        if (bytes == null) {
            return "";
        }
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }
} 