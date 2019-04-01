package com.mayhew3.mediamogulscheduler.model.tv;

import com.mayhew3.mediamogulscheduler.model.Person;
import com.mayhew3.postgresobject.dataobject.*;

public class EpisodeRating extends RetireableDataObject {

  public FieldValueForeignKey episodeId = registerForeignKey(new Episode(), Nullability.NOT_NULL);

  public FieldValueBoolean watched = registerBooleanField("watched", Nullability.NOT_NULL).defaultValue(true);
  public FieldValueTimestamp watchedDate = registerTimestampField("watched_date", Nullability.NULLABLE);
  public FieldValueTimestamp ratingDate = registerTimestampField("rating_date", Nullability.NULLABLE);

  public FieldValueBigDecimal ratingFunny = registerBigDecimalField("rating_funny", Nullability.NULLABLE);
  public FieldValueBigDecimal ratingCharacter = registerBigDecimalField("rating_character", Nullability.NULLABLE);
  public FieldValueBigDecimal ratingStory = registerBigDecimalField("rating_story", Nullability.NULLABLE);

  public FieldValueBigDecimal ratingValue = registerBigDecimalField("rating_value", Nullability.NULLABLE);

  public FieldValueString review = registerStringField("review", Nullability.NULLABLE);

  public EpisodeRating() {
    registerForeignKey(new Person(), Nullability.NOT_NULL);
    registerBooleanField("rating_pending", Nullability.NOT_NULL).defaultValue(false);
  }

  @Override
  public String getTableName() {
    return "episode_rating";
  }
}
