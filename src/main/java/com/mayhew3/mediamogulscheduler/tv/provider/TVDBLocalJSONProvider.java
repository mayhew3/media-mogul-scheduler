package com.mayhew3.mediamogulscheduler.tv.provider;

import com.google.common.annotations.VisibleForTesting;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Timestamp;

@VisibleForTesting
public class TVDBLocalJSONProvider implements TVDBJWTProvider {
  private String filePrefix = "src\\test\\resources\\TVDBTest\\";

  public TVDBLocalJSONProvider(String localFilePath) {
    this.filePrefix = localFilePath;
  }

  @Override
  public JSONObject findSeriesMatches(String formattedTitle) throws UnirestException {
    String filepath = filePrefix + "search_" + formattedTitle + ".json";
    return parseJSONObject(filepath);
  }

  @Override
  public JSONObject getSeriesData(Integer tvdbSeriesId) throws UnirestException {
    String filepath = filePrefix + tvdbSeriesId + "_summary.json";
    return parseJSONObject(filepath);
  }

  @Override
  public JSONObject getEpisodeSummaries(Integer tvdbSeriesId, Integer pageNumber) {
    String filepath = filePrefix + tvdbSeriesId + "_episodes.json";
    return parseJSONObject(filepath);
  }

  @Override
  public JSONObject getEpisodeData(Integer tvdbEpisodeId) throws UnirestException {
    String filepath = filePrefix + "E" + tvdbEpisodeId + ".json";
    return parseJSONObject(filepath);
  }

  @Override
  public JSONObject getPosterData(Integer tvdbId) throws UnirestException {
    String filepath = filePrefix + tvdbId + "_posters.json";
    return parseJSONObject(filepath);
  }

  @Override
  public JSONObject getUpdatedSeries(Timestamp fromDate) throws UnirestException {
    String filepath = filePrefix + "_updated.json";
    return parseJSONObject(filepath);
  }


  // utility methods

  private JSONObject parseJSONObject(String filepath) {
    try {
      byte[] bytes = Files.readAllBytes(Paths.get(filepath));
      String text = new String(bytes, Charset.defaultCharset());
      return new JSONObject(text);
    } catch (IOException e) {
      e.printStackTrace();
      throw new RuntimeException("Unable to read from file path: " + filepath);
    }
  }

}
