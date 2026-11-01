package br.com.andreteixeira.agenterag.seguranca;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * As respostas de erro de quem não passa pela cadeia de segurança, no formato do resto da API,
 * {@code {"erro": "..."}}: 401 sem login (ou com token inválido ou expirado) e 403 quando o papel
 * não permite. Vivem aqui, e não no {@code GlobalExceptionHandler}, porque a recusa acontece num
 * filtro, antes de qualquer controller.
 */
final class RespostasDeAcesso implements AuthenticationEntryPoint, AccessDeniedHandler {

    static final String NAO_AUTENTICADO = "É preciso entrar para usar este recurso.";
    static final String SEM_PERMISSAO = "Você não tem permissão para esta ação.";

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        escrever(response, HttpServletResponse.SC_UNAUTHORIZED, NAO_AUTENTICADO);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        escrever(response, HttpServletResponse.SC_FORBIDDEN, SEM_PERMISSAO);
    }

    /** Mensagens fixas, escritas no código: só aspas e barra invertida precisam de escape. */
    static void escrever(HttpServletResponse response, int status, String mensagem) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"erro\":\"" + mensagem.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}");
    }
}
