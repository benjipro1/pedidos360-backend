package cl.benji.pedidos360.mspedidos.repository;

import java.util.List;

import cl.benji.pedidos360.mspedidos.entity.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    List<Pedido> findByUsuarioEmail(String usuarioEmail);
}
