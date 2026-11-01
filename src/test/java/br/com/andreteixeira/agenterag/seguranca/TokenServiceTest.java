package br.com.andreteixeira.agenterag.seguranca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.andreteixeira.agenterag.limite.RelogioMutavel;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.OptionalLong;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * O token da sessão contra o Nimbus de verdade (o mesmo encoder e decoder da aplicação,
 * montados por {@link SegurancaConfig}) e um relógio que anda sob controle do teste. Os segredos
 * daqui são valores falsos de teste.
 */
class TokenServiceTest {

    // 64 bytes: também serve para assinar com HS512 nos casos de algoritmo trocado.
    private static final String SEGREDO = "segredo-falso-de-teste-com-64-bytes-para-o-hs512-nao-e-de-verdade!";
    private static final String OUTRO_SEGREDO = "outro-segredo-falso-de-teste-com-64-bytes-que-nao-e-o-da-aplicacao";
    private static final Instant INICIO = Instant.parse("2026-10-21T15:00:00Z");

    private final RelogioMutavel relogio = new RelogioMutavel(INICIO);
    private final SegurancaConfig config = new SegurancaConfig();

    private TokenService tokens(String segredo) {
        AuthProperties auth = new AuthProperties(segredo, true, Duration.ofHours(8));
        SecretKey chave = config.chaveDoToken(auth);
        return new TokenService(config.jwtEncoder(chave), config.jwtDecoder(chave, relogio), relogio, auth);
    }

    @Test
    void oTokenEmitidoValeEDevolveOIdDaConta() {
        TokenService tokens = tokens(SEGREDO);

        OptionalLong id = tokens.validar(tokens.emitir(42));

        assertThat(id).hasValue(42);
    }

    @Test
    void oTokenNaoLevaPapelNemEmpresaNemOutroDado() throws Exception {
        TokenService tokens = tokens(SEGREDO);

        String payload = new String(Base64.getUrlDecoder().decode(tokens.emitir(42).split("\\.")[1]), StandardCharsets.UTF_8);

        assertThat(payload).contains("\"sub\":\"42\"", "\"iss\":\"sabia\"", "\"iat\":", "\"exp\":");
        assertThat(payload).doesNotContain("papel", "empresa", "usuario", "role");
    }

    @Test
    void oTokenVale8HorasEExpiraDepois() {
        TokenService tokens = tokens(SEGREDO);
        String token = tokens.emitir(42);

        relogio.definir(INICIO.plus(Duration.ofHours(8)).minusSeconds(1));
        assertThat(tokens.validar(token)).as("1 s antes das 8 h").hasValue(42);

        relogio.definir(INICIO.plus(Duration.ofHours(8)).plusSeconds(1));
        assertThat(tokens.validar(token)).as("1 s depois das 8 h").isEmpty();
    }

    @Test
    void tokenAssinadoComOutraChaveEhRecusado() {
        String deOutro = tokens(OUTRO_SEGREDO).emitir(42);

        assertThat(tokens(SEGREDO).validar(deOutro)).isEmpty();
    }

    @Test
    void assinaturaTrocadaEhRecusada() {
        TokenService tokens = tokens(SEGREDO);
        String[] partes = tokens.emitir(42).split("\\.");
        String assinaturaDeOutro = tokens(OUTRO_SEGREDO).emitir(42).split("\\.")[2];

        assertThat(tokens.validar(partes[0] + "." + partes[1] + "." + assinaturaDeOutro)).isEmpty();
    }

    @Test
    void payloadAdulteradoComAAssinaturaOriginalEhRecusado() {
        TokenService tokens = tokens(SEGREDO);
        String[] partes = tokens.emitir(42).split("\\.");
        String payloadDoOutroId = tokens.emitir(1).split("\\.")[1];

        assertThat(tokens.validar(partes[0] + "." + payloadDoOutroId + "." + partes[2])).isEmpty();
    }

    @Test
    void tokenSemAssinaturaComAlgNoneEhRecusado() {
        String cabecalho = base64Url("{\"alg\":\"none\"}");
        String payload = base64Url("{\"iss\":\"sabia\",\"sub\":\"42\",\"exp\":" + INICIO.plusSeconds(3600).getEpochSecond() + "}");

        assertThat(tokens(SEGREDO).validar(cabecalho + "." + payload + ".")).isEmpty();
    }

    @Test
    void tokenAssinadoComAChaveCertaMasOutroAlgoritmoEhRecusado() throws Exception {
        String hs512 = assinar(JWSAlgorithm.HS512, SEGREDO, "sabia", "42", INICIO.plusSeconds(3600));

        assertThat(tokens(SEGREDO).validar(hs512)).as("só HS256").isEmpty();
    }

    @Test
    void tokenComEmissorDiferenteEhRecusado() throws Exception {
        String deOutroEmissor = assinar(JWSAlgorithm.HS256, SEGREDO, "outro-emissor", "42", INICIO.plusSeconds(3600));

        assertThat(tokens(SEGREDO).validar(deOutroEmissor)).isEmpty();
    }

    @Test
    void tokenSemValidadeEhRecusado() throws Exception {
        String semExp = assinar(JWSAlgorithm.HS256, SEGREDO, "sabia", "42", null);

        assertThat(tokens(SEGREDO).validar(semExp)).isEmpty();
    }

    @Test
    void tokenValidoComSubjectQueNaoEhUmIdEhRecusado() throws Exception {
        String subjectDeTexto = assinar(JWSAlgorithm.HS256, SEGREDO, "sabia", "admin", INICIO.plusSeconds(3600));

        assertThat(tokens(SEGREDO).validar(subjectDeTexto)).isEmpty();
    }

    @Test
    void lixoNuloEVazioNuncaLancamEViramVazio() {
        TokenService tokens = tokens(SEGREDO);

        assertThat(tokens.validar(null)).isEmpty();
        assertThat(tokens.validar("")).isEmpty();
        assertThat(tokens.validar("   ")).isEmpty();
        assertThat(tokens.validar("lixo")).isEmpty();
        assertThat(tokens.validar("a.b.c")).isEmpty();
        assertThat(tokens.validar("...")).isEmpty();
    }

    @Test
    void segredoComMenosDe32BytesImpedeOBoot() {
        assertThatThrownBy(() -> config.chaveDoToken(new AuthProperties("curto-demais", true, Duration.ofHours(8))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_AUTH_JWT_SECRET")
                .hasMessageContaining("32 bytes")
                .satisfies(erro -> assertThat(erro.getMessage()).doesNotContain("curto-demais"));
    }

    @Test
    void segredoDeExatamente32BytesEhAceito() {
        SecretKey chave = config.chaveDoToken(new AuthProperties("x".repeat(32), true, Duration.ofHours(8)));

        assertThat(chave.getEncoded()).hasSize(32);
        assertThat(chave.getAlgorithm()).isEqualTo("HmacSHA256");
    }

    @Test
    void semSegredoGeraUmAleatorioAvisaEOsTokensNaoValemEmOutroBoot() {
        ListAppender<ILoggingEvent> log = new ListAppender<>();
        log.start();
        Logger logger = (Logger) LoggerFactory.getLogger(SegurancaConfig.class);
        logger.addAppender(log);
        try {
            AuthProperties semSegredo = new AuthProperties("", true, Duration.ofHours(8));
            SecretKey primeiro = config.chaveDoToken(semSegredo);
            SecretKey segundo = config.chaveDoToken(semSegredo);

            assertThat(primeiro.getEncoded()).hasSize(32).isNotEqualTo(segundo.getEncoded());
            assertThat(log.list)
                    .filteredOn(evento -> evento.getLevel() == Level.WARN)
                    .hasSize(2)
                    .allSatisfy(evento -> assertThat(evento.getFormattedMessage())
                            .contains("APP_AUTH_JWT_SECRET")
                            .doesNotContain(java.util.HexFormat.of().formatHex(primeiro.getEncoded())));
        } finally {
            logger.detachAppender(log);
        }
    }

    @Test
    void oTokenDeUmBootSemSegredoNaoValeNoSeguinte() {
        TokenService primeiroBoot = tokens("");
        TokenService segundoBoot = tokens("");

        assertThat(segundoBoot.validar(primeiroBoot.emitir(42))).isEmpty();
        assertThat(primeiroBoot.validar(primeiroBoot.emitir(42))).hasValue(42);
    }

    @Test
    void oToStringDasPropriedadesNaoMostraOSegredo() {
        AuthProperties auth = new AuthProperties(SEGREDO, true, Duration.ofHours(8));

        assertThat(auth.toString()).doesNotContain(SEGREDO).contains("***");
    }

    private static String base64Url(String texto) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(texto.getBytes(StandardCharsets.UTF_8));
    }

    private static String assinar(JWSAlgorithm algoritmo, String segredo, String emissor, String subject, Instant expira)
            throws Exception {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder().issuer(emissor).subject(subject);
        if (expira != null) {
            claims.issueTime(new Date(INICIO.toEpochMilli())).expirationTime(new Date(expira.toEpochMilli()));
        }
        SignedJWT jwt = new SignedJWT(new JWSHeader(algoritmo), claims.build());
        jwt.sign(new MACSigner(segredo.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }
}
