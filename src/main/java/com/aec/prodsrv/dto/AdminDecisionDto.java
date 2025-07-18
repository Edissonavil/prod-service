package com.aec.prodsrv.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminDecisionDto {
    @NotNull
    private Boolean aprobar;  // true = aprobar, false = rechazar
    private String  comentario; // opcional – para logging futuro
}
