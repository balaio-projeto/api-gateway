package br.com.balaio.api_gateway;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Substitui a página de erro padrão do Spring: as respostas de erro do gateway usam o mesmo envelope
 * {dados, erro} das demais. Mensagens são fixas, sem detalhes internos.
 */
@RestController
class GatewayErrorController implements ErrorController {

    @RequestMapping(path = "/error", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> error(HttpServletRequest request) {
        Object raw = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = raw instanceof Integer code ? code : 500;
        String codigo;
        String mensagem;
        if (status == 404) {
            codigo = "RECURSO_NAO_ENCONTRADO";
            mensagem = "Recurso não encontrado.";
        } else if (status >= 500) {
            codigo = "ERRO_INTERNO";
            mensagem = "Não foi possível concluir a requisição. Tente novamente.";
        } else {
            codigo = "REQUISICAO_INVALIDA";
            mensagem = "Requisição inválida.";
        }
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"dados\":null,\"erro\":{\"codigo\":\"" + codigo + "\",\"mensagem\":\"" + mensagem + "\"}}");
    }
}
