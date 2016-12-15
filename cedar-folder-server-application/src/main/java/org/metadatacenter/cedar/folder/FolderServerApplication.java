package org.metadatacenter.cedar.folder;

import io.dropwizard.Application;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.metadatacenter.cedar.folder.health.FolderServerHealthCheck;
import org.metadatacenter.cedar.folder.resources.*;
import org.metadatacenter.cedar.util.dw.CedarDropwizardApplicationUtil;

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
    CedarDropwizardApplicationUtil.setupKeycloak();
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

    CedarDropwizardApplicationUtil.setupEnvironment(environment);
  }
}
