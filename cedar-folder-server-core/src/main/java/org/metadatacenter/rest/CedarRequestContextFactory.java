package org.metadatacenter.rest;

import play.mvc.Http;

public class CedarRequestContextFactory {
  public static ICedarRequestContext fromRequest(Http.Request request) {
    return new PlayRequestContext(request);
  }
}
