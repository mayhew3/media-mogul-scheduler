package com.mayhew3.mediamogulscheduler.games;

public class GameFailedException extends Exception {
  public GameFailedException(String errorMessage) {
    super(errorMessage);
  }
}
