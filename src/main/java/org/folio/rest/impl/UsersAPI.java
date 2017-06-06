package org.folio.rest.impl;

import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.jaxrs.resource.UsersResource;
import org.folio.rest.tools.client.HttpModuleClient;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

/**
 * @author shale
 *
 */
public class UsersAPI implements UsersResource {

  private static String OKAPI_URL_HEADER = "X-Okapi-URL";
  private static String OKAPI_TOKEN_HEADER = "X-Okapi-Token";
  private static String OKAPI_TENANT_HEADER = "X-Okapi-Tenant";
  private static String OKAPI_PERMISSIONS_HEADER = "X-Okapi-Permissions";

  private boolean isBetween(int x, int min, int max) {
      return x>=min && x<=max;
  }


  @Override
  public void getUsers(String query, String orderBy, Order order, int offset, int limit,
      List<String> include, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
    // TODO Auto-generated method stub

  }


  @Override
  public void getUsersByIdByUserid(String userid, List<String> include,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) throws Exception {

    String tenant = okapiHeaders.get(OKAPI_TENANT_HEADER);
    String okapiURL = okapiHeaders.get(OKAPI_URL_HEADER);
    HttpModuleClient client = new HttpModuleClient(okapiURL, tenant);
    org.folio.rest.tools.client.Response response = client.request("/users/" + userid, okapiHeaders);
    int statusCode = response.getCode();
    if(isBetween(statusCode, 200, 300)){

    }
    else if(statusCode == 404){
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
        GetUsersByIdByUseridResponse.withPlainNotFound(response.getError().encodePrettily())));
    }
    else{
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
        GetUsersByIdByUseridResponse.withPlainInternalServerError(response.getError().encodePrettily())));
    }

  }

  /* (non-Javadoc)
   * @see org.folio.rest.jaxrs.resource.UsersResource#getUsersByUsernameByUsername(java.lang.String, java.util.List, java.util.Map, io.vertx.core.Handler, io.vertx.core.Context)
   */
  @Override
  public void getUsersByUsernameByUsername(String username, List<String> include,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) throws Exception {
    // TODO Auto-generated method stub

  }
}
