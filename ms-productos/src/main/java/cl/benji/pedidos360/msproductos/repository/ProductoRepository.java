package cl.benji.pedidos360.msproductos.repository;

import java.util.Optional;

import cl.benji.pedidos360.msproductos.entity.Producto;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductoRepository extends JpaRepository<Producto, Long> {

    /**
     * Lee el producto bloqueando su fila hasta el fin de la transaccion
     * (SELECT ... FOR UPDATE). Kafka ya serializa los movimientos de un
     * mismo producto (misma key, misma particion); el bloqueo protege ademas
     * frente a otra instancia de ms-productos o a una edicion simultanea
     * desde la vista de administracion.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Producto p where p.id = :id")
    Optional<Producto> buscarParaActualizar(@Param("id") Long id);
}
