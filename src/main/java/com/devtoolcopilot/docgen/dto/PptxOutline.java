package com.devtoolcopilot.docgen.dto;

import lombok.Data;

import java.util.List;

@Data
public class PptxOutline {
    private String title;
    private String subtitle;
    private String style;
    private List<PptxSlide> slides;

    @Data
    public static class PptxSlide {
        private String title;
        private List<String> bullets;
    }
}
