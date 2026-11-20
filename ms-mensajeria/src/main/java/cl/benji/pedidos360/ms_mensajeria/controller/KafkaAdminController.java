package cl.benji.pedidos360.ms_mensajeria.controller;

import java.util.List;
import java.util.Map;

import cl.benji.pedidos360.ms_mensajeria.dto.kafka.ClusterInfo;
import cl.benji.pedidos360.ms_mensajeria.dto.kafka.ConfigsRequest;
import cl.benji.pedidos360.ms_mensajeria.dto.kafka.ConsumerGroupDetalle;
import cl.benji.pedidos360.ms_mensajeria.dto.kafka.ConsumerGroupResumen;
import cl.benji.pedidos360.ms_mensajeria.dto.kafka.ParticionesRequest;
import cl.benji.pedidos360.ms_mensajeria.dto.kafka.ResetOffsetsRequest;
import cl.benji.pedidos360.ms_mensajeria.dto.kafka.TopicoDetalle;
import cl.benji.pedidos360.ms_mensajeria.dto.kafka.TopicoRequest;
import cl.benji.pedidos360.ms_mensajeria.dto.kafka.TopicoResumen;
import cl.benji.pedidos360.ms_mensajeria.service.KafkaAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administrador de Kafka (EP5): topicos, configs, particiones y consumer
 * groups. Como RabbitAdminController, se protege a nivel de red: el puerto
 * 8083 no esta abierto en el security group y se usa por tunel SSH.
 */
@RestController
@RequestMapping("/admin/kafka")
@RequiredArgsConstructor
public class KafkaAdminController {

    private final KafkaAdminService kafkaAdminService;

    @GetMapping("/cluster")
    public ClusterInfo cluster() {
        return kafkaAdminService.describirCluster();
    }

    // ---------------------------------------------------------------- topicos

    @GetMapping("/topics")
    public List<TopicoResumen> listarTopicos(@RequestParam(defaultValue = "false") boolean internos) {
        return kafkaAdminService.listarTopicos(internos);
    }

    @GetMapping("/topics/{nombre}")
    public TopicoDetalle describirTopico(@PathVariable String nombre) {
        return kafkaAdminService.describirTopico(nombre);
    }

    @PostMapping("/topics")
    public ResponseEntity<TopicoDetalle> crearTopico(@Valid @RequestBody TopicoRequest request) {
        kafkaAdminService.crearTopico(request);
        return ResponseEntity.status(201).body(kafkaAdminService.describirTopico(request.nombre()));
    }

    @DeleteMapping("/topics/{nombre}")
    public ResponseEntity<Void> eliminarTopico(@PathVariable String nombre) {
        kafkaAdminService.eliminarTopico(nombre);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/topics/{nombre}/configs")
    public Map<String, String> modificarConfigs(@PathVariable String nombre, @RequestBody ConfigsRequest request) {
        return kafkaAdminService.modificarConfigs(nombre, request);
    }

    @PostMapping("/topics/{nombre}/particiones")
    public TopicoDetalle aumentarParticiones(@PathVariable String nombre,
                                             @Valid @RequestBody ParticionesRequest request) {
        kafkaAdminService.aumentarParticiones(nombre, request.total());
        return kafkaAdminService.describirTopico(nombre);
    }

    // -------------------------------------------------------- consumer groups

    @GetMapping("/consumer-groups")
    public List<ConsumerGroupResumen> listarConsumerGroups() {
        return kafkaAdminService.listarConsumerGroups();
    }

    @GetMapping("/consumer-groups/{groupId}")
    public ConsumerGroupDetalle describirConsumerGroup(@PathVariable String groupId) {
        return kafkaAdminService.describirConsumerGroup(groupId);
    }

    @PostMapping("/consumer-groups/{groupId}/reset-offsets")
    public ConsumerGroupDetalle resetearOffsets(@PathVariable String groupId,
                                                @Valid @RequestBody ResetOffsetsRequest request) {
        return kafkaAdminService.resetearOffsets(groupId, request);
    }

    @DeleteMapping("/consumer-groups/{groupId}")
    public ResponseEntity<Void> eliminarConsumerGroup(@PathVariable String groupId) {
        kafkaAdminService.eliminarConsumerGroup(groupId);
        return ResponseEntity.noContent().build();
    }
}
