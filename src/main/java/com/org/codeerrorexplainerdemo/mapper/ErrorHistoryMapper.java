package com.org.codeerrorexplainerdemo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.org.codeerrorexplainerdemo.entity.ErrorHistory;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ErrorHistoryMapper extends BaseMapper<ErrorHistory> {
}
