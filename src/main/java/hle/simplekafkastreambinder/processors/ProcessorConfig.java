package hle.simplekafkastreambinder.processors;

import hle.simplekafkastreambinder.dto.RatingResult;
import hle.simplekafkastreambinder.dto.ScoreResult;
import hle.simplekafkastreambinder.service.RatingService;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.serializer.JsonSerde;

import java.time.Duration;
import java.util.function.BiFunction;

@Configuration
public class ProcessorConfig {

    @Bean
    public Serde<ScoreResult> scoreResultJsonSerde(){
        return new JsonSerde<>(ScoreResult.class);
    }

    @Bean
    public Serde<RatingResult> ratingResultJsonSerde() {
        return new JsonSerde<>(RatingResult.class);
    }

    @Bean
    public BiFunction<KStream<String, ScoreResult>, KStream<String, ScoreResult>, KStream<String, RatingResult>> normalPath(RatingService ratingService) {
        return (pdStream, shieldStream) -> pdStream.join(shieldStream, ratingService::integrateRate
        , JoinWindows.of(Duration.ofMinutes(1)),
            StreamJoined.with(Serdes.String(), scoreResultJsonSerde(), scoreResultJsonSerde()));
    }

    // KStream: groupByKey -> windowBy -> Reduce -> Suppress -> toStream -> FilterNot
    // KTable: Materialize -> suppress -> toStream -> FilterNot
    @Bean
    public BiFunction<KTable<String, ScoreResult>, KTable<String, ScoreResult>, KStream<String, RatingResult>> windowFinalPath(RatingService ratingService) {
        return (pdTable, shieldTable) -> pdTable.outerJoin(shieldTable, (pdScore, shieldScore) -> {
            if (pdScore == null) {
                return String.format("%s pd absence", shieldScore.getName());
            } else if (shieldScore == null) {
                return String.format("%s shield absence", pdScore.getName());
            } else {
                return "normal";
            }
        }, Materialized.with(Serdes.String(), Serdes.String()))
                .suppress(Suppressed.untilTimeLimit(Duration.ofMinutes(1), Suppressed.BufferConfig.unbounded()))
                .toStream()
                .filterNot((k,v) -> v.equals("normal"))
                .map((k,v) -> KeyValue.pair(k, ratingService.sideAbsenceRate(v)));
    }
}
