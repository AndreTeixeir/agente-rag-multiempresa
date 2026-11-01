package br.com.andreteixeira.agenterag.seguranca;

import br.com.andreteixeira.agenterag.conta.AutenticacaoService;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.header.HeaderWriterFilter;

/**
 * Login por cookie com JWT. Sem sessão no servidor: cada requisição se autentica pelo cookie
 * ({@link SessaoCookieFilter}), e o que cada papel alcança é decidido aqui, por rota.
 * <p>
 * Rotas públicas: a página e os arquivos estáticos, o login e o logout, {@code /health},
 * {@code /actuator/health} e {@code /error}. Todo o resto exige login, e as de administração exigem
 * o papel: {@code /api/contas} para ADMIN e SUPERADMIN, {@code /api/admin} para SUPERADMIN. As
 * regras mais finas (empresa da conta, conta protegida, o próprio usuário) são do serviço de
 * contas, que responde com exceções próprias — não uso {@code @PreAuthorize}: um
 * {@code AccessDeniedException} lançado dentro de um controller seria engolido pelo
 * {@code @ExceptionHandler(Exception.class)} do {@code GlobalExceptionHandler} e viraria 500.
 * <p>
 * Um bean {@link JwtDecoder} existir faz a autoconfiguração do Spring Boot NÃO criar o usuário em
 * memória com a senha gerada, que ela imprimiria no log ("Using generated security password").
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(AuthProperties.class)
public class SegurancaConfig {

    private static final Logger log = LoggerFactory.getLogger(SegurancaConfig.class);

    /** HS256 pede uma chave de pelo menos 256 bits. */
    static final int TAMANHO_MINIMO_DO_SEGREDO_EM_BYTES = 32;

    /**
     * A chave de assinatura do token. Sem {@code APP_AUTH_JWT_SECRET}, uma chave aleatória por boot
     * (cômodo no notebook; os tokens deixam de valer a cada reinício) e um aviso. Com a variável,
     * ela é usada como está, em UTF-8, e precisa ter pelo menos 32 bytes — um segredo curto
     * demais impede o app de subir, em vez de enfraquecer a assinatura em silêncio.
     */
    @Bean
    SecretKey chaveDoToken(AuthProperties auth) {
        byte[] bytes;
        if (auth.jwtSecret().isBlank()) {
            bytes = new byte[TAMANHO_MINIMO_DO_SEGREDO_EM_BYTES];
            new SecureRandom().nextBytes(bytes);
            log.warn("APP_AUTH_JWT_SECRET não está definida: o segredo do token foi gerado agora, ao acaso, "
                    + "e as sessões deixam de valer a cada reinício. Em produção, defina a variável.");
        } else {
            bytes = auth.jwtSecret().getBytes(StandardCharsets.UTF_8);
            if (bytes.length < TAMANHO_MINIMO_DO_SEGREDO_EM_BYTES) {
                throw new IllegalStateException("APP_AUTH_JWT_SECRET precisa ter pelo menos "
                        + TAMANHO_MINIMO_DO_SEGREDO_EM_BYTES + " bytes (por exemplo, a saída de: openssl rand -base64 48).");
            }
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey chaveDoToken) {
        return NimbusJwtEncoder.withSecretKey(chaveDoToken).algorithm(MacAlgorithm.HS256).build();
    }

    /**
     * Só HS256 (um token com outro {@code alg}, inclusive {@code none}, é recusado), emissor
     * {@code sabia} e validade conferida pelo {@link Clock} da aplicação, sem folga de relógio e
     * com {@code exp} obrigatório.
     */
    @Bean
    JwtDecoder jwtDecoder(SecretKey chaveDoToken, Clock relogio) {
        NimbusJwtDecoder decoder =
                NimbusJwtDecoder.withSecretKey(chaveDoToken).macAlgorithm(MacAlgorithm.HS256).build();
        JwtTimestampValidator validade = new JwtTimestampValidator(Duration.ZERO);
        validade.setClock(relogio);
        // Por padrão o validador aceita um token SEM exp (valeria para sempre). O nosso sempre tem.
        validade.setAllowEmptyExpiryClaim(false);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validade, new JwtIssuerValidator(TokenService.EMISSOR)));
        return decoder;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain cadeiaDeSeguranca(
            HttpSecurity http, AuthProperties auth, TokenService tokens, AutenticacaoService autenticacao)
            throws Exception {
        RespostasDeAcesso respostas = new RespostasDeAcesso();
        http
                // Sem o CSRF do Spring: a barreira é o OrigemFilter (ver o javadoc dele).
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                // A política de TLS é do Caddy. O HSTS padrão do Spring Security (um ano, e
                // sub-domínios) é uma decisão sobre o domínio inteiro que esta aplicação não deve tomar.
                .headers(cabecalhos -> cabecalhos.httpStrictTransportSecurity(HeadersConfigurer.HstsConfig::disable))
                .exceptionHandling(erros -> erros.authenticationEntryPoint(respostas).accessDeniedHandler(respostas))
                .authorizeHttpRequests(rotas -> rotas
                        .requestMatchers("/", "/index.html", "/favicon.svg", "/sabia-banner.jpg", "/error")
                        .permitAll()
                        .requestMatchers("/health", "/actuator/health")
                        .permitAll()
                        .requestMatchers("/api/auth/login", "/api/auth/logout")
                        .permitAll()
                        .requestMatchers("/api/contas", "/api/contas/**")
                        .hasAnyRole("ADMIN", "SUPERADMIN")
                        .requestMatchers("/api/admin/**")
                        .hasRole("SUPERADMIN")
                        .anyRequest()
                        .authenticated())
                .addFilterAfter(new OrigemFilter(auth), HeaderWriterFilter.class)
                .addFilterBefore(new SessaoCookieFilter(auth, tokens, autenticacao), AnonymousAuthenticationFilter.class);
        return http.build();
    }
}
