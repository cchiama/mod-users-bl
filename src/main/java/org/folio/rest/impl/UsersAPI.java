package org.folio.rest.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.folio.rest.jaxrs.model.CompositeUser;
import org.folio.rest.jaxrs.model.Credentials;
import org.folio.rest.jaxrs.model.PatronGroup;
import org.folio.rest.jaxrs.model.Permissions;
import org.folio.rest.jaxrs.model.User;
import org.folio.rest.jaxrs.resource.UsersResource;
import org.folio.rest.tools.client.BuildCQL;
import org.folio.rest.tools.client.HttpModuleClient2;
import org.folio.rest.tools.client.Response;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
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

  @Override
  public void getUsersByUsernameByUsername(String username, List<String> include,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<javax.ws.rs.core.Response>> asyncResultHandler, Context vertxContext)
      throws Exception {
    // TODO Auto-generated method stub

  }

  Consumer<Response> handlePreviousResponse(boolean isSingleResult, Handler<AsyncResult<javax.ws.rs.core.Response>> asyncResultHandler){
    return (response) -> {
        handleError(response, isSingleResult, asyncResultHandler);
    };
  }

  private void handleError(Response response, boolean isSingleResult, Handler<AsyncResult<javax.ws.rs.core.Response>> asyncResultHandler){
    int statusCode = response.getCode();
    boolean ok = Response.isSuccess(statusCode);
    if(ok && !isSingleResult){
        Integer totalRecords = response.getBody().getInteger("total_records");
        if(totalRecords == null || totalRecords < 1) {
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            GetUsersByIdByUseridResponse.withPlainNotFound("No record found for query '" + response.getEndpoint() + "'")));
        } else if(totalRecords > 1) {
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            GetUsersByIdByUseridResponse.withPlainBadRequest(("'" + response.getEndpoint() + "' returns multiple results"))));
        }
    }
    else if(!ok){
      statusCode = response.getError().getInteger("statusCode");
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
  public void getUsersByIdByUserid(String userid, List<String> include,
      Map<String, String> okapiHeaders, Handler<AsyncResult<javax.ws.rs.core.Response>> asyncResultHandler,
      Context vertxContext) throws Exception {

    boolean []aRequestHasFailed = new boolean[]{false};
    String tenant = okapiHeaders.get(OKAPI_TENANT_HEADER);
    String okapiURL = okapiHeaders.get(OKAPI_URL_HEADER);
    HttpModuleClient2 client = new HttpModuleClient2(okapiURL, tenant);

    //make a call to /users/id and get back a cf to access results when ready
    CompletableFuture<Response> userIdResponse = client.request("/users/" + userid, okapiHeaders);

    //calling thenCompose on the userId cf will wait for the /users/id call to complete
    //and once complete the Response object will be passed in as a parameter to the function within the
    //thenCompose - in this case chainedRequest - which is different then a regular request in that it
    //(1) receives the Response from the previous (2) can populate the request url with values from the
    //returned response json. (3) receive a callback which is called on the passed in response to make
    //sure it is a valid response and that it is ok to continue with the actual request
    //NOTE: if the previous request failed (the Response error or exception objects are populated
    //the chained http request will not be triggered
    int includeCount = include.size();
    CompletableFuture<Response> []requestedIncludes = new CompletableFuture[includeCount+1];
    Map<String, CompletableFuture<Response>> completedLookup = new HashMap<>();

    for (int i = 0; i < includeCount; i++) {

      if(include.get(i).equals("credentials")){
        //call credentials once the /users?query=username={username} completes
        CompletableFuture<Response> credResponse = userIdResponse.thenCompose(
              client.chainedRequest("/authn/credentials/{username}", okapiHeaders, new BuildCQL(null, "users[*].username", "cuser"),
                handlePreviousResponse(true, asyncResultHandler)));
        requestedIncludes[i] = credResponse;
        completedLookup.put("credentials", credResponse);
      }
      else if(include.get(i).equals("perms")){
        //call perms once the /users?query=username={username} (same as creds) completes
        CompletableFuture<Response> permResponse = userIdResponse.thenCompose(
              client.chainedRequest("/perms/users/{username}", okapiHeaders, new BuildCQL(null, "users[*].username", "cuser"),
                handlePreviousResponse(true, asyncResultHandler)));
        requestedIncludes[i] = permResponse;
        completedLookup.put("perms", permResponse);
      }
      else if(include.get(i).equals("groups")){
        CompletableFuture<Response> groupResponse = userIdResponse.thenCompose(
          client.chainedRequest("/groups/{patronGroup}", okapiHeaders, null,
            handlePreviousResponse(true, asyncResultHandler)));
        requestedIncludes[i] = groupResponse;
        completedLookup.put("groups", groupResponse);
      }
    }
    requestedIncludes[includeCount] = userIdResponse;
    //INCLUDES INDICATE WHICH HTTP REQUESTS TO MAKE AND THEN MERGE INTO COMPOSITE OBJECT
    //NO MORE IDS IN COMPOSITE OBJECT
    //IF ONE REQUEST FAILS, FAIL REQUEST WITH ERROR OF FIRST FAILURE
    //need to be able to run on two resp and populate two fields in the composite

    CompletableFuture.allOf(requestedIncludes)
      .thenAccept((response) -> {
        try {
          CompositeUser cu = new CompositeUser();
          cu.setUser((User)userIdResponse.get().convertToPojo(User.class));
          CompletableFuture<Response> cf = completedLookup.get("credentials");
          if(cf != null){
            cu.setCredentials((Credentials)cf.get().convertToPojo(Credentials.class) );
          }
          cf = completedLookup.get("perms");
          if(cf != null){
            cu.setPermissions((Permissions)cf.get().convertToPojo(Permissions.class) );
          }
          cf = completedLookup.get("groups");
          if(cf != null){
            cu.setPatronGroup((PatronGroup)cf.get().convertToPojo(PatronGroup.class) );
          }
          client.closeClient();
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            GetUsersByIdByUseridResponse.withJsonOK(cu)));
        } catch (Exception e) {
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            GetUsersByIdByUseridResponse.withPlainInternalServerError(e.getLocalizedMessage())));
          logger.error(e.getMessage(), e);
        }
      });
  }

  @Override
  public void getUsers(String query, String orderBy, Order order, int offset, int limit,
      List<String> include, Map<String, String> okapiHeaders,
      Handler<AsyncResult<javax.ws.rs.core.Response>> asyncResultHandler, Context vertxContext)
      throws Exception {
    // TODO Auto-generated method stub

  }

}
