package com.mayhew3.mediamogulscheduler.tv.exception;

public class MalformedTVDBEpisodeException extends ShowFailedException {
  public MalformedTVDBEpisodeException(String errorMessage) {
    super(errorMessage);
  }
}
