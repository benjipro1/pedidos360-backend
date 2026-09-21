package cl.benji.pedidos360.mspedidos.service;

import java.util.List;

import cl.benji.pedidos360.mspedidos.dto.DetalleItemRequest;
import cl.benji.pedidos360.mspedidos.dto.DetallePedidoResponse;
import cl.benji.pedidos360.mspedidos.dto.PedidoRequest;
import cl.benji.pedidos360.mspedidos.dto.PedidoResponse;
import cl.benji.pedidos360.mspedidos.entity.DetallePedido;
import cl.benji.pedidos360.mspedidos.entity.EstadoPedido;
import cl.benji.pedidos360.mspedidos.entity.Pedido;
import cl.benji.pedidos360.mspedidos.exception.PedidoNoEncontradoException;
import cl.benji.pedidos360.mspedidos.exception.UsuarioNoIdentificadoException;
import cl.benji.pedidos360.mspedidos.repository.PedidoRepository;
import org.springframework.stereotype.Service;

@Service
public class PedidoService {

    private final PedidoRepository pedidoRepository;

    public PedidoService(PedidoRepository pedidoRepository) {
        this.pedidoRepository = pedidoRepository;
    }

    public List<PedidoResponse> listar(String usuarioEmail) {
        List<Pedido> pedidos = (usuarioEmail == null || usuarioEmail.isBlank())
                ? pedidoRepository.findAll()
                : pedidoRepository.findByUsuarioEmail(usuarioEmail);
        return pedidos.stream().map(this::toResponse).toList();
    }

    public PedidoResponse buscarPorId(Long id) {
        return toResponse(obtenerOLanzar(id));
    }

    public PedidoResponse crear(String usuarioEmail, PedidoRequest request) {
        if (usuarioEmail == null || usuarioEmail.isBlank()) {
            throw new UsuarioNoIdentificadoException();
        }

        Pedido pedido = new Pedido(usuarioEmail);
        int total = 0;
        for (DetalleItemRequest item : request.detalles()) {
            DetallePedido detalle = new DetallePedido(
                    item.productoId(),
                    item.nombreProducto(),
                    item.cantidad(),
                    item.precioUnitario()
            );
            pedido.agregarDetalle(detalle);
            total += item.cantidad() * item.precioUnitario();
        }
        pedido.setTotal(total);

        return toResponse(pedidoRepository.save(pedido));
    }

    public PedidoResponse cambiarEstado(Long id, EstadoPedido nuevoEstado) {
        Pedido pedido = obtenerOLanzar(id);
        pedido.setEstado(nuevoEstado);
        return toResponse(pedidoRepository.save(pedido));
    }

    private Pedido obtenerOLanzar(Long id) {
        return pedidoRepository.findById(id)
                .orElseThrow(() -> new PedidoNoEncontradoException(id));
    }

    private PedidoResponse toResponse(Pedido pedido) {
        List<DetallePedidoResponse> detalles = pedido.getDetalles().stream()
                .map(d -> new DetallePedidoResponse(
                        d.getId(),
                        d.getProductoId(),
                        d.getNombreProducto(),
                        d.getCantidad(),
                        d.getPrecioUnitario()
                ))
                .toList();

        return new PedidoResponse(
                pedido.getId(),
                pedido.getUsuarioEmail(),
                pedido.getFechaCreacion(),
                pedido.getEstado(),
                pedido.getTotal(),
                detalles
        );
    }
}
