package com.mayhew3.mediamogulscheduler.tv;

import com.mayhew3.postgresobject.db.SQLConnection;

import java.io.Serializable;

public class SeriesDenormFactory implements Serializable {

  public SeriesDenormUpdater generate(SQLConnection connection) {
    return new SeriesDenormUpdater(connection);
  }

  @Override
  public String toString() {
    return "SeriesDenormFactory";
  }
}
