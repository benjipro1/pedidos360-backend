package cl.benji.pedidos360.mspedidos.exception;

/**
 * Se lanza si falta la cabecera X-User-Email al crear un pedido. En
 * condiciones normales esto no deberia ocurrir: el BFF siempre la agrega
 * despues de validar el token (ver README, seccion de confianza en el BFF).
 */
public class UsuarioNoIdentificadoException extends RuntimeException {

    public UsuarioNoIdentificadoException() {
        super("falta la cabecera X-User-Email");
    }
}
