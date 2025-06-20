package com.goodsop.file.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * WAV音频文件工具类
 * <p>
 * 提供将原始PCM数据添加WAV头的功能，生成可播放的WAV文件。
 * </p>
 */
public class WavUtil {

    /**
     * 将PCM数据转换为WAV格式的字节数组
     *
     * @param pcmData    原始PCM音频数据
     * @param sampleRate 采样率 (e.g., 16000)
     * @param channels   通道数 (e.g., 1 for mono, 2 for stereo)
     * @param bitDepth   位深度 (e.g., 16)
     * @return 包含WAV头的完整音频数据
     * @throws IOException IO异常
     */
    public static byte[] addWavHeader(byte[] pcmData, int sampleRate, int channels, int bitDepth) throws IOException {
        long audioDataLen = pcmData.length;
        long totalDataLen = audioDataLen + 36;
        long byteRate = (long) sampleRate * channels * bitDepth / 8;
        int blockAlign = channels * bitDepth / 8;

        byte[] header = new byte[44];

        // RIFF/WAVE header
        header[0] = 'R';
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';

        // 'fmt ' chunk
        header[12] = 'f';
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = 16;  // 'fmt ' chunk size
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = 1;   // format = 1 (PCM)
        header[21] = 0;
        header[22] = (byte) channels;
        header[23] = 0;

        // Sample Rate
        header[24] = (byte) (sampleRate & 0xff);
        header[25] = (byte) ((sampleRate >> 8) & 0xff);
        header[26] = (byte) ((sampleRate >> 16) & 0xff);
        header[27] = (byte) ((sampleRate >> 24) & 0xff);

        // Byte Rate
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);

        // Block Align
        header[32] = (byte) blockAlign;
        header[33] = 0;

        // Bits per sample
        header[34] = (byte) bitDepth;
        header[35] = 0;

        // 'data' chunk header
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (audioDataLen & 0xff);
        header[41] = (byte) ((audioDataLen >> 8) & 0xff);
        header[42] = (byte) ((audioDataLen >> 16) & 0xff);
        header[43] = (byte) ((audioDataLen >> 24) & 0xff);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            out.write(header);
            out.write(pcmData);
            return out.toByteArray();
        }
    }
} 