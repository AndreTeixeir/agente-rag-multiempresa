package br.com.andreteixeira.agenterag.seguranca;

import java.time.Clock;
import java.time.Instant;
import java.util.OptionalLong;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

/**
 * Emite e confere o token da sessão: um JWT HS256 com {@code iss}, {@code sub} (o id da conta),
 * {@code iat} e {@code exp}, e mais nada. Papel e empresa NÃO vão no token — são lidos do banco a
 * cada requisição ({@link ContaAutenticada}).
 * <p>
 * O relógio é o bean {@link Clock} da aplicação, o mesmo do decoder (ver
 * {@link SegurancaConfig#jwtDecoder}), então os testes fazem o token expirar sem esperar.
 */
@Service
public class TokenService {

    static final String EMISSOR = "sabia";

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final Clock relogio;
    private final AuthProperties auth;

    public TokenService(JwtEncoder encoder, JwtDecoder decoder, Clock relogio, AuthProperties auth) {
        this.encoder = encoder;
        this.decoder = decoder;
        this.relogio = relogio;
        this.auth = auth;
    }

    public String emitir(long contaId) {
        Instant agora = relogio.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(EMISSOR)
                .subject(Long.toString(contaId))
                .issuedAt(agora)
                .expiresAt(agora.plus(auth.duracaoDaSessao()))
                .build();
        JwsHeader cabecalho = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(cabecalho, claims)).getTokenValue();
    }

    /**
     * O id da conta dona do token, ou vazio se o token for inválido: assinatura errada, algoritmo
     * diferente de HS256, emissor diferente, expirado ou malformado. Nunca lança por causa do token.
     */
    public OptionalLong validar(String token) {
        if (token == null || token.isBlank()) {
            return OptionalLong.empty();
        }
        try {
            Jwt jwt = decoder.decode(token);
            return OptionalLong.of(Long.parseLong(jwt.getSubject()));
        } catch (JwtException | NumberFormatException e) {
            return OptionalLong.empty();
        }
    }
}
