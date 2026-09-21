package cl.benji.pedidos360.msproductos.service;

import java.util.List;

import cl.benji.pedidos360.msproductos.dto.ProductoRequest;
import cl.benji.pedidos360.msproductos.dto.ProductoResponse;
import cl.benji.pedidos360.msproductos.entity.Producto;
import cl.benji.pedidos360.msproductos.exception.ProductoNoEncontradoException;
import cl.benji.pedidos360.msproductos.repository.ProductoRepository;
import org.springframework.stereotype.Service;

@Service
public class ProductoService {

    private final ProductoRepository productoRepository;

    public ProductoService(ProductoRepository productoRepository) {
        this.productoRepository = productoRepository;
    }

    public List<ProductoResponse> listar() {
        return productoRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public ProductoResponse buscarPorId(Long id) {
        return toResponse(obtenerOLanzar(id));
    }

    public ProductoResponse crear(ProductoRequest request) {
        boolean activo = request.activo() == null || request.activo();
        Producto producto = new Producto(
                request.nombre(),
                request.descripcion(),
                request.precio(),
                request.stock(),
                activo
        );
        return toResponse(productoRepository.save(producto));
    }

    public ProductoResponse actualizar(Long id, ProductoRequest request) {
        Producto producto = obtenerOLanzar(id);
        producto.setNombre(request.nombre());
        producto.setDescripcion(request.descripcion());
        producto.setPrecio(request.precio());
        producto.setStock(request.stock());
        if (request.activo() != null) {
            producto.setActivo(request.activo());
        }
        return toResponse(productoRepository.save(producto));
    }

    /**
     * Baja logica: no se elimina la fila, se marca como inactivo.
     */
    public void eliminar(Long id) {
        Producto producto = obtenerOLanzar(id);
        producto.setActivo(false);
        productoRepository.save(producto);
    }

    private Producto obtenerOLanzar(Long id) {
        return productoRepository.findById(id)
                .orElseThrow(() -> new ProductoNoEncontradoException(id));
    }

    private ProductoResponse toResponse(Producto producto) {
        return new ProductoResponse(
                producto.getId(),
                producto.getNombre(),
                producto.getDescripcion(),
                producto.getPrecio(),
                producto.getStock(),
                producto.isActivo()
        );
    }
}
