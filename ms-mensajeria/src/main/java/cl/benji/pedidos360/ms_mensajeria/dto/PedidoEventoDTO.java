package cl.benji.pedidos360.ms_mensajeria.dto;

import java.time.Instant;

public class PedidoEventoDTO {

    private Long pedidoId;
    private String usuarioEmail;
    private String estado;
    private Integer total;
    private Instant fechaEvento;

    public PedidoEventoDTO() {
    }

    public Long getPedidoId() { return pedidoId; }
    public void setPedidoId(Long pedidoId) { this.pedidoId = pedidoId; }

    public String getUsuarioEmail() { return usuarioEmail; }
    public void setUsuarioEmail(String usuarioEmail) { this.usuarioEmail = usuarioEmail; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public Integer getTotal() { return total; }
    public void setTotal(Integer total) { this.total = total; }

    public Instant getFechaEvento() { return fechaEvento; }
    public void setFechaEvento(Instant fechaEvento) { this.fechaEvento = fechaEvento; }

    @Override
    public String toString() {
        return "PedidoEventoDTO{pedidoId=" + pedidoId + ", usuarioEmail='" + usuarioEmail +
                "', estado='" + estado + "', total=" + total + ", fechaEvento=" + fechaEvento + '}';
    }
}