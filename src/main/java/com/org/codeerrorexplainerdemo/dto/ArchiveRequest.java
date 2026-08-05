package com.org.codeerrorexplainerdemo.dto;

import lombok.Data;

/**
 * PUT /api/history/{id}/archive 请求体：沉淀/取消沉淀为踩坑档案，并可选更新标签。
 */
@Data
public class ArchiveRequest {

    /** true=沉淀为踩坑档案，false=取消沉淀 */
    private Boolean archived;

    /** 逗号分隔标签（可空；传空串表示清空标签，不传表示保持原值） */
    private String tags;
}
