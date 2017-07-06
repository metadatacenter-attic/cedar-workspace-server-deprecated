package org.metadatacenter.cedar.workspace.health;

import com.codahale.metrics.health.HealthCheck;

public class FolderServerHealthCheck extends HealthCheck {

  public FolderServerHealthCheck() {
  }

  @Override
  protected Result check() throws Exception {
    if (2 * 2 == 5) {
      return Result.unhealthy("Unhealthy, because 2 * 2 == 5");
    }
    return Result.healthy();
  }
}