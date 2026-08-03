package com.org.codeerrorexplainerdemo;

import com.org.codeerrorexplainerdemo.entity.ErrorTemplate;
import com.org.codeerrorexplainerdemo.service.TemplateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 内置知识卡片查询（H2 内存库 + 种子）。
 */
@SpringBootTest
@ActiveProfiles("test")
class TemplateServiceTest {

    @Autowired
    private TemplateService templateService;

    @Test
    void listAll_returnsSeededCards() {
        List<ErrorTemplate> all = templateService.list(null);
        assertThat(all).isNotEmpty();
        assertThat(all).extracting(ErrorTemplate::getErrorType)
                .contains("NullPointerException", "SQLException");
        assertThat(all).isSortedAccordingTo(
                (a, b) -> a.getErrorType().compareToIgnoreCase(b.getErrorType()));
    }

    @Test
    void searchByKeyword_filtersCards() {
        List<ErrorTemplate> byType = templateService.list("SQL");
        assertThat(byType).extracting(ErrorTemplate::getErrorType).contains("SQLException");

        List<ErrorTemplate> byCategory = templateService.list("空指针");
        assertThat(byCategory).extracting(ErrorTemplate::getErrorType).contains("NullPointerException");

        assertThat(templateService.list("不存在的关键字")).isEmpty();
    }
}
