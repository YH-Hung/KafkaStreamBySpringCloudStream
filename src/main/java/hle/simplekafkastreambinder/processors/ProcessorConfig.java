package hle.simplekafkastreambinder.processors;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.function.BiFunction;

@Configuration
public class ProcessorConfig {

    @Bean
    public BiFunction<KStream<String, String>, KStream<String, String>, KStream<String, String>> normalPath() {
        return (pdStream, shieldStream) -> pdStream.join(shieldStream, (pdScore, shieldScore) -> {
            try {
                int pdValue = Integer.parseInt(pdScore);
                int shieldValue = Integer.parseInt(shieldScore);
                int integratedScore = pdValue + shieldValue;
                return String.format("pd: %d, shield: %d, integrated: %d", pdValue, shieldValue, integratedScore);
            } catch (NumberFormatException e) {
                return "Bad numeric value";
            }
        }, JoinWindows.of(Duration.ofMinutes(1)), StreamJoined.with(Serdes.String(), Serdes.String(), Serdes.String()))
                .map((k,v) -> KeyValue.pair(k, String.format("%s : %s", k, v)));
    }

    // KStream: groupByKey -> windowBy -> Reduce -> Suppress -> toStream -> FilterNot
    // KTable: Materialize -> suppress -> toStream -> FilterNot
    @Bean
    public BiFunction<KTable<String, String>, KTable<String, String>, KStream<String, String>> windowFinalPath() {
        return (pdTable, shieldTable) -> pdTable.outerJoin(shieldTable, (pdScore, shieldScore) -> {
            if (pdScore == null) {
                return "Absence PD";
            } else if (shieldScore == null) {
                return "Absence Shield";
            } else {
                return "normal";
            }
        }, Materialized.with(Serdes.String(), Serdes.String()))
                .suppress(Suppressed.untilTimeLimit(Duration.ofMinutes(1), Suppressed.BufferConfig.unbounded()))
                .toStream()
                .filterNot((k,v) -> v.equals("normal"))
                .map((k,v) -> KeyValue.pair(k, String.format("%s : %s", k, v)));
    }
}
