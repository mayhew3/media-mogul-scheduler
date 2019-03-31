package com.mayhew3.mediamogulscheduler.games;

import callback.OnSuccessCallback;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import wrapper.IGDBWrapper;
import wrapper.Parameters;
import wrapper.Version;

public class IGDBUpdateTestRunner {


  /*

    Test utility to see if threads close in most basic case. They don't.

  * */
  public static void main(String[] args) {
    IGDBWrapper igdbWrapper = new IGDBWrapper(System.getenv("igdb_key"), Version.STANDARD, false);

    Parameters parameters = new Parameters()
        .addSearch("Forza Horizon 4")
        .addFields("name,cover")
        .addLimit("5")
        .addOffset("0");

    igdbWrapper.games(parameters, new OnSuccessCallback() {
      @Override
      public void onSuccess(@NotNull JSONArray jsonArray) {
        System.out.println(jsonArray);
      }

      @Override
      public void onError(@NotNull Exception e) {
        throw new RuntimeException(e);
      }
    });



  }

  protected static void debug(Object object) {
    System.out.println(object);
  }



}
