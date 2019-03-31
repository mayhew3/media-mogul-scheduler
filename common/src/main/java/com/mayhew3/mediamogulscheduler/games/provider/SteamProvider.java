package com.mayhew3.mediamogulscheduler.games.provider;

import org.json.JSONObject;

import java.io.IOException;

public interface SteamProvider {
  JSONObject getSteamInfo() throws IOException;

  String getFullUrl();
}
