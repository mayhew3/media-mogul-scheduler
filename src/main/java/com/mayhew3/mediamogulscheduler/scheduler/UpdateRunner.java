package com.mayhew3.mediamogulscheduler.scheduler;

import com.mashape.unirest.http.exceptions.UnirestException;
import com.mayhew3.mediamogulscheduler.tv.helper.UpdateMode;
import com.mayhew3.postgresobject.db.SQLConnection;
import org.apache.http.auth.AuthenticationException;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.sql.SQLException;

public interface UpdateRunner {
  String getRunnerName();

  @Nullable
  UpdateMode getUpdateMode();

  default String getUniqueIdentifier() {
    UpdateMode updateMode = getUpdateMode();
    if (updateMode == null) {
      return getRunnerName();
    } else {
      return getRunnerName() + " (" + updateMode.getTypekey() + ")";
    }
  }

  void runUpdate() throws SQLException, InterruptedException, IOException, UnirestException, AuthenticationException;
}
