package com.mayhew3.mediamogulscheduler.tv.helper;

import com.mayhew3.mediamogulscheduler.tv.exception.ShowFailedException;

public class MetacriticException extends ShowFailedException {
  public MetacriticException(String errorMessage) {
    super(errorMessage);
  }
}
