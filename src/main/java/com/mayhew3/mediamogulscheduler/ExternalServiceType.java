package com.mayhew3.mediamogulscheduler;

import com.google.common.collect.Lists;

import java.util.Optional;

public enum ExternalServiceType {
  TVDB("tvdb"),
  HowLongToBeat("HowLongToBeat");

  private final String typekey;

  ExternalServiceType(String typekey) {
    this.typekey = typekey;
  }

  public String getTypekey() {
    return typekey;
  }

  public static Optional<ExternalServiceType> getServiceType(final String typekey) {
    return Lists.newArrayList(ExternalServiceType.values())
        .stream()
        .filter(serviceType -> serviceType.typekey.equalsIgnoreCase(typekey))
        .findAny();
  }

  @Override
  public String toString() {
    return getTypekey();
  }

}
