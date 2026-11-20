package cl.benji.pedidos360.msanalitica.controller;

import cl.benji.pedidos360.msanalitica.dto.ResumenAnaliticaResponse;
import cl.benji.pedidos360.msanalitica.service.AnaliticaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * KPIs en tiempo real. Puerto 8084 sin exponer en el security group: se
 * consulta por tunel SSH durante la demo (no pasa por API Gateway ni BFF).
 */
@RestController
@RequestMapping("/analitica")
public class AnaliticaController {

    private final AnaliticaService analiticaService;

    public AnaliticaController(AnaliticaService analiticaService) {
        this.analiticaService = analiticaService;
    }

    @GetMapping("/resumen")
    public ResumenAnaliticaResponse resumen() {
        return analiticaService.resumen();
    }
}
