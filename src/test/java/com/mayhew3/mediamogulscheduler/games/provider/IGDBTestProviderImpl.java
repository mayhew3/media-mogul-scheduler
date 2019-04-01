package com.mayhew3.mediamogulscheduler.games.provider;

import com.mayhew3.mediamogulscheduler.xml.JSONReader;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;

public class IGDBTestProviderImpl implements IGDBProvider {
  private String filePrefix;
  private JSONReader jsonReader;

  public IGDBTestProviderImpl(String filePrefix, JSONReader jsonReader) {
    this.filePrefix = filePrefix;
    this.jsonReader = jsonReader;
  }

  @Override
  public JSONArray findGameMatches(String gameTitle) {
    String filepath = filePrefix + "search_" + gameTitle + ".json";
    @NotNull JSONArray jsonArrayFromFile = jsonReader.parseJSONArray(filepath);
     return jsonArrayFromFile;
  }

  @Override
  public JSONArray getUpdatedInfo(Integer igdb_id) {
    String filepath = filePrefix + "id_" + igdb_id + ".json";
    @NotNull JSONArray jsonArrayFromFile = jsonReader.parseJSONArray(filepath);
    return jsonArrayFromFile;
  }

}
