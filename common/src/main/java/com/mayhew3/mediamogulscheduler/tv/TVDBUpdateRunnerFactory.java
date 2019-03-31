package com.mayhew3.mediamogulscheduler.tv;

import com.mayhew3.mediamogulscheduler.tv.helper.UpdateMode;
import com.mayhew3.mediamogulscheduler.tv.provider.TVDBJWTProvider;
import com.mayhew3.mediamogulscheduler.xml.JSONReader;
import com.mayhew3.postgresobject.db.SQLConnection;

import java.io.Serializable;

public class TVDBUpdateRunnerFactory implements Serializable {

  private UpdateMode updateMode;

  public TVDBUpdateRunnerFactory(UpdateMode updateMode) {
    this.updateMode = updateMode;
  }

  public TVDBUpdateRunner generate(SQLConnection connection,
                                   TVDBJWTProvider tvdbjwtProvider,
                                   JSONReader jsonReader,
                                   UpdateMode updateMode) {
    return new TVDBUpdateRunner(connection, tvdbjwtProvider, jsonReader, updateMode);
  }

  @Override
  public String toString() {
    return "TVDBUpdateRunnerFactory, UpdateMode: " + this.getUpdateMode();
  }

  public UpdateMode getUpdateMode() {
    return updateMode;
  }
}
