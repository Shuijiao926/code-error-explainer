package com.org.codeerrorexplainerdemo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.org.codeerrorexplainerdemo.entity.ErrorTemplate;
import com.org.codeerrorexplainerdemo.mapper.ErrorTemplateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 内置异常知识卡片查询。
 */
@Service
@RequiredArgsConstructor
public class TemplateService {

    private final ErrorTemplateMapper templateMapper;

    public List<ErrorTemplate> list(String keyword) {
        LambdaQueryWrapper<ErrorTemplate> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(ErrorTemplate::getErrorType, keyword)
                    .or().like(ErrorTemplate::getCategory, keyword));
        }
        wrapper.orderByAsc(ErrorTemplate::getErrorType);
        return templateMapper.selectList(wrapper);
    }
}
