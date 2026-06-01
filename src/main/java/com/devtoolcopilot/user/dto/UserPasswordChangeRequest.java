package com.devtoolcopilot.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserPasswordChangeRequest {
    @NotBlank
    private String oldPassword;

    @NotBlank
    @Size(min = 12, max = 64)
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^\\w\\s]).+$")
    private String newPassword;
}
