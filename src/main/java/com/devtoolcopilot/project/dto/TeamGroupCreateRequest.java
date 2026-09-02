package com.devtoolcopilot.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TeamGroupCreateRequest {
    @NotBlank
    @Size(max = 64)
    private String name;
}
