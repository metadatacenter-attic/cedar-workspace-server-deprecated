package org.metadatacenter.cedar.folder;

import io.dropwizard.Application;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.cedar.folder.core.CedarAssertionExceptionMapper;
import org.metadatacenter.cedar.folder.health.FolderServerHealthCheck;
import org.metadatacenter.cedar.folder.resources.*;
import org.metadatacenter.server.security.Authorization;
import org.metadatacenter.server.security.AuthorizationKeycloakAndApiKeyResolver;
import org.metadatacenter.server.security.IAuthorizationResolver;
import org.metadatacenter.server.security.KeycloakDeploymentProvider;

import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import java.util.EnumSet;

import static org.eclipse.jetty.servlets.CrossOriginFilter.*;

public class FolderServerApplication extends Application<FolderServerConfiguration> {
  public static void main(String[] args) throws Exception {
    new FolderServerApplication().run(args);
  }

  @Override
  public String getName() {
    return "folder-server";
  }

  @Override
  public void initialize(Bootstrap<FolderServerConfiguration> bootstrap) {
    // Init Keycloak
    KeycloakDeploymentProvider.getInstance();
    // Init Authorization Resolver
    IAuthorizationResolver authResolver = new AuthorizationKeycloakAndApiKeyResolver();
    Authorization.setAuthorizationResolver(authResolver);
    Authorization.setUserService(CedarDataServices.getUserService());
  }

  @Override
  public void run(FolderServerConfiguration configuration, Environment environment) {
    final IndexResource index = new IndexResource();
    environment.jersey().register(index);

    environment.jersey().register(new AccessibleNodesResource());
    environment.jersey().register(new CommandResource());
    environment.jersey().register(new FolderContentsResource());
    environment.jersey().register(new FoldersResource());
    environment.jersey().register(new NodesResource());
    environment.jersey().register(new ResourcesResource());
    environment.jersey().register(new UsersResource());

    final FolderServerHealthCheck healthCheck = new FolderServerHealthCheck();
    environment.healthChecks().register("message", healthCheck);

    environment.jersey().register(new CedarAssertionExceptionMapper());

    // Enable CORS headers
    final FilterRegistration.Dynamic cors = environment.servlets().addFilter("CORS", CrossOriginFilter.class);

    // Configure CORS parameters
    cors.setInitParameter(ALLOWED_ORIGINS_PARAM, "*");
    cors.setInitParameter(ALLOWED_HEADERS_PARAM,
        "X-Requested-With,Content-Type,Accept,Origin,Referer,User-Agent,Authorization");
    cors.setInitParameter(ALLOWED_METHODS_PARAM, "OPTIONS,GET,PUT,POST,DELETE,HEAD,PATCH");

    // Add URL mapping
    cors.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true, "/*");

  }
}
