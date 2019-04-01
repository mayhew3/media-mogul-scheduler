package com.mayhew3.mediamogulscheduler.tv.exception;

public class MultipleMatchesException extends ShowFailedException {
  public MultipleMatchesException(String errorMessage) {
    super(errorMessage);
  }
}
