package org.metadatacenter.rest;

import play.mvc.Http;

public class PlayRequestContext implements ICedarRequestContext {

  private Http.Request request;
  
  PlayRequestContext(Http.Request request) {
    this.request = request;
  }
}
