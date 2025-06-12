package com.hathoute.kubernetes.operator.openhands.reporter;

record Health(Status status, Integer statusCode) {

  enum Status {
    RUNNING, STOPPED, UNKNOWN
  }
}
