package cl.benji.pedidos360.mspedidos.dto;

import cl.benji.pedidos360.mspedidos.entity.EstadoPedido;

import java.time.Instant;

/**
 * Payload que ms-pedidos publica en RabbitMQ. ms-mensajeria lo deserializa
 * en sus listeners para generar notificaciones, documentos y auditoria.
 */
public class PedidoEventoDTO {

    private Long pedidoId;
    private String usuarioEmail;
    private EstadoPedido estado;
    private Integer total;
    private Instant fechaEvento;

    public PedidoEventoDTO() {
    }

    public PedidoEventoDTO(Long pedidoId, String usuarioEmail, EstadoPedido estado, Integer total, Instant fechaEvento) {
        this.pedidoId = pedidoId;
        this.usuarioEmail = usuarioEmail;
        this.estado = estado;
        this.total = total;
        this.fechaEvento = fechaEvento;
    }

    public Long getPedidoId() { return pedidoId; }
    public void setPedidoId(Long pedidoId) { this.pedidoId = pedidoId; }

    public String getUsuarioEmail() { return usuarioEmail; }
    public void setUsuarioEmail(String usuarioEmail) { this.usuarioEmail = usuarioEmail; }

    public EstadoPedido getEstado() { return estado; }
    public void setEstado(EstadoPedido estado) { this.estado = estado; }

    public Integer getTotal() { return total; }
    public void setTotal(Integer total) { this.total = total; }

    public Instant getFechaEvento() { return fechaEvento; }
    public void setFechaEvento(Instant fechaEvento) { this.fechaEvento = fechaEvento; }
}