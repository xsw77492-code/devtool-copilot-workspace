package com.devtoolcopilot.docgen.dto;

import lombok.Data;

import java.util.List;

@Data
public class DocxOutline {
    private String title;
    private List<DocxSection> sections;

    @Data
    public static class DocxSection {
        private String heading;
        private List<String> paragraphs;
        private List<String> bullets;
    }
}

