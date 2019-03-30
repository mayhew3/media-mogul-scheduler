package com.mayhew3.mediamogulscheduler.model.tv.group;

import com.mayhew3.postgresobject.dataobject.*;

public class TVGroupBallot extends RetireableDataObject {

  public FieldValueTimestamp voting_open = registerTimestampField("voting_open", Nullability.NOT_NULL).defaultValueNow();
  public FieldValueString reason = registerStringField("reason", Nullability.NULLABLE);
  public FieldValueForeignKey tv_group_series = registerForeignKey(new TVGroupSeries(), Nullability.NOT_NULL);

  public TVGroupBallot() {
    registerTimestampField("voting_closed", Nullability.NULLABLE);
    registerIntegerField("last_episode", Nullability.NULLABLE);
    registerIntegerField("first_episode", Nullability.NULLABLE);
  }

  @Override
  public String getTableName() {
    return "tv_group_ballot";
  }
}
