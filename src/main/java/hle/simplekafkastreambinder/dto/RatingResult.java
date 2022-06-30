package hle.simplekafkastreambinder.dto;

import hle.simplekafkastreambinder.model.ScoreRating;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class RatingResult {
    String name;
    Integer mergedScore;
    ScoreRating level;
}
