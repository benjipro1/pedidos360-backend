package cl.benji.pedidos360.msproductos.exception;

public class ProductoNoEncontradoException extends RuntimeException {

    public ProductoNoEncontradoException(Long id) {
        super("no existe un producto con id " + id);
    }
}
