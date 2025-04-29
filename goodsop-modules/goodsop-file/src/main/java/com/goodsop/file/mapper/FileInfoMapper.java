package com.goodsop.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.goodsop.file.entity.FileInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文件信息Mapper接口
 */
@Mapper
public interface FileInfoMapper extends BaseMapper<FileInfo> {
} 