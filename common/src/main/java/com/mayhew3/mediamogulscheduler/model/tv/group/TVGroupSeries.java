package com.mayhew3.mediamogulscheduler.model.tv.group;

import com.mayhew3.mediamogulscheduler.model.tv.Series;
import com.mayhew3.postgresobject.dataobject.FieldValueForeignKey;
import com.mayhew3.postgresobject.dataobject.Nullability;
import com.mayhew3.postgresobject.dataobject.RetireableDataObject;

public class TVGroupSeries extends RetireableDataObject {

  /* Data */
  public FieldValueForeignKey tv_group_id = registerForeignKey(new TVGroup(), Nullability.NOT_NULL);
  public FieldValueForeignKey series_id = registerForeignKey(new Series(), Nullability.NOT_NULL);

  public TVGroupSeries() {
    addUniqueConstraint(tv_group_id, series_id);
  }

  @Override
  public String getTableName() {
    return "tv_group_series";
  }

  @Override
  public String toString() {
    return "tv_group_series " + tv_group_id.getValue() + ", " + series_id.getValue();
  }

}
