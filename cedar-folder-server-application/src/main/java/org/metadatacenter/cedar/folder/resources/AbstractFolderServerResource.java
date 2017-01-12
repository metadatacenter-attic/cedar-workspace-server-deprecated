package org.metadatacenter.cedar.folder.resources;

import org.metadatacenter.config.CedarConfig;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;

public class AbstractFolderServerResource {

  protected
  @Context
  UriInfo uriInfo;

  protected
  @Context
  HttpServletRequest request;

  protected
  @Context
  HttpServletResponse response;

  protected final CedarConfig cedarConfig;

  protected AbstractFolderServerResource(CedarConfig cedarConfig) {
    this.cedarConfig = cedarConfig;
  }

}
