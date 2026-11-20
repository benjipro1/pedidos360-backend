package cl.benji.pedidos360.msproductos.repository;

import java.util.UUID;

import cl.benji.pedidos360.msproductos.dto.evento.TipoMovimiento;
import cl.benji.pedidos360.msproductos.entity.MovimientoProcesado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MovimientoProcesadoRepository extends JpaRepository<MovimientoProcesado, UUID> {

    /** Unidades realmente aplicadas de un tipo, para un producto dentro de un pedido. */
    @Query("""
            select coalesce(sum(m.cantidadAplicada), 0)
            from MovimientoProcesado m
            where m.pedidoId = :pedidoId and m.productoId = :productoId and m.tipo = :tipo
            """)
    long sumarCantidadAplicada(@Param("pedidoId") Long pedidoId,
                               @Param("productoId") Long productoId,
                               @Param("tipo") TipoMovimiento tipo);
}
