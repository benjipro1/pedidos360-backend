package cl.benji.pedidos360.msproductos.dto;

/**
 * DTO de salida expuesto por el controlador. Nunca se devuelve la entidad
 * JPA directamente.
 */
public record ProductoResponse(
        Long id,
        String nombre,
        String descripcion,
        Integer precio,
        Integer stock,
        boolean activo
) {
}
