package cl.benji.pedidos360.mspedidos.exception;

public class PedidoNoEncontradoException extends RuntimeException {

    public PedidoNoEncontradoException(Long id) {
        super("no existe un pedido con id " + id);
    }
}
