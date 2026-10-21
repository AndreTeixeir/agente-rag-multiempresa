package br.com.andreteixeira.agenterag.limite;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Relógio que só anda quando o teste manda — o jeito de fazer o dia virar sem
 * esperar. {@code withZone} devolve um relógio fixo no instante atual, o que
 * basta porque quem o usa lê a data logo em seguida.
 */
final class RelogioMutavel extends Clock {

    private volatile Instant agora;

    RelogioMutavel(Instant inicio) {
        this.agora = inicio;
    }

    void definir(Instant novo) {
        this.agora = novo;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zona) {
        return Clock.fixed(agora, zona);
    }

    @Override
    public Instant instant() {
        return agora;
    }
}
