package com.mayhew3.mediamogulscheduler;

import com.mayhew3.postgresobject.db.SQLConnection;
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

  void runUpdate(SQLConnection connection) throws SQLException, InterruptedException, IOException;
}
