package cl.benji.pedidos360.ms_mensajeria.dto.kafka;

import java.util.Map;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Alta de un topico. Ejemplo:
 * {"nombre":"pedidos.eventos","particiones":3,"replicas":3,
 *  "configs":{"retention.ms":"604800000","cleanup.policy":"delete","min.insync.replicas":"2"}}
 */
public record TopicoRequest(
        @NotBlank @Pattern(regexp = "[a-zA-Z0-9._-]{1,249}", message = "solo letras, numeros, '.', '_' y '-'")
        String nombre,
        @Min(1) @Max(50) int particiones,
        @Min(1) @Max(3) short replicas,
        Map<String, String> configs
) {
}
