package com.mayhew3.mediamogulscheduler.games.provider;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

public class IGDBProviderImpl implements IGDBProvider {

  @Override
  public JSONArray findGameMatches(String gameTitle) {

    String gameTitleEncoded = encodeGameTitle(gameTitle);

    String url = "https://api-endpoint.igdb.com/games/";
    HashMap<String, Object> queryVars = new HashMap<>();
    queryVars.put("search", gameTitleEncoded);
    queryVars.put("fields", "name,cover");
    queryVars.put("limit", "5");
    queryVars.put("offset", "0");

    return getArrayData(url, queryVars);
  }

  @Override
  public JSONArray getUpdatedInfo(Integer igdb_id) {
    String url = "https://api-endpoint.igdb.com/games/" + igdb_id;
    HashMap<String, Object> queryVars = new HashMap<>();
    queryVars.put("fields", "name,cover");

    return getArrayData(url, queryVars);
  }

  private String encodeGameTitle(String gameTitle) {
    String gameTitleEncoded;
    try {
      gameTitleEncoded = URLEncoder.encode(gameTitle, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
    return gameTitleEncoded;
  }


  // utilities

  private HttpResponse<String> getDataInternal(String url, Map<String, Object> queryParams) throws UnirestException {
    String igdb_key = System.getenv("igdb_key");
    if (igdb_key == null) {
      throw new IllegalStateException("No igdb_key environment variable found!");
    }

    return Unirest.get(url)
        .header("user-key", igdb_key)
        .header("Accept", "application/json")
        .queryString(queryParams)
        .asString();
  }

  private JSONArray getJsonArray(HttpResponse<String> stringData) {
    String body = stringData.getBody();
    try {
      return new JSONArray(body);
    } catch (JSONException e) {
      System.out.println("Unable to parse response: ");
      System.out.println(body);
      throw e;
    }
  }

  private JSONArray getArrayData(String url, Map<String, Object> queryParams) {
    try {
      return getJsonArray(getDataInternal(url, queryParams));
    } catch (UnirestException e) {
      throw new RuntimeException(e);
    }
  }

  private JSONObject getJsonObject(HttpResponse<String> stringData) {
    String body = stringData.getBody();
    try {
      return new JSONObject(body);
    } catch (JSONException e) {
      System.out.println("Unable to parse response: ");
      System.out.println(body);
      throw e;
    }
  }

  private JSONObject getObjectData(String url, Map<String, Object> queryParams) {
    try {
      HttpResponse<String> dataInternal = getDataInternal(url, queryParams);
      return getJsonObject(dataInternal);
    } catch (UnirestException e) {
      throw new RuntimeException(e);
    }
  }

}
