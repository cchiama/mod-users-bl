package org.folio.rest.impl;

import java.util.List;
import java.util.Map;

import org.folio.rest.jaxrs.resource.UsersResource;
import org.folio.rest.tools.client.HttpModuleClient;
import org.folio.rest.tools.client.Response;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 * @author shale
 *
 */
public class UsersAPI implements UsersResource {

  private static String OKAPI_URL_HEADER = "X-Okapi-URL";
  private static String OKAPI_TENANT_HEADER = "X-Okapi-Tenant";
  private static String USERS_ENTRY = "users";

  private final Logger logger = LoggerFactory.getLogger(UsersAPI.class);

  private boolean isBetween(int x, int min, int max) {
      return x>=min && x<=max;
  }


  @Override
  public void getUsersByUsernameByUsername(String username, List<String> include,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<javax.ws.rs.core.Response>> asyncResultHandler, Context vertxContext)
      throws Exception {
    // TODO Auto-generated method stub

  }

  @Override
  public void getUsersByIdByUserid(String userid, List<String> include,
      Map<String, String> okapiHeaders, Handler<AsyncResult<javax.ws.rs.core.Response>> asyncResultHandler,
      Context vertxContext) throws Exception {

    String tenant = okapiHeaders.get(OKAPI_TENANT_HEADER);
    String okapiURL = okapiHeaders.get(OKAPI_URL_HEADER);
    HttpModuleClient client = new HttpModuleClient(okapiURL, tenant);
    Response response = client.request("/users/" + userid, okapiHeaders);
    int statusCode = response.getCode();
    if(isBetween(statusCode, 200, 300)){
      //can use BuildCQL object to get value in json as well
      String userName = response.getBody().getString("username");
      response = client.request("/users?query=username=" + userName, okapiHeaders);
      statusCode = response.getCode();
      if(isBetween(statusCode, 200, 300)){
        JsonObject result = response.getBody();
        Integer totalRecords = result.getInteger("total_records");
        if(totalRecords == null || totalRecords < 1) {
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            GetUsersByIdByUseridResponse.withPlainNotFound("Unable to find user " + userName)));
        } else if(totalRecords > 1) {
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            GetUsersByIdByUseridResponse.withPlainBadRequest("'" +
                userName + "' is not unique")));
        } else {
          JsonObject record = result.getJsonArray(USERS_ENTRY).getJsonObject(0);
          logger.debug("Got record " + record.encode());
          Response credResponse = client.request("/authn/credentials/" + userName, okapiHeaders);
          Response permResponse = client.request("/perms/users/" + userName, okapiHeaders);

        }
      }
    }

    if(!isBetween(statusCode, 200, 300)){
      if(statusCode == 404){
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
          GetUsersByIdByUseridResponse.withPlainNotFound(response.getError().encodePrettily())));
      }
      else if(statusCode == 400){
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
          GetUsersByIdByUseridResponse.withPlainBadRequest(response.getError().encodePrettily())));
      }
      else{
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
          GetUsersByIdByUseridResponse.withPlainInternalServerError(response.getError().encodePrettily())));
      }
    }

  }


  @Override
  public void getUsers(String query, String orderBy, Order order, int offset, int limit,
      List<String> include, Map<String, String> okapiHeaders,
      Handler<AsyncResult<javax.ws.rs.core.Response>> asyncResultHandler, Context vertxContext)
      throws Exception {
    // TODO Auto-generated method stub

  }




}
