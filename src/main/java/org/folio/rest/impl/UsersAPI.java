package org.folio.rest.impl;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.jaxrs.resource.UsersResource;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

/**
 * @author shale
 *
 */
public class UsersAPI implements UsersResource {

  @Override
  public void getUsersByUsername(String username, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
    // TODO Auto-generated method stub

  }

  @Override
  public void getUsersByIdByUserid(String userid, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
    // TODO Auto-generated method stub

  }


}
