package cl.benji.pedidos360.msproductos.repository;

import cl.benji.pedidos360.msproductos.entity.Producto;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductoRepository extends JpaRepository<Producto, Long> {
}
