package com.mayhew3.mediamogulscheduler.games.provider;

import org.json.JSONArray;

public interface IGDBProvider {

  JSONArray findGameMatches(String gameTitle);

  JSONArray getUpdatedInfo(Integer igdb_id);
}
